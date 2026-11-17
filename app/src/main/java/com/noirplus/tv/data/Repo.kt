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

    data class RefreshStep(val kind: Kind, val count: Int?, val error: String? = null)

    /** Vuelve a descargar las listas indicadas (todas si kinds es null) y avisa del progreso. */
    suspend fun refresh(kinds: List<Kind>? = null, onStep: (RefreshStep) -> Unit = {}) {
        val src = source ?: return
        val list = kinds ?: Kind.entries.toList()
        Platforms.clear()
        if (kinds == null) src.clear(null) else list.forEach { src.clear(it) }
        for (k in list) {
            onStep(RefreshStep(k, null))
            val r = runCatching {
                src.categories(k)
                src.all(k).size
            }
            onStep(RefreshStep(k, r.getOrNull(), r.exceptionOrNull()?.let { friendlyError(it) }))
        }
        loadedAt = System.currentTimeMillis()
        dataVersion.intValue = dataVersion.intValue + 1
    }

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
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
                .crossfade(150)
                .allowRgb565(true)
                .respectCacheHeaders(false)
                .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.20).build() }
                .diskCache { DiskCache.Builder().directory(ctx.cacheDir.resolve("img")).maxSizeBytes(200L * 1024 * 1024).build() }
                .build()
        )
    }
}
