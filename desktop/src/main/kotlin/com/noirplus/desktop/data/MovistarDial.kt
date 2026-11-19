package com.noirplus.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Carpeta «Movistar+»: los canales de tu lista ordenados como el dial de Movistar Plus+ (La 1 en el 1, La 2 en el 2…),
 * eligiendo de cada canal la versión que arranca más rápido. Es un acceso directo: los canales siguen en su temática.
 */
object MovistarDial {
    /** Dial y nombre (como suele escribirlo el proveedor); los alias ayudan a encontrar el canal. Fuente: dial de Movistar Plus+ 2026. */
    val DIAL: List<Triple<Int, String, List<String>>> = listOf(
        Triple(1, "La 1", listOf("La1", "TVE 1", "TVE1")),
        Triple(2, "La 2", listOf("La2", "TVE 2")),
        Triple(3, "Antena 3", emptyList()),
        Triple(4, "Cuatro", emptyList()),
        Triple(5, "Telecinco", listOf("Tele 5", "T5")),
        Triple(6, "La Sexta", listOf("LaSexta", "La 6")),
        Triple(7, "Movistar Plus+", listOf("M+ #0", "#0", "M+ Plus", "Movistar Plus")),
        Triple(8, "M+ Vamos", listOf("#Vamos", "Vamos")),
        Triple(9, "TV3", listOf("TV3 Cat", "TV3 Catalunya")),
        Triple(12, "M+ Cine", listOf("M+ Estrenos", "Cine")),
        Triple(13, "M+ Series", listOf("Series")),
        Triple(14, "M+ Originales", listOf("Originales")),
        Triple(15, "M+ Los Goya", listOf("Los Goya")),
        Triple(16, "M+ Multicine", listOf("Multicine", "Multi Cine")),
        Triple(17, "M+ Documentales", listOf("Documentales")),
        Triple(18, "M+ Los Oscars", listOf("Oscars", "Los Oscar")),
        Triple(20, "M+ Clasicos", listOf("Clasicos")),
        Triple(21, "M+ Accion", listOf("Accion")),
        Triple(22, "M+ Comedia", listOf("Comedia")),
        Triple(23, "M+ Drama", listOf("Drama")),
        Triple(25, "M+ Indie", listOf("Indie")),
        Triple(26, "M+ Cine Espanol", listOf("Cine Espanol")),
        Triple(29, "SkyShowtime 1", listOf("SkyShowtime", "Sky Showtime 1")),
        Triple(30, "TCM", emptyList()),
        Triple(32, "BBC Series", emptyList()),
        Triple(34, "BBC Top Gear", listOf("Top Gear")),
        Triple(36, "Be Mad", listOf("BeMad")),
        Triple(37, "Star Channel", listOf("Star")),
        Triple(38, "AXN", emptyList()),
        Triple(39, "Warner TV", listOf("Warner")),
        Triple(40, "Comedy Central", emptyList()),
        Triple(41, "Calle 13", listOf("Calle13")),
        Triple(42, "Cosmo", emptyList()),
        Triple(43, "SYFY", listOf("Sci Fi")),
        Triple(46, "Energy", emptyList()),
        Triple(47, "FDF", listOf("Factoria de Ficcion")),
        Triple(48, "Neox", emptyList()),
        Triple(49, "A3Series", listOf("Atreseries", "A3 Series")),
        Triple(51, "M+ Vamos 2", listOf("Vamos 2")),
        Triple(52, "M+ Vamos 3", listOf("Vamos 3")),
        Triple(53, "Primera Federacion", listOf("1 RFEF", "Primera RFEF", "M+ Primera Federacion")),
        Triple(54, "M+ LaLiga", listOf("LaLiga", "M+ LaLiga TV")),
        Triple(55, "DAZN LaLiga", emptyList()),
        Triple(56, "LaLiga TV Hypermotion", listOf("Hypermotion", "LaLiga Hypermotion")),
        Triple(57, "M+ LaLiga 2", listOf("LaLiga 2")),
        Triple(58, "DAZN LaLiga 2", emptyList()),
        Triple(59, "LaLiga TV Hypermotion 2", listOf("Hypermotion 2", "LaLiga Hypermotion 2")),
        Triple(60, "M+ Liga de Campeones", listOf("Liga de Campeones")),
        Triple(61, "M+ Liga de Campeones 2", listOf("Liga de Campeones 2")),
        Triple(62, "M+ Liga de Campeones 3", listOf("Liga de Campeones 3")),
        Triple(63, "M+ Deportes", listOf("Deportes")),
        Triple(64, "M+ Deportes 2", listOf("Deportes 2")),
        Triple(65, "M+ Deportes 3", listOf("Deportes 3")),
        Triple(66, "M+ Deportes 4", listOf("Deportes 4")),
        Triple(67, "M+ Golf", listOf("Golf")),
        Triple(68, "M+ Golf 2", listOf("Golf 2")),
        Triple(69, "DAZN F1", listOf("DAZN Formula 1")),
        Triple(70, "DAZN MotoGP", listOf("DAZN Moto GP")),
        Triple(71, "DAZN 1", emptyList()),
        Triple(72, "DAZN 2", emptyList()),
        Triple(73, "DAZN Baloncesto", emptyList()),
        Triple(74, "DAZN Baloncesto 2", emptyList()),
        Triple(75, "DAZN Baloncesto 3", emptyList()),
        Triple(76, "Eurosport 1", listOf("Eurosport")),
        Triple(77, "Eurosport 2", emptyList()),
        Triple(78, "GOL", listOf("Gol Play", "Gol TV")),
        Triple(79, "Teledeporte", listOf("TDP")),
        Triple(80, "Real Madrid TV", listOf("Real Madrid")),
        Triple(81, "Caza y Pesca", emptyList()),
        Triple(82, "Iberalia TV", listOf("Iberalia")),
        Triple(83, "Garage TV", listOf("Garage")),
        Triple(84, "Flash Deportes", emptyList()),
        Triple(85, "Ubeat", emptyList()),
        Triple(86, "National Geographic", listOf("Nat Geo", "National Geographic Channel")),
        Triple(87, "Nat Geo Wild", listOf("National Geographic Wild")),
        Triple(88, "Historia", emptyList()),
        Triple(89, "Discovery", listOf("Discovery Channel")),
        Triple(90, "BBC Earth", emptyList()),
        Triple(91, "DMAX", emptyList()),
        Triple(92, "BBC Food", emptyList()),
        Triple(93, "BBC Lifestyle", emptyList()),
        Triple(94, "DKISS", emptyList()),
        Triple(95, "Divinity", emptyList()),
        Triple(96, "Nova", emptyList()),
        Triple(97, "MEGA", emptyList()),
        Triple(98, "Ten", emptyList()),
        Triple(110, "Baby TV", emptyList()),
        Triple(111, "Disney Junior", emptyList()),
        Triple(113, "Nick JR", listOf("Nick Junior")),
        Triple(114, "Nickelodeon", emptyList()),
        Triple(115, "DreamWorks", emptyList()),
        Triple(117, "Boing", emptyList()),
        Triple(118, "Clan", listOf("Clan TVE")),
        Triple(119, "SX3", listOf("Super3", "Super 3")),
        Triple(120, "MTV", emptyList()),
        Triple(121, "MTV 00s", listOf("MTV 00")),
        Triple(123, "Mezzo", emptyList()),
        Triple(124, "Mezzo Live", emptyList()),
        Triple(125, "Stingray Classica", emptyList()),
        Triple(127, "24h", listOf("24 Horas", "Canal 24 Horas")),
        Triple(128, "BBC World", listOf("BBC World News")),
        Triple(129, "CNN", listOf("CNN International")),
        Triple(130, "Fox News", emptyList()),
        Triple(131, "Euronews", emptyList()),
        Triple(132, "Al Jazeera", listOf("Aljazeera")),
        Triple(133, "France 24", listOf("France24")),
        Triple(136, "CNBC", emptyList()),
        Triple(137, "TV5 Monde", listOf("TV5Monde")),
        Triple(138, "Bloomberg", listOf("Bloomberg Television")),
        Triple(139, "El Toro TV", listOf("El Toro")),
        Triple(140, "Negocios TV", listOf("Negocios")),
        Triple(142, "CGTN Espanol", listOf("CGTN")),
        Triple(149, "EWTN", emptyList()),
        Triple(151, "Canal Sur", listOf("Canal Sur Andalucia")),
        Triple(152, "TVG", listOf("Television de Galicia", "TVG Galicia")),
        Triple(153, "Canal Extremadura", emptyList()),
        Triple(155, "ETB", listOf("ETB 1", "ETB Basque")),
        Triple(156, "Aragon TV", emptyList()),
        Triple(157, "Telemadrid", emptyList()),
        Triple(164, "Esport3", listOf("Esport 3")),
        Triple(166, "Beteve", emptyList()),
    )

