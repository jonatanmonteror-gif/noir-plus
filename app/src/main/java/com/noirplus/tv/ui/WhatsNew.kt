package com.noirplus.tv.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
fun NotesDialog(title: String, allNotes: List<Updater.Notes>, onClose: () -> Unit) {
    // Solo lo de este aparato (tele o móvil)
    val tv = LocalDims.current.tv
    val notes = remember(allNotes, tv) {
        allNotes.map { it.copy(items = forDevice(it.items, tv)) }.filter { it.items.isNotEmpty() }
    }
    val fr = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    val scrimTap = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Tocar fuera de la ventana también la cierra (en el móvil); la ventana se come sus propios toques
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))
            .clickable(interactionSource = scrimTap, indication = null) { onClose() }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 560.dp).heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp)).background(Col.Surface2)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Col.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("✕", color = Col.Sub, fontSize = 18.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClose() }.padding(8.dp))
            }
            if (notes.isEmpty()) Text("Aún no hay novedades publicadas.", color = Col.Sub, fontSize = 13.sp)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(notes, key = { i, it -> "$i:${it.version}" }) { _, n ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (n.version == "2.0") "Noir+ 2.0" else "Versión ${Updater.display(n.version)}" + (if (n.date.isNotBlank()) "  ·  " + prettyDate(n.date) else ""),
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
    val tv = LocalDims.current.tv
    LaunchedEffect(Unit) {
        // Llegada a la 2.0: una sola vez, la bienvenida a la versión estable
        if (com.noirplus.tv.BuildConfig.VERSION_NAME.startsWith("2.") && !prefs.seen20) {
            delay(1500)
            notes = listOf(Updater.Notes("2.0", "", listOf(
            "Bienvenido a Noir+ 2.0. Ya es la versión estable: todo lo que necesitas para ver películas, series y canales, en una sola app.",
            "Inicio como el de las plataformas: tendencias en España, series con capítulos nuevos, lo recién llegado y filas de Netflix, Max, Prime Video, Disney+ y más.",
            "Películas y series por plataforma y por género, con filtros combinados, fecha de estreno, tráiler en español y orden por últimos estrenos.",
            "Próximos estrenos: lo que llega el próximo mes y lo recién estrenado, con avisos cuando tu proveedor lo sube y cuando sale un capítulo nuevo de lo que sigues.",
            "Directo por temáticas, con la carpeta Movistar+ ordenada como su dial y todas las versiones de cada canal para elegir la que mejor va.",
            "Tele: reproductor pensado para el mando, con audio y subtítulos en la flecha arriba y avance continuo con izquierda y derecha.",
            "Listas guardadas en tu aparato y actualizadas solas a horas fijas: entra al momento y carga menos al proveedor.",
            "Más rápida y más estable, y vuelves siempre a donde estabas.",
            )))
            return@LaunchedEffect
        }
        val seen = prefs.notesSeen
        if (seen >= cur) return@LaunchedEffect
        delay(2500)
        // La primera vez solo lo de esta versión
        val from = if (seen == 0) cur else seen + 1
        val mine = Updater.changelog().filter { it.code in from..cur && forDevice(it.items, tv).isNotEmpty() }
        if (mine.isEmpty()) prefs.notesSeen = cur else notes = mine
    }
    val n = notes ?: return
    val is20 = n.firstOrNull()?.version == "2.0"
    NotesDialog(if (is20) "¡Bienvenido a Noir+ 2.0!" else "Novedades de esta versión", n) {
        if (is20) prefs.seen20 = true
        prefs.notesSeen = cur; notes = null
    }
}
