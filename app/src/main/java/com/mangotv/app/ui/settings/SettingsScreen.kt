package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcBrandGradient
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.TextTertiary
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * The Settings destinations, now presented as a two-pane
 * master/detail layout (sidebar left, selected category's content filling
 * the remaining 75% on the right) instead of each being its own full-screen
 * navigation destination. icon/title/subtitle are exactly what each
 * category's old standalone screen already used for its own row/title (see
 * git history) -- nothing new added, just consolidated onto one enum so the
 * sidebar row and the detail pane's header stay in sync automatically.
 */
internal enum class SettingsCategory(val icon: ImageVector, val title: String, val subtitle: String) {
    ACCOUNT(Icons.Filled.AccountCircle, "Account", "Manage your Arc TV account"),
    PLUS(Icons.Filled.WorkspacePremium, "Arc TV Plus", "Extra features for supporters"),
    PLUS_SETTINGS(Icons.Filled.Tune, "Plus settings", "Switches for your Plus features"),
    STATS(Icons.Filled.BarChart, "Your stats", "How much you watch, at a glance"),
    ADDONS(Icons.Filled.Extension, "Addons", "Manage installed content providers"),
    HOME_ROWS(Icons.Filled.GridView, "Home Rows", "Choose which rows show up on Home"),
    BLOCKED_GENRES(Icons.Filled.Block, "Blocked Genres", "Hide genres you don't want to see"),
    PLAYER(Icons.Filled.PlayCircle, "Player", "Choose your default video player"),
    SUBTITLES(Icons.Filled.Subtitles, "Subtitles", "Default on/off and preferred language"),
    AUDIO(Icons.Filled.SurroundSound, "Audio", "Surround sound and passthrough"),
    SOUNDS(Icons.Filled.MusicNote, "Sounds", "Choose your app boot sound")
}

/** The side navigation's groups, in the same order and with the same headings as the web app's Settings. */
private val SettingsGroups: List<Pair<String, List<SettingsCategory>>> = listOf(
    "You" to listOf(SettingsCategory.ACCOUNT, SettingsCategory.PLUS, SettingsCategory.PLUS_SETTINGS, SettingsCategory.STATS),
    "Content" to listOf(SettingsCategory.ADDONS, SettingsCategory.HOME_ROWS, SettingsCategory.BLOCKED_GENRES),
    "Playback & sound" to listOf(SettingsCategory.PLAYER, SettingsCategory.SUBTITLES, SettingsCategory.AUDIO, SettingsCategory.SOUNDS)
)

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onSignedOut: () -> Unit,
    onAddAddon: () -> Unit,
    updateViewModel: com.mangotv.app.ui.update.UpdateViewModel
) {
    val navFocusRequester = remember { FocusRequester() }
    val accountRowFocusRequester = remember { FocusRequester() }
    val addonsRowFocusRequester = remember { FocusRequester() }
    val homeRowsRowFocusRequester = remember { FocusRequester() }
    val blockedGenresRowFocusRequester = remember { FocusRequester() }
    val soundsRowFocusRequester = remember { FocusRequester() }
    val subtitlesRowFocusRequester = remember { FocusRequester() }
    val audioRowFocusRequester = remember { FocusRequester() }
    val playerRowFocusRequester = remember { FocusRequester() }
    val plusRowFocusRequester = remember { FocusRequester() }
    val statsRowFocusRequester = remember { FocusRequester() }
    val plusSettingsRowFocusRequester = remember { FocusRequester() }
    // Your stats is a Plus feature: without Plus its row stays in the list, locked and not clickable.
    val plusStatus by (LocalContext.current.applicationContext as com.mangotv.app.MangoTvApplication).container.plusRepository.status.collectAsStateWithLifecycle()

    // Shared by every sidebar row's focusRight: only the selected category's
    // content is ever actually composed on the right (see the `when` in
    // SettingsDetailPane below), so every row can point RIGHT at this same
    // instance without needing to know which pane is currently showing --
    // whichever one is on screen is the one that lands the focus.
    val paneContentFocusRequester = remember { FocusRequester() }

    // Opens on Arc TV Plus when the Plus popup sent the person here ("Take me there"), otherwise on Account.
    var selected by remember { mutableStateOf(if (PendingSettingsTab.takePlus()) SettingsCategory.PLUS else SettingsCategory.ACCOUNT) }

    fun rowFocusRequesterFor(category: SettingsCategory): FocusRequester = when (category) {
        SettingsCategory.ACCOUNT -> accountRowFocusRequester
        SettingsCategory.ADDONS -> addonsRowFocusRequester
        SettingsCategory.HOME_ROWS -> homeRowsRowFocusRequester
        SettingsCategory.BLOCKED_GENRES -> blockedGenresRowFocusRequester
        SettingsCategory.SOUNDS -> soundsRowFocusRequester
        SettingsCategory.SUBTITLES -> subtitlesRowFocusRequester
        SettingsCategory.AUDIO -> audioRowFocusRequester
        SettingsCategory.PLAYER -> playerRowFocusRequester
        SettingsCategory.PLUS -> plusRowFocusRequester
        SettingsCategory.STATS -> statsRowFocusRequester
        SettingsCategory.PLUS_SETTINGS -> plusSettingsRowFocusRequester
    }

    SettingsLayout(
        selected = selected,
        onSelect = { selected = it },
        plusActive = plusStatus.active,
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        paneContentFocusRequester = paneContentFocusRequester,
        rowFocusRequesterFor = ::rowFocusRequesterFor,
        footer = { UpdateCheckRow(viewModel = updateViewModel, focusRight = paneContentFocusRequester) },
        pane = { category, sidebarFocus ->
            SettingsDetailPane(
                category = category,
                navFocusRequester = navFocusRequester,
                contentFocusRequester = paneContentFocusRequester,
                sidebarFocusRequester = sidebarFocus,
                onSignedOut = onSignedOut,
                onAddAddon = onAddAddon,
                onOpenProfiles = { onNavigate(com.mangotv.app.navigation.MangoRoutes.PROFILES) }
            )
        }
    )
}

