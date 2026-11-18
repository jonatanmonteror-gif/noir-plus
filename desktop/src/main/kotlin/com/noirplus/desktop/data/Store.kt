package com.noirplus.desktop.data

import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Dónde vive todo lo de Noir+ en el PC: %APPDATA%\NoirPlus */
object AppDirs {
    val root: File by lazy {
        val base = System.getenv("APPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".noirplus")
        File(base, "NoirPlus").apply { mkdirs() }
    }
}

/** Lo visto a medias, para "Continuar viendo". */
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
            o.optString("key"), o.optString("title"), o.str("image"), o.optString("url"),
            o.optLong("pos"), o.optLong("dur"), o.optLong("time"), o.str("parent"),
        )
    }
}

/**
 * Ajustes y datos de la app guardados en un archivo JSON del PC (no en el código).
 * Cada cambio sube [changes] para que la interfaz se refresque.
 */
object Store {
    private val file = File(AppDirs.root, "settings.json")
    private var o: JSONObject = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    val changes = mutableIntStateOf(0)

    @Synchronized
    private fun save() {
        runCatching {
            val tmp = File(file.parentFile, "settings.tmp")
            tmp.writeText(o.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
        changes.intValue++
    }

    private fun s(k: String) = o.optString(k, "")

    // ---------- Cuenta ----------
    val type: String get() = s("type")
    val server: String get() = s("server")
    val user: String get() = s("user")
    val pass: String get() = s("pass")
    val m3u: String get() = s("m3u")
    val hasAccount: Boolean get() = type == "xtream" || (type == "m3u" && m3u.isNotBlank())

    fun saveXtream(server: String, user: String, pass: String) {
        o.put("type", "xtream").put("server", server).put("user", user).put("pass", pass); save()
    }
    fun saveM3u(url: String) { o.put("type", "m3u").put("m3u", url); save() }
    fun logout() {
        listOf("type", "server", "user", "pass", "m3u", "favs", "history", "syncState").forEach { o.remove(it) }; save()
    }

    var name: String
        get() = s("name")
        set(v) { o.put("name", v.trim()); save() }

    /** Formato de los canales en directo: "ts" o "m3u8". */
    var liveFormat: String
        get() = s("liveFormat").ifBlank { "ts" }
        set(v) { o.put("liveFormat", v); save() }

    /** Tamaño de los subtítulos en el reproductor (escala de VLC). */
    var subScale: Int
        get() = o.optInt("subScale", 100)
        set(v) { o.put("subScale", v.coerceIn(30, 200)); save() }

    /** ¿Esta opción del reproductor va como botón en pantalla? (si no, va dentro de la ruedita). Por defecto, todo en la ruedita. */
    fun quick(k: String): Boolean = o.optJSONObject("quick")?.optBoolean(k, false) ?: false
    fun setQuick(k: String, v: Boolean) {
        val q = o.optJSONObject("quick") ?: JSONObject().also { o.put("quick", it) }
        q.put(k, v); save()
    }

    /** Ventana: "full" (pantalla completa sin barra de título, por defecto), "immersive" (oculta también la barra de Windows) o "window". */
    var windowMode: String
        get() = s("windowMode").ifBlank { "full" }
        set(v) { o.put("windowMode", v); save() }

    // ---------- Subtítulos (los dibuja VLC con estas opciones) ----------
    var subFont: String
        get() = s("subFont").takeIf { it in listOf("Segoe UI", "Inter", "Atkinson Hyperlegible", "Verdana", "Arial", "Georgia") } ?: "Segoe UI"
        set(v) { o.put("subFont", v); save() }
    /** Color del texto en RGB. */
    var subColor: Int
        get() = o.optInt("subColor", 0xFFFFFF)
        set(v) { o.put("subColor", v); save() }
    /** Fondo: 0 sin fondo, 1 sombra, 2 caja semitransparente. */
    var subBg: Int
        get() = o.optInt("subBg", 1)
        set(v) { o.put("subBg", v.coerceIn(0, 2)); save() }
    /** Posición: 0 abajo, 1 centro, 2 arriba. */
    var subPos: Int
        get() = o.optInt("subPos", 0)
        set(v) { o.put("subPos", v.coerceIn(0, 2)); save() }
    /** Ajuste fino de altura (píxeles del vídeo). */
    var subMargin: Int
        get() = o.optInt("subMargin", 40)
        set(v) { o.put("subMargin", v.coerceIn(0, 600)); save() }
    /** Contorno: 0 ninguno, 1 fino, 2 normal, 3 grueso. */
    var subOutline: Int
        get() = o.optInt("subOutline", 2)
        set(v) { o.put("subOutline", v.coerceIn(0, 3)); save() }
    /** Retardo por defecto en milisegundos. */
    var subDelayMs: Int
        get() = o.optInt("subDelayMs", 0)
        set(v) { o.put("subDelayMs", v.coerceIn(-5000, 5000)); save() }
    /** Versión del estilo: sube con cada cambio para volver a preparar el reproductor. */
    val subStyleKey: String get() = "$subScale|$subFont|$subColor|$subBg|$subMargin|$subOutline|$subPos"

    /** Vista de Películas/Series: "plat" (plataformas, por defecto), "genre" o "folders". */
    fun browseMode(kind: Kind): String = s("browse_${kind.name}").takeIf { it == "genre" || it == "new" } ?: "plat"
    fun setBrowseMode(kind: Kind, v: String) { o.put("browse_${kind.name}", v); save() }

    var lastCrashSeen: Long
        get() = o.optLong("lastCrashSeen", 0L)
        set(v) { o.put("lastCrashSeen", v); save() }

    var volume: Int
        get() = o.optInt("volume", 100)
        set(v) { o.put("volume", v.coerceIn(0, 150)); o.toString().let { runCatching { file.writeText(it) } } }

    // ---------- Continuar viendo ----------
    fun history(): List<Progress> {
        val a = o.optJSONArray("history") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Progress::fromJson) }
            .filter { it.duration <= 0 || it.fraction < 0.95f }
            .sortedByDescending { it.time }
    }

