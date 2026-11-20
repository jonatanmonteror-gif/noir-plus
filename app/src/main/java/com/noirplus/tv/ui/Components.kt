package com.noirplus.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

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

private val focusSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

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
    shape: Shape = CardShape,
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
    // En la tele la carátula NO crece al seleccionarla (el cambio de tamaño hacía que la fila diera saltitos):
    // se marca con un borde blanco. Los botones mantienen su cambio de color.
    val target = when {
        pressed && !tv -> 0.96f
        else -> 1f
    }
    val tvRing = tv && focused && focusScale > 1.07f
    // Foco ágil: crecimiento corto y sin resplandor/sombra (la sombra animada se cortaba y frenaba la tele)
    val scale by animateFloatAsState(target, if (tv) tween(120, easing = FastOutSlowInEasing) else focusSpring, label = "scale")
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

            .then(if ((focused && border) || tvRing) Modifier.border(if (tvRing) 3.dp else 2.dp, Color.White, shape) else Modifier)
            .clip(shape)
            .then(clickMod),
    ) {
        content(focused)
    }
}

/**
 * Logo de Noir+ (misma familia que Noir Books): «Noir» en Playfair y «PLUS» en mayúsculas espaciadas.
 * En tamaño grande va en dos líneas con una raya fina; en cabeceras, en una línea.
 */
@Composable
fun Logo(size: Int = 24) {
    val playfair = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.noirplus.tv.R.font.playfair_regular))
    val silver = Color(0xFFAAAAAF)
    if (size >= 34) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Noir", color = Col.Text, fontFamily = playfair, fontSize = (size * 1.2f).sp, lineHeight = (size * 1.3f).sp)
            Box(Modifier.padding(top = (size * 0.12f).dp).width((size * 1.6f).dp).height(1.dp).background(Color(0xFF6E6E73)))
            Text("PLUS", color = silver, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = (size * 0.26f).sp,
                letterSpacing = (size * 0.12f).sp, modifier = Modifier.padding(top = (size * 0.14f).dp))
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Noir", color = Col.Text, fontFamily = playfair, fontSize = (size * 1.25f).sp, lineHeight = (size * 1.3f).sp)
            Text("PLUS", color = silver, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = (size * 0.42f).sp,
                letterSpacing = (size * 0.12f).sp, modifier = Modifier.padding(start = (size * 0.3f).dp, bottom = (size * 0.24f).dp))
        }
    }
}

