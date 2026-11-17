package com.noirplus.tv.ui

import androidx.compose.foundation.background
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
    RECENT("Más recientes"),
    RATING("Mejor valoradas"),
    AZ("Título A → Z"),
    ZA("Título Z → A"),
}

private val marks = Regex("\\p{Mn}+")
fun normText(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

fun List<Item>.sortedFor(mode: SortMode): List<Item> = when (mode) {
    SortMode.DEFAULT -> this
    // Por fecha de estreno de TMDB (si no se conoce, el año del proveedor); a igualdad, lo último añadido
    SortMode.RECENT -> com.noirplus.tv.data.Tmdb.byRelease(this)
    SortMode.RATING -> sortedByDescending { parseRating(it.rating) ?: 0f }
    SortMode.AZ -> sortedBy { normText(it.name) }
    SortMode.ZA -> sortedByDescending { normText(it.name) }
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
fun <T> OptionPicker(open: Boolean, title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    if (LocalDims.current.compact) {
        if (!open) return
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onDismiss() }) {
                androidx.compose.animation.AnimatedVisibility(
                    shown,
                    enter = androidx.compose.animation.slideInHorizontally { it },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxHeight().width(290.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                            .background(Col.Surface)
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { }
                            .padding(top = 40.dp, bottom = 24.dp),
                    ) {
                        Text(title, color = Col.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 22.dp, bottom = 12.dp))
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                            items(options.size) { i ->
                                val (v, label) = options[i]
                                val sel = v == selected
                                androidx.compose.foundation.layout.Row(
                                    Modifier.fillMaxWidth().clickable { onPick(v); onDismiss() }
                                        .background(if (sel) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                        .padding(horizontal = 22.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, color = if (sel) Color.White else Col.Sub, fontSize = 15.sp,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                    if (sel) Text("✓", color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        DropdownMenu(expanded = open, onDismissRequest = onDismiss, modifier = Modifier.background(Col.Surface2)) {
            options.forEach { (v, label) ->
                val sel = v == selected
                DropdownMenuItem(
                    text = {
                        Text(label, color = if (sel) Color.White else Col.Sub, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = { onPick(v); onDismiss() },
                )
            }
        }
    }
}

/** Botón de filtrar por plataforma (datos de TMDB). null = todas. */
@Composable
fun PlatformButton(selected: String?, options: List<String>, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(PlatformIcon, "Plataforma", active = selected != null) { open = true }
        OptionPicker(open, "Plataforma", listOf<Pair<String?, String>>(null to "Todas las plataformas") + options.map { it to it },
            selected, onChange) { open = false }
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
fun GenreButton(selected: Int?, genres: Map<Int, String>, onChange: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(GenreIcon, "Género", active = selected != null) { open = true }
        OptionPicker(open, "Género", listOf<Pair<Int?, String>>(null to "Todos los géneros") + genres.map { it.key to it.value },
            selected, onChange) { open = false }
    }
}
