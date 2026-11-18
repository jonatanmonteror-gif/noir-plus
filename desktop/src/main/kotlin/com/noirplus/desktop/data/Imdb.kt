package com.noirplus.desktop.data

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Nota de IMDb.
 * - Las notas salen de los datos públicos de IMDb (title.ratings.tsv.gz, uso personal y no comercial):
 *   se descargan directamente de IMDb a tu PC una vez por semana.
 * - Para saber qué título de IMDb es cada uno se usa TMDB (su ficha guarda el código de IMDb).
 */
object Imdb {
    private const val TMDB_KEY = "b5a63bce5d9e66d0a925f9736bd5d403"
    private const val DATASET = "https://datasets.imdbws.com/title.ratings.tsv.gz"
    private val dir by lazy { File(AppDirs.root, "imdb").apply { mkdirs() } }

    data class Score(val rating: Float, val votes: Int) {
        val votesText: String
            get() = when {
                votes >= 1_000_000 -> String.format("%.1f M votos", votes / 1_000_000f)
                votes >= 1_000 -> "${votes / 1000} mil votos"
                else -> "$votes votos"
            }
    }

    /** Sube cuando llegan notas nuevas (para refrescar las tarjetas). */
    val version = mutableIntStateOf(0)

    // ---------- Tabla de notas (ordenada por número de título) ----------
    private var ids: IntArray? = null
    private var ratings: ByteArray? = null
    private var votes: IntArray? = null
    private val tableLock = Mutex()
    private var tableTried = 0L

    private suspend fun ensureTable(): Boolean {
        if (ids != null) return true
        if (System.currentTimeMillis() - tableTried < 10 * 60_000L) return false
        return tableLock.withLock {
            if (ids != null) return@withLock true
            tableTried = System.currentTimeMillis()
            withContext(Dispatchers.IO) { runCatching { loadTable() }.onFailure { ErrorLog.note("imdb-tabla", it) } }
            ids != null
        }
    }

    private fun loadTable() {
        val bin = File(dir, "ratings.bin")
        val week = 7L * 24 * 3600 * 1000
        if (!bin.exists() || System.currentTimeMillis() - bin.lastModified() > week) {
            runCatching { download(bin) }.onFailure { ErrorLog.note("imdb-descarga", it) }
        }
        if (!bin.exists()) return
        DataInputStream(bin.inputStream().buffered(1 shl 16)).use { i ->
            val n = i.readInt()
            val a = IntArray(n); val r = ByteArray(n); val v = IntArray(n)
            for (k in 0 until n) { a[k] = i.readInt(); r[k] = i.readByte(); v[k] = i.readInt() }
            ids = a; ratings = r; votes = v
        }
        version.intValue++
    }

