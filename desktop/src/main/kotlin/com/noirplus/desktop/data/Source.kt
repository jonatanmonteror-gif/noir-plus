package com.noirplus.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Origen del contenido: un panel Xtream Codes o una lista M3U. */
interface Source {
    suspend fun categories(kind: Kind): List<Category>
    suspend fun items(kind: Kind, categoryId: String): List<Item>
    suspend fun all(kind: Kind): List<Item>
    suspend fun movieDetails(item: Item): Details?
    suspend fun seriesDetails(item: Item): SeriesDetails?
    suspend fun accountInfo(): String?
    fun streamUrl(item: Item): String
    fun episodeUrl(ep: Episode): String

    /** Olvida lo descargado (de un tipo o de todo) para volver a pedirlo al servidor. */
    fun clear(kind: Kind?)
}

fun normalizeServer(s: String): String {
    var b = s.trim().trimEnd('/')
    if (!b.startsWith("http://", true) && !b.startsWith("https://", true)) b = "http://$b"
    b = b.removeSuffix("/player_api.php").removeSuffix("/get.php").trimEnd('/')
    return b
}

/** Si pegan un enlace tipo get.php?username=..&password=.. lo convertimos a Xtream (da más datos). */
fun xtreamFromM3u(url: String): Triple<String, String, String>? {
    val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return null
    if (u.path?.endsWith("get.php") != true) return null
    val q = (u.rawQuery ?: "").split("&").mapNotNull {
        val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }.toMap()
    val user = q["username"] ?: return null
    val pass = q["password"] ?: return null
    val port = if (u.port > 0) ":${u.port}" else ""
    return Triple("${u.scheme}://${u.host}$port", user, pass)
}

/**
 * Prueba el login; si el servidor se escribió con https:// pero ese puerto no usa cifrado
 * (error TLS/SSL), reintenta con http://. Devuelve la dirección que ha funcionado.
 */
suspend fun xtreamLoginAuto(server: String, user: String, pass: String): String {
    val base = normalizeServer(server)
    try {
        XtreamSource(base, user, pass).login()
        return base
    } catch (e: Exception) {
        val tls = e is javax.net.ssl.SSLException ||
            (e.message ?: "").contains("TLS", true) || (e.message ?: "").contains("SSL", true)
        if (base.startsWith("https://", true) && tls) {
            val alt = "http://" + base.substring(8)
            XtreamSource(alt, user, pass).login()
            return alt
        }
        throw e
    }
}

/** Traduce errores técnicos de red a algo entendible. */
fun friendlyError(t: Throwable): String {
    val m = t.message ?: ""
    return when {
        t is java.net.UnknownHostException -> "No se encuentra el servidor. Revisa la dirección."
        t is java.net.SocketTimeoutException -> "El servidor no responde. Revisa la dirección y el puerto."
        t is java.net.ConnectException -> "No se puede conectar con el servidor. Revisa el puerto."
        t is javax.net.ssl.SSLException || m.contains("TLS", true) -> "Error de conexión segura. Prueba a poner http:// en lugar de https://."
        m.isNotBlank() -> m
        else -> "No se ha podido conectar"
    }
}

