package com.noirplus.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay

enum class Tab(val label: String) {
    HOME("Inicio"), LIVE("Directo"), MOVIES("Películas"), SERIES("Series"), SEARCH("Buscar"), SETTINGS("Ajustes")
}

@Composable
fun MainScreen() {
    if (LocalDims.current.compact) PhoneMain() else BigScreenMain()
}

// ───────────────────────────── TV / tablet ─────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BigScreenMain() {
    val d = LocalDims.current
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var shown by rememberSaveable { mutableStateOf(Tab.HOME) }
    val requesters = remember { Tab.entries.associateWith { FocusRequester() } }
    val mem = LocalFocusMemory.current
    var hasFocus by remember { mutableStateOf(false) }

    // En TV, al pasar por las pestañas esperamos a que el usuario se quede quieto antes de cargar.
    LaunchedEffect(tab) {
        if (d.tv) delay(250)
        shown = tab
    }
    LaunchedEffect(Unit) {
        if (!d.tv) return@LaunchedEffect
        delay(if (mem.restore) 700 else 120)
        if (!hasFocus) runCatching { requesters.getValue(tab).requestFocus() }
    }

    Column(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        Row(
            Modifier.fillMaxWidth().padding(start = d.edge, end = d.edge, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { Logo(22) }
            Row(
                Modifier.background(Col.Surface.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(4.dp).focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Tab.entries.forEach { t ->
                    TabItem(t.label, selected = t == tab, requester = requesters.getValue(t), onSelect = { tab = t })
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Crossfade(targetState = shown, animationSpec = tween(220), modifier = Modifier.weight(1f).fillMaxWidth(), label = "tabs") { t ->
            when (t) {
                Tab.HOME -> HomeContent()
                Tab.LIVE -> LiveContent()
                Tab.MOVIES -> BrowseContent(Kind.MOVIE)
                Tab.SERIES -> BrowseContent(Kind.SERIES)
                Tab.SEARCH -> SearchContent()
                Tab.SETTINGS -> SettingsContent()
            }
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, requester: FocusRequester, onSelect: () -> Unit) {
    Focusable(
        key = "tab:$label",
        focusRequester = requester,
        shape = RoundedCornerShape(50),
        focusScale = 1f,
        onFocus = onSelect,
        onClick = onSelect,
    ) { focused ->
        Text(
            label,
            Modifier
                .background(
                    when {
                        focused -> Color.White
                        selected -> Col.Surface2
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 18.dp, vertical = 7.dp),
            color = when {
                focused -> Color.Black
                selected -> Col.Text
                else -> Col.Sub
            },
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}

// ───────────────────────────── Móvil ─────────────────────────────

private enum class Section(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Home),
    SEARCH("Buscar", Icons.Filled.Search),
    MYLIST("Mi lista", Icons.Filled.Favorite),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

private enum class Filter(val label: String) { ALL("Todo"), SERIES("Series"), MOVIES("Películas"), LIVE("Directo") }

@Composable
private fun PhoneMain() {
    val d = LocalDims.current
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (section == Section.HOME) {
                Row(Modifier.fillMaxWidth().padding(start = d.edge, end = d.edge, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(24)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = d.edge, end = d.edge, top = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Filter.entries.forEach { f -> FilterTab(f.label, selected = f == filter) { filter = f } }
                }
            } else {
                Text(
                    section.label,
                    fontFamily = InterDisplay, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Col.Text, letterSpacing = (-0.8).sp,
                    modifier = Modifier.padding(start = d.edge, end = d.edge, top = 18.dp, bottom = 6.dp),
                )
            }
            Crossfade(
                targetState = if (section == Section.HOME) "h-" + filter.name else section.name,
                animationSpec = tween(220),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "phone",
            ) { k ->
                when (k) {
                    "h-ALL" -> HomeContent()
                    "h-SERIES" -> BrowseContent(Kind.SERIES)
                    "h-MOVIES" -> BrowseContent(Kind.MOVIE)
                    "h-LIVE" -> LiveContent()
                    "SEARCH" -> SearchContent()
                    "MYLIST" -> MyListContent()
                    else -> SettingsContent()
                }
            }
        }
        BottomBar(section, Modifier.align(Alignment.BottomCenter)) { section = it }
    }
}

@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val w by animateDpAsState(if (selected) 20.dp else 0.dp, tween(220), label = "underline")
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (selected) Col.Text else Col.Sub, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(w).height(3.dp).clip(RoundedCornerShape(50)).background(Col.Text))
    }
}

@Composable
private fun BottomBar(current: Section, modifier: Modifier, onSelect: (Section) -> Unit) {
    Row(
        modifier
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(50))
            .background(Col.Surface.copy(alpha = 0.94f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Section.entries.forEach { s ->
            val sel = s == current
            Column(
                Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(s) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(s.icon, s.label, tint = if (sel) Color.White else Col.Sub, modifier = Modifier.size(24.dp))
                Text(s.label, color = if (sel) Color.White else Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Favoritos (películas, series y canales). */
@Composable
private fun MyListContent() {
    val d = LocalDims.current
    val prefs = Repo.prefs
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val changes = prefs.changes.intValue
    val favs = remember(changes) { prefs.favorites() }
    if (favs.isEmpty()) return Message("Aún no has añadido nada.\nPulsa «Mi lista» en una película o serie, o mantén pulsado un canal.")
    val lives = favs.filter { it.kind == Kind.LIVE }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(d.poster),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = d.edge, end = d.edge, top = 8.dp, bottom = d.bottomPad),
        horizontalArrangement = Arrangement.spacedBy(d.gap),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(favs, key = { i, it2 -> "$i:${it2.key}" }) { _, item ->
            PosterCard(item, memKey = "fav/${item.key}", fill = true, onFocus = {}) {
                if (item.kind == Kind.LIVE) {
                    nav.push(
                        Screen.Player(
                            PlayRequest(url = src.streamUrl(item), title = item.name, key = item.key, image = item.image, isLive = true,
                                channels = lives, channelIndex = lives.indexOf(item).coerceAtLeast(0))
                        )
                    )
                } else {
                    nav.push(Screen.Detail(item))
                }
            }
        }
    }
}

// ───────────────────────────── Contenido común ─────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeContent() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val d = LocalDims.current
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val changes = prefs.changes.intValue
    val history = remember(changes) { prefs.history() }
    val favs = remember(changes) { prefs.favorites().filter { it.kind != Kind.LIVE } }
    var hero by remember { mutableStateOf<Item?>(null) }
    val movieCats by produceState(emptyList<Category>()) {
        value = runCatching { src.categories(Kind.MOVIE).take(4) }.getOrDefault(emptyList())
    }
    val seriesCats by produceState(emptyList<Category>()) {
        value = runCatching { src.categories(Kind.SERIES).take(4) }.getOrDefault(emptyList())
    }
    val featured by produceState(emptyList<Item>(), movieCats) {
        val c = movieCats.firstOrNull() ?: return@produceState
        value = runCatching { src.items(Kind.MOVIE, c.id).take(8) }.getOrDefault(emptyList())
    }
    val setFirst: (Item) -> Unit = { if (hero == null) hero = it }

    HeroList({
        if (d.compact) FeaturedPager(featured)
        else HeroArea(hero, "Bienvenido", "Películas, series y canales en directo de tu lista")
    }) {
        if (history.isNotEmpty()) {
            item(key = "history") {
                Column {
                    SectionTitle("Continuar viendo")
                    LazyRow(
                        Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = d.edge, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(d.gap),
                    ) {
                        items(history, key = { it.key }) { p ->
                            WideCard(
                                title = p.title,
                                image = p.image,
                                progress = p.fraction,
                                memKey = "home-h/${p.key}",
                                onFocus = { hero = Item(id = p.key, name = p.title, image = p.image, kind = Kind.LIVE) },
                                onClick = {
                                    nav.push(
                                        Screen.Player(
                                            PlayRequest(url = p.url, title = p.title, key = p.key, image = p.image,
                                                isLive = false, startMs = p.position, parent = p.parent)
                                        )
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (favs.isNotEmpty()) {
            item(key = "favs") {
                Column {
                    SectionTitle("Mi lista")
                    PosterRow("home-f", favs, { hero = it }) { nav.push(Screen.Detail(it)) }
                }
            }
        }
        itemsIndexed(movieCats, key = { i, c -> "m$i:${c.id}" }) { i, c ->
            CategoryRow(Kind.MOVIE, c, "Películas · ", onLoaded = if (i == 0) setFirst else null) { hero = it }
        }
        itemsIndexed(seriesCats, key = { i, c -> "s$i:${c.id}" }) { _, c ->
            CategoryRow(Kind.SERIES, c, "Series · ") { hero = it }
        }
    }
}

@Composable
fun BrowseContent(kind: Kind) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val d = LocalDims.current
    var hero by remember(kind) { mutableStateOf<Item?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val cats by produceState<List<Category>?>(null, kind) {
        value = try {
            src.categories(kind)
        } catch (t: Throwable) {
            error = t.message
            emptyList()
        }
    }
    val featured by produceState(emptyList<Item>(), cats) {
        val c = cats?.firstOrNull() ?: return@produceState
        value = runCatching { src.items(kind, c.id).take(8) }.getOrDefault(emptyList())
    }
    val title = if (kind == Kind.MOVIE) "Películas" else "Series"
    val setFirst: (Item) -> Unit = { if (hero == null) hero = it }

    val c = cats
    when {
        c == null -> Loading()
        c.isEmpty() -> Message(error?.let { "No se pudo cargar: $it" } ?: "No hay contenido en esta sección")
        else -> HeroList({
            if (d.compact) FeaturedPager(featured)
            else HeroArea(hero, title, "Explora las categorías de tu lista")
        }) {
            itemsIndexed(c, key = { i, cat -> "$i:${cat.id}" }) { i, cat ->
                CategoryRow(kind, cat, onLoaded = if (i == 0) setFirst else null) { hero = it }
            }
        }
    }
}

/** TV/tablet: cabecera fija y filas debajo. Móvil: la cabecera se desplaza con la lista. */
@Composable
private fun HeroList(hero: @Composable () -> Unit, content: LazyListScope.() -> Unit) {
    val d = LocalDims.current
    val state = rememberLazyListState()
    if (d.compact) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = d.bottomPad)) {
            item(key = "hero") { hero() }
            content()
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            hero()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TvRowScroll {
                    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = d.bottomPad + 120.dp)) { content() }
                }
            }
        }
    }
}
