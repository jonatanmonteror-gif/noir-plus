package com.noirplus.tv.data

import android.content.Context
import com.noirplus.tv.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de errores: cierres inesperados de la app y fallos importantes (listas, reproducción).
 * Se guarda solo en el dispositivo; desde Ajustes se puede copiar o compartir para revisarlo.
 * No guarda contraseñas ni direcciones del servidor.
 */
object ErrorLog {
    private const val MAX = 48 * 1024
    private var file: File? = null
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.filesDir, "errors.log")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { note("Cierre inesperado (${t.name})", e) }
            prev?.uncaughtException(t, e)
        }
    }

    /** Quita usuario, contraseña y servidor de cualquier texto antes de guardarlo. */
    private fun scrub(s: String): String {
        var out = s.replace(Regex("""https?://[^\s"']+"""), "<url>")
        runCatching {
            val p = Repo.prefs
            listOf(p.user, p.pass, p.server).filter { it.length >= 3 }.forEach { out = out.replace(it, "***") }
        }
        return out
    }

    @Synchronized
    fun note(where: String, t: Throwable? = null, detail: String? = null) {
        val f = file ?: return
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sb = StringBuilder()
            sb.append("[").append(time).append("] v").append(BuildConfig.VERSION_NAME).append(" · ").append(where).append('\n')
            detail?.let { sb.append(it).append('\n') }
            if (t != null) {
                sb.append(t.javaClass.name).append(": ").append(t.message ?: "").append('\n')
                t.stackTrace.take(14).forEach { sb.append("    at ").append(it).append('\n') }
                t.cause?.let { c -> sb.append("Causa: ").append(c.javaClass.name).append(": ").append(c.message ?: "").append('\n') }
            }
            sb.append('\n')
            var text = (if (f.exists()) f.readText() else "") + scrub(sb.toString())
            if (text.length > MAX) text = text.substring(text.length - MAX).substringAfter("\n[", "").let { if (it.isEmpty()) text.takeLast(MAX) else "[$it" }
            f.writeText(text)
            version.intValue++
        }
    }

    fun read(): String = runCatching { file?.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    fun count(): Int = Regex("""(?m)^\[\d{4}-""").findAll(read()).count()

    fun clear() {
        runCatching { file?.delete() }
        version.intValue++
    }
}
