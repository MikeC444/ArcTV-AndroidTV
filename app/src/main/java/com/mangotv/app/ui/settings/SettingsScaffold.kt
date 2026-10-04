package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.TextPrimary

/**
 * Shared shell for every Settings-family screen: the same persistent top nav
 * (with "Settings" highlighted) plus a title, so the D-pad focus-anchoring
 * fix used on Home (nav <-> first content row is a deterministic seam, not
 * left to the default spatial-search heuristic) is applied consistently
 * everywhere instead of being a one-off Home fix.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onNavigate: (String) -> Unit,
    navFocusRequester: FocusRequester,
    firstContentFocusRequester: FocusRequester,
    titleIcon: ImageVector? = null,
    // Which MangoNavItems label to highlight in the nav bar -- defaults to
    // "Settings" so every existing Settings-family screen is unaffected.
    selectedNavLabel: String = "Settings",
    // See TopNavBar's own kdoc on its identically-named parameter. Required
    // (not just harmless-to-omit) for any caller whose content is a
    // scrollable list with firstContentFocusRequester pinned to its first
    // item, e.g. HomeRowsScreen: the declarative
    // firstContentFocusRequester alone crashes the moment that first item
    // has scrolled out of composition, since nothing here scrolls the
    // caller's own list back into view first. Screens whose
    // firstContentFocusRequester targets a static, always-composed element
    // instead (a button, a short fixed-size list) are unaffected either way.
    onNavigateDown: (() -> Unit)? = null,
    // The margins around the body and the gap under the title. The defaults are what every Settings-family screen has always had; the
    // main Settings screen passes smaller ones so its panels use more of the screen.
    horizontalPadding: Dp = MangoDimens.ScreenPaddingHorizontal,
    verticalPadding: Dp = 28.dp,
    titleGap: Dp = 28.dp,
    // False leaves the big title out (the nav bar already shows Settings as the open tab): on a 540 dp tall screen its height is better
    // spent on the panels.
    showTitle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val selectedIndex = remember(selectedNavLabel) { MangoNavItems.indexOf(selectedNavLabel) }

    // Every other TopNavBar-hosting screen (Movies, TV Shows, Search, ...)
    // explicitly requests focus onto its selected nav item on first
    // composition -- without it, Compose's default initial-focus behavior
    // lands on the first focusable element in the tree (the nav bar's
    // first item, "Home") regardless of which item is actually selected.
    // This was missing here, so every SettingsScaffold-hosted screen
    // (Settings, Addons, Home Rows) opened with the D-pad cursor
    // on Home instead of its own tab.
    LaunchedEffect(Unit) {
        runCatching { navFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MangoBackground)
    ) {
        TopNavBar(
            transparentBackground = false,
            selectedIndex = selectedIndex,
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = firstContentFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = onNavigateDown
        )
        Column(
            // weight(1f): the body takes ALL the height under the bar (it used to be only as tall as its content), so a Row with weight(1f)
            // inside it really fills the screen.
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            if (showTitle) {
                if (titleIcon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = titleIcon, contentDescription = null, tint = TextPrimary)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = title,
                            color = TextPrimary,
                            style = MaterialTheme.typography.displayMedium
                        )
                    }
                } else {
                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.displayMedium
                    )
                }
                Spacer(Modifier.height(titleGap))
            }
            content()
        }
    }
}
