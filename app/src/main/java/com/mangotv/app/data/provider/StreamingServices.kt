package com.mangotv.app.data.provider

import com.mangotv.app.data.model.AddonCatalogDef

/**
 * The streaming services (Netflix, Disney+ and so on) Home can offer as tiles. Same list, order, words and rules as the web app
 * (`domain/streamingServices.ts`): a tile only exists while an installed addon has a catalogue for the service, found by the catalogue's own name or id.
 */
class StreamingService(
    val id: String,
    val name: String,
    /** Words (the id or name split on anything that is not a letter or number, lower case) that mean this service. Includes the short ids some addons use. */
    val words: List<String>,
    /** Switched off until the person turns it on (a service that is not listed first is more likely clutter than wanted). */
    val off: Boolean = false
)

/**
 * In the order the tiles appear until the person puts them in their own: the best known first, Shudder further back, then the rest. `off` marks a
 * service that is switched off until the person turns it on (see [isServiceVisible]), so a Home with many services stays tidy.
 */
val STREAMING_SERVICES: List<StreamingService> = listOf(
    StreamingService("netflix", "Netflix", listOf("netflix", "nfx")),
    StreamingService("prime", "Prime Video", listOf("prime", "primevideo", "amazon", "amp")),
    StreamingService("apple", "Apple TV+", listOf("apple", "appletv", "atp")),
    StreamingService("max", "HBO Max", listOf("hbo", "hbomax", "hbm", "max")),
    StreamingService("disney", "Disney+", listOf("disney", "disneyplus", "dnp")),
    StreamingService("paramount", "Paramount+", listOf("paramount", "paramountplus", "pmp")),
    StreamingService("hulu", "Hulu", listOf("hulu", "hlu")),
    StreamingService("peacock", "Peacock", listOf("peacock", "pcp")),
    StreamingService("iplayer", "BBC iPlayer", listOf("iplayer", "bbciplayer")),
    StreamingService("shudder", "Shudder", listOf("shudder", "shd")),
    StreamingService("crunchyroll", "Crunchyroll", listOf("crunchyroll", "cru"), off = true),
    StreamingService("skyshowtime", "SkyShowtime", listOf("skyshowtime", "sst"), off = true),
    StreamingService("britbox", "BritBox", listOf("britbox", "bbo"), off = true),
    StreamingService("itvx", "ITVX", listOf("itvx", "itv"), off = true),
    StreamingService("channel4", "Channel 4", listOf("channel4", "all4", "al4"), off = true),
    StreamingService("discovery", "Discovery+", listOf("discovery", "discoveryplus", "dpe"), off = true),
    StreamingService("sky", "Sky", listOf("sky", "sgo"), off = true),
    StreamingService("acorn", "Acorn TV", listOf("acorn", "acorntv", "act"), off = true),
    StreamingService("canal", "Canal+", listOf("canal", "canalplus", "cpd"), off = true),
    StreamingService("claro", "Claro video", listOf("claro", "clarovideo", "clv"), off = true),
    StreamingService("criterion", "Criterion Channel", listOf("criterion", "crc"), off = true),
    StreamingService("curiosity", "Curiosity Stream", listOf("curiosity", "curiositystream", "cts"), off = true),
    StreamingService("globoplay", "Globoplay", listOf("globoplay", "gop"), off = true),
    StreamingService("hayu", "Hayu", listOf("hayu", "hay"), off = true),
    StreamingService("iqiyi", "iQIYI", listOf("iqiyi", "iqi"), off = true),
    StreamingService("jiohotstar", "JioHotstar", listOf("jiohotstar", "hotstar", "jhs"), off = true),
    StreamingService("magellan", "MagellanTV", listOf("magellantv", "magellan", "mgl"), off = true),
    StreamingService("movistar", "Movistar+", listOf("movistar", "mp9"), off = true),
    StreamingService("mubi", "MUBI", listOf("mubi", "mbi"), off = true),
    StreamingService("netflixkids", "Netflix Kids", listOf("netflixkids", "nfk"), off = true),
    StreamingService("nlziet", "NLZIET", listOf("nlziet", "nlz"), off = true),
    StreamingService("sonyliv", "Sony LIV", listOf("sonyliv"), off = true),
    StreamingService("starz", "Starz", listOf("starz", "stz"), off = true),
    StreamingService("videoland", "Videoland", listOf("videoland", "vil"), off = true),
    StreamingService("viki", "Viki", listOf("viki", "vik"), off = true),
    StreamingService("zee5", "ZEE5", listOf("zee5", "z5", "zee"), off = true)
)

private val SERVICES_BY_ID = STREAMING_SERVICES.associateBy { it.id }

fun streamingServiceById(id: String): StreamingService? = SERVICES_BY_ID[id]

/** Words of seven letters or more also count when they sit inside the whole name run together ("Netflix Kids" is "netflixkids"). */
private const val LONG_WORD = 7

/** An addon with catalogues for at least this many services is a streaming-catalogue addon (one is just a catalogue called "Netflix Originals"). */
private const val MIN_SERVICES_FOR_SERVICE_ADDON = 3

private fun tokens(text: String?): List<String> = (text ?: "").lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

