package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
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

/** Próximos estrenos (90 días) y lo estrenado el último mes. */
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
        l.filter { (type == "all" || it.up.kind == type) && (plats.isEmpty() || it.up.platform in plats) }.sortedBy { it.up.date }
    }
    var remindVer by remember { mutableStateOf(0) }
    val remindedKeys = remember(remindVer, changes) { Upcoming.reminders().map { Upcoming.remindKey(it) }.toSet() }
    val today = Upcoming.today()
    // «Calendario» (con imagen, por defecto) o «Compacta»
    val compactView = Repo.prefs.str("upView2", "cal") == "list"
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
                Box(
                    Modifier.align(Alignment.CenterEnd).width(36.dp).height(40.dp)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Col.Bg.copy(alpha = 0f), Col.Bg)))
                )
            }
            Row(Modifier.padding(start = 4.dp, end = d.edge), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformButton(plats, Platforms.main) { p -> platsRaw = Platforms.main.filter { it in p }.joinToString("|") }
                RoundIconButton(if (compactView) GridIcon else ListIcon, if (compactView) "Vista con imágenes" else "Vista compacta") {
                    Repo.prefs.putStr("upView2", if (compactView) "cal" else "list")
                }
            }
        }
        Text(
            (if (plats.isNotEmpty()) Platforms.main.filter { it in plats }.joinToString(" + ") + "  ·  " else "") +
                "Del último mes a los próximos 3. Toca un estreno y te aviso cuando tu proveedor lo suba.",
            color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = d.edge, end = d.edge, top = 10.dp, bottom = 4.dp),
        )
        if (shown.isEmpty()) return Message(if (l.isEmpty()) "No se pudieron cargar los próximos estrenos. Revisa la conexión." else "Nada con estos filtros")
        // Se abre en «hoy» (lo del último mes queda encima)
        val state = androidx.compose.foundation.lazy.rememberLazyListState()
        val todayIndex = remember(shown) { shown.indexOfFirst { it.up.date >= today }.coerceAtLeast(0) }
        LaunchedEffect(shown) { runCatching { state.scrollToItem(todayIndex) } }
        LazyColumn(Modifier.fillMaxSize(), state = state,
            contentPadding = PaddingValues(start = d.edge, end = d.edge, top = 8.dp, bottom = d.bottomPad),
            verticalArrangement = Arrangement.spacedBy(if (compactView) 6.dp else 14.dp)) {
            itemsIndexed(shown, key = { i, s -> "$i:${s.up.key}:${s.up.season}:${s.up.episode}:${s.up.date}" }) { i, s ->
                val firstOfDay = i == 0 || shown[i - 1].up.date != s.up.date
                val reminded = remindedKeys.contains(Upcoming.remindKey(s.up))
                if (compactView) {
                    if (firstOfDay) Text(dayLabel(s.up.date, today), color = if (s.up.date < today) Col.Sub else Col.Text, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    UpcomingRow(s, reminded, today) { onPick(s) }
                } else {
                    UpcomingEntry(s, reminded, today, showDate = firstOfDay) { onPick(s) }
                }
            }
        }
    }
}

/** Qué se puede hacer con un estreno: verlo, o que te avisemos cuando tu proveedor lo suba. */
private enum class UpState { WATCH, REMINDED, REMIND_UPLOAD, REMIND }

private fun stateOf(s: Upcoming.Shown, reminded: Boolean, today: Int): UpState = when {
    s.available -> UpState.WATCH
    reminded -> UpState.REMINDED
    s.up.date <= today -> UpState.REMIND_UPLOAD
    else -> UpState.REMIND
}