    private fun download(bin: File) {
        val req = Request.Builder().url(DATASET).header("User-Agent", "NoirPlus-Desktop").build()
        val a = IntArrayList(); val r = ArrayList<Byte>(1 shl 20); val v = IntArrayList()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("IMDb respondió ${resp.code}")
            java.util.zip.GZIPInputStream(resp.body!!.byteStream()).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("tt")) continue
                    val t1 = line.indexOf('\t'); val t2 = line.indexOf('\t', t1 + 1)
                    if (t1 < 0 || t2 < 0) continue
                    val id = line.substring(2, t1).toIntOrNull() ?: continue
                    val rt = line.substring(t1 + 1, t2).toFloatOrNull() ?: continue
                    val vt = line.substring(t2 + 1).trim().toIntOrNull() ?: continue
                    a.add(id); r.add((rt * 10).toInt().toByte()); v.add(vt)
                }
            }
        }
        if (a.size < 1000) throw IllegalStateException("datos de IMDb incompletos")
        // Por si acaso no vienen ordenados
        val order = (0 until a.size).sortedBy { a[it] }
        val tmp = File(dir, "ratings.tmp")
        DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { o ->
            o.writeInt(a.size)
            for (k in order) { o.writeInt(a[k]); o.writeByte(r[k].toInt()); o.writeInt(v[k]) }
        }
        bin.delete(); tmp.renameTo(bin)
    }

    private class IntArrayList {
        var data = IntArray(1 shl 20); var size = 0
        fun add(x: Int) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = x }
        operator fun get(i: Int) = data[i]
    }

    private fun lookup(imdb: String): Score? {
        val a = ids ?: return null
        val n = imdb.removePrefix("tt").toIntOrNull() ?: return null
        val k = java.util.Arrays.binarySearch(a, n)
        if (k < 0) return null
        return Score((ratings!![k].toInt() and 0xFF) / 10f, votes!![k])
    }

    // ---------- Título de tu lista → código de IMDb (guardado en el PC) ----------
    private val mapFile by lazy { File(dir, "map.tsv") }
    private val map: ConcurrentHashMap<String, String> by lazy {
        val m = ConcurrentHashMap<String, String>()
        runCatching {
            if (mapFile.exists()) mapFile.forEachLine { l -> val p = l.split('\t'); if (p.size == 2) m[p[0]] = p[1] }
        }
        m
    }

    private fun remember(key: String, imdb: String) {
        map[key] = imdb
        runCatching { synchronized(this) { mapFile.appendText("$key\t$imdb\n") } }
    }

    private val api = Semaphore(4)

    private fun tmdbJson(path: String, params: Map<String, String> = emptyMap()): JSONObject? {
        val b = "https://api.themoviedb.org/3/$path".toHttpUrl().newBuilder().addQueryParameter("api_key", TMDB_KEY)
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        val req = Request.Builder().url(b.build()).header("User-Agent", "NoirPlus-Desktop").build()
        return Http.client.newCall(req).execute().use { r -> if (r.isSuccessful) JSONObject(r.body!!.string()) else null }
    }

    /** Nota ya conocida, sin hacer consultas (para las tarjetas). */
    fun cached(item: Item): Score? {
        if (item.kind == Kind.LIVE) return null
        val imdb = map[item.key]?.takeIf { it.startsWith("tt") } ?: return null
        return lookup(imdb)
    }

    /** Nota de IMDb de un título (consulta TMDB solo la primera vez). */
    suspend fun score(item: Item, tmdbHint: String? = null): Score? {
        if (item.kind == Kind.LIVE) return null
        if (!ensureTable()) return null
        val known = map[item.key]
        if (known != null) return if (known.startsWith("tt")) lookup(known) else null
        val imdb = withContext(Dispatchers.IO) {
            api.withPermit { runCatching { findImdb(item, tmdbHint) }.getOrNull() }
        } ?: return null
        remember(item.key, imdb)
        version.intValue++
        return if (imdb.startsWith("tt")) lookup(imdb) else null
    }

    /** Tráiler en español (de España si lo hay) en YouTube; si no hay, una búsqueda en YouTube. */
    suspend fun trailerUrl(type: String, id: String, title: String): String = withContext(Dispatchers.IO) {
        val best = runCatching {
            val arr = tmdbJson("$type/$id/videos", mapOf("include_video_language" to "es"))?.optJSONArray("results")
            (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it) }
                .filter { it.optString("site") == "YouTube" && it.optString("iso_639_1") == "es" && it.optString("key").isNotBlank() }
                .minByOrNull {
                    (if (it.optString("iso_3166_1") == "ES") 0 else 10) +
                        when (it.optString("type")) { "Trailer" -> 0; "Teaser" -> 3; else -> 6 } + (if (it.optBoolean("official")) 0 else 1)
                }
        }.getOrNull()
        best?.let { "https://www.youtube.com/watch?v=" + it.optString("key") }
            ?: ("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("$title tráiler español", "UTF-8"))
    }

    private val datesCache = ConcurrentHashMap<String, Pair<Int, Int>>()

    /** Películas: (estreno en cines en España, estreno en plataformas/digital en España), aaaammdd o 0. */
    suspend fun movieDates(item: Item, tmdbHint: String?): Pair<Int, Int>? {
        if (item.kind != Kind.MOVIE) return null
        datesCache[item.key]?.let { return it }
        return withContext(Dispatchers.IO) {
            api.withPermit {
                runCatching {
                    val p = Tmdb.prep(item)
                    var id = tmdbHint?.takeIf { it.isNotBlank() && it != "0" } ?: p.tmdb ?: Tmdb.tmdbIdOf(item)
                    if (id == null) {
                        val (q, _) = Tmdb.clean(item.name)
                        val params = mutableMapOf("query" to q)
                        p.year?.let { params["year"] = it.toString() }
                        id = tmdbJson("search/movie", params)?.optJSONArray("results")?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }?.toString()
                    }
                    val arr = id?.let { tmdbJson("movie/$it/release_dates")?.optJSONArray("results") } ?: return@runCatching null
                    var cine = 0; var digital = 0
                    for (i in 0 until arr.length()) {
                        val c = arr.optJSONObject(i) ?: continue
                        if (c.optString("iso_3166_1") != "ES") continue
                        val ds = c.optJSONArray("release_dates") ?: continue
                        for (k in 0 until ds.length()) {
                            val r = ds.optJSONObject(k) ?: continue
                            val d = r.optString("release_date").take(10).replace("-", "").toIntOrNull() ?: continue
                            when (r.optInt("type")) {
                                2, 3 -> if (cine == 0 || d < cine) cine = d
                                4, 6 -> if (digital == 0 || d < digital) digital = d
                            }
                        }
                    }
                    (cine to digital).also { datesCache[item.key] = it }
                }.getOrNull()
            }
        }
    }

    private fun findImdb(item: Item, tmdbHint: String?): String {
        val type = if (item.kind == Kind.SERIES) "tv" else "movie"
        val p = Tmdb.prep(item)
        var id = (tmdbHint?.takeIf { it.isNotBlank() && it != "0" } ?: p.tmdb ?: Tmdb.tmdbIdOf(item))
        if (id == null) {
            val (q, _) = Tmdb.clean(item.name)
            val params = mutableMapOf("query" to q, "language" to "es-ES")
            p.year?.let { params[if (type == "tv") "first_air_date_year" else "year"] = it.toString() }
            val res = tmdbJson("search/$type", params)?.optJSONArray("results")
            id = res?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }?.toString()
        }
        if (id == null) return "-"
        val ext = tmdbJson("$type/$id/external_ids") ?: throw IllegalStateException("TMDB sin respuesta")
        return ext.optString("imdb_id").takeIf { it.startsWith("tt") } ?: "-"
    }
}
