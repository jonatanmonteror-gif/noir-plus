package com.noirplus.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Repo
import com.noirplus.tv.data.Updater
import kotlinx.coroutines.delay

/** Ventana con las novedades de una o varias versiones. */
@Composable
fun NotesDialog(title: String, notes: List<Updater.Notes>, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 560.dp).clip(RoundedCornerShape(16.dp)).background(Col.Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = Col.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (notes.isEmpty()) Text("Aún no hay novedades publicadas.", color = Col.Sub, fontSize = 13.sp)
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(notes, key = { it.version }) { n ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Versión ${n.version}" + (if (n.date.isNotBlank()) "  ·  " + prettyDate(n.date) else ""),
                            color = Col.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        n.items.forEach { line ->
                            Row {
                                Text("•", color = Col.Text, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                                Text(line, color = Col.Text, fontSize = 14.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton("Entendido", primary = true, key = "notes-ok", focusRequester = fr) { onClose() }
            }
        }
    }
}

private fun prettyDate(iso: String): String {
    val p = iso.split('-').mapNotNull { it.toIntOrNull() }
    if (p.size < 3) return iso
    val m = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    return "${p[2]} ${m.getOrElse(p[1] - 1) { "" }} ${p[0]}"
}

/** Tras actualizar: enseña una vez lo nuevo de esta versión (y de las que te hayas saltado). */
@Composable
fun WhatsNewAfterUpdate() {
    val prefs = Repo.prefs
    val cur = Updater.currentCode
    var notes by remember { mutableStateOf<List<Updater.Notes>?>(null) }
    LaunchedEffect(Unit) {
        val seen = prefs.notesSeen
        if (seen >= cur) return@LaunchedEffect
        delay(2500)
        // La primera vez solo lo de esta versión
        val from = if (seen == 0) cur else seen + 1
        val mine = Updater.changelog().filter { it.code in from..cur }
        if (mine.isEmpty()) prefs.notesSeen = cur else notes = mine
    }
    val n = notes ?: return
    NotesDialog("Novedades de esta versión", n) { prefs.notesSeen = cur; notes = null }
}
