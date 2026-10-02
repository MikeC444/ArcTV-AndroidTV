package com.mangotv.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Wraps the whole app: shows [UpdatePopup] over [content] whenever an update is available and hasn't been dismissed.
 * [suppressed] hides it without losing state -- passed true while the video player is active, the same way
 * MangoNavHost already silences other chrome (nav sounds, the back-press toast) during playback. While the
 * "allow installing updates" prompt is up the popup steps aside, so the two never stack.
 */
@Composable
fun UpdatePromptHost(
    viewModel: UpdateViewModel,
    suppressed: Boolean,
    content: @Composable () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val update = state.update
    val showPopup = state.showBanner && update != null && !suppressed && !state.showUnknownSourcesDialog

    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }

    if (showPopup && update != null) {
        UpdatePopup(
            state = state,
            update = update,
            onDownload = viewModel::downloadUpdate,
            onInstall = viewModel::installUpdateOrRequestPermission,
            onDismiss = viewModel::dismissBanner
        )
    }

    if (state.showUnknownSourcesDialog) {
        UpdateUnknownSourcesOverlay(
            onOpenSettings = viewModel::openUnknownSourcesSettings,
            onDismiss = viewModel::dismissUnknownSourcesDialog
        )
    }
}

@Composable
private fun UpdateUnknownSourcesOverlay(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val openSettingsFocusRequester = remember { FocusRequester() }
    // This overlay is a plain Box drawn over everything, not a real dialog, so nothing moves D-pad focus onto it
    // automatically; without this the button is unreachable.
    LaunchedEffect(Unit) {
        runCatching { openSettingsFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MangoSurfaceHigh)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Allow installing updates",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Text(
                text = "Arc TV needs permission to install app updates it downloads. " +
                    "You'll be taken to a settings screen -- allow it there, then come back and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.padding(top = 20.dp))
            Row {
                MangoButton(
                    text = "Open Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                    style = MangoButtonStyle.FILLED,
                    backgroundOverride = Color.White,
                    focusRequester = openSettingsFocusRequester
                )
                Spacer(modifier = Modifier.padding(start = 12.dp))
                MangoButton(
                    text = "Cancel",
                    icon = Icons.Filled.Close,
                    onClick = onDismiss,
                    style = MangoButtonStyle.GLASS,
                    clickSound = ClickSound.BACK
                )
            }
        }
    }
}
