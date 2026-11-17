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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Library
import com.noirplus.tv.data.Platforms
import com.noirplus.tv.data.Tmdb
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay

enum class Tab(val label: String) {
    HOME("Inicio"), LIVE("Directo"), MOVIES("Películas"), SERIES("Series"), MYLIST("Mi lista"), RATINGS("Valoraciones"),
    SEARCH("Buscar"), SETTINGS("Ajustes")
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
        if (d.tv) delay(180)
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
            Column(Modifier.weight(1f)) {
                Logo(22)
                Greeting(Modifier.padding(top = 2.dp))
            }
            Row(
                Modifier.background(Col.Surface.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(4.dp).focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Tab.entries.filter { it != Tab.RATINGS || Repo.prefs.ratingsEnabled }.forEach { t ->
                    TabItem(t.label, selected = t == tab, requester = requesters.getValue(t), onSelect = { tab = t })
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Crossfade(targetState = shown, animationSpec = tween(if (d.tv) 0 else 220), modifier = Modifier.weight(1f).fillMaxWidth(), label = "tabs") { t ->
            when (t) {
                Tab.HOME -> HomeContent()
                Tab.LIVE -> LiveContent()
                Tab.MOVIES -> BrowseContent(Kind.MOVIE)
                Tab.SERIES -> BrowseContent(Kind.SERIES)
                Tab.MYLIST -> MyListContent()
                Tab.RATINGS -> RatingsContent()
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
    RATINGS("Valoraciones", ReviewIcon),
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
                    "RATINGS" -> RatingsContent()
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
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Col.Surface.copy(alpha = 0.94f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Section.entries.filter { it != Section.RATINGS || Repo.prefs.ratingsEnabled }.forEach { s ->
            val sel = s == current
            val dot by animateDpAsState(if (sel) 4.dp else 0.dp, tween(200), label = "dot")
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(s) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val pop by androidx.compose.animation.core.animateFloatAsState(
                    if (sel) 1.12f else 1f,
                    androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f), label = "pop",
                )
                Icon(s.icon, s.label, tint = if (sel) Color.White else Col.Sub,
                    modifier = Modifier.size(25.dp).graphicsLayer { scaleX = pop; scaleY = pop })
                Box(Modifier.padding(top = 4.dp).size(dot).clip(RoundedCornerShape(50)).background(Color.White))
            }
        }
    }
}

/** Mi lista: acceso directo a tus películas, series y canales favoritos, agrupados. */
@Composable
fun MyListContent() {
    val d = LocalDims.current
    val prefs = Repo.prefs
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val changes = prefs.changes.intValue
    val favs = remember(changes) { prefs.favorites() }
    if (favs.isEmpty()) return Message("Aún no has añadido nada.\nPulsa «Mi lista» en una película o serie, o mantén pulsado un canal.")
    val groups = listOf(
        "Películas" to favs.filter { it.kind == Kind.MOVIE },
        "Series" to favs.filter { it.kind == Kind.SERIES },
        "Canales" to favs.filter { it.kind == Kind.LIVE },
    ).filter { it.second.isNotEmpty() }
    TvRowScroll {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = d.bottomPad)) {
            groups.forEach { (title, list) ->
                item(key = "g-$title") {
                    Column {
                        SectionTitle(title, year = list.size.toString())
                        PosterRow("fav-$title", list, {}) { item ->
                            if (item.kind == Kind.LIVE) {
                                nav.push(
                                    Screen.Player(
                                        PlayRequest(url = src.streamUrl(item), title = item.name, key = item.key, image = item.image, isLive = true,
                                            channels = list, channelIndex = list.indexOf(item).coerceAtLeast(0))
                                    )
                                )
                            } else {
                                nav.push(Screen.Detail(item))
                            }
                        }
                    }
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
    val movieCats by produceState(emptyList<Category>(), changes, Repo.dataVersion.intValue) {
        value = runCatching { src.categories(Kind.MOVIE).filter { prefs.visible(Kind.MOVIE, it.id) }.newestFirst().take(4) }.getOrDefault(emptyList())
    }
    val seriesCats by produceState(emptyList<Category>(), changes, Repo.dataVersion.intValue) {
        value = runCatching { src.categories(Kind.SERIES).filter { prefs.visible(Kind.SERIES, it.id) }.newestFirst().take(4) }.getOrDefault(emptyList())
    }
    val featured by produceState(emptyList<Item>(), changes, Repo.dataVersion.intValue) {
        val m = recentOf(Kind.MOVIE, 6)
        val sr = recentOf(Kind.SERIES, 4)
        value = (m.zip(sr).flatMap { listOf(it.first, it.second) } + m.drop(sr.size) + sr.drop(m.size)).take(10)
    }

    HeroList({
        if (d.compact) FeaturedPager(featured)
        else HeroArea(hero ?: cycling(featured), "Bienvenido", "Películas, series y canales en directo de tu lista")
    }) {
        if (history.isNotEmpty()) {
            item(key = "history") { ContinueRow(history, "home-h") { hero = it } }
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
            CategoryRow(Kind.MOVIE, c, tag = "Película") { hero = it }
        }
        itemsIndexed(seriesCats, key = { i, c -> "s$i:${c.id}" }) { _, c ->
            CategoryRow(Kind.SERIES, c, tag = "Serie") { hero = it }
        }
    }
}

/** Lo último añadido de un tipo (para "Recién llegados"). */
suspend fun recentOf(kind: Kind, n: Int): List<Item> {
    val src = Repo.source ?: return emptyList()
    val prefs = Repo.prefs
    return runCatching {
        src.all(kind).filter { prefs.visible(kind, it.categoryId) }.sortedByDescending { it.added }.take(n)
    }.getOrElse {
        runCatching {
            val c = src.categories(kind).firstOrNull { prefs.visible(kind, it.id) } ?: return emptyList()
            src.items(kind, c.id).take(n)
        }.getOrDefault(emptyList())
    }
}

/** TV: mientras no se elige nada, la cabecera va pasando sola por los destacados. */
@Composable
private fun cycling(items: List<Item>): Item? {
    var i by remember(items) { mutableStateOf(0) }
    LaunchedEffect(items) {
        if (items.size < 2) return@LaunchedEffect
        while (true) {
            delay(7000)
            i = (i + 1) % items.size
        }
    }
    return items.getOrNull(i)
}

@Composable
fun BrowseContent(kind: Kind) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val d = LocalDims.current
    val prefs = Repo.prefs
    val changes = prefs.changes.intValue
    var hero by remember(kind) { mutableStateOf<Item?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val cats by produceState<List<Category>?>(null, kind, changes, Repo.dataVersion.intValue) {
        value = try {
            src.categories(kind).filter { prefs.visible(kind, it.id) }.newestFirst()
        } catch (t: Throwable) {
            error = t.message
            emptyList()
        }
    }
    val featured by produceState(emptyList<Item>(), kind, changes, Repo.dataVersion.intValue) { value = recentOf(kind, 10) }
    val title = if (kind == Kind.MOVIE) "Películas" else "Series"
    // Móvil: siempre por plataforma o por género (por defecto, plataforma). Tele: también carpetas (por defecto).
    var mode by remember(kind) {
        mutableStateOf(when {
            !Tmdb.configured() -> "folders"
            d.compact -> prefs.browseMode(kind).takeIf { it != "folders" } ?: "plat"
            // Tele: solo Plataformas o Géneros (Recién subido está en Ajustes; Carpetas, oculto)
            else -> prefs.browseMode(kind).takeIf { it == "plat" || it == "genre" } ?: "plat"
        })
    }
    val history = remember(changes, kind) {
        prefs.history().filter { if (kind == Kind.MOVIE) it.key.startsWith("MOVIE:") else it.key.startsWith("EP:") }
    }
    LaunchedEffect(mode, kind) { if (mode == "genre") Tmdb.indexGenres(kind) }

    val c = cats
    when {
        c == null -> Loading()
        c.isEmpty() -> Message(error?.let { "No se pudo cargar: $it" } ?: "No hay contenido en esta sección")
        else -> HeroList({
            if (d.compact) FeaturedPager(featured)
            else HeroArea(hero ?: cycling(featured), title, "Explora las categorías de tu lista")
        }) {
            item(key = "modes") { BrowseModes(kind, mode) { mode = it; prefs.setBrowseMode(kind, it) } }
            if (d.compact) item(key = "all") { AllEntryRow(kind) }
            if (history.isNotEmpty()) item(key = "history") { ContinueRow(history, "${kind.name}-h") { hero = it } }
            when (mode) {
                "plat" -> {
                    itemsIndexed(Platforms.main, key = { _, g -> "p:$g" }) { _, g ->
                        CategoryRow(kind, Category(Library.PLAT + g, g)) { hero = it }
                    }
                    item(key = "noplat") {
                        CategoryRow(kind, Category(Library.NO_PLAT, if (kind == Kind.MOVIE) "Cine y otras" else "Otras series")) { hero = it }
                    }
                }
                "new" -> {
                    // Lo último que ha subido tu proveedor, por fecha de subida (aunque sean títulos antiguos)
                    item(key = "n1") { CategoryRow(kind, Category(Library.NEW + "0-1", "Últimas 24 horas")) { hero = it } }
                    item(key = "n7") { CategoryRow(kind, Category(Library.NEW + "1-7", "Esta semana")) { hero = it } }
                    item(key = "n30") { CategoryRow(kind, Category(Library.NEW + "7-30", "Este mes")) { hero = it } }
                    item(key = "n90") { CategoryRow(kind, Category(Library.NEW + "30-90", "Últimos 3 meses")) { hero = it } }
                }
                "genre" -> {
                    item(key = "gprog") {
                        val p = Tmdb.genreProgress.value
                        if (p != null) Text(
                            "Clasificando por géneros… ${p.first}/${p.second}. Las filas se completan solas.",
                            color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = d.edge, vertical = 4.dp),
                        )
                    }
                    val genres = Tmdb.genreNames(kind).entries.toList()
                    itemsIndexed(genres, key = { _, g -> "g:${g.key}" }) { _, g ->
                        CategoryRow(kind, Category(Library.GENRE + g.key, g.value)) { hero = it }
                    }
                }
                else -> itemsIndexed(c, key = { i, cat -> "$i:${cat.id}" }) { _, cat ->
                    CategoryRow(kind, cat) { hero = it }
                }
            }
        }
    }
}

/** Fila "Continuar viendo" (Inicio, Películas y Series). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContinueRow(history: List<com.noirplus.tv.data.Progress>, memPrefix: String, onHero: (Item) -> Unit) {
    val d = LocalDims.current
    val nav = LocalNav.current
    Column {
        SectionTitle("Continuar viendo")
        TvHorizontalScroll {
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
                    memKey = "$memPrefix/${p.key}",
                    onFocus = { onHero(Item(id = p.key, name = p.title, image = p.image, kind = Kind.LIVE)) },
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

/** Selector de vista de Películas/Series: por plataforma, por género o por carpetas del proveedor. */
@Composable
private fun BrowseModes(kind: Kind, mode: String, onChange: (String) -> Unit) {
    val d = LocalDims.current
    val nav = LocalNav.current
    val opts = if (d.compact) listOf("plat" to "Plataformas", "genre" to "Géneros", "new" to "Recién subido")
    else listOf("plat" to "Plataformas", "genre" to "Géneros")
    Row(
        Modifier.fillMaxWidth().padding(start = d.edge, end = d.edge, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        opts.forEach { (k, label) ->
            val sel = k == mode
            Focusable(Modifier.height(34.dp), key = "mode-$k", shape = RoundedCornerShape(50), focusScale = 1.06f,
                onClick = { onChange(k) }) { focused ->
                Box(
                    Modifier.fillMaxHeight()
                        .background(if (sel || focused) Color.White else Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel || focused) Color.Black else Col.Text, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
        if (!d.compact) {
            // A la derecha y discreto: ver la lista completa (con buscador y orden)
            Spacer(Modifier.weight(1f))
            val label = if (kind == Kind.MOVIE) "Todas las películas" else "Todas las series"
            Focusable(Modifier.height(34.dp), key = "all-${kind.name}", shape = RoundedCornerShape(50), focusScale = 1.06f,
                onClick = { nav.push(Screen.Category(kind, Category(ALL_ID, label), search = false)) }) { focused ->
                Box(
                    Modifier.fillMaxHeight().background(if (focused) Color.White else Color.Transparent).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ver " + label.replaceFirstChar { it.lowercase() } + "  ›", color = if (focused) Color.Black else Col.Sub,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** "Buenos días, Jonatan" según la hora. */
@Composable
fun Greeting(modifier: Modifier = Modifier) {
    val name = Repo.prefs.name.takeIf { it.isNotBlank() } ?: return
    val h = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val hello = when (h) {
        in 6..13 -> "Buenos días"
        in 14..20 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    Text("$hello, $name", modifier, color = Col.Sub, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
