package com.noirplus.tv.data

import java.text.Normalizer

/**
 * Canales en directo ordenados por temática (Deportes, Cine y series, Documentales…) en lugar de las
 * carpetas del proveedor, y con los canales repetidos en distintas calidades (SD/HD/FHD/4K) juntos.
 * Mismo código en Android y en Windows.
 */
object LiveIndex {
    data class Theme(val id: String, val label: String)

    val THEMES = listOf(
        Theme("sports", "Deportes"),
        Theme("movies", "Cine y series"),
        Theme("docs", "Documentales"),
        Theme("kids", "Infantil"),
        Theme("news", "Noticias"),
        Theme("general", "Generalistas"),
        Theme("music", "Música"),
        Theme("other", "Otros"),
        Theme("adult", "Adultos"),
    )

    fun label(theme: String) = THEMES.firstOrNull { it.id == theme }?.label ?: "Otros"

    /** Un canal (con sus calidades). [alts] va de mejor a peor calidad; el primero es el que se reproduce. */
    class Channel(
        val key: String,          // país|nombre, estable para recordar la temática elegida a mano
        val name: String,         // nombre limpio, sin país ni calidad
        val country: String?,     // ES, UK, FR…
        val theme: String,
        val alts: List<Item>,
        val bestQ: Int = 20,
    ) {
        val main: Item get() = alts.first()
        val item: Item get() = main.copy(name = name)
    }

    class Index(val channels: List<Channel>) {
        private val byItem = HashMap<String, Channel>().apply { for (c in channels) for (a in c.alts) put(a.key, c) }
        fun channelOf(itemKey: String): Channel? = byItem[itemKey]
        fun byTheme(theme: String): List<Channel> = channels.filter { it.theme == theme }
        val themes: List<Theme> get() = THEMES.filter { t -> channels.any { it.theme == t.id } }
    }

    @Volatile
    var current: Index? = null
        private set

    /** Calidades de un canal (mejor primero), para cambiar de calidad o probar otra si una falla. */
    fun alternatives(itemKey: String): List<Item> = current?.channelOf(itemKey)?.alts ?: emptyList()

    val COUNTRY_NAMES = mapOf(
        "ES" to "España", "UK" to "Reino Unido", "US" to "EE. UU.", "FR" to "Francia", "DE" to "Alemania",
        "IT" to "Italia", "PT" to "Portugal", "MX" to "México", "AR" to "Argentina", "CO" to "Colombia",
        "CL" to "Chile", "PE" to "Perú", "LAT" to "Latinoamérica", "NL" to "Países Bajos", "BE" to "Bélgica",
        "TR" to "Turquía", "CA" to "Canadá", "BR" to "Brasil", "AD" to "Andorra", "IE" to "Irlanda",
        "PL" to "Polonia", "RO" to "Rumanía", "MA" to "Marruecos", "SA" to "Arabia", "IN" to "India",
        "CH" to "Suiza", "AT" to "Austria", "SE" to "Suecia", "NO" to "Noruega", "DK" to "Dinamarca",
        "GR" to "Grecia", "RU" to "Rusia", "UA" to "Ucrania", "AL" to "Albania", "VE" to "Venezuela",
    )
    private val ALIASES = mapOf("GB" to "UK", "LATAM" to "LAT", "LATINO" to "LAT", "SP" to "ES", "ESP" to "ES", "USA" to "US", "POR" to "PT")
    private val WORD_COUNTRY = mapOf(
        "spain" to "ES", "espana" to "ES", "españa" to "ES", "spanish" to "ES", "uk" to "UK", "united kingdom" to "UK",
        "france" to "FR", "francia" to "FR", "germany" to "DE", "alemania" to "DE", "italy" to "IT", "italia" to "IT",
        "portugal" to "PT", "mexico" to "MX", "argentina" to "AR", "usa" to "US", "latino" to "LAT", "latam" to "LAT",
    )

