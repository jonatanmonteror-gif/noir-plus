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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
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
    var retries by remember(url) { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Directo: calidades del mismo canal (mejor primero). Si una falla se prueba la siguiente.
    val curKey = if (cur.isLive) (channel?.key ?: cur.key) else ""
    val qualities = remember(curKey) { if (cur.isLive) com.noirplus.tv.data.LiveIndex.alternatives(curKey) else emptyList() }
    var qIdx by remember(curKey) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var infoVisible by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var panelSection by remember { mutableStateOf("all") }
    // Secciones desplegadas dentro de la ruedita (todas recogidas al abrir)
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var speed by remember { mutableFloatStateOf(1f) }
    var tracksTick by remember { mutableIntStateOf(0) }
    fun openPanel(sec: String) {
        if (sec == "all") expanded.clear()
        panelSection = sec
        panelOpen = true
    }
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
            com.noirplus.tv.data.Sync.request(800)
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
            if (down) openPanel("all")
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
        if (!view.isControllerFullyVisible) {
            when (ev.keyCode) {
                // OK del centro: pausa / reanuda sin abrir los controles
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (down && ev.repeatCount == 0) {
                        if (exo.isPlaying) exo.pause() else exo.play()
                    }
                    return true
                }
                // Flecha arriba: elegir audio
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (down && ev.repeatCount == 0) openPanel("audio")
                    return true
                }
                // Flecha abajo: elegir subtítulos
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (down && ev.repeatCount == 0) openPanel("cc")
                    return true
                }
            }
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

    LaunchedEffect(speed) { exo.setPlaybackSpeed(speed) }

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
                com.noirplus.tv.data.ErrorLog.note("Reproducción", e, "Código: ${e.errorCodeName}")
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
                    // Después, la siguiente calidad del mismo canal
                    cur.isLive && src != null && qIdx + 1 < qualities.size -> {
                        qIdx += 1
                        triedAlt = false
                        url = src.streamUrl(qualities[qIdx])
                    }
                    // El servidor ha dicho que no (lleno, cuenta, conexiones): un reintento a los 3 s
                    httpCode(e) != null && retries < 1 -> {
                        retries += 1
                        scope.launch { kotlinx.coroutines.delay(3000); exo.prepare(); exo.playWhenReady = true }
                    }
                    else -> error = playError(e)
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                tracksTick++
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
                    // Barra de controles: nuestra ruedita (con todas las opciones) y los accesos rápidos elegidos en Ajustes
                    val gear = findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                    val ccBtn = findViewById<View>(androidx.media3.ui.R.id.exo_subtitle)
                    (gear?.parent as? android.view.ViewGroup)?.let { bar ->
                        if (ccBtn != null && ccBtn.parent === bar) bar.removeView(ccBtn)
                        var pos = bar.indexOfChild(gear).coerceAtLeast(0)
                        bar.removeView(gear)
                        fun add(icon: Int, desc: String, onTap: () -> Unit) {
                            val b = android.widget.ImageButton(c, null, 0, androidx.media3.ui.R.style.ExoStyledControls_Button_Bottom)
                            b.setImageResource(icon)
                            b.contentDescription = desc
                            b.setOnClickListener { onTap() }
                            bar.addView(b, pos.coerceAtMost(bar.childCount))
                            pos++
                        }
                        if (prefs.quick("audio")) add(com.noirplus.tv.R.drawable.ic_audio, "Audio") { openPanel("audio") }
                        if (prefs.quick("cc")) add(com.noirplus.tv.R.drawable.ic_cc, "Subtítulos") { openPanel("cc") }
                        if (prefs.quick("subopts")) add(com.noirplus.tv.R.drawable.ic_subtitle_tune, "Opciones de subtítulos") { openPanel("subopts") }
                        if (prefs.quick("speed")) add(com.noirplus.tv.R.drawable.ic_speed, "Velocidad") { openPanel("speed") }
                        if (prefs.quick("format")) add(com.noirplus.tv.R.drawable.ic_aspect, "Formato de imagen") {
                            resize = (resize + 1) % ResizeModes.size
                            formatToast++
                        }
                        // La ruedita solo aparece si queda algo dentro (lo que es botón no se repite en ella)
                        if (QuickKeys.any { !prefs.quick(it) }) add(com.noirplus.tv.R.drawable.ic_gear, "Opciones") { openPanel("all") }
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

        // Panel de opciones (compacto): todo desde la ruedita, o una sección desde su acceso rápido
        if (panelOpen) {
            LaunchedEffect(panelSection) {
                delay(120)
                runCatching { panelFr.requestFocus() }
            }
            val sec = panelSection
            val all = sec == "all"
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { panelOpen = false } })
            Column(
                Modifier.align(Alignment.CenterEnd).padding(14.dp).width(if (dims.tv) 340.dp else 300.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Col.Surface.copy(alpha = 0.96f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    when (sec) {
                        "cc" -> "Subtítulos"
                        "subopts" -> "Opciones de subtítulos"
                        "audio" -> "Audio"
                        "speed" -> "Velocidad"
                        "format" -> "Formato de imagen"
                        "quality" -> "Calidad"
                        else -> "Opciones"
                    },
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                var first = true
                fun fr(): FocusRequester? = if (first) { first = false; panelFr } else null
                // En la ruedita: solo lo que NO es botón, cada cosa en una sección que se despliega.
                // Desde un botón: directamente esa sección abierta.
                @Composable
                fun section(key: String, label: String): Boolean {
                    if (!all) return sec == key
                    if (prefs.quick(key)) return false
                    val open = expanded[key] == true
                    PanelHeader(label, open, fr()) { expanded[key] = !open }
                    return open
                }
                if (all && QuickKeys.all { prefs.quick(it) }) {
                    Text("Todo está como botón en pantalla. Puedes cambiarlo en Ajustes → Reproductor.", color = Col.Sub, fontSize = 12.sp)
                }
                if (section("audio", "Audio")) {
                    TrackChoices(exo, MC.TRACK_TYPE_AUDIO, tracksTick, allowOff = false, focus = fr())
                }
                if (section("cc", "Subtítulos")) {
                    TrackChoices(exo, MC.TRACK_TYPE_TEXT, tracksTick, allowOff = true, focus = fr())
                }
                if (section("subopts", "Opciones de subtítulos")) {
                    Text("Retardo", color = Col.Sub, fontSize = 12.sp)
                    MiniStepper(fmtDelay(sessionDelay), "pd", { sessionDelay = (sessionDelay - 100).coerceAtLeast(-2000) },
                        { sessionDelay = (sessionDelay + 100).coerceAtMost(2000) }, focus = fr())
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
                }
                if (section("speed", "Velocidad")) {
                    MiniStepper(String.format(java.util.Locale("es", "ES"), "%.1f×", speed), "sp",
                        { speed = ((speed * 10f).roundToInt() - 1).coerceAtLeast(5) / 10f },
                        { speed = ((speed * 10f).roundToInt() + 1).coerceAtMost(20) / 10f }, focus = fr())
                }
                if (section("format", "Formato de imagen")) {
                    Segmented(ResizeModes.map { it.second }, resize, "pr") { resize = it }
                }
                // Directo con varias calidades del mismo canal
                if (qualities.size > 1 && all) {
                    val open = expanded["quality"] == true
                    PanelHeader("Calidad", open, fr()) { expanded["quality"] = !open }
                    if (open) Segmented(qualities.map { qualityName(it.name) }, qIdx, "pq") { i ->
                        if (src != null && i != qIdx) { qIdx = i; triedAlt = false; url = src.streamUrl(qualities[i]) }
                    }
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

/** Opciones que pueden ir como botón en pantalla o dentro de la ruedita. */
val QuickKeys = listOf("audio", "cc", "subopts", "speed", "format")

/** Cabecera de sección de la ruedita: se pulsa para desplegar o recoger. */
@Composable
private fun PanelHeader(label: String, open: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Focusable(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), focusScale = 1f, focusRequester = focus, onClick = onClick) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) Color.White else Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (f) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("›", color = if (f) Color.Black else Col.Sub, fontSize = 18.sp, modifier = Modifier.graphicsLayer { rotationZ = rot })
        }
    }
}

