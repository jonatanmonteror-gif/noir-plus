package com.nexotv.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.nexotv.app.data.Category
import com.nexotv.app.data.Details
import com.nexotv.app.data.Item
import com.nexotv.app.data.Kind
import com.nexotv.app.data.Repo
import kotlinx.coroutines.delay

object Col {
    val Bg = Color(0xFF0A0A0F)
    val Surface = Color(0xFF16161E)
    val Surface2 = Color(0xFF23232E)
    val Accent = Color(0xFF8B5CF6)
    val Accent2 = Color(0xFFEC4899)
    val Text = Color(0xFFF4F4F5)
    val Sub = Color(0xFFA1A1AA)
    val Error = Color(0xFFF87171)
}

/** Recuerda qué tarjeta tenía el foco para devolverlo al volver atrás. */
class FocusMemory {
    var last: String? = null
    var restore: Boolean = false
}

val LocalFocusMemory = staticCompositionLocalOf { FocusMemory() }

/** Caja enfocable con el mando: escala, borde y clic con OK (y pulsación larga opcional). */
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    key: String? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    focusScale: Float = 1.08f,
    border: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "scale")
    val mem = LocalFocusMemory.current
    val ownRequester = remember { FocusRequester() }
    val fr = focusRequester ?: ownRequester
    val interaction = remember { MutableInteractionSource() }

    LaunchedEffect(key) {
        if (key != null && mem.restore && mem.last == key) {
            delay(60)
            if (runCatching { fr.requestFocus() }.isSuccess) mem.restore = false
        }
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
            .then(if (focused && border) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    ) {
        content(focused)
    }
}

@Composable
fun Logo(size: Int = 24) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("NEXO", color = Col.Accent, fontWeight = FontWeight.Black, fontSize = size.sp, letterSpacing = 2.sp)
        Text(" TV", color = Col.Text, fontWeight = FontWeight.Light, fontSize = size.sp)
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
    Focusable(modifier, key = key, shape = RoundedCornerShape(50), focusScale = 1.05f, border = false,
        focusRequester = focusRequester, onClick = onClick) { focused ->
        Text(
            text,
            Modifier
                .background(
                    when {
                        focused -> Color.White
                        primary -> Col.Accent
                        else -> Col.Surface2
                    }
                )
                .padding(horizontal = 22.dp, vertical = 10.dp),
            color = if (focused) Color.Black else Col.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Col.Text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 48.dp, top = 10.dp, end = 48.dp),
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Col.Accent)
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Col.Sub, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun RowPlaceholder() {
    Box(Modifier.fillMaxWidth().height(210.dp).padding(start = 48.dp), contentAlignment = Alignment.CenterStart) {
        Text("Cargando…", color = Col.Sub, fontSize = 13.sp)
    }
}

