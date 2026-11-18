package com.noirplus.desktop.data

import com.noirplus.desktop.BuildInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/**
 * Actualizaciones desde GitHub: busca la última versión "windows-v1.0.N", descarga el instalador,
 * comprueba su huella SHA-256 y lo ejecuta (se instala encima de la versión actual).
 */
object Updater {
    private const val REPO = "jonatanmonteror-gif/noir-plus"

    data class Release(val version: String, val exeUrl: String, val shaUrl: String?)

    private fun num(v: String) = v.substringAfterLast('.').toIntOrNull() ?: 0

    data class Notes(val version: String, val date: String, val items: List<String>) {
        val code: Int get() = version.substringAfterLast('.').toIntOrNull() ?: 0
    }

    val currentCode: Int get() = num(BuildInfo.VERSION)

    /** Historial de novedades de Windows (descarga directa de la versión fija "windows"). */
    suspend fun changelog(): List<Notes> = runCatching {
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("https://github.com/$REPO/releases/download/windows/changelog-windows.json")
                .header("User-Agent", "NoirPlus-Desktop").build()
            val txt = Http.client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null } ?: "[]"
            val arr = JSONArray(txt)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val it = o.optJSONArray("items") ?: return@mapNotNull null
                Notes(o.optString("v"), o.optString("date"), (0 until it.length()).map { k -> it.optString(k) })
            }
        }
    }.getOrDefault(emptyList())

    /** Primero la versión fija "windows" (descarga directa, sin límite de consultas); si no existe, la API. */
    suspend fun check(): Release? {
        val fixed = runCatching { checkFixed() }
        return if (fixed.isSuccess) fixed.getOrNull() else checkApi()
    }

    /** null = ya tienes la última. Lanza error si la versión fija no está disponible. */
    private suspend fun checkFixed(): Release? = withContext(Dispatchers.IO) {
        val base = "https://github.com/$REPO/releases/download/windows"
        val req = Request.Builder().url("$base/windows-version.txt").header("User-Agent", "NoirPlus-Desktop").build()
        val v = Http.client.newCall(req).execute().use { r -> if (!r.isSuccessful) null else r.body?.string()?.trim() }
            ?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+""")) } ?: throw IllegalStateException("sin versión fija")
        if (num(v) <= num(BuildInfo.VERSION)) null
        else Release(v, "$base/NoirPlus-Setup.exe", "$base/NoirPlus-Setup.exe.sha256")
    }

    private suspend fun checkApi(): Release? = withContext(Dispatchers.IO) {
        // GitHub mezcla las versiones de Android y Windows: se leen varias páginas
        val all = ArrayList<org.json.JSONObject>()
        for (page in 1..3) {
            val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases?per_page=100&page=$page")
                .header("Accept", "application/vnd.github+json").header("User-Agent", "NoirPlus-Desktop").build()
            val arr = Http.client.newCall(req).execute().use { r -> if (!r.isSuccessful) null else JSONArray(r.body!!.string()) } ?: break
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { all.add(it) }
            if (arr.length() < 100) break
        }
        val best = all
            .filter { it.optString("tag_name").startsWith("windows-v") && !it.optBoolean("draft") }
            .maxByOrNull { num(it.optString("tag_name")) } ?: return@withContext null
        val v = best.optString("tag_name").removePrefix("windows-v")
        if (num(v) <= num(BuildInfo.VERSION)) return@withContext null
        val assets = best.optJSONArray("assets") ?: return@withContext null
        var exe: String? = null
        var sha: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            when (a.optString("name")) {
                "NoirPlus-Setup.exe" -> exe = a.optString("browser_download_url")
                "NoirPlus-Setup.exe.sha256" -> sha = a.optString("browser_download_url")
            }
        }
        exe?.let { Release(v, it, sha) }
    }

    /** Descarga, verifica y lanza el instalador. onProgress de 0 a 1. */
    suspend fun install(r: Release, onProgress: (Float) -> Unit): Unit = withContext(Dispatchers.IO) {
        val out = File(System.getProperty("java.io.tmpdir"), "NoirPlus-Setup-${r.version}.exe")
        val req = Request.Builder().url(r.exeUrl).header("User-Agent", "NoirPlus-Desktop").build()
        val md = MessageDigest.getInstance("SHA-256")
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("No se pudo descargar (${resp.code})")
            val body = resp.body ?: throw IllegalStateException("Descarga vacía")
            val total = body.contentLength().coerceAtLeast(1)
            var done = 0L
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n); done += n
                        onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        r.shaUrl?.let { url ->
            val expected = Http.get(url).trim().lowercase()
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (expected.isNotEmpty() && expected != got) {
                out.delete()
                throw SecurityException("La huella del instalador no coincide. No se instala.")
            }
        }
        ProcessBuilder(out.absolutePath).start()
        Runtime.getRuntime().halt(0)
    }
}
