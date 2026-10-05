package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.player.AudioChannelMode
import com.mangotv.app.ui.player.DevicePlayerPrefs
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/** The "no preference" entry reads "Automatic" here (the file's own default track), unlike the subtitle list's "System Default". */
val AudioLanguageOptions: List<SubtitleLanguageOption> =
    SubtitleLanguageOptions.map { if (it.code == null) it.copy(label = "Automatic") else it }

/**
 * Settings > Audio. Three things:
 *  - Audio Passthrough: the same switch as the player's Settings > Advanced (both read and write [DevicePlayerPrefs], so they always
 *    agree). Kept on this device only, not synced: what a TV or receiver can play is a property of the device.
 *  - Speakers: the most channels to send out (Auto / Stereo / 5.1), also device-only; surround is mixed down to fit.
 *  - Default Language: the preferred audio language, synced to the account like the subtitle language.
 * A change here applies to the next video; the player's own passthrough switch applies instantly.
 *
 * A ColumnScope extension hosted by SettingsScreen's detail pane, with its toggle row first and carrying the pane's focus
 * wiring -- the same shape as SubtitleSettingsContent.
 */
@Composable
fun ColumnScope.AudioSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    viewModel: AudioSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var passthrough by remember { mutableStateOf(DevicePlayerPrefs.audioPassthrough(context)) }
    var channelMode by remember { mutableStateOf(DevicePlayerPrefs.audioChannelMode(context)) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        // Headroom for the focused row's scale-up, which the list would otherwise clip (see SubtitleSettingsContent).
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "passthrough") {
            TvFocusSurface(
                onClick = {
                    passthrough = !passthrough
                    DevicePlayerPrefs.setAudioPassthrough(context, passthrough)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
                focusedScale = 1.02f,
                backgroundColor = MangoSurface,
                borderColor = TextPrimary,
                focusRequester = contentFocusRequester,
                focusUp = navFocusRequester,
                focusLeft = sidebarFocusRequester
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audio Passthrough",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = passthrough,
                        onCheckedChange = {
                            passthrough = it
                            DevicePlayerPrefs.setAudioPassthrough(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = ArcAccent)
                    )
                }
            }
        }
        item(key = "description") {
            Text(
                text = "Turn it off if a source plays with no sound.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item(key = "speakers_title") {
            Text(
                text = "Speakers",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item(key = "speakers_description") {
            Text(
                text = "Automatically filters the source list to this audio type (you can change it there any time) and mixes surround " +
                    "sound down to fit. Applies to the next video. Coming in a future update: the auto source picker will choose this audio type for you.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(ChannelModeOptions, key = { it.first.wire }) { (mode, label) ->
            LanguageOptionRow(
                label = label,
                selected = mode == channelMode,
                onClick = {
                    channelMode = mode
                    DevicePlayerPrefs.setAudioChannelMode(context, mode)
                }
            )
        }
        item(key = "language_title") {
            Text(
                text = "Default Language",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item(key = "language_description") {
            Text(
                text = "Picks the audio track in this language when a video has one.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(AudioLanguageOptions, key = { "audio_" + (it.code ?: "automatic") }) { option ->
            LanguageOptionRow(
                label = option.label,
                selected = option.code == preferences.defaultAudioLanguage,
                onClick = { viewModel.setDefaultAudioLanguage(option.code) }
            )
        }
    }
}

private val ChannelModeOptions: List<Pair<AudioChannelMode, String>> = listOf(
    AudioChannelMode.AUTO to "Automatic (what your TV supports)",
    AudioChannelMode.STEREO to "Stereo",
    AudioChannelMode.SURROUND_5_1 to "5.1 surround"
)
