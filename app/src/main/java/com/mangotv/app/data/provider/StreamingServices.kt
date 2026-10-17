package com.mangotv.app.data.provider

import com.mangotv.app.data.model.AddonCatalogDef

/**
 * The streaming services (Netflix, Disney+ and so on) an addon can have catalogues for. Same list and matching as the web app
 * (`domain/streamingServices.ts`). The TV has no service tiles yet; for now this only tells a catalogue-only streaming addon (Streaming Catalogs: dozens
 * of catalogues) apart from an ordinary addon, so its catalogues are not merged into one huge Home row.
 */
private class StreamingService(val id: String, val words: List<String>)

private val STREAMING_SERVICES = listOf(
    StreamingService("netflix", listOf("netflix", "nfx")),
    StreamingService("prime", listOf("prime", "primevideo", "amazon", "amp")),
    StreamingService("apple", listOf("apple", "appletv", "atp")),
    StreamingService("max", listOf("hbo", "hbomax", "hbm", "max")),
    StreamingService("disney", listOf("disney", "disneyplus", "dnp")),
    StreamingService("paramount", listOf("paramount", "paramountplus", "pmp")),
    StreamingService("hulu", listOf("hulu", "hlu")),
    StreamingService("peacock", listOf("peacock", "pcp")),
    StreamingService("iplayer", listOf("iplayer", "bbciplayer")),
    StreamingService("shudder", listOf("shudder", "shd")),
    StreamingService("crunchyroll", listOf("crunchyroll", "cru")),
    StreamingService("skyshowtime", listOf("skyshowtime", "sst")),
    StreamingService("britbox", listOf("britbox", "bbo")),
    StreamingService("itvx", listOf("itvx", "itv")),
    StreamingService("channel4", listOf("channel4", "all4", "al4")),
    StreamingService("discovery", listOf("discovery", "discoveryplus", "dpe")),
    StreamingService("sky", listOf("sky", "sgo")),
    StreamingService("acorn", listOf("acorn", "acorntv", "act")),
    StreamingService("canal", listOf("canal", "canalplus", "cpd")),
    StreamingService("claro", listOf("claro", "clarovideo", "clv")),
    StreamingService("criterion", listOf("criterion", "crc")),
    StreamingService("curiosity", listOf("curiosity", "curiositystream", "cts")),
    StreamingService("globoplay", listOf("globoplay", "gop")),
    StreamingService("hayu", listOf("hayu", "hay")),
    StreamingService("iqiyi", listOf("iqiyi", "iqi")),
    StreamingService("jiohotstar", listOf("jiohotstar", "hotstar", "jhs")),
    StreamingService("magellan", listOf("magellantv", "magellan", "mgl")),
    StreamingService("movistar", listOf("movistar", "mp9")),
    StreamingService("mubi", listOf("mubi", "mbi")),
    StreamingService("netflixkids", listOf("netflixkids", "nfk")),
    StreamingService("nlziet", listOf("nlziet", "nlz")),
    StreamingService("sonyliv", listOf("sonyliv")),
    StreamingService("starz", listOf("starz", "stz")),
    StreamingService("videoland", listOf("videoland", "vil")),
    StreamingService("viki", listOf("viki", "vik")),
    StreamingService("zee5", listOf("zee5", "z5", "zee"))
)

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
