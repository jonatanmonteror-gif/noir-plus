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
import com.noirplus.tv.data.Repo
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
    val items by produceState<List<Item>?>(null, kind, cat.id) {
        value = runCatching {
            if (isAll) src.all(kind).filter { prefs.visible(kind, it.categoryId) } else src.items(kind, cat.id)
        }.getOrDefault(emptyList())
    }
    val pretty = remember(cat.name) { if (isAll) PrettyName(cat.name, null) else prettyCategory(cat.name, kind) }
    var searching by rememberSaveable { mutableStateOf(startSearch) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortMode.DEFAULT) }
    val fieldFr = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) {
            delay(150)
            runCatching { fieldFr.requestFocus() }
        }
    }
    val shown = remember(items, query, sort) {
        val q = normText(query.trim())
        items?.let { all -> (if (q.isEmpty()) all else all.filter { normText(it.name).contains(q) }).sortedFor(sort) }
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
                if (isAll) RoundIconButton(Icons.Filled.Search, "Buscar") { searching = true }
            }
            if (isAll) {
                Spacer(Modifier.width(8.dp))
                SortButton(sort) { sort = it }
            }
        }
        if (sort != SortMode.DEFAULT || query.isNotBlank()) {
            Text(
                listOfNotNull(
                    shown?.let { "${it.size} resultados" },
                    sort.takeIf { it != SortMode.DEFAULT }?.label,
                ).joinToString("  ·  "),
                color = Col.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = d.edge, bottom = 6.dp),
            )
        }
        val list = shown
        when {
            list == null -> Loading()
            list.isEmpty() -> Message(if (query.isBlank()) "No hay contenido en esta categoría" else "Sin resultados para «${query.trim()}»")
            else -> LazyVerticalGrid(
                columns = if (LocalDims.current.compact) GridCells.Fixed(Repo.prefs.gridCols) else GridCells.Adaptive(LocalDims.current.poster),
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
