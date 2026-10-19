package com.mangotv.app.ui.home

import com.mangotv.app.data.provider.ProviderRegistry
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mangotv.app.MangoTvApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.navigation.PROFILES_NAV_LABEL
import com.mangotv.app.ui.components.ArcLogo
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.profiles.ProfileAvatarTile
import com.mangotv.app.ui.theme.ArcBlue
import com.mangotv.app.ui.theme.ArcCyan
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

// The frosted pill the links sit in, the profile chip beside it, and the wash behind the page you are on (same colours as the web app's top bar).
private val NavPillColor = Color(0x94141417)
private val NavPillBorder = Color(0x17FFFFFF)
private val NavSelectedWash = Color(0x24FFFFFF)
private val NavFocusWash = Color(0x17FFFFFF)

val MangoNavItems = listOf("Home", "Movies", "TV Shows", "Search", "My List", "Settings")

/** The tab for anime, after TV Shows: it only exists while an installed addon has anime catalogues (see [navItemsWithAnime]). */
const val ANIME_NAV_LABEL = "Anime"

/** [items] with the Anime tab after TV Shows when [hasAnime]; otherwise unchanged. */
fun navItemsWithAnime(items: List<String>, hasAnime: Boolean): List<String> {
    if (!hasAnime || ANIME_NAV_LABEL in items) return items
    val at = items.indexOf("TV Shows")
    return if (at < 0) items else items.take(at + 1) + ANIME_NAV_LABEL + items.drop(at + 1)
}

