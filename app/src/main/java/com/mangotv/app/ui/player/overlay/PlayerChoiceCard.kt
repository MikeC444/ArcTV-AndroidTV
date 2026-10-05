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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** The ways to play a source: Arc TV's own player, VLC's engine inside Arc TV, or another player app on the device. */
enum class PlayerChoiceOption { BUILT_IN, VLC, EXTERNAL }

/**
 * "Choose player": pick how to play this source, then press Play -- nothing happens (and nothing leaves Arc TV) until then. Pressing a row
 * only selects it. [externalAvailable] false means no other player app is installed, so that row is left out and a note says so.
 * [initial] is the row that starts selected. BACK closes it (the player's overlay stack handles that), like every other menu.
 */
@Composable
fun PlayerChoiceCard(
    externalAvailable: Boolean,
    initial: PlayerChoiceOption,
    onPlay: (PlayerChoiceOption) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val start = if (initial == PlayerChoiceOption.EXTERNAL && !externalAvailable) PlayerChoiceOption.BUILT_IN else initial
    var selected by remember { mutableStateOf(start) }
    val startFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { startFocusRequester.requestFocus() } }

    val options = buildList {
        add(Triple(PlayerChoiceOption.BUILT_IN, "Built-in player", "Arc TV's normal player"))
        add(Triple(PlayerChoiceOption.VLC, "VLC engine", "Plays formats the built-in player can't; uses more power"))
        if (externalAvailable) add(Triple(PlayerChoiceOption.EXTERNAL, "Another app", "Opens a player app on this device and leaves Arc TV"))
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(MangoBackground.copy(alpha = 0.97f), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "How do you want to play this?", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            options.forEach { (option, label, description) ->
                MenuOptionRow(
                    label = label,
                    supportingText = description,
                    isSelected = option == selected,
                    onClick = { selected = option },
                    focusRequester = if (option == start) startFocusRequester else null
                )
            }
            if (!externalAvailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No other video player app is installed on this device.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MangoButton(
                    text = "Play",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlay(selected) },
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White
                )
                MangoButton(
                    text = "Cancel",
                    icon = Icons.Filled.Close,
                    onClick = onCancel,
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White
                )
            }
        }
    }
}