    fun progress(key: String): Progress? = history().firstOrNull { it.key == key }

    fun saveProgress(p: Progress, notify: Boolean = false) {
        val list = history().filter { it.key != p.key && (p.parent == null || it.parent != p.parent) }.toMutableList()
        if (p.duration <= 0 || p.fraction < 0.95f) list.add(0, p)
        o.put("history", JSONArray(list.take(60).map { it.toJson() }))
        if (notify) save() else runCatching { file.writeText(o.toString()) }
    }

    fun removeProgress(key: String) {
        o.put("history", JSONArray(history().filter { it.key != key }.map { it.toJson() })); save()
        Sync.request()
    }

    // ---------- Mi lista ----------
    fun favorites(): List<Item> {
        val a = o.optJSONArray("favs") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Item::fromJson) }
    }
    fun isFavorite(item: Item) = favorites().any { it.key == item.key }
    fun toggleFavorite(item: Item) {
        val l = favorites().toMutableList()
        if (l.removeAll { it.key == item.key }.not()) l.add(0, item)
        o.put("favs", JSONArray(l.map { it.toJson() })); save()
        Sync.request()
    }

    // ---------- Canales por temática ----------
    fun liveThemes(): Map<String, String> = o.optJSONObject("liveThemes")?.let { j -> j.keys().asSequence().associateWith { j.getString(it) } } ?: emptyMap()
    fun setLiveTheme(channelKey: String, theme: String) {
        val j = o.optJSONObject("liveThemes") ?: JSONObject().also { o.put("liveThemes", it) }
        j.put(channelKey, theme); save()
    }
    var liveView: String
        get() = o.optString("liveViewMode", "list")
        set(v) { o.put("liveViewMode", v); save() }

    // ---------- Sincronización ----------
    fun str(k: String, def: String = ""): String = o.optString(k, def)
    fun putStr(k: String, v: String) { o.put(k, v); save() }
    fun bool(k: String, def: Boolean): Boolean = o.optBoolean(k, def)
    fun putBool(k: String, v: Boolean) { o.put(k, v); save() }
    fun replaceHistory(list: List<Progress>) { o.put("history", JSONArray(list.take(60).map { it.toJson() })); save() }
    fun replaceFavorites(list: List<Item>) { o.put("favs", JSONArray(list.map { it.toJson() })); save() }
}
