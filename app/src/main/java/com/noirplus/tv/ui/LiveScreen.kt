package com.noirplus.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.LiveIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip

private const val FAV = "__fav"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveContent() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val menu = LocalItemMenu.current
    val changes = prefs.changes.intValue
    var error by remember { mutableStateOf<String?>(null) }

    // Todos los canales, ordenados por temática y con las calidades juntas
    val overrides = remember(changes) { prefs.liveThemes() }
    val index by produceState<LiveIndex.Index?>(null, Repo.dataVersion.intValue, overrides) {
        value = try {
            withContext(Dispatchers.Default) {
                val cats = src.categories(Kind.LIVE)
                val visible = cats.filter { prefs.visible(Kind.LIVE, it.id) }
                val names = visible.associate { it.id to it.name }
                val all = src.all(Kind.LIVE).filter { it.categoryId in names }
                LiveIndex.build(all, { names[it] }, overrides)
            }
        } catch (t: Throwable) {
            error = t.message
            LiveIndex.Index(emptyList())
        }
    }
    val favs = remember(changes) { prefs.favorites().filter { it.kind == Kind.LIVE } }
    val favKeys = remember(favs) { favs.map { it.key }.toSet() }
    val idx = index
    val folders = remember(idx) {
        listOf(FAV to "★  Favoritos") + (idx?.themes ?: emptyList()).map { it.id to it.label }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(idx, favs.isEmpty()) {
        if (idx != null && folders.none { it.first == selected }) {
            selected = if (favs.isNotEmpty()) FAV else folders.getOrNull(1)?.first ?: FAV
        }
    }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selected) { country = null }

    val inFolder: List<LiveIndex.Channel> = remember(idx, selected, favs) {
        when {
            idx == null -> emptyList()
            selected == FAV -> favs.map { f ->
                idx.channelOf(f.key) ?: LiveIndex.Channel("-|${f.key}", f.name, null, "other", listOf(f))
            }
            else -> idx.byTheme(selected ?: "")
        }
    }
    val countries = remember(inFolder) {
        inFolder.mapNotNull { it.country }.groupingBy { it }.eachCount().entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { if (it.key == "ES") 0 else 1 }.thenByDescending { it.value })
            .map { it.key }
    }
    val channels = remember(inFolder, country) { if (country == null) inFolder else inFolder.filter { it.country == country } }

    if (idx == null) return Loading()
    if (idx.channels.isEmpty() && favs.isEmpty()) return Message(error?.let { "No se pudo cargar: $it" } ?: "No hay canales en directo en tu lista")

    val d = LocalDims.current
    val asList = prefs.liveView == "list"
    val gridFr = remember { FocusRequester() }
    val gridReady = channels.isNotEmpty()
    val play: (Int) -> Unit = { i ->
        val list = channels.map { it.item }
        val ch = list[i]
        nav.push(
            Screen.Player(
                PlayRequest(url = src.streamUrl(ch), title = ch.name, key = ch.key, image = ch.image,
                    isLive = true, channels = list, channelIndex = i)
            )
        )
    }
    val longPress: (LiveIndex.Channel) -> Unit = { ch ->
        menu(
            ch.item.menuTarget().copy(
                label = "Canal" + (ch.country?.let { " · " + (LiveIndex.COUNTRY_NAMES[it] ?: it) } ?: ""),
                theme = ch.theme,
                onTheme = if (ch.key.startsWith("-|")) null else ({ t -> prefs.setLiveTheme(ch.key, t) }),
            )
        )
    }
    val grid: @Composable (Modifier) -> Unit = { mod ->
        when {
            channels.isEmpty() -> Message(
                if (selected == FAV) "Aún no tienes canales favoritos.\nMantén pulsado un canal para añadirlo." else "No hay canales aquí", mod)
            asList -> key(selected, country, "list") {
                LazyColumn(
                    mod.focusRequester(gridFr).focusRestorer(),
                    contentPadding = PaddingValues(start = if (d.compact) d.edge else 0.dp, top = 6.dp, end = d.edge, bottom = d.bottomPad),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(channels, key = { i, ch -> "$i:${ch.key}" }) { i, ch ->
                        ChannelRow(ch, i + 1, ch.main.key in favKeys || ch.alts.any { it.key in favKeys }, "live/$selected/${ch.key}",
                            onLongClick = { longPress(ch) }) { play(i) }
                    }
                }
            }
            else -> key(selected, country, "grid") {
                LazyVerticalGrid(
                    columns = if (d.compact) GridCells.Adaptive(104.dp) else GridCells.Adaptive(150.dp),
                    modifier = mod.focusRequester(gridFr).focusRestorer(),
                    contentPadding = PaddingValues(
                        start = if (d.compact) d.edge else 0.dp, top = 6.dp, end = d.edge, bottom = d.bottomPad
                    ),
                    horizontalArrangement = Arrangement.spacedBy(d.gap),
                    verticalArrangement = Arrangement.spacedBy(d.gap),
                ) {
                    itemsIndexed(channels, key = { i, ch -> "$i:${ch.key}" }) { i, ch ->
                        ChannelCard(
                            ch.item,
                            number = i + 1,
                            isFav = ch.main.key in favKeys || ch.alts.any { it.key in favKeys },
                            memKey = "live/${selected}/${ch.key}",
                            onLongClick = { longPress(ch) },
                        ) { play(i) }
                    }
                }
            }
        }
    }
    // Barra de arriba: países (si hay varios) y cajas / lista
    val toolbar: @Composable () -> Unit = {
        LazyRow(
            contentPadding = PaddingValues(start = if (d.compact) d.edge else 0.dp, end = d.edge),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "view") {
                CategoryChip(if (asList) "▦  Ver en cajas" else "☰  Ver en lista", selected = false) {
                    prefs.liveView = if (asList) "grid" else "list"
                }
            }
            if (countries.size > 1) {
                item(key = "c-all") { CategoryChip("Todos los países", selected = country == null) { country = null } }
                itemsIndexed(countries, key = { _, c -> "c-$c" }) { _, c ->
                    CategoryChip(LiveIndex.COUNTRY_NAMES[c] ?: c, selected = country == c) { country = c }
                }
            }
        }
    }
    val hint = if (d.tv) "OK para ver  ·  Mantén OK pulsado: favoritos y temática"
    else "Toca para ver  ·  Mantén pulsado: favoritos y temática"

    if (d.compact) {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = d.edge),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(folders, key = { i, c -> "$i:${c.first}" }) { _, c ->
                    CategoryChip(c.second, selected = c.first == selected) { selected = c.first }
                }
            }
            Spacer(Modifier.height(8.dp))
            toolbar()
            Text(hint, color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = d.edge, vertical = 10.dp))
            grid(Modifier.fillMaxSize())
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(start = d.edge, top = 12.dp)) {
        LazyColumn(
            Modifier.width(240.dp).fillMaxHeight().focusRestorer(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(folders, key = { i, c -> "$i:${c.first}" }) { _, c ->
                CategoryItem(
                    c.second,
                    selected = c.first == selected,
                    memKey = "livecat/${c.first}",
                    modifier = Modifier.focusProperties {
                        right = if (gridReady) gridFr else FocusRequester.Default
                    },
                ) { selected = c.first }
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                folders.firstOrNull { it.first == selected }?.second ?: "",
                color = Col.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text(hint, color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            toolbar()
            Spacer(Modifier.height(8.dp))
            grid(Modifier.fillMaxSize())
        }
    }
}

/** Canal en modo lista: número, logo, nombre y país. */
@Composable
private fun ChannelRow(ch: LiveIndex.Channel, number: Int, isFav: Boolean, memKey: String, onLongClick: () -> Unit, onClick: () -> Unit) {
    Focusable(Modifier.fillMaxWidth().height(56.dp), key = memKey, shape = RoundedCornerShape(10.dp), focusScale = 1.02f,
        onLongClick = onLongClick, onClick = onClick) { focused ->
        Row(
            Modifier.fillMaxSize().background(if (focused) Color.White else Col.Surface).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(number.toString(), color = if (focused) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 13.sp,
                modifier = Modifier.width(36.dp))
            Box(Modifier.width(64.dp).fillMaxHeight().padding(vertical = 6.dp).clip(RoundedCornerShape(6.dp)).background(Col.Surface2),
                contentAlignment = Alignment.Center) {
                if (ch.main.image != null) AsyncImage(ch.main.image, null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp))
            }
            Text(ch.name, color = if (focused) Color.Black else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp).weight(1f))
            if (ch.alts.size > 1) Text(ch.qualityLabel(), color = if (focused) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp))
            ch.country?.let { Text(it, color = if (focused) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp)) }
            if (isFav) Text("★", color = if (focused) Color.Black else Col.Star, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

private fun LiveIndex.Channel.qualityLabel(): String = when {
    bestQ >= 40 -> "4K"
    bestQ >= 30 -> "FHD"
    bestQ >= 20 -> "HD"
    else -> "SD"
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(shape = RoundedCornerShape(50), focusScale = 1f, onClick = onClick) { focused ->
        Text(
            name,
            Modifier
                .background(if (selected || focused) Color.White else Col.Surface2)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected || focused) Color.Black else Col.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryItem(name: String, selected: Boolean, memKey: String, modifier: Modifier = Modifier, onFocus: () -> Unit) {
    Focusable(
        modifier.fillMaxWidth(),
        key = memKey,
        shape = RoundedCornerShape(8.dp),
        focusScale = 1f,
        border = false,
        onFocus = onFocus,
        onClick = onFocus,
    ) { focused ->
        Text(
            name,
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        focused -> Color.White
                        selected -> Col.Surface
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            color = when {
                focused -> Color.Black
                selected -> Col.Text
                else -> Col.Sub
            },
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelCard(
    ch: Item,
    number: Int,
    isFav: Boolean,
    memKey: String,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    Column {
        Focusable(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f),
            key = memKey,
            focusScale = 1.06f,
            onLongClick = onLongClick,
            onClick = onClick,
        ) {
            Box(Modifier.fillMaxSize().background(Col.Surface))
            Text(
                ch.name, Modifier.align(Alignment.Center).padding(8.dp),
                color = Col.Sub, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 3,
            )
            if (ch.image != null) {
                AsyncImage(
                    model = ch.image, contentDescription = ch.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Col.Surface2).padding(14.dp),
                )
            }
            Text(
                number.toString(),
                Modifier.align(Alignment.TopStart).padding(6.dp),
                color = Col.Sub, fontSize = 11.sp,
            )
            if (isFav) {
                Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Col.Star, fontSize = 14.sp)
            }
        }
        Text(
            ch.name, color = Col.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
