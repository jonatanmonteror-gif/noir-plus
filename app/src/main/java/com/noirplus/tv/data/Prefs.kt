package com.noirplus.tv.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

data class Progress(
    val key: String,
    val title: String,
    val image: String?,
    val url: String,
    val position: Long,
    val duration: Long,
    val time: Long,
    val parent: String? = null,
) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("title", title).put("image", image ?: "").put("url", url)
        .put("pos", position).put("dur", duration).put("time", time).put("parent", parent ?: "")

    companion object {
        fun fromJson(o: JSONObject) = Progress(
            key = o.optString("key"), title = o.optString("title"), image = o.str("image"),
            url = o.optString("url"), position = o.optLong("pos"), duration = o.optLong("dur"),
            time = o.optLong("time"), parent = o.str("parent"),
        )
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("noirplus", Context.MODE_PRIVATE)

    /** Se incrementa con cada cambio para que la interfaz se refresque. */
    val changes = mutableIntStateOf(0)
    private fun bump() { changes.intValue = changes.intValue + 1 }

    private fun s(k: String): String = sp.getString(k, "") ?: ""

    val type: String get() = s("type")
    val server: String get() = s("server")
    val user: String get() = s("user")
    val pass: String get() = s("pass")
    val m3u: String get() = s("m3u")

    var liveFormat: String
        get() = s("liveFormat").ifEmpty { "m3u8" }
        set(v) { sp.edit().putString("liveFormat", v).apply(); bump() }

    fun hasAccount() = type.isNotEmpty()

    fun saveXtream(server: String, user: String, pass: String) {
        sp.edit().putString("type", "xtream").putString("server", server)
            .putString("user", user).putString("pass", pass).apply()
        bump()
    }

    fun saveM3u(url: String) {
        sp.edit().putString("type", "m3u").putString("m3u", url).apply()
        bump()
    }

    fun clearAccount() {
        sp.edit().remove("type").remove("favs").remove("history").apply()
        bump()
    }

    private fun readArr(k: String): JSONArray =
        runCatching { JSONArray(sp.getString(k, "[]") ?: "[]") }.getOrDefault(JSONArray())

    // ---------- Favoritos ("Mi lista") ----------
    fun favorites(): List<Item> {
        val a = readArr("favs")
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Item.fromJson(it) } }
    }

    fun isFavorite(key: String) = favorites().any { it.key == key }

    fun toggleFavorite(item: Item): Boolean {
        val list = favorites().toMutableList()
        val existed = list.removeAll { it.key == item.key }
        if (!existed) list.add(0, item)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("favs", arr.toString()).apply()
        bump()
        return !existed
    }

    // ---------- Continuar viendo ----------
    fun history(): List<Progress> {
        val a = readArr("history")
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Progress.fromJson(it) } }
            .sortedByDescending { it.time }
    }

    fun progress(key: String): Progress? = history().firstOrNull { it.key == key }
    fun lastOf(parent: String): Progress? = history().firstOrNull { it.parent == parent }

    private fun writeHistory(list: List<Progress>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("history", arr.toString()).apply()
        bump()
    }

    fun saveProgress(p: Progress) {
        val list = history().filter { it.key != p.key && (p.parent == null || it.parent != p.parent) }.toMutableList()
        list.add(0, p)
        writeHistory(list.take(60))
    }

    fun removeProgress(key: String) = writeHistory(history().filter { it.key != key })
    fun clearHistory() = writeHistory(emptyList())
}