/**
 * The Settings page itself -- the nav bar, the grouped side panel and the open category's card -- with everything that needs the app's state
 * handed in, so it can also be drawn on its own (the screenshot test does).
 */
@Composable
internal fun SettingsLayout(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    plusActive: Boolean,
    onNavigate: (String) -> Unit,
    navFocusRequester: FocusRequester,
    paneContentFocusRequester: FocusRequester,
    rowFocusRequesterFor: (SettingsCategory) -> FocusRequester,
    footer: @Composable () -> Unit,
    pane: @Composable (SettingsCategory, FocusRequester) -> Unit
) {
    SettingsScaffold(
        title = "Settings",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = rowFocusRequesterFor(SettingsCategory.ACCOUNT),
        // The screen is only 540 dp tall: no big title (the nav bar already shows Settings as the open tab) and slim margins, so the side
        // panel shows every category and the card has the height left for its settings.
        showTitle = false,
        horizontalPadding = 32.dp,
        verticalPadding = 8.dp,
        titleGap = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // The side navigation: a rounded panel of grouped categories (You / Content / Playback & sound), wide enough that no name
            // is squeezed. It scrolls with the remote if the groups don't fit. The padding inside the scroll leaves room for a
            // focused row's scale-up, which the scroll area would otherwise clip at its edges.
            Column(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .padding(end = 16.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MangoBackgroundElevated)
                    .border(1.dp, DividerSubtle, RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
              // The categories scroll; the version and update check below them stay pinned, so their result line is never cut off.
              Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SettingsGroups.forEachIndexed { groupIndex, (label, categories) ->
                    Text(
                        text = label.uppercase(),
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 4.dp, top = if (groupIndex == 0) 0.dp else 14.dp)
                    )
                    categories.forEachIndexed { index, category ->
                        SettingsSidebarRow(
                            category = category,
                            selected = category == selected,
                            locked = (category == SettingsCategory.STATS || category == SettingsCategory.PLUS_SETTINGS) && !plusActive,
                            onClick = { onSelect(category) },
                            focusRequester = rowFocusRequesterFor(category),
                            focusUp = if (groupIndex == 0 && index == 0) navFocusRequester else null,
                            focusRight = paneContentFocusRequester
                        )
                        if (index != categories.lastIndex) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
              }
                // The app's version and the update check, pinned at the foot of the panel.
                Spacer(Modifier.height(10.dp))
                footer()
            }

            // The open category's settings, in a card that takes all the width left.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MangoBackgroundElevated)
                    .border(1.dp, DividerSubtle, RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                pane(selected, rowFocusRequesterFor(selected))
            }
        }
    }
}

