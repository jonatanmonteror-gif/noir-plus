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

/** Valoración propia: 0,5 a 5 estrellas y comentario opcional. */
data class Rating(
    val key: String,
    val title: String,
    val image: String?,
    val stars: Float,
    val text: String,
    val time: Long,
    val item: Item?,
    val label: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("title", title).put("image", image ?: "").put("stars", stars.toDouble())
        .put("text", text).put("time", time).put("label", label).apply { item?.let { put("item", it.toJson()) } }

    companion object {
        fun fromJson(o: JSONObject) = Rating(
            key = o.optString("key"), title = o.optString("title"), image = o.str("image"),
            stars = o.optDouble("stars", 0.0).toFloat(), text = o.optString("text"), time = o.optLong("time"),
            item = o.optJSONObject("item")?.let { Item.fromJson(it) }, label = o.optString("label"),
        )
    }
}

const val SUB_MIN = 14
const val SUB_MAX = 26

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("noirplus", Context.MODE_PRIVATE)

    /** Se incrementa con cada cambio para que la interfaz se refresque. */
    val changes = mutableIntStateOf(0)
    private fun bump() { changes.intValue = changes.intValue + 1 }

    private fun s(k: String): String = sp.getString(k, "") ?: ""

    /** ¿Es una tele (Android TV / Google TV)? */
    val isTv: Boolean = runCatching {
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            ctx.packageManager.hasSystemFeature("android.software.leanback")
    }.getOrDefault(false)

    /** Equipo con poca memoria (muchas teles): se evitan tareas pesadas en segundo plano. */
    val isLowEnd: Boolean = isTv || runCatching {
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
    }.getOrDefault(false)

    /** Último cierre inesperado ya mostrado al usuario (para no repetir el aviso). */
    var lastCrashSeen: Long
        get() = sp.getLong("lastCrashSeen", 0L)
        set(v) { sp.edit().putLong("lastCrashSeen", v).apply() }

    /** Valoraciones visibles. Por defecto: sí en el móvil, no en la tele. */
    var ratingsEnabled: Boolean
        get() = if (sp.contains("ratingsVisible")) sp.getBoolean("ratingsVisible", true) else !isTv
        set(v) { sp.edit().putBoolean("ratingsVisible", v).apply(); bump() }

    val type: String get() = s("type")
    val server: String get() = s("server")
    val user: String get() = s("user")
    val pass: String get() = s("pass")
    val m3u: String get() = s("m3u")

    var liveFormat: String
        get() = s("liveFormat").ifEmpty { "m3u8" }
        set(v) { sp.edit().putString("liveFormat", v).apply(); bump() }

    fun hasAccount() = type.isNotEmpty()

    /** Última vez que se entró a la app (ms), para contar novedades. */
    var lastVisit: Long
        get() = sp.getLong("lastVisit", 0L)
        set(v) { sp.edit().putLong("lastVisit", v).apply() }

    /** Clave de TMDB (API key v3 o token de lectura v4) para saber en qué plataforma está cada título. */
    var tmdbKey: String
        get() = s("tmdbKey")
        set(v) { sp.edit().putString("tmdbKey", v.trim()).apply(); bump() }

    /** Accesos rápidos del reproductor (botones junto a la ruedita). */
    fun quick(k: String): Boolean = sp.getBoolean("quick_$k", false)
    fun setQuick(k: String, v: Boolean) { sp.edit().putBoolean("quick_$k", v).apply(); bump() }

    // ---------- Perfil ----------
    var name: String
        get() = s("name")
        set(v) { sp.edit().putString("name", v.trim()).apply(); bump() }

    // ---------- Subtítulos ----------
    /** Retardo en ms (−2000…+2000). Positivo = salen más tarde. */
    var subDelayMs: Int
        get() = sp.getInt("subDelay", 0)
        set(v) { sp.edit().putInt("subDelay", v.coerceIn(-2000, 2000)).apply(); bump() }

    /** 0 = abajo, 1 = centro, 2 = arriba */
    var subPos: Int
        get() = sp.getInt("subPos", 0)
        set(v) { sp.edit().putInt("subPos", v.coerceIn(0, 2)).apply(); bump() }

    /** Ajuste fino de altura en dp (−60…+60). Positivo = más arriba. */
    var subOffset: Int
        get() = sp.getInt("subOffset", 0)
        set(v) { sp.edit().putInt("subOffset", v.coerceIn(-60, 60)).apply(); bump() }

    /** 0 = pequeño, 1 = normal, 2 = grande */
    var subSize: Int
        get() = sp.getInt("subSize", 1)
        set(v) { sp.edit().putInt("subSize", v.coerceIn(0, 2)).apply(); bump() }

    /** Tamaño del texto en sp (14…26). Por defecto, según el antiguo pequeño/normal/grande. */
    var subSizeSp: Int
        get() = sp.getInt("subSizeSp", when (sp.getInt("subSize", 1)) { 0 -> 16; 2 -> 26; else -> 20 })
        set(v) { sp.edit().putInt("subSizeSp", v.coerceIn(SUB_MIN, SUB_MAX)).apply(); bump() }

    /** Tipografía: 0 Inter, 1 Sistema, 2 Clásica, 3 Playfair, 4 Mono */
    var subFont: Int
        get() = sp.getInt("subFont", 0)
        set(v) { sp.edit().putInt("subFont", v.coerceIn(0, 4)).apply(); bump() }

    /** 0 = sin fondo (con sombra), 1 = caja semitransparente */
    var subBg: Int
        get() = sp.getInt("subBg", 0)
        set(v) { sp.edit().putInt("subBg", v.coerceIn(0, 1)).apply(); bump() }

    // ---------- Reproducción ----------
    var autoNext: Boolean
        get() = sp.getBoolean("autoNext", true)
        set(v) { sp.edit().putBoolean("autoNext", v).apply(); bump() }

    /** Columnas en las cuadrículas del móvil (2 o 3). */
    /** Orden con el que se abren las carpetas ("Ver todo", "Todas"). Por defecto, lo más reciente primero. */
    var defaultSort: com.noirplus.tv.ui.SortMode
        get() = runCatching { com.noirplus.tv.ui.SortMode.valueOf(s("defaultSort")) }.getOrDefault(com.noirplus.tv.ui.SortMode.RECENT)
        set(v) { sp.edit().putString("defaultSort", v.name).apply(); bump() }

    var gridCols: Int
        get() = sp.getInt("gridCols", 3).coerceIn(2, 4)
        set(v) { sp.edit().putInt("gridCols", v.coerceIn(2, 4)).apply(); bump() }

    /** Cómo se ve Películas/Series: "plat" (por plataforma), "genre" (por género) o "folders" (carpetas del proveedor). */
    fun browseMode(kind: Kind): String = sp.getString("browse_${kind.name}", "folders") ?: "folders"

    /** Títulos por fila (el resto, en «Ver todo»). Fijo. */
    val rowLimit: Int get() = 20
    fun setBrowseMode(kind: Kind, v: String) { sp.edit().putString("browse_${kind.name}", v).apply(); bump() }

    // ---------- Valoraciones propias ----------
    fun ratings(): List<Rating> {
        val a = readArr("ratings")
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Rating.fromJson(it) } }.sortedByDescending { it.time }
    }

    fun rating(key: String): Rating? = ratings().firstOrNull { it.key == key }

    fun saveRating(r: Rating) {
        val list = ratings().filter { it.key != r.key }.toMutableList()
        list.add(0, r)
        val arr = JSONArray()
        list.take(500).forEach { arr.put(it.toJson()) }
        sp.edit().putString("ratings", arr.toString()).apply(); bump()
    }

    fun removeRating(key: String) {
        val arr = JSONArray()
        ratings().filter { it.key != key }.forEach { arr.put(it.toJson()) }
        sp.edit().putString("ratings", arr.toString()).apply(); bump()
    }

    // ---------- Carpetas ocultas y protegidas ----------
    private fun set(k: String): Set<String> = sp.getStringSet(k, emptySet())?.toSet() ?: emptySet()

    val hidden: Set<String> get() = set("hiddenCats")
    val locked: Set<String> get() = set("lockedCats")

    fun toggleHidden(key: String) {
        val cur = hidden.toMutableSet()
        if (!cur.add(key)) cur.remove(key)
        sp.edit().putStringSet("hiddenCats", cur).apply(); bump()
    }

    fun toggleLocked(key: String) {
        val cur = locked.toMutableSet()
        if (!cur.add(key)) cur.remove(key)
        sp.edit().putStringSet("lockedCats", cur).apply(); bump()
    }

    var pin: String
        get() = s("pin")
        set(v) { sp.edit().putString("pin", v).apply(); bump() }

    /** Desbloqueo del contenido protegido solo mientras la app está abierta. */
    var unlocked: Boolean = false
        set(v) { field = v; bump() }

    /** ¿Se debe mostrar esta carpeta? (ni oculta ni protegida sin desbloquear) */
    fun visible(kind: Kind, catId: String): Boolean {
        val k = "${kind.name}:$catId"
        if (k in hidden) return false
        if (k in locked && !(unlocked || pin.isEmpty())) return false
        return true
    }

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
