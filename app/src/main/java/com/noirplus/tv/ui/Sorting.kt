package com.noirplus.tv.ui

import androidx.compose.foundation.background
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
    SortMode.RECENT -> sortedWith(compareByDescending<Item> { it.added }.thenByDescending { it.year?.toIntOrNull() ?: 0 })
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

/** Botón de filtrar por plataforma (datos de TMDB) con su menú. null = todas. */
@Composable
fun PlatformButton(selected: String?, options: List<String>, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(PlatformIcon, "Plataforma", active = selected != null) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Col.Surface2)) {
            (listOf<String?>(null) + options).forEach { p ->
                val sel = p == selected
                DropdownMenuItem(
                    text = {
                        Text(p ?: "Todas las plataformas", color = if (sel) Color.White else Col.Sub, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = {
                        onChange(p)
                        open = false
                    },
                )
            }
        }
    }
}
