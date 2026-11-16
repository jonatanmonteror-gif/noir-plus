package com.noirplus.tv.ui

import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.noirplus.tv.MainActivity
import com.noirplus.tv.data.Http
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Progress
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(req: PlayRequest) {
    val ctx = LocalContext.current
    val src = Repo.source
    val prefs = Repo.prefs
    val channels = req.channels
    var index by remember { mutableIntStateOf(req.channelIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    val channel = channels.getOrNull(index)
    var url by remember { mutableStateOf(req.url) }
    var triedAlt by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var infoVisible by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(false) }
    val startApplied = remember { booleanArrayOf(false) }
    val backConsumed = remember { booleanArrayOf(false) }
    val viewRef = remember { arrayOfNulls<PlayerView>(1) }

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .build()
    }

    fun save() {
        if (req.isLive) return
        val d = exo.duration
        val p = exo.currentPosition
        if (d <= 0) return
        if (p > d * 0.95) prefs.removeProgress(req.key)
        else if (p > 15_000) prefs.saveProgress(
            Progress(req.key, req.title, req.image, url, p, d, System.currentTimeMillis(), req.parent)
        )
    }

    fun handleKey(ev: KeyEvent): Boolean {
        val down = ev.action == KeyEvent.ACTION_DOWN
        if (req.isLive) {
            when (ev.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (down && channels.size > 1) index = (index - 1 + channels.size) % channels.size
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (down && channels.size > 1) index = (index + 1) % channels.size
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (down && ev.repeatCount == 0) infoVisible = !infoVisible
                    return true
                }
            }
            return false
        }
        val view = viewRef[0] ?: return false
        if (ev.keyCode == KeyEvent.KEYCODE_BACK) {
            if (down && view.isControllerFullyVisible) {
                view.hideController()
                backConsumed[0] = true
                return true
            }
            if (!down && backConsumed[0]) {
                backConsumed[0] = false
                return true
            }
            return false
        }
        if (down && !view.isControllerFullyVisible) {
            when (ev.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0))
                    view.showController()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    exo.seekTo(exo.currentPosition + 10_000)
                    view.showController()
                    return true
                }
            }
        }
        return false
    }

    // Cambio de canal (zapping)
    LaunchedEffect(index) {
        if (req.isLive && channel != null && src != null) {
            val u = src.streamUrl(channel)
            if (u.isNotEmpty() && u != url) {
                triedAlt = false
                url = u
            }
            infoVisible = true
        }
    }

    LaunchedEffect(url) {
        error = null
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        if (!startApplied[0]) {
            startApplied[0] = true
            if (!req.isLive && req.startMs > 0) exo.seekTo(req.startMs)
        }
        exo.playWhenReady = true
    }

    LaunchedEffect(infoVisible, index) {
        if (infoVisible) {
            delay(4000)
            infoVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            save()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                when {
                    e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        exo.seekToDefaultPosition()
                        exo.prepare()
                    }
                    // Si el canal falla en HLS probamos en TS (y al revés).
                    req.isLive && !triedAlt && (url.endsWith(".m3u8") || url.endsWith(".ts")) -> {
                        triedAlt = true
                        url = if (url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else url.removeSuffix(".ts") + ".m3u8"
                    }
                    else -> error = "No se ha podido reproducir este contenido.\n(${e.errorCodeName})"
                }
            }
        }
        exo.addListener(listener)
        MainActivity.keyHandler = { ev -> handleKey(ev) }
        onDispose {
            save()
            MainActivity.keyHandler = null
            exo.removeListener(listener)
            exo.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    useController = !req.isLive
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    controllerShowTimeoutMs = 4000
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE }
                    )
                    viewRef[0] = this
                    post { requestFocus() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Título arriba cuando se ven los controles (películas y series)
        if (!req.isLive && controlsVisible) {
            Text(
                req.title,
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Banda de información del canal en directo
        if (req.isLive && infoVisible) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val img = channel?.image ?: req.image
                if (img != null) {
                    AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 110.dp, height = 64.dp))
                    Spacer(Modifier.width(18.dp))
                }
                Column {
                    Text(
                        if (channel != null) "${index + 1}   ${channel.name}" else req.title,
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (channels.size > 1) "▲ ▼  Cambiar de canal   ·   OK  Mostrar/ocultar   ·   Atrás  Salir" else "Atrás  Salir",
                        color = Col.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        error?.let {
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(it, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                Text(
                    if (req.isLive && channels.size > 1) "Prueba otro canal con ▲ ▼ o pulsa Atrás" else "Pulsa Atrás para volver",
                    color = Col.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
