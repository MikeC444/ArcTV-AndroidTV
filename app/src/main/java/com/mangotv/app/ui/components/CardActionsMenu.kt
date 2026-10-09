package com.mangotv.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.ArcCyan
import com.mangotv.app.ui.theme.ArcAccent
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.auth.GuestGate
import com.mangotv.app.data.feedback.FeedbackRepository
import com.mangotv.app.data.feedback.FeedbackTarget
import com.mangotv.app.data.plus.PlusRepository
import com.mangotv.app.data.recommend.Feedback
import com.mangotv.app.data.recommend.PickedStateRepository
import com.mangotv.app.data.provider.MyListRepository
import com.mangotv.app.data.sync.ContinueWatchingSyncRepository
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.ErrorCoral
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Holds which card (if any) currently has its long-press actions menu open.
 * One instance lives for the whole app (provided by MangoNavHost via
 * [LocalCardActionsMenu]) so any ContentCard, however deeply nested inside
 * Home's rows or a browse grid, can open it with zero prop-threading --
 * the same reasoning LocalUiSoundPlayer already uses for a cross-cutting,
 * app-scoped concern. The menu itself renders once, at the NavHost root
 * (see CardActionsMenuOverlay), on top of whatever screen is showing.
 */
class CardActionsMenuState {
    var target: Content? by mutableStateOf(null)
        private set

    // Whether the overlay may move keyboard focus onto its own rows yet.
    // Starts false on every open() -- see TvFocusSurface's own
    // onLongClickKeyReleased doc for why stealing focus while the
    // triggering D-pad button is still physically held causes an unwanted
    // extra click. armFocus() is the signal that it's now safe, called
    // once the card that opened this menu observes that button's release.
    var canFocusActions: Boolean by mutableStateOf(false)
        private set

    // The FocusRequester of the card that opened this menu (handed back by
    // TvFocusSurface's onLongClickKeyReleased) -- not Compose state, since
    // it's only ever read imperatively from dismiss() below, never during
    // composition. Lets dismiss() return focus to that exact card instead
    // of wherever Compose's focus system falls back to once this overlay's
    // own focused row leaves composition (empirically, the top nav bar's
    // Home button, since that's this app's other default-focus target).
    private var originFocusRequester: FocusRequester? = null

    fun open(content: Content) {
        target = content
        canFocusActions = false
        originFocusRequester = null
    }

    fun armFocus(requester: FocusRequester) {
        canFocusActions = true
        originFocusRequester = requester
    }

    fun dismiss() {
        originFocusRequester?.let { runCatching { it.requestFocus() } }
        target = null
        canFocusActions = false
        originFocusRequester = null
    }
}

val LocalCardActionsMenu = staticCompositionLocalOf { CardActionsMenuState() }

/** The Play button's fill: the accent cyan, lightened so dark text on it reads at a distance (the web app's same tint). */
private val PlayFill: Color get() = lerp(ArcCyan, Color.White, 0.38f)