/** El "+" de Noir+: fino, con puntas redondeadas, degradado plateado, leve brillo y punto de luz central. */
@Composable
fun PlusMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val stroke = w * 0.17f
        val half = w / 2f - stroke / 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = c, radius = w * 0.9f),
            radius = w * 0.9f, center = c,
        )
        val silver = Brush.verticalGradient(listOf(Color.White, Color(0xFF9A9AA0)), startY = c.y - half, endY = c.y + half)
        drawLine(silver, Offset(c.x - half, c.y), Offset(c.x + half, c.y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(silver, Offset(c.x, c.y - half), Offset(c.x, c.y + half), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = stroke * 0.42f, center = c)
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
fun SectionTitle(text: String, onSeeAll: (() -> Unit)? = null, year: String? = null, tag: String? = null, logo: String? = null) {
    val compact = LocalDims.current.compact
    val size = if (compact) 15 else 17
    Row(Modifier.fillMaxWidth().padding(start = edge, top = 10.dp, end = edge - 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        if (logo != null) {
            AsyncImage(logo, null, modifier = Modifier.padding(end = 8.dp).size(if (compact) 22.dp else 24.dp).clip(RoundedCornerShape(5.dp)))
        }
        Text(
            buildAnnotatedString {
                append(text)
                if (year != null) {
                    withStyle(SpanStyle(fontSize = (size - 4).sp, color = Col.Sub, fontWeight = FontWeight.Medium)) { append("  ($year)") }
                }
            },
            color = Col.Text,
            fontSize = size.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (tag != null) {
            Text(
                tag.uppercase(),
                Modifier.padding(start = 8.dp).border(0.8.dp, Col.Sub.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                color = Col.Sub, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
            )
        }
        }
        if (onSeeAll != null) {
            Text(
                "Ver todo",
                color = Col.Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSeeAll() }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Géneros en botoncitos de bordes redondeados. */
@Composable
fun GenreChips(genre: String?, modifier: Modifier = Modifier, max: Int = 4) {
    val list = genre?.split(',', '/', '|', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(max) ?: return
    if (list.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { g ->
            Text(
                g,
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Col.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            )
        }
    }
}

/** 5 estrellas a partir de una nota sobre 10. */
@Composable
fun StarRow(rating: String?, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val r = parseRating(rating) ?: return
    val full = (r / 2f).roundToInt()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            Icon(Icons.Filled.Star, null, tint = if (i < full) Col.Star else Col.Sub.copy(alpha = 0.45f), modifier = Modifier.size(size))
        }
    }
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
            Box(Modifier.width(d.poster).aspectRatio(2f / 3f).clip(CardShape).background(Col.Surface))
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
    fill: Boolean = false,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val menu = LocalItemMenu.current
    val rateable = item.kind != Kind.LIVE
    val prefs = Repo.prefs
    val changes = prefs.changes.intValue
    val mine = remember(changes, item.key) { if (rateable) prefs.rating(item.key)?.stars else null }
    Column(if (fill) Modifier.fillMaxWidth() else Modifier.width(width)) {
        Focusable(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f), key = memKey, onFocus = onFocus,
            onLongClick = { menu(item.menuTarget()) },
            onClick = onClick,
        ) { focused ->
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
            Box(Modifier.matchParentSize().border(1.dp, if (focused) Color.Transparent else Col.Hairline, CardShape))
            RatingPill(item.rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (mine != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ReviewIcon, null, tint = Color.Black, modifier = Modifier.size(10.dp))
                    Text(fmtStars(mine), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp))
                }
            }
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
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(Modifier.width(width)) {
        Focusable(Modifier.fillMaxWidth().aspectRatio(16f / 9f), key = memKey, focusRequester = focusRequester,
            onFocus = onFocus, onLongClick = onLongClick, onClick = onClick) { focused ->
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
            Box(Modifier.matchParentSize().border(1.dp, if (focused) Color.Transparent else Col.Hairline, CardShape))
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
fun PosterRow(rowId: String, items: List<Item>, onFocus: (Item) -> Unit, more: Int = 0, onSeeAll: (() -> Unit)? = null, onClick: (Item) -> Unit) {
    val d = LocalDims.current
    TvHorizontalScroll {
    LazyRow(
        Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = d.edge, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(d.gap),
    ) {
        itemsIndexed(items, key = { i, item -> "$i:${item.key}" }) { _, item ->
            PosterCard(item, memKey = "$rowId/${item.key}", onFocus = { onFocus(item) }, onClick = { onClick(item) })
        }
        if (onSeeAll != null && more > 0) item(key = "more") { SeeAllCard(rowId, more, onSeeAll) }
    }
    }
}

/** Última tarjeta de una fila recortada: lleva a la carpeta completa. */
@Composable
private fun SeeAllCard(rowId: String, more: Int, onClick: () -> Unit) {
    val d = LocalDims.current
    Focusable(Modifier.width(d.poster).aspectRatio(2f / 3f), key = "$rowId/more", onClick = onClick) { focused ->
        Column(
            Modifier.matchParentSize().background(if (focused) Color.White else Col.Surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Ver todo", color = if (focused) Color.Black else Col.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("+$more", color = if (focused) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Una fila por categoría ("carpeta") del proveedor, cargada bajo demanda. */
@Composable
fun CategoryRow(
    kind: Kind,
    cat: Category,
    tag: String? = null,
    onLoaded: ((Item) -> Unit)? = null,
    onFocus: (Item) -> Unit,
) {
    val src = Repo.source ?: return
    val nav = LocalNav.current
    val d = LocalDims.current
    val genreV = if (cat.id.startsWith(com.noirplus.tv.data.Library.GENRE)) com.noirplus.tv.data.Tmdb.genreVersion.intValue else 0
    val items by produceState<List<Item>?>(null, kind, cat.id, Repo.dataVersion.intValue, genreV) {
        value = runCatching { com.noirplus.tv.data.Library.items(kind, cat.id) }.getOrDefault(emptyList())
    }
    val limit = Repo.prefs.rowLimit
    val all = items
    val list = remember(all, limit) { all?.take(limit) }
    val logo by produceState<String?>(null, cat.id) {
        if (cat.id.startsWith(com.noirplus.tv.data.Library.PLAT)) value = com.noirplus.tv.data.Tmdb.logo(cat.id.removePrefix(com.noirplus.tv.data.Library.PLAT))
    }
    LaunchedEffect(list) {
        val first = list?.firstOrNull()
        if (first != null) onLoaded?.invoke(first)
    }
    if (list != null && list.isEmpty()) return
    val pretty = remember(cat.name, kind) {
        if (com.noirplus.tv.data.Library.isVirtual(cat.id)) PrettyName(cat.name, null) else prettyCategory(cat.name, kind)
    }
    Column {
        SectionTitle(
            pretty.main,
            year = pretty.year,
            tag = tag,
            logo = logo,
            onSeeAll = if (!d.tv && !list.isNullOrEmpty()) ({ nav.push(Screen.Category(kind, cat)) }) else null,
        )
        Crossfade(targetState = list, animationSpec = tween(if (d.tv) 0 else 350), label = "row") { l ->
            if (l == null) RowPlaceholder()
            else PosterRow("${kind.name}-${cat.id}", l, onFocus, more = (all?.size ?: 0) - l.size,
                onSeeAll = { nav.push(Screen.Category(kind, cat)) }) { nav.push(Screen.Detail(it)) }
        }
    }
}

/** Primera fila de Películas / Series: acceso a "Todas" con buscador y orden. */
@Composable
fun AllEntryRow(kind: Kind) {
    val nav = LocalNav.current
    val d = LocalDims.current
    val label = if (kind == Kind.MOVIE) "Todas las películas" else "Todas las series"
    Row(Modifier.fillMaxWidth().padding(horizontal = d.edge, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Focusable(Modifier.weight(1f).height(46.dp), key = "all-${kind.name}", shape = RoundedCornerShape(50), focusScale = 1.03f,
            onClick = { nav.push(Screen.Category(kind, Category(ALL_ID, label), search = false)) }) { focused ->
            Row(
                Modifier.fillMaxSize().background(if (focused) Color.White else Col.Surface).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = if (focused) Color.Black else Col.Sub, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(label, color = if (focused) Color.Black else Col.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                Text("Buscar y ordenar  ›", color = if (focused) Color.Black.copy(alpha = 0.6f) else Col.Sub, fontSize = 12.sp)
            }
        }
    }
}

const val ALL_ID = "__all"

/**
 * Cabecera grande con la imagen e info del elemento enfocado.
 * TV/tablet: imagen a la derecha con degradado lateral. Móvil: imagen a lo ancho con el texto encima.
 */
@Composable
fun HeroArea(item0: Item?, emptyTitle: String, emptySubtitle: String) {
    val d = LocalDims.current
    // En la tele la cabecera espera a que te detengas un instante en una carátula:
    // así, al recorrer una fila, no se recarga en cada paso (más fluido y sin tirones).
    var shownItem by remember { mutableStateOf(item0) }
    LaunchedEffect(item0?.key) {
        if (d.tv && shownItem != null) delay(320)
        shownItem = item0
    }
    val item = shownItem
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
            animationSpec = tween(if (d.tv) 180 else 450),
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
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    (if (d.compact) 0.25f else 0.45f) to Color.Transparent,
                    0.8f to Col.Bg.copy(alpha = 0.55f),
                    1f to Col.Bg,
                )
            )
        )
        AnimatedContent(
            targetState = item,
            contentKey = { it?.key },
            transitionSpec = {
                if (d.tv) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(140))
            },
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
                        genre = null,
                        duration = if (d.compact) null else details?.duration,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GenreChips(details?.genre)
                        PlatformBadge(it0, Modifier.padding(start = 8.dp))
                    }
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
        Crossfade(targetState = url, animationSpec = tween(if (LocalDims.current.tv) 150 else 500), label = "backdrop") { u ->
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

/**
 * Desplazamiento de filas en TV: al bajar a una fila, la anterior se queda asomando arriba
 * (un ~15 %), y en horizontal la carátula enfocada queda fija cerca del margen.
 */
@OptIn(ExperimentalFoundationApi::class)
class PeekSpec(private val leadPx: Float) : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    // Muelle sin rebote: al pulsar seguido, el movimiento se encadena con suavidad en vez de ir a golpes
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 340f)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (size >= containerSize) return offset
        return offset - leadPx
    }
}

/**
 * Filas horizontales en la tele: la fila NO se mueve en cada pulsación. El foco recorre las carátulas
 * visibles y la fila solo se desliza (con suavidad) cuando el foco llega cerca del borde,
 * dejando ver un trozo de la siguiente. Así no da saltos.
 */
@OptIn(ExperimentalFoundationApi::class)
class EdgeSpec(private val keylinePx: Float) : BringIntoViewSpec {
    // La carátula elegida se queda siempre en el mismo sitio (a la izquierda) y la fila se desliza
    // una carátula por pulsación, con un movimiento corto y uniforme: sin saltos ni frenazos.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(200, easing = LinearOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - keylinePx
}

/** Envuelve una fila horizontal de la tele con el desplazamiento suave por bordes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvHorizontalScroll(content: @Composable () -> Unit) {
    val d = LocalDims.current
    if (!d.tv) {
        content()
        return
    }
    val px = with(LocalDensity.current) { d.edge.toPx() }
    val spec = remember(px) { EdgeSpec(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvRowScroll(content: @Composable () -> Unit) {
    val d = LocalDims.current
    if (!d.tv) {
        content()
        return
    }
    val px = with(LocalDensity.current) { 80.dp.toPx() }
    val spec = remember(px) { PeekSpec(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content() }
}

/** Portada del móvil: a todo el ancho, carrusel automático de "Recién llegados". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedPager(items: List<Item>, label: String = "Recién llegados") {
    val nav = LocalNav.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.82f).background(Col.Surface))
        return
    }
    val pager = rememberPagerState(pageCount = { items.size })
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pager.settledPage, dragged, items.size) {
        if (dragged || items.size < 2) return@LaunchedEffect
        delay(6000)
        val next = (pager.settledPage + 1) % items.size
        pager.animateScrollToPage(next, animationSpec = tween(1200, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            Box {
                FeaturedPage(items[page], label) { nav.push(Screen.Detail(items[page])) }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(items.size) { i ->
                val sel = i == pager.currentPage
                val w by animateDpAsState(if (sel) 16.dp else 5.dp, tween(250), label = "dot")
                Box(Modifier.height(5.dp).width(w).clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.White.copy(alpha = 0.35f)))
            }
        }
    }
}

@Composable
private fun FeaturedPage(item: Item, label: String, onClick: () -> Unit) {
    val src = Repo.source
    val details by produceState<Details?>(null, item.key) {
        value = runCatching {
            if (item.kind == Kind.MOVIE) src?.movieDetails(item) else src?.seriesDetails(item)?.details
        }.getOrNull()
    }
    Box(Modifier.fillMaxWidth().aspectRatio(0.82f).background(Col.Surface).clickable { onClick() }) {
        Crossfade(targetState = details?.backdrop ?: item.image, animationSpec = tween(400), label = "feat") { url ->
            AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.55f to Color.Transparent, 1f to Col.Bg)
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label.uppercase(), color = Col.Sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
            Text(item.name, fontFamily = InterDisplay, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 31.sp,
                letterSpacing = (-0.7).sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingPill(details?.rating ?: item.rating)
                (details?.year ?: item.year)?.let { Text(it, color = Col.Sub, fontSize = 12.sp) }
            }
            GenreChips(details?.genre, Modifier.padding(top = 10.dp), max = 3)
            PlatformBadge(item, Modifier.padding(top = 10.dp))
        }
    }
}

/** Pastilla "Disponible en Netflix / Disney+ …": con TMDB son datos reales de España; si no, se deduce de las carpetas. */
@Composable
fun PlatformBadge(item: Item?, modifier: Modifier = Modifier) {
    val list by produceState<List<com.noirplus.tv.data.Tmdb.Provider>>(emptyList(), item?.key, Repo.dataVersion.intValue, Repo.prefs.tmdbKey) {
        value = item?.let { com.noirplus.tv.data.Platforms.providers(it) } ?: emptyList()
    }
    androidx.compose.animation.AnimatedVisibility(list.isNotEmpty(), modifier = modifier, enter = androidx.compose.animation.fadeIn(tween(300))) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
                .border(0.8.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            list.take(3).forEach { p ->
                if (p.logo != null) {
                    AsyncImage(p.logo, p.name, modifier = Modifier.padding(end = 4.dp).size(18.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
            Text(if (list.first().logo == null) "  Disponible en " else " ", color = Col.Sub, fontSize = 11.sp)
            Text(list.first().name + if (list.size > 1) "  +${list.size - 1}" else "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