    /** Canales del dial que hay en tu lista: (dial, canal con la versión más rápida). */
    fun build(idx: LiveIndex.Index): List<Pair<Int, LiveIndex.Channel>> {
        // Versiones de cada canal, por la clave de grupo (sin país)
        val byBase = HashMap<String, MutableList<LiveIndex.Channel>>()
        for (c in idx.channels) {
            if (c.country != null && c.country != "ES") continue
            byBase.getOrPut(c.key.substringBefore('#').substringAfter('|')) { mutableListOf() } += c
        }
        val used = HashSet<String>()
        return DIAL.mapNotNull { (dial, name, aliases) ->
            val variants = (listOf(name) + aliases).asSequence()
                .map { LiveIndex.groupKey(it) }
                .mapNotNull { byBase[it] }.firstOrNull() ?: return@mapNotNull null
            val base = variants.first().key.substringBefore('#')
            if (!used.add(base)) return@mapNotNull null
            val best = LiveSpeed.best(variants)
            dial to LiveIndex.Channel(best.key, name.replace("M+ ", "M+ ").let { pretty(it) }, best.country, best.theme,
                listOf(best.item) + variants.filter { it !== best }.map { it.item }, best.bestQ)
        }
    }

    private fun pretty(n: String) = n.replace("Clasicos", "Clásicos").replace("Accion", "Acción").replace("Espanol", "Español")
        .replace("Federacion", "Federación").replace("Aragon", "Aragón").replace("Beteve", "Betevé")

