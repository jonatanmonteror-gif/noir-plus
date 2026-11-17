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

    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Si la app vuelve tras más de 4 horas en segundo plano, saludo de nuevo y listas frescas.
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 4 * 3600_000L) {
            Repo.welcomeRequest.intValue = Repo.welcomeRequest.intValue + 1
        }
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
