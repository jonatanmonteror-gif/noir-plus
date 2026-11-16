package com.noirplus.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var shown by rememberSaveable { mutableStateOf(Tab.HOME) }
    val requesters = remember { Tab.entries.associateWith { FocusRequester() } }
    val mem = LocalFocusMemory.current
    var hasFocus by remember { mutableStateOf(false) }

    // Al moverse por las pestañas no cargamos cada sección: esperamos a que se quede quieto.
    LaunchedEffect(tab) {
        delay(250)
        shown = tab
    }
    LaunchedEffect(Unit) {
        delay(if (mem.restore) 700 else 120)
        if (!hasFocus) runCatching { requesters.getValue(tab).requestFocus() }
    }

    Column(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 4.dp)
                .focusRestorer(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo()
            Spacer(Modifier.width(32.dp))
            Tab.entries.forEach { t ->
                TabItem(t.label, selected = t == tab, requester = requesters.getValue(t), onFocus = { tab = t })
                Spacer(Modifier.width(6.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (shown) {
                Tab.HOME -> HomeContent()
                Tab.LIVE -> LiveContent()
                Tab.MOVIES -> key("movies") { BrowseContent(Kind.MOVIE) }
                Tab.SERIES -> key("series") { BrowseContent(Kind.SERIES) }
                Tab.SEARCH -> SearchContent()
                Tab.SETTINGS -> SettingsContent()
            }
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, requester: FocusRequester, onFocus: () -> Unit) {
    Focusable(
        key = "tab:$label",
        focusRequester = requester,
        shape = RoundedCornerShape(50),
        focusScale = 1f,
        border = false,
        onFocus = onFocus,
        onClick = {},
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                Modifier
                    .background(if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                color = when {
                    focused -> Color.Black
                    selected -> Col.Text
                    else -> Col.Sub
                },
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp,
            )
            Box(
                Modifier.width(18.dp).height(3.dp)
                    .background(if (selected && !focused) Col.Accent else Color.Transparent, RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeContent() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
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

    Column(Modifier.fillMaxSize()) {
        HeroArea(hero, "Bienvenido", "Películas, series y canales en directo de tu lista")
        LazyColumn(Modifier.weight(1f), state = rememberLazyListState(), contentPadding = PaddingValues(bottom = 48.dp)) {
            if (history.isNotEmpty()) {
                item(key = "history") {
                    Column {
                        SectionTitle("Continuar viendo")
                        LazyRow(
                            Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
            itemsIndexed(movieCats, key = { i, c -> "m$i:${c.id}" }) { _, c ->
                CategoryRow(Kind.MOVIE, c, "Películas · ") { hero = it }
            }
            itemsIndexed(seriesCats, key = { i, c -> "s$i:${c.id}" }) { _, c ->
                CategoryRow(Kind.SERIES, c, "Series · ") { hero = it }
            }
        }
    }
}

@Composable
fun BrowseContent(kind: Kind) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    var hero by remember { mutableStateOf<Item?>(null) }
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

    Column(Modifier.fillMaxSize()) {
        HeroArea(hero, title, "Explora las categorías de tu lista")
        val c = cats
        when {
            c == null -> Loading(Modifier.weight(1f))
            c.isEmpty() -> Message(error?.let { "No se pudo cargar: $it" } ?: "No hay contenido en esta sección", Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), state = rememberLazyListState(), contentPadding = PaddingValues(bottom = 48.dp)) {
                itemsIndexed(c, key = { i, cat -> "$i:${cat.id}" }) { _, cat ->
                    CategoryRow(kind, cat) { hero = it }
                }
            }
        }
    }
}
