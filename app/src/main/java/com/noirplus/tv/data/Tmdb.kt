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
        genreFile = File(ctx.filesDir, "tmdb_genres.json")
        loadGenres()
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

    /** Una película o serie del catálogo de TMDB: año y géneros. */
    class Entry(val year: Int, val genres: List<Int>)

    /** Lo que hay en una plataforma: fichas por id de TMDB y, por cada título normalizado, sus ids. */
    class Catalog(val entries: Map<String, Entry>, val titles: Map<String, List<String>>) {
        /** Id de TMDB de un título de tu lista dentro de este catálogo, o null si no está. */
        fun match(item: Item): String? {
            item.tmdb?.let { if (entries.containsKey(it)) return it }
            val (q, y) = clean(item.name)
            val ids = titles[titleKey(q)] ?: return null
            val year = (item.year ?: y)?.take(4)?.toIntOrNull() ?: return ids.firstOrNull()
            return ids.firstOrNull { val e = entries[it]; e == null || e.year == 0 || kotlin.math.abs(e.year - year) <= 1 }
        }
        fun has(item: Item): Boolean = match(item) != null
        fun genresOf(item: Item): List<Int>? = match(item)?.let { entries[it]?.genres }
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
        return file?.parentFile?.let { File(it, "tmdb_cat2_" + titleKey("$type:$group").replace(' ', '_') + ".json") }
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
                kotlinx.coroutines.delay(2_000)
            }
            // Después, los géneros de lo que no está en ninguna plataforma
            if (Repo.source != null) { indexGenres(Kind.MOVIE); genreJobs[Kind.MOVIE]?.join(); indexGenres(Kind.SERIES) }
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
                val eO = o.getJSONObject("e")
                val tO = o.getJSONObject("t")
                val entries = HashMap<String, Entry>()
                for (k in eO.keys()) {
                    val a = eO.getJSONArray(k)
                    val g = a.optJSONArray(1)
                    entries[k] = Entry(a.optInt(0), (0 until (g?.length() ?: 0)).map { g!!.optInt(it) })
                }
                val titles = HashMap<String, List<String>>()
                for (k in tO.keys()) { val a = tO.getJSONArray(k); titles[k] = (0 until a.length()).map { a.optString(it) } }
                return@coroutineScope Catalog(entries, titles).also { catalogs[id] = it }
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
        val sem = Semaphore(if (slow) 2 else 8)
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
        val entries = HashMap<String, Entry>()
        val titles = HashMap<String, MutableList<String>>()
        for (arr in results.values) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tid = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val year = (o.optString(if (type == "tv") "first_air_date" else "release_date")).take(4).toIntOrNull() ?: 0
            val g = o.optJSONArray("genre_ids")
            entries[tid] = Entry(year, (0 until (g?.length() ?: 0)).map { g!!.optInt(it) })
            for (k in listOf("title", "original_title", "name", "original_name")) {
                val t = o.optString(k).takeIf { it.isNotBlank() } ?: continue
                val key = titleKey(t)
                if (key.isNotEmpty()) titles.getOrPut(key) { ArrayList() }.let { if (tid !in it) it.add(tid) }
            }
        }
        val cat = Catalog(entries, titles)
        catalogs[id] = cat
        // Solo se guarda si llegaron casi todas las páginas
        if (f != null && results.size >= pages * 9 / 10) runCatching {
            val eO = JSONObject()
            for ((k, v) in entries) eO.put(k, org.json.JSONArray().put(v.year).put(org.json.JSONArray(v.genres)))
            val tO = JSONObject()
            for ((k, v) in titles) tO.put(k, org.json.JSONArray(v))
            f.writeText(JSONObject().put("e", eO).put("t", tO).toString())
        }
        cat
    }

    /** Catálogos de todas las plataformas de un tipo (de disco si están, si no se descargan). */
    suspend fun allCatalogs(kind: Kind): Map<String, Catalog> = coroutineScope {
        Platforms.main.map { g -> async { g to runCatching { catalog(kind, g) }.getOrNull() } }.awaitAll()
            .mapNotNull { (g, c) -> c?.let { g to it } }.toMap()
    }

    // --- Géneros de cada título de tu lista ---
    // Primero se miran los catálogos de plataformas (ya traen los géneros). Lo que no esté en ninguna
    // plataforma (estrenos de cine, etc.) se busca en TMDB poco a poco en segundo plano y se guarda.

    val movieGenres = linkedMapOf(
        28 to "Acción", 12 to "Aventura", 16 to "Animación", 35 to "Comedia", 80 to "Crimen", 99 to "Documental",
        18 to "Drama", 10751 to "Familia", 14 to "Fantasía", 36 to "Historia", 27 to "Terror", 10402 to "Música",
        9648 to "Misterio", 10749 to "Romance", 878 to "Ciencia ficción", 53 to "Suspense", 10752 to "Bélica", 37 to "Western",
    )
    val tvGenres = linkedMapOf(
        10759 to "Acción y aventura", 16 to "Animación", 35 to "Comedia", 80 to "Crimen", 99 to "Documental",
        18 to "Drama", 10751 to "Familia", 10762 to "Infantil", 9648 to "Misterio", 10764 to "Reality",
        10765 to "Ciencia ficción y fantasía", 10768 to "Bélica y política", 37 to "Western", 10766 to "Telenovela",
    )
    fun genreNames(kind: Kind) = if (kind == Kind.SERIES) tvGenres else movieGenres

    private val genreMeta = ConcurrentHashMap<String, List<Int>>() // item.key → géneros (vacío = no encontrado)
    private var genreFile: File? = null
    @Volatile private var genreDirty = false
    /** Sube cada vez que se clasifican más títulos, para refrescar las filas de géneros. */
    val genreVersion = androidx.compose.runtime.mutableIntStateOf(0)
    /** Progreso de la clasificación en segundo plano: (hechos, total) o null si no hay ninguna en marcha. */
    val genreProgress = androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null)

    private fun loadGenres() {
        val f = genreFile ?: return
        runCatching {
            val o = JSONObject(f.readText())
            for (k in o.keys()) { val a = o.getJSONArray(k); genreMeta[k] = (0 until a.length()).map { a.optInt(it) } }
        }
    }

    private fun saveGenres() {
        val f = genreFile ?: return
        if (!genreDirty) return
        genreDirty = false
        runCatching {
            val o = JSONObject()
            for ((k, v) in genreMeta) o.put(k, org.json.JSONArray(v))
            f.writeText(o.toString())
        }
    }

    /** Géneros de un título: de los catálogos (si se pasan) o de lo ya clasificado. null = aún sin clasificar. */
    fun genresOf(item: Item, cats: Collection<Catalog>): List<Int>? {
        for (c in cats) c.genresOf(item)?.let { return it }
        return genreMeta[item.key]
    }

    private val genreJobs = ConcurrentHashMap<Kind, kotlinx.coroutines.Job>()

    /** Clasifica por géneros, poco a poco, lo que no esté en ningún catálogo de plataforma. */
    fun indexGenres(kind: Kind) {
        if (!configured() || kind == Kind.LIVE) return
        if (genreJobs[kind]?.isActive == true) return
        genreJobs[kind] = bg.launch {
            val src = Repo.source ?: return@launch
            val items = runCatching { src.all(kind) }.getOrNull() ?: return@launch
            val cats = allCatalogs(kind).values
            val pending = items.filter { genresOf(it, cats) == null }
            if (pending.isEmpty()) return@launch
            val type = if (kind == Kind.SERIES) "tv" else "movie"
            val sem = Semaphore(2)
            val done = AtomicInteger(0)
            genreProgress.value = 0 to pending.size
            try {
                pending.map { item ->
                    async {
                        sem.withPermit {
                            kotlinx.coroutines.delay(400)
                            val (q, year) = clean(item.name)
                            if (q.isNotBlank()) {
                                val yp = year?.let { if (type == "tv") "&first_air_date_year=$it" else "&year=$it" } ?: ""
                                val r = get("/search/$type", "query=${enc(q)}&include_adult=false$yp")
                                if (r != null) {
                                    val g = r.optJSONArray("results")?.optJSONObject(0)?.optJSONArray("genre_ids")
                                    genreMeta[item.key] = (0 until (g?.length() ?: 0)).map { g!!.optInt(it) }
                                    genreDirty = true
                                }
                            }
                        }
                        val n = done.incrementAndGet()
                        if (n % 25 == 0 || n == pending.size) {
                            saveGenres()
                            genreProgress.value = n to pending.size
                            genreVersion.intValue++
                        }
                    }
                }.awaitAll()
            } finally {
                saveGenres()
                genreProgress.value = null
                genreVersion.intValue++
            }
        }
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
