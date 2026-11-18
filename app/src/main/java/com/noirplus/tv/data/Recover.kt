package com.noirplus.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.Normalizer

/**
 * Cuando el proveedor responde 404 a una película o episodio (lo ha vuelto a subir con otro número o en otro
 * formato), buscamos la dirección nueva: primero otros formatos del mismo archivo y después el mismo título en la
 * lista recién descargada.
 */
object Recover {
    private val EXTS = listOf("mp4", "mkv", "avi", "m4v", "mov", "ts", "m3u8")

    /** ¿Responde el servidor a esta dirección? (pide solo el primer byte) */
    suspend fun alive(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).header("User-Agent", Http.UA).header("Range", "bytes=0-0").build()
            Http.client.newCall(req).execute().use { it.code in 200..299 }
        }.getOrDefault(false)
    }

    fun norm(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return n.replace(Regex("""\[[^]]*]|\([^)]*\)|\|.*?\|"""), " ")
            .replace(Regex("""^\s*[a-z]{2,4}\s*[-:|]\s+"""), " ")
            .replace(Regex("""\b(4k|uhd|fhd|hd|sd|1080p|720p|2160p|multi|dual|latino|castellano|vose?)\b"""), " ")
            .replace(Regex("""\b(19|20)\d\d\b"""), " ")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun yearOf(s: String?): String? = s?.let { Regex("""(19|20)\d\d""").find(it)?.value }

    /** Misma película o serie en una lista: mismo nombre limpio y, si se sabe, mismo año. */
    private fun find(list: List<Item>, name: String, year: String?): Item? {
        val n = norm(name)
        if (n.isEmpty()) return null
        val same = list.filter { norm(it.name) == n }
        if (same.isEmpty()) return null
        val y = year ?: yearOf(name)
        return same.firstOrNull { y != null && (it.year == y || yearOf(it.name) == y) } ?: same.first()
    }

    /** Otros formatos del mismo archivo (…/123.mkv en vez de …/123.mp4). */
    private suspend fun otherExt(url: String): String? {
        val m = Regex("""^(.*/\d+)\.([A-Za-z0-9]{2,4})(\?.*)?$""").find(url) ?: return null
        val (base, ext) = m.destructured
        for (e in EXTS) if (!e.equals(ext, true)) {
            val u = "$base.$e"
            if (alive(u)) return u
        }
        return null
    }

    /**
     * Dirección nueva para lo que ha dado 404, o null si no aparece.
     * [key] es «MOVIE:id» o «EP:id»; [parent] la clave de la serie; [title] lo que se reproduce.
     */
    suspend fun newUrl(src: Source, url: String, key: String, title: String, parent: String?, seriesName: String?,
                       episode: Episode?): String? {
        otherExt(url)?.let { return it }
        return runCatching {
            when {
                key.startsWith("MOVIE:") -> {
                    src.clear(Kind.MOVIE)
                    val n = norm(title)
                    val y = yearOf(title)
                    val all = src.all(Kind.MOVIE).filter { norm(it.name) == n }
                    // Primero las del mismo año; luego el resto con el mismo nombre
                    val cands = all.sortedBy { if (y != null && (it.year == y || yearOf(it.name) == y)) 0 else 1 }
                    val hit = cands.filter { src.streamUrl(it) != url }.take(4).firstOrNull { alive(src.streamUrl(it)) }
                        ?: return@runCatching null
                    Repo.prefs.replaceFavorite(key, hit)
                    Repo.dataVersion.intValue = Repo.dataVersion.intValue + 1
                    src.streamUrl(hit)
                }
                key.startsWith("EP:") && episode != null -> {
                    val sid = parent?.removePrefix("SERIES:")
                    src.clear(Kind.SERIES)
                    val all = src.all(Kind.SERIES)
                    val show = all.firstOrNull { it.id == sid }?.takeIf { seriesName == null || norm(it.name) == norm(seriesName) }
                        ?: seriesName?.let { find(all, it, null) } ?: all.firstOrNull { it.id == sid } ?: return@runCatching null
                    val sd = src.seriesDetails(show) ?: return@runCatching null
                    val ep = sd.seasons[episode.season]?.firstOrNull { it.number == episode.number } ?: return@runCatching null
                    val u = src.episodeUrl(ep)
                    if (u != url && alive(u)) {
                        if (show.key != parent && parent != null) Repo.prefs.replaceFavorite(parent, show)
                        Repo.dataVersion.intValue = Repo.dataVersion.intValue + 1
                        u
                    } else null
                }
                else -> null
            }
        }.getOrNull()
    }
}