@Composable
private fun SettingsSidebarRow(
    category: SettingsCategory,
    selected: Boolean,
    locked: Boolean = false,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusRight: FocusRequester? = null
) {
    TvFocusSurface(
        // A locked (Plus-only) row can be focused, so the list moves normally, but pressing it does nothing.
        onClick = if (locked) ({}) else onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        // Same reasoning as HomeRowToggleRow/SubtitlesToggleRow's own focusedScale override: the default (tuned for small poster cards) is
        // too big a jump for a row that spans its whole container's width.
        focusedScale = 1.02f,
        // The open category is a filled row with a gradient bar down its left edge and a gradient icon tile, and stays that way once focus
        // has moved into the pane on the right (independent of this surface's own transient focus ring).
        backgroundColor = if (selected) MangoSurfaceHigh else Color.Transparent,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusRight = focusRight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (selected) {
                        drawRoundRect(
                            brush = ArcBrandGradient,
                            topLeft = Offset(0f, size.height * 0.22f),
                            size = Size(3.dp.toPx(), size.height * 0.56f),
                            cornerRadius = CornerRadius(1.5.dp.toPx())
                        )
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconTile(if (locked) Icons.Filled.Lock else category.icon, selected, size = 32)
            Spacer(Modifier.width(12.dp))
            Text(
                text = category.title,
                color = if (locked) TextTertiary else if (selected) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (locked) {
                Spacer(Modifier.width(8.dp))
                PlusTag()
            }
        }
    }
}

/** The small rounded tile behind a category's icon: dark with a light icon, or (for the open category and the pane header) the brand gradient. */
@Composable
private fun CategoryIconTile(icon: ImageVector, highlighted: Boolean, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3.2f).dp))
            .let { if (highlighted) it.background(ArcBrandGradient) else it.background(MangoSurface) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) MangoBackground else TextSecondary,
            modifier = Modifier.size((size * 0.58f).dp)
        )
    }
}

/**
 * Convention for any category with more content than fits on screen
 * (Addons, Home Rows, Subtitles today): put everything -- header text,
 * toggle rows, list items, footers -- into ONE LazyColumn as items, rather
 * than a static header/footer around a separately-scrolling inner list.
 * That way the whole tab scrolls as a unit instead of permanently pinning
 * a header/footer that eats into the space available for actual list
 * content. A future category with a scrollable list should follow the
 * same shape (see AddonsSettingsContent/SubtitleSettingsContent for the
 * pattern) rather than reintroducing a fixed header above a nested list.
 */
@Composable
internal fun SettingsDetailPane(
    category: SettingsCategory,
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    onSignedOut: () -> Unit,
    onAddAddon: () -> Unit,
    onOpenProfiles: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconTile(category.icon, highlighted = true, size = 40)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = category.title, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(text = category.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerSubtle))
        Spacer(Modifier.height(12.dp))

        when (category) {
            SettingsCategory.ACCOUNT -> AccountSettingsContent(
                onSignedOut = onSignedOut,
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester,
                onOpenProfiles = onOpenProfiles
            )
            SettingsCategory.ADDONS -> AddonsSettingsContent(
                onAddAddon = onAddAddon,
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.HOME_ROWS -> HomeRowsSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.BLOCKED_GENRES -> BlockedGenresSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.SOUNDS -> SoundSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester
            )
            SettingsCategory.SUBTITLES -> SubtitleSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.PLAYER -> PlayerSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.AUDIO -> AudioSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.PLUS_SETTINGS -> PlusFeatureSettingsContent(
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.STATS -> StatsSettingsContent(
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.PLUS -> PlusSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }
    }
}

/** The small glowing "Plus" tag on a feature only Arc TV Plus has: a brand-gradient rim and glow, same as the web app's. */
@Composable
internal fun PlusTag(text: String = "Plus") {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = text,
        color = com.mangotv.app.ui.theme.ArcCyan,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .drawBehind {
                // A soft glow behind the tag, then its rim.
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(com.mangotv.app.ui.theme.ArcCyan.copy(alpha = 0.28f), com.mangotv.app.ui.theme.ArcViolet.copy(alpha = 0.28f))),
                    cornerRadius = CornerRadius(size.height / 2),
                    topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                    size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx())
                )
            }
            .background(MangoBackgroundElevated, shape)
            .border(1.dp, ArcBrandGradient, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
