package com.noirplus.tv.ui

import com.noirplus.tv.data.Category
import com.noirplus.tv.data.Item
import com.noirplus.tv.data.Kind
import com.noirplus.tv.data.ListDisk
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Al salir a otra app (el tráiler en YouTube) se apunta dónde estabas. Si la tele cierra Noir+ por falta de
 * memoria mientras tanto, al volver se abre en la misma pantalla (la misma ficha y la misma pestaña), sin bienvenida.
 */
object ResumePoint {
    var nav: Nav? = null
    private const val MAX_AGE = 45 * 60_000L

    private fun file(): File? = ListDisk.root?.parentFile?.let { File(it, "volver.json") }

    fun save() {
        val n = nav ?: return
        runCatching {
            val arr = JSONArray()
            for (s in n.stack) when (s) {
                Screen.Main -> arr.put(JSONObject().put("s", "main"))
                Screen.Folders -> arr.put(JSONObject().put("s", "folders"))
                is Screen.Detail -> arr.put(JSONObject().put("s", "detail").put("item", s.item.toJson()))
                is Screen.Category -> arr.put(JSONObject().put("s", "cat").put("kind", s.kind.name)
                    .put("id", s.cat.id).put("name", s.cat.name).put("search", s.search))
                else -> {}
            }
            val o = JSONObject().put("at", System.currentTimeMillis()).put("tab", TvTabs.tab.value.name).put("stack", arr)
            file()?.writeText(o.toString())
        }
    }

    /** Pantallas guardadas hace poco (y se borra para que solo sirva una vez). */
    fun take(): List<Screen>? = runCatching {
        val f = file() ?: return null
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        f.delete()
        if (System.currentTimeMillis() - o.optLong("at") > MAX_AGE) return null
        runCatching { Tab.valueOf(o.optString("tab")) }.getOrNull()?.let { TvTabs.tab.value = it; TvTabs.shown.value = it }
        val arr = o.getJSONArray("stack")
        (0 until arr.length()).mapNotNull { i ->
            val e = arr.getJSONObject(i)
            when (e.optString("s")) {
                "main" -> Screen.Main
                "folders" -> Screen.Folders
                "detail" -> Screen.Detail(Item.fromJson(e.getJSONObject("item")))
                "cat" -> Screen.Category(Kind.valueOf(e.getString("kind")), Category(e.getString("id"), e.getString("name")), e.optBoolean("search"))
                else -> null
            }
        }.takeIf { it.isNotEmpty() && it.first() == Screen.Main }
    }.getOrNull()

    fun clear() { runCatching { file()?.delete() } }
}
