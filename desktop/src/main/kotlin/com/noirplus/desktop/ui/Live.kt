package com.noirplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import com.noirplus.desktop.data.*

private const val FAV = "__fav"

/** Directo: Favoritos y temáticas a la izquierda; canales (cajas o lista) a la derecha. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScreen() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val dv = Repo.dataVersion.intValue
    val changes = Store.changes.intValue
    var error by remember { mutableStateOf<String?>(null) }
    val overrides = remember(changes) { Store.liveThemes() }
    val index by produceState<LiveIndex.Index?>(null, dv, overrides) {
        value = try {
            withContext(Dispatchers.Default) {
                val names = src.categories(Kind.LIVE).associate { it.id to it.name }
                LiveIndex.build(src.all(Kind.LIVE), { names[it] }, overrides)
            }
        } catch (t: Throwable) { error = friendlyError(t); LiveIndex.Index(emptyList()) }
    }
    val favs = remember(changes) { Store.favorites().filter { it.kind == Kind.LIVE } }
    val favKeys = remember(favs) { favs.map { it.key }.toSet() }
    val idx = index
    val folders = remember(idx) { listOf(FAV to "★  Favoritos") + (idx?.themes ?: emptyList()).map { it.id to it.label } }
    var sel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(idx) { if (idx != null && folders.none { it.first == sel }) sel = if (favs.isNotEmpty()) FAV else folders.getOrNull(1)?.first ?: FAV }
    var country by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sel) { country = null }
    var query by remember { mutableStateOf("") }
    val inFolder = remember(idx, sel, favs) {
        when {
            idx == null -> emptyList()
            sel == FAV -> favs.map { f -> idx.channelOf(f.key) ?: LiveIndex.Channel("-|${f.key}", f.name, null, "other", listOf(f)) }
            else -> idx.byTheme(sel ?: "")
        }
    }
    val countries = remember(inFolder) {
        inFolder.mapNotNull { it.country }.groupingBy { it }.eachCount().entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { if (it.key == "ES") 0 else 1 }.thenByDescending { it.value }).map { it.key }
    }
    val shown = remember(inFolder, country, query) {
        val q = normText(query.trim())
        inFolder.filter { (country == null || it.country == country) && (q.isEmpty() || normText(it.name).contains(q)) }
    }
    val asList = Store.liveView == "list"
    when {
        idx == null -> Loading()
        idx.channels.isEmpty() && favs.isEmpty() -> Message(error?.let { "No se pudo cargar: $it" } ?: "No hay canales")
        else -> Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF0E0E10)), contentPadding = PaddingValues(12.dp)) {
                item {
                    Text("Directo", color = Col.Text, fontSize = 28.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 14.dp))
                }
                itemsIndexed(folders, key = { _, it -> it.first }) { _, f ->
                    val s = f.first == sel
                    val src2 = remember { MutableInteractionSource() }
                    val h by src2.collectIsHoveredAsState()
                    Text(f.second, color = if (s) Color.Black else Col.Text, fontSize = 14.sp,
                        fontWeight = if (s) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp))
                            .background(when { s -> Color.White; h -> Color.White.copy(alpha = 0.08f); else -> Color.Transparent })
                            .hoverable(src2).clickable { sel = f.first; query = "" }.pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(folders.firstOrNull { it.first == sel }?.second ?: "", color = Col.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LiveChip(if (asList) "▦  Cajas" else "☰  Lista", false) { Store.liveView = if (asList) "grid" else "list" }
                    Spacer(Modifier.width(10.dp))
                    SearchBox(query, "Buscar canal", Modifier.width(240.dp)) { query = it }
                }
                if (countries.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)) {
                    item { LiveChip("Todos los países", country == null) { country = null } }
                    items(countries) { c -> LiveChip(LiveIndex.COUNTRY_NAMES[c] ?: c, country == c) { country = c } }
                }
                Text("Clic derecho en un canal: favoritos y cambiar de temática", color = Col.Sub, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 6.dp))
                val list = shown.map { it.item }
                val open: (Int) -> Unit = { i -> nav.push(Screen.Player(liveRequest(list[i], list))) }
                @Composable
                fun withMenu(ch: LiveIndex.Channel, content: @Composable () -> Unit) {
                    val fav = ch.alts.any { it.key in favKeys }
                    ContextMenuArea(items = {
                        listOf(ContextMenuItem(if (fav) "Quitar de Favoritos" else "Añadir a Favoritos") {
                            val f = favs.firstOrNull { x -> ch.alts.any { it.key == x.key } }
                            Store.toggleFavorite(f ?: ch.item)
                        }) + if (ch.key.startsWith("-|")) emptyList() else LiveIndex.THEMES.filter { it.id != ch.theme }.map { t ->
                            ContextMenuItem("Mover a ${t.label}") { Store.setLiveTheme(ch.key.substringBefore('#'), t.id) }
                        }
                    }) { content() }
                }
                when {
                    shown.isEmpty() -> Message(if (sel == FAV && query.isBlank()) "Aún no tienes canales favoritos. Clic derecho en un canal → Añadir a Favoritos." else "No hay canales")
                    asList -> LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(shown, key = { i, it -> "$i:${it.key}" }) { i, ch ->
                            withMenu(ch) { ChannelCard(ch, i + 1, ch.alts.any { it.key in favKeys }) { open(i) } }
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(170.dp),
                        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(shown, key = { i, it -> "$i:${it.key}" }) { i, ch ->
                            withMenu(ch) { ChannelBox(ch, i + 1, ch.alts.any { it.key in favKeys }) { open(i) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, color = if (on) Color.Black else Col.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 7.dp))
}

/** Canal en caja: logo grande y nombre debajo. */
@Composable
private fun ChannelBox(ch: LiveIndex.Channel, number: Int, fav: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Column(Modifier.hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }.pointerHoverIcon(PointerIcon.Hand)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color(0xFF3A3A40) else Col.Surface)
            .then(if (hovered) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)) {
            if (ch.main.image != null) AsyncImage(ch.main.image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
            else Text(ch.name, color = Col.Sub, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp), maxLines = 3)
            Text(number.toString(), color = Col.Sub, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            if (fav) Text("★", color = Color(0xFFF5C518), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            ch.country?.let { Text(it, color = Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) }
        }
        Text(ch.name, color = Col.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Canal en lista: número, logo, nombre, país. */
@Composable
private fun ChannelCard(ch: LiveIndex.Channel, number: Int, fav: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color.White else Col.Surface)
            .hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }.pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), color = if (hovered) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 13.sp, modifier = Modifier.width(40.dp))
        Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) {
            ch.main.image?.let { AsyncImage(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)) }
        }
        Text(ch.name, color = if (hovered) Color.Black else Col.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp).weight(1f))
        ch.country?.let { Text(it, color = if (hovered) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        if (fav) Text("★", color = if (hovered) Color.Black else Color(0xFFF5C518), fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}
