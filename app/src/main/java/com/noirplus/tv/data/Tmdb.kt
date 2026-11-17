package com.noirplus.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * TMDB (The Movie Database): en qué plataformas está cada película o serie en España.
 * Datos de "watch providers" de TMDB (origen JustWatch). Necesita una clave gratuita de TMDB.
 */
object Tmdb {
    data class Provider(val name: String, val logo: String?)

    private val cache = ConcurrentHashMap<String, List<Provider>>()

    /** Clave incluida en la app: así funciona en todos los dispositivos sin configurar nada. */
    private const val BUILT_IN_KEY = "b5a63bce5d9e66d0a925f9736bd5d403"
    /** Clave en uso: la que el usuario haya puesto en Ajustes, o la incluida. */
    val key: String get() = Repo.prefs.tmdbKey.ifBlank { BUILT_IN_KEY.takeUnless { it.startsWith("__") } ?: "" }

    fun configured(): Boolean = key.isNotBlank()
    fun clear() = cache.clear()

    // --- Memoria en disco de las plataformas de cada título (para filtrar sin volver a preguntar) ---
    private const val WEEK = 7L * 24 * 3600 * 1000
    private var file: File? = null
    private val names = ConcurrentHashMap<String, Pair<Long, List<String>>>()
    @Volatile private var dirty = false

    fun init(ctx: android.content.Context) {
        file = File(ctx.filesDir, "tmdb_platforms.json")
        runCatching {
            val o = JSONObject(file!!.readText())
            val now = System.currentTimeMillis()
            for (k in o.keys()) {
                val e = o.optJSONObject(k) ?: continue
                val t = e.optLong("t")
                if (now - t > WEEK) continue // las plataformas cambian: se renuevan cada semana
                val arr = e.optJSONArray("p")
                names[k] = t to (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }
            }
        }
    }

    private fun remember(key: String, list: List<Provider>) {
        names[key] = System.currentTimeMillis() to list.map { it.name }
        dirty = true
    }

    fun save() {
        val f = file ?: return
        if (!dirty) return
        dirty = false
        runCatching {
            val o = JSONObject()
            for ((k, v) in names) o.put(k, JSONObject().put("t", v.first).put("p", org.json.JSONArray(v.second)))
            f.writeText(o.toString())
        }
    }

    /** Plataformas ya conocidas de un título (null = aún no se ha consultado). */
    fun known(item: Item): List<String>? = cache[item.key]?.map { it.name } ?: names[item.key]?.second

    /**
     * Consulta las plataformas de muchos títulos a la vez (para el filtro).
     * Sin pedir la ficha al proveedor IPTV, para no saturarlo. Lo ya consultado no se repite.
     */
    suspend fun resolve(items: List<Item>, onProgress: (Int) -> Unit) = coroutineScope {
        val sem = Semaphore(8)
        val done = AtomicInteger(0)
        items.map { item ->
            async(Dispatchers.IO) {
                if (known(item) == null) sem.withPermit { runCatching { providers(item, fast = true) } }
                val n = done.incrementAndGet()
                if (n % 25 == 0) save()
                onProgress(n)
            }
        }.awaitAll()
        save()
    }

    private suspend fun get(path: String, params: String = "", key: String = this.key): JSONObject? = withContext(Dispatchers.IO) {
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

    suspend fun providers(item: Item, fast: Boolean = false): List<Provider> {
        if (!configured() || item.kind == Kind.LIVE) return emptyList()
        cache[item.key]?.let { return it }
        val type = if (item.kind == Kind.SERIES) "tv" else "movie"
        val src = Repo.source
        var id: String? = item.tmdb?.takeIf { it.isNotBlank() && it != "0" } ?: if (fast) null else runCatching {
            if (item.kind == Kind.MOVIE) src?.movieDetails(item)?.tmdbId else src?.seriesDetails(item)?.details?.tmdbId
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "0" }
        if (id == null) {
            val (q, year) = clean(item.name)
            if (q.isBlank()) return emptyList()
            val yp = year?.let { if (type == "tv") "&first_air_date_year=$it" else "&year=$it" } ?: ""
            val res = (get("/search/$type", "query=${enc(q)}&include_adult=false$yp") ?: return emptyList()).optJSONArray("results")
            id = res?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }
        if (id == null) return emptyList<Provider>().also { cache[item.key] = it; remember(item.key, it) }
        val es = (get("/$type/$id/watch/providers") ?: return emptyList()).optJSONObject("results")?.optJSONObject("ES")
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
        remember(item.key, list)
        return list
    }
}
