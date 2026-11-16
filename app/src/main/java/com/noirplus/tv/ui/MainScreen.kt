package com.noirplus.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainScreen() {
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

    val tabs: @Composable () -> Unit = {
        Row(
            Modifier.background(Col.Surface.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Tab.entries.forEach { t ->
                TabItem(t.label, selected = t == tab, requester = requesters.getValue(t), onSelect = { tab = t })
            }
        }
    }

    Column(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        if (d.compact) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                Box(Modifier.padding(horizontal = d.edge)) { Logo(22) }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(start = d.edge, end = d.edge, top = 10.dp)
                ) { tabs() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = d.edge, end = d.edge, top = 16.dp, bottom = 4.dp).focusRestorer(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { Logo(22) }
                tabs()
                Spacer(Modifier.weight(1f))
            }
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
    val setFirst: (Item) -> Unit = { if (hero == null) hero = it }

    HeroList({ HeroArea(hero, "Bienvenido", "Películas, series y canales en directo de tu lista") }) {
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
    val title = if (kind == Kind.MOVIE) "Películas" else "Series"
    val setFirst: (Item) -> Unit = { if (hero == null) hero = it }

    val c = cats
    when {
        c == null -> Loading()
        c.isEmpty() -> Message(error?.let { "No se pudo cargar: $it" } ?: "No hay contenido en esta sección")
        else -> HeroList({ HeroArea(hero, title, "Explora las categorías de tu lista") }) {
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
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "hero") { hero() }
            content()
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            hero()
            LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(bottom = 48.dp)) { content() }
        }
    }
}
