package com.noirplus.tv.data

/**
 * Listas "virtuales" de Películas/Series además de las carpetas del proveedor:
 * por plataforma (Netflix, Disney+…), fuera de plataformas y por género. Todo sale de tu propia lista,
 * cruzada con los catálogos de TMDB.
 */
object Library {
    const val PLAT = "__plat:"
    const val GENRE = "__genre:"
    const val NO_PLAT = "__noplat"

    fun isVirtual(catId: String) = catId.startsWith("__")
    fun isPlatformList(catId: String) = catId.startsWith(PLAT) || catId == NO_PLAT

    private suspend fun visibleAll(kind: Kind): List<Item> {
        val src = Repo.source ?: return emptyList()
        val prefs = Repo.prefs
        return src.all(kind).filter { prefs.visible(kind, it.categoryId) }
    }

    private fun List<Item>.newest() =
        sortedWith(compareByDescending<Item> { it.added }.thenByDescending { it.year?.toIntOrNull() ?: 0 })

    /** Títulos de una carpeta real o virtual. */
    suspend fun items(kind: Kind, catId: String): List<Item> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val src = Repo.source ?: return@withContext emptyList()
        when {
            catId == com.noirplus.tv.ui.ALL_ID -> visibleAll(kind)
            catId.startsWith(PLAT) -> {
                val c = Tmdb.catalog(kind, catId.removePrefix(PLAT)) ?: return@withContext emptyList()
                // Lo último estrenado primero (por fecha de estreno, no por cuándo lo añadió el proveedor)
                visibleAll(kind).asSequence().map { Tmdb.prep(it) }
                    .mapNotNull { p -> c.match(p)?.let { id -> p to (c.entries[id]?.date ?: 0) } }
                    .distinctBy { it.first.key }
                    .sortedByDescending { it.second }
                    .map { it.first.item }.toList()
            }
            catId == NO_PLAT -> {
                val cats = Tmdb.allCatalogs(kind).values
                visibleAll(kind).filter { i -> val p = Tmdb.prep(i); cats.none { it.match(p) != null } }.newest()
            }
            catId.startsWith(GENRE) -> {
                val g = catId.removePrefix(GENRE).toIntOrNull() ?: return@withContext emptyList()
                val cats = Tmdb.allCatalogs(kind).values
                visibleAll(kind).filter { Tmdb.genresOf(it, cats)?.contains(g) == true }
                    .distinctBy { Tmdb.prep(it).key }.newest()
            }
            else -> {
                // Carpetas del proveedor: también por fecha de estreno de TMDB
                if (kind != Kind.LIVE && Tmdb.configured()) runCatching { Tmdb.allCatalogs(kind) }
                val l = src.items(kind, catId)
                if (kind == Kind.LIVE) l else Tmdb.byRelease(l)
            }
        }
    }
}
