package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirplus.tv.data.Platforms
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.Upcoming
import kotlinx.coroutines.delay

private val TYPES = listOf("all" to "Todo", "film" to "Películas", "new" to "Series nuevas", "season" to "Temporadas", "episode" to "Mis capítulos")

/** Próximos estrenos (90 días), agrupados por día. */
@Composable
fun UpcomingContent() {
    val d = LocalDims.current
    val nav = LocalNav.current
    val dv = Repo.dataVersion.intValue
    val changes = Repo.prefs.changes.intValue
    val list by produceState<List<Upcoming.Shown>?>(null, dv, changes / 50) { value = Upcoming.shown() }
    var type by rememberSaveable { mutableStateOf("all") }
    var platsRaw by rememberSaveable { mutableStateOf("") }
    val plats = remember(platsRaw) { platsRaw.split('|').filter { it.isNotEmpty() }.toSet() }
    val l = list ?: return Loading()
    val shown = remember(l, type, platsRaw) {
        l.filter { (type == "all" || it.up.kind == type) && (plats.isEmpty() || it.up.platform in plats) }
    }
    val groups = remember(shown) { shown.groupBy { it.up.date }.toSortedMap().entries.toList() }
    val today = Upcoming.today()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = d.edge, end = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TYPES, key = { it.first }) { (k, label) -> CategoryChip(label, selected = type == k) { type = k } }
                }
                // Fundido suave hacia el botón de plataformas (las pestañas se esconden poco a poco)
                Box(
                    Modifier.align(Alignment.CenterEnd).width(36.dp).height(48.dp)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Col.Bg.copy(alpha = 0f), Col.Bg)))
                )
            }
            Box(Modifier.padding(start = 4.dp, end = d.edge)) {
                PlatformButton(plats, Platforms.main) { p -> platsRaw = Platforms.main.filter { it in p }.joinToString("|") }
            }
        }
        Text(
            (if (plats.isNotEmpty()) Platforms.main.filter { it in plats }.joinToString(" + ") + "  ·  " else "") +
                "Próximos 3 meses. «Mis capítulos»: de las series que sigues (Mi lista y Continuar viendo).",
            color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = d.edge, end = d.edge, top = 10.dp, bottom = 4.dp),
        )
        if (shown.isEmpty()) return Message(if (l.isEmpty()) "No se pudieron cargar los próximos estrenos. Revisa la conexión." else "Nada con estos filtros")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = d.edge, end = d.edge, bottom = d.bottomPad)) {
            groups.forEach { (day, rows) ->
                item(key = "h$day") {
                    Text(dayLabel(day, today), color = Col.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                }
                itemsIndexed(rows, key = { i, s -> "$day:$i:${s.up.key}:${s.up.season}:${s.up.episode}" }) { _, s ->
                    UpcomingRow(s) { s.local?.let { nav.push(Screen.Detail(it)) } }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(s: Upcoming.Shown, onClick: () -> Unit) {
    val up = s.up
    Focusable(Modifier.fillMaxWidth().height(92.dp), key = "up/${up.key}/${up.season}/${up.episode}", shape = RoundedCornerShape(12.dp),
        focusScale = 1.02f, onClick = onClick) { focused ->
        Row(Modifier.fillMaxSize().background(if (focused) Color.White else Col.Surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(52.dp).height(76.dp).clip(RoundedCornerShape(6.dp)).background(Col.Surface2)) {
                up.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(up.name, color = if (focused) Color.Black else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Upcoming.label(up), color = if (focused) Color.Black.copy(alpha = 0.7f) else Col.Sub, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (up.platform.isNotBlank()) Text(up.platform, color = if (focused) Color.Black.copy(alpha = 0.7f) else Col.Sub, fontSize = 12.sp)
            }
            // A la derecha: si ya lo tiene tu proveedor
            statusOf(s)?.let { (txt, strong) ->
                Text(txt, color = if (focused) Color.Black.copy(alpha = if (strong) 1f else 0.6f) else if (strong) Col.Text else Col.Sub,
                    fontSize = 12.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * Estado a la derecha: «Disponible» = ese estreno ya lo ha subido tu proveedor;
 * «Pendiente» = ya se ha estrenado pero tu proveedor aún no lo tiene. Si aún no se ha estrenado, nada.
 */
internal fun statusOf(s: Upcoming.Shown): Pair<String, Boolean>? = when {
    s.available -> "Disponible" to true
    s.local != null && s.up.date <= Upcoming.today() -> "Pendiente" to false
    else -> null
}

private val DAYS = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun dayLabel(day: Int, today: Int): String {
    val c = java.util.Calendar.getInstance().apply { set(day / 10000, day / 100 % 100 - 1, day % 100, 12, 0, 0) }
    val t = java.util.Calendar.getInstance().apply { set(today / 10000, today / 100 % 100 - 1, today % 100, 12, 0, 0) }
    val diff = ((c.timeInMillis - t.timeInMillis) / 86_400_000L).toInt()
    val base = "${DAYS[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]} ${day % 100} ${MONTHS[day / 100 % 100 - 1]}"
    return when (diff) {
        -1 -> "Ayer · $base"
        0 -> "Hoy · $base"
        1 -> "Mañana · $base"
        else -> base.replaceFirstChar { it.uppercase() }
    }
}

/** Aviso al abrir: «Nuevo capítulo disponible» de una serie que sigues. */
@Composable
fun NewEpisodeNotice() {
    val nav = LocalNav.current
    val resume = Repo.resumeCount.intValue
    var queue by remember { mutableStateOf<List<Pair<Upcoming.Up, com.noirplus.tv.data.Item>>>(emptyList()) }
    LaunchedEffect(resume, Repo.dataVersion.intValue) {
        delay(4000)
        queue = runCatching { Upcoming.newEpisodes() }.getOrDefault(emptyList())
    }
    val (up, item) = queue.firstOrNull() ?: return
    fun next() { Upcoming.markNotified(up); queue = queue.drop(1) }
    val compact = LocalDims.current.compact
    Box(Modifier.fillMaxSize(), contentAlignment = if (compact) Alignment.TopCenter else Alignment.TopEnd) {
        Column(
            Modifier.padding(horizontal = if (compact) 12.dp else 20.dp, vertical = if (compact) 12.dp else 70.dp)
                .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(460.dp))
                .clip(RoundedCornerShape(14.dp)).background(Col.Surface2.copy(alpha = 0.98f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                up.poster?.let {
                    AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 34.dp, height = 50.dp).clip(RoundedCornerShape(4.dp)))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Nuevo capítulo disponible", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(up.name + (if (up.lastS > 0) " · T${up.lastS} E${up.lastE}" else ""), color = Col.Sub, fontSize = 12.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                // Cerrar siempre visible
                Focusable(Modifier.size(32.dp), key = "ne-x", shape = RoundedCornerShape(50), focusScale = 1.1f, onClick = { next() }) { f ->
                    Box(Modifier.fillMaxSize().background(if (f) Color.White else Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Text("✕", color = if (f) Color.Black else Col.Text, fontSize = 14.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                ActionButton("Ahora no", key = "ne-close") { next() }
                Spacer(Modifier.width(8.dp))
                ActionButton("Ver", primary = true, key = "ne-ver") { next(); nav.push(Screen.Detail(item)) }
            }
        }
    }
}
