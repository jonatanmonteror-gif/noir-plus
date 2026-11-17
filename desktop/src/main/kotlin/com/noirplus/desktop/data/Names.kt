package com.noirplus.desktop.data


import java.util.Locale

/**
 * Nombres de carpetas más cuidados:
 * "SERIES NETFLIX" (en Series) → "Netflix"
 * "ESTRENOS 2025" → "Estrenos" + año "2025" (se muestra pequeño entre paréntesis)
 * Respeta marcas y siglas (HBO, Disney+, 4K…).
 */
data class PrettyName(val main: String, val year: String?)

private val ES = Locale("es", "ES")

private val prefixes = listOf(
    "series", "serie", "películas", "peliculas", "película", "pelicula", "pelis", "peli",
    "movies", "movie", "films", "film", "cine", "vod", "tv", "canales", "canal", "live",
)

/** Marcas y siglas con su forma correcta. */
private val brands = mapOf(
    "netflix" to "Netflix", "hbo" to "HBO", "max" to "Max", "disney" to "Disney", "disney+" to "Disney+",
    "apple" to "Apple", "appletv" to "Apple TV", "amazon" to "Amazon", "prime" to "Prime", "primevideo" to "Prime Video",
    "movistar" to "Movistar", "movistar+" to "Movistar+", "dazn" to "DAZN", "skyshowtime" to "SkyShowtime",
    "paramount" to "Paramount", "paramount+" to "Paramount+", "filmin" to "Filmin", "atresplayer" to "Atresplayer",
    "rtve" to "RTVE", "tve" to "TVE", "antena" to "Antena", "telecinco" to "Telecinco", "cuatro" to "Cuatro",
    "lasexta" to "laSexta", "marvel" to "Marvel", "dc" to "DC", "pixar" to "Pixar", "anime" to "Anime",
    "star" to "Star", "wars" to "Wars", "lgtb" to "LGTB", "lgtbi" to "LGTBI", "bbc" to "BBC", "cnn" to "CNN",
    "espn" to "ESPN", "nba" to "NBA", "nfl" to "NFL", "ufc" to "UFC", "f1" to "F1", "motogp" to "MotoGP",
    "laliga" to "LaLiga", "uefa" to "UEFA", "tdt" to "TDT", "usa" to "USA", "uk" to "UK", "es" to "ES",
    "latam" to "Latam", "4k" to "4K", "uhd" to "UHD", "fhd" to "FHD", "hd" to "HD", "sd" to "SD", "3d" to "3D",
    "hdr" to "HDR", "vose" to "VOSE", "vo" to "VO", "dual" to "Dual", "xxx" to "XXX", "tv" to "TV",
)

private val yearRegex = Regex("""[\(\[]?\b(19[5-9]\d|20[0-4]\d)\b[\)\]]?""")

fun prettyCategory(raw: String, kind: Kind? = null): PrettyName {
    var s = raw.trim()
    // Quitar prefijos de país/idioma típicos de los paneles: "ES|", "|ES|", "[ES]", "ES -", "ES:"
    s = s.replace(Regex("""^\s*[\|\[]?\s*(ES|ESP|SPA|EN|LAT)\s*[\|\]:\-]+\s*""", RegexOption.IGNORE_CASE), "")
    // Año
    val year = yearRegex.find(s)?.groupValues?.get(1)
    if (year != null) s = s.replace(yearRegex, " ")
    // Quitar "SERIES" / "PELÍCULAS"… del principio si ya estamos en esa sección (o en general si sobra)
    val words = s.split(Regex("""[\s\|\-:·_/]+""")).filter { it.isNotBlank() }.toMutableList()
    while (words.size > 1 && words.first().lowercase(ES) in prefixes) words.removeAt(0)
    // Si el nombre era solo el prefijo (p. ej. "SERIES"), lo dejamos tal cual
    if (words.isEmpty()) words.addAll(s.split(" ").filter { it.isNotBlank() })
    // Formato frase: primera letra en mayúscula, el resto en minúscula, salvo marcas y siglas
    val out = words.mapIndexed { i, w ->
        val low = w.lowercase(ES)
        brands[low] ?: if (i == 0) low.replaceFirstChar { it.titlecase(ES) } else low
    }.joinToString(" ").trim()
    return PrettyName(out.ifEmpty { raw.trim() }, year)
}

/** Orden de carpetas: primero "Estrenos <año actual>", luego lo del año actual, luego otros estrenos; el resto, como venga. */
fun List<Category>.newestFirst(): List<Category> {
    val y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
    fun score(name: String): Int {
        val n = normText(name)
        val est = n.contains("estreno") || n.contains("novedad")
        return when {
            est && n.contains(y) -> 0
            n.contains(y) -> 1
            est -> 2
            else -> 3
        }
    }
    return sortedBy { score(it.name) }
}

private val marks = Regex("\\p{Mn}+")
fun normText(s: String): String = marks.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "").lowercase()
