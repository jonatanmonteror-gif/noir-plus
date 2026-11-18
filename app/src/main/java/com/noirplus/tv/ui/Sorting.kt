package com.noirplus.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Item
import java.text.Normalizer

enum class SortMode(val label: String) {
    DEFAULT("Orden original"),
    RECENT("Últimos estrenos"),
    RATING("Mejor valoradas"),
}

private val marks = Regex("\\p{Mn}+")
fun normText(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

fun List<Item>.sortedFor(mode: SortMode): List<Item> = when (mode) {
    SortMode.DEFAULT -> this
    // Por fecha de estreno de TMDB (si no se conoce, el año del proveedor); a igualdad, lo último añadido
    SortMode.RECENT -> com.noirplus.tv.data.Tmdb.byRelease(this)
    SortMode.RATING -> sortedByDescending { parseRating(it.rating) ?: 0f }
}

/** Icono "ordenar" (tres líneas decrecientes). */
val SortIcon: ImageVector = materialIcon(name = "Filled.Sort") {
    materialPath {
        moveTo(3f, 18f); horizontalLineToRelative(6f); verticalLineToRelative(-2f); horizontalLineTo(3f); verticalLineToRelative(2f); close()
        moveTo(3f, 6f); verticalLineToRelative(2f); horizontalLineToRelative(18f); verticalLineTo(6f); horizontalLineTo(3f); close()
        moveTo(3f, 13f); horizontalLineToRelative(12f); verticalLineToRelative(-2f); horizontalLineTo(3f); verticalLineToRelative(2f); close()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
    Focusable(Modifier.size(34.dp), shape = CircleShape, focusScale = 1.12f, onClick = onClick) { focused ->
        Box(
            Modifier.matchParentSize().background(if (active || focused) Color.White else Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, desc, tint = if (active || focused) Color.Black else Col.Text, modifier = Modifier.size(18.dp)) }
    }
}

/** Botón de ordenar con su menú desplegable. */
@Composable
fun SortButton(mode: SortMode, default: SortMode = SortMode.DEFAULT, onChange: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(SortIcon, "Ordenar", active = mode != default) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Col.Surface2)) {
            SortMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Text(m.label, color = if (m == mode) Color.White else Col.Sub, fontSize = 14.sp,
                            fontWeight = if (m == mode) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = {
                        onChange(m)
                        open = false
                    },
                )
            }
        }
    }
}

/** Icono "plataformas" (una tele con un triángulo de play). */
val PlatformIcon: ImageVector = materialIcon(name = "Filled.Platforms") {
    materialPath {
        // marco de la pantalla
        moveTo(3f, 5f); horizontalLineTo(21f); verticalLineTo(17f); horizontalLineTo(3f); close()
        moveTo(5f, 7f); verticalLineTo(15f); horizontalLineTo(19f); verticalLineTo(7f); close()
        // play
        moveTo(10f, 8.5f); lineTo(15f, 11f); lineTo(10f, 13.5f); close()
        // peana
        moveTo(8f, 19f); horizontalLineTo(16f); verticalLineTo(21f); horizontalLineTo(8f); close()
    }
}


