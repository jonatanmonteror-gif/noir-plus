package com.noirplus.tv.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.noirplus.tv.R

/** Inter completa: los 9 pesos (Thin → Black) con sus cursivas. Es la libre más parecida a la San Francisco de Apple. */
val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.inter_extra_light, FontWeight.ExtraLight),
    Font(R.font.inter_extra_light_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_regular_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_semi_bold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.inter_extra_bold, FontWeight.ExtraBold),
    Font(R.font.inter_extra_bold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.inter_black, FontWeight.Black),
    Font(R.font.inter_black_italic, FontWeight.Black, FontStyle.Italic),
)

/** Inter Display: versión de Inter dibujada para tamaños grandes (títulos). */
val InterDisplay = FontFamily(
    Font(R.font.inter_display_thin, FontWeight.Thin),
    Font(R.font.inter_display_extra_light, FontWeight.ExtraLight),
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_extra_bold, FontWeight.ExtraBold),
    Font(R.font.inter_display_black, FontWeight.Black),
)

private fun TextStyle.text() = copy(fontFamily = Inter)
private fun TextStyle.display() = copy(fontFamily = InterDisplay)

fun interTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.display(),
        displayMedium = d.displayMedium.display(),
        displaySmall = d.displaySmall.display(),
        headlineLarge = d.headlineLarge.display(),
        headlineMedium = d.headlineMedium.display(),
        headlineSmall = d.headlineSmall.display(),
        titleLarge = d.titleLarge.text(),
        titleMedium = d.titleMedium.text(),
        titleSmall = d.titleSmall.text(),
        bodyLarge = d.bodyLarge.text(),
        bodyMedium = d.bodyMedium.text(),
        bodySmall = d.bodySmall.text(),
        labelLarge = d.labelLarge.text(),
        labelMedium = d.labelMedium.text(),
        labelSmall = d.labelSmall.text(),
    )
}
