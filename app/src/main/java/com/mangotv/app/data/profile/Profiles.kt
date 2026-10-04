package com.mangotv.app.data.profile

import androidx.annotation.DrawableRes
import com.mangotv.app.R
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

/** A preset avatar: an illustrated picture ([res], app/src/main/res/drawable-nodpi/avatar_<id>.webp). The ids are what is stored and what the web app also uses. */
data class Avatar(val id: String, val label: String, @DrawableRes val res: Int)

val AVATARS: List<Avatar> = listOf(
    Avatar("fox", "Fox", R.drawable.avatar_fox),
    Avatar("cat", "Cat", R.drawable.avatar_cat),
    Avatar("dog", "Dog", R.drawable.avatar_dog),
    Avatar("panda", "Panda", R.drawable.avatar_panda),
    Avatar("frog", "Frog", R.drawable.avatar_frog),
    Avatar("owl", "Owl", R.drawable.avatar_owl),
    Avatar("ghost", "Ghost", R.drawable.avatar_ghost),
    Avatar("robot", "Robot", R.drawable.avatar_robot),
    Avatar("alien", "Alien", R.drawable.avatar_alien),
    Avatar("astronaut", "Astronaut", R.drawable.avatar_astronaut),
    Avatar("raccoon", "Raccoon", R.drawable.avatar_raccoon),
    Avatar("penguin", "Penguin", R.drawable.avatar_penguin),
    Avatar("octopus", "Octopus", R.drawable.avatar_octopus),
    Avatar("dragon", "Dragon", R.drawable.avatar_dragon),
    Avatar("retro-tv", "Retro TV", R.drawable.avatar_retro_tv),
    Avatar("lion", "Lion", R.drawable.avatar_lion)
)

/** Ids from the old colour-tile set that profiles may still carry, drawn as the nearest new picture. */
private val LEGACY_AVATARS = mapOf(
    "astro" to "astronaut", "monster" to "alien", "sunrise" to "fox", "ocean" to "octopus", "forest" to "frog",
    "violet" to "ghost", "ember" to "dragon", "mint" to "owl", "wave" to "penguin", "bolt" to "robot"
)

fun avatarById(id: String): Avatar = AVATARS.firstOrNull { it.id == (LEGACY_AVATARS[id] ?: id) } ?: AVATARS.first()

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