@Composable
private fun PanelLabel(t: String) {
    Text(t.uppercase(), color = Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 6.dp))
}

/** Nombre legible de una pista: etiqueta, idioma y canales (5.1…). */
private fun trackName(f: androidx.media3.common.Format, i: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let {
        java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale("es", "ES")).replaceFirstChar { c -> c.titlecase() }
    }
    val label = f.label?.takeIf { it.isNotBlank() }
    val base = listOfNotNull(label, lang?.takeIf { label == null || !label.contains(it, true) }).joinToString(" · ").ifBlank { "Pista ${i + 1}" }
    val ch = when (f.channelCount) {
        6 -> " (5.1)"
        8 -> " (7.1)"
        2 -> ""
        else -> ""
    }
    return base + ch
}

/** Lista de pistas (subtítulos o audio) del vídeo actual, con la elegida marcada. */
@OptIn(UnstableApi::class)
@Composable
private fun TrackChoices(exo: ExoPlayer, type: Int, tick: Int, allowOff: Boolean, focus: FocusRequester?) {
    val groups = remember(tick) { exo.currentTracks.groups.filter { it.type == type } }
    val disabled = remember(tick) { exo.trackSelectionParameters.disabledTrackTypes.contains(type) }
    val anySelected = groups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
    val options = buildList {
        groups.forEach { g -> for (i in 0 until g.length) if (g.isTrackSupported(i)) add(Triple(g, i, trackName(g.getTrackFormat(i), size))) }
    }
    if (options.isEmpty()) {
        Text(if (type == MC.TRACK_TYPE_TEXT) "Este vídeo no trae subtítulos." else "Solo hay una pista de audio.", color = Col.Sub, fontSize = 12.sp)
        return
    }
    var first = focus
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (allowOff) {
            TrackRow("Desactivados", selected = disabled || !anySelected, focus = first.also { first = null }) {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, true).build()
            }
        }
        options.forEach { (g, i, name) ->
            TrackRow(name, selected = !disabled && g.isTrackSelected(i), focus = first.also { first = null }) {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(type, false)
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i))
                    .build()
            }
        }
    }
}

