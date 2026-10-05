package com.mangotv.app.ui.player

import androidx.compose.ui.focus.FocusRequester
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode

/**
 * Which look the VLC player's controls have. false = the Netflix-style layout (VlcControlsNetflix.kt); true brings back the original
 * layout, kept unchanged in VlcControlsClassic.kt. Both draw from the same [VlcControlsModel] and [VlcControlFocus], so flipping this is the
 * only change needed.
 */
internal const val USE_CLASSIC_VLC_CONTROLS = false

/** One focus requester per control, so the VLC screen can put the cursor back where it was after a menu closes. */
internal class VlcControlFocus {
    val back = FocusRequester()
    val play = FocusRequester()
    val rewind = FocusRequester()
    val forward = FocusRequester()
    val timeline = FocusRequester()
    val audio = FocusRequester()
    val subtitles = FocusRequester()
    val speed = FocusRequester()
    val next = FocusRequester()
    val choose = FocusRequester()
    val change = FocusRequester()
}

/** Everything the controls show and can do; the VLC screen owns the state and the player, the controls only draw and report presses. */
internal class VlcControlsModel(
    val content: Content,
    val episode: Episode?,
    val playing: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val speedLabel: String,
    val showAudio: Boolean,
    val showSubtitles: Boolean,
    val hasNextEpisode: Boolean,
    val onPlayPause: () -> Unit,
    val onSeek: (deltaMs: Long) -> Unit,
    val onAudio: () -> Unit,
    val onSubtitles: () -> Unit,
    val onSpeed: () -> Unit,
    val onNextEpisode: () -> Unit,
    val onChoosePlayer: () -> Unit,
    val onChangeSource: () -> Unit,
    val onBack: () -> Unit,
    /** Told which control has the cursor, so it can return there. */
    val onFocused: (FocusRequester) -> Unit
)
