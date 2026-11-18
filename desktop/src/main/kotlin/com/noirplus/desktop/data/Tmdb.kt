package com.noirplus.desktop.data

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Plataformas y géneros (datos de TMDB). Cada día GitHub prepara un archivo pequeño por plataforma
 * (lo mismo que usa la app de Android) y aquí se cruza con tu lista: rápido y sin miles de consultas.
 */
object Tmdb {
    val platforms = listOf("Netflix", "Disney+", "Max", "Prime Video", "Apple TV+", "Movistar Plus+", "SkyShowtime", "Filmin")
    private val slugs = mapOf(
        "Netflix" to "netflix", "Disney+" to "disney", "Max" to "max", "Prime Video" to "prime", "Apple TV+" to "apple",
        "Movistar Plus+" to "movistar", "SkyShowtime" to "skyshowtime", "Filmin" to "filmin",
    )
    private const val URL = "https://github.com/jonatanmonteror-gif/noir-plus/releases/download/catalog/"
    private val dir by lazy { File(AppDirs.root, "tmdb").apply { mkdirs() } }

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

    /** [last]: fecha del último capítulo emitido (solo series; 0 si no se sabe). */
    class Entry(val date: Int, val genres: List<Int>, val last: Int = 0) { val year get() = date / 10000 }

    class Catalog(val entries: Map<String, Entry>, val titles: Map<String, List<String>>) {
        fun match(p: Prep): String? {
            p.tmdb?.let { if (entries.containsKey(it)) return it }
            val ids = titles[p.key] ?: return null
            val year = p.year ?: return ids.firstOrNull()
            return ids.firstOrNull { val e = entries[it]; e == null || e.year == 0 || kotlin.math.abs(e.year - year) <= 1 }
        }
    }

    /** Título de tu lista preparado para cruzarlo (se calcula una vez). */
    class Prep(val item: Item, val key: String, val year: Int?, val tmdb: String?)

    private val preps = ConcurrentHashMap<String, Prep>()
    fun prep(item: Item): Prep = preps.getOrPut(item.key + "|" + item.name) {
        val (q, y) = clean(item.name)
        Prep(item, titleKey(q), (item.year ?: y)?.take(4)?.toIntOrNull(), item.tmdb?.takeIf { it.isNotBlank() && it != "0" })
    }

    private val marks = Regex("\\p{Mn}+")
    fun titleKey(s: String): String =
        marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

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

    private val catalogs = ConcurrentHashMap<String, Catalog>()
    private val lock = Mutex()
    /** Sube cada vez que llega un catálogo (para reordenar y rellenar filas). */
    val version = mutableIntStateOf(0)

    private fun type(kind: Kind) = if (kind == Kind.SERIES) "tv" else "movie"

    /** Catálogo de una plataforma: archivo del día (se descarga como mucho una vez al día). */
    suspend fun catalog(kind: Kind, group: String): Catalog? {
        val id = type(kind) + ":" + group
        catalogs[id]?.let { return it }
        return lock.withLock {
            catalogs[id]?.let { return@withLock it }
            load(type(kind), group)?.also { catalogs[id] = it; version.intValue++ }
        }
    }

