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
import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay

private val FAV = Category("__fav", "★  Favoritos")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveContent() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val changes = prefs.changes.intValue
    var error by remember { mutableStateOf<String?>(null) }
    val cats by produceState<List<Category>?>(null) {
        value = try {
            src.categories(Kind.LIVE).filter { prefs.visible(Kind.LIVE, it.id) }
        } catch (t: Throwable) {
            error = t.message
            emptyList()
        }
    }
    val favs = remember(changes) { prefs.favorites().filter { it.kind == Kind.LIVE } }
    val favKeys = remember(favs) { favs.map { it.key }.toSet() }
    val allCats = remember(cats, favs.isEmpty()) {
        buildList {
            if (favs.isNotEmpty()) add(FAV)
            addAll(cats ?: emptyList())
        }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(allCats) {
        if (allCats.isNotEmpty() && allCats.none { it.id == selected }) selected = allCats.first().id
    }

    val channels by produceState<List<Item>?>(null, selected, favs) {
        val id = selected ?: return@produceState
        if (id == FAV.id) {
            value = favs
            return@produceState
        }
        value = null
        delay(200)
        value = runCatching { src.items(Kind.LIVE, id) }.getOrDefault(emptyList())
    }

    if (cats == null) return Loading()
    if (allCats.isEmpty()) return Message(error?.let { "No se pudo cargar: $it" } ?: "No hay canales en directo en tu lista")

    val d = LocalDims.current
    val gridFr = remember { FocusRequester() }
    val gridReady = !channels.isNullOrEmpty()
    val grid: @Composable (Modifier) -> Unit = { mod ->
        val list = channels
        when {
            list == null -> Loading(mod)
            list.isEmpty() -> Message("No hay canales en esta categoría", mod)
            else -> key(selected) {
                LazyVerticalGrid(
                    columns = if (d.compact) GridCells.Adaptive(104.dp) else GridCells.Adaptive(150.dp),
                    modifier = mod.focusRequester(gridFr).focusRestorer(),
                    contentPadding = PaddingValues(
                        start = if (d.compact) d.edge else 0.dp, top = 6.dp, end = d.edge, bottom = d.bottomPad
                    ),
                    horizontalArrangement = Arrangement.spacedBy(d.gap),
                    verticalArrangement = Arrangement.spacedBy(d.gap),
                ) {
                    itemsIndexed(list, key = { i, ch -> "$i:${ch.key}" }) { i, ch ->
                        ChannelCard(
                            ch,
                            number = i + 1,
                            isFav = ch.key in favKeys,
                            memKey = "live/${selected}/${ch.key}",
                            onLongClick = { prefs.toggleFavorite(ch) },
                        ) {
                            nav.push(
                                Screen.Player(
                                    PlayRequest(url = src.streamUrl(ch), title = ch.name, key = ch.key, image = ch.image,
                                        isLive = true, channels = list, channelIndex = i)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    val hint = if (d.tv) "OK para ver  ·  Mantén OK pulsado para añadir o quitar de favoritos"
    else "Toca para ver  ·  Mantén pulsado para añadir o quitar de favoritos"

    if (d.compact) {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = d.edge),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(allCats, key = { i, c -> "$i:${c.id}" }) { _, c ->
                    CategoryChip(liveLabel(c.name), selected = c.id == selected) { selected = c.id }
                }
            }
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
            itemsIndexed(allCats, key = { i, c -> "$i:${c.id}" }) { _, c ->
                CategoryItem(
                    liveLabel(c.name),
                    selected = c.id == selected,
                    memKey = "livecat/${c.id}",
                    modifier = Modifier.focusProperties {
                        right = if (gridReady) gridFr else FocusRequester.Cancel
                    },
                ) { selected = c.id }
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                allCats.firstOrNull { it.id == selected }?.let { liveLabel(it.name) } ?: "",
                color = Col.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text(hint, color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            grid(Modifier.fillMaxSize())
        }
    }
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

private fun liveLabel(name: String): String {
    if (name.startsWith("★")) return name
    val p = prettyCategory(name, Kind.LIVE)
    return p.main + (p.year?.let { " ($it)" } ?: "")
}
