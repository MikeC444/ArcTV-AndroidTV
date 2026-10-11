package com.mangotv.app.data.player

import com.mangotv.app.data.model.Stream

/**
 * The source the person just picked, carried from the source list to the player in memory.
 *
 * The player used to ask every addon for its sources again and look the chosen one up by its id. An addon that hands out a fresh id
 * (or address) on every request, as some addons do, was then never found again, and the person got "This source is no longer
 * available" without anything having played. Handing the chosen source over avoids both the second lookup and that failure.
 * In memory only, and only the latest pick: a retry of the same source still finds it, a different title never sees it.
 */
object StreamHandoff {
    @Volatile
    private var held: Stream? = null

    fun put(stream: Stream) {
        held = stream
    }

    /** The held source when it is the one with this id, else null. */
    fun get(streamId: String): Stream? = held?.takeIf { it.id == streamId }
}
