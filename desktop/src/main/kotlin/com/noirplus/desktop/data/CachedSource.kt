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
 * se renuevan solas si tienen más de 12 horas, o al pulsar «Actualizar listas».
 */
class CachedSource(private val inner: Source, account: String) : Source by inner {
    private val dir = File(AppDirs.root, "listas/" + account.hashCode().toUInt().toString(16)).apply { mkdirs() }
    private val ttl = 12L * 3600 * 1000
    private val cats = ConcurrentHashMap<Kind, List<Category>>()
    private val alls = ConcurrentHashMap<Kind, List<Item>>()
    private val byCat = ConcurrentHashMap<Kind, Map<String, List<Item>>>()
    private val locks = ConcurrentHashMap<Kind, Mutex>()

    private fun file(name: String) = File(dir, name)
    private fun fresh(f: File) = f.exists() && System.currentTimeMillis() - f.lastModified() < ttl

    override suspend fun categories(kind: Kind): List<Category> {
        cats[kind]?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file("cats-${kind.name}.json")
            val cached = if (fresh(f)) runCatching {
                val a = JSONArray(f.readText())
                (0 until a.length()).map { a.getJSONObject(it).let { o -> Category(o.getString("id"), o.getString("name")) } }
            }.getOrNull() else null
            val list = cached ?: inner.categories(kind).also { l ->
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
                val list = cached ?: inner.all(kind).also { l ->
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
            file("cats-${k.name}.json").delete(); file("all-${k.name}.json").delete()
        }
        inner.clear(kind)
    }
}
