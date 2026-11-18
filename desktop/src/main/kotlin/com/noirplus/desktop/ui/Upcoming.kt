package com.noirplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.noirplus.desktop.data.Item
import com.noirplus.desktop.data.Repo
import com.noirplus.desktop.data.Tmdb
import com.noirplus.desktop.data.Upcoming
import kotlinx.coroutines.delay

private val TYPES = listOf("all" to "Todo", "film" to "Películas", "new" to "Series nuevas", "season" to "Temporadas", "episode" to "Mis capítulos")

/** Próximos estrenos (90 días), agrupados por día. */
@Composable
fun UpcomingScreen() {
    val nav = LocalNav.current
    val dv = Repo.dataVersion.intValue
    val list by produceState<List<Upcoming.Shown>?>(null, dv) { value = Upcoming.shown() }
    var type by remember { mutableStateOf("all") }
    var plats by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Próximos estrenos", color = Col.Text, fontSize = 34.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            items(TYPES, key = { it.first }) { (k, l) -> UpChip(l, type == k) { type = k } }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            items(Tmdb.platforms, key = { "p:$it" }) { p -> UpChip(p, p in plats, small = true) { plats = if (p in plats) plats - p else plats + p } }
        }
        Text("Próximos 3 meses. «Mis capítulos»: de las series que sigues (Mi lista y Continuar viendo).", color = Col.Sub, fontSize = 12.sp,
            modifier = Modifier.padding(start = 40.dp, bottom = 6.dp))
        val l = list
        val shown = remember(l, type, plats) { l?.filter { (type == "all" || it.up.kind == type) && (plats.isEmpty() || it.up.platform in plats) } }
        when {
            l == null -> Loading()
            shown.isNullOrEmpty() -> Message(if (l.isEmpty()) "No se pudieron cargar los próximos estrenos." else "Nada con estos filtros")
            else -> {
                val groups = remember(shown) { shown.groupBy { it.up.date }.toSortedMap().entries.toList() }
                val today = Upcoming.today()
                LazyColumn(contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp)) {
                    groups.forEach { (day, rows) ->
                        item(key = "h$day") {
                            Text(dayLabel(day, today), color = Col.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                        }
                        itemsIndexed(rows, key = { i, s -> "$day:$i:${s.up.key}:${s.up.season}:${s.up.episode}" }) { _, s ->
                            UpRow(s) { s.local?.let { nav.push(Screen.Detail(it)) } }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpChip(label: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text((if (on && small) "✓  " else "") + label, color = if (on) Color.Black else Col.Text, fontSize = if (small) 12.sp else 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .then(if (small) Modifier.border(1.dp, if (on) Color.White else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)) else Modifier)
            .background(if (on) Color.White else if (small) Color.Transparent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = if (small) 5.dp else 7.dp))
}

@Composable
private fun UpRow(s: Upcoming.Shown, onClick: () -> Unit) {
    val up = s.up
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().widthIn(max = 980.dp).height(100.dp).clip(RoundedCornerShape(12.dp))
            .background(if (hovered && s.local != null) Color(0xFF2E2E34) else Col.Surface)
            .hoverable(src).clickable(enabled = s.local != null, interactionSource = src, indication = null) { onClick() }
            .then(if (s.local != null) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2E))) {
            up.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(up.name, color = Col.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Upcoming.label(up), color = Col.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (up.platform.isNotBlank()) Text(up.platform, color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                if (s.available) Text("Ya disponible", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp))
                else if (s.local != null) Text("En tu lista", color = Col.Sub, fontSize = 11.sp)
            }
        }
    }
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

/** Aviso: «Nuevo capítulo disponible» de una serie que sigues (se mira al abrir y cada 30 minutos). */
@Composable
fun NewEpisodeBanner(modifier: Modifier) {
    val nav = LocalNav.current
    var queue by remember { mutableStateOf<List<Pair<Upcoming.Up, Item>>>(emptyList()) }
    val dv = Repo.dataVersion.intValue
    LaunchedEffect(dv) {
        delay(5000)
        while (true) {
            queue = runCatching { Upcoming.newEpisodes() }.getOrDefault(emptyList())
            delay(30 * 60_000L)
        }
    }
    val (up, item) = queue.firstOrNull() ?: return
    fun next() { Upcoming.markNotified(up); queue = queue.drop(1) }
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF26262B)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        up.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 36.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))) }
        Column(Modifier.padding(horizontal = 12.dp).widthIn(max = 260.dp)) {
            Text("Nuevo capítulo disponible", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(up.name + (if (up.lastS > 0) " · T${up.lastS} E${up.lastE}" else ""), color = Col.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NButton("Ver", primary = true) { next(); nav.push(Screen.Detail(item)) }
        Spacer(Modifier.width(8.dp))
        NButton("Cerrar") { next() }
    }
}
