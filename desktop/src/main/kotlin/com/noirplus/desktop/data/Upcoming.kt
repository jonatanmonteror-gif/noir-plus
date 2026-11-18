package com.noirplus.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.Calendar

/**
 * Próximos estrenos (90 días): películas, series nuevas, temporadas y capítulos, preparados cada noche en GitHub.
 * Los capítulos sueltos solo se enseñan de las series que sigues (Mi lista o Continuar viendo).
 */
object Upcoming {
    private const val URL = "https://github.com/jonatanmonteror-gif/noir-plus/releases/download/catalog/upcoming.json.gz"

    data class Up(
        val type: String,        // "tv" | "movie"
        val id: String,
        val name: String,
        val date: Int,           // aaaammdd (0 en series ya emitidas sin próximo capítulo)
        val kind: String,        // "new" | "season" | "episode" | "film" | "aired"
        val season: Int, val episode: Int, val epName: String,
        val platform: String,    // "Netflix"… o ""
        val where: String,       // películas: "cines" | "digital"
        val poster: String?,
        val keys: List<String>,
        val last: Int, val lastS: Int, val lastE: Int,
        val pop: Double = 0.0, val lang: String = "",
        val backdrop: String? = null, val overview: String = "",
    ) {
        val key: String get() = "$type:$id"
    }

    data class Shown(val up: Up, val local: Item?, val available: Boolean)

    private var cache: List<Up>? = null
    private var loadedAt = 0L
    private val lock = Mutex()

    suspend fun all(): List<Up> = lock.withLock {
        cache?.takeIf { System.currentTimeMillis() - loadedAt < 6 * 3600_000L }?.let { return@withLock it }
        val list = withContext(Dispatchers.IO) { runCatching { read() }.onFailure { ErrorLog.note("proximos", it) }.getOrNull() }
        if (list != null) { cache = list; loadedAt = System.currentTimeMillis() }
        cache ?: emptyList()
    }

