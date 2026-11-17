package com.noirplus.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirplus.tv.BuildConfig
import com.noirplus.tv.R
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Playfair Display: la tipografía elegante del saludo. */
val Playfair = FontFamily(
    Font(R.font.playfair_regular, FontWeight.Normal),
    Font(R.font.playfair_semibold, FontWeight.SemiBold),
    Font(R.font.playfair_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.playfair_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

private val ES = Locale("es", "ES")

fun greetingWord(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 6..13 -> "Buenos días"
    in 14..20 -> "Buenas tardes"
    else -> "Buenas noches"
}

fun kindPlural(k: Kind) = when (k) {
    Kind.MOVIE -> "películas"
    Kind.SERIES -> "series"
    Kind.LIVE -> "canales"
}

/** Aparición suave (sube y se ilumina) con retraso escalonado. */
@Composable
private fun Reveal(delayMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 24.dp.toPx()
    }) { content() }
}

@Composable
private fun WelcomeButton(text: String, primary: Boolean, focusRequester: FocusRequester? = null, fill: Boolean = false, onClick: () -> Unit) {
    Focusable(
        if (fill) Modifier.fillMaxWidth() else Modifier,
        shape = RoundedCornerShape(50), focusScale = 1.06f, focusRequester = focusRequester, onClick = onClick,
    ) { focused ->
        Text(
            text,
            Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .background(
                    when {
                        primary || focused -> Color.White
                        else -> Color.White.copy(alpha = 0.14f)
                    }
                )
                .padding(horizontal = 30.dp, vertical = 13.dp),
            color = if (primary || focused) Color.Black else Color.White,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun WelcomeScreen() {
    val prefs = Repo.prefs
    val nav = LocalNav.current
    val d = LocalDims.current
    val scope = rememberCoroutineScope()
    val enterFr = remember { FocusRequester() }
    val name = prefs.name.ifBlank { null }
    val hello = remember { greetingWord() }
    val date = remember { SimpleDateFormat("EEEE, d 'de' MMMM", ES).format(Date()).replaceFirstChar { it.titlecase(ES) } }

    var backdrops by remember { mutableStateOf<List<Item>>(emptyList()) }
    var bgIndex by remember { mutableStateOf(0) }
    var news by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    val steps = remember { mutableStateListOf<Repo.RefreshStep>() }

    fun enter() {
        if (Repo.isStale()) Repo.invalidate(null)
        prefs.lastVisit = System.currentTimeMillis()
        nav.reset(Screen.Main)
    }

    // Fondo: lo recién llegado, pasando despacio. A la vez se precargan las listas y se cuentan las novedades.
    LaunchedEffect(Unit) {
        delay(150)
        if (d.tv) runCatching { enterFr.requestFocus() }
        val m = recentOf(Kind.MOVIE, 6)
        val s = recentOf(Kind.SERIES, 4)
        backdrops = (m + s).filter { it.image != null }.shuffled().take(8)
        val src = Repo.source ?: return@LaunchedEffect
        val last = prefs.lastVisit / 1000
        val nf = NumberFormat.getIntegerInstance(ES)
        news = runCatching {
            val movies = src.all(Kind.MOVIE)
            val series = src.all(Kind.SERIES)
            if (last <= 0) {
                "Tu catálogo: ${nf.format(movies.size)} películas · ${nf.format(series.size)} series"
            } else {
                val n = movies.count { it.added > last } + series.count { it.added > last }
                when (n) {
                    0 -> "Todo al día desde tu última visita"
                    1 -> "1 novedad desde tu última visita"
                    else -> "${nf.format(n)} novedades desde tu última visita"
                }
            }
        }.getOrNull()
    }
    LaunchedEffect(backdrops) {
        if (backdrops.size < 2) return@LaunchedEffect
        while (true) {
            delay(8000)
            bgIndex = (bgIndex + 1) % backdrops.size
        }
    }

    fun update() {
        if (updating) return
        updating = true
        steps.clear()
        scope.launch {
            Repo.refresh { st ->
                val i = steps.indexOfFirst { it.kind == st.kind }
                if (i >= 0) steps[i] = st else steps.add(st)
            }
            delay(900)
            prefs.lastVisit = System.currentTimeMillis()
            nav.reset(Screen.Main)
        }
    }

    // Movimiento lento del fondo (efecto "Ken Burns"), muy ligero
    val drift = rememberInfiniteTransition(label = "drift")
    val zoom by drift.animateFloat(1.0f, 1.08f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")

    Box(Modifier.fillMaxSize().background(Col.Bg)) {
        Crossfade(targetState = backdrops.getOrNull(bgIndex)?.image, animationSpec = tween(1400), label = "bg") { url ->
            if (url != null) {
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.5f,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
                )
            }
        }
        // Velo: lateral en TV, de abajo arriba en móvil
        Box(
            Modifier.fillMaxSize().background(
                if (d.compact) Brush.verticalGradient(0f to Col.Bg.copy(alpha = 0.3f), 0.45f to Col.Bg.copy(alpha = 0.55f), 1f to Col.Bg)
                else Brush.horizontalGradient(0f to Col.Bg, 0.45f to Col.Bg.copy(alpha = 0.85f), 1f to Col.Bg.copy(alpha = 0.25f))
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Col.Bg.copy(alpha = 0.9f))))

        val text: @Composable () -> Unit = {
            val align = if (d.compact) TextAlign.Center else TextAlign.Start
            Reveal(100) {
                Text(date.uppercase(ES), color = Col.Sub, fontSize = 11.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Medium,
                    textAlign = align, modifier = Modifier.fillMaxWidth())
            }
            Reveal(250) {
                Text("$hello,", fontFamily = Playfair, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.82f), fontSize = if (d.compact) 30.sp else 38.sp, textAlign = align,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
            }
            Reveal(400) {
                Text(name ?: "bienvenido", fontFamily = Playfair, fontWeight = FontWeight.SemiBold, color = Color.White,
                    fontSize = if (d.compact) 50.sp else 64.sp, lineHeight = if (d.compact) 54.sp else 68.sp, textAlign = align,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            }
            Reveal(600) {
                Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = if (d.compact) Alignment.Center else Alignment.CenterStart) {
                    Box(Modifier.width(36.dp).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
                }
            }
            Crossfade(targetState = news, label = "news") { n ->
                Text(n ?: " ", color = Col.Sub, fontSize = 14.sp, textAlign = align, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
            }
        }

        val actions: @Composable () -> Unit = {
            if (!updating) {
                Reveal(750) {
                    if (d.compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WelcomeButton("Entrar", primary = true, focusRequester = enterFr, fill = true) { enter() }
                            WelcomeButton("Actualizar listas", primary = false, fill = true) { update() }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WelcomeButton("Entrar", primary = true, focusRequester = enterFr) { enter() }
                            WelcomeButton("Actualizar listas", primary = false) { update() }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Actualizando tus listas…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    val nf = NumberFormat.getIntegerInstance(ES)
                    Kind.entries.forEach { k ->
                        val st = steps.firstOrNull { it.kind == k }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                when {
                                    st == null -> Box(Modifier.size(5.dp).background(Col.Sub, RoundedCornerShape(50)))
                                    st.count == null && st.error == null -> CircularProgressIndicator(color = Color.White, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
                                    st.error != null -> Text("!", color = Col.Error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    else -> Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                kindPlural(k).replaceFirstChar { it.titlecase(ES) } + when {
                                    st?.count != null -> "  ·  ${nf.format(st.count)}"
                                    st?.error != null -> "  ·  ${st.error}"
                                    else -> ""
                                },
                                color = if (st == null) Col.Sub else Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        if (d.compact) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                text()
                Spacer(Modifier.height(30.dp))
                actions()
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 28.dp)) { Logo(22) }
        } else {
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = d.edge * 1.6f).widthIn(max = 560.dp),
            ) {
                text()
                Spacer(Modifier.height(34.dp))
                actions()
            }
            Box(Modifier.align(Alignment.TopStart).padding(start = d.edge * 1.6f, top = 30.dp)) { Logo(22) }
            Text("v${BuildConfig.VERSION_NAME}", color = Col.Sub.copy(alpha = 0.6f), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(d.edge))
        }
    }
}
