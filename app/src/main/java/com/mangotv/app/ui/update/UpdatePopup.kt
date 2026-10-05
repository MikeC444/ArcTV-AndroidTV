package com.mangotv.app.ui.update

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mangotv.app.data.update.AppUpdate
import com.mangotv.app.data.update.NO_NOTES_FALLBACK
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ArcBlue
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.ArcWarn
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * The update prompt: a pop-up over the whole app, in the same style as the old "What's new" overlay, that shows the new
 * version, its release notes, and Update / Install / Retry and Not now buttons. It replaces the banner that used to
 * push the screen down, and folds the release notes into itself so there is one place to look.
 *
 * It is a real dialog window, so the remote stays inside it and BACK means "Not now" -- except while downloading,
 * when BACK is ignored (as the old banner hid its dismiss button then).
 */
@Composable
internal fun UpdatePopup(
    state: UpdateUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val primaryFocusRequester = remember { FocusRequester() }

    // A dialog window doesn't move D-pad focus by itself; start on the main button.
    LaunchedEffect(Unit) {
        runCatching { primaryFocusRequester.requestFocus() }
    }

    // The version is the pill in the header; this line under the title is the download size, when the release says it.
    val sizeLine = update.assetSizeBytes?.let { "Download size " + Formatter.formatShortFileSize(context, it) }

    Dialog(
        onDismissRequest = { if (!state.isDownloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !state.isDownloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            // A card in the same style as the Arc TV Plus pop-up: about 560 dp wide, with the same soft blue / violet glows in the top corners,
            // a thin edge, the notes as a short list with accent dots (about six fit before it scrolls), and buttons centred under them.
            val panelShape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(panelShape)
                    .background(MangoBackgroundElevated)
                    .drawBehind {
                        drawRect(Brush.radialGradient(listOf(ArcBlue.copy(alpha = 0.28f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 0.8f))
                        drawRect(Brush.radialGradient(listOf(ArcViolet.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.6f))
                    }
                    .border(1.dp, DividerSubtle, panelShape)
                    .padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MangoSurface)
                            .border(1.dp, DividerSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = ArcAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update available",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        if (sizeLine != null) {
                            Text(
                                text = sizeLine,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    // The version as a pill, so what is on offer reads at a glance.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(ArcAccent.copy(alpha = 0.16f))
                            .border(1.dp, ArcAccent.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(text = update.tag, style = MaterialTheme.typography.labelLarge, color = ArcAccent, fontWeight = FontWeight.Bold)
                    }
                }

                UpdateStatus(state)

                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerSubtle))
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "What's new in ${update.tag}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                ScrollableNotes(update.notes.ifBlank { NO_NOTES_FALLBACK })

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    MangoButton(
                        text = when {
                            state.isDownloading -> "Downloading…"
                            state.downloadedApkPath != null -> "Install"
                            state.errorMessage != null -> "Retry"
                            else -> "Update"
                        },
                        icon = Icons.Filled.CloudDownload,
                        onClick = {
                            when {
                                state.isDownloading -> Unit
                                state.downloadedApkPath != null -> onInstall()
                                else -> onDownload()
                            }
                        },
                        style = MangoButtonStyle.FILLED,
                        focusRequester = primaryFocusRequester
                    )
                    if (!state.isDownloading) {
                        MangoButton(
                            text = "Not now",
                            icon = Icons.Filled.Close,
                            onClick = onDismiss,
                            style = MangoButtonStyle.GLASS,
                            clickSound = ClickSound.BACK
                        )
                    }
                }
            }
        }
    }
}

/** Download progress, "Ready to install", or what went wrong; nothing at all while the update is just on offer. */
@Composable
private fun UpdateStatus(state: UpdateUiState) {
    val progress = state.downloadProgress
    when {
        state.errorMessage != null -> StatusText(state.errorMessage, ArcWarn)
        state.isDownloading -> {
            StatusText(
                if (progress != null) "Downloading… ${(progress * 100).toInt().coerceIn(0, 100)}%" else "Downloading…",
                TextSecondary
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = ArcAccent,
                    trackColor = DividerSubtle,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            } else {
                LinearProgressIndicator(
                    color = ArcAccent,
                    trackColor = DividerSubtle,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        state.downloadedApkPath != null -> StatusText("Ready to install", TextSecondary)
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/**
 * The release notes in a box the remote can scroll. Plain scrolling text isn't focusable, so a remote could never move
 * it: this box takes focus (UP from the buttons reaches it), DOWN and UP then scroll it, and once it is at its end
 * DOWN (or at its start UP) is left alone so focus moves on to the buttons as usual. A thin bar on the right shows how
 * far through the notes you are, and a hint says the box can be scrolled.
 */
@Composable
private fun ScrollableNotes(notes: String) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val stepPx = with(LocalDensity.current) { SCROLL_STEP.toPx() }
    val shape = RoundedCornerShape(10.dp)

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(shape)
                .border(2.dp, if (focused) FocusBorder else Color.Transparent, shape)
                .onPreviewKeyEvent { event ->
                    val down = event.key == Key.DirectionDown && scrollState.canScrollForward
                    val up = event.key == Key.DirectionUp && scrollState.canScrollBackward
                    if (down || up) {
                        // Consume both key phases so an unconsumed KeyUp can't also move focus; scroll on KeyDown only.
                        if (event.type == KeyEventType.KeyDown) {
                            scope.launch { scrollState.animateScrollBy(if (down) stepPx else -stepPx) }
                        }
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .drawWithContent {
                    drawContent()
                    val max = scrollState.maxValue
                    if (max > 0) {
                        val track = size.height
                        val thumb = (track * track / (track + max)).coerceAtLeast(24.dp.toPx())
                        val top = (track - thumb) * scrollState.value / max
                        drawRoundRect(
                            color = TextTertiary,
                            topLeft = Offset(size.width - 6.dp.toPx(), top),
                            size = Size(4.dp.toPx(), thumb),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                }
        ) {
            // The notes as a list: an accent dot and the line for each bullet (blank lines dropped, the gap comes from the spacing), and a
            // plain bold line for a "Version x" heading when several releases were missed.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                notes.lines().filter { it.isNotBlank() }.forEach { line ->
                    when {
                        line.startsWith("• ") -> Row(verticalAlignment = Alignment.Top) {
                            Box(modifier = Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(ArcAccent))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = line.removePrefix("• "),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp,
                                color = TextPrimary.copy(alpha = 0.9f)
                            )
                        }
                        line.startsWith("Version ") -> Text(text = line, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        else -> Text(text = line, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp, color = TextSecondary)
                    }
                }
            }
        }
        if (scrollState.maxValue > 0) {
            Text(
                text = if (focused) "▲ ▼  scroll the notes" else "▲  select the notes to scroll",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private val SCROLL_STEP = 45.dp
