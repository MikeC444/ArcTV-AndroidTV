package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.feedback.FeedbackEntry
import com.mangotv.app.data.feedback.FeedbackTarget
import com.mangotv.app.data.recommend.Feedback
import com.mangotv.app.data.recommend.RecommendConfig
import com.mangotv.app.data.recommend.pointsLabel
import com.mangotv.app.data.recommend.summarizeRecommendations
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/** Cinemeta ids are IMDb ids, whose posters live at Metahub: a fallback for ratings made before the poster was kept. */
private fun metahubPoster(id: String): String? = if (Regex("^tt\\d+$").matches(id)) "https://images.metahub.space/poster/small/$id/img" else null

private enum class RatingTab { LIKED, NOT_FOR_ME }

/**
 * Settings > Recommendations (Arc TV Plus), ported from the web app's tab of the same name and kept to how the remote works: two tabs for
 * the titles you rated (press OK on a poster to remove its rating), what shapes your picks with the points each kind of title adds, how it
 * works, and Reset preferences (with a confirm step). The whole tab is one list that scrolls as a unit. The tab can't be opened without
 * Plus (its row is locked), so the locked message only guards a stale selection.
 */
@Composable
fun ColumnScope.RecommendationsSettingsContent(contentFocusRequester: FocusRequester, sidebarFocusRequester: FocusRequester) {
    val container = (LocalContext.current.applicationContext as MangoTvApplication).container
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    if (!plus.active) {
        Column(Modifier.weight(1f)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = TextTertiary)
            Spacer(Modifier.height(8.dp))
            Text("Recommendations are only for Arc TV Plus.", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val feedback by container.feedbackRepository.entries.collectAsStateWithLifecycle()
    val list by container.myListRepository.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    RecommendationsPanel(
        feedback = feedback,
        list = list,
        onRemove = { id, entry -> scope.launch { container.feedbackRepository.set(FeedbackTarget(id, entry.title, entry.providerId, entry.type), null) } },
        onReset = { all -> scope.launch { all.forEach { (id, entry) -> container.feedbackRepository.set(FeedbackTarget(id, entry.title, entry.providerId, entry.type), null) } } },
        contentFocusRequester = contentFocusRequester,
        sidebarFocusRequester = sidebarFocusRequester,
        modifier = Modifier.weight(1f)
    )
}

/** The tab's content, with everything it needs handed in (also drawn on its own by the screenshot test). */
@Composable
internal fun RecommendationsPanel(
    feedback: Map<String, FeedbackEntry>,
    list: List<com.mangotv.app.data.provider.SavedListItem>,
    onRemove: (String, FeedbackEntry) -> Unit,
    onReset: (List<Map.Entry<String, FeedbackEntry>>) -> Unit,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(RatingTab.LIKED) }
    var confirming by remember { mutableStateOf(false) }

    val summary = remember(feedback, list) { summarizeRecommendations(feedback, list) }
    val liked = remember(feedback) { feedback.entries.filter { it.value.feedback == Feedback.LIKE }.sortedByDescending { it.value.at } }
    val disliked = remember(feedback) { feedback.entries.filter { it.value.feedback == Feedback.DISLIKE }.sortedByDescending { it.value.at } }
    val shown = if (tab == RatingTab.LIKED) liked else disliked
    val total = summary.likes + summary.dislikes

    LazyColumn(
        modifier = modifier,
        contentPadding = SettingsListPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "lead") {
            Text("See what shapes your Picked for you row, and fine-tune what you see.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        item(key = "tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecsPill("Liked (${summary.likes})", tab == RatingTab.LIKED, { tab = RatingTab.LIKED }, contentFocusRequester, sidebarFocusRequester)
                RecsPill("Not for me (${summary.dislikes})", tab == RatingTab.NOT_FOR_ME, { tab = RatingTab.NOT_FOR_ME }, null, null)
            }
        }

        item(key = "ratings_$tab") {
            if (shown.isEmpty()) {
                Text(
                    text = if (tab == RatingTab.LIKED) "Nothing liked yet. Press Like on any movie or show and it shows up here."
                    else "Nothing marked Not for me yet. Those titles, and ones like them, are left out of your picks.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column {
                    Text("Press OK on a title to remove its rating.", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                        itemsIndexed(shown, key = { _, e -> e.key }) { index, (id, entry) ->
                            RatedPoster(
                                id = id,
                                entry = entry,
                                posterUrl = entry.posterUrl ?: list.firstOrNull { it.id == id }?.posterUrl ?: metahubPoster(id),
                                liked = tab == RatingTab.LIKED,
                                focusLeft = if (index == 0) sidebarFocusRequester else null,
                                onRemove = { onRemove(id, entry) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "shapes") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What shapes your picks", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                ShapeLine(
                    sidebarFocusRequester,
                    "Titles you like (${summary.likes}): ${pointsLabel(summary.likePoints)}",
                    "${RecommendConfig.WEIGHT_LIKE.toInt()} points each, shared across the title's genres, directors and cast. Pulls in more like it."
                )
                ShapeLine(
                    sidebarFocusRequester,
                    "Titles marked Not for me (${summary.dislikes}): ${pointsLabel(summary.dislikePoints)}",
                    "${RecommendConfig.WEIGHT_DISLIKE.toInt()} points each, shared the same way. The title itself is never picked, and similar titles are pushed down."
                )
                ShapeLine(
                    sidebarFocusRequester,
                    "Your My List and finished titles (${summary.finished} finished, ${summary.saved} saved): ${pointsLabel(summary.listPoints)}",
                    "${RecommendConfig.WEIGHT_COMPLETED.toInt()} points for each finished, ${RecommendConfig.WEIGHT_WATCHLIST.toInt()} for each saved, when you have not rated it. " +
                        "Movies count as finished when you watch them to the end; mark a show as watched yourself."
                )
                ReadBlock(sidebarFocusRequester) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Points are how much each title counts toward your taste. They are not a score.", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
                    if (summary.overLimit) {
                        Text(
                            "Only your ${RecommendConfig.INTERACTION_DETAIL_FETCH_LIMIT} strongest ratings and saves are used for your picks right now.",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (!summary.ready) {
                        Text(
                            "Not enough yet: your picks start once you have at least ${RecommendConfig.MIN_INTERACTIONS_FOR_PERSONALISATION} titles you like, finished, saved or marked " +
                                "Not for me, and at least one of them liked, finished or saved. Until then Home shows popular titles.",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    }
                }
            }
        }

        item(key = "how") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How it works", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                HowStep(sidebarFocusRequester, "1", "It learns your taste.", "Every movie or show you like, finish or save adds points toward the genres, directors and cast you enjoy. A Like counts most, and Not for me counts against.")
                HowStep(sidebarFocusRequester, "2", "It scores what is on offer.", "Titles from your Home rows are compared with your taste, mostly on genre, then on director and cast. Titles you have already finished, rated or are watching are left out.")
                HowStep(sidebarFocusRequester, "3", "It shows a spread.", "Your strongest matches stay, and the rest of the row follows your mix of tastes, so a smaller taste still gets its share.")
                HowStep(sidebarFocusRequester, "4", "It changes when you refresh.", "Most of the row is different next time, and no single title of yours can explain too many picks.")
                HowStep(sidebarFocusRequester, "5", "It explains itself.", "The line under most picks names the title behind it. A few say \"More from directors you enjoy\" instead, and with no real match no reason is shown.")
            }
        }

        item(key = "reset") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reset preferences", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                ReadBlock(sidebarFocusRequester) {
                Text(
                    "Clears every Like and Not for me on this profile ($total now). Your picks still use your My List and finished titles, and a title you removed from the row stays out for ${RecommendConfig.PICKED_DISMISS_DAYS} days.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                }
                if (confirming) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RecsPill("Yes, reset", false, {
                            onReset(liked + disliked)
                            confirming = false
                        }, null, sidebarFocusRequester)
                        RecsPill("Cancel", false, { confirming = false }, null, null)
                    }
                } else if (total > 0) {
                    RecsPill("Reset preferences", false, { confirming = true }, null, sidebarFocusRequester)
                }
            }
        }
    }
}

