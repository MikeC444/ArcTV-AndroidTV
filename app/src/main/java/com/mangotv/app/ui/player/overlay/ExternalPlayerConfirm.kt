package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * The "are you sure" step before leaving ArcTV for another player: nothing opens until the person presses the confirm button.
 * [available] false means no player app is installed, so the confirm button is replaced by an explanation. BACK closes it
 * (the player's overlay stack handles that), like every other menu.
 */
@Composable
fun ExternalPlayerConfirm(
    available: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocusRequester.requestFocus() } }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(MangoBackground.copy(alpha = 0.97f), RoundedCornerShape(16.dp))
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Play in an external player?", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (available) {
                    "This leaves ArcTV and opens the video in another player app on this device. " +
                        "Your progress won't be saved to Continue Watching while you watch there."
                } else {
                    "No other video player app is installed on this device. Install one (such as VLC or Just Player) and try again."
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (available) {
                    MangoButton(
                        text = "Open External Player",
                        icon = Icons.Filled.OpenInNew,
                        onClick = onConfirm,
                        style = MangoButtonStyle.GLASS,
                        borderColor = Color.White,
                        focusRequester = firstFocusRequester
                    )
                }
                MangoButton(
                    text = if (available) "Cancel" else "OK",
                    icon = Icons.Filled.Close,
                    onClick = onCancel,
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White,
                    focusRequester = if (available) null else firstFocusRequester
                )
            }
        }
    }
}
