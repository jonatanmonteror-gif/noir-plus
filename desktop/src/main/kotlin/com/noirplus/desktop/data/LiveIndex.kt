package com.noirplus.desktop.data

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

    /** Filas de adorno que algunos proveedores meten como canal («✿✿✿✿ CINE Y SERIES ✿✿✿✿», «[COMUNICADOS]»…). */
    private fun isSeparator(name: String): Boolean {
        val t = name.trim()
        val letters = t.count { it.isLetterOrDigit() }
        if (letters == 0) return true
        val marks = t.count { !it.isLetterOrDigit() && !it.isWhitespace() && it !in "+.&'!()-/:" }
        if (marks >= 4 && marks >= letters / 2) return true
        if (Regex("""([^\p{L}\p{N}\s])\1\1""").containsMatchIn(t)) return true
        return t.startsWith("[") && t.endsWith("]")
    }

    /**
     * Clave para juntar el mismo canal escrito de formas distintas:
     * «M. LIGA DE CAMPEONES 2», «M+ Liga de Campeones 2», «LIGA DE CAMPEONES 2 ORANGE», «mligadecampeones2»…
     */
    private val DROP = setOf("tv", "channel", "chanel", "canal", "orange", "sat", "op", "the")
    private fun groupKey(name: String): String {
        var toks = norm(name).split(' ').filter { it.isNotEmpty() }
        toks = when {
            toks.size > 2 && toks[0] == "m" && toks[1] == "plus" -> toks.drop(2)
            toks.size > 2 && toks[0] == "movistar" && toks[1] == "plus" -> toks.drop(2)
            toks.size > 1 && (toks[0] == "m" || toks[0] == "movistar") -> toks.drop(1)
            else -> toks
        }
        var k = toks.filter { it !in DROP }.joinToString("").ifEmpty { toks.joinToString("") }
        k = k.replace("laliga", "liga").replace("ligadecampeones", "ligacampeones").replace("lcampeones", "ligacampeones")
            .replace("orange", "").replace("movistar", "")
        if (k.length > 4 && k.endsWith("tv")) k = k.dropLast(2)
        if (k.length > 9 && k.endsWith("channel")) k = k.dropLast(7)
        // Un número suelto («2» de «M+ 2») no basta para identificar un canal
        if (k.all { it.isDigit() }) k = norm(name).replace(" ", "")
        return k.ifEmpty { norm(name).replace(" ", "") }
    }

    // Palabras clave por temática. «x*» = empieza por x; el resto, palabra o frase completa. El orden importa: gana la primera.
    private val RULES: List<Pair<String, List<String>>> = listOf(
        "adult" to listOf("xxx", "adult*", "adultos", "18", "porn*", "hustler", "playboy", "brazzers", "erotic*", "redlight", "dorcel"),
        "sports" to listOf(
            "sport*", "deport*", "dazn*", "danz", "laliga*", "la liga", "liga*", "lcampeones", "campeones", "champions", "europa league",
            "conference league", "federacion", "rfef", "hypermotion", "smartbank", "copa", "supercopa", "mundial*", "eurocopa", "futbol*",
            "football", "soccer", "f1", "formula 1", "formula1", "motogp", "moto gp", "moto", "motoadv", "moto adv", "automoto", "motor*",
            "rally*", "wrc", "eurosport*", "espn*", "golf*", "tenis", "tennis", "padel", "wta", "atp", "nba", "nfl", "nhl", "mlb", "acb",
            "euroliga", "euroleague", "baloncesto", "basket*", "balonmano", "atletismo", "ciclismo", "cycling", "bici", "billar", "rugby",
            "hockey", "voley*", "voleibol", "ufc", "boxing", "boxeo", "wwe", "fight*", "bein*", "gol", "gol play", "golplay", "goltv",
            "gol stadium", "real madrid*", "realmadrid*", "barca", "betis", "athletic", "atletico", "toros", "onetoro", "one toro", "racing",
            "ppv*", "arena*", "setanta", "match", "premier", "bundesliga", "serie a", "ligue 1", "eredivisie", "olympic*", "olimpic*",
            "eleven*", "teledeporte", "tdp", "esport*", "vamos", "ellas", "caza*", "pesca", "hipica", "horse", "nautic*", "surf*",
            "red bull", "redbull*", "skysports", "eventos", "evento",
        ),
        "news" to listOf(
            "news", "noticias", "24h", "24 h", "24 horas", "24horas", "cnn*", "bbc world", "euronews", "france 24", "al jazeera", "aljazeera",
            "cnbc", "bloomberg", "telediario", "informativos", "rt", "dw", "lci", "bfm*", "sky tg24", "tg24", "rainews*", "3 24", "324",
            "el toro", "eltoro*", "libertad digital", "intereconomia", "trece", "13tv", "13 tv", "negocios*", "parlamento", "congreso",
            "tv5monde", "france info", "franceinfo",
        ),
        "kids" to listOf(
            "disney*", "clan", "nick*", "cartoon*", "boing", "baby*", "kids", "junior", "jr", "toon*", "panda", "dreamworks", "cbeebies",
            "jim jam", "duck tv", "pocoyo", "peques", "super 3", "super3", "sx3", "boomerang", "tiji", "gulli", "piwi", "minimax", "kika",
            "rai yoyo", "kidz*", "dibujos", "pequenos", "paka paka", "pakapaka",
        ),
        "docs" to listOf(
            "discovery*", "national geographic", "nationalgeo*", "nat geo*", "natgeo*", "history*", "historia", "odisea", "documental*",
            "docu*", "animal planet", "bbc earth", "viajar", "viaje*", "dviaje", "buenviaje", "escapa*", "cocina", "canalcocina", "decasa",
            "canaldecasa", "dcasa", "dmax", "crimen", "crime*", "investigation", "id", "love nature", "curiosity*", "arte", "xplora",
            "iberalia", "science", "ciencia", "planet*", "travel*", "food*", "nature", "wild", "blaze", "naturaleza", "misterio",
            "ancient", "lifestyle", "estilo de vida", "top gear", "topgear", "cultura",
        ),
        "music" to listOf(
            "music*", "musica*", "musical*", "mtv*", "vh1", "40 tv", "los40", "kiss*", "cadena dial", "radio*", "sol musica", "sol",
            "mezzo*", "trace*", "clubbing*", "hit tv", "deluxe music", "dlx*", "europa fm", "rock*", "jazz", "dance*", "classica", "clasica",
            "stingray", "flaix*", "cadena 100", "rne", "conciertos", "videoclips", "flamenco", "hqm*", "q music", "qmusic", "now 70", "now 80",
            "now 90", "now rock", "cadena elite", "musik*", "nortevision",
        ),
        "movies" to listOf(
            "cine*", "cinema*", "movie*", "film*", "pelic*", "series", "serie", "axn*", "tnt*", "hollywood*", "comedy*", "comedia",
            "amc*", "syfy", "cosmo*", "fox*", "calle 13", "calle13", "warner*", "paramount*", "tcm", "dark", "accion", "action", "drama*",
            "xtrm", "star channel", "star", "hbo*", "showtime*", "sky showtime*", "skyshowtime*", "cinemax", "sundance*",
            "studio universal", "universal*", "enfamilia", "somos", "dizi", "novelas", "telenovela*", "atreseries", "a3series", "fdf",
            "factoria de ficcion", "factoriadeficcion", "acontra*", "max", "netflix", "prime video", "filmin", "estrenos*", "clasico*",
            "western", "suspense", "thriller", "terror", "humor", "romance", "hits", "oscars", "autor", "independiente", "indie",
            "originales", "pops", "ciclo", "los 2000", "los 90", "los 80", "flix ole", "flixole", "bom cine", "cineman",
        ),
        "general" to listOf(
            "la 1", "la1", "la 2", "la2", "tve*", "antena 3", "antena3", "a3", "telecinco", "t5", "cuatro", "la sexta", "lasexta",
            "tv3*", "3cat", "etb*", "eitb*", "tvg*", "canal sur*", "canalsur*", "telemadrid", "a punt", "apunt", "aragon*", "cmm", "8tv",
            "8 tv", "bbc one", "bbc two", "bbc three", "bbc four", "itv*", "channel 4", "channel 5", "tf1", "france 2", "france 3",
            "france 5", "m6", "rai 1", "rai 2", "rai 3", "canale 5", "italia 1", "rete 4", "rtl*", "zdf", "ard", "sat 1", "rtp*", "sic",
            "tvi", "ib3", "tpa*", "tvcan*", "7rm", "la 7", "la7", "rtve*", "televisa", "azteca", "las estrellas", "canal 5", "abc", "nbc",
            "cbs", "pbs", "real time", "be mad", "bemad", "mega", "ten", "divinity", "nova", "neox", "energy", "dkiss", "bom", "squirrel",
            "telemundo", "univision", "caracol*", "tele caracol", "m plus", "m plus 2", "cero", "la resistencia", "resistencia",
            "canaria*", "tele canarias", "galicia*", "extremadura*", "castilla*", "andalucia", "onda*", "levante*", "sevilla*", "malaga",
            "cadiz", "huelva", "teleonuba", "fibracat*", "la otra", "la septima", "lebrija*", "sal tv", "101 tv", "7tv", "ptv", "local",
            "autonomic*", "nacionales", "generalistas", "tdt",
        ),
    )

    private val compiled: List<Pair<String, List<Regex>>> = RULES.map { (t, kws) ->
        t to kws.map { kw ->
            val stem = kw.endsWith("*")
            val w = Regex.escape(kw.removeSuffix("*"))
            Regex(if (stem) """(?<![a-z0-9])$w""" else """(?<![a-z0-9])$w(?![a-z0-9])""")
        }
    }

    // Para nombres pegados («mligadecampeones2», «canalsurandalucia»): palabras largas buscadas dentro del nombre
    private val compact: List<Pair<String, List<String>>> = RULES.map { (t, kws) ->
        t to kws.map { it.removeSuffix("*").replace(" ", "") }.filter { it.length >= 6 }
    }

    private fun match(n: String): String? {
        for ((t, rs) in compiled) if (rs.any { it.containsMatchIn(n) }) return t
        if (!n.contains(' ')) for ((t, ks) in compact) if (ks.any { n.contains(it) }) return t
        return null
    }

    fun classify(text: String): String? {
        val n = norm(text)
        if (n.isEmpty()) return null
        match(n)?.let { return it }
        // «mgolf», «mvamos», «mcine»…: la «m» delante es de Movistar
        if (!n.contains(' ') && n.length >= 5 && n.startsWith("m")) match(n.drop(1))?.let { return it }
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
            // Las carpetas de «bajas conexiones» son la misma señal en peor calidad: van detrás
            val low = norm(cat).let { c -> c.contains("bajas conexiones") || c.contains("baja calidad") || c.contains("low") }
            val q = if (low) 5 else quality(rest)
            val clean = rest.replace(QUALITY, " ").replace(JUNK, " ")
                .replace(Regex("""\(\s*\)|\[\s*]"""), " ")
                .replace(Regex("""\s{2,}"""), " ").trim(' ', '-', ':', '.', '–')
            val name = clean.ifBlank { it.name.trim() }
            val base = groupKey(name)
            parsed += Parsed(it, country, name, base, q, cat, i)
        }

        val groups = LinkedHashMap<String, MutableList<Parsed>>()
        for (p in parsed) groups.getOrPut("${p.country ?: "-"}|${p.base}") { mutableListOf() } += p
        // «mdeportes3» (nombre pegado con la m de Movistar) se junta con «deportes3» si existe
        for (k in groups.keys.toList()) {
            val (c, b) = k.split('|', limit = 2).let { it[0] to it[1] }
            if (b.length > 4 && b.startsWith("m")) {
                val other = "$c|${b.drop(1)}"
                if (other != k && groups.containsKey(other)) groups[other]!!.addAll(groups.remove(k)!!)
            }
        }

        val channels = groups.map { (key, ps) ->
            val sorted = ps.sortedWith(compareByDescending<Parsed> { it.q }.thenBy { it.order })
            val first = ps.minByOrNull { it.order }!!
            // Nombre a mostrar: el mejor escrito (con espacios, de una carpeta normal)
            val shown = ps.filter { it.q > 5 && it.name.contains(' ') }.minByOrNull { it.order }
                ?: ps.filter { it.q > 5 }.minByOrNull { it.order } ?: first
            val theme = overrides[key]
                ?: classify(shown.name)
                ?: ps.firstNotNullOfOrNull { classify(it.name) }
                ?: ps.firstNotNullOfOrNull { categoryTheme(it.cat) }
                ?: "other"
            Channel(key, shown.name, shown.country, theme, sorted.map { it.item }, sorted.first().q) to first.order
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
