package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.player.DevicePlayerPrefs
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Settings > Audio: the same Audio Passthrough switch as the player's Settings > Advanced (both read and write
 * [DevicePlayerPrefs], so they always agree). Kept on this device only, not synced: what a TV or receiver can play is a
 * property of the device. A change here applies to the next video; the player's own switch applies instantly.
 *
 * A ColumnScope extension hosted by SettingsScreen's detail pane, with its toggle row first and carrying the pane's focus
 * wiring -- the same shape as SubtitleSettingsContent.
 */
@Composable
fun ColumnScope.AudioSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    var passthrough by remember { mutableStateOf(DevicePlayerPrefs.audioPassthrough(context)) }

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
    }
}
