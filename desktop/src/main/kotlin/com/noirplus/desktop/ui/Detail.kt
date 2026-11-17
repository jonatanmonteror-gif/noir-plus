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
import com.noirplus.desktop.data.*

@Composable
fun DetailScreen(item: Item) {
    if (item.kind == Kind.SERIES) SeriesDetail(item) else MovieDetail(item)
}

@Composable
private fun Header(item: Item, d: Details?, extra: String?) {
    val nav = LocalNav.current
    Text("‹  Volver", color = Col.Sub, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { nav.pop() }
        .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp, vertical = 6.dp))
    Text(item.name, color = Col.Text, fontSize = 44.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold, lineHeight = 48.sp,
        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
    val meta = listOfNotNull(d?.year ?: item.year, extra, d?.duration, d?.genre).filter { it.isNotBlank() }.joinToString("   ·   ")
    if (meta.isNotBlank()) Text(meta, color = Col.Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    (d?.rating ?: item.rating)?.toFloatOrNull()?.takeIf { it > 0 }?.let { r ->
        val stars = if (r > 5) r / 2 else r
        Text("★".repeat(stars.toInt().coerceIn(0, 5)) + "☆".repeat(5 - stars.toInt().coerceIn(0, 5)) + "  " + String.format("%.1f", stars),
            color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
    }
    (d?.plot ?: item.plot)?.let {
        Text(it, color = Col.Text.copy(alpha = 0.88f), fontSize = 16.sp, lineHeight = 23.sp, maxLines = 6, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp).widthIn(max = 720.dp))
    }
    d?.cast?.takeIf { it.isNotBlank() }?.let {
        Text("Reparto: $it", color = Col.Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp).widthIn(max = 720.dp))
    }
    d?.director?.takeIf { it.isNotBlank() }?.let {
        Text("Dirección: $it", color = Col.Sub, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MovieDetail(item: Item) {
    val src = Repo.source ?: return
    val nav = LocalNav.current
    val changes = Store.changes.intValue
    val details by produceState<Details?>(null, item.key) { value = runCatching { src.movieDetails(item) }.getOrNull() }
    val progress = remember(changes) { Store.progress(item.key) }
    val fav = remember(changes) { Store.isFavorite(item) }
    fun play(start: Long) = nav.push(Screen.Player(PlayRequest(url = src.streamUrl(item), title = item.name, key = item.key,
        image = item.image, isLive = false, startMs = start)))

    Box(Modifier.fillMaxSize()) {
        Backdrop(details?.backdrop ?: item.image, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 40.dp, end = 48.dp)) {
            Header(item, details, "Película")
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress != null) {
                    NButton("▶  Continuar (${fmtTime(progress.position)})", primary = true) { play(progress.position) }
                    NButton("Desde el principio") { play(0) }
                } else NButton("▶  Reproducir", primary = true) { play(0) }
                NButton(if (fav) "✓  En mi lista" else "+  Mi lista") { Store.toggleFavorite(item) }
            }
        }
    }
}

@Composable
private fun SeriesDetail(item: Item) {
    val src = Repo.source ?: return
    val nav = LocalNav.current
    val changes = Store.changes.intValue
    var failed by remember { mutableStateOf(false) }
    val sd by produceState<SeriesDetails?>(null, item.key) {
        val r = runCatching { src.seriesDetails(item) }.getOrNull()
        if (r == null) failed = true
        value = r
    }
    val fav = remember(changes) { Store.isFavorite(item) }
    val seasons = sd?.seasons?.keys?.toList().orEmpty()
    var season by remember(sd) { mutableStateOf(seasons.firstOrNull() ?: 1) }
    val history = remember(changes) { Store.history() }
    val last = remember(history) { history.firstOrNull { it.parent == item.key } }
    val allEps = remember(sd) { sd?.seasons?.values?.flatten().orEmpty() }

    fun playEp(ep: Episode, start: Long) {
        val idx = allEps.indexOf(ep)
        nav.push(Screen.Player(PlayRequest(url = src.episodeUrl(ep), title = "${item.name} · T${ep.season} E${ep.number}",
            key = "EP:${ep.id}", image = ep.image ?: item.image, isLive = false, startMs = start, parent = item.key,
            episodes = allEps, episodeIndex = idx, seriesName = item.name)))
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(sd?.details?.backdrop ?: item.image, Modifier.fillMaxSize())
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 48.dp, top = 40.dp, end = 48.dp, bottom = 40.dp)) {
            item {
                Header(item, sd?.details, if (seasons.isNotEmpty()) "${seasons.size} temporada" + (if (seasons.size > 1) "s" else "") else "Serie")
                Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val first = allEps.firstOrNull()
                    if (last != null) NButton("▶  Continuar · " + last.title.substringAfterLast("· "), primary = true) {
                        nav.push(Screen.Player(PlayRequest(url = last.url, title = last.title, key = last.key, image = last.image, isLive = false,
                            startMs = last.position, parent = item.key, episodes = allEps,
                            episodeIndex = allEps.indexOfFirst { "EP:${it.id}" == last.key }, seriesName = item.name)))
                    } else if (first != null) NButton("▶  Ver T${first.season} E${first.number}", primary = true) { playEp(first, 0) }
                    NButton(if (fav) "✓  En mi lista" else "+  Mi lista") { Store.toggleFavorite(item) }
                }
                Spacer(Modifier.height(28.dp))
                when {
                    sd == null && !failed -> Text("Cargando episodios…", color = Col.Sub)
                    seasons.isEmpty() -> Text("No hay episodios disponibles", color = Col.Sub)
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { s ->
                            val sel = s == season
                            Text("Temporada $s", color = if (sel) Color.Black else Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.White.copy(alpha = 0.1f))
                                    .clickable { season = s }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            items(sd?.seasons?.get(season).orEmpty(), key = { it.id }) { ep ->
                val p = history.firstOrNull { it.key == "EP:${ep.id}" }
                EpisodeRow(ep, item, p) { playEp(ep, p?.position ?: 0L) }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, series: Item, progress: Progress?, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().widthIn(max = 980.dp).padding(vertical = 5.dp).clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }.pointerHoverIcon(PointerIcon.Hand).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(220.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Col.Surface2)
            .then(if (hovered) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)) {
            (ep.image ?: series.image)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Text("▶", color = Color.White, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 4.dp))
            if (progress != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(progress.fraction).fillMaxHeight().background(Color.White))
            }
        }
        Column(Modifier.padding(start = 18.dp).weight(1f)) {
            val t = ep.title.removePrefix(series.name).trim(' ', '-', ':', '|', '·').ifBlank { "Episodio ${ep.number}" }
            Text("${ep.number}. $t", color = Col.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ep.duration?.let { Text(it, color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
            ep.plot?.let { Text(it, color = Col.Sub, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