/** Icono "columnas" (cuadrícula 2×2). */
val GridIcon: ImageVector = materialIcon(name = "Filled.GridSmall") {
    materialPath {
        moveTo(4f, 4f); horizontalLineTo(11f); verticalLineTo(11f); horizontalLineTo(4f); close()
        moveTo(13f, 4f); horizontalLineTo(20f); verticalLineTo(11f); horizontalLineTo(13f); close()
        moveTo(4f, 13f); horizontalLineTo(11f); verticalLineTo(20f); horizontalLineTo(4f); close()
        moveTo(13f, 13f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(13f); close()
    }
}


/** Icono "lista" (filas). */
val ListIcon: ImageVector = materialIcon(name = "Filled.ListRows") {
    materialPath {
        moveTo(3f, 5f); horizontalLineTo(6f); verticalLineTo(8f); horizontalLineTo(3f); close()
        moveTo(8f, 5f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(8f); close()
        moveTo(3f, 10.5f); horizontalLineTo(6f); verticalLineTo(13.5f); horizontalLineTo(3f); close()
        moveTo(8f, 10.5f); horizontalLineTo(21f); verticalLineTo(13.5f); horizontalLineTo(8f); close()
        moveTo(3f, 16f); horizontalLineTo(6f); verticalLineTo(19f); horizontalLineTo(3f); close()
        moveTo(8f, 16f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(8f); close()
    }
}


/** Icono "géneros" (etiqueta). */
val GenreIcon: ImageVector = materialIcon(name = "Filled.GenreTag") {
    materialPath {
        moveTo(3f, 3f); horizontalLineTo(11f); lineTo(21f, 13f); lineTo(13f, 21f); lineTo(3f, 11f); close()
        moveTo(7.5f, 6f); arcToRelative(1.5f, 1.5f, 0f, true, false, 0.01f, 0f); close()
    }
}


/** Icono "tráiler" (pantalla con play). */
val TrailerIcon: ImageVector = materialIcon(name = "Filled.Trailer") {
    materialPath {
        moveTo(2f, 5f); horizontalLineTo(22f); verticalLineTo(19f); horizontalLineTo(2f); close()
        moveTo(10f, 8.5f); verticalLineTo(15.5f); lineTo(16f, 12f); close()
    }
}

/**
 * Selector de opciones: en el móvil, panel lateral a la derecha (cómodo de recorrer, no se sale de la pantalla);
 * en la tele, el menú desplegable de siempre.
 */
@Composable
fun <T> OptionPicker(
    open: Boolean, title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit,
    isSel: ((T) -> Boolean)? = null, closeOnPick: Boolean = true, onDismiss: () -> Unit,
) {
    if (LocalDims.current.compact) {
        // Móvil: tarjeta flotante compacta bajo el botón (no ocupa la pantalla), con desplazamiento si hay muchas opciones
        if (!open) return
        val dy = with(androidx.compose.ui.platform.LocalDensity.current) { 42.dp.roundToPx() }
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopEnd,
            offset = androidx.compose.ui.unit.IntOffset(0, dy),
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            androidx.compose.animation.AnimatedVisibility(
                shown,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) +
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(160), initialScale = 0.92f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            ) {
                androidx.compose.foundation.layout.Column(
                    Modifier.width(220.dp)
                        .shadow(18.dp, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .background(Col.Surface2)
                        .border(0.5.dp, Color.White.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .padding(vertical = 6.dp),
                ) {
                    Text(title.uppercase(), color = Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp))
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(options.size) { i ->
                            val (v, label) = options[i]
                            val sel = isSel?.invoke(v) ?: (v == selected)
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth().clickable { onPick(v); if (closeOnPick) onDismiss() }.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, color = if (sel) Color.White else Col.Text.copy(alpha = 0.8f), fontSize = 14.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                if (sel) Text("✓", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    } else {
        DropdownMenu(expanded = open, onDismissRequest = onDismiss, modifier = Modifier.background(Col.Surface2)) {
            options.forEach { (v, label) ->
                val sel = isSel?.invoke(v) ?: (v == selected)
                DropdownMenuItem(
                    text = {
                        Text(label, color = if (sel) Color.White else Col.Sub, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    trailingIcon = { if (sel && !closeOnPick) Text("✓", color = Color.White, fontSize = 13.sp) },
                    onClick = { onPick(v); if (closeOnPick) onDismiss() },
                )
            }
        }
    }
}

/** Botón de filtrar por plataforma (datos de TMDB). null = todas. */
@Composable
fun PlatformButton(selected: Set<String>, options: List<String>, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(PlatformIcon, "Plataformas", active = selected.isNotEmpty()) { open = true }
        // Se pueden marcar varias (p. ej. Netflix y Max); «Todas» las desmarca
        OptionPicker(open, "Plataformas (varias)", listOf<Pair<String?, String>>(null to "Todas las plataformas") + options.map { it to it },
            null, onPick = { v -> onChange(if (v == null) emptySet() else if (v in selected) selected - v else selected + v) },
            isSel = { v -> if (v == null) selected.isEmpty() else v in selected }, closeOnPick = false) { open = false }
    }
}

/** Botón para elegir 2, 3 o 4 columnas desde la propia lista (móvil). */
@Composable
fun ColumnsButton(cols: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(GridIcon, "Columnas") { open = true }
        OptionPicker(open, "Columnas", (2..4).map { it to "$it columnas" }, cols, onChange) { open = false }
    }
}

/** Botón de filtrar por género. null = todos. */
@Composable
fun GenreButton(selected: Set<Int>, genres: Map<Int, String>, onChange: (Set<Int>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(GenreIcon, "Géneros", active = selected.isNotEmpty()) { open = true }
        OptionPicker(open, "Géneros (varios)", listOf<Pair<Int?, String>>(null to "Todos los géneros") + genres.map { it.key to it.value },
            null, onPick = { v -> onChange(if (v == null) emptySet() else if (v in selected) selected - v else selected + v) },
            isSel = { v -> if (v == null) selected.isEmpty() else v in selected }, closeOnPick = false) { open = false }
    }
}
