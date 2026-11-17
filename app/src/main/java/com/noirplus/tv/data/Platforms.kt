package com.noirplus.tv.data

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * A qué plataforma pertenece una película o serie, deducido de las carpetas de tu proveedor:
 * - Si está en una carpeta de plataforma ("Netflix", "Disney+", "HBO Max"…), esa.
 * - Si no (p. ej. está en "Estrenos 2026"), se busca el mismo título dentro de las carpetas de plataforma.
 * No inventa: si el proveedor no la tiene en ninguna carpeta de plataforma, no se muestra nada.
 */
object Platforms {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""\bnetflix\b""") to "Netflix",
        Regex("""\bdisney""") to "Disney+",
        Regex("""\bhbo\b|\bmax\b""") to "Max",
        Regex("""\bprime\b|\bamazon\b""") to "Prime Video",
        Regex("""\bapple\b""") to "Apple TV+",
        Regex("""\bmovistar""") to "Movistar Plus+",
        Regex("""\bskyshowtime\b|\bsky\b""") to "SkyShowtime",
        Regex("""\bparamount""") to "Paramount+",
        Regex("""\bfilmin\b""") to "Filmin",
        Regex("""\batres""") to "Atresplayer",
        Regex("""\brtve\b""") to "RTVE Play",
        Regex("""\bcrunchyroll\b""") to "Crunchyroll",
        Regex("""\bdazn\b""") to "DAZN",
    )

    private val marks = Regex("\\p{Mn}+")
    private fun norm(s: String) = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    fun fromFolder(name: String?): String? {
        val n = norm(name ?: return null)
        return rules.firstOrNull { it.first.containsMatchIn(n) }?.second
    }

    /** Título "limpio" para comparar: sin año, calidad, idioma ni signos. */
    private fun key(title: String): String = norm(title)
        .replace(Regex("""[\(\[].*?[\)\]]"""), " ")
        .replace(Regex("""\b(19|20)\d{2}\b"""), " ")
        .replace(Regex("""\b(4k|uhd|fhd|hd|sd|hdr|vose|vo|dual|latino|castellano|esp|es)\b"""), " ")
        .replace(Regex("""^\s*[a-z]{2,3}\s*[\|:\-]\s*"""), " ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

    private val index = ConcurrentHashMap<Kind, Map<String, String>>()

    fun clear() {
        index.clear()
        Tmdb.clear()
    }

    /** Con clave de TMDB: plataformas reales en España. Sin clave: deducción por carpetas. */
    suspend fun providers(item: Item): List<Tmdb.Provider> {
        if (Tmdb.configured()) {
            val t = Tmdb.providers(item)
            if (t.isNotEmpty()) return t
        }
        return of(item)?.let { listOf(Tmdb.Provider(it, null)) } ?: emptyList()
    }

    suspend fun of(item: Item): String? {
        if (item.kind == Kind.LIVE) return null
        val src = Repo.source ?: return null
        return runCatching {
            val cats = src.categories(item.kind)
            fromFolder(cats.firstOrNull { it.id == item.categoryId }?.name)?.let { return it }
            val idx = index[item.kind] ?: run {
                val platformCats = cats.mapNotNull { c -> fromFolder(c.name)?.let { c.id to it } }.toMap()
                if (platformCats.isEmpty()) emptyMap() else {
                    val m = HashMap<String, String>()
                    for (x in src.all(item.kind)) {
                        val p = platformCats[x.categoryId] ?: continue
                        m.putIfAbsent(key(x.name), p)
                    }
                    m
                }
            }.also { index[item.kind] = it }
            idx[key(item.name)]
        }.getOrNull()
    }
}