@Composable
fun RatingBadge(rating: String?, modifier: Modifier = Modifier) {
    val r = rating?.replace(',', '.')?.toFloatOrNull()?.takeIf { it > 0f } ?: return
    Text(
        "★ " + String.format("%.1f", r),
        modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color(0xFFFACC15),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** Carátula vertical tipo Netflix. */
@Composable
fun PosterCard(item: Item, memKey: String, width: Dp = 112.dp, onFocus: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(width)) {
        Focusable(Modifier.fillMaxWidth().aspectRatio(2f / 3f), key = memKey, onFocus = onFocus, onClick = onClick) {
            Box(Modifier.fillMaxSize().background(Col.Surface2))
            Text(
                item.name,
                Modifier.align(Alignment.Center).padding(8.dp),
                color = Col.Sub, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 4,
            )
            if (item.image != null) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.name,
                    contentScale = if (item.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (item.kind == Kind.LIVE) Modifier.padding(12.dp) else Modifier),
                )
            }
            RatingBadge(item.rating, Modifier.align(Alignment.TopStart))
        }
        Text(
            item.name,
            color = Col.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
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
    width: Dp = 210.dp,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    Column(Modifier.width(width)) {
        Focusable(Modifier.fillMaxWidth().aspectRatio(16f / 9f), key = memKey, focusRequester = focusRequester,
            onFocus = onFocus, onClick = onClick) {
            Box(Modifier.fillMaxSize().background(Col.Surface2))
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(Col.Accent))
            }
        }
        Text(
            title,
            color = Col.Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(rowId: String, items: List<Item>, onFocus: (Item) -> Unit, onClick: (Item) -> Unit) {
    LazyRow(
        Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(items, key = { i, item -> "$i:${item.key}" }) { _, item ->
            PosterCard(item, memKey = "$rowId/${item.key}", onFocus = { onFocus(item) }, onClick = { onClick(item) })
        }
    }
}

/** Una fila por categoría ("carpeta") del proveedor, cargada bajo demanda. */
@Composable
fun CategoryRow(kind: Kind, cat: Category, titlePrefix: String = "", onFocus: (Item) -> Unit) {
    val src = Repo.source ?: return
    val nav = LocalNav.current
    val items by produceState<List<Item>?>(null, kind, cat.id) {
        value = runCatching { src.items(kind, cat.id) }.getOrDefault(emptyList())
    }
    val list = items
    if (list != null && list.isEmpty()) return
    Column {
        SectionTitle(titlePrefix + cat.name)
        if (list == null) RowPlaceholder()
        else PosterRow("${kind.name}-${cat.id}", list, onFocus) { nav.push(Screen.Detail(it)) }
    }
}

/** Cabecera grande con la imagen y la info del elemento enfocado. */
@Composable
fun HeroArea(item: Item?, emptyTitle: String, emptySubtitle: String) {
    val src = Repo.source
    val details by produceState<Details?>(null, item?.key) {
        value = null
        val it0 = item ?: return@produceState
        if (it0.kind == Kind.LIVE || src == null) return@produceState
        delay(500)
        value = runCatching {
            if (it0.kind == Kind.MOVIE) src.movieDetails(it0) else src.seriesDetails(it0)?.details
        }.getOrNull()
    }
    Box(Modifier.fillMaxWidth().height(196.dp)) {
        val img = details?.backdrop ?: item?.image
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.62f),
            )
        } else {
            Box(
                Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.62f)
                    .background(Brush.linearGradient(listOf(Col.Accent.copy(alpha = 0.45f), Col.Accent2.copy(alpha = 0.25f))))
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to Col.Bg, 0.4f to Col.Bg, 0.7f to Col.Bg.copy(alpha = 0.45f), 1f to Color.Transparent)
            )
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Col.Bg)))
        Column(Modifier.padding(start = 48.dp, top = 8.dp, end = 24.dp).fillMaxWidth(0.55f)) {
            if (item == null) {
                Text(emptyTitle, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Col.Text)
                Text(emptySubtitle, fontSize = 14.sp, color = Col.Sub, modifier = Modifier.padding(top = 6.dp))
            } else {
                Text(item.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Col.Text, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 32.sp)
                val rating = (details?.rating ?: item.rating)?.replace(',', '.')?.toFloatOrNull()?.takeIf { it > 0f }
                val meta = listOfNotNull(
                    details?.year ?: item.year,
                    rating?.let { "★ " + String.format("%.1f", it) },
                    details?.genre,
                    details?.duration,
                ).joinToString("   ·   ")
                if (meta.isNotEmpty()) {
                    Text(meta, color = Col.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp))
                }
                val plot = details?.plot ?: item.plot
                if (plot != null) {
                    Text(plot, color = Col.Text.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 3, lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** Fondo a pantalla completa con degradado para las fichas. */
@Composable
fun Backdrop(url: String?) {
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.6f,
                modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Col.Bg, 0.5f to Col.Bg.copy(alpha = 0.75f), 1f to Color.Transparent)
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Col.Bg)))
    }
}
