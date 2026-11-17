package com.noirplus.tv.ui

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import java.util.Locale

/** Retardo de subtítulos activo (µs). Positivo = salen más tarde. */
object SubtitleClock {
    @Volatile
    var delayUs: Long = 0L
}

/**
 * Envuelve el renderizador de subtítulos y le da una "hora" desplazada:
 * así los subtítulos (incrustados o externos) salen antes o después que la imagen.
 */
@UnstableApi
class DelayedTextRenderer(private val inner: Renderer) : Renderer by inner {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        inner.render(positionUs - SubtitleClock.delayUs, elapsedRealtimeUs)
    }
}

@OptIn(UnstableApi::class)
class NoirRenderersFactory(ctx: Context) : DefaultRenderersFactory(ctx) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val tmp = ArrayList<Renderer>()
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, tmp)
        tmp.forEach { out.add(DelayedTextRenderer(it)) }
    }
}

fun fmtDelay(ms: Int): String =
    (if (ms > 0) "+" else if (ms < 0) "−" else "") + String.format(Locale("es", "ES"), "%.1f s", kotlin.math.abs(ms) / 1000f)

val SubPositions = listOf("Abajo", "Centro", "Arriba")
val SubFontNames = listOf("Inter", "Sistema", "Clásica", "Playfair", "Mono")
fun subFontFamily(i: Int): androidx.compose.ui.text.font.FontFamily = when (i) {
    1 -> androidx.compose.ui.text.font.FontFamily.SansSerif
    2 -> androidx.compose.ui.text.font.FontFamily.Serif
    3 -> Playfair
    4 -> androidx.compose.ui.text.font.FontFamily.Monospace
    else -> Inter
}
val SubBgs = listOf("Sombra", "Caja")

/** Subtítulos dibujados por la app: posición, altura, tamaño y fondo configurables. */
@Composable
fun BoxScope.SubtitleLayer(cues: List<Cue>, pos: Int, offsetDp: Int, sizeSp: Int, box: Boolean, font: Int = 0, preview: Boolean = false) {
    if (cues.isEmpty()) return
    val fs = if (preview) (sizeSp * 0.7f).toInt() else sizeSp
    val margin = if (preview) 10 else 34
    val align = when (pos) {
        2 -> Alignment.TopCenter
        1 -> Alignment.Center
        else -> Alignment.BottomCenter
    }
    val mod = when (pos) {
        2 -> Modifier.padding(top = (margin - offsetDp).coerceAtLeast(0).dp)
        1 -> Modifier.offset(y = (-offsetDp).dp)
        else -> Modifier.padding(bottom = (margin + offsetDp).coerceAtLeast(0).dp)
    }
    Column(
        Modifier.align(align).then(mod).fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        cues.forEach { cue ->
            val bmp = cue.bitmap
            val txt = cue.text?.toString()?.trim()
            when {
                bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(if (cue.size > 0f && cue.size <= 1f) cue.size else 0.6f))
                !txt.isNullOrEmpty() -> Text(
                    txt,
                    Modifier
                        .widthIn(max = 900.dp)
                        .then(if (box) Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 10.dp, vertical = 3.dp) else Modifier),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = fs.sp,
                        lineHeight = (fs * 1.25f).sp,
                        fontFamily = subFontFamily(font),
                        fontWeight = if (font == 4) FontWeight.Normal else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        shadow = if (box) null else Shadow(Color.Black.copy(alpha = 0.95f), Offset(0f, 2f), 8f),
                    ),
                )
            }
        }
    }
}

/** Vista previa para Ajustes → Subtítulos. */
@Composable
fun SubtitlePreview(pos: Int, offsetDp: Int, sizeSp: Int, box: Boolean, font: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2B3440), Color(0xFF1A1A1F), Color(0xFF3A2E28)))
        )
    ) {
        Text("Vista previa", Modifier.align(Alignment.TopStart).padding(8.dp), color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
        val cue = Cue.Builder().setText("Así se verán los subtítulos.").build()
        SubtitleLayer(listOf(cue), pos, offsetDp, sizeSp, box, font, preview = true)
    }
}

/** Fila de ajuste: en móvil el control va debajo del título; en TV/tablet, a la derecha. */
@Composable
fun SettingRow(label: String, note: String? = null, control: @Composable () -> Unit) {
    val compact = LocalDims.current.compact
    if (compact) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            control()
            note?.let { Text(it, color = Col.Sub, fontSize = 11.sp, lineHeight = 15.sp) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(label, color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                note?.let { Text(it, color = Col.Sub, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
            Box(Modifier.widthIn(max = 340.dp)) { control() }
        }
    }
}

/** Control segmentado (tipo iOS): opciones excluyentes en una cápsula. */
@Composable
fun Segmented(options: List<String>, selected: Int, keyPrefix: String, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Col.Bg.copy(alpha = 0.55f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Focusable(Modifier.weight(1f), key = "$keyPrefix-$i", shape = RoundedCornerShape(8.dp), focusScale = 1f,
                onClick = { onSelect(i) }) { focused ->
                Text(
                    o,
                    Modifier.fillMaxWidth()
                        .background(
                            when {
                                sel -> Color.White
                                focused -> Color.White.copy(alpha = 0.22f)
                                else -> Color.Transparent
                            }
                        )
                        .padding(vertical = 7.dp),
                    color = if (sel) Color.Black else Col.Text,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** − valor + compacto. */
@Composable
fun MiniStepper(value: String, keyPrefix: String, onMinus: () -> Unit, onPlus: () -> Unit, focus: androidx.compose.ui.focus.FocusRequester? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Col.Bg.copy(alpha = 0.55f)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepBtn("−", "$keyPrefix-minus", onMinus, focus)
        Text(value, color = Col.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 70.dp).padding(horizontal = 6.dp))
        StepBtn("+", "$keyPrefix-plus", onPlus)
    }
}

@Composable
private fun StepBtn(t: String, key: String, onClick: () -> Unit, focus: androidx.compose.ui.focus.FocusRequester? = null) {
    Focusable(key = key, shape = RoundedCornerShape(8.dp), focusScale = 1f, focusRequester = focus, onClick = onClick) { focused ->
        Text(t, Modifier.background(if (focused) Color.White else Color.White.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 5.dp),
            color = if (focused) Color.Black else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, keyPrefix: String, note: String? = null) {
    SettingRow(label, note) { MiniStepper(value, keyPrefix, onMinus, onPlus) }
}

@Composable
fun ChoiceRow(label: String, options: List<String>, selected: Int, keyPrefix: String, note: String? = null, onSelect: (Int) -> Unit) {
    SettingRow(label, note) { Segmented(options, selected, keyPrefix, onSelect) }
}

/** Tipografías de subtítulos: cada opción se muestra con su propia letra. */
@Composable
fun FontPicker(selected: Int, keyPrefix: String, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SubFontNames.forEachIndexed { i, n ->
            val sel = i == selected
            Focusable(key = "$keyPrefix-$i", shape = RoundedCornerShape(8.dp), focusScale = 1.04f, onClick = { onSelect(i) }) { f ->
                Text(
                    n,
                    Modifier.background(
                        when {
                            sel -> Color.White
                            f -> Color.White.copy(alpha = 0.22f)
                            else -> Color.White.copy(alpha = 0.08f)
                        }
                    ).padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (sel) Color.Black else Col.Text,
                    fontFamily = subFontFamily(i),
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
