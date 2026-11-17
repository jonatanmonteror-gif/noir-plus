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
import kotlinx.coroutines.launch
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

    // --- Catálogo de cada plataforma en España (para el filtro) ---
    // En vez de preguntar título a título, se descarga una vez la lista de lo que hay en
    // la plataforma (unas decenas de páginas en paralelo) y se cruza con tu lista al instante.

    /** Lo que hay en una plataforma: ids de TMDB y títulos normalizados con sus años. */
    class Catalog(val ids: Set<String>, val titles: Map<String, List<Int>>) {
        fun has(item: Item): Boolean {
            item.tmdb?.let { if (it in ids) return true }
            val (q, y) = clean(item.name)
            val years = titles[titleKey(q)] ?: return false
            val year = (item.year ?: y)?.take(4)?.toIntOrNull() ?: return true
            return years.isEmpty() || years.any { it == 0 || kotlin.math.abs(it - year) <= 1 }
        }
    }

    private val catalogs = ConcurrentHashMap<String, Catalog>()
    private val marks = Regex("\\p{Mn}+")
    fun titleKey(s: String): String =
        marks.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "").lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Ids de TMDB de todas las variantes de una plataforma en España (p. ej. Netflix y Netflix con anuncios). */
    private suspend fun providerIds(type: String, group: String): String? {
        val arr = get("/watch/providers/$type", "watch_region=ES")?.optJSONArray("results") ?: return null
        val ids = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            o.optString("provider_id").takeIf { Platforms.group(o.optString("provider_name")) == group }
        }
        return ids.takeIf { it.isNotEmpty() }?.joinToString("|")
    }

    /** Catálogo de una plataforma (se guarda una semana). onProgress(hechas, total) en páginas. */
    private fun catFile(kind: Kind, group: String): File? {
        val type = if (kind == Kind.SERIES) "tv" else "movie"
        return file?.parentFile?.let { File(it, "tmdb_cat_" + titleKey("$type:$group").replace(' ', '_') + ".json") }
    }

    private fun isFresh(kind: Kind, group: String): Boolean =
        catFile(kind, group)?.let { it.exists() && System.currentTimeMillis() - it.lastModified() < WEEK } == true

    /**
     * Precarga en segundo plano de todos los catálogos, poco a poco (≈4 consultas por segundo),
     * empezando unos segundos después de abrir la app. Solo descarga los que tienen más de una semana.
     */
    fun preloadLater() {
        if (preloadStarted) return
        preloadStarted = true
        bg.launch {
            kotlinx.coroutines.delay(12_000)
            for (kind in listOf(Kind.MOVIE, Kind.SERIES)) for (group in Platforms.main) {
                if (!configured()) return@launch
                val type = if (kind == Kind.SERIES) "tv" else "movie"
                if (isFresh(kind, group) || catalogs.containsKey("$type:$group")) continue
                runCatching { catalog(kind, group, slow = true) }
                catalogs.remove("$type:$group") // no se queda en memoria: se lee del disco cuando haga falta
                kotlinx.coroutines.delay(2_000)
            }
        }
    }
    @Volatile private var preloadStarted = false
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    suspend fun catalog(kind: Kind, group: String, slow: Boolean = false, onProgress: (Int, Int) -> Unit = { _, _ -> }): Catalog? = coroutineScope {
        val type = if (kind == Kind.SERIES) "tv" else "movie"
        val id = "$type:$group"
        catalogs[id]?.let { return@coroutineScope it }
        val f = catFile(kind, group)
        // Copia guardada en el móvil/tele
        runCatching {
            if (f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < WEEK) {
                val o = JSONObject(f.readText())
                val idsA = o.getJSONArray("ids")
                val tO = o.getJSONObject("t")
                val titles = HashMap<String, List<Int>>()
                for (k in tO.keys()) { val a = tO.getJSONArray(k); titles[k] = (0 until a.length()).map { a.optInt(it) } }
                return@coroutineScope Catalog((0 until idsA.length()).map { idsA.optString(it) }.toSet(), titles)
                    .also { catalogs[id] = it }
            }
        }
        val prov = providerIds(type, group) ?: return@coroutineScope null
        val base = "watch_region=ES&with_watch_providers=${enc(prov)}&with_watch_monetization_types=${enc("flatrate|free|ads")}&sort_by=popularity.desc"
        val first = get("/discover/$type", "$base&page=1") ?: return@coroutineScope null
        val pages = first.optInt("total_pages", 1).coerceIn(1, 500)
        val results = ConcurrentHashMap<Int, org.json.JSONArray>()
        first.optJSONArray("results")?.let { results[1] = it }
        val done = AtomicInteger(1)
        onProgress(1, pages)
        val sem = Semaphore(if (slow) 2 else 16)
        (2..pages).map { pg ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    if (slow) kotlinx.coroutines.delay(400)
                    var r: JSONObject? = null
                    for (attempt in 0 until 3) { r = get("/discover/$type", "$base&page=$pg"); if (r != null) break; kotlinx.coroutines.delay(400L * (attempt + 1)) }
                    r?.optJSONArray("results")?.let { results[pg] = it }
                }
                onProgress(done.incrementAndGet(), pages)
            }
        }.awaitAll()
        val ids = HashSet<String>()
        val titles = HashMap<String, MutableList<Int>>()
        for (arr in results.values) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            ids.add(o.optString("id"))
            val year = (o.optString(if (type == "tv") "first_air_date" else "release_date")).take(4).toIntOrNull() ?: 0
            for (k in listOf("title", "original_title", "name", "original_name")) {
                val t = o.optString(k).takeIf { it.isNotBlank() } ?: continue
                val key = titleKey(t)
                if (key.isNotEmpty()) titles.getOrPut(key) { ArrayList() }.let { if (year !in it) it.add(year) }
            }
        }
        val cat = Catalog(ids, titles)
        catalogs[id] = cat
        // Solo se guarda si llegaron casi todas las páginas
        if (f != null && results.size >= pages * 9 / 10) runCatching {
            val tO = JSONObject()
            for ((k, v) in titles) tO.put(k, org.json.JSONArray(v))
            f.writeText(JSONObject().put("ids", org.json.JSONArray(ids.toList())).put("t", tO).toString())
        }
        cat
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
    fun clean(title: String): Pair<String, String?> {
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
