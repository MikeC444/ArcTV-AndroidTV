package com.mangotv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** How long "Up next" counts down before the next episode starts by itself (when Auto Play Next Episode is on). */
const val UP_NEXT_SECONDS = 5

/** The "Next episode" button bottom right for the last minute of an episode, with "S1 E3 • title" under it. */
@Composable
fun NextEpisodeOffer(next: NextEpisode, onGo: () -> Unit, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(end = 40.dp, bottom = 120.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MangoButton(text = "Next episode", icon = Icons.Filled.SkipNext, onClick = onGo, style = MangoButtonStyle.LIGHT, focusRequester = focusRequester)
        Text(
            text = "S${next.season} E${next.episode} • ${next.title}",
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** After an episode ends with Auto Play Next Episode on: a short countdown, then the next episode; Cancel stays on the finished one. */
@Composable
fun UpNextCard(next: NextEpisode, onGo: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var seconds by remember(next) { mutableIntStateOf(UP_NEXT_SECONDS) }
    val goFocus = remember { FocusRequester() }
    LaunchedEffect(next) {
        runCatching { goFocus.requestFocus() }
        while (seconds > 0) {
            delay(1_000L)
            seconds -= 1
        }
        onGo()
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier
                .padding(end = 40.dp, bottom = 56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MangoBackgroundElevated.copy(alpha = 0.96f))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Up next in $seconds", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(text = "S${next.season} E${next.episode} • ${next.title}", color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MangoButton(text = "Play now", icon = Icons.Filled.SkipNext, onClick = onGo, style = MangoButtonStyle.LIGHT, compact = true, focusRequester = goFocus)
                MangoButton(text = "Cancel", icon = Icons.Filled.ArrowBack, onClick = onCancel, compact = true)
            }
        }
    }
}