/** Botón de acción del estreno (como «Recordarme» de las plataformas). */
@Composable
private fun ActionPill(st: UpState, focused: Boolean, small: Boolean = false) {
    val (txt, filled) = when (st) {
        UpState.WATCH -> "Ver ahora" to true
        UpState.REMINDED -> "Te avisaré" to false
        UpState.REMIND_UPLOAD -> (if (small) "Aún no subido" else "Avísame cuando lo suban") to false
        UpState.REMIND -> "Avísame" to false
    }
    val fg = if (filled) Color.Black else if (focused) Color.Black else Col.Text
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else if (focused) Color.Black.copy(alpha = 0.08f) else Color.Transparent)
            .then(if (filled) Modifier else androidx.compose.ui.Modifier.border(1.dp, if (focused) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)))
            .padding(horizontal = if (small) 9.dp else 12.dp, vertical = if (small) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (st) {
            UpState.WATCH -> androidx.compose.material.icons.Icons.Filled.PlayArrow
            UpState.REMINDED -> androidx.compose.material.icons.Icons.Filled.Notifications
            else -> androidx.compose.material.icons.Icons.Outlined.Notifications
        }
        androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.size(if (small) 13.dp else 15.dp))
        Text(txt, color = fg, fontSize = if (small) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(start = 5.dp))
    }
}

/** Vista con imágenes: fecha a la izquierda y tarjeta con la imagen del estreno (como «Próximamente» de las plataformas). */
@Composable
private fun UpcomingEntry(s: Upcoming.Shown, reminded: Boolean, today: Int, showDate: Boolean, onClick: () -> Unit) {
    val d = LocalDims.current
    val up = s.up
    val past = up.date < today
    val st = stateOf(s, reminded, today)
    Row(Modifier.fillMaxWidth()) {
        // Fecha
        Column(Modifier.width(if (d.compact) 42.dp else 64.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) {
                Text(if (up.date == today) "HOY" else MONTHS[up.date / 100 % 100 - 1].uppercase(), color = if (up.date == today) Col.Text else Col.Sub,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                Text("${up.date % 100}", color = if (past) Col.Sub else Col.Text, fontSize = if (d.compact) 22.sp else 28.sp,
                    fontWeight = FontWeight.Bold, fontFamily = InterDisplay)
            }
        }
        Focusable(Modifier.weight(1f), key = "upe/${up.key}/${up.season}/${up.episode}", shape = RoundedCornerShape(14.dp),
            focusScale = 1.08f, onClick = onClick) { focused ->
            val img = up.backdrop ?: up.poster
            val textCol = if (focused && !d.tv) Color.Black else Col.Text
            if (d.compact) {
                Column(Modifier.fillMaxWidth().background(Col.Surface)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Col.Surface2)) {
                        img?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        if (up.platform.isNotBlank()) PlatformTag(up.platform, Modifier.align(Alignment.TopStart).padding(8.dp))
                    }
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(up.name, color = Col.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Upcoming.label(up), color = Col.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (up.overview.isNotBlank()) Text(up.overview, color = Col.Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp)
                        Box(Modifier.padding(top = 6.dp)) { ActionPill(st, focused = false) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(150.dp).background(if (focused) Col.Surface2 else Col.Surface)) {
                    Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).background(Col.Surface2)) {
                        img?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        if (up.platform.isNotBlank()) PlatformTag(up.platform, Modifier.align(Alignment.TopStart).padding(8.dp))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(up.name, color = textCol, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Upcoming.label(up), color = Col.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (up.overview.isNotBlank()) Text(up.overview, color = Col.Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp)
                        Spacer(Modifier.weight(1f))
                        ActionPill(st, focused = false)
                    }
                }
            }
        }
    }
}

/** Etiqueta de la plataforma sobre la imagen. */
@Composable
private fun PlatformTag(name: String, modifier: Modifier) {
    Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 7.dp, vertical = 3.dp))
}

/** Vista compacta: una fila por estreno. */
@Composable
private fun UpcomingRow(s: Upcoming.Shown, reminded: Boolean, today: Int, onClick: () -> Unit) {
    val up = s.up
    val st = stateOf(s, reminded, today)
    Focusable(Modifier.fillMaxWidth().height(84.dp), key = "up/${up.key}/${up.season}/${up.episode}", shape = RoundedCornerShape(12.dp),
        focusScale = 1.08f, onClick = onClick) { focused ->
        Row(Modifier.fillMaxSize().background(if (focused) Col.Surface2 else Col.Surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(46.dp).height(68.dp).clip(RoundedCornerShape(6.dp)).background(Col.Surface2)) {
                up.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(up.name, color = Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Upcoming.label(up) + if (up.platform.isNotBlank()) "  ·  ${up.platform}" else "", color = Col.Sub, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.padding(start = 8.dp)) { ActionPill(st, focused = false, small = true) }
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
