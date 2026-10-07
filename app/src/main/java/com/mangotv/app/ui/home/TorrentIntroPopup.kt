package com.mangotv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.torrent.torrentIntroDue
import com.mangotv.app.ui.components.ArcLogo
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ArcBlue
import com.mangotv.app.ui.theme.ArcCyan
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay

private const val SHOW_AFTER_MS = 1_500L

private data class IntroPoint(val icon: ImageVector, val title: String, val detail: String)

private val Points = listOf(
    IntroPoint(Icons.Filled.Link, "Works with your addons", "Torrent sources from your addons now play, with nothing else to install"),
    IntroPoint(Icons.Filled.Bolt, "Plays while it downloads", "Only what you're watching is fetched"),
    IntroPoint(Icons.Filled.Tune, "Add your own too", "Tap + Torrent on Select a Source for a magnet link or .torrent file. Tune it in Settings \u2192 Player")
)

/**
 * A one-off celebration on Home for people who already had an account when this version arrived: shown until they click it away once, then
 * never again for that user (kept per account on this device, surviving sign-out and in). Guests and accounts that sign in later never see it.
 * [onOpenChanged] tells the caller while it is up so other pop-ups can wait. It is a real dialog window: the remote stays inside it and BACK
 * closes it (which counts as seen).
 */
@Composable
fun TorrentIntroHost(onOpenChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val store = container.torrentIntroStore
    val session by container.authRepository.session.collectAsStateWithLifecycle()
    val sessionLoaded by container.authRepository.sessionLoaded.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val userId = session?.user?.id
    // Not decided until the stored session has been read, so a signed-in user is never taken for a guest.
    if (sessionLoaded) store.ensureBaseline(userId)
    val due = sessionLoaded && torrentIntroDue(store.isEligible(userId), store.hasSeen(userId), store.shownThisSession)

    // Leaving Home before the delay is up cancels this, so it is only used up once it has actually been on screen.
    LaunchedEffect(due, userId) {
        if (due) {
            delay(SHOW_AFTER_MS)
            store.markShown()
            open = true
            onOpenChanged(true)
        }
    }
    if (open) {
        TorrentIntroDialog(onClose = {
            userId?.let { store.markSeen(it) }
            open = false
            onOpenChanged(false)
        })
    }
}

@Composable
private fun TorrentIntroDialog(onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(18.dp)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .clip(shape)
                    .background(MangoBackgroundElevated)
                    .drawBehind {
                        drawRect(Brush.radialGradient(listOf(ArcBlue.copy(alpha = 0.30f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 0.8f))
                        drawRect(Brush.radialGradient(listOf(ArcViolet.copy(alpha = 0.24f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.6f))
                    }
                    .border(1.dp, DividerSubtle, shape)
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArcLogo(fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(ArcCyan, ArcBlue, ArcViolet)))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = "NEW", color = MangoBackground, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(text = "Addons now support torrents.", color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Torrent sources now play right inside Arc TV.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Points.forEach { PointRow(it) } }
                Spacer(Modifier.height(18.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MangoButton(
                        text = "Got it",
                        icon = Icons.Filled.Check,
                        onClick = onClose,
                        style = MangoButtonStyle.FILLED,
                        focusRequester = focus,
                        compact = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Only add torrents you have the right to watch.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PointRow(point: IntroPoint) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(MangoSurface).border(1.dp, DividerSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = point.icon, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = point.title, color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = point.detail, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}
