package com.noirplus.desktop.data

import com.noirplus.desktop.BuildInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro de errores de Noir+ para Windows (sin usuario, contraseña ni servidor). */
object ErrorLog {
    private const val MAX = 64 * 1024
    private val file by lazy { File(AppDirs.root, "errors.log") }
    private val fmt get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun install() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { note("Cierre inesperado (${t.name})", e) }
            prev?.uncaughtException(t, e)
        }
    }

    private fun scrub(s: String): String {
        var out = s.replace(Regex("""https?://[^\s"']+"""), "<url>")
        runCatching { listOf(Store.user, Store.pass, Store.server).filter { it.length >= 3 }.forEach { out = out.replace(it, "***") } }
        return out
    }

    @Synchronized
    fun note(where: String, t: Throwable? = null) {
        runCatching {
            val sb = StringBuilder("[${fmt.format(Date())}] v${BuildInfo.VERSION} · $where\n")
            if (t != null) {
                sb.append(t.javaClass.name).append(": ").append(t.message ?: "").append('\n')
                t.stackTrace.take(18).forEach { sb.append("    at ").append(it).append('\n') }
                t.cause?.let { c -> sb.append("Causa: ").append(c.javaClass.name).append(": ").append(c.message ?: "").append('\n') }
            }
            sb.append('\n')
            var text = (if (file.exists()) file.readText() else "") + scrub(sb.toString())
            if (text.length > MAX) text = text.takeLast(MAX)
            file.writeText(text)
        }
    }

    fun read(): String = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")

    /** Último cierre inesperado posterior a [since]. */
    fun lastCrash(since: Long): Pair<Long, String>? =
        read().split(Regex("""(?m)^(?=\[\d{4}-)""")).filter { it.contains("Cierre inesperado") || it.contains("Error en pantalla") }
            .mapNotNull { b ->
                val ts = Regex("""^\[([^\]]+)\]""").find(b)?.groupValues?.get(1) ?: return@mapNotNull null
                val t = runCatching { fmt.parse(ts)?.time }.getOrNull() ?: return@mapNotNull null
                if (t > since) t to b.trim() else null
            }.maxByOrNull { it.first }
}
