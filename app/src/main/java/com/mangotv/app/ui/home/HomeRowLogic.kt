package com.mangotv.app.ui.home

import com.mangotv.app.data.model.HomeSection

/**
 * A title appears in only one row: the first one (in the order shown) that holds it. Later rows lose their copy, and a
 * row left with nothing is dropped. Call it with the rows exactly as they will be displayed (ordered, hidden rows
 * removed), so a hidden row never "uses up" a title.
 */
fun dedupeSections(sections: List<HomeSection>): List<HomeSection> {
    val seen = HashSet<String>()
    val out = ArrayList<HomeSection>(sections.size)
    for (section in sections) {
        val items = section.items.filter { seen.add(it.id) }
        if (items.isNotEmpty()) out += if (items.size == section.items.size) section else section.copy(items = items)
    }
    return out
}

/**
 * Continue Watching keeps only titles that no catalogue row shows (they already have a place on the page). Returns
 * null when nothing is left, so the row disappears instead of showing empty.
 */
fun withoutShownTitles(continueWatching: HomeSection, catalogueRows: List<HomeSection>): HomeSection? {
    val shown = catalogueRows.flatMapTo(HashSet()) { row -> row.items.map { it.id } }
    val kept = continueWatching.items.filter { it.id !in shown }
    return if (kept.isEmpty()) null else if (kept.size == continueWatching.items.size) continueWatching else continueWatching.copy(items = kept)
}
