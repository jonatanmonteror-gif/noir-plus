package com.noirplus.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Paleta monocroma de Noir+ (igual que en la tele). */
object Col {
    val Bg = Color(0xFF0A0A0B)
    val Surface = Color(0xFF161618)
    val Surface2 = Color(0xFF222225)
    val Text = Color(0xFFF5F5F7)
    val Sub = Color(0xFF9A9AA0)
    val Line = Color(0x22FFFFFF)
    val Error = Color(0xFFFF6B6B)
}

val Inter = FontFamily(
    Font("font/inter_regular.otf", FontWeight.Normal),
    Font("font/inter_medium.otf", FontWeight.Medium),
    Font("font/inter_semi_bold.otf", FontWeight.SemiBold),
    Font("font/inter_bold.otf", FontWeight.Bold),
)

val InterDisplay = FontFamily(
    Font("font/inter_display_semi_bold.otf", FontWeight.SemiBold),
    Font("font/inter_display_bold.otf", FontWeight.Bold),
)

val Playfair = FontFamily(Font("font/playfair_regular.ttf", FontWeight.Normal))

@Composable
fun NoirTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    fun TextStyle.i() = copy(fontFamily = Inter)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Color.Black,
            background = Col.Bg, surface = Col.Surface, onSurface = Col.Text, onBackground = Col.Text,
            surfaceVariant = Col.Surface2, outline = Col.Line,
        ),
        typography = base.copy(
            bodyLarge = base.bodyLarge.i(), bodyMedium = base.bodyMedium.i(), bodySmall = base.bodySmall.i(),
            titleLarge = base.titleLarge.i(), titleMedium = base.titleMedium.i(), titleSmall = base.titleSmall.i(),
            labelLarge = base.labelLarge.i(), labelMedium = base.labelMedium.i(), labelSmall = base.labelSmall.i(),
            headlineLarge = base.headlineLarge.copy(fontFamily = InterDisplay),
            headlineMedium = base.headlineMedium.copy(fontFamily = InterDisplay),
            headlineSmall = base.headlineSmall.copy(fontFamily = InterDisplay),
        ),
        content = content,
    )
}
