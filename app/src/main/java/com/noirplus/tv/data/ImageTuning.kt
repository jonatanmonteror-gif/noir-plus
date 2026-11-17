package com.noirplus.tv.data

import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.size.Dimension
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Portadas más rápidas:
 * - Muchas listas enlazan las portadas de TMDB en tamaño original (1–3 MB cada una).
 *   Aquí se pide el tamaño justo para el hueco donde se pinta (w185/w342/w500/w780): pesa 10–30 veces menos.
 * - Más descargas en paralelo y tiempos de espera cortos para que una portada lenta no bloquee a las demás.
 */
object ImageTuning {
    private val tmdb = Regex("""(https?://image\.tmdb\.org/t/p/)([^/]+)(/.+)""")

    private fun bucket(px: Int): String = when {
        px <= 200 -> "w185"
        px <= 360 -> "w342"
        px <= 520 -> "w500"
        px <= 820 -> "w780"
        px <= 1300 -> "w1280"
        else -> "original"
    }

    /** Cambia el tamaño de una URL de TMDB al que hace falta. Otras URLs no se tocan. */
    fun resize(url: String, widthPx: Int): String {
        val m = tmdb.matchEntire(url) ?: return url
        val want = bucket(widthPx)
        val have = m.groupValues[2]
        // Si ya viene más pequeña de lo que pediríamos, se deja como está.
        val haveW = Regex("""^w(\d+)$""").find(have)?.groupValues?.get(1)?.toIntOrNull()
        val wantW = want.removePrefix("w").toIntOrNull()
        if (haveW != null && wantW != null && haveW <= wantW) return url
        return m.groupValues[1] + want + m.groupValues[3]
    }

    val interceptor = object : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val req = chain.request
            val data = req.data as? String ?: return chain.proceed(req)
            if (!data.contains("image.tmdb.org")) return chain.proceed(req)
            val w = (chain.size.width as? Dimension.Pixels)?.px ?: return chain.proceed(req)
            val url = resize(data, w)
            if (url == data) return chain.proceed(req)
            return chain.proceed(req.newBuilder().data(url).build())
        }
    }

    val client: OkHttpClient = Http.client.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 48; maxRequestsPerHost = 12 })
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