class XtreamSource(
    server: String,
    private val user: String,
    private val pass: String,
    private val liveExt: () -> String = { "m3u8" },
) : Source {
    val base: String = normalizeServer(server)

    private val cats = ConcurrentHashMap<Kind, List<Category>>()
    private val itemsCache = ConcurrentHashMap<String, List<Item>>()
    private val allCache = ConcurrentHashMap<Kind, List<Item>>()
    private val movies = ConcurrentHashMap<String, Details>()
    private val series = ConcurrentHashMap<String, SeriesDetails>()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun api(params: String) =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}$params"

    suspend fun login(): JSONObject {
        val text = Http.get(api(""))
        val o = runCatching { JSONObject(text) }.getOrNull()
            ?: throw Exception("El servidor no responde como un panel Xtream. Revisa la URL y el puerto.")
        val ui = o.optJSONObject("user_info") ?: throw Exception("Respuesta no válida del servidor")
        if (ui.optString("auth") != "1") throw Exception("Usuario o contraseña incorrectos")
        return o
    }

    override suspend fun accountInfo(): String? = runCatching {
        val ui = login().getJSONObject("user_info")
        val exp = ui.str("exp_date")?.toLongOrNull()
        val expTxt = exp?.let { SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(it * 1000)) } ?: "sin caducidad"
        "Estado: ${ui.str("status") ?: "-"}  ·  Caduca: $expTxt  ·  Conexiones: ${ui.str("max_connections") ?: "-"}"
    }.getOrNull()

    private fun catAction(k: Kind) = when (k) {
        Kind.LIVE -> "get_live_categories"
        Kind.MOVIE -> "get_vod_categories"
        Kind.SERIES -> "get_series_categories"
    }

    private fun listAction(k: Kind) = when (k) {
        Kind.LIVE -> "get_live_streams"
        Kind.MOVIE -> "get_vod_streams"
        Kind.SERIES -> "get_series"
    }

    override suspend fun categories(kind: Kind): List<Category> {
        cats[kind]?.let { return it }
        val text = Http.get(api("&action=${catAction(kind)}"))
        val list = withContext(Dispatchers.Default) {
            val arr = runCatching { JSONArray(text) }.getOrNull() ?: JSONArray()
            val out = ArrayList<Category>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.str("category_id") ?: continue
                out.add(Category(id, o.str("category_name") ?: "Sin nombre"))
            }
            out
        }
        cats[kind] = list
        return list
    }

    private fun parseItems(kind: Kind, text: String): List<Item> {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            when (kind) {
                Kind.LIVE -> {
                    val id = o.str("stream_id") ?: continue
                    out.add(Item(id = id, name = o.str("name") ?: "Canal", image = o.str("stream_icon"), kind = kind,
                        categoryId = o.str("category_id") ?: ""))
                }
                Kind.MOVIE -> {
                    val id = o.str("stream_id") ?: continue
                    out.add(Item(id = id, name = o.str("name") ?: "Película", image = o.str("stream_icon") ?: o.str("cover"),
                        kind = kind, categoryId = o.str("category_id") ?: "", rating = o.str("rating"),
                        ext = o.str("container_extension"), added = o.str("added")?.toLongOrNull() ?: 0L, year = o.str("year"),
                        tmdb = (o.str("tmdb") ?: o.str("tmdb_id"))?.takeIf { it != "0" }))
                }
                Kind.SERIES -> {
                    val id = o.str("series_id") ?: continue
                    out.add(Item(id = id, name = o.str("name") ?: "Serie", image = o.str("cover"), kind = kind,
                        categoryId = o.str("category_id") ?: "", rating = o.str("rating"),
                        added = o.str("last_modified")?.toLongOrNull() ?: 0L, plot = o.str("plot"),
                        year = (o.str("releaseDate") ?: o.str("release_date") ?: o.str("year"))?.take(4),
                        tmdb = (o.str("tmdb") ?: o.str("tmdb_id"))?.takeIf { it != "0" }))
                }
            }
        }
        return out
    }

    override suspend fun items(kind: Kind, categoryId: String): List<Item> {
        allCache[kind]?.let { all -> return all.filter { it.categoryId == categoryId } }
        val key = "${kind.name}:$categoryId"
        itemsCache[key]?.let { return it }
        val text = Http.get(api("&action=${listAction(kind)}&category_id=${enc(categoryId)}"))
        val list = withContext(Dispatchers.Default) { parseItems(kind, text) }
        itemsCache[key] = list
        return list
    }

    override suspend fun all(kind: Kind): List<Item> {
        allCache[kind]?.let { return it }
        val text = Http.get(api("&action=${listAction(kind)}"))
        val list = withContext(Dispatchers.Default) { parseItems(kind, text) }
        allCache[kind] = list
        return list
    }

    override suspend fun movieDetails(item: Item): Details? {
        if (item.url != null) return null
        movies[item.id]?.let { return it }
        val text = Http.get(api("&action=get_vod_info&vod_id=${item.id}"))
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val info = o.optJSONObject("info") ?: return null
        val d = Details(
            plot = info.str("plot") ?: info.str("description"),
            backdrop = info.firstImage("backdrop_path") ?: info.str("cover_big") ?: info.str("movie_image"),
            genre = info.str("genre"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            year = (info.str("releasedate") ?: info.str("release_date"))?.take(4) ?: item.year,
            rating = info.str("rating") ?: item.rating,
            duration = info.str("duration"),
            tmdbId = info.str("tmdb_id") ?: info.str("tmdb") ?: o.optJSONObject("movie_data")?.str("tmdb_id"),
        )
        movies[item.id] = d
        return d
    }

    override suspend fun seriesDetails(item: Item): SeriesDetails? {
        if (item.url != null) return null
        series[item.id]?.let { return it }
        val text = Http.get(api("&action=get_series_info&series_id=${item.id}"))
        val sd = withContext(Dispatchers.Default) { parseSeries(item, text) } ?: return null
        series[item.id] = sd
        return sd
    }

    private fun parseSeries(item: Item, text: String): SeriesDetails? {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val info = o.optJSONObject("info") ?: JSONObject()
        val seasons = sortedMapOf<Int, MutableList<Episode>>()

        fun addArr(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.str("id") ?: continue
                val s = e.str("season")?.toIntOrNull() ?: 1
                val inf = e.optJSONObject("info")
                seasons.getOrPut(s) { mutableListOf() }.add(
                    Episode(
                        id = id,
                        title = e.str("title") ?: "Episodio ${i + 1}",
                        season = s,
                        number = e.str("episode_num")?.toIntOrNull() ?: (i + 1),
                        ext = e.str("container_extension") ?: "mp4",
                        image = inf?.str("movie_image"),
                        plot = inf?.str("plot"),
                        duration = inf?.str("duration"),
                    )
                )
            }
        }

        when (val eps = o.opt("episodes")) {
            is JSONObject -> {
                val keys = eps.keys()
                while (keys.hasNext()) {
                    eps.optJSONArray(keys.next())?.let { addArr(it) }
                }
            }
            is JSONArray -> for (i in 0 until eps.length()) eps.optJSONArray(i)?.let { addArr(it) }
        }

        val details = Details(
            plot = info.str("plot") ?: item.plot,
            backdrop = info.firstImage("backdrop_path") ?: info.str("cover") ?: item.image,
            genre = info.str("genre"),
            cast = info.str("cast"),
            director = info.str("director"),
            year = (info.str("releaseDate") ?: info.str("release_date"))?.take(4) ?: item.year,
            rating = info.str("rating") ?: item.rating,
            duration = null,
            tmdbId = info.str("tmdb") ?: info.str("tmdb_id"),
        )
        val sorted = LinkedHashMap<Int, List<Episode>>()
        for ((k, v) in seasons) sorted[k] = v.sortedBy { it.number }
        return SeriesDetails(details, sorted)
    }

    override fun streamUrl(item: Item): String = item.url ?: when (item.kind) {
        Kind.LIVE -> "$base/live/$user/$pass/${item.id}.${liveExt()}"
        Kind.MOVIE -> "$base/movie/$user/$pass/${item.id}.${item.ext ?: "mp4"}"
        Kind.SERIES -> ""
    }

    override fun episodeUrl(ep: Episode): String = ep.url ?: "$base/series/$user/$pass/${ep.id}.${ep.ext}"

    override fun clear(kind: Kind?) {
        if (kind == null) {
            cats.clear(); itemsCache.clear(); allCache.clear(); movies.clear(); series.clear()
            return
        }
        cats.remove(kind)
        allCache.remove(kind)
        itemsCache.keys.filter { it.startsWith("${kind.name}:") }.forEach { itemsCache.remove(it) }
        if (kind == Kind.MOVIE) movies.clear()
        if (kind == Kind.SERIES) series.clear()
    }
}

