package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.torrent.TorrentBuffer
import com.mangotv.app.data.torrent.TorrentStorageLimit
import com.mangotv.app.ui.player.DevicePlayerPrefs
import com.mangotv.app.ui.player.PreferredPlayer
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Settings > Player: which player a title opens in the first time (VLC's engine unless changed). Kept on this device only. A title
 * remembers the player picked for it from the player's Choose player button, which wins over this default. A ColumnScope extension hosted by
 * SettingsScreen's detail pane, with its first row carrying the pane's focus wiring, like the other tabs.
 */
@Composable
fun ColumnScope.PlayerSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(DevicePlayerPrefs.defaultPlayer(context)) }
    var torrentBuffer by remember { mutableStateOf(DevicePlayerPrefs.torrentBuffer(context)) }
    var torrentStorage by remember { mutableStateOf(DevicePlayerPrefs.torrentStorageLimit(context)) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        // Headroom for the focused row's scale-up, which the list would otherwise clip (see SubtitleSettingsContent).
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "title") {
            Text(text = "Default Player", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        item(key = "description") {
            Text(
                text = "The player a title opens in the first time. Pick another from the player's Choose Player button and that title remembers it.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item(key = "vlc") {
            LanguageOptionRow(
                label = "VLC engine (recommended)",
                selected = selected == PreferredPlayer.VLC,
                onClick = {
                    selected = PreferredPlayer.VLC
                    DevicePlayerPrefs.setDefaultPlayer(context, PreferredPlayer.VLC)
                },
                focusRequester = contentFocusRequester,
                focusUp = navFocusRequester,
                focusLeft = sidebarFocusRequester
            )
        }
        item(key = "builtin") {
            LanguageOptionRow(
                label = "Built-in player",
                selected = selected == PreferredPlayer.BUILT_IN,
                onClick = {
                    selected = PreferredPlayer.BUILT_IN
                    DevicePlayerPrefs.setDefaultPlayer(context, PreferredPlayer.BUILT_IN)
                }
            )
        }
        item(key = "note") {
            Text(
                text = "VLC plays almost any file but has simpler controls. The built-in player has the full controls, next episode and surround settings.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item(key = "torrent-buffer-title") {
            Text(
                text = "Torrent Buffer",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        item(key = "torrent-buffer-description") {
            Text(
                text = "How far ahead of the picture a torrent is fetched. A larger buffer rides out slow patches but uses more storage and data.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TorrentBuffer.entries.forEach { option ->
            item(key = "torrent-buffer-${option.wire}") {
                LanguageOptionRow(
                    label = option.label,
                    selected = torrentBuffer == option,
                    onClick = {
                        torrentBuffer = option
                        DevicePlayerPrefs.setTorrentBuffer(context, option)
                    }
                )
            }
        }
        item(key = "torrent-storage-title") {
            Text(
                text = "Torrent Storage Limit",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        item(key = "torrent-storage-description") {
            Text(
                text = "The most temporary space one torrent may use. Played data is dropped to stay under it, and it is all deleted when you stop watching.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TorrentStorageLimit.entries.forEach { option ->
            item(key = "torrent-storage-${option.wire}") {
                LanguageOptionRow(
                    label = option.label,
                    selected = torrentStorage == option,
                    onClick = {
                        torrentStorage = option
                        DevicePlayerPrefs.setTorrentStorageLimit(context, option)
                    }
                )
            }
        }
    }
}
