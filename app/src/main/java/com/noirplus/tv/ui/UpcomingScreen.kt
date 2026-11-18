package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
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
import kotlinx.coroutines.launch

private val TYPES = listOf("all" to "Próximamente", "past" to "Recién estrenados", "film" to "Películas", "new" to "Series nuevas", "season" to "Temporadas", "episode" to "Mis capítulos")

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
        val t = Upcoming.today()
        val f = l.filter {
            // «Recién estrenados»: lo de la última semana; el resto de pestañas, solo lo que viene
            (if (type == "past") it.up.date < t else it.up.date >= t && (type == "all" || it.up.kind == type)) &&
                (plats.isEmpty() || it.up.platform in plats)
        }
        // Primero lo estrenado en las 2 últimas semanas (lo más reciente arriba) y luego lo que viene
        f.filter { it.up.date < t }.sortedByDescending { it.up.date } + f.filter { it.up.date >= t }.sortedBy { it.up.date }
    }
    var remindVer by remember { mutableStateOf(0) }
    val remindedKeys = remember(remindVer, changes) { Upcoming.reminders().map { Upcoming.remindKey(it) }.toSet() }
    val today = Upcoming.today()
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
            }
        }
        Text(
            (if (plats.isNotEmpty()) Platforms.main.filter { it in plats }.joinToString(" + ") + "  ·  " else "") +
                (if (type == "past") "Lo estrenado en plataformas la última semana." else "Lo que llega el próximo mes.") + " Toca un estreno y te aviso cuando tu proveedor lo suba.",
            color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = d.edge, end = d.edge, top = 10.dp, bottom = 4.dp),
        )
        if (shown.isEmpty()) return Message(if (l.isEmpty()) "No se pudieron cargar los próximos estrenos. Revisa la conexión." else "Nada con estos filtros")
        // Siempre se abre desde arriba (sin recordar dónde se quedó la última vez)
        val listState = remember(shown) { androidx.compose.foundation.lazy.LazyListState() }
        // Tele y tableta: dos columnas por día; móvil: una
        val cols = if (d.compact) 1 else 2
        val rows = remember(shown, cols) {
            shown.groupBy { it.up.date }.values.flatMap { day -> day.chunked(cols).mapIndexed { j, r -> (j == 0) to r } }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(start = d.edge, end = d.edge, top = 8.dp, bottom = d.bottomPad),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(rows, key = { i, r -> "$i:${r.second.first().up.date}:${r.second.first().up.key}" }) { _, (firstOfDay, r) ->
                // Móvil: la fecha va encima como título y las cajas a todo el ancho
                if (d.compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (firstOfDay) Text(dayLabel(r.first().up.date, today), color = if (r.first().up.date < today) Col.Sub else Col.Text,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    UpcomingEntry(r.first(), remindedKeys.contains(Upcoming.remindKey(r.first().up)), today) { onPick(r.first()) }
                } else Row(Modifier.fillMaxWidth()) {
                    DateColumn(r.first().up.date, today, firstOfDay)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        r.forEach { s ->
                            Box(Modifier.weight(1f)) {
                                UpcomingEntry(s, remindedKeys.contains(Upcoming.remindKey(s.up)), today) { onPick(s) }
                            }
                        }
                        repeat(cols - r.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateColumn(date: Int, today: Int, show: Boolean) {
    val d = LocalDims.current
    Column(Modifier.width(if (d.compact) 42.dp else 64.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (show) {
            Text(if (date == today) "HOY" else MONTHS[date / 100 % 100 - 1].uppercase(), color = if (date == today) Col.Text else Col.Sub,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Text("${date % 100}", color = if (date < today) Col.Sub else Col.Text, fontSize = if (d.compact) 22.sp else 28.sp,
                fontWeight = FontWeight.Bold, fontFamily = InterDisplay)
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

/** Botones del estreno: acción (Ver ahora / Avísame) y Tráiler. */
@Composable
private fun EntryButtons(s: Upcoming.Shown, st: UpState, onAction: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val compact = LocalDims.current.compact
    val action: @Composable () -> Unit = {
        Focusable(key = "upa/${Upcoming.remindKey(s.up)}", shape = RoundedCornerShape(50), focusScale = 1.08f, onClick = onAction) { f ->
            ActionPill(st, focused = f, short = true)
        }
    }
    val trailer: @Composable () -> Unit = {
        Focusable(key = "upt/${Upcoming.remindKey(s.up)}", shape = RoundedCornerShape(50), focusScale = 1.08f, onClick = {
            scope.launch {
                val url = runCatching { com.noirplus.tv.data.Tmdb.trailerById(s.up.type, s.up.id) }.getOrNull()
                openTrailer(ctx, url, s.up.name)
            }
        }) { f -> TrailerPill(f, iconOnly = compact) }
    }
    // Uno al lado del otro: primero el aviso y luego el tráiler (en el móvil el tráiler solo con su icono)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { action(); trailer() }
}

@Composable
private fun TrailerPill(focused: Boolean, iconOnly: Boolean = false) {
    val fg = if (focused) Color.Black else Col.Text
    val base = Modifier.clip(RoundedCornerShape(50)).background(if (focused) Color.White else Color.Transparent)
        .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
    if (iconOnly) {
        // Solo el icono, en un círculo del mismo alto que el otro botón
        Box(base.size(30.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(TrailerIcon, "Tráiler", tint = fg, modifier = Modifier.size(17.dp))
        }
        return
    }
    Row(base.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(TrailerIcon, "Tráiler", tint = fg, modifier = Modifier.size(14.dp))
        Text("Tráiler", color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(start = 5.dp))
    }
}

/** Aspecto del botón de acción (como «Recordarme» de las plataformas). */
@Composable
private fun ActionPill(st: UpState, focused: Boolean, small: Boolean = false, short: Boolean = false) {
    val (txt, filled) = when (st) {
        UpState.WATCH -> "Ver ahora" to true
        UpState.REMINDED -> "Te avisaré" to false
        UpState.REMIND_UPLOAD -> (if (small) "Aún no subido" else if (short) "Avísame" else "Avísame cuando lo suban") to false
        UpState.REMIND -> "Avísame" to false
    }
    val fg = if (filled || focused) Color.Black else Col.Text
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (filled || focused) Color.White else Color.Transparent)
            .then(if (filled || focused) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)))
            .padding(horizontal = if (small) 9.dp else 11.dp, vertical = if (small) 4.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (st) {
            UpState.WATCH -> androidx.compose.material.icons.Icons.Filled.PlayArrow
            UpState.REMINDED -> androidx.compose.material.icons.Icons.Filled.Notifications
            else -> androidx.compose.material.icons.Icons.Outlined.Notifications
        }
        androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Text(txt, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
    }
}

/** Vista con imágenes: fecha a la izquierda y tarjeta con la imagen del estreno (como «Próximamente» de las plataformas). */
@Composable
private fun UpcomingEntry(s: Upcoming.Shown, reminded: Boolean, today: Int, onClick: () -> Unit) {
    val d = LocalDims.current
    val up = s.up
    val st = stateOf(s, reminded, today)
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Col.Surface)) {
            // Portada vertical completa (sin recortar) y, detrás del texto, la imagen ancha muy oscurecida
            val poster = up.poster ?: up.backdrop
            val rowH = if (d.compact) 140.dp else 150.dp
            up.backdrop?.let {
                AsyncImage(it, null, contentScale = ContentScale.Crop, alpha = 0.22f, modifier = Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Col.Surface, Col.Surface.copy(alpha = 0.55f)))))
            }
            Row(Modifier.fillMaxWidth().height(rowH)) {
                Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).background(Col.Surface2)) {
                    poster?.let { AsyncImage(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                }
                // Logo de la plataforma arriba a la derecha (si no hay logo, el nombre encima del título)
                val logo by produceState<String?>(null, up.platform) {
                    value = if (up.platform.isBlank()) null else runCatching { com.noirplus.tv.data.Tmdb.logo(up.platform) }.getOrNull()
                }
                Row(Modifier.weight(1f).fillMaxHeight().padding(start = if (d.compact) 12.dp else 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (logo == null && up.platform.isNotBlank()) Text(up.platform.uppercase(), color = Col.Sub, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1)
                        Text(up.name, color = Col.Text, fontSize = if (d.compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = if (d.compact) 18.sp else 20.sp)
                        // Tipo de estreno y, pegados debajo, los géneros (misma letra que el reparto de las fichas)
                        Column {
                            val lbl = if (d.compact && up.kind == "new") "Nueva serie" else Upcoming.label(up)
                            Text(lbl.uppercase(), color = Col.Text.copy(alpha = 0.75f), fontSize = if (d.compact) 10.sp else 11.sp,
                                fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                lineHeight = if (d.compact) 13.sp else 15.sp)
                            val kind = if (up.type == "tv") com.noirplus.tv.data.Kind.SERIES else com.noirplus.tv.data.Kind.MOVIE
                            val gen = remember(up.genres) { up.genres.mapNotNull { com.noirplus.tv.data.Tmdb.genreNames(kind)[it] }.take(3).joinToString(" · ") }
                            if (gen.isNotEmpty()) Text(gen, color = Col.Sub, fontSize = if (d.compact) 11.sp else 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, lineHeight = if (d.compact) 14.sp else 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        EntryButtons(s, st, onClick)
                    }
                    logo?.let {
                        AsyncImage(it, up.platform, contentScale = ContentScale.Fit,
                            modifier = Modifier.padding(start = 8.dp).size(if (d.compact) 24.dp else 28.dp).clip(RoundedCornerShape(6.dp)))
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
            // 20 de margen por cada lado, casi negro con un borde blanco fino y esquinas muy redondeadas
            Modifier.then(if (compact) Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars) else Modifier)
                .padding(start = 20.dp, end = 20.dp, top = if (compact) 20.dp else 70.dp)
                .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(460.dp))
                .clip(RoundedCornerShape(22.dp)).background(Color(0xF20C0C0E))
                .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
