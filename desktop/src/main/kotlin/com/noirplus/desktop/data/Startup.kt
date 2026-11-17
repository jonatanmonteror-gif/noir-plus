package com.noirplus.desktop.data

/** Tiempos de arranque, para saber qué tarda al abrir Noir+ (se ven en Ajustes → Acerca de). */
object Startup {
    private val jvmStart: Long = runCatching { java.lang.management.ManagementFactory.getRuntimeMXBean().startTime }.getOrDefault(0L)
    var mainAt = 0L
    var windowAt = 0L
    var listsAt = 0L
    var listsFromDisk = false

    private fun s(ms: Long) = String.format("%.1f s", ms / 1000f)

    fun summary(): String {
        if (mainAt == 0L) return ""
        val parts = mutableListOf<String>()
        if (jvmStart > 0) parts += "arranque de Java ${s(mainAt - jvmStart)}"
        if (windowAt > 0) parts += "ventana ${s(windowAt - mainAt)}"
        if (listsAt > 0) parts += "listas ${s(listsAt - mainAt)}" + if (listsFromDisk) " (guardadas en el PC)" else " (descargadas del servidor)"
        return "Último arranque: " + parts.joinToString(" · ")
    }
}