@Composable
private fun RecsPill(label: String, selected: Boolean, onClick: () -> Unit, focusRequester: FocusRequester?, focusLeft: FocusRequester?) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        focusedScale = 1.04f,
        backgroundColor = if (selected) MangoSurfaceHigh else MangoSurface,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusLeft = focusLeft
    ) {
        Text(
            text = label,
            color = if (selected) ArcAccent else TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun RatedPoster(id: String, entry: FeedbackEntry, posterUrl: String?, liked: Boolean, focusLeft: FocusRequester?, onRemove: () -> Unit) {
    Column(modifier = Modifier.width(112.dp)) {
        TvFocusSurface(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = RoundedCornerShape(10.dp),
            focusedScale = 1.06f,
            backgroundColor = MangoSurface,
            borderColor = TextPrimary,
            focusLeft = focusLeft
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = rememberOpaqueImageRequest(posterUrl),
                        contentDescription = "${entry.title}, ${if (liked) "liked" else "not for me"}. Press OK to remove.",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Text(entry.title.take(1).uppercase(), color = TextTertiary, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.Center))
                }
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50)).background(ArcAccent).padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown,
                        contentDescription = null,
                        tint = MangoSurface,
                        modifier = Modifier.width(14.dp).height(14.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.title, color = TextPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShapeLine(sidebarFocusRequester: FocusRequester, title: String, detail: String) {
    ReadBlock(sidebarFocusRequester) {
    Column {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
    }
}

@Composable
private fun HowStep(sidebarFocusRequester: FocusRequester, number: String, title: String, detail: String) {
    ReadBlock(sidebarFocusRequester) {
    Row(verticalAlignment = Alignment.Top) {
        Text("$number.", color = ArcAccent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Column {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    }
}

/** Plain text can't take focus, so each block of reading is focusable: the remote steps down it and the list scrolls to keep it in view. */
@Composable
private fun ReadBlock(sidebarFocusRequester: FocusRequester, content: @Composable () -> Unit) {
    TvFocusSurface(
        onClick = {},
        clickSound = ClickSound.NONE,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        focusedScale = 1.01f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusLeft = sidebarFocusRequester
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) { content() }
    }
}
