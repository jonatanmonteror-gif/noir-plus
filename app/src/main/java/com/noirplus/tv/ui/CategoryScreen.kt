package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.Platforms
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.Tmdb
import kotlinx.coroutines.delay

/** Carpeta completa: cuadrícula con buscador dentro de la carpeta y orden. */
@Composable
fun CategoryScreen(kind: Kind, cat: Category, startSearch: Boolean = false) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val d = LocalDims.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isAll = cat.id == ALL_ID
    val prefs = Repo.prefs
    val virtual = com.noirplus.tv.data.Library.isVirtual(cat.id)
    var cols by remember { androidx.compose.runtime.mutableIntStateOf(prefs.gridCols) }
    val genreV = if (cat.id.startsWith(com.noirplus.tv.data.Library.GENRE)) Tmdb.genreVersion.intValue else 0
    val items by produceState<List<Item>?>(null, kind, cat.id, Repo.dataVersion.intValue, genreV) {
        value = runCatching { com.noirplus.tv.data.Library.items(kind, cat.id) }.getOrDefault(emptyList())
    }
    val pretty = remember(cat.name) { if (virtual) PrettyName(cat.name, null) else prettyCategory(cat.name, kind) }
    var searching by rememberSaveable { mutableStateOf(startSearch) }
    var query by rememberSaveable { mutableStateOf("") }
    val sort = prefs.defaultSort // el orden se elige en Ajustes
    // Filtro por plataforma (TMDB): se descarga el catálogo de la plataforma una vez y se cruza con la lista.
    // No aparece dentro de carpetas que ya son de una plataforma ("Series Netflix", "HBO"…).
    val canFilter = kind != Kind.LIVE && Tmdb.configured() &&
        !com.noirplus.tv.data.Library.isPlatformList(cat.id) && (virtual || Platforms.fromFolder(cat.name) == null)
    // Varias plataformas a la vez (p. ej. Netflix + Max): se guardan como "Netflix|Max"
    var platformsRaw by rememberSaveable { mutableStateOf("") }
    val platforms = remember(platformsRaw) { platformsRaw.split('|').filter { it.isNotEmpty() }.toSet() }
    var catalogs by remember { mutableStateOf<List<Tmdb.Catalog>?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(platformsRaw) {
        catalogs = null
        failed = emptyList()
        if (platforms.isEmpty()) return@LaunchedEffect
        val got = mutableListOf<Tmdb.Catalog>()
        val bad = mutableListOf<String>()
        try {
            for (p in Platforms.main.filter { it in platforms }) {
                progress = "Cargando catálogo de $p…"
                val c = Tmdb.catalog(kind, p) { done, total ->
                    if (total > 1 && (done % 5 == 0 || done == total)) progress = "Cargando catálogo de $p… ${done * 100 / total}%"
                }
                if (c != null) got += c else bad += p
            }
            catalogs = got
            failed = bad
        } finally {
            progress = null
        }
    }
    // Filtro por género (TMDB), también varios a la vez. No sale dentro de una lista que ya es de un género.
    val canGenre = kind != Kind.LIVE && Tmdb.configured() && !cat.id.startsWith(com.noirplus.tv.data.Library.GENRE)
    var genresRaw by rememberSaveable { mutableStateOf("") }
    val genres = remember(genresRaw) { genresRaw.split(',').mapNotNull { it.toIntOrNull() }.toSet() }
    var genreCats by remember { mutableStateOf<Collection<Tmdb.Catalog>?>(null) }
    val genreV2 = if (genres.isNotEmpty()) Tmdb.genreVersion.intValue else 0
    LaunchedEffect(genres.isNotEmpty()) {
        if (genres.isEmpty()) return@LaunchedEffect
        Tmdb.indexGenres(kind)
        genreCats = Tmdb.allCatalogs(kind).values
    }
    val fieldFr = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) {
            delay(150)
            runCatching { fieldFr.requestFocus() }
        }
    }
    // Cargar los catálogos para ordenar por fecha de estreno de TMDB (y reordenar cuando lleguen)
    val catV = Tmdb.catalogsVersion.intValue
    LaunchedEffect(kind) { if (kind != Kind.LIVE && Tmdb.configured()) runCatching { Tmdb.allCatalogs(kind) } }
    val shown = remember(items, query, sort, platformsRaw, catalogs, genresRaw, genreCats, genreV2, catV) {
        val q = normText(query.trim())
        items?.let { all ->
            var l = if (q.isEmpty()) all else all.filter { normText(it.name).contains(q) }
            if (platforms.isNotEmpty()) l = catalogs?.let { cs -> l.filter { i -> cs.any { it.has(i) } } } ?: emptyList()
            if (genres.isNotEmpty()) l = genreCats?.let { cs -> l.filter { i -> Tmdb.genresOf(i, cs)?.any { it in genres } == true } } ?: emptyList()
            // «Recién subido» conserva el orden de subida (lo último añadido primero)
            if (cat.id.startsWith(com.noirplus.tv.data.Library.NEW)) l else l.sortedFor(sort)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = d.edge - 4.dp, end = d.edge - 4.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Col.Glass.copy(alpha = 0.14f)).clickable { nav.pop() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Col.Text, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            if (searching) {
                Row(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(50)).background(Col.Surface2).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = Col.Sub, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Buscar en ${cat.name}", color = Col.Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Col.Text, fontSize = 14.sp, fontFamily = Inter),
                            cursorBrush = SolidColor(Col.Text),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fieldFr),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Filled.Close, "Cerrar búsqueda") {
                    query = ""
                    searching = false
                    keyboard?.hide()
                }
            } else {
                Text(
                    buildAnnotatedString {
                        append(pretty.main)
                        pretty.year?.let { withStyle(SpanStyle(fontSize = 13.sp, color = Col.Sub)) { append("  ($it)") } }
                    },
                    fontFamily = InterDisplay, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Col.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                RoundIconButton(Icons.Filled.Search, "Buscar") { searching = true }
            }
            if (canGenre) {
                Spacer(Modifier.width(8.dp))
                GenreButton(genres, Tmdb.genreNames(kind)) { g -> genresRaw = g.joinToString(",") }
            }
            if (canFilter) {
                Spacer(Modifier.width(8.dp))
                PlatformButton(platforms, Platforms.main) { p -> platformsRaw = Platforms.main.filter { it in p }.joinToString("|") }
            }
            if (d.compact) {
                Spacer(Modifier.width(8.dp))
                ColumnsButton(cols) { cols = it; prefs.gridCols = it }
            }
        }
        if (query.isNotBlank() || platforms.isNotEmpty() || genres.isNotEmpty()) {
            Text(
                listOfNotNull(
                    shown?.let { "${it.size} resultados" },
                    platforms.takeIf { it.isNotEmpty() }?.let { ps -> Platforms.main.filter { it in ps }.joinToString(" + ") },
                    genres.takeIf { it.isNotEmpty() }?.let { gs -> gs.mapNotNull { Tmdb.genreNames(kind)[it] }.joinToString(" + ") },
                    progress,
                    if (genres.isNotEmpty()) Tmdb.genreProgress.value?.let { "clasificando ${it.first}/${it.second}" } else null,
                ).joinToString("  ·  "),
                color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = d.edge, bottom = 6.dp),
            )
        }
        val list = shown
        when {
            list == null -> Loading()
            list.isEmpty() && progress != null -> Loading()
            list.isEmpty() -> Message(when {
                query.isNotBlank() -> "Sin resultados para «${query.trim()}»"
                platforms.isNotEmpty() && failed.isNotEmpty() -> "No se pudo cargar el catálogo de ${failed.joinToString(", ")}. Revisa la conexión."
                platforms.isNotEmpty() -> "Nada de " + Platforms.main.filter { it in platforms }.joinToString(" ni ") + " aquí"
                else -> "No hay contenido en esta categoría"
            })
            else -> LazyVerticalGrid(
                columns = if (LocalDims.current.compact) GridCells.Fixed(cols) else GridCells.Adaptive(LocalDims.current.poster),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = d.edge, end = d.edge, top = 4.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(d.gap),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(list, key = { i, it2 -> "$i:${it2.key}" }) { _, item ->
                    PosterCard(item, memKey = "cat/${item.key}", fill = true, onFocus = {}) { nav.push(Screen.Detail(item)) }
                }
            }
        }
    }
}