    private suspend fun load(type: String, group: String): Catalog? = withContext(Dispatchers.IO) {
        val slug = slugs[group] ?: return@withContext null
        val name = "cat-$type-$slug.tsv.gz"
        val f = File(dir, name)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 24L * 3600 * 1000
        if (!fresh) runCatching {
            val req = Request.Builder().url(URL + name).header("User-Agent", "NoirPlus-Desktop").build()
            Http.client.newCall(req).execute().use { r ->
                if (r.isSuccessful) {
                    val tmp = File(dir, "$name.tmp")
                    r.body?.byteStream()?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    if (tmp.length() > 0) { f.delete(); tmp.renameTo(f) }
                }
            }
        }
        if (!f.exists()) return@withContext null
        runCatching {
            val entries = HashMap<String, Entry>(8192)
            val titles = HashMap<String, MutableList<String>>(16384)
            java.util.zip.GZIPInputStream(f.inputStream()).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t')
                    if (p.size < 4) continue
                    val date = p[1].toIntOrNull() ?: 0
                    val genres = if (p[2].isEmpty()) emptyList() else p[2].split(',').mapNotNull { it.toIntOrNull() }
                    entries[p[0]] = Entry(date, genres, p.getOrNull(4)?.toIntOrNull() ?: 0)
                    if (p[3].isNotEmpty()) for (t in p[3].split('|')) titles.getOrPut(t) { ArrayList(1) }.add(p[0])
                }
            }
            Catalog(entries, titles)
        }.getOrNull()
    }

    // --- Logos de las plataformas (los prepara GitHub junto al catálogo) ---
    private var logos: Map<String, String>? = null
    private val logoLock = Mutex()
    suspend fun logo(group: String): String? {
        logos?.let { return it[group] }
        return logoLock.withLock {
            logos?.let { return@withLock it[group] }
            val f = File(dir, "logos.json")
            val m = withContext(Dispatchers.IO) {
                if (!(f.exists() && System.currentTimeMillis() - f.lastModified() < 7L * 24 * 3600 * 1000)) runCatching {
                    val req = Request.Builder().url(URL + "logos.json").header("User-Agent", "NoirPlus-Desktop").build()
                    Http.client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string()?.let { f.writeText(it) } }
                }
                runCatching { org.json.JSONObject(f.readText()).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }
                    .getOrDefault(emptyMap())
            }
            if (m.isNotEmpty()) logos = m
            m[group]
        }
    }

    suspend fun allCatalogs(kind: Kind): Map<String, Catalog> = coroutineScope {
        platforms.map { g -> async { g to catalog(kind, g) } }.awaitAll().mapNotNull { (g, c) -> c?.let { g to it } }.toMap()
    }

    private fun loaded(kind: Kind): List<Pair<String, Catalog>> {
        val t = type(kind) + ":"
        return catalogs.entries.filter { it.key.startsWith(t) }.map { it.key.removePrefix(t) to it.value }
    }

    /** Plataformas (de las ya cargadas) en las que está un título. */
    fun platformsOf(item: Item): List<String> {
        val p = prep(item)
        return loaded(item.kind).filter { it.second.match(p) != null }.map { it.first }
            .sortedBy { platforms.indexOf(it) }
    }

    /** Número de TMDB del título, si está en algún catálogo ya cargado. */
    fun tmdbIdOf(item: Item): String? {
        val p = prep(item)
        for ((_, c) in loaded(item.kind)) c.match(p)?.let { return it }
        return null
    }

    fun genresOf(item: Item): List<Int>? {
        val p = prep(item)
        for ((_, c) in loaded(item.kind)) c.match(p)?.let { id -> c.entries[id]?.genres?.let { return it } }
        return null
    }

    /** Fecha para «Últimos estrenos»: películas por su estreno; series por su último capítulo ya disponible. */
    fun releaseDate(item: Item): Int {
        val first = firstReleaseDate(item)
        return if (item.kind == Kind.SERIES) maxOf(first, lastEpisodeDate(item)) else first
    }

    /** Fecha de estreno de TMDB (aaaammdd); si no se conoce, el año del proveedor. */
    fun firstReleaseDate(item: Item): Int {
        val p = prep(item)
        var best = 0
        for ((_, c) in loaded(item.kind)) {
            val d = c.match(p)?.let { c.entries[it]?.date } ?: continue
            if (d > best) best = d
        }
        return if (best > 0) best else (p.year ?: 0) * 10000
    }

    /**
     * Series: fecha del último capítulo emitido que tu proveedor ya tiene (si salió ayer pero el proveedor no ha
     * actualizado la serie desde antes, cuenta el día de esa última actualización). 0 si no se sabe.
     */
    fun lastEpisodeDate(item: Item): Int {
        if (item.kind != Kind.SERIES) return 0
        val p = prep(item)
        var last = 0
        for ((_, c) in loaded(item.kind)) {
            val d = c.match(p)?.let { c.entries[it]?.last } ?: continue
            if (d > last) last = d
        }
        if (last == 0) return 0
        val t = item.added
        if (t <= 0) return last
        val ms = if (t > 100_000_000_000L) t else t * 1000
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val upd = c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH)
        return minOf(last, upd)
    }

    private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    fun fmtDay(t: Int): String? = fmt(t / 10000, t / 100 % 100, t % 100)
    private fun fmt(y: Int, m: Int, d: Int): String? = when {
        y < 1900 -> null
        m !in 1..12 -> "$y"
        d !in 1..31 -> "${MONTHS[m - 1]} $y"
        else -> "$d ${MONTHS[m - 1]} $y"
    }

    /** Para la ficha: «14 mar 2024», y en series con capítulos nuevos «2024 · Último capítulo: 12 nov 2026». */
    fun releaseLabel(item: Item, details: Details?): String? {
        val first = run {
            val t = runCatching { firstReleaseDate(item) }.getOrDefault(0)
            if (t % 10000 != 0) fmt(t / 10000, t / 100 % 100, t % 100)?.let { return@run it }
            details?.release?.let { r ->
                Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(r)?.let { m ->
                    val (y, mo, d) = m.destructured
                    fmt(y.toInt(), mo.toInt(), d.toInt())?.let { return@run it }
                }
            }
            (details?.year ?: item.year)?.takeIf { it.isNotBlank() }
        } ?: return null
        val last = lastEpisodeDate(item)
        return if (last > 0 && last > firstReleaseDate(item)) "$first · Último capítulo: " + (fmt(last / 10000, last / 100 % 100, last % 100) ?: "") else first
    }

    /** Lo más reciente primero (por fecha de estreno); a igualdad, lo último añadido. */
    fun byRelease(list: List<Item>): List<Item> =
        list.map { it to releaseDate(it) }
            .sortedWith(compareByDescending<Pair<Item, Int>> { it.second }.thenByDescending { it.first.added })
            .map { it.first }
}

