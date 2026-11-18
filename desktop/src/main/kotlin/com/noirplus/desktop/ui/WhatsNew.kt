package com.noirplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.desktop.data.Store
import com.noirplus.desktop.data.Updater
import kotlinx.coroutines.delay

/** Ventana de novedades, encima de todo. */
object NotesHost {
    private val state = mutableStateOf<Pair<String, List<Updater.Notes>>?>(null)
    var onClose: (() -> Unit)? = null
    fun show(title: String, notes: List<Updater.Notes>, closed: (() -> Unit)? = null) { onClose = closed; state.value = title to notes }

    @Composable
    fun Render() {
        val (title, notes) = state.value ?: return
        fun close() { state.value = null; onClose?.invoke(); onClose = null }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = ::close), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 620.dp).clip(RoundedCornerShape(16.dp)).background(Col.Surface).clickable(enabled = false) {}.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, color = Col.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (notes.isEmpty()) Text("Aún no hay novedades publicadas.", color = Col.Sub, fontSize = 13.sp)
                LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(notes, key = { it.version }) { n ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Versión ${n.version}" + (if (n.date.isNotBlank()) "  ·  " + prettyDate(n.date) else ""),
                                color = Col.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            n.items.forEach { line ->
                                Row {
                                    Text("•", color = Col.Text, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                                    Text(line, color = Col.Text, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { NButton("Entendido", primary = true) { close() } }
            }
        }
    }

    private fun prettyDate(iso: String): String {
        val p = iso.split('-').mapNotNull { it.toIntOrNull() }
        if (p.size < 3) return iso
        val m = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        return "${p[2]} ${m.getOrElse(p[1] - 1) { "" }} ${p[0]}"
    }
}

/** Tras actualizar: enseña una vez lo nuevo de esta versión (y de las que te hayas saltado). */
@Composable
fun WhatsNewAfterUpdate() {
    LaunchedEffect(Unit) {
        val cur = Updater.currentCode
        val seen = Store.notesSeen
        if (seen >= cur) return@LaunchedEffect
        delay(2500)
        // La primera vez solo lo de esta versión
        val from = if (seen == 0) cur else seen + 1
        val mine = Updater.changelog().filter { it.code in from..cur }
        if (mine.isEmpty()) Store.notesSeen = cur
        else NotesHost.show("Novedades de esta versión", mine) { Store.notesSeen = cur }
    }
}
