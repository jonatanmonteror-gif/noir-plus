package com.noirplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.desktop.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────────────────── Inicio de sesión ─────────────────────────

@Composable
fun SetupScreen() {
    var xtream by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(Store.name) }
    var server by remember { mutableStateOf(Store.server) }
    var user by remember { mutableStateOf(Store.user) }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf(Store.m3u) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        error = null
        busy = true
        scope.launch {
            try {
                if (name.isNotBlank()) Store.name = name
                val auto = if (!xtream) xtreamFromM3u(m3u) else null
                when {
                    xtream -> { val ok = xtreamLoginAuto(server, user.trim(), pass.trim()); Store.saveXtream(ok, user.trim(), pass.trim()) }
                    auto != null -> { val ok = xtreamLoginAuto(auto.first, auto.second, auto.third); Store.saveXtream(ok, auto.second, auto.third) }
                    else -> { M3uSource(m3u.trim()).all(Kind.LIVE); Store.saveM3u(m3u.trim()) }
                }
                Repo.rebuild()
            } catch (t: Throwable) {
                error = friendlyError(t)
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1C1C20), Col.Bg), radius = 1400f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(46)
            Text("Conecta tu lista para empezar", color = Col.Sub, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Col.Surface).padding(4.dp)) {
                listOf(true to "Xtream Codes", false to "Enlace M3U").forEach { (v, l) ->
                    Text(l, color = if (xtream == v) Color.Black else Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (xtream == v) Color.White else Color.Transparent)
                            .clickable { xtream = v }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 20.dp, vertical = 9.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Field("Tu nombre (para el saludo)", name) { name = it }
            if (xtream) {
                Field("Servidor (http://dirección:puerto)", server) { server = it }
                Field("Usuario", user) { user = it }
                Field("Contraseña", pass, password = true, onDone = { connect() }) { pass = it }
            } else {
                Field("Enlace de la lista M3U", m3u, onDone = { connect() }) { m3u = it }
            }
            error?.let { Text(it, color = Col.Error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(18.dp))
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
            else NButton("Entrar", primary = true, modifier = Modifier.fillMaxWidth()) { connect() }
            Text("Los datos se guardan solo en este PC.", color = Col.Sub.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
fun Field(label: String, value: String, password: Boolean = false, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Go else androidx.compose.ui.text.input.ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White, unfocusedBorderColor = Col.Line, focusedLabelColor = Color.White,
            unfocusedLabelColor = Col.Sub, cursorColor = Color.White, focusedTextColor = Col.Text, unfocusedTextColor = Col.Text,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    )
}

// ───────────────────────── Inicio ─────────────────────────

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 6..13 -> "Buenos días"
    in 14..20 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val changes = Store.changes.intValue
    val dv = Repo.dataVersion.intValue
    val history = remember(changes) { Store.history() }
    val movies by produceState(emptyList<Item>(), dv) {
        value = Repo.recent(Kind.MOVIE, 20)
        if (Startup.listsAt == 0L) Startup.listsAt = System.currentTimeMillis()
    }
    val series by produceState(emptyList<Item>(), dv) { value = Repo.recent(Kind.SERIES, 20) }
    var heroIndex by remember { mutableIntStateOf(0) }
    val featured = remember(movies, series) { (movies.take(5).zip(series.take(5)).flatMap { listOf(it.first, it.second) }).ifEmpty { movies + series } }
    LaunchedEffect(featured) { while (featured.isNotEmpty()) { delay(9000); heroIndex = (heroIndex + 1) % featured.size } }
    val hero = featured.getOrNull(heroIndex)
    val heroDetails by produceState<Details?>(null, hero?.key) {
        value = hero?.let { h -> runCatching { if (h.kind == Kind.MOVIE) Repo.source?.movieDetails(h) else Repo.source?.seriesDetails(h)?.details }.getOrNull() }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().height(460.dp)) {
                androidx.compose.animation.Crossfade(heroDetails?.backdrop ?: hero?.image, animationSpec = androidx.compose.animation.core.tween(900), label = "hero") { url ->
                    Backdrop(url, Modifier.fillMaxSize())
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 30.dp).widthIn(max = 620.dp)) {
                    Text(greeting() + (Store.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""), color = Col.Sub, fontSize = 16.sp,
                        fontFamily = Playfair)
                    if (hero != null) {
                        Text(hero.name, color = Col.Text, fontSize = 42.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 46.sp, modifier = Modifier.padding(top = 6.dp))
                        val meta = listOfNotNull(hero.year ?: heroDetails?.year, heroDetails?.genre, heroDetails?.duration).joinToString("  ·  ")
                        if (meta.isNotBlank()) Text(meta, color = Col.Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        (heroDetails?.plot ?: hero.plot)?.let {
                            Text(it, color = Col.Text.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NButton("Ver ficha", primary = true) { nav.push(Screen.Detail(hero)) }
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) item(key = "h") {
            SectionTitle("Continuar viendo")
            ScrollRow(history, { it.key }) { p ->
                WideCard(p.title, p.image, p.fraction) {
                    nav.push(Screen.Player(PlayRequest(url = p.url, title = p.title, key = p.key, image = p.image, isLive = false,
                        startMs = p.position, parent = p.parent)))
                }
            }
        }
        if (movies.isNotEmpty()) item(key = "m") {
            SectionTitle("Películas recién llegadas") { nav.tab(Screen.Movies) }
            ScrollRow(movies, { it.key }) { PosterCard(it) { nav.push(Screen.Detail(it)) } }
        }
        if (series.isNotEmpty()) item(key = "s") {
            SectionTitle("Series recién llegadas") { nav.tab(Screen.Series) }
            ScrollRow(series, { it.key }) { PosterCard(it) { nav.push(Screen.Detail(it)) } }
        }
        val favs = Store.favorites()
        if (favs.isNotEmpty()) item(key = "f") {
            SectionTitle("Mi lista") { nav.tab(Screen.MyList) }
            ScrollRow(favs, { it.key }) { PosterCard(it) { nav.push(Screen.Detail(it)) } }
        }
    }
}

// ───────────────────────── Películas / Series ─────────────────────────

@Composable
fun BrowseScreen(kind: Kind) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val dv = Repo.dataVersion.intValue
    var error by remember { mutableStateOf<String?>(null) }
    val cats by produceState<List<Category>?>(null, kind, dv) {
        value = try { src.categories(kind).newestFirst() } catch (t: Throwable) { error = friendlyError(t); emptyList() }
    }
    val nav = LocalNav.current
    var mode by remember(kind) { mutableStateOf(Store.browseMode(kind)) }
    val c = cats
    when {
        c == null -> Loading()
        c.isEmpty() -> Message(error?.let { "No se pudo cargar: $it" } ?: "No hay contenido")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)) {
            item(key = "title") {
                Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (kind == Kind.MOVIE) "Películas" else "Series", color = Col.Text, fontSize = 34.sp, fontFamily = InterDisplay,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    NButton(if (kind == Kind.MOVIE) "Todas las películas" else "Todas las series") {
                        nav.push(Screen.Category(kind, Category(ALL_ID, if (kind == Kind.MOVIE) "Todas las películas" else "Todas las series")))
                    }
                }
            }
            item(key = "modes") {
                Row(Modifier.padding(start = 40.dp, top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("plat" to "Plataformas", "genre" to "Géneros", "folders" to "Carpetas").forEach { (k, l) ->
                        Chip(l, mode == k) { mode = k; Store.setBrowseMode(kind, k) }
                    }
                }
            }
            when (mode) {
                "plat" -> {
                    items(Tmdb.platforms, key = { "p:$it" }) { g -> CategoryRow(kind, Category(Library.PLAT + g, g)) }
                    item(key = "noplat") { CategoryRow(kind, Category(Library.NO_PLAT, if (kind == Kind.MOVIE) "Cine y otras" else "Otras series")) }
                }
                "genre" -> items(Tmdb.genreNames(kind).entries.toList(), key = { "g:${it.key}" }) { g ->
                    CategoryRow(kind, Category(Library.GENRE + g.key, g.value))
                }
                else -> items(c, key = { it.id }) { cat -> CategoryRow(kind, cat) }
            }
        }
    }
}

const val ALL_ID = "__all"

@Composable
fun CategoryRow(kind: Kind, cat: Category) {
    val src = Repo.source ?: return
    val nav = LocalNav.current
    val dv = Repo.dataVersion.intValue
    val items by produceState<List<Item>?>(null, cat.id, dv) { value = runCatching { Library.items(kind, cat.id) }.getOrDefault(emptyList()) }
    val l = items
    if (l != null && l.isEmpty()) return
    val pretty = remember(cat.name) { if (Library.isVirtual(cat.id)) PrettyName(cat.name, null) else prettyCategory(cat.name, kind) }
    Column {
        SectionTitle(pretty.main + (pretty.year?.let { " ($it)" } ?: "")) { nav.push(Screen.Category(kind, cat)) }
        if (l == null) Box(Modifier.fillMaxWidth().height(290.dp))
        else ScrollRow(l.take(20), { it.key }) { PosterCard(it) { nav.push(Screen.Detail(it)) } }
    }
}

@Composable
fun CategoryScreen(kind: Kind, cat: Category) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val dv = Repo.dataVersion.intValue
    val items by produceState<List<Item>?>(null, cat.id, dv) {
        value = runCatching { Library.items(kind, if (cat.id == ALL_ID) Library.ALL else cat.id) }.getOrDefault(emptyList())
    }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(0) } // 0 recientes, 1 A-Z, 2 valoración, 3 original
    val shown = remember(items, query, sort) {
        val q = normText(query.trim())
        items?.filter { q.isEmpty() || normText(it.name).contains(q) }?.let { l ->
            when (sort) {
                0 -> if (kind == Kind.LIVE) l else Tmdb.byRelease(l)
                1 -> l.sortedBy { normText(it.name) }
                2 -> l.sortedByDescending { it.rating?.toFloatOrNull() ?: 0f }
                else -> l
            }
        }
    }
    val title = remember(cat.name) { if (cat.id == ALL_ID || Library.isVirtual(cat.id)) cat.name else prettyCategory(cat.name, kind).let { it.main + (it.year?.let { y -> " ($y)" } ?: "") } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Col.Text, fontSize = 30.sp, modifier = Modifier.clickable { nav.pop() }.pointerHoverIcon(PointerIcon.Hand).padding(end = 14.dp))
            Text(title, color = Col.Text, fontSize = 28.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            SearchBox(query, "Buscar aquí", Modifier.width(280.dp)) { query = it }
            Spacer(Modifier.width(12.dp))
            listOf("Recientes", "A–Z", "Valoradas", "Original").forEachIndexed { i, l ->
                Text(l, color = if (sort == i) Color.Black else Col.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(if (sort == i) Color.White else Color.Transparent)
                        .clickable { sort = i }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        val l = shown
        when {
            l == null -> Loading()
            l.isEmpty() -> Message(if (query.isBlank()) "No hay contenido" else "Sin resultados para «${query.trim()}»")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(l, key = { i, it -> "$i:${it.key}" }) { _, it ->
                    PosterCard(it, width = 170.dp) { if (it.kind == Kind.LIVE) nav.push(Screen.Player(liveRequest(it, l))) else nav.push(Screen.Detail(it)) }
                }
            }
        }
    }
}

@Composable
fun SearchBox(value: String, hint: String, modifier: Modifier = Modifier, autoFocus: Boolean = false, onChange: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Row(modifier.height(40.dp).clip(RoundedCornerShape(50)).background(Col.Surface2).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, null, tint = Col.Sub, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Col.Sub, fontSize = 14.sp)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Col.Text, fontSize = 14.sp, fontFamily = Inter),
                cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth().focusRequester(fr))
        }
    }
}

