package com.noirplus.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Details
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay
import java.util.Locale

/** Paleta sobria tipo Apple TV: negros, grises y blanco. */
object Col {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val Surface2 = Color(0xFF2C2C2E)
    val Accent = Color(0xFFFFFFFF)
    val Accent2 = Color(0xFF8E8E93)
    val Text = Color(0xFFFFFFFF)
    val Sub = Color(0xFF8E8E93)
    val Error = Color(0xFFFF6961)
    val Glass = Color(0x33FFFFFF)
    val GlassStrong = Color(0x55FFFFFF)
    val Star = Color(0xFFFFD60A)
    val Hairline = Color(0x14FFFFFF)
}

private val focusSpring = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

/** Recuerda qué tarjeta tenía el foco para devolverlo al volver atrás. */
class FocusMemory {
    var last: String? = null
    var restore: Boolean = false
}

val LocalFocusMemory = staticCompositionLocalOf { FocusMemory() }

/**
 * Caja interactiva para mando y táctil:
 * - TV: escala con muelle suave, sombra y OK / OK mantenido.
 * - Móvil: leve hundimiento al pulsar y pulsación larga.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    key: String? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusScale: Float = 1.1f,
    border: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    val tv = LocalDims.current.tv
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = when {
        focused && tv -> focusScale
        pressed && !tv -> 0.96f
        else -> 1f
    }
    val scale by animateFloatAsState(target, focusSpring, label = "scale")
    val elevation by animateDpAsState(if (focused && tv && focusScale > 1f) 22.dp else 0.dp, tween(220), label = "elev")
    val mem = LocalFocusMemory.current
    val ownRequester = remember { FocusRequester() }
    val fr = focusRequester ?: ownRequester

    LaunchedEffect(key) {
        if (key != null && mem.restore && mem.last == key) {
            delay(60)
            if (runCatching { fr.requestFocus() }.isSuccess) mem.restore = false
        }
    }

    val clickMod = if (!tv && onLongClick != null) {
        Modifier.combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
    }

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(fr)
            .onFocusChanged { st ->
                focused = st.isFocused
                if (st.isFocused) {
                    if (key != null) mem.last = key
                    onFocus()
                }
            }
            .onPreviewKeyEvent { ev ->
                val cb = onLongClick ?: return@onPreviewKeyEvent false
                val code = ev.nativeKeyEvent.keyCode
                if (code != android.view.KeyEvent.KEYCODE_DPAD_CENTER && code != android.view.KeyEvent.KEYCODE_ENTER) {
                    return@onPreviewKeyEvent false
                }
                when {
                    ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount > 0 -> {
                        if (!longFired) {
                            longFired = true
                            cb()
                        }
                        true
                    }
                    ev.type == KeyEventType.KeyUp && longFired -> {
                        longFired = false
                        true
                    }
                    else -> false
                }
            }
            .shadow(elevation, shape, clip = false, ambientColor = Color.White.copy(alpha = 0.25f), spotColor = Color.White.copy(alpha = 0.35f))
            .then(if (focused && border) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clip(shape)
            .then(clickMod),
    ) {
        content(focused)
    }
}

@Composable
fun Logo(size: Int = 24) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Noir", color = Col.Text, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = size.sp, letterSpacing = (-0.5).sp)
        Text("+", color = Col.Text, fontFamily = InterDisplay, fontWeight = FontWeight.Light, fontSize = size.sp)
    }
}

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    key: String? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Focusable(modifier, key = key, shape = RoundedCornerShape(50), focusScale = 1.06f, focusRequester = focusRequester,
        onClick = onClick) { focused ->
        Text(
            text,
            Modifier
                .background(
                    when {
                        focused -> Color.White
                        primary -> Col.GlassStrong
                        else -> Col.Glass
                    }
                )
                .padding(horizontal = 24.dp, vertical = 11.dp),
            color = if (focused) Color.Black else Col.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Col.Text,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = edge, top = 10.dp, end = edge),
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Col.Text, strokeWidth = 2.dp)
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(edge), contentAlignment = Alignment.Center) {
        Text(text, color = Col.Sub, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun RowPlaceholder() {
    val d = LocalDims.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = d.edge, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(d.gap),
    ) {
        repeat(8) {
            Box(Modifier.width(d.poster).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Col.Surface))
        }
    }
}

fun parseRating(r: String?): Float? = r?.replace(',', '.')?.toFloatOrNull()?.takeIf { it > 0f && it <= 10f }

fun fmtRating(r: Float): String = String.format(Locale("es", "ES"), "%.1f", r)

/** Valoración en cápsula de cristal: estrella amarilla + nota. */
@Composable
fun RatingPill(rating: String?, modifier: Modifier = Modifier, small: Boolean = true) {
    val r = parseRating(rating) ?: return
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = if (small) 7.dp else 9.dp, vertical = if (small) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = Col.Star, fontSize = if (small) 9.sp else 11.sp)
        Spacer(Modifier.width(4.dp))
        Text(fmtRating(r), color = Color.White, fontSize = if (small) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Línea de datos: año · valoración · género · duración. */
@Composable
fun MetaLine(year: String?, rating: String?, genre: String?, duration: String?, modifier: Modifier = Modifier) {
    val parts = listOfNotNull(year, genre, duration)
    if (parts.isEmpty() && parseRating(rating) == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RatingPill(rating, small = false)
        if (parts.isNotEmpty()) {
            Text(parts.joinToString("  ·  "), color = Col.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Carátula vertical: esquinas redondeadas, borde finísimo y valoración en cápsula. */
@Composable
fun PosterCard(
    item: Item,
    memKey: String,
    width: Dp = LocalDims.current.poster,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Column(Modifier.width(width)) {
        Focusable(Modifier.fillMaxWidth().aspectRatio(2f / 3f), key = memKey, onFocus = onFocus, onClick = onClick) { focused ->
            Box(Modifier.fillMaxSize().background(Col.Surface))
            Text(
                item.name,
                Modifier.align(Alignment.Center).padding(10.dp),
                color = Col.Sub, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 4,
            )
            if (item.image != null) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.name,
                    contentScale = if (item.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (item.kind == Kind.LIVE) Modifier.padding(14.dp) else Modifier),
                )
            }
            Box(Modifier.matchParentSize().border(1.dp, if (focused) Color.Transparent else Col.Hairline, RoundedCornerShape(12.dp)))
            RatingPill(item.rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            item.name,
            color = Col.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

/** Tarjeta horizontal (continuar viendo, episodios). */
@Composable
fun WideCard(
    title: String,
    image: String?,
    progress: Float?,
    memKey: String,
    width: Dp = LocalDims.current.wide,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    Column(Modifier.width(width)) {
        Focusable(Modifier.fillMaxWidth().aspectRatio(16f / 9f), key = memKey, focusRequester = focusRequester,
            onFocus = onFocus, onClick = onClick) { focused ->
            Box(Modifier.fillMaxSize().background(Col.Surface))
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (progress != null && progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)
                        .height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(50)).background(Color.White))
                }
            }
            Box(Modifier.matchParentSize().border(1.dp, if (focused) Color.Transparent else Col.Hairline, RoundedCornerShape(12.dp)))
        }
        Text(
            title,
            color = Col.Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(rowId: String, items: List<Item>, onFocus: (Item) -> Unit, onClick: (Item) -> Unit) {
    val d = LocalDims.current
    LazyRow(
        Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = d.edge, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(d.gap),
    ) {
        itemsIndexed(items, key = { i, item -> "$i:${item.key}" }) { _, item ->
            PosterCard(item, memKey = "$rowId/${item.key}", onFocus = { onFocus(item) }, onClick = { onClick(item) })
        }
    }
}

/** Una fila por categoría ("carpeta") del proveedor, cargada bajo demanda. */
@Composable
fun CategoryRow(
    kind: Kind,
    cat: Category,
    titlePrefix: String = "",
    onLoaded: ((Item) -> Unit)? = null,
    onFocus: (Item) -> Unit,
) {
    val src = Repo.source ?: return
    val nav = LocalNav.current
    val items by produceState<List<Item>?>(null, kind, cat.id) {
        value = runCatching { src.items(kind, cat.id) }.getOrDefault(emptyList())
    }
    val list = items
    LaunchedEffect(list) {
        val first = list?.firstOrNull()
        if (first != null) onLoaded?.invoke(first)
    }
    if (list != null && list.isEmpty()) return
    Column {
        SectionTitle(titlePrefix + cat.name)
        if (list == null) RowPlaceholder()
        else PosterRow("${kind.name}-${cat.id}", list, onFocus) { nav.push(Screen.Detail(it)) }
    }
}

/**
 * Cabecera grande con la imagen e info del elemento enfocado.
 * TV/tablet: imagen a la derecha con degradado lateral. Móvil: imagen a lo ancho con el texto encima.
 */
@Composable
fun HeroArea(item: Item?, emptyTitle: String, emptySubtitle: String) {
    val d = LocalDims.current
    val src = Repo.source
    val nav = LocalNav.current
    val details by produceState<Details?>(null, item?.key) {
        value = null
        val it0 = item ?: return@produceState
        if (it0.kind == Kind.LIVE || src == null) return@produceState
        delay(450)
        value = runCatching {
            if (it0.kind == Kind.MOVIE) src.movieDetails(it0) else src.seriesDetails(it0)?.details
        }.getOrNull()
    }
    val img = details?.backdrop ?: item?.image
    val clickable = !d.tv && item != null && item.kind != Kind.LIVE

    Box(
        Modifier.fillMaxWidth().height(d.heroH)
            .then(if (clickable) Modifier.clickable { item?.let { nav.push(Screen.Detail(it)) } } else Modifier)
    ) {
        Crossfade(
            targetState = img,
            animationSpec = tween(450),
            modifier = if (d.compact) Modifier.fillMaxSize() else Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.62f),
            label = "heroImg",
        ) { url ->
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Col.Surface2, Col.Bg))))
            }
        }
        if (!d.compact) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(0f to Col.Bg, 0.4f to Col.Bg, 0.7f to Col.Bg.copy(alpha = 0.45f), 1f to Color.Transparent)
                )
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient((if (d.compact) 0.25f else 0.55f) to Color.Transparent, 1f to Col.Bg)
            )
        )
        AnimatedContent(
            targetState = item,
            contentKey = { it?.key },
            transitionSpec = { fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(140)) },
            modifier = if (d.compact) {
                Modifier.align(Alignment.BottomStart).padding(horizontal = d.edge, vertical = 14.dp)
            } else {
                Modifier.padding(start = d.edge, top = 8.dp, end = 24.dp).fillMaxWidth(0.55f)
            },
            label = "heroText",
        ) { it0 ->
            Column {
                if (it0 == null) {
                    Text(emptyTitle, fontFamily = InterDisplay, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Col.Text,
                        letterSpacing = (-0.8).sp)
                    Text(emptySubtitle, fontSize = 14.sp, color = Col.Sub, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(it0.name, fontFamily = InterDisplay, fontSize = if (d.compact) 26.sp else 32.sp, fontWeight = FontWeight.Bold,
                        color = Col.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = if (d.compact) 30.sp else 36.sp,
                        letterSpacing = (-0.8).sp)
                    MetaLine(
                        year = details?.year ?: it0.year,
                        rating = details?.rating ?: it0.rating,
                        genre = details?.genre,
                        duration = if (d.compact) null else details?.duration,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val plot = details?.plot ?: it0.plot
                    if (plot != null) {
                        Text(plot, color = Col.Text.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = if (d.compact) 2 else 3,
                            lineHeight = 18.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/** Fondo a pantalla completa con degradado para las fichas. */
@Composable
fun Backdrop(url: String?) {
    val compact = LocalDims.current.compact
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = url, animationSpec = tween(500), label = "backdrop") { u ->
            if (u != null) {
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.6f,
                    modifier = Modifier.fillMaxSize())
            }
        }
        if (!compact) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Col.Bg, 0.5f to Col.Bg.copy(alpha = 0.75f), 1f to Color.Transparent)
                )
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient((if (compact) 0.15f else 0.45f) to Color.Transparent, 1f to Col.Bg)))
    }
}
