package com.noirplus.desktop.data

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Origen de datos activo (Xtream o M3U) creado a partir de la cuenta guardada. */
object Repo {
    var source by mutableStateOf<Source?>(null)
        private set
    /** Sube cuando se recargan las listas, para refrescar pantallas. */
    val dataVersion = mutableIntStateOf(0)

    fun rebuild() {
        source = when (Store.type) {
            "xtream" -> XtreamSource(Store.server, Store.user, Store.pass) { Store.liveFormat }
            "m3u" -> M3uSource(Store.m3u)
            else -> null
        }
        dataVersion.intValue++
    }

    fun refresh() {
        source?.clear(null)
        dataVersion.intValue++
    }

    /** Lo último añadido de un tipo (para la portada e Inicio). */
    suspend fun recent(kind: Kind, n: Int): List<Item> = runCatching {
        source?.all(kind).orEmpty().sortedByDescending { it.added }.take(n)
    }.getOrDefault(emptyList())
}

