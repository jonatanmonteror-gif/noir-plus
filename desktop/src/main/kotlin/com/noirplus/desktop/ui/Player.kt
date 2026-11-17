package com.noirplus.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import com.noirplus.desktop.LocalWindowState
import com.noirplus.desktop.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

/** Opciones de VLC, incluido el estilo de los subtítulos elegido en Ajustes. */
fun vlcArgs(): List<String> {
    val a = mutableListOf("--no-video-title-show", "--quiet", "--avcodec-hw=any",
        "--sub-text-scale=${Store.subScale}", "--freetype-font=${Store.subFont}", "--freetype-color=${Store.subColor}",
        "--freetype-outline-thickness=${listOf(0, 2, 4, 6)[Store.subOutline]}", "--sub-margin=${Store.subMargin}")
    when (Store.subBg) {
        2 -> a += listOf("--freetype-background-opacity=160", "--freetype-background-color=0", "--freetype-shadow-opacity=0")
        1 -> a += listOf("--freetype-background-opacity=0", "--freetype-shadow-opacity=200")
        else -> a += listOf("--freetype-background-opacity=0", "--freetype-shadow-opacity=0")
    }
    return a
}

/** ¿Hay VLC instalado? Se comprueba una sola vez. */
object Vlc {
    val available: Boolean by lazy { runCatching { NativeDiscovery().discover() }.getOrDefault(false) }
}

/**
 * Motor de vídeo: VLC dibuja cada fotograma en memoria y Compose lo pinta.
 * Así los controles pueden ir encima del vídeo sin parpadeos.
 */
class VlcVideo {
    val factory = MediaPlayerFactory(*vlcArgs().toTypedArray())
    val mp: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
    val frame = mutableStateOf<ImageBitmap?>(null)

    @Volatile private var bitmap: Bitmap? = null
    @Volatile private var bytes: ByteArray? = null

