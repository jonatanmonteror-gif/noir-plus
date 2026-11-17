package com.noirplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

/** Directo: carpetas a la izquierda y canales a la derecha. */
@Composable
fun LiveScreen() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val dv = Repo.dataVersion.intValue
    var error by remember { mutableStateOf<String?>(null) }
    val cats by produceState<List<Category>?>(null, dv) {
        value = try { src.categories(Kind.LIVE) } catch (t: Throwable) { error = friendlyError(t); emptyList() }
    }
    var sel by remember { mutableStateOf<Category?>(null) }
    LaunchedEffect(cats) { if (sel == null) sel = cats?.firstOrNull() }
    var query by remember { mutableStateOf("") }
    val channels by produceState<List<Item>?>(null, sel?.id, dv) {
        value = null
        value = sel?.let { c -> runCatching { src.items(Kind.LIVE, c.id) }.getOrDefault(emptyList()) } ?: emptyList()
    }
    val shown = remember(channels, query) {
        val q = normText(query.trim()); channels?.filter { q.isEmpty() || normText(it.name).contains(q) }
    }
    val c = cats
    when {
        c == null -> Loading()
        c.isEmpty() -> Message(error?.let { "No se pudo cargar: $it" } ?: "No hay canales")
        else -> Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(290.dp).fillMaxHeight().background(Color(0xFF0E0E10)), contentPadding = PaddingValues(12.dp)) {
                item {
                    Text("Directo", color = Col.Text, fontSize = 28.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 14.dp))
                }
                itemsIndexed(c, key = { _, it -> it.id }) { _, cat ->
                    val s = cat.id == sel?.id
                    val src2 = remember { MutableInteractionSource() }
                    val h by src2.collectIsHoveredAsState()
                    Text(prettyCategory(cat.name, Kind.LIVE).main, color = if (s) Color.Black else Col.Text, fontSize = 14.sp,
                        fontWeight = if (s) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp))
                            .background(when { s -> Color.White; h -> Color.White.copy(alpha = 0.08f); else -> Color.Transparent })
                            .hoverable(src2).clickable { sel = cat; query = "" }.pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sel?.let { prettyCategory(it.name, Kind.LIVE).main } ?: "", color = Col.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SearchBox(query, "Buscar canal", Modifier.width(260.dp)) { query = it }
                }
                val l = shown
                when {
                    l == null -> Loading()
                    l.isEmpty() -> Message("No hay canales")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(210.dp),
                        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(l, key = { i, it -> "$i:${it.key}" }) { _, ch ->
                            ChannelCard(ch) { nav.push(Screen.Player(liveRequest(ch, l))) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(ch: Item, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color.White else Col.Surface)
            .hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }.pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) {
            ch.image?.let { AsyncImage(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)) }
        }
        Text(ch.name, color = if (hovered) Color.Black else Col.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}
