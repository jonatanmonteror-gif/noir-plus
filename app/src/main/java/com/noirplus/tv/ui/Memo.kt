package com.noirplus.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState

/**
 * Lo último que se cargó en cada pantalla, en memoria. Al volver atrás la pantalla sale al momento con los mismos
 * datos (y así se recupera la posición y el foco), y por detrás se refresca.
 */
object Memo {
    private val m = HashMap<String, Any?>()
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = synchronized(m) { m[key] as T? }
    fun put(key: String, v: Any?) { synchronized(m) { m[key] = v } }
}

@Composable
fun <T> produceMemo(key: String, initial: T, vararg keys: Any?, load: suspend () -> T): State<T> =
    produceState(Memo.get<T>(key) ?: initial, key, *keys) {
        val v = load()
        Memo.put(key, v)
        value = v
    }
