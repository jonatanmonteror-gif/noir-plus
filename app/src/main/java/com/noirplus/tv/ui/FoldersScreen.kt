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

/** Ajustes → Ocultar y proteger carpetas. */
@Composable
fun FoldersScreen() {
    val src = Repo.source ?: return Message("No hay ninguna lista configurada")
    val prefs = Repo.prefs
    val d = LocalDims.current
    val changes = prefs.changes.intValue
    var tab by rememberSaveable { mutableStateOf(0) }
    var askPin by remember { mutableStateOf(false) }
    val kind = listOf(Kind.MOVIE, Kind.SERIES, Kind.LIVE)[tab]
    val cats by produceState<List<Category>?>(null, kind, Repo.dataVersion.intValue) {
        value = runCatching { src.categories(kind) }.getOrDefault(emptyList())
    }
    val hidden = remember(changes) { prefs.hidden }
    val locked = remember(changes) { prefs.locked }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = d.edge, end = d.edge, top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ocultar y proteger carpetas", fontFamily = InterDisplay, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Col.Text)
                Text(
                    if (prefs.pin.isEmpty()) "«Ocultar» la quita de la app. Para «Proteger» (pedir PIN) primero crea un PIN en Ajustes."
                    else "«Ocultar» la quita de la app. «Proteger» solo la muestra tras poner el PIN en Ajustes.",
                    color = Col.Sub, fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Películas", "Series", "Directo").forEachIndexed { i, l ->
                        ActionButton(l, primary = i == tab, key = "ft-$i") { tab = i }
                    }
                }
            }
            val list = cats
            when {
                list == null -> Loading()
                list.isEmpty() -> Message("No hay carpetas")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = d.edge, end = d.edge, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(list, key = { i, c -> "$i:${c.id}" }) { _, c ->
                        val k = "${kind.name}:${c.id}"
                        val p = prettyCategory(c.name, kind)
                        val adult = adultWords.any { c.name.lowercase().contains(it) }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Col.Surface).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                p.main + (p.year?.let { " ($it)" } ?: "") + if (adult) "  ·  sugerida para proteger" else "",
                                color = if (k in hidden) Col.Sub else Col.Text, fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            ActionButton(if (k in hidden) "Oculta" else "Ocultar", primary = k in hidden, key = "fh-$k") { prefs.toggleHidden(k) }
                            ActionButton(if (k in locked) "Protegida" else "Proteger", primary = k in locked, key = "fl-$k",
                                modifier = Modifier.padding(start = 8.dp)) {
                                if (prefs.pin.isEmpty() && k !in locked) askPin = true else prefs.toggleLocked(k)
                            }
                        }
                    }
                }
            }
        }
        if (askPin) {
            PinDialog("Crea un PIN de 4 cifras", onCancel = { askPin = false }) { pin ->
                prefs.pin = pin
                askPin = false
                null
            }
        }
    }
}