/**
 * Which service a catalogue is for (by its name first, then its id), or null when it is not one of them. When more than one service fits
 * ("Netflix Kids" is both Netflix Kids and Netflix) the longest matching word wins.
 */
internal fun serviceOfCatalog(name: String?, id: String): String? {
    for (source in listOf(name, id)) {
        val found = tokens(source)
        if (found.isEmpty()) continue
        val compact = found.joinToString("")
        var best: Pair<String, Int>? = null
        for (service in STREAMING_SERVICES) {
            for (word in service.words) {
                val hit = word in found || (word.length >= LONG_WORD && compact.contains(word))
                if (hit && (best == null || word.length > best.second)) best = service.id to word.length
            }
        }
        if (best != null) return best.first
    }
    return null
}

/**
 * The catalogues of an addon that is made of streaming-service catalogues (at least [MIN_SERVICES_FOR_SERVICE_ADDON] services), or none for an
 * ordinary addon. Only catalogues that need nothing from the caller count, as on the web.
 */
internal fun streamingServiceCatalogs(catalogs: List<AddonCatalogDef>): List<AddonCatalogDef> {
    val matched = catalogs.filter { c -> c.allExtras.none { it.isRequired } }.mapNotNull { c -> serviceOfCatalog(c.name, c.id)?.let { c to it } }
    return if (matched.map { it.second }.distinct().size >= MIN_SERVICES_FOR_SERVICE_ADDON) matched.map { it.first } else emptyList()
}

/** Service ids in tile order, each once. Ids that are not known are dropped. */
fun orderServices(ids: Iterable<String>): List<String> {
    val wanted = ids.toSet()
    return STREAMING_SERVICES.filter { it.id in wanted }.map { it.id }
}

/** The id Home Rows keeps in its hidden list for a service's tile (the same list that hides ordinary rows, so it is saved and synced with them). */
fun serviceRowId(service: String): String = "streaming:$service"

/** The marker Home Rows keeps in the same hidden list when the person has turned a switched-off-by-default service on. */
fun serviceOnId(service: String): String = "streaming-on:$service"

/** Is this service shown as a tile? Not when the person hid it; a service that is off by default only when they have turned it on. */
fun isServiceVisible(service: String, hiddenRowIds: Set<String>): Boolean {
    if (serviceRowId(service) in hiddenRowIds) return false
    return if (SERVICES_BY_ID[service]?.off == true) serviceOnId(service) in hiddenRowIds else true
}

/** The hidden list after the person turns a service on or off in Home Rows. */
fun withServiceVisible(hiddenRowIds: Set<String>, service: String, visible: Boolean): Set<String> {
    val rest = hiddenRowIds - serviceRowId(service) - serviceOnId(service)
    if (!visible) return rest + serviceRowId(service)
    return if (SERVICES_BY_ID[service]?.off == true) rest + serviceOnId(service) else rest
}

/** Which of these services are shown as tiles. */
fun visibleServices(services: List<String>, hiddenRowIds: Set<String>): List<String> = services.filter { isServiceVisible(it, hiddenRowIds) }

/** Is this entry in the Home Rows order a streaming service's (rather than an ordinary row's)? */
fun isServiceRowId(id: String): Boolean = id.startsWith("streaming:")

/**
 * Services in the person's own order: the ones they have placed (in the Home Rows order list, as `streaming:<id>`) in that order, then any not placed yet
 * in the default tile order. So a service an addon brings later joins at the end instead of jumping to the front.
 */
fun inUserOrder(services: List<String>, order: List<String>): List<String> {
    val placed = order.filter(::isServiceRowId).map { it.removePrefix("streaming:") }.filter { it in services }.distinct()
    val placedSet = placed.toSet()
    return placed + services.filter { it !in placedSet }
}

/** One service moved up or down among [current]; null when it cannot move that way. */
fun moveService(current: List<String>, service: String, delta: Int): List<String>? {
    val ids = current.toMutableList()
    val at = ids.indexOf(service)
    val to = at + delta
    if (at < 0 || to < 0 || to >= ids.size) return null
    ids.removeAt(at)
    ids.add(to, service)
    return ids
}

/** The Home Rows order list with the services' part replaced by [services] (in that order); ordinary rows' entries are kept as they were. */
fun withServiceOrder(order: List<String>, services: List<String>): List<String> = order.filterNot(::isServiceRowId) + services.map(::serviceRowId)

/** The Home Rows order list after the ordinary rows were reordered as [displayed]: the streaming services' entries (which Settings does not show among the rows) are kept as they were. */
fun orderKeepingServices(displayed: List<String>, existing: List<String>): List<String> = displayed.filterNot(::isServiceRowId) + existing.filter(::isServiceRowId)

/** The catalogues an addon has for each streaming service: ones that need nothing from the caller (movies and series). A service with none is absent. */
internal fun serviceCatalogsByService(catalogs: List<AddonCatalogDef>): Map<String, List<AddonCatalogDef>> {
    val byService = LinkedHashMap<String, MutableList<AddonCatalogDef>>()
    for (c in catalogs) {
        if (c.allExtras.any { it.isRequired }) continue
        val service = serviceOfCatalog(c.name, c.id) ?: continue
        byService.getOrPut(service) { mutableListOf() } += c
    }
    return byService
}