    /** Todas las versiones de los canales del dial (para medir cuál va mejor). */
    fun candidates(idx: LiveIndex.Index): List<Item> {
        val keys = DIAL.flatMap { (_, n, a) -> (listOf(n) + a).map { LiveIndex.groupKey(it) } }.toSet()
        return idx.channels.filter { (it.country == null || it.country == "ES") &&
            it.key.substringBefore('#').substringAfter('|') in keys }.map { it.item }
    }
}

/**
 * Mide cuánto tarda en empezar a llegar la señal de cada versión de un canal (de una en una, sin cargar al servidor)
 * y lo guarda una semana. La versión que va más rápido es la que se usa en la carpeta Movistar+.
 */
object LiveSpeed {
    private const val WEEK = 7L * 86400_000L
    private var cache: JSONObject? = null
    @Volatile var running = false

    private fun file(): File? = File(AppDirs.root, "velocidad-canales.json")

    private fun load(): JSONObject = cache ?: runCatching { JSONObject(file()!!.readText()) }.getOrDefault(JSONObject()).also { cache = it }

    /** Milisegundos medidos (o null si no se ha medido o hace más de una semana). Un fallo cuenta como muy lento. */
    fun ms(key: String): Long? {
        val o = load().optJSONObject(key) ?: return null
        val age = System.currentTimeMillis() - o.optLong("at")
        val ms = o.optLong("ms")
        // Un fallo puede ser pasajero (servidor lleno): se vuelve a probar a la hora
        if (age > WEEK || (ms >= 99_999L && age > 3600_000L)) return null
        return ms
    }

    fun best(variants: List<LiveIndex.Channel>): LiveIndex.Channel {
        val measured = variants.mapNotNull { v -> ms(v.item.key)?.let { v to it } }
        measured.filter { it.second < 60_000 }.minByOrNull { it.second }?.let { return it.first }
        // Sin medir aún: FHD/HD antes que 4K (pesa más) y que bajas conexiones
        return variants.sortedBy { v -> if (v.bestQ in 20..39) 0 else if (v.bestQ >= 40) 1 else 2 }.first()
    }

    fun needsProbe(items: List<Item>): Boolean = items.any { ms(it.key) == null }

    /** Mide las versiones que no están medidas. Devuelve true si ha medido alguna. */
    suspend fun probe(src: Source, items: List<Item>): Boolean = withContext(Dispatchers.IO) {
        if (running) return@withContext false
        running = true
        var any = false
        try {
            val client = Http.client.newBuilder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS).build()
            for (it in items) {
                if (ms(it.key) != null) continue
                val url = src.streamUrl(it)
                if (url.isBlank()) continue
                val t0 = System.currentTimeMillis()
                val ok = runCatching {
                    client.newCall(Request.Builder().url(url).header("User-Agent", Http.UA).build()).execute().use { r ->
                        if (!r.isSuccessful) return@use false
                        val body = r.body ?: return@use false
                        val buf = ByteArray(16 * 1024)
                        var got = 0
                        val ins = body.byteStream()
                        while (got < 64 * 1024) { val n = ins.read(buf); if (n < 0) break; got += n }
                        got > 0
                    }
                }.getOrDefault(false)
                val ms = if (ok) System.currentTimeMillis() - t0 else 99_999L
                synchronized(this@LiveSpeed) {
                    val o = load()
                    o.put(it.key, JSONObject().put("ms", ms).put("at", System.currentTimeMillis()))
                    runCatching { file()?.writeText(o.toString()) }
                }
                any = true
                delay(400) // de una en una y con calma
            }
        } finally { running = false }
        any
    }
}
