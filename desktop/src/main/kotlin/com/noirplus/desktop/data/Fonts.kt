package com.noirplus.desktop.data

import java.io.File

/**
 * Letras extra para los subtítulos (Inter y Atkinson Hyperlegible, pensada para leer con facilidad).
 * VLC solo usa letras instaladas en Windows, así que se instalan para tu usuario (sin permisos de administrador)
 * la primera vez que se abre Noir+.
 */
object Fonts {
    private val bundled = listOf(
        Triple("font/inter_regular.otf", "NoirPlus-Inter-Regular.otf", "Inter (OpenType)"),
        Triple("font/atkinson_regular.ttf", "NoirPlus-Atkinson-Regular.ttf", "Atkinson Hyperlegible (TrueType)"),
    )

    fun installForUser() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val local = System.getenv("LOCALAPPDATA") ?: return
        val dir = File(local, "Microsoft/Windows/Fonts").apply { mkdirs() }
        for ((res, fileName, regName) in bundled) runCatching {
            val f = File(dir, fileName)
            if (!f.exists()) {
                Fonts::class.java.classLoader.getResourceAsStream(res)?.use { i -> f.outputStream().use { i.copyTo(it) } }
                ProcessBuilder("reg", "add", "HKCU\\Software\\Microsoft\\Windows NT\\CurrentVersion\\Fonts",
                    "/v", regName, "/t", "REG_SZ", "/d", f.absolutePath, "/f").redirectErrorStream(true).start().waitFor()
            }
            // También disponible al momento para este programa (sin reiniciar)
            Gdi.INSTANCE?.AddFontResourceW(com.sun.jna.WString(f.absolutePath))
        }
    }

    private interface Gdi : com.sun.jna.Library {
        fun AddFontResourceW(path: com.sun.jna.WString): Int
        companion object {
            val INSTANCE: Gdi? = runCatching { com.sun.jna.Native.load("gdi32", Gdi::class.java) }.getOrNull()
        }
    }
}
