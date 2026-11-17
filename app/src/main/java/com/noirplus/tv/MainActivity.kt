package com.noirplus.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.noirplus.tv.data.Repo
import com.noirplus.tv.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        hideSystemBars()
        setContent { App() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Pantalla completa: oculta la barra de navegación y la de estado (se muestran deslizando desde el borde). */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = keyHandler
        if (handler != null && handler(event)) return true
        return super.dispatchKeyEvent(event)
    }

    companion object {
        /** El reproductor lo usa para capturar el mando (cambiar de canal, avanzar, etc.). */
        var keyHandler: ((KeyEvent) -> Boolean)? = null
    }
}
