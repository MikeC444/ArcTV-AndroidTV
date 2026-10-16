package com.mangotv.app.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.InstalledAddon
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/** SettingsListPadding with a roomier bottom, so the last addon of a long list scrolls fully clear of the screen edge (its outline and scale-up included). */
private val AddonsListPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp)

/** A ColumnScope extension hosted by SettingsScreen's detail pane -- see AccountSettingsContent's kdoc for why this isn't its own screen anymore. */
@Composable
fun ColumnScope.AddonsSettingsContent(
    onAddAddon: () -> Unit,
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    viewModel: AddonsViewModel = viewModel()
) {
    val addons by viewModel.installedAddons.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    // "Click here for help" on the debrid note: a QR code for a phone.
    var showDebridHelp by remember { mutableStateOf(false) }
    if (showDebridHelp) DebridHelpDialog(onClose = { showDebridHelp = false })

    // The "Add Addon" header and the addon list are both items in this one
    // LazyColumn (rather than a static header above a separately-scrolling
    // list) so the whole tab scrolls as a unit -- the header scrolls away
    // with everything else instead of permanently reserving space at the
    // top, leaving more of the screen for addon rows once scrolled.
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = AddonsListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stremio-compatible addons contribute their catalogs directly into Home.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                MangoButton(
                    text = "Add Addon",
                    icon = Icons.Filled.Add,
                    onClick = onAddAddon,
                    style = MangoButtonStyle.FILLED,
                    focusRequester = contentFocusRequester,
                    focusUp = navFocusRequester,
                    focusLeft = sidebarFocusRequester,
                    compact = true,
                    borderColor = TextPrimary
                )
            }
        }

        // Same note as the web app: only debrid-backed addons give playable links.
        item(key = "debrid_notice") { DebridNotice(onHelp = { showDebridHelp = true }) }

        if (addons.isEmpty()) {
            item(key = "empty") { EmptyAddonsHint() }
        } else {
            items(addons, key = { it.manifestUrl }) { addon ->
                AddonRow(
                    addon = addon,
                    focusLeft = sidebarFocusRequester,
                    onToggle = { enabled -> viewModel.setEnabled(addon.manifestUrl, enabled) },
                    onRemove = { viewModel.remove(addon.manifestUrl) }
                )
            }
        }

        // At the bottom, after the last addon: re-reads the account's addons, for ones added on the phone while this app was already open.
        item(key = "refresh") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MangoButton(
                    text = if (refreshing) "Refreshing..." else "Refresh addons",
                    icon = Icons.Filled.Refresh,
                    onClick = { viewModel.refresh() },
                    style = MangoButtonStyle.GLASS,
                    focusLeft = sidebarFocusRequester,
                    compact = true,
                    borderColor = TextPrimary
                )
                refreshMessage?.let {
                    Spacer(Modifier.width(16.dp))
                    Text(text = it, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DebridNotice(onHelp: () -> Unit) {
    val shape = RoundedCornerShape(MangoDimens.CardCornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcAccent.copy(alpha = 0.08f))
            .border(1.dp, ArcAccent.copy(alpha = 0.25f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Torrent sources play inside Arc TV.") }
                    append(" A debrid service (Real-Debrid, TorBox) is still faster and needs no downloading.")
                },
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            // Selectable with the remote; opens a QR code to scan with a phone.
            TvFocusSurface(onClick = onHelp, shape = RoundedCornerShape(8.dp), focusedScale = 1.04f) {
                Text(
                    text = "Click here for help",
                    color = ArcAccent,
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyAddonsHint() {
    Column {
        Icon(imageVector = Icons.Filled.Extension, contentDescription = null, tint = TextTertiary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No addons installed yet",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add a Stremio-compatible addon to bring its catalog into Arc TV.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddonRow(
    addon: InstalledAddon,
    focusLeft: FocusRequester,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val titleColor = if (addon.enabled) TextPrimary else TextTertiary
    val bodyColor = if (addon.enabled) TextSecondary else TextTertiary
    // The focused addon's description scrolls sideways so all of it can be read; the others stay cut with "..." so the list isn't all moving.
    var focused by remember { mutableStateOf(false) }
    // Two stops per addon, side by side: the whole row is the on/off switch (a bordered, scaled-up row you can't miss, like Home rows),
    // and a separate Remove button to its right. Left always goes back to the category list.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TvFocusSurface(
            onClick = { onToggle(!addon.enabled) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            // A full-width row: the default poster-card scale would push it past the screen's safe margin.
            focusedScale = 1.02f,
            backgroundColor = MangoSurface,
            borderColor = TextPrimary,
            focusLeft = focusLeft,
            onFocusChanged = { focused = it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = addon.manifest.name, color = titleColor, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(text = "v${addon.manifest.version}", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                    addon.manifest.description?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = bodyColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            // Clipped (not "...") while it scrolls, so the whole text is readable.
                            overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 800) else Modifier
                        )
                    }
                    if (addon.manifest.types.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = addon.manifest.types.joinToString("  ·  ") { it.replaceFirstChar { c -> c.uppercase() } },
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Display only: the row around it takes the press, so focus shows on the whole row rather than as a faint ring on the switch.
                Text(text = if (addon.enabled) "On" else "Off", color = bodyColor, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = addon.enabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MangoBackground,
                        checkedTrackColor = TextPrimary,
                        checkedBorderColor = TextPrimary,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = MangoBackground,
                        uncheckedBorderColor = TextTertiary
                    )
                )
            }
        }

        TvFocusSurface(
            onClick = onRemove,
            shape = RoundedCornerShape(8.dp),
            backgroundColor = MangoBackground,
            borderColor = TextPrimary
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove ${addon.manifest.name}",
                tint = TextSecondary,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}