@Composable
private fun TrackRow(name: String, selected: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    Focusable(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), focusScale = 1f, focusRequester = focus, onClick = onClick) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) Color.White else Color.Transparent).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (selected) "✓" else "", color = if (f) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(20.dp))
            Text(name, color = if (f) Color.Black else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


/** Nombre corto de la calidad de un canal (por su nombre en la lista). */
private fun qualityName(name: String): String {
    val n = name.lowercase()
    return when {
        Regex("""(?<![a-z\d])(4k|uhd)(?![a-z\d])|ᵁᴴᴰ""").containsMatchIn(n) -> "4K"
        Regex("""(?<![a-z\d])(fhd|1080[pi]?)(?![a-z\d])|ᶠᴴᴰ""").containsMatchIn(n) -> "FHD"
        Regex("""(?<![a-z\d])(sd|576p|low)(?![a-z\d])|ˢᴰ""").containsMatchIn(n) -> "SD"
        else -> "HD"
    }
}


/** Código HTTP que devolvió el servidor, si el fallo es de ese tipo. */
private fun httpCode(e: PlaybackException): Int? {
    var c: Throwable? = e
    while (c != null) {
        if (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return c.responseCode
        c = c.cause
    }
    return null
}

/** Mensaje entendible para el error de reproducción. */
private fun playError(e: PlaybackException): String {
    val code = httpCode(e) ?: return "No se ha podido reproducir este contenido.\n(${e.errorCodeName})"
    val why = when (code) {
        401, 403 -> "El proveedor rechaza la cuenta (caducada, bloqueada o en uso en otro sitio)."
        404, 410 -> "El proveedor ya no tiene este contenido."
        429, 456, 458, 509 -> "Has llegado al máximo de conexiones a la vez. Cierra la app en otro aparato y vuelve a probar."
        in 500..599 -> "El servidor del proveedor está fallando ahora mismo. Prueba en unos minutos."
        else -> "El servidor del proveedor no deja reproducirlo."
    }
    return "$why\n(Error $code)"
}
