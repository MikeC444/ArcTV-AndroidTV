package com.mangotv.app.data.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which profile this device is using right now (ArcTV Plus profiles, see docs in the web repo: ArcTV-Web/docs/PROFILES.md).
 *
 * A process-wide holder rather than a constructor argument, because the one shared account HTTP client has to read it on every
 * request (see [AccountApiHttpClient]) and every library repository's cache belongs to whichever profile is active here: a profile
 * switch wipes those caches and pulls the new profile's library from the account (see ProfileSwitcher), so what is on disk and
 * what is in here always agree. [ProfileRepository] is the only writer.
 */
object ActiveProfile {
    /** The account's own profile. Needs no header and exists for every account, so everything that predates profiles belongs to it. */
    const val DEFAULT_ID = "main"

    @Volatile
    var id: String = DEFAULT_ID
        private set

    private val _idFlow = MutableStateFlow(DEFAULT_ID)

    /** [id] as a flow, for anything that has to re-read its data when the person switches profile. */
    val idFlow: StateFlow<String> = _idFlow.asStateFlow()

    private val _kids = MutableStateFlow(false)

    /** True while a kids profile is active: no Settings, and the fixed kids genres are hidden everywhere. */
    val kids: StateFlow<Boolean> = _kids.asStateFlow()

    fun set(id: String, kids: Boolean) {
        this.id = id
        _idFlow.value = id
        _kids.value = kids
    }

    fun reset() = set(DEFAULT_ID, kids = false)
}