class M3uSource(private val url: String) : Source {
    @Volatile
    private var data: Map<Kind, List<Item>>? = null

    private suspend fun load(): Map<Kind, List<Item>> {
        data?.let { return it }
        val text = Http.get(url.trim())
        if (!text.contains("#EXTINF")) throw Exception("La URL no contiene una lista M3U válida")
        val parsed = withContext(Dispatchers.Default) { parse(text) }
        data = parsed
        return parsed
    }

    private fun parse(text: String): Map<Kind, List<Item>> {
        val attr = Regex("([\\w-]+)=\"([^\"]*)\"")
        val out = HashMap<Kind, ArrayList<Item>>()
        for (k in Kind.entries) out[k] = ArrayList()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var n = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                val q = line.lastIndexOf('"')
                val c = line.indexOf(',', if (q >= 0) q else 0)
                name = if (c >= 0) line.substring(c + 1).trim() else attrs["tvg-name"]
                logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                group = attrs["group-title"]?.takeIf { it.isNotBlank() }
            } else if (line.isNotEmpty() && !line.startsWith("#") && name != null) {
                val lower = line.lowercase()
                val path = lower.substringBefore('?')
                val kind = when {
                    "/movie/" in lower -> Kind.MOVIE
                    "/series/" in lower -> Kind.SERIES
                    path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") -> Kind.MOVIE
                    else -> Kind.LIVE
                }
                out[kind]?.add(
                    Item(id = (n++).toString(), name = name ?: "Sin nombre", image = logo, kind = kind,
                        categoryId = group ?: "Sin categoría", url = line)
                )
                name = null
            }
        }
        return out
    }

    override suspend fun categories(kind: Kind): List<Category> =
        (load()[kind] ?: emptyList()).map { it.categoryId }.distinct().map { Category(it, it) }

    override suspend fun items(kind: Kind, categoryId: String): List<Item> =
        (load()[kind] ?: emptyList()).filter { it.categoryId == categoryId }

    override suspend fun all(kind: Kind): List<Item> = load()[kind] ?: emptyList()
    override suspend fun movieDetails(item: Item): Details? = null
    override suspend fun seriesDetails(item: Item): SeriesDetails? = null
    override suspend fun accountInfo(): String? = "Lista M3U"
    override fun streamUrl(item: Item): String = item.url ?: ""
    override fun episodeUrl(ep: Episode): String = ep.url ?: ""

    // La lista M3U es un único archivo: actualizar cualquier parte la vuelve a descargar entera.
    override fun clear(kind: Kind?) {
        data = null
    }
}
