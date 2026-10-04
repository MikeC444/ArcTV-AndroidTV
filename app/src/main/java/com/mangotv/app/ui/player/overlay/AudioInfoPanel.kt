package com.mangotv.app.ui.player.overlay

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mangotv.app.ui.theme.TextSecondary

/** Why Audio can't be changed when the source has a single track (the web app's explanation, opened from Settings > Audio). */
@Composable
fun AudioInfoPanel(trackLabel: String?, modifier: Modifier = Modifier) {
    MenuOverlayScaffold(title = "Audio", modifier = modifier) {
        if (trackLabel != null) {
            Text(text = trackLabel, color = com.mangotv.app.ui.theme.TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "This source has a single audio track, so there is nothing to switch between. Pick another source on the Sources screen if you want a different language or mix.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