    private fun read(): List<Up> {
        val dir = File(AppDirs.root, "tmdb").apply { mkdirs() }
        val f = File(dir, "upcoming.json.gz")
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 12 * 3600_000L) runCatching {
            val req = Request.Builder().url(URL).header("User-Agent", "NoirPlus-Desktop").build()
            Http.client.newCall(req).execute().use { r ->
                if (r.isSuccessful) {
                    val tmp = File(dir, "upcoming.tmp")
                    r.body?.byteStream()?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    if (tmp.length() > 0) { f.delete(); tmp.renameTo(f) }
                }
            }
        }
        if (!f.exists()) return emptyList()
        val txt = java.util.zip.GZIPInputStream(f.inputStream()).bufferedReader().readText()
        val arr = JSONArray(txt)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ks = o.optJSONArray("keys")
            Up(
                o.optString("t"), o.optString("id"), o.optString("n"), o.optInt("d"), o.optString("k"),
                o.optInt("s"), o.optInt("e"), o.optString("en"), o.optString("p"), o.optString("w"),
                o.optString("img").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w185$it" },
                (0 until (ks?.length() ?: 0)).map { ks!!.optString(it) },
                o.optInt("last"), o.optInt("ls"), o.optInt("le"),
                o.optDouble("pop", 0.0), o.optString("lang"),
                o.optString("bd").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }, o.optString("ov"),
            )
        }
    }

    fun today(): Int = day(System.currentTimeMillis())
    fun day(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
    private fun providerDay(t: Long): Int = if (t <= 0) 0 else day(if (t > 100_000_000_000L) t else t * 1000)

    /** Tu lista indexada para saber si un estreno ya lo tiene tu proveedor. */
    private class Local(val byKey: Map<String, Item>, val byTmdb: Map<String, Item>)

    private fun index(items: List<Item>): Local {
        val k = HashMap<String, Item>(); val t = HashMap<String, Item>()
        for (it in items) {
            val p = Tmdb.prep(it)
            k.putIfAbsent(p.key, it)
            p.tmdb?.let { id -> t.putIfAbsent(id, it) }
        }
        return Local(k, t)
    }

    private fun find(up: Up, l: Local): Item? = l.byTmdb[up.id] ?: up.keys.firstNotNullOfOrNull { l.byKey[it] }

    /** Series que sigues: Mi lista y Continuar viendo. */
    private fun followedKeys(): Set<String> {
        val prefs = Store
        val fav = prefs.favorites().filter { it.kind == Kind.SERIES }.map { it.key }
        val hist = prefs.history().mapNotNull { it.parent?.takeIf { p -> p.startsWith("SERIES:") } }
        return (fav + hist).toSet()
    }

    /** Lista para la sección «Próximos», ya filtrada y con lo que ya está disponible en tu proveedor. */
    suspend fun shown(): List<Shown> = withContext(Dispatchers.Default) {
        val src = Repo.source ?: return@withContext emptyList()
        val ups = all()
        val series = index(runCatching { src.all(Kind.SERIES) }.getOrDefault(emptyList()))
        val movies = index(runCatching { src.all(Kind.MOVIE) }.getOrDefault(emptyList()))
        val follow = followedKeys()
        val t = today()
        val since = day(System.currentTimeMillis() - 14L * 86400_000L)
        // Películas: solo estrenos en plataformas (lo de cines no se sube a la lista)
        ups.filter { it.kind != "aired" && it.date >= since && !(it.type == "movie" && it.where == "cines") }.mapNotNull { up ->
            val local = if (up.type == "tv") find(up, series) else find(up, movies)
            val followed = local != null && local.key in follow
            if (up.kind == "episode" && !followed) return@mapNotNull null
            // Sin ruido: lo de otras cadenas o idiomas poco vistos solo si lo sigues o es popular
            if (!followed && !relevant(up)) return@mapNotNull null
            val avail = when {
                local == null || up.date > t -> false
                up.type == "movie" -> true
                else -> providerDay(local.added) >= up.date
            }
            Shown(up, local, avail)
        }
    }

    /** Aviso: capítulos nuevos de series que sigues que ya ha subido tu proveedor (y aún no te hemos avisado). */
    suspend fun newEpisodes(): List<Pair<Up, Item>> = withContext(Dispatchers.Default) {
        val src = Repo.source ?: return@withContext emptyList()
        val follow = followedKeys()
        if (follow.isEmpty()) return@withContext emptyList()
        val series = index(runCatching { src.all(Kind.SERIES) }.getOrDefault(emptyList()))
        val seen = Store.strSet("upNotified")
        val t = today()
        val limit = day(System.currentTimeMillis() - 10L * 86400_000L)
        all().filter { it.type == "tv" && it.last in limit..t }.mapNotNull { up ->
            val local = find(up, series) ?: return@mapNotNull null
            if (local.key !in follow) return@mapNotNull null
            if ("${up.id}:${up.last}" in seen) return@mapNotNull null
            if (providerDay(local.added) < up.last) return@mapNotNull null
            up to local
        }.distinctBy { it.first.id }
    }

    // ---------- Recordatorios: «Avísame cuando esté» ----------
    /** Clave de un estreno concreto (la película, o esa temporada/capítulo). */
    fun remindKey(up: Up) = "${up.type}:${up.id}:${up.season}:${up.episode}"

    private fun upToJson(up: Up) = org.json.JSONObject().put("t", up.type).put("id", up.id).put("n", up.name).put("d", up.date)
        .put("k", up.kind).put("s", up.season).put("e", up.episode).put("p", up.platform).put("img", up.poster ?: "").put("bd", up.backdrop ?: "")
        .put("keys", JSONArray(up.keys))

    private fun upFromJson(o: org.json.JSONObject): Up {
        val ks = o.optJSONArray("keys")
        return Up(o.optString("t"), o.optString("id"), o.optString("n"), o.optInt("d"), o.optString("k"), o.optInt("s"), o.optInt("e"), "",
            o.optString("p"), "", o.optString("img").takeIf { it.isNotBlank() }, (0 until (ks?.length() ?: 0)).map { ks!!.optString(it) }, 0, 0, 0,
            backdrop = o.optString("bd").takeIf { it.isNotBlank() })
    }

    fun reminders(): List<Up> = runCatching {
        val a = JSONArray(Store.str("upReminders", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::upFromJson) }
    }.getOrDefault(emptyList())

    fun isReminded(up: Up): Boolean = reminders().any { remindKey(it) == remindKey(up) }

    /** Activa o quita el aviso. Devuelve true si queda activado. */
    fun toggleReminder(up: Up): Boolean {
        val cur = reminders().toMutableList()
        val on = cur.removeAll { remindKey(it) == remindKey(up) }.not()
        if (on) cur += up
        Store.putStr("upReminders", JSONArray(cur.takeLast(200).map(::upToJson)).toString())
        return on
    }

    /** Recordatorios que ya ha subido tu proveedor. */
    suspend fun remindersReady(): List<Pair<Up, Item>> = withContext(Dispatchers.Default) {
        val src = Repo.source ?: return@withContext emptyList()
        val rem = reminders()
        if (rem.isEmpty()) return@withContext emptyList()
        val series = index(runCatching { src.all(Kind.SERIES) }.getOrDefault(emptyList()))
        val movies = index(runCatching { src.all(Kind.MOVIE) }.getOrDefault(emptyList()))
        val t = today()
        rem.mapNotNull { up ->
            if (up.date > t) return@mapNotNull null
            val local = (if (up.type == "tv") find(up, series) else find(up, movies)) ?: return@mapNotNull null
            if (up.type == "tv" && providerDay(local.added) < up.date) return@mapNotNull null
            up to local
        }
    }

    private val LANGS = setOf("es", "en", "ca", "eu", "gl")
    private fun relevant(up: Up): Boolean = when {
        up.type == "tv" && up.platform.isBlank() -> up.pop >= 60
        up.lang in LANGS -> true
        else -> up.pop >= 25
    }

    fun markNotified(up: Up) {
        val cur = Store.strSet("upNotified").toMutableSet()
        cur += "${up.id}:${up.last}"
        Store.putStrSet("upNotified", cur.toList().takeLast(300).toSet())
    }

    fun label(up: Up): String = when (up.kind) {
        "new" -> "Serie nueva"
        "season" -> "Temporada ${up.season}"
        "episode" -> "T${up.season} · E${up.episode}" + (up.epName.takeIf { it.isNotBlank() && !it.startsWith("Episodio") }?.let { " · $it" } ?: "")
        // Películas: dónde se estrena
        else -> when {
            up.where == "cines" -> "Estreno en cines"
            up.platform.isNotBlank() -> "Estreno en ${up.platform}"
            else -> "Estreno digital"
        }
    }
}
