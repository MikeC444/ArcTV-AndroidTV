package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.player.formatTimestamp
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.TextPrimary

/**
 * "Pick up where you left off?" (the web app's resume card): the video waits at the saved spot until one of the buttons is pressed.
 * Resume is focused first; a different source can be picked instead.
 */
@Composable
fun ResumeCard(positionMs: Long, onResume: () -> Unit, onStartOver: () -> Unit, onChangeSource: () -> Unit, modifier: Modifier = Modifier) {
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { resumeFocus.requestFocus() } }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .padding(bottom = 56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MangoBackgroundElevated.copy(alpha = 0.96f))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = "Pick up where you left off?", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MangoButton(
                    text = "Resume from ${formatTimestamp(positionMs)}",
                    icon = Icons.Filled.PlayArrow,
                    onClick = onResume,
                    style = MangoButtonStyle.LIGHT,
                    focusRequester = resumeFocus
                )
                MangoButton(text = "Start over", icon = Icons.Filled.Replay, onClick = onStartOver)
            }
            MangoButton(text = "Choose a different source", icon = Icons.Filled.SwapHoriz, onClick = onChangeSource, compact = true)
        }
    }
}
