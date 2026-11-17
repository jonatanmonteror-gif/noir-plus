package com.noirplus.desktop.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Núcleo de la sincronización (igual en Android y en Windows).
 *
 * Cada sección (progress, favs, ratings, settings) es un mapa clave → valor.
 * Se guarda un «estado» con la huella y la marca de tiempo de cada elemento tal como quedó tras la última
 * sincronización. Comparando con lo que hay ahora se sabe qué ha cambiado o se ha borrado en este aparato.
 * El servidor mezcla por elemento: gana la marca de tiempo más reciente.
 */
interface SyncLocal {
    /** Valores actuales de una sección. El Long es la marca de tiempo propia del elemento, si la tiene. */
    fun snapshot(section: String): Map<String, Pair<JSONObject, Long?>>

    /** Sustituye la sección por estos elementos (ordenados del más reciente al más antiguo). */
    fun apply(section: String, items: List<Pair<String, JSONObject>>)
}

object SyncCore {
    val ALL = listOf("progress", "favs", "ratings", "settings")

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun normServer(s: String) = s.trim().trimEnd('/').lowercase()

    /** Identificador de la cuenta (no contiene la contraseña) y clave secreta (sí la incluye, solo como huella). */
    fun identity(type: String, server: String, user: String, pass: String, m3u: String): Pair<String, String>? = when {
        type == "xtream" && server.isNotBlank() && user.isNotBlank() ->
            sha256("noir|${normServer(server)}|${user.trim()}") to sha256("noir-token|${normServer(server)}|${user.trim()}|$pass")
        type == "m3u" && m3u.isNotBlank() ->
            sha256("noir|m3u|${m3u.trim()}") to sha256("noir-token|m3u|${m3u.trim()}")
        else -> null
    }

    /** JSON con las claves ordenadas: la misma información da siempre la misma huella. */
    fun canon(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { k -> JSONObject.quote(k) + ":" + canon(v.opt(k)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
        is String -> JSONObject.quote(v)
        is Number -> if (v.toDouble() == Math.floor(v.toDouble()) && !v.toDouble().isInfinite()) v.toLong().toString() else v.toString()
        else -> JSONObject.quote(v.toString())
    }

    private fun hash(v: JSONObject) = sha256(canon(v)).take(16)

    /** Lo que hay que mandar al servidor y las huellas con las que se ha calculado. */
    class Outgoing(val body: JSONObject, val sentHashes: Map<String, Map<String, String>>)

    fun buildOutgoing(local: SyncLocal, sections: List<String>, state: JSONObject, now: Long): Outgoing {
        val body = JSONObject()
        val sent = mutableMapOf<String, Map<String, String>>()
        for (sec in sections) {
            val cur = local.snapshot(sec)
            val st = state.optJSONObject(sec) ?: JSONObject()
            val out = JSONObject()
            val hashes = mutableMapOf<String, String>()
            for ((k, pair) in cur) {
                val (v, own) = pair
                val h = hash(v)
                hashes[k] = h
                val old = st.optJSONObject(k)
                if (old == null || old.optBoolean("d") || old.optString("h") != h) {
                    val oldT = old?.optLong("t") ?: 0L
                    val t = maxOf(own ?: now, oldT + 1)
                    out.put(k, JSONObject().put("t", t).put("v", v))
                }
            }
            for (k in st.keys().asSequence().toList()) {
                val old = st.optJSONObject(k) ?: continue
                if (old.optBoolean("d") || cur.containsKey(k)) continue
                out.put(k, JSONObject().put("t", maxOf(now, old.optLong("t") + 1)).put("d", true))
            }
            sent[sec] = hashes
            if (out.length() > 0) body.put(sec, out)
        }
        return Outgoing(body, sent)
    }

    /**
     * Aplica lo que devuelve el servidor y devuelve el nuevo estado.
     * Si algo cambió en este aparato mientras se sincronizaba, se respeta lo local y se mandará la próxima vez.
     */
    fun applyIncoming(local: SyncLocal, sections: List<String>, resp: JSONObject, oldState: JSONObject, sent: Outgoing): JSONObject {
        val newState = JSONObject()
        for (sec in sections) {
            val srv = resp.optJSONObject(sec) ?: JSONObject()
            val before = sent.sentHashes[sec] ?: emptyMap()
            val nowLocal = local.snapshot(sec)
            val dirty = mutableSetOf<String>()
            for ((k, p) in nowLocal) if (before[k] != hash(p.first)) dirty += k
            for (k in before.keys) if (!nowLocal.containsKey(k)) dirty += k

            val list = mutableListOf<Triple<String, JSONObject, Long>>()
            for (k in srv.keys().asSequence().toList()) {
                if (k in dirty) continue
                val e = srv.optJSONObject(k) ?: continue
                if (e.optBoolean("d")) continue
                val v = e.optJSONObject("v") ?: continue
                list += Triple(k, v, e.optLong("t"))
            }
            for (k in dirty) nowLocal[k]?.let { (v, own) -> list += Triple(k, v, own ?: Long.MAX_VALUE) }
            list.sortByDescending { it.third }
            local.apply(sec, list.map { it.first to it.second })

            // Estado nuevo: huella de lo que realmente ha quedado guardado aquí
            val after = local.snapshot(sec)
            val st = JSONObject()
            val prevSt = oldState.optJSONObject(sec) ?: JSONObject()
            val cutoff = System.currentTimeMillis() - 90L * 86400_000L
            for (k in srv.keys().asSequence().toList()) {
                if (k in dirty) { prevSt.optJSONObject(k)?.let { st.put(k, it) }; continue }
                val e = srv.optJSONObject(k) ?: continue
                val t = e.optLong("t")
                if (e.optBoolean("d")) {
                    if (t > cutoff) st.put(k, JSONObject().put("t", t).put("d", true))
                } else {
                    val v = after[k]?.first
                    st.put(k, JSONObject().put("t", t).put("h", if (v != null) hash(v) else ""))
                }
            }
            for (k in dirty) prevSt.optJSONObject(k)?.let { st.put(k, it) }
            newState.put(sec, st)
        }
        return newState
    }
}
