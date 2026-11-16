package com.noirplus.tv.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.noirplus.tv.R

/** Inter: la tipografía libre más parecida a la San Francisco de Apple. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_bold, FontWeight.ExtraBold),
    Font(R.font.inter_bold, FontWeight.Black),
    Font(R.font.inter_regular, FontWeight.Light),
)

private fun TextStyle.inter() = copy(fontFamily = Inter)

fun interTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.inter(),
        displayMedium = d.displayMedium.inter(),
        displaySmall = d.displaySmall.inter(),
        headlineLarge = d.headlineLarge.inter(),
        headlineMedium = d.headlineMedium.inter(),
        headlineSmall = d.headlineSmall.inter(),
        titleLarge = d.titleLarge.inter(),
        titleMedium = d.titleMedium.inter(),
        titleSmall = d.titleSmall.inter(),
        bodyLarge = d.bodyLarge.inter(),
        bodyMedium = d.bodyMedium.inter(),
        bodySmall = d.bodySmall.inter(),
        labelLarge = d.labelLarge.inter(),
        labelMedium = d.labelMedium.inter(),
        labelSmall = d.labelSmall.inter(),
    )
}
