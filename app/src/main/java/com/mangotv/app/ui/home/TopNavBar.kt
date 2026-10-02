package com.mangotv.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
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
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

val MangoNavItems = listOf("Home", "Movies", "TV Shows", "Genres", "Search", "My List", "Settings")

/** The nav items for someone without an account: the same tabs in the same places, with Settings replaced by Sign In. */
fun navItemsForGuest(items: List<String>): List<String> = items.map { if (it == "Settings") "Sign In" else it }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopNavBar(
    transparentBackground: Boolean,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
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
    onNavigateDown: (() -> Unit)? = null
) {
    // Someone browsing without an account sees "Sign In" where Settings would be (Settings needs an account).
    val context = LocalContext.current
    val guestGate = remember { (context.applicationContext as MangoTvApplication).container.guestGate }
    val isGuest by guestGate.isGuest.collectAsStateWithLifecycle()
    // ArcTV Plus profiles: the active profile's picture sits at the top right (it opens "Who's watching?", like the web app), and a kids profile has no Settings.
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val profiles by container.profileRepository.state.collectAsStateWithLifecycle()
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    val activeProfile = if (!isGuest && plus.active && profiles.supported) profiles.active else null
    val baseItems = if (isGuest) navItemsForGuest(MangoNavItems) else MangoNavItems.filterNot { activeProfile?.isKids == true && it == "Settings" }
    val navItems = baseItems

    val scrimAlpha by animateFloatAsState(
        // Was 0.45f, then 0.6f -- against a bright/busy hero image behind
        // it (the common case: transparentBackground is true right when
        // Home loads, before any scrolling), that still left the nav bar
        // hard to read. A bit darker keeps the see-through hero effect but
        // gives the labels enough contrast.
        targetValue = if (transparentBackground) 0.72f else 0.96f,
        animationSpec = tween(300),
        label = "navBarScrimAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
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
        ArcLogo()
        Spacer(Modifier.width(56.dp))
        // LazyRow rather than a plain Row: with enough nav items (this list
        // has grown since this bar was first built), the fully laid-out
        // width can exceed a real TV screen's — a plain Row still draws
        // every child at its natural size regardless, which just clips the
        // last item(s) off the edge instead of scrolling to reach them.
        // Wrapping only the item list (not the logo) means it's still drawn
        // exactly as before, at its natural (unscrolled) size, whenever it
        // already fits — this only engages once it doesn't.
        // Fast bring-into-view spec (same one every other horizontally-
        // scrolling row in the app already uses, see ContentRow.kt) so a
        // held D-pad moving across nav items doesn't outrun Compose's
        // slower default spring-based scroll and stutter.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.FastBringIntoViewSpec) {
            LazyRow(
                // Fills the space between the logo and the profile picture, so the picture sits at the far right (the items stay at the left).
                modifier = Modifier.weight(1f),
                // LazyRow clips its content to its own laid-out bounds --
                // with no content padding, that boundary sat exactly at
                // the first/last item's un-scaled edge, so the focused
                // scale-up (TvFocusSurface animates to 1.08x on focus)
                // pushed Home's/Settings' border past it and got clipped.
                // A little breathing room on each end gives the scale
                // somewhere to grow into.
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Tightened from 8dp -- at the old spacing plus the old
                // (larger) label size, this row started scrolling once
                // enough nav items were added to no longer fit one screen
                // width. See NavItem's smaller labelMedium text below;
                // together these reclaim enough width that it shouldn't
                // need to anymore.
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(navItems) { index, label ->
                    NavItem(
                        label = label,
                        selected = index == selectedIndex,
                        onClick = { onItemClick(label) },
                        focusRequester = if (index == selectedIndex) selectedItemFocusRequester else null,
                        focusDown = contentFocusRequester
                    )
                }
            }
        }
        if (activeProfile != null) {
            Spacer(Modifier.width(12.dp))
            ProfileNavButton(avatar = activeProfile.avatar, name = activeProfile.name, onClick = { onItemClick(PROFILES_NAV_LABEL) }, focusDown = contentFocusRequester)
        }
    }
}

/** The active profile's picture at the top right of the bar: pressing it opens "Who's watching?" (same place and behaviour as the web app). */
@Composable
private fun ProfileNavButton(avatar: String, name: String, onClick: () -> Unit, focusDown: FocusRequester?) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = Color.Transparent,
        borderColor = TextPrimary,
        focusedElevation = 0f,
        borderAnimationSpec = snap(),
        focusDown = focusDown
    ) {
        Row(
            modifier = Modifier.padding(start = 3.dp, top = 3.dp, bottom = 3.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Small, like the labels beside it: the bar is not the place for a big picture.
            ProfileAvatarTile(avatar = avatar, size = 28.dp, cornerRadius = 6.dp)
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
        shape = RoundedCornerShape(6.dp),
        backgroundColor = Color.Transparent,
        // White rather than TvFocusSurface's default accent border -- scoped
        // to just the nav bar via this explicit override, not a global
        // FocusBorder change, so every other focusable element in the app
        // (cards, buttons) keeps its usual focus color.
        borderColor = TextPrimary,
        // TvFocusSurface's default focus shadow is a blurred black
        // ambient/spot shadow -- invisible against the accent border/dark
        // cards it was designed for, but at nav-item size it sits right at
        // the white border's inner edge and reads as a faint dark ring
        // inside the border. Nav items don't need the "lift" effect anyway
        // (there's no card underneath to lift off of), so this just turns
        // it off here.
        focusedElevation = 0f,
        // Adjacent nav items are separate TvFocusSurfaces, each fading its
        // own border independently -- with the shared 150ms fade, the
        // outgoing item's fade-out and the incoming item's fade-in overlap
        // and read as the border lagging behind on the previous item.
        // Snapping it instant gives a clean, immediate handoff instead.
        borderAnimationSpec = snap(),
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false,
        focusRequester = focusRequester,
        focusDown = focusDown
    ) {
        Text(
            text = label,
            color = if (focused || selected) TextPrimary else TextSecondary,
            // Keyed on selected only, not focused -- selected stays fixed
            // while moving focus around the bar, but focused changes on
            // every D-pad step, and Bold glyphs measure wider than Medium
            // ones. NavItem isn't a fixed-width box, so that width change
            // reflowed every item after the focused one (and the whole
            // LazyRow, which sizes to fit its content) on every step,
            // reading as the entire bar twitching. Color alone (plus the
            // border) is enough to show focus without moving anything.
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            // labelMedium (13sp) rather than the original titleMedium
            // (16sp) -- see the tightened item spacing above, both
            // together are needed to fit all the nav items without the
            // row falling back to scrolling.
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
