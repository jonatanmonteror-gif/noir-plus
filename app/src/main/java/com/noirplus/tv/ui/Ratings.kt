package com.noirplus.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Rating
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lo que se va a valorar. */
data class RateTarget(val key: String, val title: String, val image: String?, val item: Item?, val label: String)

/** Abre el diálogo de valoración desde cualquier pantalla (pulsación larga). */
val LocalRate = staticCompositionLocalOf<(RateTarget) -> Unit> { {} }

/** Icono de "reseña" (bocadillo con lápiz): distinto de la estrella de favoritos. */
val ReviewIcon: ImageVector = materialIcon(name = "Filled.RateReview") {
    materialPath {
        moveTo(20f, 2f); lineTo(4f, 2f); curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f); lineTo(2f, 22f); lineToRelative(4f, -4f)
        horizontalLineToRelative(14f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); lineTo(22f, 4f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
        moveTo(6f, 14f); verticalLineToRelative(-2.47f); lineToRelative(6.88f, -6.88f); curveToRelative(0.2f, -0.2f, 0.51f, -0.2f, 0.71f, 0f)
        lineToRelative(1.77f, 1.77f); curveToRelative(0.2f, 0.2f, 0.2f, 0.51f, 0f, 0.71f); lineTo(8.47f, 14f); lineTo(6f, 14f); close()
        moveTo(18f, 14f); horizontalLineToRelative(-7.5f); lineToRelative(2f, -2f); lineTo(18f, 12f); verticalLineToRelative(2f); close()
    }
}

fun fmtStars(v: Float): String = String.format(Locale("es", "ES"), if (v % 1f == 0f) "%.0f" else "%.1f", v)

/** 5 estrellas con medias estrellas (0–5). */
@Composable
fun HalfStars(value: Float, size: Dp = 16.dp, gap: Dp = 3.dp, empty: Color = Col.Sub.copy(alpha = 0.4f)) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(5) { i ->
            val fill = (value - i).coerceIn(0f, 1f)
            Box(Modifier.size(size)) {
                Icon(Icons.Filled.Star, null, tint = empty, modifier = Modifier.fillMaxSize())
                if (fill > 0f) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(RoundedCornerShape(0.dp))) {
                        Icon(Icons.Filled.Star, null, tint = Col.Star, modifier = Modifier.size(size))
                    }
                }
            }
        }
    }
}

/** Selector de estrellas: tocar media estrella / ◀ ▶ con el mando, en pasos de 0,5. */
@Composable
private fun StarPicker(value: Float, focusRequester: FocusRequester? = null, onChange: (Float) -> Unit) {
    val starSize = 34.dp
    val gap = 8.dp
    Focusable(
        Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionRight -> { onChange((value + 0.5f).coerceAtMost(5f)); true }
                Key.DirectionLeft -> { onChange((value - 0.5f).coerceAtLeast(0.5f)); true }
                else -> false
            }
        },
        shape = RoundedCornerShape(10.dp), focusScale = 1.04f, border = true, focusRequester = focusRequester, onClick = {},
    ) {
        Box(
            Modifier.padding(8.dp).pointerInput(Unit) {
                detectTapGestures { o ->
                    val unit = (starSize + gap).toPx()
                    val raw = o.x / unit
                    val whole = raw.toInt()
                    val frac = raw - whole
                    val v = whole + if (frac * unit > starSize.toPx() / 2f) 1f else 0.5f
                    onChange(v.coerceIn(0.5f, 5f))
                }
            }
        ) { HalfStars(value, size = starSize, gap = gap) }
    }
}

@Composable
fun RatingDialog(target: RateTarget, onClose: () -> Unit) {
    val prefs = Repo.prefs
    val existing = remember(target.key) { prefs.rating(target.key) }
    var stars by remember { mutableFloatStateOf(existing?.stars ?: 4f) }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    val starsFr = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { starsFr.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 440.dp).clip(RoundedCornerShape(18.dp)).background(Col.Surface2)
                .pointerInput(Unit) { detectTapGestures { } }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (target.image != null) {
                    AsyncImage(target.image, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(54.dp).aspectRatio(2f / 3f).clip(CardShape))
                }
                Column(Modifier.padding(start = if (target.image != null) 14.dp else 0.dp)) {
                    Text(target.label.uppercase(), color = Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(target.title, color = Col.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarPicker(stars, starsFr) { stars = it }
                Text(fmtStars(stars), color = Col.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(500) },
                label = { Text("Tu opinión (opcional)") }, minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Guardar", primary = true) {
                    prefs.saveRating(Rating(target.key, target.title, target.image, stars, text.trim(), System.currentTimeMillis(), target.item, target.label))
                    onClose()
                }
                if (existing != null) ActionButton("Quitar") { prefs.removeRating(target.key); onClose() }
                ActionButton("Cancelar") { onClose() }
            }
        }
    }
}

fun Item.rateTarget(): RateTarget = RateTarget(key, name, image, this, if (kind == com.noirplus.tv.data.Kind.SERIES) "Serie" else "Película")

/** Sección "Valoraciones": todo lo que has puntuado. */
@Composable
fun RatingsContent() {
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val rate = LocalRate.current
    val d = LocalDims.current
    val changes = prefs.changes.intValue
    val list = remember(changes) { prefs.ratings() }
    if (list.isEmpty()) return Message("Aún no has valorado nada.\nMantén pulsada una película, serie o episodio para valorarlo.")
    val df = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = d.edge, end = d.edge, top = 8.dp, bottom = d.bottomPad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(list, key = { it.key }) { r ->
            Focusable(
                Modifier.fillMaxWidth(), key = "rate/${r.key}", shape = RoundedCornerShape(12.dp), focusScale = 1.02f, border = true,
                onLongClick = { rate(RateTarget(r.key, r.title, r.image, r.item, r.label)) },
                onClick = { r.item?.let { nav.push(Screen.Detail(it)) } },
            ) {
                Row(Modifier.fillMaxWidth().background(Col.Surface).padding(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(58.dp).aspectRatio(2f / 3f).clip(CardShape).background(Col.Surface2)) {
                        AsyncImage(r.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("${r.label.uppercase()}  ·  ${df.format(Date(r.time))}", color = Col.Sub, fontSize = 10.sp, letterSpacing = 0.8.sp)
                        Text(r.title, color = Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            HalfStars(r.stars, size = 14.dp, gap = 2.dp)
                            Text(fmtStars(r.stars), color = Col.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 6.dp))
                        }
                        if (r.text.isNotBlank()) {
                            Text("“${r.text}”", color = Col.Text.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Fila pequeña "Tu valoración" para las fichas. */
@Composable
fun MyRatingLine(key: String, onRate: () -> Unit, modifier: Modifier = Modifier) {
    val prefs = Repo.prefs
    val changes = prefs.changes.intValue
    val r = remember(changes, key) { prefs.rating(key) }
    Row(
        modifier.clip(RoundedCornerShape(50)).clickable { onRate() }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (r == null) {
            Icon(ReviewIcon, null, tint = Col.Sub, modifier = Modifier.size(16.dp))
            Text("Valorar", color = Col.Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        } else {
            Text("Tu nota", color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            HalfStars(r.stars, size = 14.dp, gap = 2.dp)
            Text(fmtStars(r.stars), color = Col.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
