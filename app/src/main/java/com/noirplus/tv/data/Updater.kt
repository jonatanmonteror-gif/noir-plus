package com.noirplus.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.noirplus.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Actualizaciones desde GitHub Releases, con comprobación de huella SHA-256 antes de instalar. */
object Updater {
    private const val REPO = "jonatanmonteror-gif/noir-plus"

    data class Release(val versionCode: Int, val name: String, val apkUrl: String, val shaUrl: String?)

    val currentCode: Int get() = BuildConfig.VERSION_CODE

    /** Número interno 2.0.<compilación> → el que se enseña: 2.0.0 desde la compilación 105. */
    fun display(v: String): String {
        val n = v.substringAfterLast('.').toIntOrNull() ?: return v
        val head = v.substringBeforeLast('.', "")
        return if (head.endsWith("2.0") && n >= 105) head + "." + (n - 105) else v
    }

    /** Novedades de una versión. */
    data class Notes(val version: String, val date: String, val items: List<String>) {
        val code: Int get() = version.substringAfterLast('.').toIntOrNull() ?: 0
    }

    /** Historial de novedades de Android (se publica con cada versión; descarga directa, sin límite de consultas). */
    suspend fun changelog(): List<Notes> = runCatching {
        val arr = org.json.JSONArray(Http.get("https://github.com/$REPO/releases/latest/download/changelog.json"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val it = o.optJSONArray("items") ?: return@mapNotNull null
            Notes(o.optString("v"), o.optString("date"), (0 until it.length()).map { k -> it.optString(k) })
        }
    }.getOrDefault(emptyList())

    /**
     * Última versión publicada. Primero por la web de GitHub (sin límite de consultas: se lee a dónde
     * redirige «releases/latest»); si falla, por la API (máx. 60 consultas por hora y casa).
     */
    suspend fun latest(): Release? = latestByRedirect() ?: latestByApi()

    private suspend fun latestByRedirect(): Release? = runCatching {
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("https://github.com/$REPO/releases/latest").head()
                .header("User-Agent", Http.UA).build()
            Http.client.newCall(req).execute().use { r ->
                val tag = r.request.url.pathSegments.lastOrNull().orEmpty()
                if (!tag.startsWith("v")) return@withContext null
                val code = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
                val base = "https://github.com/$REPO/releases/download/$tag"
                Release(code, "Noir+ ${display(tag.removePrefix("v"))}", "$base/NoirPlus.apk", "$base/NoirPlus.apk.sha256")
            }
        }
    }.getOrNull()

    private suspend fun latestByApi(): Release? = runCatching {
        val o = JSONObject(Http.get("https://api.github.com/repos/$REPO/releases/latest"))
        val code = o.optString("tag_name").substringAfterLast('.').toIntOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        var apk: String? = null
        var sha: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            when (a.optString("name")) {
                "NoirPlus.apk" -> apk = a.optString("browser_download_url")
                "NoirPlus.apk.sha256" -> sha = a.optString("browser_download_url")
            }
        }
        Release(code, o.optString("name").ifEmpty { "Noir+ 1.0.$code" }, apk ?: return null, sha)
    }.getOrNull()

    /** Descarga el APK a la caché y verifica la huella. Devuelve el archivo listo para instalar. */
    suspend fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "NoirPlus-${rel.versionCode}.apk")
        val req = Request.Builder().url(rel.apkUrl).header("User-Agent", Http.UA).build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Error ${r.code} al descargar")
            val body = r.body ?: throw IOException("Descarga vacía")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        rel.shaUrl?.let { url ->
            val expected = Http.get(url).trim().take(64).lowercase()
            val actual = sha256(file)
            if (expected.length == 64 && expected != actual) {
                file.delete()
                throw IOException("La huella del archivo no coincide. Descarga cancelada por seguridad.")
            }
        }
        file
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Lanza el instalador. Si falta el permiso de "instalar apps desconocidas", abre esa pantalla y devuelve false. */
    fun install(ctx: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(i) }
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }
}
