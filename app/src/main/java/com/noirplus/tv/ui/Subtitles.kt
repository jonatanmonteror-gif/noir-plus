package com.noirplus.tv.ui

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
val SubSizes = listOf("Pequeño", "Normal", "Grande")
val SubBgs = listOf("Sombra", "Caja")

/** Subtítulos dibujados por la app: posición, altura, tamaño y fondo configurables. */
@Composable
fun BoxScope.SubtitleLayer(cues: List<Cue>, pos: Int, offsetDp: Int, size: Int, box: Boolean, preview: Boolean = false) {
    if (cues.isEmpty()) return
    val base = if (preview) 14 else 20
    val fs = when (size) { 0 -> base - 4; 2 -> base + 6; else -> base }
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
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
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
fun SubtitlePreview(pos: Int, offsetDp: Int, size: Int, box: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2B3440), Color(0xFF1A1A1F), Color(0xFF3A2E28)))
        )
    ) {
        Text("Vista previa", Modifier.align(Alignment.TopStart).padding(8.dp), color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
        val cue = Cue.Builder().setText("Así se verán los subtítulos.").build()
        SubtitleLayer(listOf(cue), pos, offsetDp, size, box, preview = true)
    }
}

/** Fila de ajuste con − valor + (para mando y táctil). */
@Composable
fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, keyPrefix: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Col.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        ActionButton("−", key = "$keyPrefix-minus") { onMinus() }
        Text(value, color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 78.dp).padding(horizontal = 8.dp))
        ActionButton("+", key = "$keyPrefix-plus") { onPlus() }
    }
}

/** Fila de opciones excluyentes (Abajo / Centro / Arriba…). */
@Composable
fun ChoiceRow(label: String, options: List<String>, selected: Int, keyPrefix: String, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Col.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o ->
                ActionButton(o, primary = i == selected, key = "$keyPrefix-$i") { onSelect(i) }
            }
        }
    }
}
