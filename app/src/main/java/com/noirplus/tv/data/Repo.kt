package com.noirplus.tv.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

object Repo {
    lateinit var prefs: Prefs
        private set
    var source: Source? = null
        private set

    /** Se incrementa cuando hay que volver a mostrar la bienvenida (app abierta tras varias horas). */
    val welcomeRequest = mutableIntStateOf(0)
    /** Sube cada vez que la app vuelve a primer plano (para volver a mirar si hay actualización). */
    val resumeCount = mutableIntStateOf(0)

    /** Cambia cada vez que se actualizan las listas: las pantallas lo usan para recargar. */
    val dataVersion = mutableIntStateOf(0)

    /** Momento de la última carga completa o vaciado de caché. */
    var loadedAt: Long = System.currentTimeMillis()
        private set

    /** Las listas se consideran viejas a las 6 horas. */
    fun isStale(): Boolean = System.currentTimeMillis() - loadedAt > 6 * 3600_000L

    fun invalidate(kind: Kind? = null) {
        source?.clear(kind)
        Platforms.clear()
        loadedAt = System.currentTimeMillis()
        dataVersion.intValue = dataVersion.intValue + 1
    }

    data class RefreshStep(val kind: Kind, val count: Int?, val error: String? = null, val new: Int? = null)

    /** Horas fijas de actualización automática: cada 6 horas desde las 8 de la mañana. */
    private val SLOTS = listOf(2, 8, 14, 20)

    /** Momento de la última hora fija ya pasada (hoy o ayer). */
    fun lastSlot(now: Long = System.currentTimeMillis()): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        val h = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
        val slot = SLOTS.lastOrNull { it <= h }
        if (slot != null) c.set(java.util.Calendar.HOUR_OF_DAY, slot)
        else { c.add(java.util.Calendar.DAY_OF_YEAR, -1); c.set(java.util.Calendar.HOUR_OF_DAY, SLOTS.last()) }
        return c.timeInMillis
    }

    private var autoBusy = false
    /** Cuándo se abrió la app: la actualización automática solo salta si una hora fija pasa con la app abierta. */
    private val startedAt = System.currentTimeMillis()

    /** Si una hora fija (8, 14, 20, 2) pasa con la app abierta, actualiza en segundo plano. Al abrir no se descarga nada. */
    suspend fun autoRefreshIfDue() {
        if (source == null || autoBusy) return
        val slot = lastSlot()
        if (prefs.lastListRefresh >= slot || slot <= startedAt) return
        autoBusy = true
        try { refresh(null) } finally { autoBusy = false }
    }

    /** Vuelve a descargar las listas indicadas (todas si kinds es null) y avisa del progreso. */
    suspend fun refresh(kinds: List<Kind>? = null, onStep: (RefreshStep) -> Unit = {}) {
        val src = source ?: return
        val list = kinds ?: Kind.entries.toList()
        // Lo «nuevo» es lo que tu proveedor ha añadido desde la actualización anterior
        val since = prefs.lastListRefresh
        Platforms.clear()
        if (kinds == null) src.clear(null) else list.forEach { src.clear(it) }
        for (k in list) {
            onStep(RefreshStep(k, null))
            var newCount: Int? = null
            val r = runCatching {
                src.categories(k)
                val all = src.all(k)
                if (since > 0) newCount = all.count { (if (it.added > 100_000_000_000L) it.added else it.added * 1000) > since }
                all.size
            }
            r.exceptionOrNull()?.let { ErrorLog.note("Actualizar lista (${k.name})", it) }
            onStep(RefreshStep(k, r.getOrNull(), r.exceptionOrNull()?.let { friendlyError(it) }, newCount))
        }
        if (kinds == null) prefs.lastListRefresh = System.currentTimeMillis()
        loadedAt = System.currentTimeMillis()
        dataVersion.intValue = dataVersion.intValue + 1
    }

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            ErrorLog.init(ctx.applicationContext)
            ListDisk.root = java.io.File(ctx.applicationContext.filesDir, "listas").apply { mkdirs() }
            prefs = Prefs(ctx.applicationContext)
            setupImages(ctx.applicationContext)
            Tmdb.init(ctx.applicationContext)
            Tmdb.preloadLater()
            rebuild()
        }
    }

    /** Crea de nuevo el origen (vacía cachés). */
    fun rebuild() {
        dataVersion.intValue = dataVersion.intValue + 1
        loadedAt = System.currentTimeMillis()
        val p = prefs
        source = when (p.type) {
            "xtream" -> XtreamSource(p.server, p.user, p.pass) { p.liveFormat }
            "m3u" -> M3uSource(p.m3u)
            else -> null
        }
        Sync.request(2000)
    }

    /**
     * Imágenes: fundido suave al aparecer, caché en memoria y en disco (no se vuelven a descargar)
     * y formato de color ligero para no saturar la memoria de la TV.
     */
    private fun setupImages(ctx: Context) {
        Coil.setImageLoader(
            ImageLoader.Builder(ctx)
                .okHttpClient(ImageTuning.client)
                .components { add(ImageTuning.interceptor) }
                .crossfade(if (prefs.isTv) 0 else 150)
                .allowRgb565(true)
                .respectCacheHeaders(false)
                .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.20).build() }
                .diskCache { DiskCache.Builder().directory(ctx.cacheDir.resolve("img")).maxSizeBytes(200L * 1024 * 1024).build() }
                .build()
        )
    }
}
