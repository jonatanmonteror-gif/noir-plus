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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.focus.FocusRequester
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
    data object Welcome : Screen
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
    Screen.Welcome -> "welcome"
    Screen.Folders -> "folders"
    is Screen.Detail -> "detail:" + s.item.key
    is Screen.Player -> "player:" + s.req.key
    is Screen.Category -> "cat:" + s.kind.name + ":" + s.cat.id
}

@Composable
fun App() {
    val nav = remember { Nav(if (Repo.prefs.hasAccount()) Screen.Welcome else Screen.Setup) }
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
            RateHost {
            AnimatedContent(
                targetState = top,
                contentKey = { keyOf(it) },
                // En la tele: cambio de pantalla directo, sin fundidos
                transitionSpec = {
                    if (dims.tv) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(140))
                },
                modifier = Modifier.fillMaxSize().background(Col.Bg),
                label = "screens",
            ) { screen ->
                // Móvil: la barra de abajo está siempre (menos en el reproductor, el saludo y la configuración)
                val withBar = dims.compact && (screen is Screen.Detail || screen is Screen.Category || screen == Screen.Folders)
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Col.Bg)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        holder.SaveableStateProvider(keyOf(screen)) {
                            when (screen) {
                                Screen.Setup -> SetupScreen()
                                Screen.Main -> MainScreen()
                                Screen.Welcome -> WelcomeScreen()
                                Screen.Folders -> FoldersScreen()
                                is Screen.Detail -> DetailScreen(screen.item)
                                is Screen.Player -> PlayerScreen(screen.req)
                                is Screen.Category -> CategoryScreen(screen.kind, screen.cat, screen.search)
                            }
                        }
                    }
                    if (withBar) PhoneBottomBar()
                }
            }
            }
            LaunchedEffect(top) {
                delay(1500)
                mem.restore = false
            }
            if (top == Screen.Main) UpdateNotice()
            if (top == Screen.Main) WhatsNewAfterUpdate()
            CrashNotice()
            // Vuelta a la app tras varias horas: saludo de nuevo (salvo si se está viendo algo)
            val wr = Repo.welcomeRequest.intValue
            LaunchedEffect(wr) {
                if (wr > 0 && Repo.prefs.hasAccount() && nav.top !is Screen.Player) nav.reset(Screen.Welcome)
            }
        }
    }
}

/** Actualización: se comprueba al abrir, se descarga sola en segundo plano y queda lista para instalar. */
private object UpdateCheck {
    var checkedAt = 0L
    var release: Updater.Release? = null
    var file: java.io.File? = null
}

@Composable
private fun UpdateNotice() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rel by remember { mutableStateOf(UpdateCheck.release) }
    var ready by remember { mutableStateOf(UpdateCheck.file) }
    var visible by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    val installFr = remember { FocusRequester() }
    // Se mira al abrir y cada vez que la app vuelve a primer plano (la tele casi nunca cierra la app del todo)
    val resume = Repo.resumeCount.intValue
    LaunchedEffect(resume) {
        if (System.currentTimeMillis() - UpdateCheck.checkedAt > 20 * 60_000L) {
            UpdateCheck.checkedAt = System.currentTimeMillis()
            delay(1500)
            val r = Updater.latest()
            if (r != null && r.versionCode > Updater.currentCode) {
                UpdateCheck.release = r
                rel = r
                // Descarga en segundo plano (comprobada con su huella SHA-256)
                val f = runCatching { Updater.download(ctx, r) { } }.getOrNull()
                UpdateCheck.file = f
                ready = f
            }
        }
    }
    val r = rel ?: return
    val f = ready
    if (!visible || f == null) return
    // En la tele, el botón «Instalar» queda seleccionado: basta con pulsar OK
    LaunchedEffect(f) {
        delay(300)
        runCatching { installFr.requestFocus() }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = if (LocalDims.current.compact) 88.dp else 24.dp)
                .clip(RoundedCornerShape(14.dp)).background(Col.Surface2.copy(alpha = 0.97f)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = 14.dp).widthIn(max = 340.dp)) {
                Text("Nueva versión lista", color = Col.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(status ?: "${r.name} · ya descargada", color = Col.Sub, fontSize = 12.sp)
                // Novedades de lo que vas a instalar
                val pending by androidx.compose.runtime.produceState<List<String>>(emptyList(), r.versionCode) {
                    value = Updater.changelog().filter { it.code in (Updater.currentCode + 1)..r.versionCode }.flatMap { it.items }
                }
                pending.take(3).forEach { Text("• $it", color = Col.Text, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp)) }
                if (pending.size > 3) Text("y ${pending.size - 3} más (Ajustes → Actualizaciones → Novedades)", color = Col.Sub, fontSize = 11.sp)
            }
            ActionButton("Instalar", primary = true, key = "upd-install", focusRequester = installFr) {
                status = if (Updater.install(ctx, f)) "Abriendo el instalador…" else "Permite «Instalar apps desconocidas» y vuelve a pulsar"
            }
            Spacer(Modifier.width(8.dp))
            ActionButton("Luego", key = "upd-later") { visible = false }
        }
    }
}


/**
 * Si la app se cerró sola la última vez, al abrirla se muestra el error para poder copiarlo o enviarlo
 * (aunque no se pueda llegar a Ajustes).
 */
@Composable
private fun CrashNotice() {
    val ctx = LocalContext.current
    val crash = remember { com.noirplus.tv.data.ErrorLog.lastCrash(Repo.prefs.lastCrashSeen) }
    var open by remember { mutableStateOf(crash != null) }
    if (!open || crash == null) return
    fun close() { Repo.prefs.lastCrashSeen = crash.first; open = false }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 520.dp).clip(RoundedCornerShape(16.dp)).background(Col.Surface2).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Noir+ se cerró la última vez", color = Col.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Envía este informe para arreglarlo. No incluye tu usuario, contraseña ni servidor.", color = Col.Sub, fontSize = 13.sp)
            Text(crash.second.take(900), color = Col.Sub, fontSize = 10.sp, lineHeight = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Compartir", primary = true, key = "crash-share") {
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Noir+ · error")
                        .putExtra(android.content.Intent.EXTRA_TEXT, crash.second)
                    runCatching { ctx.startActivity(android.content.Intent.createChooser(i, "Enviar informe").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                ActionButton("Copiar", key = "crash-copy") {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Noir+ error", crash.second))
                    android.widget.Toast.makeText(ctx, "Copiado", android.widget.Toast.LENGTH_SHORT).show()
                }
                ActionButton("Cerrar", key = "crash-close") { close() }
            }
        }
    }
}