fun liveRequest(ch: Item, list: List<Item>): PlayRequest {
    val src = Repo.source
    return PlayRequest(url = src?.streamUrl(ch) ?: "", title = ch.name, key = ch.key, image = ch.image, isLive = true,
        channels = list, channelIndex = list.indexOfFirst { it.key == ch.key }.coerceAtLeast(0))
}

// ───────────────────────── Mi lista ─────────────────────────

@Composable
fun MyListScreen() {
    val nav = LocalNav.current
    val changes = Store.changes.intValue
    val favs = remember(changes) { Store.favorites() }
    Column(Modifier.fillMaxSize()) {
        Text("Mi lista", color = Col.Text, fontSize = 34.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 40.dp, top = 24.dp, bottom = 12.dp))
        if (favs.isEmpty()) Message("Aún no has añadido nada. Usa «+ Mi lista» en cualquier ficha.")
        else LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(favs, key = { _, it -> it.key }) { _, it ->
                PosterCard(it, width = 170.dp) { if (it.kind == Kind.LIVE) nav.push(Screen.Player(liveRequest(it, favs))) else nav.push(Screen.Detail(it)) }
            }
        }
    }
}

// ───────────────────────── Buscar ─────────────────────────

@Composable
fun SearchScreen() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val dv = Repo.dataVersion.intValue
    var query by remember { mutableStateOf("") }
    val index by produceState<List<Item>?>(null, dv) {
        value = runCatching { src.all(Kind.MOVIE) + src.all(Kind.SERIES) + src.all(Kind.LIVE) }.getOrDefault(emptyList())
    }
    val results = remember(index, query) {
        val q = normText(query.trim())
        if (q.length < 2) emptyList() else index.orEmpty().filter { normText(it.name).contains(q) }.distinctBy { it.key }.take(300)
    }
    Column(Modifier.fillMaxSize()) {
        Text("Buscar", color = Col.Text, fontSize = 34.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 40.dp, top = 24.dp, bottom = 12.dp))
        SearchBox(query, "Películas, series o canales", Modifier.padding(horizontal = 40.dp).width(520.dp), autoFocus = true) { query = it }
        when {
            index == null -> Loading()
            query.trim().length < 2 -> Message("Escribe al menos 2 letras")
            results.isEmpty() -> Message("Sin resultados para «${query.trim()}»")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 20.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(results, key = { _, it -> it.key }) { _, it ->
                    PosterCard(it, width = 170.dp) {
                        if (it.kind == Kind.LIVE) nav.push(Screen.Player(liveRequest(it, results.filter { r -> r.kind == Kind.LIVE })))
                        else nav.push(Screen.Detail(it))
                    }
                }
            }
        }
    }
}
