package com.noirplus.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB (The Movie Database): en qué plataformas está cada película o serie en España.
 * Datos de "watch providers" de TMDB (origen JustWatch). Necesita una clave gratuita de TMDB.
 */
object Tmdb {
    data class Provider(val name: String, val logo: String?)

    private val cache = ConcurrentHashMap<String, List<Provider>>()

    fun configured(): Boolean = Repo.prefs.tmdbKey.isNotBlank()
    fun clear() = cache.clear()

    private suspend fun get(path: String, params: String = "", key: String = Repo.prefs.tmdbKey): JSONObject? = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext null
        val v4 = key.length > 40 // el token de lectura (v4) es largo; la API key (v3) tiene 32 caracteres
        val q = buildString {
            append("https://api.themoviedb.org/3").append(path).append("?language=es-ES")
            if (params.isNotEmpty()) append("&").append(params)
            if (!v4) append("&api_key=").append(key)
        }
        val req = Request.Builder().url(q).header("Accept", "application/json").apply {
            if (v4) header("Authorization", "Bearer $key")
        }.build()
        runCatching {
            Http.client.newCall(req).execute().use { r -> if (r.isSuccessful) JSONObject(r.body?.string() ?: "{}") else null }
        }.getOrNull()
    }

    /** Comprueba que la clave funciona. */
    suspend fun test(key: String): Boolean = get("/configuration", key = key.trim()) != null

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Título limpio para buscar: sin año, calidad, idioma ni corchetes. */
    private fun clean(title: String): Pair<String, String?> {
        val year = Regex("""\b(19|20)\d{2}\b""").find(title)?.value
        val t = title
            .replace(Regex("""^\s*[A-Za-z]{2,3}\s*[\|:\-]\s*"""), "")
            .replace(Regex("""[\(\[].*?[\)\]]"""), " ")
            .replace(Regex("""\b(19|20)\d{2}\b"""), " ")
            .replace(Regex("""\b(4K|UHD|FHD|HD|SD|HDR|VOSE|VO|DUAL|LATINO|CASTELLANO)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|', ':')
        return t to year
    }

    suspend fun providers(item: Item): List<Provider> {
        if (!configured() || item.kind == Kind.LIVE) return emptyList()
        cache[item.key]?.let { return it }
        val type = if (item.kind == Kind.SERIES) "tv" else "movie"
        val src = Repo.source
        var id: String? = runCatching {
            if (item.kind == Kind.MOVIE) src?.movieDetails(item)?.tmdbId else src?.seriesDetails(item)?.details?.tmdbId
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "0" }
        if (id == null) {
            val (q, year) = clean(item.name)
            if (q.isBlank()) return emptyList()
            val yp = year?.let { if (type == "tv") "&first_air_date_year=$it" else "&year=$it" } ?: ""
            val res = get("/search/$type", "query=${enc(q)}&include_adult=false$yp")?.optJSONArray("results")
            id = res?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }
        if (id == null) return emptyList<Provider>().also { cache[item.key] = it }
        val es = get("/$type/$id/watch/providers")?.optJSONObject("results")?.optJSONObject("ES")
        val list = ArrayList<Provider>()
        for (kindKey in listOf("flatrate", "free", "ads")) {
            val arr = es?.optJSONArray(kindKey) ?: continue
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val name = p.optString("provider_name")
                if (name.isBlank()) continue
                if (list.none { it.name == name }) {
                    list.add(Provider(name, p.optString("logo_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w92$it" }))
                }
            }
        }
        cache[item.key] = list
        return list
    }
}