private fun formatElapsed(positionMs: Long): String {
    val totalMinutes = (positionMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * The long-press quick-actions panel: a centered card showing the title's
 * own artwork alongside a short list of actions (Play/Resume, My List,
 * View Details, Remove from Continue Watching, Choose Source). Rendered
 * once at the NavHost root rather than per-row/per-grid, reading whichever
 * [Content] [state] currently points at -- see [CardActionsMenuState]'s own
 * doc for why. [onNavigate] is the same top-level nav callback MangoNavHost
 * already threads everywhere else.
 */
@Composable
fun CardActionsMenuOverlay(
    state: CardActionsMenuState,
    myListRepository: MyListRepository,
    continueWatchingSyncRepository: ContinueWatchingSyncRepository,
    // Saving a title (My List, Watched) needs an account: for someone browsing without one these ask them to sign in.
    guestGate: GuestGate,
    // Like / Not for me on movies (the "Picked for you" preview).
    feedbackRepository: FeedbackRepository,
    // Whether this account has ArcTV Plus (the backend decides): Like / Not for me are Plus features.
    plusRepository: PlusRepository,
    // "Remove from Picked for you": keeps a title out of that row without being a Like / Not for me.
    pickedStateRepository: PickedStateRepository,
    onNavigate: (String) -> Unit,
    resolvePlayRoute: (Content) -> String,
    modifier: Modifier = Modifier
) {
    val content = state.target
    BackHandler(enabled = content != null) { state.dismiss() }
    if (content == null) return

    val coroutineScope = rememberCoroutineScope()
    val savedIds by myListRepository.items.collectAsStateWithLifecycle()
    val isInMyList = savedIds.any { it.id == content.id }
    // Derived the same way as isInMyList above (from the live
    // myListRepository.items snapshot, not content.watched) so this is
    // always accurate regardless of which screen's Content this menu was
    // opened from -- some callers stamp watched onto Content, some don't.
    val isWatched = savedIds.any { it.id == content.id && it.watched }
    val feedbackEntries by feedbackRepository.entries.collectAsStateWithLifecycle()
    val plusStatus by plusRepository.status.collectAsStateWithLifecycle()
    val feedback = feedbackEntries[content.id]?.feedback
    val firstRowFocusRequester = remember(content.id) { FocusRequester() }

    // Gated on canFocusActions rather than firing as soon as content is set
    // -- see CardActionsMenuState's own doc for why taking focus early
    // (while the long-press button is still held) causes an unwanted click
    // on whichever row ends up focused.
    LaunchedEffect(content.id, state.canFocusActions) {
        if (state.canFocusActions) {
            runCatching { firstRowFocusRequester.requestFocus() }
        }
    }

    fun dismissAndNavigate(route: String) {
        state.dismiss()
        onNavigate(route)
    }

    val watchProgress = content.watchProgress
    val providerId = content.providerId
    val target = FeedbackTarget(content.id, content.title, content.providerId, content.type, content.posterUrl)

    CardActionsMenuPanel(
        content = content,
        isInMyList = isInMyList,
        isWatched = isWatched,
        feedback = feedback,
        plusActive = plusStatus.active,
        firstFocusRequester = firstRowFocusRequester,
        modifier = modifier,
        onPlay = { if (providerId != null) dismissAndNavigate(resolvePlayRoute(content)) },
        onToggleMyList = {
            guestGate.requireAccount { coroutineScope.launch { myListRepository.toggle(content) } }
            state.dismiss()
        },
        // Non-suspend: toggleWatched() already fires fire-and-forget on MyListRepository's own long-lived scope, unlike toggle() above.
        // Unlike the player's own one-way markWatched(), this flips watched in either direction on each tap.
        onToggleWatched = {
            guestGate.requireAccount { myListRepository.toggleWatched(content) }
            state.dismiss()
        },
        onLike = {
            guestGate.requireAccount { coroutineScope.launch { feedbackRepository.toggle(target, Feedback.LIKE) } }
            state.dismiss()
        },
        onDislike = {
            guestGate.requireAccount { coroutineScope.launch { feedbackRepository.toggle(target, Feedback.DISLIKE) } }
            state.dismiss()
        },
        onRemoveFromPicked = {
            coroutineScope.launch { pickedStateRepository.dismiss(content.id) }
            state.dismiss()
        },
        onViewDetails = { if (providerId != null) dismissAndNavigate(MangoRoutes.detail(providerId, content.type, content.id)) },
        // Not "finished": taking a title out of Continue Watching must not mark it watched, and it starts over next time.
        onRemoveFromContinueWatching = if (watchProgress != null && providerId != null) {
            {
                continueWatchingSyncRepository.removeEntry(providerId, content.id, content.type)
                state.dismiss()
            }
        } else {
            null
        },
        onChooseSource = if (providerId != null) {
            {
                dismissAndNavigate(
                    MangoRoutes.sources(providerId, content.type, content.id, watchProgress?.seasonNumber, watchProgress?.episodeNumber, skipAutoSelect = true)
                )
            }
        } else {
            null
        }
    )
}

/**
 * The menu itself, with everything it needs handed in (also drawn on its own by the screenshot test). Laid out like the web app's: a small
 * poster with the title beside it, one big Play (or Resume) button, a two-by-two grid of My List / Watched / Like / Not for me, then a
 * short list of the other actions. Kept compact because the screen is only 540 dp tall.
 */
@Composable
internal fun CardActionsMenuPanel(
    content: Content,
    isInMyList: Boolean,
    isWatched: Boolean,
    feedback: Feedback?,
    plusActive: Boolean,
    firstFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onToggleMyList: () -> Unit,
    onToggleWatched: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onRemoveFromPicked: () -> Unit,
    onViewDetails: () -> Unit,
    onRemoveFromContinueWatching: (() -> Unit)?,
    onChooseSource: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val watchProgress = content.watchProgress
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(MangoBackgroundElevated, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = rememberOpaqueImageRequest(content.posterUrl ?: content.backdropUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(80.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    text = content.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            PlayButton(
                label = if (watchProgress != null) "Resume from ${formatElapsed(watchProgress.positionMs)}" else "Play",
                focusRequester = firstFocusRequester,
                onClick = onPlay
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GridAction(
                    icon = if (isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                    label = if (isInMyList) "Remove from My List" else "Add to My List",
                    on = isInMyList,
                    onClick = onToggleMyList,
                    modifier = Modifier.weight(1f)
                )
                GridAction(
                    icon = if (isWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    label = if (isWatched) "Remove from Watched" else "Mark as watched",
                    on = isWatched,
                    onClick = onToggleWatched,
                    modifier = Modifier.weight(1f)
                )
            }
            if (plusActive) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GridAction(
                        icon = if (feedback == Feedback.LIKE) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        label = if (feedback == Feedback.LIKE) "Remove like" else "Like",
                        on = feedback == Feedback.LIKE,
                        onClick = onLike,
                        modifier = Modifier.weight(1f)
                    )
                    GridAction(
                        icon = if (feedback == Feedback.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        label = if (feedback == Feedback.DISLIKE) "Remove \"Not for me\"" else "Not for me",
                        on = feedback == Feedback.DISLIKE,
                        onClick = onDislike,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerSubtle))
            Spacer(Modifier.height(4.dp))
            if (plusActive && content.pickedForYou) {
                CardActionRow(icon = Icons.Filled.Close, label = "Remove from Picked for you", onClick = onRemoveFromPicked)
            }
            CardActionRow(icon = Icons.Filled.Info, label = "View Details", chevron = true, onClick = onViewDetails)
            if (onRemoveFromContinueWatching != null) {
                CardActionRow(icon = Icons.Filled.Delete, label = "Remove from Continue Watching", destructive = true, onClick = onRemoveFromContinueWatching)
            }
            if (onChooseSource != null) {
                CardActionRow(icon = Icons.Filled.List, label = "Choose Source", chevron = true, onClick = onChooseSource)
            }
        }
    }
}

/** The one big button at the top: Play, or Resume from where the person stopped. */
@Composable
private fun PlayButton(label: String, focusRequester: FocusRequester, onClick: () -> Unit) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = PlayFill,
        focusRequester = focusRequester,
        borderColor = TextPrimary,
        focusedScale = 1.02f,
        bringIntoViewOnFocus = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = MangoBackground, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(text = label, color = MangoBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** One cell of the two-by-two grid. [on] marks a state that is set (in My List, watched, liked, not for me): a tick-style icon in the accent colour and a lighter fill. */
@Composable
private fun GridAction(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        backgroundColor = if (on) MangoSurfaceHigh else MangoSurface,
        borderColor = TextPrimary,
        focusedScale = 1.03f,
        bringIntoViewOnFocus = false,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CardActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    destructive: Boolean = false,
    chevron: Boolean = false
) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color.Transparent,
        focusRequester = focusRequester,
        borderColor = if (destructive) ErrorCoral else FocusBorder,
        bringIntoViewOnFocus = false,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) ErrorCoral else TextPrimary,
                modifier = Modifier.width(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = if (destructive) ErrorCoral else TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (chevron) {
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}
