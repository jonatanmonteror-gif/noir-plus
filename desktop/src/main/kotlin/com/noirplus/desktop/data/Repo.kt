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

    /** Vuelve a descargar las listas del servidor ahora mismo. Devuelve un resumen (con lo nuevo) o lanza el error. */
    suspend fun refreshNow(): String {
        val src = source ?: throw IllegalStateException("no hay ninguna cuenta")
        val since = Store.lastListRefresh
        src.clear(null)
        val all = Kind.entries.associateWith { k -> src.categories(k); src.all(k) }
        Store.lastListRefresh = System.currentTimeMillis()
        dataVersion.intValue++
        fun newOf(k: Kind) = all[k].orEmpty().count { (if (it.added > 100_000_000_000L) it.added else it.added * 1000) > since }
        val base = "Listas actualizadas: ${all[Kind.MOVIE]?.size ?: 0} películas, ${all[Kind.SERIES]?.size ?: 0} series y ${all[Kind.LIVE]?.size ?: 0} canales."
        return if (since > 0) "$base\nNuevo desde la última actualización: ${newOf(Kind.MOVIE)} películas, ${newOf(Kind.SERIES)} series y ${newOf(Kind.LIVE)} canales."
        else base
    }

    /** Horas fijas de actualización automática: cada 6 horas desde las 8 de la mañana. */
    private val SLOTS = listOf(2, 8, 14, 20)
    fun lastSlot(now: Long = System.currentTimeMillis()): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        val h = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
        val slot = SLOTS.lastOrNull { it <= h }
        if (slot != null) c.set(java.util.Calendar.HOUR_OF_DAY, slot)
        else { c.add(java.util.Calendar.DAY_OF_YEAR, -1); c.set(java.util.Calendar.HOUR_OF_DAY, SLOTS.last()) }
        return c.timeInMillis
    }

    private var autoBusy = false
    /** Si ha pasado una hora fija desde la última actualización, actualiza en segundo plano. */
    suspend fun autoRefreshIfDue() {
        if (source == null || autoBusy) return
        if (Store.lastListRefresh >= lastSlot()) return
        autoBusy = true
        try { refreshNow() } finally { autoBusy = false }
    }

    /** Lo último añadido de un tipo (para la portada e Inicio). */
    suspend fun recent(kind: Kind, n: Int): List<Item> = runCatching {
        source?.all(kind).orEmpty().sortedByDescending { it.added }.take(n)
    }.getOrDefault(emptyList())
}

