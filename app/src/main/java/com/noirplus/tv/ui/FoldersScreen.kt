package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.Repo

private val adultWords = listOf("xxx", "adult", "+18", "18+", "porn", "erotic", "erótic")

/** Ajustes → Ocultar y proteger carpetas: cada carpeta Visible / Oculta / Con PIN. */
@Composable
fun FoldersScreen() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val d = LocalDims.current
    val changes = prefs.changes.intValue
    var tab by rememberSaveable { mutableStateOf(0) }
    var pinFor by remember { mutableStateOf<String?>(null) }
    val kind = listOf(Kind.MOVIE, Kind.SERIES, Kind.LIVE)[tab]
    val cats by produceState<List<Category>?>(null, kind, Repo.dataVersion.intValue) {
        value = runCatching { src.categories(kind) }.getOrDefault(emptyList())
    }
    val hidden = remember(changes) { prefs.hidden }
    val locked = remember(changes) { prefs.locked }

    fun stateOf(k: String) = when { k in hidden -> 1; k in locked -> 2; else -> 0 }
    fun setState(k: String, st: Int) {
        if (st == 2 && prefs.pin.isEmpty()) { pinFor = k; return }
        if ((k in hidden) != (st == 1)) prefs.toggleHidden(k)
        if ((k in locked) != (st == 2)) prefs.toggleLocked(k)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = d.edge, end = d.edge, top = 18.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ocultar y proteger carpetas", fontFamily = InterDisplay, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Col.Text)
                Box(Modifier.widthIn(max = 420.dp)) {
                    Segmented(listOf("Películas", "Series", "Directo"), tab, "ft") { tab = it }
                }
                val list = cats.orEmpty()
                val nHidden = list.count { "${kind.name}:${it.id}" in hidden }
                val nLocked = list.count { "${kind.name}:${it.id}" in locked && "${kind.name}:${it.id}" !in hidden }
                Text(
                    buildString {
                        append("Oculta: no aparece en ningún sitio.  Con PIN: solo aparece tras poner tu PIN en Ajustes.")
                        if (kind == Kind.LIVE) append("\nSon las carpetas de tu proveedor: sus canales dejan de salir en las temáticas.")
                        if (nHidden + nLocked > 0) append("\n$nHidden ocultas · $nLocked con PIN")
                    },
                    color = Col.Sub, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            val list = cats
            when {
                list == null -> Loading()
                list.isEmpty() -> Message("No hay carpetas")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = d.edge, end = d.edge, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(list, key = { i, c -> "$i:${c.id}" }) { _, c ->
                        val k = "${kind.name}:${c.id}"
                        val p = prettyCategory(c.name, kind)
                        val adult = adultWords.any { c.name.lowercase().contains(it) }
                        val st = stateOf(k)
                        val title: @Composable () -> Unit = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.main + (p.year?.let { " ($it)" } ?: ""),
                                    color = if (st == 1) Col.Sub else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                                if (adult) Text("+18", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(Col.Sub)
                                        .padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        val control: @Composable (Modifier) -> Unit = { m ->
                            Box(m) { Segmented(listOf("Visible", "Oculta", "Con PIN"), st, "fs-$k") { setState(k, it) } }
                        }
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (st == 0) Col.Surface else Col.Surface.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (d.compact) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                title()
                                control(Modifier.fillMaxWidth())
                            } else Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).padding(end = 16.dp)) { title() }
                                control(Modifier.width(300.dp))
                            }
                        }
                    }
                }
            }
        }
        pinFor?.let { k ->
            PinDialog("Crea un PIN de 4 cifras", onCancel = { pinFor = null }) { pin ->
                prefs.pin = pin
                if (k in hidden) prefs.toggleHidden(k)
                if (k !in prefs.locked) prefs.toggleLocked(k)
                pinFor = null
                null
            }
        }
    }
}
