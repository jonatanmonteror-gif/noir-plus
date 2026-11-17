package com.noirplus.tv.data

import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * Sincronización con tu propio servidor (sync.php en tu hosting):
 * «Continuar viendo», Mi lista, valoraciones y algunos ajustes, entre tele, móvil y Windows con la misma cuenta.
 */
object Sync {
    /** Dirección por defecto del servidor (vacía: hay que escribirla en Ajustes). */
    const val DEFAULT_URL = ""

    data class Device(val id: String, val name: String, val kind: String, val seen: Long, val blocked: Boolean)

    /** Estado para la pantalla de Ajustes. */
    val status = mutableStateOf<String?>(null)
    val devices = mutableStateOf<List<Device>>(emptyList())
    val blocked = mutableStateOf(false)
    val version = mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, t -> ErrorLog.note("sync", t) })
    private val lock = Mutex()
    private var pending: Job? = null
    private val pendingDevices = JSONObject()

    private val prefs get() = Repo.prefs

    // ---------- Preferencias ----------
    val url: String get() = prefs.str("syncUrl", DEFAULT_URL).trim()
    var enabled: Boolean
        get() = prefs.bool("syncOn", true)
        set(v) { prefs.putBool("syncOn", v); if (v) request(0) }

    fun section(sec: String): Boolean = prefs.bool("sync_$sec", true)
    fun setSection(sec: String, v: Boolean) { prefs.putBool("sync_$sec", v); if (v) request(0) }

    val deviceId: String
        get() = prefs.str("syncDevice").ifEmpty { UUID.randomUUID().toString().also { prefs.putStr("syncDevice", it) } }

    val kind: String get() = if (prefs.isTv) "tv" else "movil"

    var deviceName: String
        get() = prefs.str("syncName").ifBlank { if (prefs.isTv) "Tele" else listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ").take(40) }
        set(v) { prefs.putStr("syncName", v.trim().take(40)); request(0) }

    fun setUrl(v: String) {
        var u = v.trim()
        if (u.isNotEmpty() && !u.startsWith("http")) u = "https://$u"
        if (u.isNotEmpty() && !u.endsWith(".php")) u = u.trimEnd('/') + "/noir-sync/sync.php"
        prefs.putStr("syncUrl", u)
        request(0)
    }

    fun active(): Boolean = enabled && url.isNotEmpty() && identity() != null

    private fun identity() = SyncCore.identity(prefs.type, prefs.server, prefs.user, prefs.pass, prefs.m3u)

    // ---------- Datos locales ----------
    private val local = object : SyncLocal {
        override fun snapshot(section: String): Map<String, Pair<JSONObject, Long?>> = when (section) {
            "progress" -> prefs.history().associate { it.key to (it.toJson() to it.time) }
            "favs" -> prefs.favorites().associate { it.key to (it.toJson() to null) }
            "ratings" -> prefs.ratings().associate { it.key to (it.toJson() to it.time) }
            "settings" -> {
                val m = mutableMapOf<String, Pair<JSONObject, Long?>>()
                fun add(k: String, v: Any) { m[k] = Pair(JSONObject().put("v", v), null) }
                if (prefs.has("defaultSort")) add("sort", prefs.str("defaultSort"))
                if (prefs.has("autoNext")) add("autoNext", prefs.autoNext)
                if (prefs.has("hiddenCats")) add("hidden", JSONArray(prefs.hidden.sorted()))
                if (prefs.has("lockedCats")) add("locked", JSONArray(prefs.locked.sorted()))
                if (prefs.has("pin")) add("pin", prefs.pin)
                m
            }
            else -> emptyMap()
        }

        override fun apply(section: String, items: List<Pair<String, JSONObject>>) {
            when (section) {
                "progress" -> prefs.replaceHistory(items.map { Progress.fromJson(it.second) })
                "favs" -> prefs.replaceFavorites(items.map { Item.fromJson(it.second) })
                "ratings" -> prefs.replaceRatings(items.map { Rating.fromJson(it.second) })
                "settings" -> for ((k, o) in items) {
                    fun set(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }.toSet()
                    when (k) {
                        "sort" -> o.optString("v").takeIf { it.isNotEmpty() }?.let { prefs.putStr("defaultSort", it) }
                        "autoNext" -> prefs.autoNext = o.optBoolean("v", true)
                        "hidden" -> prefs.putStrSet("hiddenCats", set(o.optJSONArray("v")))
                        "locked" -> prefs.putStrSet("lockedCats", set(o.optJSONArray("v")))
                        "pin" -> o.optString("v").let { if (it.isEmpty() || it.length == 4) prefs.pin = it }
                    }
                }
            }
        }
    }

    // ---------- Aparatos ----------
    fun setBlocked(id: String, block: Boolean) {
        pendingDevices.put(id, JSONObject().put("t", System.currentTimeMillis()).put("blocked", block))
        devices.value = devices.value.map { if (it.id == id) it.copy(blocked = block) else it }
        request(0)
    }

    // ---------- Sincronizar ----------
    /** Pide sincronizar dentro de un momento (varias peticiones seguidas se juntan en una). */
    fun request(delayMs: Long = 1500) {
        if (!active()) {
            if (enabled && url.isEmpty()) status.value = "Falta la dirección del servidor"
            return
        }
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            runCatching { syncNow() }.onFailure {
                status.value = "No se pudo sincronizar: ${it.message ?: "sin conexión"}"
            }
        }
    }

    private suspend fun syncNow() = lock.withLock {
        val (id, token) = identity() ?: return@withLock
        val sections = SyncCore.ALL.filter { section(it) }
        val state = runCatching { JSONObject(prefs.str("syncState", "{}")) }.getOrDefault(JSONObject())
        val out = SyncCore.buildOutgoing(local, sections, state, System.currentTimeMillis())
        val devChanges = JSONObject(pendingDevices.toString())
        if (devChanges.length() > 0) out.body.put("devices", devChanges)
        status.value = "Sincronizando…"

        val target = url
        val resp = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(target)
                .header("User-Agent", "NoirPlus")
                .header("X-Noir-App", "noirplus-sync-v1")
                .header("X-Noir-Id", id)
                .header("X-Noir-Token", token)
                .header("X-Noir-Device", deviceId)
                .header("X-Noir-Device-Name", URLEncoder.encode(deviceName, "UTF-8").replace("+", "%20"))
                .header("X-Noir-Device-Kind", kind)
                .post(out.body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            Http.client.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: ""
                if (r.code == 401) throw IllegalStateException("la cuenta está ligada a otra contraseña")
                if (!r.isSuccessful) throw IllegalStateException("el servidor respondió ${r.code}")
                JSONObject(txt)
            }
        }
        for (k in devChanges.keys()) pendingDevices.remove(k)

        val isBlocked = resp.optBoolean("blocked")
        blocked.value = isBlocked
        val dev = resp.optJSONObject("devices") ?: JSONObject()
        devices.value = dev.keys().asSequence().mapNotNull { k ->
            val o = dev.optJSONObject(k) ?: return@mapNotNull null
            Device(k, o.optString("name", "Aparato"), o.optString("kind"), o.optLong("seen") * 1000, o.optBoolean("blocked"))
        }.sortedByDescending { it.seen }.toList()

        if (!isBlocked) {
            val newState = SyncCore.applyIncoming(local, sections, resp, state, out)
            // Las secciones desactivadas conservan su estado anterior
            for (k in state.keys()) if (!newState.has(k)) newState.put(k, state.get(k))
            prefs.putStr("syncState", newState.toString())
            status.value = "Sincronizado " + java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "ES")).format(java.util.Date())
        } else {
            status.value = "Este aparato está excluido. Vuelve a incluirlo desde otro aparato."
        }
        version.intValue = version.intValue + 1
    }
}
