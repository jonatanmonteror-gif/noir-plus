package com.noirplus.desktop.data

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
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * Sincronización con tu propio servidor (sync.php en tu hosting): «Continuar viendo» y Mi lista
 * entre Windows, la tele y el móvil con la misma cuenta.
 */
object Sync {
    const val DEFAULT_URL = ""
    /** En Windows no hay valoraciones ni carpetas ocultas: solo se comparte lo que existe aquí. */
    val SECTIONS = listOf("progress", "favs")

    data class Device(val id: String, val name: String, val kind: String, val seen: Long, val blocked: Boolean)

    val status = mutableStateOf<String?>(null)
    val devices = mutableStateOf<List<Device>>(emptyList())
    val blocked = mutableStateOf(false)
    val version = mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing + CoroutineExceptionHandler { _, t -> ErrorLog.note("sync", t) })
    private val lock = Mutex()
    private var pending: Job? = null
    private val pendingDevices = JSONObject()

    val url: String get() = Store.str("syncUrl", DEFAULT_URL).trim()
    var enabled: Boolean
        get() = Store.bool("syncOn", true)
        set(v) { Store.putBool("syncOn", v); if (v) request(0) }

    fun section(sec: String): Boolean = Store.bool("sync_$sec", true)
    fun setSection(sec: String, v: Boolean) { Store.putBool("sync_$sec", v); if (v) request(0) }

    val deviceId: String
        get() = Store.str("syncDevice").ifEmpty { UUID.randomUUID().toString().also { Store.putStr("syncDevice", it) } }

    var deviceName: String
        get() = Store.str("syncName").ifBlank { (System.getenv("COMPUTERNAME") ?: "PC").lowercase().replaceFirstChar { it.uppercase() }.take(40) }
        set(v) { Store.putStr("syncName", v.trim().take(40)); request(0) }

    fun setUrl(v: String) {
        var u = v.trim()
        if (u.isNotEmpty() && !u.startsWith("http")) u = "https://$u"
        if (u.isNotEmpty() && !u.endsWith(".php")) u = u.trimEnd('/') + "/noir-sync/sync.php"
        Store.putStr("syncUrl", u)
        request(0)
    }

    private fun identity() = SyncCore.identity(Store.type, Store.server, Store.user, Store.pass, Store.m3u)
    fun active(): Boolean = enabled && url.isNotEmpty() && identity() != null

    private val local = object : SyncLocal {
        override fun snapshot(section: String): Map<String, Pair<JSONObject, Long?>> = when (section) {
            "progress" -> Store.history().associate { it.key to (it.toJson() to it.time) }
            "favs" -> Store.favorites().associate { it.key to (it.toJson() to null) }
            else -> emptyMap()
        }

        override fun apply(section: String, items: List<Pair<String, JSONObject>>) {
            when (section) {
                "progress" -> Store.replaceHistory(items.map { Progress.fromJson(it.second) })
                "favs" -> Store.replaceFavorites(items.map { Item.fromJson(it.second) })
            }
        }
    }

    fun setBlocked(id: String, block: Boolean) {
        pendingDevices.put(id, JSONObject().put("t", System.currentTimeMillis()).put("blocked", block))
        devices.value = devices.value.map { if (it.id == id) it.copy(blocked = block) else it }
        request(0)
    }

    fun request(delayMs: Long = 1500) {
        if (!active()) {
            if (enabled && url.isEmpty()) status.value = "Falta la dirección del servidor"
            return
        }
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            runCatching { syncNow() }.onFailure { status.value = "No se pudo sincronizar: ${it.message ?: "sin conexión"}" }
        }
    }

    private suspend fun syncNow() = lock.withLock {
        val (id, token) = identity() ?: return@withLock
        val sections = SECTIONS.filter { section(it) }
        val state = runCatching { JSONObject(Store.str("syncState", "{}")) }.getOrDefault(JSONObject())
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
                .header("X-Noir-Device-Kind", "windows")
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
            for (k in state.keys()) if (!newState.has(k)) newState.put(k, state.get(k))
            Store.putStr("syncState", newState.toString())
            status.value = "Sincronizado " + java.text.SimpleDateFormat("HH:mm").format(java.util.Date())
        } else {
            status.value = "Este aparato está excluido. Vuelve a incluirlo desde otro aparato."
        }
        version.intValue = version.intValue + 1
    }
}
