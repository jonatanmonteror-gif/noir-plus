package com.noirplus.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplus.desktop.BuildInfo
import com.noirplus.desktop.data.Repo
import com.noirplus.desktop.data.Store
import com.noirplus.desktop.data.Updater
import kotlinx.coroutines.launch

/** Teclado global: el último manejador registrado (p. ej. el reproductor) recibe las teclas primero. */
object KeyBus {
    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()
    fun dispatch(e: KeyEvent): Boolean {
        for (h in handlers.reversed()) if (h(e)) return true
        return false
    }
    fun add(h: (KeyEvent) -> Boolean) { handlers.add(h) }
    fun remove(h: (KeyEvent) -> Boolean) { handlers.remove(h) }
}

@Composable
fun KeyHandler(handler: (KeyEvent) -> Boolean) {
    val h by rememberUpdatedState(handler)
    DisposableEffect(Unit) {
        val f: (KeyEvent) -> Boolean = { h(it) }
        KeyBus.add(f)
        onDispose { KeyBus.remove(f) }
    }
}

@Composable
fun App() {
    val changes = Store.changes.intValue
    val hasAccount = remember(changes) { Store.hasAccount }
    Box(Modifier.fillMaxSize().background(Col.Bg)) {
        if (!hasAccount) SetupScreen() else Shell()
    }
}

@Composable
private fun Shell() {
    val nav = remember { Nav(Screen.Home) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    LaunchedEffect(Unit) { update = runCatching { Updater.check() }.getOrNull() }

    // Esc / Retroceso = volver atrás
    KeyHandler { e ->
        if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || e.key == Key.Backspace) && nav.current !is Screen.Player) {
            if (nav.stack.size > 1) { nav.pop(); true } else false
        } else false
    }

    CompositionLocalProvider(LocalNav provides nav) {
        val cur = nav.current
        if (cur is Screen.Player) {
            PlayerScreen(cur.req)
            return@CompositionLocalProvider
        }
        Row(Modifier.fillMaxSize()) {
            Sidebar(nav)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = cur,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen()
                        Screen.Live -> LiveScreen()
                        Screen.Movies -> BrowseScreen(com.noirplus.desktop.data.Kind.MOVIE)
                        Screen.Series -> BrowseScreen(com.noirplus.desktop.data.Kind.SERIES)
                        Screen.MyList -> MyListScreen()
                        Screen.Search -> SearchScreen()
                        Screen.Settings -> SettingsScreen()
                        is Screen.Detail -> DetailScreen(s.item)
                        is Screen.Category -> CategoryScreen(s.kind, s.cat)
                        is Screen.Player -> {}
                    }
                }
                update?.let { r ->
                    UpdateBanner(r, Modifier.align(Alignment.BottomEnd).padding(24.dp)) { update = null }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(nav: Nav) {
    val root = nav.stack.first()
    Column(
        Modifier.width(230.dp).fillMaxHeight().background(Color(0xFF070708)).padding(horizontal = 16.dp, vertical = 26.dp),
    ) {
        Box(Modifier.padding(start = 10.dp, bottom = 30.dp)) { Logo(30) }
        NavItem(Icons.Outlined.Home, Icons.Filled.Home, "Inicio", root == Screen.Home) { nav.tab(Screen.Home) }
        NavItem(Icons.Outlined.LiveTv, Icons.Filled.LiveTv, "Directo", root == Screen.Live) { nav.tab(Screen.Live) }
        NavItem(Icons.Outlined.Movie, Icons.Filled.Movie, "Películas", root == Screen.Movies) { nav.tab(Screen.Movies) }
        NavItem(Icons.Outlined.Tv, Icons.Filled.Tv, "Series", root == Screen.Series) { nav.tab(Screen.Series) }
        NavItem(Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark, "Mi lista", root == Screen.MyList) { nav.tab(Screen.MyList) }
        NavItem(Icons.Outlined.Search, Icons.Filled.Search, "Buscar", root == Screen.Search) { nav.tab(Screen.Search) }
        Spacer(Modifier.weight(1f))
        NavItem(Icons.Outlined.Settings, Icons.Filled.Settings, "Ajustes", root == Screen.Settings) { nav.tab(Screen.Settings) }
        Text("v${BuildInfo.VERSION}", color = Col.Sub.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
    }
}

@Composable
private fun NavItem(icon: ImageVector, iconSel: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(when { selected -> Color.White; hovered -> Color.White.copy(alpha = 0.08f); else -> Color.Transparent })
            .hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }
            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selected) iconSel else icon, null, tint = if (selected) Color.Black else Col.Text, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (selected) Color.Black else Col.Text, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun UpdateBanner(r: Updater.Release, modifier: Modifier, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier.width(340.dp).clip(RoundedCornerShape(14.dp)).background(Col.Surface2).padding(18.dp),
    ) {
        Text("Nueva versión ${r.version}", color = Col.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(error ?: "Se descarga, se comprueba y se instala encima. La app se cerrará un momento.", color = if (error != null) Col.Error else Col.Sub,
            fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        val p = progress
        if (p != null) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Color.White))
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton("Actualizar", primary = true) {
                progress = 0f
                scope.launchCatching({ error = it; progress = null }) { Updater.install(r) { progress = it } }
            }
            NButton("Más tarde") { onClose() }
        }
    }
}

/** Lanza una corrutina y convierte cualquier error en un texto para el usuario. */
fun kotlinx.coroutines.CoroutineScope.launchCatching(onError: (String) -> Unit, block: suspend () -> Unit) =
    launch {
        try { block() } catch (t: Throwable) { onError(com.noirplus.desktop.data.friendlyError(t)) }
    }

/** Para que la lista se recargue cuando se pide desde Ajustes. */
fun reloadLists() = Repo.refresh()
