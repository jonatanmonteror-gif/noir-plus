package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Details
import com.noirplus.tv.data.Episode
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.SeriesDetails
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(item: Item) {
    if (item.kind == Kind.SERIES && item.url == null) SeriesDetail(item) else MovieDetail(item)
}

@Composable
private fun InfoBlock(item: Item, details: Details?) {
    val compact = LocalDims.current.compact
    Text(item.name, color = Col.Text, fontFamily = InterDisplay, fontSize = if (compact) 28.sp else 38.sp, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = if (compact) 32.sp else 42.sp, letterSpacing = (-1).sp)
    MetaLine(
        year = details?.year ?: item.year,
        rating = details?.rating ?: item.rating,
        genre = details?.genre,
        duration = details?.duration,
        modifier = Modifier.padding(top = 10.dp),
    )
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

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format("%d:%02d", s / 60, s % 60)
}

@Composable
private fun MovieDetail(item: Item) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val mem = LocalFocusMemory.current
    val changes = prefs.changes.intValue
    val details by produceState<Details?>(null) { value = runCatching { src.movieDetails(item) }.getOrNull() }
    val progress = remember(changes) { prefs.progress(item.key) }
    val fav = remember(changes) { prefs.isFavorite(item.key) }
    val playFr = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
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

    val d = LocalDims.current
    Box(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        Backdrop(details?.backdrop ?: item.image)
        Column(
            Modifier.fillMaxWidth(if (d.compact) 1f else 0.58f).verticalScroll(rememberScrollState())
                .padding(start = d.edge + 8.dp, top = if (d.compact) 190.dp else (d.edge + 8.dp), end = if (d.compact) d.edge else 24.dp, bottom = 32.dp)
        ) {
            InfoBlock(item, details)
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
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeriesDetail(item: Item) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val mem = LocalFocusMemory.current
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
    val playFr = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(if (mem.restore) 700 else 150)
        if (!hasFocus) runCatching { playFr.requestFocus() }
    }

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
                )
            )
        )
    }

    val d = LocalDims.current
    Box(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        Backdrop(sd?.details?.backdrop ?: item.image)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = if (d.compact) 170.dp else 36.dp, bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth(if (d.compact) 1f else 0.6f).padding(start = d.edge + 8.dp, end = if (d.compact) d.edge else 24.dp)) {
                InfoBlock(item, sd?.details)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val first = sd?.seasons?.values?.firstOrNull()?.firstOrNull()
                    when {
                        last != null -> ActionButton("▶  Continuar  ·  ${last.title.substringAfterLast("· ")}", primary = true,
                            key = "s-play", focusRequester = playFr) {
                            nav.push(
                                Screen.Player(
                                    PlayRequest(url = last.url, title = last.title, key = last.key, image = last.image,
                                        isLive = false, startMs = last.position, parent = item.key)
                                )
                            )
                        }
                        first != null -> ActionButton("▶  Ver T${first.season} E${first.number}", primary = true,
                            key = "s-play", focusRequester = playFr) { playEpisode(first, 0) }
                    }
                    ActionButton(if (fav) "✓  En mi lista" else "+  Mi lista", key = "s-fav",
                        focusRequester = if (last == null && first == null) playFr else null) { prefs.toggleFavorite(item) }
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                sd == null && !failed -> Text("Cargando episodios…", color = Col.Sub, modifier = Modifier.padding(start = (d.edge + 8.dp)))
                sd == null || seasons.isEmpty() -> Text("No hay episodios disponibles", color = Col.Sub, modifier = Modifier.padding(start = (d.edge + 8.dp)))
                else -> {
                    LazyRow(
                        Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = (d.edge + 8.dp), vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(seasons, key = { it }) { s ->
                            SeasonChip("Temporada $s", selected = s == season, memKey = "season/$s") { season = s }
                        }
                    }
                    LazyRow(
                        Modifier.focusRestorer(),
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(horizontal = (d.edge + 8.dp), vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            val p = progressMap["EP:${ep.id}"]
                            WideCard(
                                title = "${ep.number}. ${cleanTitle(ep.title, item.name)}",
                                image = ep.image ?: item.image,
                                progress = p?.fraction,
                                memKey = "ep/${ep.id}",
                                
                            ) { playEpisode(ep, p?.position ?: 0L) }
                        }
                    }
                }
            }
        }
    }
}

/** Quita el nombre de la serie y el "S01E02" que muchos paneles ponen en el título. */
private fun cleanTitle(title: String, series: String): String {
    var t = title
    if (t.startsWith(series, ignoreCase = true)) t = t.substring(series.length)
    t = t.replace(Regex("^[\\s\\-:|·]*S\\d+\\s*E\\d+[\\s\\-:|·]*", RegexOption.IGNORE_CASE), "")
    t = t.trim(' ', '-', ':', '|', '·')
    return t.ifEmpty { title }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, memKey: String, onFocus: () -> Unit) {
    Focusable(
        key = memKey,
        shape = RoundedCornerShape(50),
        focusScale = 1f,
        border = false,
        onFocus = onFocus,
        onClick = onFocus,
    ) { focused ->
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
