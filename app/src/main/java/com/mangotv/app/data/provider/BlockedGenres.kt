package com.mangotv.app.data.provider

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection

private fun normalise(genre: String): String = genre.trim().lowercase()

/** The set a stored genre list turns into for matching: trimmed and lower-cased, so "Horror" and "horror " are the same genre. */
fun blockedGenreSet(genres: Collection<String>): Set<String> = genres.map(::normalise).filter { it.isNotEmpty() }.toSet()

/** True when a title carries any blocked genre. A title whose addon sent no genres can't be matched, so it is never hidden. */
fun Content.isBlockedBy(blocked: Set<String>): Boolean =
    blocked.isNotEmpty() && genres.any { normalise(it.name) in blocked }

fun List<Content>.withoutBlocked(blocked: Set<String>): List<Content> =
    if (blocked.isEmpty()) this else filterNot { it.isBlockedBy(blocked) }

/** Drops blocked titles from every row, then any row left empty. (Named apart so it doesn't clash with the title-list version once generics are erased.) */
@JvmName("withoutBlockedSections")
fun List<HomeSection>.withoutBlocked(blocked: Set<String>): List<HomeSection> =
    if (blocked.isEmpty()) this else map { it.copy(items = it.items.withoutBlocked(blocked)) }.filter { it.items.isNotEmpty() }

/** Genre options minus the blocked ones, for the Genres tab and the Movies / TV Shows drop-down. */
fun List<String>.withoutBlockedNames(blocked: Set<String>): List<String> =
    if (blocked.isEmpty()) this else filterNot { normalise(it) in blocked }
