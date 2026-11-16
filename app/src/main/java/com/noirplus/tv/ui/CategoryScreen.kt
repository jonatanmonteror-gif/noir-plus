package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.Repo

/** "Ver todo": todas las películas o series de una categoría en cuadrícula. */
@Composable
fun CategoryScreen(kind: Kind, cat: Category) {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val nav = LocalNav.current
    val d = LocalDims.current
    val items by produceState<List<Item>?>(null, kind, cat.id) {
        value = runCatching { src.items(kind, cat.id) }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = d.edge - 4.dp, end = d.edge, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Col.Glass).clickable { nav.pop() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Col.Text) }
            Text(
                cat.name, fontFamily = InterDisplay, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Col.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp),
            )
        }
        val list = items
        when {
            list == null -> Loading()
            list.isEmpty() -> Message("No hay contenido en esta categoría")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(d.poster),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = d.edge, end = d.edge, top = 8.dp, bottom = 32.dp),
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
