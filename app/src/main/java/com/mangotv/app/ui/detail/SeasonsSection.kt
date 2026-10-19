package com.mangotv.app.ui.detail

import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import com.mangotv.app.ui.theme.WatchedGreen
import com.mangotv.app.ui.theme.ProgressTrack
import com.mangotv.app.ui.theme.ProgressFill
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.data.history.episodeKey
import com.mangotv.app.data.history.EpisodeStatus
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Season
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.EdgeSafeBringIntoView
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

private val EpisodeThumbWidth = MangoDimens.ContinueWatchingWidth

/** The episode picture when the page is squeezed to fit seasons, episodes and cast on one screen. */
private val CompactThumbWidth = 224.dp

/**
 * Replaces Cast/"You May Also Like" for TV shows: a season selector followed
 * by that season's episodes. Tapping an episode navigates to the source
 * picker for that specific season/episode.
 */
@Composable
fun SeasonsSection(
    seasons: List<Season>,
    modifier: Modifier = Modifier,
    onNavigateUpPastRow: (() -> Unit)? = null,
    onEpisodeClick: (Episode) -> Unit = {},
    // What the account's history says about each episode ("season:episode" -> finished, or how far in); drives the tick and progress bar.
    statuses: Map<String, EpisodeStatus> = emptyMap(),
    // The season Play will start in: the list opens on it until the person picks another.
    startSeason: Int? = null,
    onSetEpisodeWatched: (Episode, Boolean) -> Unit = { _, _ -> },
    onSetSeasonWatched: (Season, Boolean) -> Unit = { _, _ -> },
    // Tighter spacing and smaller pictures so the seasons, the episodes and the cast fit one TV screen together (the page scrolls down to them).
    compact: Boolean = false
) {
    if (seasons.isEmpty()) return

    var picked by remember { mutableStateOf<Int?>(null) }
    val selectedIndex = picked ?: seasons.indexOfFirst { it.seasonNumber == startSeason }.coerceAtLeast(0)
    val seasonsState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    val selectedSeason = seasons.getOrNull(selectedIndex) ?: return
    // The episode whose hold-OK menu is open.
    var menuEpisode by remember { mutableStateOf<Episode?>(null) }

    Column(modifier = modifier) {
        Text(
            text = "Seasons",
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                horizontal = MangoDimens.ScreenPaddingHorizontal,
                vertical = if (compact) 6.dp else 12.dp
            )
        )
        // A long list of seasons scrolls as focus moves along it: this page switches the automatic scroll-into-view off, so the row asks for the
        // edge-safe one (as the episode row below does), and it opens scrolled to the season that is selected.
        EdgeSafeBringIntoView {
            LazyRow(
                state = seasonsState,
                modifier = if (onNavigateUpPastRow != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionUp) {
                            if (event.type == KeyEventType.KeyDown) {
                                onNavigateUpPastRow()
                            }
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
                contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(seasons, key = { it.seasonNumber }) { season ->
                    SeasonPill(
                        seasonNumber = season.seasonNumber,
                        selected = season.seasonNumber == selectedSeason.seasonNumber,
                        onClick = { picked = seasons.indexOf(season) },
                        size = if (compact) 44.dp else 52.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(if (compact) 12.dp else 28.dp))

        Row(
            modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedSeason.name,
                color = TextPrimary,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${selectedSeason.episodes.size} Episodes",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

        EdgeSafeBringIntoView {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
                horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
            ) {
                items(selectedSeason.episodes, key = { it.id }) { episode ->
                    EpisodeCard(
                        episode = episode,
                        status = statuses[episodeKey(episode.seasonNumber, episode.episodeNumber)],
                        compact = compact,
                        onClick = { onEpisodeClick(episode) },
                        onLongClick = { menuEpisode = episode }
                    )
                }
            }
        }
    }

    menuEpisode?.let { episode ->
        val status = statuses[episodeKey(episode.seasonNumber, episode.episodeNumber)]
        val seasonWatched = selectedSeason.episodes.isNotEmpty() &&
            selectedSeason.episodes.all { statuses[episodeKey(it.seasonNumber, it.episodeNumber)]?.watched == true }
        EpisodeActionsDialog(
            episode = episode,
            watched = status?.watched == true,
            seasonWatched = seasonWatched,
            onPlay = { menuEpisode = null; onEpisodeClick(episode) },
            onToggleWatched = { menuEpisode = null; onSetEpisodeWatched(episode, status?.watched != true) },
            onToggleSeason = { menuEpisode = null; onSetSeasonWatched(selectedSeason, !seasonWatched) },
            onDismiss = { menuEpisode = null }
        )
    }
}

/**
 * Styled to echo the hero's Play button and icon buttons rather than the
 * old flat accent-tinted circle: selected is a solid white fill with bold
 * black text (same "confident, high-contrast active state" as
 * MangoButtonStyle.LIGHT), unselected is the same frosted glass tint
 * HeroIconButton rests at. Focus (accent border/scale/elevation) still
 * comes from TvFocusSurface's own default, unchanged from every other
 * focusable element on this screen.
 */
@Composable
private fun SeasonPill(
    seasonNumber: Int,
    selected: Boolean,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusSurface(
        onClick = onClick,
        shape = CircleShape,
        backgroundColor = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false,
        modifier = Modifier.size(size)
    ) {
        Text(
            text = seasonNumber.toString(),
            color = when {
                selected -> Color.Black
                focused -> TextPrimary
                else -> TextSecondary
            },
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun EpisodeCard(episode: Episode, status: EpisodeStatus?, compact: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val thumbWidth = if (compact) CompactThumbWidth else EpisodeThumbWidth
    val thumbHeight = if (compact) CompactThumbWidth * 9 / 16 else MangoDimens.ContinueWatchingHeight
    Column(modifier = Modifier.width(thumbWidth)) {
        TvFocusSurface(
            onClick = onClick,
            // Holding OK opens the episode's menu (Play, mark watched, mark season watched), as it does on a poster.
            onLongClick = onLongClick,
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            backgroundColor = MangoSurfaceHigh,
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight),
            bringIntoViewOnFocus = false
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(
                    model = rememberOpaqueImageRequest(episode.thumbnailUrl),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
            // Finished episodes get a tick in the corner; one part-way gets a bar along the bottom showing how far in.
            if (status?.watched == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(26.dp)
                        .background(WatchedGreen, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Watched",
                        tint = Color.Black,
                        modifier = Modifier.align(Alignment.Center).size(16.dp)
                    )
                }
            } else if (status != null && status.fraction > 0f) {
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(ProgressTrack)) {
                    Box(modifier = Modifier.fillMaxWidth(status.fraction).height(4.dp).background(ProgressFill))
                }
            }
        }

        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))

        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "${episode.episodeNumber}. ${episode.title}",
                color = TextPrimary,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            episode.runtimeMinutes?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${it}m",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (episode.description.isNotBlank()) {
            Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
            Text(
                text = episode.description,
                color = TextSecondary,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The menu that opens when OK is held on an episode: Play, mark that episode watched or not watched, and mark the whole season. It is a dialog
 * window, so the remote stays inside it and BACK closes it. Focus moves into it only once the held key has been let go (the key that opened it
 * would otherwise press the first row as it is released); with a touch long-press, which has no release, a short delay does the same.
 */
@Composable
private fun EpisodeActionsDialog(
    episode: Episode,
    watched: Boolean,
    seasonWatched: Boolean,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleSeason: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    // The OK key that opened this menu is usually still held when the window appears, and its key-repeat (then its release) would press
    // the focused Play row and jump to the source picker. Swallow OK/Enter until that key has been let go: either its release arrives
    // here, or no OK event has been seen for a moment (the release went to the card underneath before this window took focus).
    var armed by remember { mutableStateOf(false) }
    var lastKeyMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        delay(450)
        runCatching { firstFocus.requestFocus() }
        while (!armed) {
            delay(150)
            if (System.currentTimeMillis() - lastKeyMs > 300) armed = true
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .onPreviewKeyEvent { event ->
                    val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (!isOk || armed) {
                        false
                    } else {
                        lastKeyMs = System.currentTimeMillis()
                        if (event.type == KeyEventType.KeyUp) armed = true
                        true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .padding(horizontal = 16.dp)
                    .background(MangoSurfaceHigh, RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "S${episode.seasonNumber} \u00B7 E${episode.episodeNumber}",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = episode.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                MangoButton(text = "Play", icon = Icons.Filled.PlayArrow, onClick = onPlay, style = MangoButtonStyle.FILLED, focusRequester = firstFocus, compact = true)
                MangoButton(
                    text = if (watched) "Mark as not watched" else "Mark as watched",
                    icon = Icons.Filled.Check,
                    onClick = onToggleWatched,
                    style = MangoButtonStyle.GLASS,
                    compact = true
                )
                MangoButton(
                    text = if (seasonWatched) "Mark season as not watched" else "Mark season as watched",
                    icon = Icons.Filled.Check,
                    onClick = onToggleSeason,
                    style = MangoButtonStyle.GLASS,
                    compact = true
                )
            }
        }
    }
}
