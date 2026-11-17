package com.noirplus.tv.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirplus.tv.data.Details
import com.noirplus.tv.data.Episode
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Progress
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.SeriesDetails
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(item: Item) {
    if (item.kind == Kind.SERIES && item.url == null) SeriesDetail(item) else MovieDetail(item)
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format("%d:%02d", s / 60, s % 60)
}

/** Quita el nombre de la serie y el "S01E02" que muchos paneles ponen en el título. */
private fun cleanTitle(title: String, series: String): String {
    var t = title
    if (t.startsWith(series, ignoreCase = true)) t = t.substring(series.length)
    t = t.replace(Regex("^[\\s\\-:|·]*S\\d+\\s*E\\d+[\\s\\-:|·]*", RegexOption.IGNORE_CASE), "")
    t = t.trim(' ', '-', ':', '|', '·')
    return t.ifEmpty { title }
}

// ───────────────────────────── Piezas TV ─────────────────────────────

@Composable
private fun TvInfo(item: Item, details: Details?) {
    Text(item.name, color = Col.Text, fontFamily = InterDisplay, fontSize = 38.sp, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 42.sp, letterSpacing = (-1).sp)
    MetaLine(
        year = details?.year ?: item.year,
        rating = details?.rating ?: item.rating,
        genre = null,
        duration = details?.duration,
        modifier = Modifier.padding(top = 10.dp),
    )
    GenreChips(details?.genre, Modifier.padding(top = 10.dp))
    val plot = details?.plot ?: item.plot
    if (plot != null) {
        Text(plot, color = Col.Text.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
    }
    val credits = listOfNotNull(details?.director?.let { "Dirección: $it" }, details?.cast?.let { "Reparto: $it" })
    credits.forEach {
        Text(it, color = Col.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, memKey: String, onFocus: () -> Unit) {
    Focusable(key = memKey, shape = RoundedCornerShape(50), focusScale = 1f, onFocus = onFocus, onClick = onFocus) { focused ->
        Text(
            label,
            Modifier
                .background(
                    when {
                        focused -> Color.White
                        selected -> Col.GlassStrong
                        else -> Col.Glass
                    }
                )
                .padding(horizontal = 16.dp, vertical = 7.dp),
            color = if (focused) Color.Black else Col.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ───────────────────────────── Piezas móvil ─────────────────────────────

/** Imagen grande arriba, título centrado, datos, estrellas y géneros. */
@Composable
private fun PhoneHeader(item: Item, details: Details?, extra: String? = null) {
    val nav = LocalNav.current
    Box(Modifier.fillMaxWidth().aspectRatio(0.92f)) {
        AsyncImage(
            model = details?.backdrop ?: item.image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f), 0.22f to Color.Transparent, 0.6f to Color.Transparent, 1f to Col.Bg,
                )
            )
        )
        Box(
            Modifier.padding(12.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { nav.pop() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            item.name, color = Col.Text, fontFamily = InterDisplay, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 34.sp, letterSpacing = (-0.8).sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        val parts = listOfNotNull(details?.year ?: item.year, extra, details?.duration)
        if (parts.isNotEmpty()) {
            Text(parts.joinToString("   |   "), color = Col.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        StarRow(details?.rating ?: item.rating, size = 20.dp, modifier = Modifier.padding(top = 12.dp))
        GenreChips(details?.genre, Modifier.padding(top = 12.dp), max = 3)
    }
}

@Composable
private fun IconLabel(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Col.Text, modifier = Modifier.size(28.dp))
        Text(label, color = Col.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Mi lista · botón grande de reproducir · Info */
@Composable
private fun PhoneActions(fav: Boolean, onFav: () -> Unit, onPlay: () -> Unit, onInfo: () -> Unit, playHint: String?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconLabel(if (fav) Icons.Filled.Check else Icons.Filled.Add, "Mi lista", onFav)
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable { onPlay() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, "Reproducir", tint = Color.Black, modifier = Modifier.size(40.dp)) }
        IconLabel(Icons.Filled.Info, "Info", onInfo)
    }
    if (playHint != null) {
        Text(playHint, color = Col.Sub, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun PhonePlot(details: Details?, item: Item, expanded: Boolean) {
    val plot = details?.plot ?: item.plot ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).animateContentSize()) {
        Text(plot, color = Col.Text.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
        if (expanded) {
            listOfNotNull(details?.director?.let { "Dirección: $it" }, details?.cast?.let { "Reparto: $it" }).forEach {
                Text(it, color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(ep: Episode, seriesName: String, fallbackImage: String?, progress: Progress?, onLongClick: () -> Unit, onClick: () -> Unit) {
    val mine = Repo.prefs.let { p -> remember(p.changes.intValue) { p.rating("EP:${ep.id}")?.stars } }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(136.dp).aspectRatio(16f / 9f).clip(CardShape).background(Col.Surface)) {
            AsyncImage(model = ep.image ?: fallbackImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            val f = progress?.fraction ?: 0f
            if (f > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("${ep.number}. ${cleanTitle(ep.title, seriesName)}", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            ep.plot?.let {
                Text(it, color = Col.Sub, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ep.duration?.let { Text(it, color = Col.Sub, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp)) }
                if (mine != null) {
                    HalfStars(mine, size = 11.dp, gap = 1.dp)
                    Text(fmtStars(mine), color = Col.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ───────────────────────────── Película ─────────────────────────────

@Composable
private fun MovieDetail(item: Item) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val mem = LocalFocusMemory.current
    val d = LocalDims.current
    val changes = prefs.changes.intValue
    val details by produceState<Details?>(null) { value = runCatching { src.movieDetails(item) }.getOrNull() }
    val progress = remember(changes) { prefs.progress(item.key) }
    val fav = remember(changes) { prefs.isFavorite(item.key) }
    val playFr = remember { FocusRequester() }
    val rate = LocalRate.current
    val mine = remember(changes) { prefs.rating(item.key) }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!d.tv) return@LaunchedEffect
        delay(if (mem.restore) 600 else 120)
        if (!hasFocus) runCatching { playFr.requestFocus() }
    }

    fun play(start: Long) {
        nav.push(
            Screen.Player(
                PlayRequest(url = src.streamUrl(item), title = item.name, key = item.key, image = item.image,
                    isLive = false, startMs = start)
            )
        )
    }

    if (d.compact) {
        var expanded by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            PhoneHeader(item, details, extra = "Película")
            PhoneActions(
                fav = fav,
                onFav = { prefs.toggleFavorite(item) },
                onPlay = { play(progress?.position ?: 0L) },
                onInfo = { expanded = !expanded },
                playHint = progress?.let { "Continuar desde ${fmt(it.position)}" },
            )
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                MyRatingLine(item.key, { rate(item.rateTarget()) })
            }
            PhonePlot(details, item, expanded)
            if (progress != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ActionButton("Ver desde el principio") { play(0) }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        Backdrop(details?.backdrop ?: item.image)
        Column(
            Modifier.fillMaxWidth(0.58f).verticalScroll(rememberScrollState())
                .padding(start = d.edge + 8.dp, top = 56.dp, end = 24.dp, bottom = 32.dp)
        ) {
            TvInfo(item, details)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress != null) {
                    ActionButton("▶  Continuar (${fmt(progress.position)})", primary = true, key = "d-play", focusRequester = playFr) {
                        play(progress.position)
                    }
                    ActionButton("Desde el principio", key = "d-restart") { play(0) }
                } else {
                    ActionButton("▶  Reproducir", primary = true, key = "d-play", focusRequester = playFr) { play(0) }
                }
                ActionButton(if (fav) "✓  En mi lista" else "+  Mi lista", key = "d-fav") { prefs.toggleFavorite(item) }
                ActionButton(mine?.let { "Tu nota ${fmtStars(it.stars)} ★" } ?: "Valorar", key = "d-rate") { rate(item.rateTarget()) }
            }
        }
    }
}

// ───────────────────────────── Serie ─────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeriesDetail(item: Item) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val mem = LocalFocusMemory.current
    val d = LocalDims.current
    val changes = prefs.changes.intValue
    var failed by remember { mutableStateOf(false) }
    val sd by produceState<SeriesDetails?>(null) {
        val r = runCatching { src.seriesDetails(item) }.getOrNull()
        if (r == null) failed = true
        value = r
    }
    val seasons = sd?.seasons?.keys?.toList() ?: emptyList()
    var season by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && season !in seasons) season = seasons.first()
    }
    val last = remember(changes) { prefs.lastOf(item.key) }
    val fav = remember(changes) { prefs.isFavorite(item.key) }
    val progressMap = remember(changes) { prefs.history().associateBy { it.key } }
    val episodes = sd?.seasons?.get(season) ?: emptyList()
    val first = sd?.seasons?.values?.firstOrNull()?.firstOrNull()
    val playFr = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!d.tv) return@LaunchedEffect
        delay(if (mem.restore) 700 else 150)
        if (!hasFocus) runCatching { playFr.requestFocus() }
    }

    val queue = remember(sd) { sd?.seasons?.values?.flatten() ?: emptyList() }
    val rate = LocalRate.current
    fun epTarget(ep: Episode) = RateTarget(
        "EP:${ep.id}", "${item.name} · T${ep.season} E${ep.number} · ${cleanTitle(ep.title, item.name)}",
        ep.image ?: item.image, item, "Episodio",
    )

    fun playEpisode(ep: Episode, start: Long) {
        nav.push(
            Screen.Player(
                PlayRequest(
                    url = src.episodeUrl(ep),
                    title = "${item.name} · T${ep.season} E${ep.number}",
                    key = "EP:${ep.id}",
                    image = ep.image ?: item.image,
                    isLive = false,
                    startMs = start,
                    parent = item.key,
                    episodes = queue,
                    episodeIndex = queue.indexOfFirst { it.id == ep.id },
                    seriesName = item.name,
                )
            )
        )
    }

    fun playMain() {
        val l = last
        when {
            l != null -> {
                val idx = queue.indexOfFirst { "EP:${it.id}" == l.key }
                nav.push(
                    Screen.Player(
                        PlayRequest(url = l.url, title = l.title, key = l.key, image = l.image, isLive = false, startMs = l.position,
                            parent = item.key, episodes = queue, episodeIndex = idx, seriesName = item.name)
                    )
                )
            }
            first != null -> playEpisode(first, 0)
        }
    }

    if (d.compact) {
        var expanded by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            PhoneHeader(item, sd?.details, extra = if (seasons.isNotEmpty()) "${seasons.size} temporada" + (if (seasons.size > 1) "s" else "") else "Serie")
            PhoneActions(
                fav = fav,
                onFav = { prefs.toggleFavorite(item) },
                onPlay = { playMain() },
                onInfo = { expanded = !expanded },
                playHint = when {
                    last != null -> "Continuar · " + last.title.substringAfterLast("· ")
                    first != null -> "Empezar · T${first.season} E${first.number}"
                    else -> null
                },
            )
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                MyRatingLine(item.key, { rate(item.rateTarget()) })
            }
            PhonePlot(sd?.details, item, expanded)
            when {
                sd == null && !failed -> Text("Cargando episodios…", color = Col.Sub, modifier = Modifier.padding(horizontal = 20.dp))
                seasons.isEmpty() -> Text("No hay episodios disponibles", color = Col.Sub, modifier = Modifier.padding(horizontal = 20.dp))
                else -> {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        seasons.forEach { s ->
                            val sel = s == season
                            Column(
                                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { season = s },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Temporada $s", color = if (sel) Col.Text else Col.Sub, fontSize = 16.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                                Box(
                                    Modifier.padding(top = 5.dp).width(if (sel) 28.dp else 0.dp).height(3.dp)
                                        .clip(RoundedCornerShape(50)).background(Col.Text)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    episodes.forEach { ep ->
                        val p = progressMap["EP:${ep.id}"]
                        EpisodeRow(ep, item.name, item.image, p, onLongClick = { rate(epTarget(ep)) }) { playEpisode(ep, p?.position ?: 0L) }
                    }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        Backdrop(sd?.details?.backdrop ?: item.image)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 36.dp, bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth(0.6f).padding(start = d.edge + 8.dp, end = 24.dp)) {
                TvInfo(item, sd?.details)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        last != null -> ActionButton("▶  Continuar  ·  ${last.title.substringAfterLast("· ")}", primary = true,
                            key = "s-play", focusRequester = playFr) { playMain() }
                        first != null -> ActionButton("▶  Ver T${first.season} E${first.number}", primary = true,
                            key = "s-play", focusRequester = playFr) { playMain() }
                    }
                    ActionButton(if (fav) "✓  En mi lista" else "+  Mi lista", key = "s-fav",
                        focusRequester = if (last == null && first == null) playFr else null) { prefs.toggleFavorite(item) }
                    val mineS = remember(changes) { prefs.rating(item.key) }
                    ActionButton(mineS?.let { "Tu nota ${fmtStars(it.stars)} ★" } ?: "Valorar", key = "s-rate") { rate(item.rateTarget()) }
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                sd == null && !failed -> Text("Cargando episodios…", color = Col.Sub, modifier = Modifier.padding(start = d.edge + 8.dp))
                seasons.isEmpty() -> Text("No hay episodios disponibles", color = Col.Sub, modifier = Modifier.padding(start = d.edge + 8.dp))
                else -> {
                    LazyRow(
                        Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = d.edge + 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(seasons, key = { it }) { s ->
                            SeasonChip("Temporada $s", selected = s == season, memKey = "season/$s") { season = s }
                        }
                    }
                    LazyRow(
                        Modifier.focusRestorer(),
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(horizontal = d.edge + 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            val p = progressMap["EP:${ep.id}"]
                            WideCard(
                                title = "${ep.number}. ${cleanTitle(ep.title, item.name)}",
                                image = ep.image ?: item.image,
                                progress = p?.fraction,
                                memKey = "ep/${ep.id}",
                                onLongClick = { rate(epTarget(ep)) },
                            ) { playEpisode(ep, p?.position ?: 0L) }
                        }
                    }
                }
            }
        }
    }
}
