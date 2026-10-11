package com.mangotv.app.ui.player

import com.mangotv.app.data.network.PlaybackOutcomeDto

/** A viewing that ends sooner than this after the picture appeared counts as "short" (the person left, or it wasn't worth staying). */
const val SHORT_WATCH_MS = 30_000L

/**
 * Watches one viewing in the player and reports once, when it ends, how it went: played for a while ("ok"), left soon after the picture
 * appeared ("short"), the player failed ("error") or nothing ever appeared ("never_started"). It sees no title and no stream address,
 * only timings and the source's labels, and it never changes playback. [finish] is safe to call more than once.
 *
 * Call [onPlaying] when the picture is moving, [onPaused] when it stops (pause, buffering), [onEnded] at the natural end and [onError]
 * when the player reports a failure. Watched time is the time spent actually playing, so a seek doesn't inflate it.
 */
class PlaybackHealthTracker(
    private val engine: String,
    private val deviceModel: String?,
    private val resolution: String?,
    private val codec: String?,
    private val hdr: String?,
    private val sourcesTried: Int,
    private val report: (PlaybackOutcomeDto) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val openedAt = clock()
    private var startMs: Long? = null
    private var playingSince: Long? = null
    private var watchedMs = 0L
    private var errorKind: String? = null
    private var ended = false
    private var finished = false

    fun onPlaying() {
        if (finished) return
        val now = clock()
        if (startMs == null) startMs = now - openedAt
        if (playingSince == null) playingSince = now
    }

    fun onPaused() {
        settle()
    }

    fun onEnded() {
        settle()
        ended = true
    }

    fun onError(kind: String) {
        if (finished) return
        settle()
        if (errorKind == null) errorKind = kind.take(200)
    }

    /** The outcome so far; what [finish] would report. */
    fun outcome(): String = when {
        ended || watchedMs >= SHORT_WATCH_MS -> "ok"
        errorKind != null -> "error"
        startMs == null -> "never_started"
        else -> "short"
    }

    fun finish() {
        if (finished) return
        settle()
        finished = true
        report(
            PlaybackOutcomeDto(
                engine = engine,
                deviceModel = deviceModel,
                resolution = resolution,
                codec = codec,
                hdr = hdr,
                startMs = startMs,
                watchedMs = watchedMs,
                outcome = outcome(),
                errorKind = errorKind,
                sourcesTried = sourcesTried
            )
        )
    }

    private fun settle() {
        val since = playingSince ?: return
        watchedMs += (clock() - since).coerceAtLeast(0)
        playingSince = null
    }
}

/** "Dolby Vision", "HDR" or null, read from a release name. */
fun hdrLabelOf(releaseTitle: String): String? = when {
    Regex("""dolby[ .-]?vision|\bdovi\b|\bdv\b""", RegexOption.IGNORE_CASE).containsMatchIn(releaseTitle) -> "Dolby Vision"
    Regex("""hdr10\+?|\bhdr\b""", RegexOption.IGNORE_CASE).containsMatchIn(releaseTitle) -> "HDR"
    else -> null
}

/**
 * How many sources the person has opened for the same title in this sitting: 1 for the first, 2 for the next one tried soon after, and so
 * on. A different title, or ten quiet minutes, starts again at 1. In memory only.
 */
object SourceAttempts {
    private const val SITTING_MS = 10 * 60_000L
    private var lastKey: String? = null
    private var lastAt = 0L
    private var count = 0

    @Synchronized
    fun next(titleKey: String, now: Long = System.currentTimeMillis()): Int {
        if (titleKey != lastKey || now - lastAt > SITTING_MS) count = 0
        lastKey = titleKey
        lastAt = now
        count += 1
        return count
    }
}
