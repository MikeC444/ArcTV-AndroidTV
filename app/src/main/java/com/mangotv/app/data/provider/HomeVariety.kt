package com.mangotv.app.data.provider

import com.mangotv.app.data.recommend.seededRandom
import java.time.LocalDate
import kotlin.math.pow

/**
 * Keeping Home fresh without making it irrelevant (the same rules as the web app's `domain/homeVariety.ts`). A catalogue row that always shows the
 * first 100 titles shows the same films to everyone, every day. Instead each row draws from a few pages deep into the same ranking (so everything is
 * still popular / new / well rated), and the order within it is a gentle, seeded shuffle that favours the higher ranks. The seed is the account
 * plus the day, so a row is steady while you use the app and different tomorrow.
 */
object HomeVariety {
    /** Set by Home before it fetches (account + day), read by the addon provider while it builds rows. */
    @Volatile
    var seed: String = ""

    /** The genres Home shows as rows, in this order: wide appeal. Every other genre stays on the Movies and TV Shows pages. */
    val HOME_GENRES = listOf("Action", "Comedy", "Drama", "Thriller", "Horror", "Sci-Fi", "Crime", "Animation", "Documentary")

    /** Chance of reading page 1, 2 or 3 (100 titles each) of a ranking today: mostly the top, sometimes deeper. New releases go stale faster. */
    val PAGE_WEIGHTS = listOf(5, 3, 2)
    val NEW_PAGE_WEIGHTS = listOf(6, 4)

    fun seedFor(userId: String?, today: LocalDate = LocalDate.now()): String = "${userId ?: "guest"}|${dayStamp(today)}"
}

/** A stable 32-bit number from text (FNV-1a). */
fun hashSeed(text: String): Int {
    var hash = 0x811c9dc5.toInt()
    for (ch in text) {
        hash = hash xor ch.code
        hash *= 0x01000193
    }
    return hash
}

/** The date as YYYY-MM-DD (local), so the rows change at the person's own midnight. */
fun dayStamp(date: LocalDate = LocalDate.now()): String = "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

/** Which catalogue page (0 = the top 100) a row reads today. [weights] is the chance of each page; the first is the likeliest. */
fun pickPage(seed: String, rowKey: String, weights: List<Int>): Int {
    val random = seededRandom(hashSeed("$seed|$rowKey|page"))()
    val total = weights.sum().toDouble()
    var acc = 0.0
    for ((index, weight) in weights.withIndex()) {
        acc += weight / total
        if (random < acc) return index
    }
    return weights.lastIndex
}

/**
 * A gentle shuffle: every item keeps a chance to land near the top but a better-ranked one is likelier to. Weight falls with rank
 * (1 / (1 + rank / 25)), and the order is a weighted random draw (Efraimidis-Spirakis keys) from a generator seeded by [seed] + [rowKey].
 */
fun <T> varyOrder(items: List<T>, seed: String, rowKey: String): List<T> {
    val random = seededRandom(hashSeed("$seed|$rowKey|order"))
    return items
        .mapIndexed { rank, item -> item to random().pow(1.0 / (1.0 / (1.0 + rank / 25.0))) }
        .sortedByDescending { it.second }
        .map { it.first }
}

/** Which of an addon's declared genres become Home rows: the curated ones it declares, in curated order (case-insensitive). */
fun homeGenresFor(declared: List<String>): List<String> {
    val byLower = declared.associateBy { it.lowercase() }
    return HomeVariety.HOME_GENRES.mapNotNull { byLower[it.lowercase()] }
}
