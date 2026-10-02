package com.mangotv.app.data.profile

import kotlinx.serialization.Serializable

/**
 * ArcTV Plus profiles: up to [PROFILE_LIMIT] per account in any adult / kids mix, each with its own library (My List, Continue
 * Watching, history, settings, addons, Like / Not for me) and an optional 4-digit PIN. Plus only. The web app was first; its
 * docs/PROFILES.md is the shared contract, and the backend (`/user/profiles`, the `X-ArcTV-Profile` header) is in server/.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** One of [AVATARS]' ids (the web app and this one draw the same ids their own way). */
    val avatar: String,
    /** "adult" or "kids". */
    val kind: String = KIND_ADULT,
    /** The PIN itself never leaves the server; this only says one is needed to open, change or remove the profile. */
    val hasPin: Boolean = false,
    /** The account's own profile: always there, never removed, never a kids profile. */
    val isDefault: Boolean = false
) {
    val isKids: Boolean get() = kind == KIND_KIDS
}

const val KIND_ADULT = "adult"
const val KIND_KIDS = "kids"

/** An account can have this many profiles. (The backend enforces it too.) */
const val PROFILE_LIMIT = 5
const val PROFILE_NAME_MAX = 24
const val PIN_LENGTH = 4

fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

/** A preset avatar: a coloured tile with a glyph. The ids are what is stored and what the web app also uses. */
data class Avatar(val id: String, val label: String, val glyph: String, val from: Long, val to: Long)

val AVATARS: List<Avatar> = listOf(
    Avatar("sunrise", "Sunrise", "🌄", 0xFFFF9A3D, 0xFFFF3D68),
    Avatar("ocean", "Ocean", "🌊", 0xFF19E6FF, 0xFF2F80FF),
    Avatar("forest", "Forest", "🌲", 0xFF2DD9A8, 0xFF1F8F5F),
    Avatar("violet", "Violet", "🔮", 0xFF9B5CFF, 0xFF5A3DF0),
    Avatar("ember", "Ember", "🔥", 0xFFFF7A3D, 0xFFC8231A),
    Avatar("mint", "Mint", "🍃", 0xFF7AF0C9, 0xFF19B4A8),
    Avatar("astro", "Astronaut", "🧑‍🚀", 0xFF4F7CFF, 0xFF1B2A6B),
    Avatar("monster", "Monster", "👾", 0xFFFFC83D, 0xFFFF7A3D),
    Avatar("fox", "Fox", "🦊", 0xFFFF9F5A, 0xFFD9531E),
    Avatar("robot", "Robot", "🤖", 0xFF9AA7BD, 0xFF4B566B),
    Avatar("wave", "Wave", "🏄", 0xFF3DD6FF, 0xFF2A62FF),
    Avatar("bolt", "Bolt", "⚡", 0xFFFFE14D, 0xFFFF9D1F)
)

fun avatarById(id: String): Avatar = AVATARS.firstOrNull { it.id == id } ?: AVATARS.first()

/**
 * Genres a kids profile never shows (Home, Movies, TV Shows, Search, Genres, "You may also like"), on top of whatever the profile
 * blocks itself. It works on the genres an addon reports: a title that comes with no genres can't be matched (same limit as
 * Blocked Genres).
 */
val KIDS_BLOCKED_GENRES: List<String> = listOf("Horror", "Thriller", "Crime", "War", "Mystery", "Film-Noir", "Adult")

/**
 * Does this launch start at "Who's watching?": the account has Plus and more than one profile to choose from, and none has been
 * picked yet since the app started.
 */
fun needsProfilePicker(supported: Boolean, plus: Boolean, profileCount: Int, chosen: Boolean): Boolean =
    supported && plus && profileCount > 1 && !chosen

/** What the profile list should show: everything with Plus, only the account's own profile without it (nothing is deleted when Plus lapses). */
fun visibleProfiles(all: List<Profile>, plus: Boolean): List<Profile> = if (plus) all else all.filter { it.isDefault }

/** How a profile's PIN changes in an edit: left as it is, removed, or set to a new one. */
sealed interface PinChange {
    data object Keep : PinChange
    data object Remove : PinChange
    data class Set(val pin: String) : PinChange
}
