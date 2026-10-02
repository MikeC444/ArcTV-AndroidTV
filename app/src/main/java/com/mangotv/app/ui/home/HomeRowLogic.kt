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

/** Where focus returns to on Home: [rowIndex] into the sections and [itemIndex] into that row. */
data class FocusRestoreTarget(val rowIndex: Int, val itemIndex: Int)

/**
 * Finds the remembered poster again after Home is re-entered. Matches by id, never by position, so a row list that
 * changed in the meantime can't send focus to the wrong title. Null when either the row or the title is gone, or
 * nothing was remembered (focus was in the nav bar or hero), which means "no restore".
 */
fun findFocusRestoreTarget(sections: List<HomeSection>, sectionId: String?, contentId: String?): FocusRestoreTarget? {
    if (sectionId == null || contentId == null) return null
    val rowIndex = sections.indexOfFirst { it.id == sectionId }
    if (rowIndex < 0) return null
    val itemIndex = sections[rowIndex].items.indexOfFirst { it.id == contentId }
    if (itemIndex < 0) return null
    return FocusRestoreTarget(rowIndex, itemIndex)
}