/** Listas por plataforma / género / fuera de plataformas, a partir de tu propia lista. */
object Library {
    const val PLAT = "__plat:"
    const val GENRE = "__genre:"
    const val NO_PLAT = "__noplat"
    const val ALL = "__all"
    /** Lo último subido al servidor: "__new:desde-hasta" en días. */
    const val NEW = "__new:"

    fun isVirtual(id: String) = id.startsWith("__")
    fun isPlatform(id: String) = id.startsWith(PLAT) || id == NO_PLAT

    suspend fun items(kind: Kind, catId: String): List<Item> = withContext(Dispatchers.Default) {
        val src = Repo.source ?: return@withContext emptyList()
        if (kind == Kind.LIVE) return@withContext if (catId == ALL) src.all(kind) else src.items(kind, catId)
        when {
            catId.startsWith(PLAT) -> {
                val c = Tmdb.catalog(kind, catId.removePrefix(PLAT)) ?: return@withContext emptyList()
                src.all(kind).asSequence().map { Tmdb.prep(it) }
                    .mapNotNull { p -> c.match(p)?.let { _ -> p to Tmdb.releaseDate(p.item) } }
                    .distinctBy { it.first.key }.sortedByDescending { it.second }.map { it.first.item }.toList()
            }
            catId.startsWith(NEW) -> {
                val r = catId.removePrefix(NEW).split('-').map { it.toLongOrNull() ?: 0 }
                val now = System.currentTimeMillis() / 1000
                src.all(kind).filter { it.added in (now - r.getOrElse(1) { 7 } * 86_400)..(now - r.getOrElse(0) { 0 } * 86_400) }
                    .sortedByDescending { it.added }
            }
            catId == NO_PLAT -> {
                val cats = Tmdb.allCatalogs(kind).values
                Tmdb.byRelease(src.all(kind).filter { i -> val p = Tmdb.prep(i); cats.none { it.match(p) != null } })
            }
            catId.startsWith(GENRE) -> {
                val g = catId.removePrefix(GENRE).toIntOrNull() ?: return@withContext emptyList()
                Tmdb.allCatalogs(kind)
                Tmdb.byRelease(src.all(kind).filter { Tmdb.genresOf(it)?.contains(g) == true }.distinctBy { Tmdb.prep(it).key })
            }
            else -> {
                Tmdb.allCatalogs(kind)
                Tmdb.byRelease(if (catId == ALL) src.all(kind) else src.items(kind, catId))
            }
        }
    }
}
