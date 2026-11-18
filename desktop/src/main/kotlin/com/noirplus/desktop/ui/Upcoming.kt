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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Notifications
import com.noirplus.desktop.data.Item
import com.noirplus.desktop.data.Repo
import com.noirplus.desktop.data.Store
import com.noirplus.desktop.data.Tmdb
import com.noirplus.desktop.data.Upcoming
import kotlinx.coroutines.delay
import com.noirplus.desktop.data.Imdb
import androidx.compose.material.icons.filled.Movie
import kotlinx.coroutines.launch

private val TYPES = listOf("all" to "Todo", "past" to "Ya estrenados", "film" to "Películas", "new" to "Series nuevas", "season" to "Temporadas", "episode" to "Mis capítulos")

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
        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 18.dp)) {
            items(TYPES, key = { it.first }) { (k, l) -> UpChip(l, type == k) { type = k } }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            items(Tmdb.platforms, key = { "p:$it" }) { p -> UpChip(p, p in plats, small = true) { plats = if (p in plats) plats - p else plats + p } }
        }
        Text("Lo estrenado en las 2 últimas semanas y lo de los próximos 3 meses. Haz clic en un estreno y te aviso cuando tu proveedor lo suba. «Mis capítulos»: de las series que sigues.", color = Col.Sub, fontSize = 12.sp,
            modifier = Modifier.padding(start = 40.dp, bottom = 6.dp))
        val l = list
        val shown = remember(l, type, plats) { l?.filter { (type == "all" || (type == "past" && it.up.date < Upcoming.today()) || it.up.kind == type) && (plats.isEmpty() || it.up.platform in plats) }?.sortedBy { it.up.date } }
        when {
            l == null -> Loading()
            shown.isNullOrEmpty() -> Message(if (l.isEmpty()) "No se pudieron cargar los próximos estrenos." else "Nada con estos filtros")
            else -> {
                val today = Upcoming.today()
                var remindVer by remember { mutableStateOf(0) }
                val reminded = remember(remindVer) { Upcoming.reminders().map { Upcoming.remindKey(it) }.toSet() }
                val state = androidx.compose.foundation.lazy.rememberLazyListState()
                Store.changes.intValue
                val cols = Store.upcomingColumns()
                // Un bloque por día: fecha a la izquierda y los estrenos de ese día en 2 o 3 columnas
                // Primero lo estrenado en las 2 últimas semanas (lo más reciente arriba) y luego lo que viene
                val days = remember(shown) {
                    val g = shown.groupBy { it.up.date }.toList()
                    g.filter { it.first < today }.sortedByDescending { it.first } + g.filter { it.first >= today }.sortedBy { it.first }
                }
                val firstFuture = remember(days) { days.indexOfFirst { it.first >= today } }
                LazyColumn(state = state, contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 6.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    itemsIndexed(days, key = { i, d -> "$i:${d.first}" }) { i, (date, entries) ->
                      Column {
                        if (i == 0 && firstFuture != 0) UpSection("Ya estrenado · últimas 2 semanas")
                        if (i == firstFuture) UpSection("Próximamente", top = i > 0)
                        Row(Modifier.fillMaxWidth()) {
                            DateBadge(date, today)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                entries.chunked(cols).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        row.forEach { s ->
                                            Box(Modifier.weight(1f)) {
                                                UpEntry(s, Upcoming.remindKey(s.up) in reminded, today, compact = cols >= 3) {
                                                    if (s.available && s.local != null) nav.push(Screen.Detail(s.local)) else { Upcoming.toggleReminder(s.up); remindVer++ }
                                                }
                                            }
                                        }
                                        // Huecos vacíos para que la última fila no se estire
                                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                      }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpSection(text: String, top: Boolean = false) {
    Text(text, color = Col.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = if (top) 18.dp else 0.dp, bottom = 12.dp))
}

/** Fecha del bloque: mes (u HOY) y día. */
@Composable
private fun DateBadge(date: Int, today: Int) {
    Column(Modifier.width(70.dp).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (date == today) "HOY" else MONTHS[date / 100 % 100 - 1].uppercase(), color = if (date == today) Col.Text else Col.Sub,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Text("${date % 100}", color = if (date < today) Col.Sub else Col.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = InterDisplay)
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

/** Estreno: fecha a la izquierda y tarjeta con imagen, título, qué es, resumen y botón de acción. */
@Composable
private fun UpEntry(s: Upcoming.Shown, reminded: Boolean, today: Int, compact: Boolean, onClick: () -> Unit) {
    val up = s.up
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(if (compact) 210.dp else 222.dp).clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Color(0xFF2A2A30) else Col.Surface)
            .hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }.pointerHoverIcon(PointerIcon.Hand),
    ) {
        // Portada completa (vertical), sin recortar
        Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).background(Color(0xFF2A2A2E))) {
            (up.poster ?: up.backdrop)?.let { AsyncImage(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (compact) 14.dp else 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (up.platform.isNotBlank()) Text(up.platform.uppercase(), color = Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(up.name, color = Col.Text, fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = if (compact) 19.sp else 21.sp)
            Text(Upcoming.label(up), color = Col.Text.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (up.overview.isNotBlank()) Text(up.overview, color = Col.Sub, fontSize = 12.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            Spacer(Modifier.weight(1f))
            val scope = rememberCoroutineScope()
            // Tráiler encima del botón de aviso
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .clickable {
                            scope.launch {
                                val url = Imdb.trailerUrl(up.type, up.id, up.name)
                                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
                            }
                        }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Movie, null, tint = Col.Text, modifier = Modifier.size(14.dp))
                    Text("Tráiler", color = Col.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp))
                }
                ActionPill(s, reminded, today)
            }
        }
    }
}

/** Ver ahora / Avísame / Te avisaré. */
@Composable
private fun ActionPill(s: Upcoming.Shown, reminded: Boolean, today: Int) {
    val (txt, filled, icon) = when {
        s.available -> Triple("Ver ahora", true, androidx.compose.material.icons.Icons.Filled.PlayArrow)
        reminded -> Triple("Te avisaré", false, androidx.compose.material.icons.Icons.Filled.Notifications)
        s.up.date <= today -> Triple("Avísame cuando lo suban", false, androidx.compose.material.icons.Icons.Outlined.Notifications)
        else -> Triple("Avísame", false, androidx.compose.material.icons.Icons.Outlined.Notifications)
    }
    val fg = if (filled) Color.Black else Col.Text
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) Color.White else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(txt, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp))
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
    var queue by remember { mutableStateOf<List<Triple<Upcoming.Up, Item, Boolean>>>(emptyList()) }
    val dv = Repo.dataVersion.intValue
    LaunchedEffect(dv) {
        delay(5000)
        while (true) {
            val rem = runCatching { Upcoming.remindersReady() }.getOrDefault(emptyList()).map { Triple(it.first, it.second, true) }
            val eps = runCatching { Upcoming.newEpisodes() }.getOrDefault(emptyList()).map { Triple(it.first, it.second, false) }
            queue = rem + eps.filter { e -> rem.none { it.second.key == e.second.key } }
            delay(30 * 60_000L)
        }
    }
    val (up, item, isReminder) = queue.firstOrNull() ?: return
    fun next() { if (isReminder) Upcoming.toggleReminder(up) else Upcoming.markNotified(up); queue = queue.drop(1) }
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF26262B)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        up.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 36.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))) }
        Column(Modifier.padding(horizontal = 12.dp).widthIn(max = 260.dp)) {
            Text(if (isReminder) "Ya está disponible" else "Nuevo capítulo disponible", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(up.name + when {
                isReminder -> if (up.type == "tv" && up.kind != "new") " · " + Upcoming.label(up) else ""
                up.lastS > 0 -> " · T${up.lastS} E${up.lastE}"
                else -> ""
            }, color = Col.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NButton("Ver", primary = true) { next(); nav.push(Screen.Detail(item)) }
        Spacer(Modifier.width(8.dp))
        NButton("Cerrar") { next() }
    }
}
