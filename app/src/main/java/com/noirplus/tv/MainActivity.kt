package com.noirplus.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.noirplus.tv.data.Repo
import com.noirplus.tv.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent { App() }
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
