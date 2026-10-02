package com.mangotv.app.data.cast

import com.mangotv.app.data.model.CastMember
import com.mangotv.app.data.network.CastEntryDto
import java.text.Normalizer

private val combiningMarks = Regex("\\p{M}+")
private val nonAlphanumeric = Regex("[^a-z0-9]+")

// Matches people across sources that spell a name slightly differently: ignores case, accents and punctuation.
private fun nameKey(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase()
        .replace(nonAlphanumeric, " ")
        .trim()

/**
 * Fills in the photos and characters an addon didn't send, matching people by name. Whatever the addon did send is
 * kept exactly as it is -- its order, its photos, its characters. If the addon sent no cast at all, TMDB's list is used.
 * Returns [addon] itself, untouched, when there is nothing to add.
 */
fun mergeCast(addon: List<CastMember>, tmdb: List<CastEntryDto>): List<CastMember> {
    if (tmdb.isEmpty()) return addon
    if (addon.isEmpty()) return tmdb.map { CastMember(name = it.name, role = it.character, photoUrl = it.photo) }
    val byName = HashMap<String, CastEntryDto>()
    for (entry in tmdb) byName.putIfAbsent(nameKey(entry.name), entry)
    return addon.map { member ->
        if (member.photoUrl != null && member.role != null) return@map member
        val match = byName[nameKey(member.name)] ?: return@map member
        member.copy(photoUrl = member.photoUrl ?: match.photo, role = member.role ?: match.character)
    }
}
