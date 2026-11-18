package com.noirplus.desktop.data

import org.json.JSONArray
import org.json.JSONObject

enum class Kind { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

data class Item(
    val id: String,
    val name: String,
    val image: String?,
    val kind: Kind,
    val categoryId: String = "",
    val rating: String? = null,
    val ext: String? = null,
    val url: String? = null,
    val added: Long = 0L,
    val plot: String? = null,
    val year: String? = null,
    val tmdb: String? = null,
) {
    val key: String get() = "${kind.name}:$id"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("image", image ?: "")
        .put("kind", kind.name)
        .put("cat", categoryId)
        .put("rating", rating ?: "")
        .put("ext", ext ?: "")
        .put("url", url ?: "")
        .put("plot", plot ?: "")
        .put("year", year ?: "")
        .put("tmdb", tmdb ?: "")
        .put("added", added)

    companion object {
        fun fromJson(o: JSONObject): Item = Item(
            id = o.optString("id"),
            name = o.optString("name"),
            image = o.str("image"),
            kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.MOVIE),
            categoryId = o.optString("cat"),
            rating = o.str("rating"),
            ext = o.str("ext"),
            url = o.str("url"),
            plot = o.str("plot"),
            year = o.str("year"),
            tmdb = o.str("tmdb"),
            added = o.optLong("added"),
        )
    }
}

data class Details(
    val plot: String?,
    val backdrop: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val year: String?,
    val rating: String?,
    val duration: String?,
    val tmdbId: String? = null,
    /** Fecha de estreno que da el proveedor (aaaa-mm-dd o similar). */
    val release: String? = null,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val image: String?,
    val plot: String?,
    val duration: String?,
    val url: String? = null,
)

data class SeriesDetails(val details: Details, val seasons: Map<Int, List<Episode>>)

data class PlayRequest(
    val url: String,
    val title: String,
    val key: String,
    val image: String?,
    val isLive: Boolean,
    val startMs: Long = 0L,
    val channels: List<Item> = emptyList(),
    val channelIndex: Int = 0,
    val parent: String? = null,
    /** Cola de episodios (para "siguiente episodio automático"). */
    val episodes: List<Episode> = emptyList(),
    val episodeIndex: Int = -1,
    val seriesName: String? = null,
)

/** Lee un texto de un JSON ignorando null, "null" y vacíos (los paneles Xtream mezclan tipos). */
fun JSONObject.str(k: String): String? {
    if (!has(k) || isNull(k)) return null
    val s = optString(k).trim()
    return if (s.isEmpty() || s == "null") null else s
}

/** backdrop_path puede venir como array o como texto. */
fun JSONObject.firstImage(k: String): String? = when (val v = opt(k)) {
    is JSONArray -> (0 until v.length()).asSequence()
        .map { v.optString(it).trim() }
        .firstOrNull { it.isNotEmpty() && it != "null" }
    is String -> v.trim().takeIf { it.isNotEmpty() && it != "null" }
    else -> null
}