    init {
        val format = object : BufferFormatCallbackAdapter() {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                bytes = ByteArray(sourceWidth * sourceHeight * 4)
                bitmap = Bitmap().apply { allocPixels(ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)) }
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }
        }
        val render = RenderCallback { _, buffers, fmt ->
            val bb = bytes ?: return@RenderCallback
            val bmp = bitmap ?: return@RenderCallback
            val src = buffers[0]
            src.rewind()
            src.get(bb, 0, minOf(bb.size, src.remaining()))
            bmp.installPixels(bmp.imageInfo, bb, fmt.width * 4)
            frame.value = bmp.asComposeImageBitmap()
        }
        mp.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
    }

    fun release() {
        runCatching { mp.controls().stop() }
        runCatching { mp.release() }
        runCatching { factory.release() }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(start: PlayRequest) {
    val nav = LocalNav.current
    val window = LocalWindowState.current
    val base = if (com.noirplus.desktop.LocalWindowMode.current == "immersive") WindowPlacement.Fullscreen else WindowPlacement.Maximized
    if (!Vlc.available) return NoVlc { nav.pop() }

    var req by remember { mutableStateOf(start) }
    // Si cambias el estilo de los subtítulos, se prepara el reproductor de nuevo y sigue donde estaba
    var styleKey by remember { mutableStateOf(Store.subStyleKey) }
    var resumeAt by remember { mutableLongStateOf(start.startMs) }
    var pendingSubTrack by remember { mutableStateOf<Int?>(null) }
    val video = remember(styleKey) { VlcVideo() }
    val scope = rememberCoroutineScope()

    var playing by remember { mutableStateOf(true) }
    var time by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(Store.volume) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menu by remember { mutableStateOf<String?>(null) } // "all" (ruedita) o una opción concreta
    var speed by remember { mutableFloatStateOf(1f) }
    var format by remember { mutableIntStateOf(0) } // 0 ajustar, 1 rellenar, 2 estirar
    var toast by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }

    fun saveProgress(notify: Boolean = false) {
        val r = req
        if (r.isLive || length <= 0 || time < 5000) return
        Store.saveProgress(Progress(r.key, r.title, r.image, r.url, time, length, System.currentTimeMillis(), r.parent), notify)
    }

    fun load(r: PlayRequest) {
        error = null; ended = false; buffering = true; countdown = null; time = 0; length = 0
        val opts = mutableListOf(":http-user-agent=${Http.UA}", ":network-caching=${if (r.isLive) 3000 else 1500}")
        if (r.startMs > 0) opts += ":start-time=${r.startMs / 1000}"
        scope.launch(Dispatchers.IO) { video.mp.media().play(r.url, *opts.toTypedArray()) }
    }

    fun hasNext() = req.episodes.isNotEmpty() && req.episodeIndex in 0 until req.episodes.lastIndex

    fun playNext() {
        val r = req
        val src = Repo.source ?: return
        val next = r.episodes.getOrNull(r.episodeIndex + 1) ?: return
        saveProgress()
        val nr = r.copy(url = src.episodeUrl(next), title = "${r.seriesName ?: ""} · T${next.season} E${next.number}",
            key = "EP:${next.id}", image = next.image ?: r.image, startMs = 0, episodeIndex = r.episodeIndex + 1)
        req = nr
        load(nr)
    }

    fun zap(delta: Int) {
        val r = req
        if (!r.isLive || r.channels.isEmpty()) return
        val i = (r.channelIndex + delta).mod(r.channels.size)
        val ch = r.channels[i]
        val nr = r.copy(url = Repo.source?.streamUrl(ch) ?: return, title = ch.name, key = ch.key, image = ch.image, channelIndex = i)
        req = nr
        load(nr)
        toast = ch.name
    }

    fun exit() {
        saveProgress(notify = true)
        window.placement = base
        nav.pop()
    }

    DisposableEffect(video) {
        var firstPlay = true
        video.mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                buffering = false; playing = true
                if (firstPlay) {
                    firstPlay = false
                    val track = pendingSubTrack
                    pendingSubTrack = null
                    Thread {
                        runCatching {
                            if (track != null) mediaPlayer.subpictures().setTrack(track)
                            if (Store.subDelayMs != 0) mediaPlayer.subpictures().setDelay(Store.subDelayMs * 1000L)
                        }
                    }.start()
                }
            }
            override fun paused(mediaPlayer: MediaPlayer) { playing = false }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) { buffering = newCache < 100f }
            override fun finished(mediaPlayer: MediaPlayer) { ended = true }
            override fun error(mediaPlayer: MediaPlayer) {
                error = if (req.isLive) "No se pudo abrir el canal. Prueba a cambiar el formato de directo en Ajustes."
                else "No se pudo reproducir este contenido."
            }
        })
        video.mp.audio().setVolume(volume)
        load(req.copy(startMs = resumeAt))
        onDispose {
            Thread { video.release() }.start()
        }
    }

    // Reloj del reproductor
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(250)
            runCatching {
                time = video.mp.status().time()
                length = video.mp.status().length()
            }
            if (++n % 20 == 0) saveProgress()
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    // Al acabar: siguiente episodio con cuenta atrás
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        if (req.isLive) return@LaunchedEffect
        Store.removeProgress(req.key)
        if (hasNext()) {
            for (s in 8 downTo 1) { countdown = s; delay(1000) }
            countdown = null
            playNext()
        } else exit()
    }

    fun togglePause() { scope.launch(Dispatchers.IO) { video.mp.controls().pause() } }
    fun seekBy(ms: Long) { if (!req.isLive) scope.launch(Dispatchers.IO) { video.mp.controls().skipTime(ms) }; toast = if (ms > 0) "+${ms / 1000} s" else "${ms / 1000} s" }
    fun setVol(v: Int) { volume = v.coerceIn(0, 150); video.mp.audio().setVolume(volume); Store.volume = volume; toast = "Volumen $volume%" }
    fun toggleFull() {
        window.placement = if (window.placement == WindowPlacement.Fullscreen) base else WindowPlacement.Fullscreen
    }

    KeyHandler { e ->
        if (e.type != KeyEventType.KeyDown) return@KeyHandler false
        lastMove = System.currentTimeMillis()
        when (e.key) {
            Key.Spacebar, Key.K -> { togglePause(); true }
            Key.DirectionRight -> { if (req.isLive) zap(1) else seekBy(10_000); true }
            Key.DirectionLeft -> { if (req.isLive) zap(-1) else seekBy(-10_000); true }
            Key.PageUp -> { zap(-1); true }
            Key.PageDown -> { zap(1); true }
            Key.DirectionUp -> { setVol(volume + 5); true }
            Key.DirectionDown -> { setVol(volume - 5); true }
            Key.F -> { toggleFull(); true }
            Key.M -> { video.mp.audio().setMute(!video.mp.audio().isMute); toast = if (video.mp.audio().isMute) "Silencio" else "Sonido"; true }
            Key.S -> { menu = if (menu == "cc") null else "cc"; true }
            Key.A -> { menu = if (menu == "audio") null else "audio"; true }
            Key.O -> { menu = if (menu == "all") null else "all"; true }
            Key.N -> { if (hasNext()) playNext(); true }
            Key.Escape, Key.Backspace -> {
                when {
                    menu != null -> menu = null
                    window.placement == WindowPlacement.Fullscreen && base != WindowPlacement.Fullscreen -> window.placement = base
                    else -> exit()
                }
                true
            }
            else -> false
        }
    }

    val now by produceState(System.currentTimeMillis()) { while (true) { delay(300); value = System.currentTimeMillis() } }
    val showControls = menu != null || !playing || error != null || now - lastMove < 3000

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPointerEvent(PointerEventType.Move) { lastMove = System.currentTimeMillis() }
            .pointerHoverIcon(if (showControls) PointerIcon.Default else PointerIcon(java.awt.Toolkit.getDefaultToolkit()
                .createCustomCursor(java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), java.awt.Point(0, 0), "none"))),
    ) {
        video.frame.value?.let { img ->
            Image(img, null, contentScale = when (format) { 1 -> ContentScale.Crop; 2 -> ContentScale.FillBounds; else -> ContentScale.Fit }, modifier = Modifier.fillMaxSize()
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    if (menu != null) menu = null else togglePause()
                })
        }
        if (buffering && error == null) Loading()
        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = Col.Text, fontSize = 16.sp)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NButton("Reintentar", primary = true) { load(req) }
                    NButton("Volver") { exit() }
                }
            }
        }

        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Arriba: volver + título
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") { exit() }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(req.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (req.isLive) Text("EN DIRECTO  ·  ← → cambiar de canal", color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                // Abajo: barra de tiempo y botones
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 18.dp),
                ) {
                    if (!req.isLive && length > 0) {
                        var dragging by remember { mutableStateOf<Float?>(null) }
                        Slider(
                            value = dragging ?: (time.toFloat() / length).coerceIn(0f, 1f),
                            onValueChange = { dragging = it; lastMove = System.currentTimeMillis() },
                            onValueChangeFinished = {
                                dragging?.let { f -> scope.launch(Dispatchers.IO) { video.mp.controls().setTime((f * length).toLong()) } }
                                dragging = null
                            },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(fmtTime(((dragging ?: -1f).takeIf { it >= 0 }?.times(length)?.toLong()) ?: time), color = Col.Text, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text("-" + fmtTime(length - time), color = Col.Sub, fontSize = 13.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!req.isLive) RoundIcon(Icons.Filled.Replay10, "−10 s") { seekBy(-10_000) }
                        else RoundIcon(Icons.Filled.SkipPrevious, "Canal anterior") { zap(-1) }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White).clickable { togglePause() }
                            .pointerHoverIcon(PointerIcon.Hand), contentAlignment = Alignment.Center) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        if (!req.isLive) RoundIcon(Icons.Filled.Forward10, "+10 s") { seekBy(10_000) }
                        else RoundIcon(Icons.Filled.SkipNext, "Canal siguiente") { zap(1) }
                        if (hasNext()) { Spacer(Modifier.width(10.dp)); RoundIcon(Icons.Filled.SkipNext, "Siguiente episodio") { playNext() } }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Col.Sub, modifier = Modifier.size(20.dp))
                        Slider(volume.toFloat(), { setVol(it.toInt()) }, valueRange = 0f..150f, modifier = Modifier.width(140.dp).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)))
                        // Botones elegidos en Ajustes; lo demás va dentro de la ruedita (nunca en los dos sitios)
                        val changes = Store.changes.intValue
                        val quick = remember(changes) { QuickOptions.map { it.first }.filter { Store.quick(it) } }
                        quick.forEach { k ->
                            when (k) {
                                "audio" -> RoundIcon(Icons.Filled.Audiotrack, "Audio (A)") { menu = if (menu == "audio") null else "audio" }
                                "cc" -> RoundIcon(Icons.Filled.ClosedCaption, "Subtítulos (S)") { menu = if (menu == "cc") null else "cc" }
                                "subopts" -> RoundIcon(Icons.Filled.Subtitles, "Opciones de subtítulos") { menu = if (menu == "subopts") null else "subopts" }
                                "speed" -> RoundIcon(Icons.Filled.Speed, "Velocidad") { menu = if (menu == "speed") null else "speed" }
                                "format" -> RoundIcon(Icons.Filled.AspectRatio, "Formato de imagen") {
                                    format = (format + 1) % 3
                                    toast = FormatNames[format]
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (quick.size < QuickOptions.size) {
                            RoundIcon(Icons.Filled.Settings, "Opciones (O)") { menu = if (menu == "all") null else "all" }
                            Spacer(Modifier.width(8.dp))
                        }
                        RoundIcon(if (window.placement == WindowPlacement.Fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            "Pantalla completa (F)") { toggleFull() }
                    }
                }
                menu?.let { m ->
                    OptionsPanel(m, video, speed, { speed = it; video.mp.controls().setRate(it) }, format, { format = it },
                        onStyle = {
                            // Reaplicar el estilo: mismo punto, misma pista de subtítulos
                            resumeAt = if (req.isLive) 0 else time
                            pendingSubTrack = runCatching { video.mp.subpictures().track() }.getOrNull()
                            styleKey = Store.subStyleKey
                        },
                        Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 110.dp))
                }
            }
        }

        toast?.let {
            Text(it, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 18.dp, vertical = 8.dp))
        }
        countdown?.let { s ->
            Column(Modifier.align(Alignment.BottomEnd).padding(36.dp).clip(RoundedCornerShape(14.dp)).background(Col.Surface2).padding(18.dp)) {
                Text("Siguiente episodio en $s s", color = Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NButton("Ver ahora", primary = true) { countdown = null; playNext() }
                    NButton("Salir") { countdown = null; exit() }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    TooltipArea(desc) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onClick() }
            .pointerHoverIcon(PointerIcon.Hand), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TooltipArea(text: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Text(text, color = Color.Black, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp))
        },
        delayMillis = 500,
        content = content,
    )
}

