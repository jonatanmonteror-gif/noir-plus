package com.noirplus.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.PlayRequest
import com.noirplus.tv.data.Repo
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Setup : Screen
    data object Main : Screen
    data class Detail(val item: Item) : Screen
    data class Player(val req: PlayRequest) : Screen
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
    is Screen.Detail -> "detail:" + s.item.key
    is Screen.Player -> "player:" + s.req.key
}

@Composable
fun App() {
    val nav = remember { Nav(if (Repo.prefs.hasAccount()) Screen.Main else Screen.Setup) }
    val mem = remember { FocusMemory() }
    val holder = rememberSaveableStateHolder()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Col.Accent,
            background = Col.Bg,
            surface = Col.Surface,
            onSurface = Col.Text,
            onBackground = Col.Text,
        )
    ) {
        CompositionLocalProvider(LocalNav provides nav, LocalFocusMemory provides mem) {
            val top = nav.top
            BackHandler(enabled = nav.stack.size > 1) {
                mem.restore = true
                nav.pop()
            }
            Box(Modifier.fillMaxSize().background(Col.Bg)) {
                holder.SaveableStateProvider(keyOf(top)) {
                    when (top) {
                        Screen.Setup -> SetupScreen()
                        Screen.Main -> MainScreen()
                        is Screen.Detail -> DetailScreen(top.item)
                        is Screen.Player -> PlayerScreen(top.req)
                    }
                }
            }
            LaunchedEffect(top) {
                delay(1500)
                mem.restore = false
            }
        }
    }
}
