package com.noirplus.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.noirplus.desktop.data.*
import kotlinx.coroutines.launch

// ───────────────────────── Navegación ─────────────────────────

sealed interface Screen {
    data object Home : Screen
    data object Live : Screen
    data object Movies : Screen
    data object Series : Screen
    data object MyList : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Detail(val item: Item) : Screen
    data class Category(val kind: Kind, val cat: com.noirplus.desktop.data.Category) : Screen
    data class Player(val req: PlayRequest) : Screen
}

class Nav(start: Screen) {
    val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(s: Screen) { stack.clear(); stack.add(s) }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("sin Nav") }

// ───────────────────────── Piezas ─────────────────────────

/** Logo "Noir" con el "+" plateado en superíndice. */
@Composable
fun Logo(size: Int = 26) {
    Row(verticalAlignment = Alignment.Top) {
        Text("Noir", color = Col.Text, fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = size.sp,
            letterSpacing = (-0.8).sp)
        Text("+", color = Color(0xFFC9CCD3), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = (size * 0.55f).sp,
            modifier = Modifier.padding(start = 1.dp))
    }
}

/** Efecto al pasar el ratón: crece un poco y gana sombra. */
@Composable
fun Modifier.hoverLift(hovered: Boolean, scale: Float = 1.05f): Modifier {
    val s by animateFloatAsState(if (hovered) scale else 1f, label = "lift")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
fun SectionTitle(text: String, logo: String? = null, onSeeAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 32.dp, top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (logo != null) AsyncImage(logo, null, modifier = Modifier.padding(end = 10.dp).size(26.dp).clip(RoundedCornerShape(6.dp)))
        Text(text, color = Col.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onSeeAll != null) {
            Text("Ver todo  ›", color = Col.Sub, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSeeAll() }
                    .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

/** Botón de texto estilo Apple TV: blanco (principal) o cristal. */
@Composable
fun NButton(text: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (primary) (if (hovered) Color(0xFFE6E6E6) else Color.White) else Color.White.copy(alpha = if (hovered) 0.22f else 0.12f))
            .hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }
            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color.Black else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Carátula vertical con título debajo. */
@Composable
fun PosterCard(item: Item, width: Dp = 168.dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Column(Modifier.width(width).hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }
        .pointerHoverIcon(PointerIcon.Hand)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).hoverLift(hovered)
                .shadow(if (hovered) 18.dp else 0.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)).background(Col.Surface2)
                .then(if (hovered) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier),
        ) {
            if (item.image != null) {
                AsyncImage(item.image, item.name, contentScale = if (item.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (item.kind == Kind.LIVE) Modifier.padding(16.dp) else Modifier))
            } else {
                Text(item.name, color = Col.Sub, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(10.dp), maxLines = 4)
            }
            item.rating?.toFloatOrNull()?.takeIf { it > 0 }?.let { r ->
                Text("★ " + String.format("%.1f", if (r > 5) r / 2 else r), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Text(item.name, color = if (hovered) Col.Text else Col.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp))
    }
}

/** Tarjeta horizontal con barra de progreso (Continuar viendo). */
@Composable
fun WideCard(title: String, image: String?, progress: Float, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Column(Modifier.width(300.dp).hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }
        .pointerHoverIcon(PointerIcon.Hand)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).hoverLift(hovered, 1.04f).clip(RoundedCornerShape(8.dp)).background(Col.Surface2)
            .then(if (hovered) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)) {
            if (image != null) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp).height(4.dp).clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
            }
        }
        Text(title, color = if (hovered) Col.Text else Col.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp))
    }
}

/** Fila horizontal con flechas para desplazarse con el ratón. */
@Composable
fun <T> ScrollRow(items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(Modifier.fillMaxWidth().hoverable(src)) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items, key = { i, it -> "$i:${key(it)}" }) { _, it -> content(it) }
        }
        if (hovered && state.canScrollBackward) {
            RowArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) {
                scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 5).coerceAtLeast(0)) }
            }
        }
        if (hovered && state.canScrollForward) {
            RowArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) {
                scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + 5) }
            }
        }
    }
}

@Composable
private fun RowArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(horizontal = 6.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
            .clickable { onClick() }.pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Col.Sub, fontSize = 15.sp)
    }
}

/** Fondo grande con degradados para cabeceras y fichas. */
@Composable
fun Backdrop(url: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Col.Bg, 0.45f to Col.Bg.copy(alpha = 0.75f), 1f to Col.Bg.copy(alpha = 0.1f))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Col.Bg)))
    }
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
