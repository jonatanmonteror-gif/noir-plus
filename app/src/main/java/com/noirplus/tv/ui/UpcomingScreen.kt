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
import androidx.compose.material.icons.filled.Notifications
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
    var remindVer by remember { mutableStateOf(0) }
    val remindedKeys = remember(remindVer, changes) { Upcoming.reminders().map { Upcoming.remindKey(it) }.toSet() }
    val today = Upcoming.today()
    // En la tele, cajas por defecto; en el móvil, lista
    val asGrid = Repo.prefs.str("upView", if (d.tv) "grid" else "list") == "grid"
    val onPick: (Upcoming.Shown) -> Unit = { s ->
        if (s.available && s.local != null) nav.push(Screen.Detail(s.local))
        else { Upcoming.toggleReminder(s.up); remindVer++ }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = d.edge, end = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(TYPES, key = { it.first }) { (k, label) -> CategoryChip(label, selected = type == k) { type = k } }
                }
                // Fundido suave hacia el botón de plataformas (las pestañas se esconden poco a poco)
                Box(
                    Modifier.align(Alignment.CenterEnd).width(36.dp).height(40.dp)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Col.Bg.copy(alpha = 0f), Col.Bg)))
                )
            }
            Row(Modifier.padding(start = 4.dp, end = d.edge), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformButton(plats, Platforms.main) { p -> platsRaw = Platforms.main.filter { it in p }.joinToString("|") }
                // Cajas o lista
                RoundIconButton(if (asGrid) ListIcon else GridIcon, if (asGrid) "Ver en lista" else "Ver en cajas") {
                    Repo.prefs.putStr("upView", if (asGrid) "list" else "grid")
                }
            }
        }
        Text(
            (if (plats.isNotEmpty()) Platforms.main.filter { it in plats }.joinToString(" + ") + "  ·  " else "") +
                "Próximos 3 meses. Toca un estreno y te aviso cuando tu proveedor lo suba.",
            color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = d.edge, end = d.edge, top = 10.dp, bottom = 4.dp),
        )
        if (shown.isEmpty()) return Message(if (l.isEmpty()) "No se pudieron cargar los próximos estrenos. Revisa la conexión." else "Nada con estos filtros")
        if (asGrid) {
            // Cajas: una fila de carátulas por periodo (Esta semana, La semana que viene, Diciembre…)
            val periods = remember(shown) { shown.sortedBy { it.up.date }.groupBy { periodLabel(it.up.date, today) }.entries.toList() }
            TvRowScroll {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = d.bottomPad)) {
                    periods.forEach { (title, rows) ->
                        item(key = "p:$title") {
                            Column {
                                SectionTitle(title, year = rows.size.toString())
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = d.edge),
                                    horizontalArrangement = Arrangement.spacedBy(d.gap),
                                ) {
                                    itemsIndexed(rows, key = { i, s -> "$i:${s.up.key}:${s.up.season}:${s.up.episode}" }) { _, s ->
                                        UpcomingCard(s, remindedKeys.contains(Upcoming.remindKey(s.up)), today) { onPick(s) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = d.edge, end = d.edge, bottom = d.bottomPad)) {
            groups.forEach { (day, rows) ->
                item(key = "h$day") {
                    Text(dayLabel(day, today), color = Col.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                }
                itemsIndexed(rows, key = { i, s -> "$day:$i:${s.up.key}:${s.up.season}:${s.up.episode}" }) { _, s ->
                    UpcomingRow(s, reminded = remindedKeys.contains(Upcoming.remindKey(s.up))) { onPick(s) }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(s: Upcoming.Shown, reminded: Boolean, onClick: () -> Unit) {
    val up = s.up
    Focusable(Modifier.fillMaxWidth().height(92.dp), key = "up/${up.key}/${up.season}/${up.episode}", shape = RoundedCornerShape(12.dp),
        focusScale = 1.02f, onClick = onClick) { focused ->
        Row(Modifier.fillMaxSize().background(if (focused) Color.White else Col.Surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(52.dp).height(76.dp).clip(RoundedCornerShape(6.dp)).background(Col.Surface2)) {
                up.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(up.name, color = if (focused) Color.Black else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // En la misma línea que el título: estado o aviso activado
                    val st = statusOf(s)
                    when {
                        st != null -> Text(st.first, color = if (focused) Color.Black.copy(alpha = if (st.second) 1f else 0.6f) else if (st.second) Col.Text else Col.Sub,
                            fontSize = 12.sp, fontWeight = if (st.second) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp))
                        reminded -> androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Notifications, "Te aviso",
                            tint = if (focused) Color.Black else Col.Text, modifier = Modifier.padding(start = 8.dp).size(16.dp))
                    }
                }
                Text(Upcoming.label(up), color = if (focused) Color.Black.copy(alpha = 0.7f) else Col.Sub, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (up.platform.isNotBlank()) Text(up.platform, color = if (focused) Color.Black.copy(alpha = 0.7f) else Col.Sub, fontSize = 12.sp)
            }
            // A la derecha: si ya lo tiene tu proveedor

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

/** Caja de estreno: carátula con la fecha encima, título y qué es (temporada, serie nueva, estreno en cines…). */
@Composable
private fun UpcomingCard(s: Upcoming.Shown, reminded: Boolean, today: Int, onClick: () -> Unit) {
    val d = LocalDims.current
    val up = s.up
    val w = d.poster
    Column(Modifier.width(w)) {
        Focusable(Modifier.width(w).height(w * 1.5f), key = "upc/${up.key}/${up.season}/${up.episode}", focusScale = 1.08f, onClick = onClick) { _ ->
            Box(Modifier.fillMaxSize().background(Col.Surface2)) {
                up.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: Text(up.name, color = Col.Sub, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp), maxLines = 3)
                // Fecha arriba a la izquierda
                Column(
                    Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(shortDow(up.date, today), color = Col.Sub, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                    Text("${up.date % 100}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
                    Text(MONTHS[up.date / 100 % 100 - 1].uppercase(), color = Col.Sub, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                // Arriba a la derecha: disponible o aviso activado
                when {
                    s.available -> Text("DISPONIBLE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.White)
                            .padding(horizontal = 7.dp, vertical = 3.dp))
                    reminded -> Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Notifications, "Te aviso",
                            tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                // Abajo: plataforma sobre un degradado
                if (up.platform.isNotBlank()) Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
                ) { Text(up.platform, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        Text(up.name, color = Col.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp))
        Text(Upcoming.label(up), color = Col.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun shortDow(day: Int, today: Int): String {
    if (day == today) return "HOY"
    val c = java.util.Calendar.getInstance().apply { set(day / 10000, day / 100 % 100 - 1, day % 100, 12, 0, 0) }
    return listOf("DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB")[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
}

/** Periodo para agrupar las cajas: Hoy y mañana, Esta semana, La semana que viene, y después por meses. */
private fun periodLabel(day: Int, today: Int): String {
    fun cal(x: Int) = java.util.Calendar.getInstance().apply { set(x / 10000, x / 100 % 100 - 1, x % 100, 12, 0, 0) }
    val diff = ((cal(day).timeInMillis - cal(today).timeInMillis) / 86_400_000L).toInt()
    val months = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
    return when {
        diff <= 1 -> "Hoy y mañana"
        diff <= 7 -> "Esta semana"
        diff <= 14 -> "La semana que viene"
        else -> months[day / 100 % 100 - 1] + if (day / 10000 != today / 10000) " ${day / 10000}" else ""
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

/** Aviso al abrir: «Nuevo capítulo disponible» de una serie que sigues. */
@Composable
fun NewEpisodeNotice() {
    val nav = LocalNav.current
    val resume = Repo.resumeCount.intValue
    // (estreno, ficha, ¿es un recordatorio?)
    var queue by remember { mutableStateOf<List<Triple<Upcoming.Up, com.noirplus.tv.data.Item, Boolean>>>(emptyList()) }
    LaunchedEffect(resume, Repo.dataVersion.intValue) {
        delay(4000)
        val rem = runCatching { Upcoming.remindersReady() }.getOrDefault(emptyList()).map { Triple(it.first, it.second, true) }
        val eps = runCatching { Upcoming.newEpisodes() }.getOrDefault(emptyList()).map { Triple(it.first, it.second, false) }
        queue = rem + eps.filter { e -> rem.none { it.second.key == e.second.key } }
    }
    val (up, item, isReminder) = queue.firstOrNull() ?: return
    fun next() {
        if (isReminder) Upcoming.toggleReminder(up) else Upcoming.markNotified(up)
        queue = queue.drop(1)
    }
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
                    Text(if (isReminder) "Ya está disponible" else "Nuevo capítulo disponible", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(up.name + when {
                        isReminder -> if (up.type == "tv" && up.kind != "new") " · " + Upcoming.label(up) else ""
                        up.lastS > 0 -> " · T${up.lastS} E${up.lastE}"
                        else -> ""
                    }, color = Col.Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
