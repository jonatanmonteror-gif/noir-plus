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
            "xtream" -> CachedSource(XtreamSource(Store.server, Store.user, Store.pass) { Store.liveFormat },
                "x|${Store.server}|${Store.user}|${Store.liveFormat}")
            "m3u" -> CachedSource(M3uSource(Store.m3u), "m|${Store.m3u}")
            else -> null
        }
        dataVersion.intValue++
        Sync.request(2000)
    }

    fun refresh() {
        source?.clear(null)
        dataVersion.intValue++
    }

    /** Vuelve a descargar las listas del servidor ahora mismo. Devuelve un resumen o lanza el error. */
    suspend fun refreshNow(): String {
        val src = source ?: throw IllegalStateException("no hay ninguna cuenta")
        src.clear(null)
        val counts = Kind.entries.map { k -> src.categories(k); k to src.all(k).size }.toMap()
        dataVersion.intValue++
        return "Listas actualizadas: ${counts[Kind.MOVIE] ?: 0} películas, ${counts[Kind.SERIES] ?: 0} series y ${counts[Kind.LIVE] ?: 0} canales."
    }

    /** Lo último añadido de un tipo (para la portada e Inicio). */
    suspend fun recent(kind: Kind, n: Int): List<Item> = runCatching {
        source?.all(kind).orEmpty().sortedByDescending { it.added }.take(n)
    }.getOrDefault(emptyList())
}

