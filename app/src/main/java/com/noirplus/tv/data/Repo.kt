package com.noirplus.tv.data

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

object Repo {
    lateinit var prefs: Prefs
        private set
    var source: Source? = null
        private set

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = Prefs(ctx.applicationContext)
            setupImages(ctx.applicationContext)
            rebuild()
        }
    }

    /** Crea de nuevo el origen (vacía cachés). */
    fun rebuild() {
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
                .okHttpClient(Http.client)
                .crossfade(220)
                .allowRgb565(true)
                .respectCacheHeaders(false)
                .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.20).build() }
                .diskCache { DiskCache.Builder().directory(ctx.cacheDir.resolve("img")).maxSizeBytes(200L * 1024 * 1024).build() }
                .build()
        )
    }
}
