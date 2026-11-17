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

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "NoirPlus-Desktop").build()
        val arr = Http.client.newCall(req).execute().use { r -> if (!r.isSuccessful) return@withContext null; JSONArray(r.body!!.string()) }
        val best = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
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
