package com.noirplus.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay

/** Qué se ha mantenido pulsado: puede ir a Mi lista (favItem) y/o valorarse (rate). */
data class MenuTarget(val title: String, val image: String?, val label: String, val favItem: Item?, val rate: RateTarget?)

/** Abre el menú de opciones (pulsación larga) desde cualquier pantalla. */
val LocalItemMenu = staticCompositionLocalOf<(MenuTarget) -> Unit> { {} }

fun Item.menuTarget(): MenuTarget = MenuTarget(
    name, image,
    when (kind) {
        com.noirplus.tv.data.Kind.LIVE -> "Canal"
        com.noirplus.tv.data.Kind.SERIES -> "Serie"
        else -> "Película"
    },
    favItem = this,
    rate = if (kind == com.noirplus.tv.data.Kind.LIVE) null else rateTarget(),
)

@Composable
private fun MenuRow(icon: ImageVector, label: String, value: String? = null, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), focusScale = 1f, focusRequester = focusRequester, onClick = onClick) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) Color.White else Color.Transparent).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (f) Color.Black else Col.Text, modifier = Modifier.size(20.dp))
            Text(label, color = if (f) Color.Black else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp).weight(1f))
            value?.let { Text(it, color = if (f) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ItemMenu(t: MenuTarget, onRate: (RateTarget) -> Unit, onClose: () -> Unit) {
    val prefs = Repo.prefs
    val d = LocalDims.current
    val changes = prefs.changes.intValue
    val isFav = remember(changes) { t.favItem?.let { prefs.isFavorite(it.key) } ?: false }
    val mine = remember(changes) { t.rate?.let { prefs.rating(it.key) } }
    val firstFr = remember { FocusRequester() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    BackHandler { onClose() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFr.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInputTap(onClose)) {
        AnimatedVisibility(
            visibleState = shown,
            enter = if (d.compact) slideInVertically(tween(260)) { it } + fadeIn() else scaleIn(tween(200), initialScale = 0.92f) + fadeIn(),
            exit = if (d.compact) slideOutVertically { it } + fadeOut() else scaleOut() + fadeOut(),
            modifier = Modifier.align(if (d.compact) Alignment.BottomCenter else Alignment.Center),
        ) {
            Column(
                (if (d.compact) Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                else Modifier.widthIn(max = 380.dp).clip(RoundedCornerShape(18.dp)))
                    .background(Col.Surface2)
                    .pointerInputTap {}
                    .padding(horizontal = 14.dp, vertical = 16.dp)
                    .padding(bottom = if (d.compact) 16.dp else 0.dp),
            ) {
                if (d.compact) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)))
                    }
                }
                Row(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (t.image != null) {
                        AsyncImage(t.image, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(44.dp).aspectRatio(2f / 3f).clip(CardShape).background(Col.Surface))
                    }
                    Column(Modifier.padding(start = if (t.image != null) 12.dp else 0.dp)) {
                        Text(t.label.uppercase(), color = Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Text(t.title, color = Col.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.1f)))
                Column(Modifier.padding(top = 6.dp)) {
                    val fav = t.favItem
                    var first = true
                    if (fav != null) {
                        MenuRow(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (isFav) "Quitar de Mi lista" else "Añadir a Mi lista",
                            focusRequester = firstFr,
                        ) {
                            prefs.toggleFavorite(fav)
                            onClose()
                        }
                        first = false
                    }
                    t.rate?.takeIf { prefs.ratingsEnabled }?.let { r ->
                        MenuRow(
                            ReviewIcon,
                            if (mine == null) "Valorar" else "Cambiar valoración",
                            value = mine?.let { "${fmtStars(it.stars)} ★" },
                            focusRequester = if (first) firstFr else null,
                        ) {
                            onClose()
                            onRate(r)
                        }
                    }
                }
            }
        }
    }
}

/** Envuelve la app: ofrece el menú de pulsación larga y el diálogo de valoración. */
@Composable
fun RateHost(content: @Composable () -> Unit) {
    var rateTarget by remember { mutableStateOf<RateTarget?>(null) }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    CompositionLocalProvider(
        LocalRate provides { t -> rateTarget = t },
        LocalItemMenu provides { t -> menuTarget = t },
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            menuTarget?.let { m -> ItemMenu(m, onRate = { rateTarget = it }) { menuTarget = null } }
            rateTarget?.let { RatingDialog(it) { rateTarget = null } }
        }
    }
}

private fun Modifier.pointerInputTap(onTap: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit) { detectTapGestures { onTap() } })
