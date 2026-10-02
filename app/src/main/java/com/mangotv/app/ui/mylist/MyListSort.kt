package com.mangotv.app.ui.mylist

import com.mangotv.app.data.provider.SavedListItem
import java.text.Collator

/** How My List is ordered. The labels are the pills shown beside "Sort by". */
enum class MyListSort(val label: String) {
    RECENT("Recently Added"),
    TITLE("A\u2013Z"),
    HIGHEST_RATED("Highest Rated"),
    NEWEST("Newest")
}

/**
 * [items] is the repository's own oldest-added-first order. Every sort starts from the newest-added-first reading of
 * it, so titles that tie (same rating, same year) stay in recently-added order. A title with no rating or year goes last.
 */
fun sortSavedItems(items: List<SavedListItem>, sort: MyListSort): List<SavedListItem> {
    val recent = items.asReversed()
    return when (sort) {
        MyListSort.RECENT -> recent.toList()
        MyListSort.TITLE -> {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            recent.sortedWith { a, b -> collator.compare(a.title, b.title) }
        }
        MyListSort.HIGHEST_RATED -> recent.sortedByDescending { it.rating ?: -1.0 }
        MyListSort.NEWEST -> recent.sortedByDescending { it.year ?: -1 }
    }
}