/** Opciones que pueden ir como botón en pantalla o dentro de la ruedita. */
val QuickOptions = listOf(
    "audio" to "Audio (idioma)",
    "cc" to "Subtítulos (elegir pista)",
    "subopts" to "Opciones de subtítulos",
    "speed" to "Velocidad",
    "format" to "Formato de imagen",
)

val FormatNames = listOf("Ajustar", "Rellenar", "Estirar")

/**
 * Panel de opciones. Desde la ruedita ("all"): solo lo que no es botón, en secciones que se despliegan.
 * Desde un botón: directamente esa opción.
 */
@Composable
private fun OptionsPanel(
    sec: String, video: VlcVideo, speed: Float, onSpeed: (Float) -> Unit, format: Int, onFormat: (Int) -> Unit,
    onStyle: () -> Unit, modifier: Modifier,
) {
    val all = sec == "all"
    val changes = Store.changes.intValue
    val expanded = remember(sec) { mutableStateMapOf<String, Boolean>() }
    val keys = remember(sec, changes) { if (all) QuickOptions.filter { !Store.quick(it.first) } else QuickOptions.filter { it.first == sec } }
    Column(
        modifier.width(340.dp).heightIn(max = 560.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF0161618))
            .verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (all) "Opciones" else keys.firstOrNull()?.second ?: "", color = Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        if (keys.isEmpty()) Text("Todo está como botón en pantalla. Puedes cambiarlo en Ajustes.", color = Col.Sub, fontSize = 13.sp,
            modifier = Modifier.padding(8.dp))
        keys.forEach { (k, label) ->
            val open = !all || expanded[k] == true
            if (all) SectionHeader(label, open) { expanded[k] = !open }
            if (open) Column(Modifier.padding(start = if (all) 6.dp else 0.dp, bottom = 4.dp)) {
                when (k) {
                    "audio" -> Tracks(video, audio = true)
                    "cc" -> Tracks(video, audio = false)
                    "subopts" -> SubOptions(video, onStyle)
                    "speed" -> Stepper(String.format(java.util.Locale("es", "ES"), "%.1f×", speed),
                        { onSpeed(((speed * 10).roundToInt() - 1).coerceAtLeast(5) / 10f) },
                        { onSpeed(((speed * 10).roundToInt() + 1).coerceAtMost(20) / 10f) })
                    "format" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(4.dp)) {
                        FormatNames.forEachIndexed { i, n -> Chip(n, i == format) { onFormat(i) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, open: Boolean, onClick: () -> Unit) {
    val rot by animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("›", color = Col.Sub, fontSize = 18.sp, modifier = Modifier.graphicsLayer { rotationZ = rot })
    }
}

@Composable
private fun Tracks(video: VlcVideo, audio: Boolean) {
    var refresh by remember { mutableIntStateOf(0) }
    val tracks: List<TrackDescription> = remember(refresh) {
        runCatching { if (audio) video.mp.audio().trackDescriptions() else video.mp.subpictures().trackDescriptions() }.getOrDefault(emptyList())
    }
    val current = remember(refresh) { runCatching { if (audio) video.mp.audio().track() else video.mp.subpictures().track() }.getOrDefault(-1) }
    if (tracks.isEmpty()) Text("No hay pistas disponibles", color = Col.Sub, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
    tracks.forEach { t ->
        val sel = t.id() == current
        val label = if (t.id() < 0) "Desactivados" else t.description().ifBlank { "Pista ${t.id()}" }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (sel) Color.White else Color.Transparent)
            .clickable {
                if (audio) video.mp.audio().setTrack(t.id()) else video.mp.subpictures().setTrack(t.id())
                refresh++
            }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, color = if (sel) Color.Black else Col.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SubOptions(video: VlcVideo, onStyle: () -> Unit) {
    val ch = Store.changes.intValue
    var delayMs by remember { mutableLongStateOf(runCatching { video.mp.subpictures().delay() / 1000 }.getOrDefault(0L)) }
    Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Retardo", color = Col.Text, fontSize = 13.sp, modifier = Modifier.width(90.dp))
            Stepper(String.format("%+.1f s", delayMs / 1000f),
                { delayMs = (delayMs - 100).coerceIn(-5000, 5000); video.mp.subpictures().setDelay(delayMs * 1000) },
                { delayMs = (delayMs + 100).coerceIn(-5000, 5000); video.mp.subpictures().setDelay(delayMs * 1000) })
        }
        SubStyleControls(ch, compact = true, onStyle)
    }
}

/** Controles del estilo de subtítulos (en Ajustes y en el reproductor). */
@Composable
fun SubStyleControls(ch: Int, compact: Boolean, onChange: () -> Unit) {
    @Composable
    fun line(label: String, content: @Composable RowScope.() -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Col.Text, fontSize = 13.sp, modifier = Modifier.width(if (compact) 90.dp else 160.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
    // key(ch): se vuelve a dibujar todo al cambiar algo, para que se vea marcado al momento
    key(ch) {
    line("Tamaño") {
        listOf(70 to "S", 100 to "M", 130 to "L", 160 to "XL").forEach { (v, l) ->
            Chip(l, Store.subScale == v) { Store.subScale = v; onChange() }
        }
    }
    line("Altura") {
        Stepper("${Store.subMargin}", { Store.subMargin -= 20; onChange() }, { Store.subMargin += 20; onChange() })
    }
    line("Fondo") {
        listOf("Nada", "Sombra", "Caja").forEachIndexed { i, l -> Chip(l, Store.subBg == i) { Store.subBg = i; onChange() } }
    }
    line("Color") {
        listOf(0xFFFFFF to "Blanco", 0xFFE14D to "Amarillo").forEach { (c, l) -> Chip(l, Store.subColor == c) { Store.subColor = c; onChange() } }
    }
    line("Contorno") {
        listOf("No", "Fino", "Normal", "Grueso").forEachIndexed { i, l -> Chip(l, Store.subOutline == i) { Store.subOutline = i; onChange() } }
    }
    line("Letra") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Segoe UI", "Arial", "Verdana", "Tahoma", "Georgia").chunked(3).forEach { rowFonts ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowFonts.forEach { f -> Chip(f, Store.subFont == f) { Store.subFont = f; onChange() } }
                }
            }
        }
    }}
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        Chip("−", false, onMinus)
        Text(value, color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
        Chip("+", false, onPlus)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(text, color = if (selected) Color.Black else Col.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun NoVlc(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Col.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(520.dp)) {
            Text("Falta VLC", color = Col.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Noir+ usa VLC para reproducir vídeo. Instala VLC (versión de 64 bits) y vuelve a abrir Noir+.",
                color = Col.Sub, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton("Descargar VLC", primary = true) {
                    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://www.videolan.org/vlc/")) }
                }
                NButton("Volver") { onBack() }
            }
        }
    }
}
