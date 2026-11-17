package com.noirplus.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.noirplus.tv.MainActivity
import com.noirplus.tv.data.Http
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Progress
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Ajustar",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Rellenar",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Estirar",
)

@SuppressLint("ClickableViewAccessibility")
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(req: PlayRequest) {
    val ctx = LocalContext.current
    val src = Repo.source
    val prefs = Repo.prefs
    val dims = LocalDims.current
    val nav = LocalNav.current

    // Lo que se está reproduciendo ahora (cambia con el siguiente episodio)
    var cur by remember { mutableStateOf(req) }
    val channels = req.channels
    var index by remember { mutableIntStateOf(req.channelIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    val channel = channels.getOrNull(index)
    var url by remember { mutableStateOf(req.url) }
    var triedAlt by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var infoVisible by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var sessionDelay by remember { mutableIntStateOf(prefs.subDelayMs) }
    var resize by remember { mutableIntStateOf(0) }
    var nextCountdown by remember { mutableIntStateOf(-1) }
    var formatToast by remember { mutableIntStateOf(0) }
    var showFormat by remember { mutableStateOf(false) }
    // Indicador de gestos: 0 nada, 1 brillo, 2 volumen
    var gesture by remember { mutableIntStateOf(0) }
    var gestureLevel by remember { mutableFloatStateOf(0f) }
    var gestureTick by remember { mutableIntStateOf(0) }
    val startApplied = remember { booleanArrayOf(false) }
    val backConsumed = remember { booleanArrayOf(false) }
    val viewRef = remember { arrayOfNulls<PlayerView>(1) }
    val panelFr = remember { FocusRequester() }
    val changes = prefs.changes.intValue

    val exo = remember { buildPlayer(ctx, req.isLive) }
    val activity = remember { ctx.findActivity() }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    SubtitleClock.delayUs = sessionDelay * 1000L

    // Móvil: horizontal mientras se reproduce; al salir, se restaura orientación y brillo.
    DisposableEffect(Unit) {
        val prev = activity?.requestedOrientation
        if (!dims.tv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (!dims.tv && prev != null) activity?.requestedOrientation = prev
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
            SubtitleClock.delayUs = 0L
        }
    }

    fun brightness(): Float {
        val b = activity?.window?.attributes?.screenBrightness ?: -1f
        if (b >= 0f) return b
        return runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    fun setBrightness(v: Float) {
        val w = activity?.window ?: return
        val lp = w.attributes
        lp.screenBrightness = v.coerceIn(0.02f, 1f)
        w.attributes = lp
    }

    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0f, 1f) * max).roundToInt(), 0)
    }

    /** Aplica el gesto: modo 1 = brillo (izquierda), 2 = volumen (derecha). */
    fun applyGesture(mode: Int, level: Float) {
        val l = level.coerceIn(0f, 1f)
        if (mode == 1) setBrightness(l) else setVolume(l)
        gesture = mode
        gestureLevel = l
        gestureTick++
    }

    fun save() {
        val c = cur
        if (c.isLive) return
        val d = exo.duration
        val p = exo.currentPosition
        if (d <= 0) return
        if (p > d * 0.95) prefs.removeProgress(c.key)
        else if (p > 15_000) prefs.saveProgress(Progress(c.key, c.title, c.image, url, p, d, System.currentTimeMillis(), c.parent))
    }

    fun nextRequest(): PlayRequest? {
        val c = cur
        val ni = c.episodeIndex + 1
        if (c.episodeIndex < 0 || ni >= c.episodes.size || src == null) return null
        val ep = c.episodes[ni]
        return c.copy(
            url = src.episodeUrl(ep),
            title = "${c.seriesName ?: ""} · T${ep.season} E${ep.number}",
            key = "EP:${ep.id}",
            image = ep.image ?: c.image,
            startMs = 0L,
            episodeIndex = ni,
        )
    }

    fun playNext() {
        val n = nextRequest() ?: return
        save()
        nextCountdown = -1
        startApplied[0] = false
        cur = n
        url = n.url
    }

    fun handleKey(ev: KeyEvent): Boolean {
        val down = ev.action == KeyEvent.ACTION_DOWN
        // Con el panel de opciones abierto, el mando lo controla Compose (Atrás lo cierra).
        if (panelOpen) {
            if (ev.keyCode == KeyEvent.KEYCODE_BACK) {
                if (!down) panelOpen = false
                return true
            }
            return false
        }
        if (ev.keyCode == KeyEvent.KEYCODE_MENU || ev.keyCode == KeyEvent.KEYCODE_CAPTIONS || ev.keyCode == KeyEvent.KEYCODE_SETTINGS ||
            ev.keyCode == KeyEvent.KEYCODE_INFO
        ) {
            if (down) panelOpen = true
            return true
        }
        if (nextCountdown >= 0 && down && (ev.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || ev.keyCode == KeyEvent.KEYCODE_ENTER)) {
            playNext()
            return true
        }
        if (cur.isLive) {
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
        if (cur.isLive && channel != null && src != null) {
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
        cues = emptyList()
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        if (!startApplied[0]) {
            startApplied[0] = true
            if (!cur.isLive && cur.startMs > 0) exo.seekTo(cur.startMs)
        }
        exo.playWhenReady = true
    }

    LaunchedEffect(infoVisible, index) {
        if (infoVisible) {
            delay(4000)
            infoVisible = false
        }
    }

    LaunchedEffect(formatToast) {
        if (formatToast > 0) {
            showFormat = true
            delay(1300)
            showFormat = false
        }
    }

    LaunchedEffect(gestureTick) {
        if (gesture != 0) {
            delay(900)
            gesture = 0
        }
    }

    // Cuenta atrás del siguiente episodio
    LaunchedEffect(nextCountdown) {
        if (nextCountdown > 0) {
            delay(1000)
            nextCountdown -= 1
        } else if (nextCountdown == 0) {
            playNext()
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
                    cur.isLive && !triedAlt && (url.endsWith(".m3u8") || url.endsWith(".ts")) -> {
                        triedAlt = true
                        url = if (url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else url.removeSuffix(".ts") + ".m3u8"
                    }
                    else -> error = "No se ha podido reproducir este contenido.\n(${e.errorCodeName})"
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !cur.isLive) {
                    prefs.removeProgress(cur.key)
                    if (prefs.autoNext && nextRequest() != null) nextCountdown = 8
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
                    setKeepContentOnPlayerReset(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    controllerShowTimeoutMs = 4000
                    isFocusable = true
                    isFocusableInTouchMode = true
                    subtitleView?.visibility = View.GONE // los subtítulos los dibuja la app
                    setShowSubtitleButton(true)
                    // Botones propios junto a la ruedita: ajustes de subtítulos y formato de imagen
                    val gear = findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                    (gear?.parent as? android.view.ViewGroup)?.let { bar ->
                        fun add(icon: Int, desc: String, onTap: () -> Unit) {
                            val b = android.widget.ImageButton(c, null, 0, androidx.media3.ui.R.style.ExoStyledControls_Button_Bottom)
                            b.setImageResource(icon)
                            b.contentDescription = desc
                            b.setOnClickListener { onTap() }
                            bar.addView(b, bar.indexOfChild(gear))
                        }
                        add(com.noirplus.tv.R.drawable.ic_subtitle_tune, "Ajustes de subtítulos") { panelOpen = true }
                        add(com.noirplus.tv.R.drawable.ic_aspect, "Formato de imagen") {
                            resize = (resize + 1) % ResizeModes.size
                            formatToast++
                        }
                    }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE }
                    )
                    if (!dims.tv) {
                        // Gestos: mitad izquierda = brillo, mitad derecha = volumen (arrastrar arriba/abajo)
                        val slop = ViewConfiguration.get(c).scaledTouchSlop * 2
                        var sx = 0f
                        var sy = 0f
                        var mode = 0
                        var start = 0f
                        setOnTouchListener { v, e ->
                            when (e.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    sx = e.x; sy = e.y; mode = 0
                                    false
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dy = e.y - sy
                                    if (mode == 0 && abs(dy) > slop && abs(dy) > abs(e.x - sx) * 1.5f) {
                                        mode = if (sx < v.width / 2f) 1 else 2
                                        start = if (mode == 1) brightness() else volume()
                                    }
                                    if (mode != 0) {
                                        applyGesture(mode, start - dy / (v.height * 0.8f))
                                        true
                                    } else {
                                        false
                                    }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    val was = mode != 0
                                    mode = 0
                                    was
                                }
                                else -> false
                            }
                        }
                    }
                    viewRef[0] = this
                    post { requestFocus() }
                }
            },
            update = { v -> v.resizeMode = ResizeModes[resize].first },
            modifier = Modifier.fillMaxSize(),
        )

        // Subtítulos propios (posición, altura, tamaño y fondo según Ajustes)
        if (changes >= 0) {
            SubtitleLayer(
                cues,
                pos = prefs.subPos,
                offsetDp = prefs.subOffset + if (controlsVisible && prefs.subPos == 0) 60 else 0,
                sizeSp = prefs.subSizeSp,
                box = prefs.subBg == 1,
                font = prefs.subFont,
            )
        }

        // Directo en móvil: tocar = info; arrastrar = brillo / volumen
        if (cur.isLive && !dims.tv) {
            var dMode by remember { mutableIntStateOf(0) }
            var dStart by remember { mutableFloatStateOf(0f) }
            var dAcc by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { infoVisible = !infoVisible }) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { o ->
                                dMode = if (o.x < size.width / 2f) 1 else 2
                                dStart = if (dMode == 1) brightness() else volume()
                                dAcc = 0f
                            },
                            onVerticalDrag = { _, dy ->
                                dAcc += dy
                                applyGesture(dMode, dStart - dAcc / (size.height * 0.8f))
                            },
                        )
                    }
            )
        }

        // Indicador de brillo / volumen
        AnimatedVisibility(gesture != 0, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (gesture == 1) "Brillo" else "Volumen", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.padding(top = 10.dp).width(150.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(gestureLevel).clip(RoundedCornerShape(50)).background(Color.White))
                }
                Text("${(gestureLevel * 100).roundToInt()} %", color = Col.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        AnimatedVisibility(showFormat, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Text(
                ResizeModes[resize].second,
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        // Cabecera con título cuando se ven los controles
        val showTop = (!cur.isLive && controlsVisible) || (cur.isLive && infoVisible)
        if (showTop) {
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .padding(horizontal = edge, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (cur.isLive) "" else cur.title,
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (cur.isLive && !dims.tv) {
                    ActionButton("Formato") {
                        resize = (resize + 1) % ResizeModes.size
                        formatToast++
                    }
                }
            }
        }

        // Banda de información del canal en directo
        if (cur.isLive && infoVisible) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .padding(horizontal = edge, vertical = 28.dp),
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
                    if (dims.tv) {
                        Text(
                            if (channels.size > 1) "▲ ▼  Cambiar de canal   ·   OK  Mostrar/ocultar   ·   Atrás  Salir" else "Atrás  Salir",
                            color = Col.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (!dims.tv && channels.size > 1) {
                    Spacer(Modifier.weight(1f))
                    ActionButton("◀  Anterior") { index = (index - 1 + channels.size) % channels.size }
                    Spacer(Modifier.width(10.dp))
                    ActionButton("Siguiente  ▶") { index = (index + 1) % channels.size }
                }
            }
        }

        // Siguiente episodio
        if (nextCountdown >= 0) {
            val n = nextRequest()
            if (n != null) {
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(edge).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.78f)).padding(18.dp),
                ) {
                    Text("Siguiente episodio en $nextCountdown s", color = Col.Sub, fontSize = 12.sp)
                    Text(n.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton("Ver ahora", primary = true) { playNext() }
                        ActionButton("Cancelar") { nextCountdown = -1 }
                    }
                }
            }
        }

        // Panel rápido de subtítulos (compacto)
        if (panelOpen) {
            LaunchedEffect(Unit) {
                delay(120)
                runCatching { panelFr.requestFocus() }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { panelOpen = false } })
            Column(
                Modifier.align(Alignment.CenterEnd).padding(14.dp).width(if (dims.tv) 330.dp else 290.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Col.Surface.copy(alpha = 0.96f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtítulos", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Pista: botón CC", color = Col.Sub, fontSize = 11.sp)
                }
                Text("Retardo", color = Col.Sub, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Focusable(key = "pd-minus", shape = RoundedCornerShape(8.dp), focusScale = 1f, focusRequester = panelFr,
                        onClick = { sessionDelay = (sessionDelay - 100).coerceAtLeast(-2000) }) { f ->
                        Text("−", Modifier.background(if (f) Color.White else Color.White.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 5.dp),
                            color = if (f) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(fmtDelay(sessionDelay), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Focusable(key = "pd-plus", shape = RoundedCornerShape(8.dp), focusScale = 1f,
                        onClick = { sessionDelay = (sessionDelay + 100).coerceAtMost(2000) }) { f ->
                        Text("+", Modifier.background(if (f) Color.White else Color.White.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 5.dp),
                            color = if (f) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Posición", color = Col.Sub, fontSize = 12.sp)
                Segmented(SubPositions, prefs.subPos, "pp") { prefs.subPos = it }
                Text("Altura", color = Col.Sub, fontSize = 12.sp)
                MiniStepper("${if (prefs.subOffset > 0) "+" else ""}${prefs.subOffset}", "ph", { prefs.subOffset -= 4 }, { prefs.subOffset += 4 })
                Text("Tamaño", color = Col.Sub, fontSize = 12.sp)
                MiniStepper("${prefs.subSizeSp}", "ps", { prefs.subSizeSp -= 1 }, { prefs.subSizeSp += 1 })
                Text("Tipografía", color = Col.Sub, fontSize = 12.sp)
                FontPicker(prefs.subFont, "pf") { prefs.subFont = it }
                Text("Fondo", color = Col.Sub, fontSize = 12.sp)
                Segmented(listOf("Sombra", "Caja"), prefs.subBg, "pb") { prefs.subBg = it }
                Text("El retardo solo cambia esta reproducción; el resto queda guardado.", color = Col.Sub, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }

        error?.let {
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(it, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                Text(
                    if (cur.isLive && channels.size > 1) "Prueba otro canal con ▲ ▼ o pulsa Atrás" else "Pulsa Atrás para volver",
                    color = Col.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
                if (!dims.tv) ActionButton("Volver", modifier = Modifier.padding(top = 12.dp)) { nav.pop() }
            }
        }
    }
}

/**
 * Reproductor ajustado para la mejor calidad posible sin sobrecargar la TV:
 * - Empieza con una estimación de ancho de banda alta (arranca en buena calidad en HLS adaptativo).
 * - Búfer moderado: arranque rápido y pocos cortes sin gastar memoria de más.
 * - Si un decodificador por hardware falla, prueba otro automáticamente.
 * - Audio y subtítulos en español por defecto si el contenido los tiene.
 * - Subtítulos con retardo ajustable (NoirRenderersFactory).
 */
@OptIn(UnstableApi::class)
private fun buildPlayer(ctx: Context, live: Boolean): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Http.UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
    val renderers = NoirRenderersFactory(ctx).setEnableDecoderFallback(true)
    val bandwidth = DefaultBandwidthMeter.Builder(ctx).setInitialBitrateEstimate(12_000_000L).build()
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            if (live) 8_000 else 15_000,
            if (live) 30_000 else 50_000,
            1_200,
            2_500,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    val audioAttrs = AudioAttributes.Builder()
        .setUsage(MC.USAGE_MEDIA)
        .setContentType(MC.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    val player = ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
        .setBandwidthMeter(bandwidth)
        .setLoadControl(load)
        .setAudioAttributes(audioAttrs, true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setPreferredAudioLanguages("es", "spa", "cat")
        .setPreferredTextLanguages("es", "spa")
        .setSelectUndeterminedTextLanguage(true)
        .build()
    player.videoScalingMode = MC.VIDEO_SCALING_MODE_SCALE_TO_FIT
    return player
}
