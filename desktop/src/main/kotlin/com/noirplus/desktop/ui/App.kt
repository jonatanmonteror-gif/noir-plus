package com.noirplus.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
        CrashNotice()
        WhatsNewAfterUpdate()
        NotesHost.Render()
    }
}

@Composable
private fun Shell() {
    val nav = remember { Nav(Screen.Home) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    LaunchedEffect(Unit) { update = runCatching { Updater.check() }.getOrNull() }
    // Sincronización: al abrir y cada 3 minutos mientras Noir+ está abierto
    LaunchedEffect(Unit) {
        while (true) {
            com.noirplus.desktop.data.Sync.request(0)
            kotlinx.coroutines.delay(180_000)
        }
    }
    // Listas: se actualizan solas a horas fijas (8:00, 14:00, 20:00 y 2:00)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(20_000)
        while (true) {
            runCatching { Repo.autoRefreshIfDue() }
            kotlinx.coroutines.delay(10 * 60_000L)
        }
    }

    // Esc / Retroceso = volver atrás
    KeyHandler { e ->
        if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || e.key == Key.Backspace) && (PlayerHost.req == null || PlayerHost.mini)) {
            if (nav.stack.size > 1) { nav.pop(); true } else false
        } else false
    }

    CompositionLocalProvider(LocalNav provides nav) {
        val cur = nav.current
        // El reproductor vive fuera de la navegación: así se puede minimizar y seguir navegando
        LaunchedEffect(cur) {
            if (cur is Screen.Player) { PlayerHost.open(cur.req); nav.pop() }
        }
        Box(Modifier.fillMaxSize()) {
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
                        Screen.Upcoming -> UpcomingScreen()
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
                NewEpisodeBanner(Modifier.align(Alignment.TopEnd).padding(24.dp))
            }
        }
        PlayerHost.req?.let { r ->
            key(PlayerHost.session) {
                val mini = PlayerHost.mini
                Box(
                    if (mini) Modifier.align(Alignment.BottomEnd).padding(24.dp).size(440.dp, 248.dp)
                        .shadow(24.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    else Modifier.fillMaxSize()
                ) { PlayerScreen(r, mini) }
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
        NavItem(Icons.Outlined.Event, Icons.Filled.Event, "Próximos", root == Screen.Upcoming) { nav.tab(Screen.Upcoming) }
        NavItem(Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark, "Mi lista", root == Screen.MyList) { nav.tab(Screen.MyList) }
        NavItem(Icons.Outlined.Search, Icons.Filled.Search, "Buscar", root == Screen.Search) { nav.tab(Screen.Search) }
        Spacer(Modifier.weight(1f))
        NavItem(Icons.Outlined.Settings, Icons.Filled.Settings, "Ajustes", root == Screen.Settings) { nav.tab(Screen.Settings) }
        // Sin barra de título: botones para minimizar y salir
        if (com.noirplus.desktop.LocalWindowMode.current != "window") {
            val ws = com.noirplus.desktop.LocalWindowState.current
            Row(Modifier.padding(start = 4.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NavMini(Icons.Filled.Minimize, "Minimizar") { ws.isMinimized = true }
                NavMini(Icons.Filled.Close, "Salir") { kotlin.system.exitProcess(0) }
            }
        }
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


@Composable
private fun NavMini(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (hovered) 0.14f else 0.06f))
            .hoverable(src).clickable(interactionSource = src, indication = null) { onClick() }.pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Col.Text, modifier = Modifier.size(18.dp)) }
}


/** Si Noir+ se cerró la última vez, se muestra el informe para copiarlo y enviarlo. */
@Composable
private fun CrashNotice() {
    val crash = remember { com.noirplus.desktop.data.ErrorLog.lastCrash(Store.lastCrashSeen) }
    var open by remember { mutableStateOf(crash != null) }
    if (!open || crash == null) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp).clip(RoundedCornerShape(16.dp)).background(Col.Surface2).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Noir+ se cerró la última vez", color = Col.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Copia este informe y envíamelo para arreglarlo. No incluye tu usuario, contraseña ni servidor.", color = Col.Sub, fontSize = 13.sp)
            Text(crash.second.take(1500), color = Col.Sub, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton("Copiar", primary = true) {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(crash.second), null)
                }
                NButton("Cerrar") { Store.lastCrashSeen = crash.first; open = false }
            }
        }
    }
}


/** Reproductor activo (a pantalla completa o minimizado en una esquina). */
object PlayerHost {
    var req by mutableStateOf<com.noirplus.desktop.data.PlayRequest?>(null)
        private set
    var mini by mutableStateOf(false)
    var session by mutableIntStateOf(0)
        private set
    fun open(r: com.noirplus.desktop.data.PlayRequest) { req = r; mini = false; session++ }
    fun close() { req = null; mini = false; com.noirplus.desktop.data.Sync.request(800) }
}
