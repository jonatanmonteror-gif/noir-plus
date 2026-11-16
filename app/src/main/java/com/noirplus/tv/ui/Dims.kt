package com.noirplus.tv.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Medidas según el dispositivo: TV, móvil (compacto) o tablet / móvil en horizontal. */
data class Dims(
    val tv: Boolean,
    val compact: Boolean,
    val edge: Dp,
    val poster: Dp,
    val wide: Dp,
    val heroH: Dp,
    val gap: Dp,
)

val TvDims = Dims(tv = true, compact = false, edge = 48.dp, poster = 112.dp, wide = 210.dp, heroH = 196.dp, gap = 14.dp)
val PhoneDims = Dims(tv = false, compact = true, edge = 16.dp, poster = 104.dp, wide = 190.dp, heroH = 300.dp, gap = 10.dp)
val TabletDims = Dims(tv = false, compact = false, edge = 28.dp, poster = 118.dp, wide = 220.dp, heroH = 230.dp, gap = 12.dp)

/** En TV todo se dibuja al 85 % (un 15 % más pequeño). */
const val TV_SCALE = 0.85f

val LocalDims = staticCompositionLocalOf { TvDims }

/** Margen lateral de la pantalla actual. */
val edge: Dp
    @Composable @ReadOnlyComposable get() = LocalDims.current.edge

fun isTvDevice(ctx: Context): Boolean {
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = ctx.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
