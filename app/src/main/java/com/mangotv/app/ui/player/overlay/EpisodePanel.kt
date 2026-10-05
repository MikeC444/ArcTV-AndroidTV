package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Season
import com.mangotv.app.ui.components.FilterPill
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * The episode selector: a panel that slides out on the right of the player with a pill per season and the season's episodes under it, each
 * with its thumbnail. The cursor starts on the episode playing now (its season is the one open); picking an episode calls [onPick] and the
 * player starts it without leaving the player. BACK closes it (the player's own back handling), like the other menus.
 */
@Composable
fun EpisodePanel(
    seasons: List<Season>,
    currentSeason: Int?,
    currentEpisode: Int?,
    onPick: (season: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = seasons.filter { it.episodes.isNotEmpty() }
    var selectedSeason by remember {
        mutableIntStateOf(currentSeason?.takeIf { wanted -> shown.any { it.seasonNumber == wanted } } ?: shown.firstOrNull()?.seasonNumber ?: 0)
    }
    val episodes = shown.firstOrNull { it.seasonNumber == selectedSeason }?.episodes.orEmpty()
    val listState = rememberLazyListState()
    val startIndex = episodes.indexOfFirst { selectedSeason == currentSeason && it.episodeNumber == currentEpisode }.coerceAtLeast(0)
    val startRequester = remember { FocusRequester() }

    // Open on the episode playing now: scroll to it, then put the cursor there.
    LaunchedEffect(Unit) {
        if (episodes.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(startIndex)
        withFrameNanos { }
        runCatching { startRequester.requestFocus() }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(500.dp)
                .background(MangoBackground.copy(alpha = 0.97f))
                .padding(vertical = 28.dp, horizontal = 24.dp)
        ) {
            Text(text = "Episodes", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            if (shown.size > 1) {
                // A pill per season; the list below follows the one picked.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                    items(shown, key = { it.seasonNumber }) { season ->
                        FilterPill(
                            label = if (season.seasonNumber == 0) "Specials" else "Season ${season.seasonNumber}",
                            selected = season.seasonNumber == selectedSeason,
                            onClick = { selectedSeason = season.seasonNumber }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
            ) {
                itemsIndexed(episodes, key = { _, episode -> "${episode.seasonNumber}x${episode.episodeNumber}" }) { index, episode ->
                    EpisodeRow(
                        episode = episode,
                        isCurrent = episode.seasonNumber == currentSeason && episode.episodeNumber == currentEpisode,
                        focusRequester = if (index == startIndex) startRequester else null,
                        onClick = { onPick(episode.seasonNumber, episode.episodeNumber) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, isCurrent: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        backgroundColor = if (isCurrent) Color.White.copy(alpha = 0.16f) else MangoSurface,
        borderColor = Color.White,
        focusedScale = 1.02f,
        focusedElevation = 0f,
        focusRequester = focusRequester
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(width = 128.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)).background(MangoSurfaceHigh)) {
                if (episode.thumbnailUrl != null) {
                    AsyncImage(
                        model = rememberOpaqueImageRequest(episode.thumbnailUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.title}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(if (isCurrent) "Playing now" else null, episode.runtimeMinutes?.let { "$it min" }).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(text = detail, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (episode.description.isNotBlank()) {
                    Text(
                        text = episode.description,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
