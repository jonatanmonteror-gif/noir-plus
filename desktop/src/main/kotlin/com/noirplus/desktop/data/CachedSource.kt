package com.noirplus.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Guarda las listas (carpetas y títulos) en el disco del PC.
 * Al abrir Noir+ se leen del disco al instante en vez de descargarlas otra vez del servidor;
 * solo se renuevan en la actualización programada (8, 14, 20 y 2 h con Noir+ abierto) o al pulsar «Actualizar listas».
 */
class CachedSource(private val inner: Source, account: String) : Source by inner {
    private val dir = File(AppDirs.root, "listas/" + account.hashCode().toUInt().toString(16)).apply { mkdirs() }
    private val cats = ConcurrentHashMap<Kind, List<Category>>()
    private val alls = ConcurrentHashMap<Kind, List<Item>>()
    private val byCat = ConcurrentHashMap<Kind, Map<String, List<Item>>>()
    private val locks = ConcurrentHashMap<Kind, Mutex>()

    private fun file(name: String) = File(dir, name)
    private fun fresh(f: File) = f.exists() && f.length() > 2
    private fun old(f: File) = File(f.path + ".old")

    /** Descarga nueva; si el servidor falla, se sigue con la lista anterior. */
    private suspend fun <T> download(f: File, get: suspend () -> T, read: (File) -> T): T = try {
        get().also { old(f).delete() }
    } catch (e: Exception) {
        val o = old(f)
        if (o.exists()) { o.renameTo(f); read(f) } else throw e
    }

    override suspend fun categories(kind: Kind): List<Category> {
        cats[kind]?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file("cats-${kind.name}.json")
            val cached = if (fresh(f)) runCatching {
                val a = JSONArray(f.readText())
                (0 until a.length()).map { a.getJSONObject(it).let { o -> Category(o.getString("id"), o.getString("name")) } }
            }.getOrNull() else null
            val readCats = { file: File ->
                val a = JSONArray(file.readText())
                (0 until a.length()).map { a.getJSONObject(it).let { o -> Category(o.getString("id"), o.getString("name")) } }
            }
            val list = cached ?: download(f, { inner.categories(kind) }, readCats).also { l ->
                runCatching { f.writeText(JSONArray(l.map { JSONObject().put("id", it.id).put("name", it.name) }).toString()) }
            }
            cats[kind] = list
            list
        }
    }

    override suspend fun all(kind: Kind): List<Item> {
        alls[kind]?.let { return it }
        return locks.getOrPut(kind) { Mutex() }.withLock {
            alls[kind]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val f = file("all-${kind.name}.json")
                val cached = if (fresh(f)) runCatching {
                    val a = JSONArray(f.readText())
                    (0 until a.length()).map { Item.fromJson(a.getJSONObject(it)) }
                }.getOrNull() else null
                if (cached != null) Startup.listsFromDisk = true
                val readAll = { file: File ->
                    val a = JSONArray(file.readText())
                    (0 until a.length()).map { Item.fromJson(a.getJSONObject(it)) }
                }
                val list = cached ?: download(f, { inner.all(kind) }, readAll).also { l ->
                    runCatching { f.writeText(JSONArray(l.map { it.toJson().put("added", it.added) }).toString()) }
                }
                alls[kind] = list
                byCat[kind] = list.groupBy { it.categoryId }
                list
            }
        }
    }

    /** Los títulos de una carpeta salen de la lista completa ya guardada (sin pedir nada al servidor). */
    override suspend fun items(kind: Kind, categoryId: String): List<Item> {
        all(kind)
        return byCat[kind]?.get(categoryId) ?: inner.items(kind, categoryId)
    }

    override fun clear(kind: Kind?) {
        val kinds = kind?.let { listOf(it) } ?: Kind.entries
        for (k in kinds) {
            cats.remove(k); alls.remove(k); byCat.remove(k)
            for (n in listOf("cats-${k.name}.json", "all-${k.name}.json")) {
                val f = file(n); if (f.exists()) { old(f).delete(); f.renameTo(old(f)) }
            }
        }
        inner.clear(kind)
    }
}