/** The nav items for someone without an account: the same tabs in the same places, with Settings replaced by Sign In. */
fun navItemsForGuest(items: List<String>): List<String> = items.map { if (it == "Settings") "Sign In" else it }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopNavBar(
    transparentBackground: Boolean,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    // The label of the open tab. Preferred over [selectedIndex] when given: the Anime tab comes and goes, which shifts every index after TV Shows.
    selectedLabel: String? = null,
    selectedItemFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    onItemClick: (String) -> Unit = {},
    // Imperative override for the DOWN seam into content. Prefer this over
    // relying only on contentFocusRequester/focusDown when that requester
    // targets something inside a lazily-composed list: focusProperties
    // pointing at a FocusRequester with no currently-attached node throws,
    // and a scrolled-far-enough list item can be disposed. This callback
    // lets the caller scroll first, then focus, guaranteeing the target
    // exists before it's used. contentFocusRequester still applies as a
    // harmless fallback when this isn't provided (e.g. non-scrolling
    // screens, where the declarative path is already safe).
    onNavigateDown: (() -> Unit)? = null,
    // False while the page is scrolled down: the bar slides away, and slides back down when visible is true again or when focus is on it.
    visible: Boolean = true
) {
    // Someone browsing without an account sees "Sign In" where Settings would be (Settings needs an account).
    val context = LocalContext.current
    val guestGate = remember { (context.applicationContext as MangoTvApplication).container.guestGate }
    val isGuest by guestGate.isGuest.collectAsStateWithLifecycle()
    // The active profile's picture and name sit at the top right for everyone signed in (it opens "Who's watching?", like the web app), and a kids profile has no Settings.
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val profiles by container.profileRepository.state.collectAsStateWithLifecycle()
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    // Everyone signed in sees their profile: with Plus the one in use, without it the account's own (default) profile, which is the only one
    // they can use (a lapsed Plus can leave another profile as the stored choice). Pressing it opens the Profiles screen either way.
    val activeProfile = if (!isGuest && profiles.supported) {
        if (plus.active) profiles.active else profiles.profiles.firstOrNull { it.isDefault }
    } else {
        null
    }
    val baseItems = if (isGuest) navItemsForGuest(MangoNavItems) else MangoNavItems.filterNot { activeProfile?.isKids == true && it == "Settings" }
    val providers by ProviderRegistry.providers.collectAsStateWithLifecycle()
    val navItems = navItemsWithAnime(baseItems, ProviderRegistry.hasAnime(providers))

    // Over the Home / Detail picture there is no dark band behind the bar any more (only the pill and the profile chip have their own
    // frosted background); on every other screen it is the page colour, which only matters when content scrolls up under it.
    val scrimAlpha by animateFloatAsState(
        targetValue = if (transparentBackground) 0f else 0.96f,
        animationSpec = tween(300),
        label = "navBarScrimAlpha"
    )

    // Focus on the bar always brings it back (UP from the first row, the remote's BACK to the bar ...)
    var barHasFocus by remember { mutableStateOf(false) }
    val shown = visible || barHasFocus
    // Hiding slides it up and away, showing slides it back down, both smoothly; the slide starts the moment the page scrolls up
    val shownFraction by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "navBarShown"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { barHasFocus = it.hasFocus }
            .graphicsLayer {
                translationY = -size.height * (1f - shownFraction)
                alpha = shownFraction
            }
            .let { base ->
                if (onNavigateDown != null) {
                    base.onPreviewKeyEvent { event ->
                        // Consume both KeyDown and KeyUp for this key so
                        // neither phase falls through to Compose's default
                        // focus-move handling, which appears to run its own
                        // scroll-into-view independent of
                        // bringIntoViewOnFocus and was causing a second,
                        // unwanted scroll after the imperative one above.
                        if (event.key == Key.DirectionDown) {
                            if (event.type == KeyEventType.KeyDown) {
                                onNavigateDown()
                            }
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    base
                }
            }
            .background(
                // Was a straight top-to-bottom fade (scrimAlpha -> fully
                // transparent) spanning this Row's own bounds -- since the
                // logo/nav items sit vertically CENTERED in it, the text
                // was drawn where that gradient had already faded to
                // roughly half of scrimAlpha, well short of the peak value
                // increased above. Holding full strength through 70% of
                // the bar's height puts the text comfortably inside the
                // solid portion, and only the last 30% (below the text)
                // tapers off -- reading as a soft shadow trailing into the
                // content underneath rather than a wash that's already
                // thin by the time it reaches anything worth reading.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to MangoBackground.copy(alpha = scrimAlpha),
                        0.7f to MangoBackground.copy(alpha = scrimAlpha),
                        1f to Color.Transparent
                    )
                )
            )
            // Top padding trimmed from the original 20dp -- the logo and
            // nav items were sitting noticeably lower than the actual top
            // edge of the screen. Bottom stays as-is so the bar's overall
            // height (and everything that reserves MangoDimens.NavBarHeight
            // of clearance below it, e.g. RowsBrowseContent) is unaffected.
            .padding(
                start = MangoDimens.ScreenPaddingHorizontal,
                end = MangoDimens.ScreenPaddingHorizontal,
                top = 8.dp,
                bottom = 20.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo at the left, the link pill in the middle of the screen and the profile chip at the right (the two side boxes share the spare
        // width equally, which is what keeps the pill centred, as on the web app). The logo is not focusable, so it never has an outline.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            ArcLogo()
        }
        // LazyRow rather than a plain Row: with enough nav items the fully laid-out width can exceed a real TV screen's, and a plain Row just
        // clips the last item(s) instead of scrolling to them. It only scrolls once the items no longer fit; otherwise it is drawn at its
        // natural size. The fast bring-into-view spec is the one every other horizontally-scrolling row uses, so a held D-pad doesn't stutter.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.FastBringIntoViewSpec) {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NavPillColor)
                    .border(1.dp, NavPillBorder, RoundedCornerShape(50)),
                // The pill's own padding. Nothing in it scales on focus any more, so the focus ring is drawn inside each item and is never cut off.
                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(navItems) { index, label ->
                    val isSelected = if (selectedLabel != null) label == selectedLabel else index == selectedIndex
                    NavItem(
                        label = label,
                        selected = isSelected,
                        onClick = { onItemClick(label) },
                        focusRequester = if (isSelected) selectedItemFocusRequester else null,
                        focusDown = contentFocusRequester
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (activeProfile != null) {
                ProfileNavButton(avatar = activeProfile.avatar, name = activeProfile.name, onClick = { onItemClick(PROFILES_NAV_LABEL) }, focusDown = contentFocusRequester)
            }
        }
    }
}

/** The active profile's picture at the top right of the bar: pressing it opens "Who's watching?" (same place and behaviour as the web app). */
@Composable
private fun ProfileNavButton(avatar: String, name: String, onClick: () -> Unit, focusDown: FocusRequester?) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        backgroundColor = NavPillColor,
        borderColor = TextPrimary,
        focusedScale = 1f,
        focusedElevation = 0f,
        borderAnimationSpec = snap(),
        focusDown = focusDown
    ) {
        Row(
            modifier = Modifier.padding(start = 3.dp, top = 3.dp, bottom = 3.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Small, like the labels beside it: the bar is not the place for a big picture.
            ProfileAvatarTile(avatar = avatar, size = 28.dp, cornerRadius = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusDown: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        // The page you are on is a filled pill with a small brand-gradient underline; a focused link gets a lighter wash and the white ring.
        backgroundColor = if (selected) NavSelectedWash else if (focused) NavFocusWash else Color.Transparent,
        // White rather than TvFocusSurface's default accent border -- scoped to just the nav bar via this explicit override, not a global
        // FocusBorder change, so every other focusable element in the app keeps its usual focus color. The ring is the remote's focus cue.
        borderColor = TextPrimary,
        // No zoom: the link used to grow 8% on focus and the row clips anything outside it, which cut the ring off. With no zoom the ring
        // always fits inside the pill. No shadow either (it read as a faint dark ring inside the white border).
        focusedScale = 1f,
        focusedElevation = 0f,
        // Adjacent nav items are separate TvFocusSurfaces, each fading its own border independently -- snapping it instant gives a clean,
        // immediate handoff instead of the outgoing fade-out and incoming fade-in overlapping.
        borderAnimationSpec = snap(),
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false,
        focusRequester = focusRequester,
        focusDown = focusDown
    ) {
        val underline = Brush.horizontalGradient(listOf(ArcCyan, ArcBlue, ArcViolet))
        Text(
            text = label,
            color = if (focused || selected) TextPrimary else TextSecondary,
            // Keyed on selected only, not focused: Bold glyphs measure wider than Medium ones, so keying on focus reflowed every item after
            // the focused one on every D-pad step, reading as the whole bar twitching. Color and the ring are enough to show focus.
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .drawBehind {
                    if (selected) {
                        val barHeight = 2.dp.toPx()
                        drawRoundRect(
                            brush = underline,
                            topLeft = Offset(size.width * 0.28f, size.height - barHeight - 4.dp.toPx()),
                            size = Size(size.width * 0.44f, barHeight),
                            cornerRadius = CornerRadius(barHeight / 2f)
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** A page has to be scrolled at least this far before the top bar slides away (so the bar stays while the first row or two is in view). */
private const val NAV_HIDE_AFTER_PX = 120

/**
 * Whether a page's top bar should be showing: it slides away as the page scrolls down and comes back the moment the page scrolls up, or is at its top.
 * [listState] is the page's own list. A scroll up of any size brings it back (it starts sliding back at once, see [TopNavBar]'s `visible`).
 */
@Composable
fun rememberNavBarVisible(listState: LazyListState): Boolean {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            val down = index > lastIndex || (index == lastIndex && offset > lastOffset + 2)
            val up = index < lastIndex || (index == lastIndex && offset < lastOffset - 2)
            if (index == 0 && offset <= NAV_HIDE_AFTER_PX) visible = true
            else if (up) visible = true
            else if (down) visible = false
            if (down || up) {
                lastIndex = index
                lastOffset = offset
            }
        }
    }
    return visible
}
