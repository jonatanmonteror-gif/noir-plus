package com.noirplus.tv.data

import android.content.Context

object Repo {
    lateinit var prefs: Prefs
        private set
    var source: Source? = null
        private set

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = Prefs(ctx.applicationContext)
            rebuild()
        }
    }

    /** Crea de nuevo el origen (vacía cachés). */
    fun rebuild() {
        val p = prefs
        source = when (p.type) {
            "xtream" -> XtreamSource(p.server, p.user, p.pass) { p.liveFormat }
            "m3u" -> M3uSource(p.m3u)
            else -> null
        }
    }
}