    private fun countryCode(raw: String): String? {
        val c = raw.uppercase()
        val code = ALIASES[c] ?: c
        return code.takeIf { it in COUNTRY_NAMES }
    }

    private val PREFIX = Regex("""^\s*[\[(|]?\s*([A-Za-z]{2,5})\s*(?:[\])|:\-–»]+)\s*""")

    /** Quita «ES: », «[ES] », «ES | »… del principio y dice de qué país es. */
    private fun splitCountry(name: String): Pair<String?, String> {
        val m = PREFIX.find(name) ?: return null to name
        val code = countryCode(m.groupValues[1]) ?: return null to name
        return code to name.substring(m.range.last + 1)
    }

    private val QUALITY = Regex(
        """(?i)(?<![\p{L}\d])(4k|uhd|fhd|hd|sd|hevc|h\.?26[45]|1080[pi]?|720p|576p|50\s?fps|60\s?fps|hdr|raw|low|backup|bkp|multi|vip|(?:ᴴᴰ|ᶠᴴᴰ|ᵁᴴᴰ|ˢᴰ|ᴿᴬᵂ|⁴ᴷ))(?![\p{L}\d])"""
    )
    private val JUNK = Regex("""[◉●★☆⭐✪✦♦◆▶►|*#_]+""")

    private fun quality(name: String): Int {
        val n = name.lowercase()
        var q = 2
        when {
            Regex("""(?<![a-z\d])(4k|uhd)(?![a-z\d])|ᵁᴴᴰ|⁴ᴷ""").containsMatchIn(n) -> q = 40
            Regex("""(?<![a-z\d])(fhd|1080[pi]?)(?![a-z\d])|ᶠᴴᴰ""").containsMatchIn(n) -> q = 30
            Regex("""(?<![a-z\d])(hd|720p)(?![a-z\d])|ᴴᴰ""").containsMatchIn(n) -> q = 20
            Regex("""(?<![a-z\d])(sd|576p|low)(?![a-z\d])|ˢᴰ""").containsMatchIn(n) -> q = 10
            else -> q = 20
        }
        if (Regex("""(?<![a-z\d])(backup|bkp)(?![a-z\d])""").containsMatchIn(n)) q -= 5
        return q
    }

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .replace("+", " plus ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

    private fun isSeparator(name: String): Boolean {
        val letters = name.count { it.isLetterOrDigit() }
        val marks = name.count { it in "#=-*_~|<>" }
        return letters == 0 || (marks >= 4 && marks >= letters / 2)
    }

    // Palabras clave por temática. «x*» = empieza por x; el resto, palabra o frase completa.
    private val RULES: List<Pair<String, List<String>>> = listOf(
        "adult" to listOf("xxx", "adult*", "adultos", "18", "porn*", "hustler", "playboy", "brazzers", "erotic*", "redlight", "dorcel"),
        "sports" to listOf(
            "sport*", "deport*", "dazn*", "laliga*", "la liga", "liga*", "campeones", "champions", "europa league", "conference league",
            "federacion", "rfef", "hypermotion", "smartbank", "copa", "supercopa", "mundial*", "eurocopa", "futbol*", "football", "soccer",
            "f1", "formula 1", "formula1", "motogp", "moto gp", "moto", "motor*", "rally*", "eurosport*", "espn*", "golf*", "tenis", "tennis",
            "padel", "wta", "atp", "nba", "nfl", "nhl", "mlb", "acb", "euroliga", "euroleague", "baloncesto", "basket*", "balonmano",
            "atletismo", "ciclismo", "cycling", "rugby", "hockey", "voley*", "voleibol", "ufc", "boxing", "boxeo", "wwe", "fight*",
            "bein*", "gol", "gol play", "goltv", "real madrid", "barca", "athletic", "atletico", "toros", "racing", "ppv*", "arena*",
            "setanta", "match", "premier", "bundesliga", "serie a", "ligue 1", "eredivisie", "olympic*", "olimpic*", "eleven*",
            "teledeporte", "tdp", "esport*", "vamos", "ellas", "caza", "pesca", "hipica", "nautica", "skysports", "eventos", "evento",
        ),
        "news" to listOf(
            "news", "noticias", "24h", "24 h", "24 horas", "24horas", "cnn*", "bbc world", "euronews", "france 24", "al jazeera", "aljazeera",
            "cnbc", "bloomberg", "telediario", "informativos", "rt", "dw", "lci", "bfm*", "sky tg24", "tg24", "rainews*", "3 24", "324",
            "el toro", "libertad digital", "intereconomia", "trece", "negocios", "parlamento", "congreso", "tv5monde", "france info", "franceinfo",
        ),
        "kids" to listOf(
            "disney*", "clan", "nick*", "cartoon*", "cartoonito", "boing", "baby*", "kids", "infantil*", "junior", "jr", "toon*", "panda",
            "dreamworks", "cbeebies", "jim jam", "duck tv", "pocoyo", "peques", "super3", "sx3", "boomerang", "tiji", "gulli", "piwi",
            "minimax", "kika", "rai yoyo", "babytv", "kidz*", "dibujos", "anime*", "pequenos",
        ),
        "docs" to listOf(
            "discovery*", "national geographic", "nat geo*", "natgeo*", "history*", "historia", "odisea", "documental*", "docu*",
            "animal planet", "bbc earth", "viajar", "cocina", "canal cocina", "decasa", "dmax", "crimen", "crime*", "investigation", "id",
            "love nature", "curiosity*", "arte", "xplora", "iberalia", "science", "ciencia", "planet*", "travel*", "food*", "nature",
            "wild", "blaze", "naturaleza", "misterio", "ancient",
        ),
        "music" to listOf(
            "music*", "musica*", "mtv*", "vh1", "40 tv", "los40", "kiss*", "cadena dial", "radio*", "sol musica", "mezzo", "trace*",
            "clubbing*", "hit tv", "deluxe music", "europa fm", "rock*", "jazz", "dance*", "classica", "stingray", "flaix*", "cadena 100",
            "rne", "conciertos", "videoclips",
        ),
        "general" to listOf(
            "la 1", "la1", "la 2", "la2", "tve*", "antena 3", "antena3", "a3", "telecinco", "t5", "cuatro", "la sexta", "lasexta", "tv3",
            "3cat", "etb*", "tvg*", "canal sur*", "telemadrid", "a punt", "apunt", "aragon tv", "cmm", "13 tv", "8tv", "8 tv", "bbc one",
            "bbc two", "bbc three", "bbc four", "itv*", "channel 4", "channel 5", "tf1", "france 2", "france 3", "france 5", "m6", "rai 1",
            "rai 2", "rai 3", "canale 5", "italia 1", "rete 4", "rtl*", "zdf", "ard", "sat 1", "rtp*", "sic", "tvi", "ib3", "tpa*",
            "tvcan*", "7rm", "la 7", "la7", "rtve*", "televisa", "azteca", "las estrellas", "canal 5", "abc", "nbc", "cbs", "pbs",
            "real time", "be mad", "mega", "ten", "divinity", "nova", "neox", "energy", "dkiss", "boing tv", "bom", "squirrel",
            "telemundo", "univision", "autonomicas", "nacionales", "generalistas",
        ),
        "movies" to listOf(
            "cine*", "cinema*", "movie*", "film*", "pelic*", "series", "serie", "axn*", "tnt*", "hollywood*", "comedy*", "comedia",
            "amc*", "syfy", "cosmo*", "fox*", "calle 13", "warner*", "paramount*", "tcm", "dark", "accion", "action", "drama*", "xtrm",
            "star channel", "star", "hbo*", "showtime", "sky cinema*", "cinemax", "sundance*", "studio universal", "universal*",
            "enfamilia", "somos", "dizi", "novelas", "telenovela*", "atreseries", "fdf", "factoria de ficcion", "a3series", "acontra*",
            "max", "netflix", "prime video", "filmin", "skyshowtime", "estrenos*", "clasicos", "western", "suspense", "thriller", "terror",
            "humor", "romance", "hits", "oscars", "autor", "independiente",
        ),
    )

    private val compiled: List<Pair<String, List<Regex>>> = RULES.map { (t, kws) ->
        t to kws.map { kw ->
            val stem = kw.endsWith("*")
            val w = Regex.escape(kw.removeSuffix("*"))
            Regex(if (stem) """(?<![a-z0-9])$w""" else """(?<![a-z0-9])$w(?![a-z0-9])""")
        }
    }

    fun classify(text: String): String? {
        val n = norm(text)
        if (n.isEmpty()) return null
        for ((t, rs) in compiled) if (rs.any { it.containsMatchIn(n) }) return t
        return null
    }

    /** Temáticas que encajan con un texto (para saber si una carpeta del proveedor es clara o mezcla varias). */
    private fun classifyAll(text: String): Set<String> {
        val n = norm(text)
        if (n.isEmpty()) return emptySet()
        return compiled.filter { (_, rs) -> rs.any { it.containsMatchIn(n) } }.map { it.first }.toSet()
    }

    /** Temática de la carpeta del proveedor, solo si es clara (una única temática). */
    private fun categoryTheme(cat: String): String? = classifyAll(cat).singleOrNull()

    /**
     * Construye el índice: [catName] da el nombre de la carpeta del proveedor de cada canal (ayuda a saber país y temática),
     * [overrides] son las temáticas elegidas a mano (clave del canal → temática).
     */
    fun build(items: List<Item>, catName: (String) -> String?, overrides: Map<String, String>): Index {
        data class Parsed(val item: Item, val country: String?, val name: String, val base: String, val q: Int, val cat: String, val order: Int)

        val parsed = ArrayList<Parsed>(items.size)
        items.forEachIndexed { i, it ->
            if (isSeparator(it.name)) return@forEachIndexed
            val cat = catName(it.categoryId).orEmpty()
            var (country, rest) = splitCountry(it.name)
            if (country == null) {
                val (cc, _) = splitCountry(cat)
                country = cc ?: norm(cat).split(' ').firstNotNullOfOrNull { w -> WORD_COUNTRY[w] }
            }
            val q = quality(rest)
            val clean = rest.replace(QUALITY, " ").replace(JUNK, " ")
                .replace(Regex("""\(\s*\)|\[\s*]"""), " ")
                .replace(Regex("""\s{2,}"""), " ").trim(' ', '-', ':', '.', '–')
            val name = clean.ifBlank { it.name.trim() }
            val base = norm(name).ifBlank { norm(it.name) }
            parsed += Parsed(it, country, name, base, q, cat, i)
        }

        val groups = LinkedHashMap<String, MutableList<Parsed>>()
        for (p in parsed) groups.getOrPut("${p.country ?: "-"}|${p.base}") { mutableListOf() } += p

        val channels = groups.map { (key, ps) ->
            val sorted = ps.sortedWith(compareByDescending<Parsed> { it.q }.thenBy { it.order })
            val first = ps.minByOrNull { it.order }!!
            val theme = overrides[key]
                ?: classify(first.name)
                ?: ps.firstNotNullOfOrNull { categoryTheme(it.cat) }
                ?: "other"
            Channel(key, first.name, first.country, theme, sorted.map { it.item }, sorted.first().q) to first.order
        }
        // España primero; después por país con más canales; dentro, el orden del proveedor
        val countryRank = channels.groupingBy { it.first.country }.eachCount()
        val ordered = channels.sortedWith(
            compareBy<Pair<Channel, Int>> { if (it.first.country == "ES") 0 else 1 }
                .thenByDescending { countryRank[it.first.country] ?: 0 }
                .thenBy { it.first.country ?: "~" }
                .thenBy { it.second }
        ).map { it.first }
        return Index(ordered).also { current = it }
    }
}
