package com.noirplus.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.tv.data.Updater
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Setup : Screen
    data object Main : Screen
    data object Folders : Screen
    data class Detail(val item: Item) : Screen
    data class Player(val req: PlayRequest) : Screen
    data class Category(val kind: Kind, val cat: com.noirplus.tv.data.Category, val search: Boolean = false) : Screen
}

class Nav(initial: Screen) {
    val stack = mutableStateListOf(initial)
    val top: Screen get() = stack.last()

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun reset(s: Screen) {
        stack.clear()
        stack.add(s)
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav no disponible") }

private fun keyOf(s: Screen): String = when (s) {
    Screen.Setup -> "setup"
    Screen.Main -> "main"
    Screen.Folders -> "folders"
    is Screen.Detail -> "detail:" + s.item.key
    is Screen.Player -> "player:" + s.req.key
    is Screen.Category -> "cat:" + s.kind.name + ":" + s.cat.id
}

@Composable
fun App() {
    val nav = remember { Nav(if (Repo.prefs.hasAccount()) Screen.Main else Screen.Setup) }
    val mem = remember { FocusMemory() }
    val holder = rememberSaveableStateHolder()
    val ctx = LocalContext.current
    val isTv = remember { isTvDevice(ctx) }
    val conf = LocalConfiguration.current
    val dims = when {
        isTv -> TvDims
        conf.screenWidthDp < 600 -> PhoneDims
        else -> TabletDims
    }
    val base = LocalDensity.current
    val density = if (isTv) Density(base.density * TV_SCALE, base.fontScale) else base

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Col.Accent,
            background = Col.Bg,
            surface = Col.Surface,
            onSurface = Col.Text,
            onBackground = Col.Text,
        ),
        typography = interTypography(),
    ) {
        CompositionLocalProvider(
            LocalNav provides nav,
            LocalFocusMemory provides mem,
            LocalDims provides dims,
            LocalDensity provides density,
        ) {
            val top = nav.top
            BackHandler(enabled = nav.stack.size > 1) {
                mem.restore = true
                nav.pop()
            }
            AnimatedContent(
                targetState = top,
                contentKey = { keyOf(it) },
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(140)) },
                modifier = Modifier.fillMaxSize().background(Col.Bg),
                label = "screens",
            ) { screen ->
                Box(Modifier.fillMaxSize().background(Col.Bg)) {
                    holder.SaveableStateProvider(keyOf(screen)) {
                        when (screen) {
                            Screen.Setup -> SetupScreen()
                            Screen.Main -> MainScreen()
                            Screen.Folders -> FoldersScreen()
                            is Screen.Detail -> DetailScreen(screen.item)
                            is Screen.Player -> PlayerScreen(screen.req)
                            is Screen.Category -> CategoryScreen(screen.kind, screen.cat, screen.search)
                        }
                    }
                }
            }
            LaunchedEffect(top) {
                delay(1500)
                mem.restore = false
            }
            if (top == Screen.Main) UpdateNotice()
        }
    }
}

/** Aviso discreto cuando hay una versión nueva (se comprueba una vez por sesión). */
private object UpdateCheck {
    var checked = false
    var release: Updater.Release? = null
}

@Composable
private fun UpdateNotice() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rel by remember { mutableStateOf(UpdateCheck.release) }
    var visible by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!UpdateCheck.checked) {
            UpdateCheck.checked = true
            delay(4000)
            val r = Updater.latest()
            if (r != null && r.versionCode > Updater.currentCode) {
                UpdateCheck.release = r
                rel = r
            }
        }
    }
    val r = rel ?: return
    LaunchedEffect(r) {
        delay(20_000)
        if (status == null) visible = false
    }
    if (!visible) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = if (LocalDims.current.compact) 88.dp else 24.dp)
                .clip(RoundedCornerShape(14.dp)).background(Col.Surface2.copy(alpha = 0.97f)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = 14.dp)) {
                Text("Nueva versión disponible", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(status ?: r.name, color = Col.Sub, fontSize = 12.sp)
            }
            ActionButton("Actualizar", primary = true) {
                if (status?.startsWith("Descargando") == true) return@ActionButton
                status = "Descargando…"
                scope.launch {
                    status = try {
                        val f = Updater.download(ctx, r) { p -> status = "Descargando… ${(p * 100).toInt()} %" }
                        if (Updater.install(ctx, f)) "Abriendo el instalador…" else "Permite «Instalar apps desconocidas» y vuelve a pulsar"
                    } catch (t: Throwable) {
                        t.message ?: "Error al descargar"
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            ActionButton("Luego") { visible = false }
        }
    }
}
