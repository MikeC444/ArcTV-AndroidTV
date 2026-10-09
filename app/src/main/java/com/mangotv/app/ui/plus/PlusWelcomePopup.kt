package com.mangotv.app.ui.plus

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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
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
import com.mangotv.app.data.plus.welcomeDue
import com.mangotv.app.ui.components.ArcLogo
import com.mangotv.app.ui.components.ClickSound
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

/** How long after landing on Home the popup waits before it appears. */
private const val SHOW_AFTER_MS = 4_000L

private data class WelcomeItem(val icon: ImageVector, val title: String, val detail: String)

// One short line each: on a TV a long paragraph in a pop-up is too much to read from the sofa (the web popup has the longer wording).
private val Items = listOf(
    WelcomeItem(Icons.Filled.Favorite, "Picked for you", "A Home row chosen from what you like"),
    WelcomeItem(Icons.Filled.Groups, "Up to 5 profiles", "Own My List and picks, kids profiles, PINs"),
    WelcomeItem(Icons.Filled.Bolt, "Smart source picking", "Starts the best source your device can play"),
    WelcomeItem(Icons.Filled.BarChart, "Your stats", "How much you watch, and your streak")
)

/**
 * A one-time tour of what Arc TV Plus includes, for members, a few seconds after landing on Home (the web app's "Everything in ArcTV Plus"
 * popup). Close or "See my Plus settings" both mean it has been seen, and it never comes back (once ever on this device). Not for kids
 * profiles. [blocked] holds it back while another pop-up is up.
 * [onSeeSettings] opens Settings on the Plus settings tab. A real dialog window: the remote stays inside it and BACK means Close.
 */
@Composable
fun PlusWelcomeHost(onSeeSettings: () -> Unit, blocked: Boolean = false) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val repository = container.plusWelcomeRepository
    val isGuest by container.guestGate.isGuest.collectAsStateWithLifecycle()
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    val profiles by container.profileRepository.state.collectAsStateWithLifecycle()
    val welcome by repository.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    val eligible = !isGuest && plus.active && profiles.active?.isKids != true
    val due = eligible && welcome.loaded && !blocked && welcomeDue(welcome.seen, repository.shownThisSession)

    // Leaving Home before the delay is up cancels this, so it is only "used up" once it has actually been on screen.
    LaunchedEffect(due) {
        if (due) {
            delay(SHOW_AFTER_MS)
            repository.markShown()
            open = true
        }
    }

    if (open && eligible) {
        PlusWelcomeDialog(
            onClose = {
                open = false
                repository.markSeen()
            },
            onGo = {
                open = false
                repository.markSeen()
                onSeeSettings()
            }
        )
    }
}

@Composable
private fun PlusWelcomeDialog(onClose: () -> Unit, onGo: () -> Unit) {
    val primaryFocusRequester = remember { FocusRequester() }
    // A dialog window doesn't move D-pad focus by itself; start on the main button.
    LaunchedEffect(Unit) { runCatching { primaryFocusRequester.requestFocus() } }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            PlusWelcomeCard(onGo = onGo, onClose = onClose, primaryFocusRequester = primaryFocusRequester)
        }
    }
}

/** The popup's card, with everything handed in (also drawn on its own by the screenshot test). */
@Composable
internal fun PlusWelcomeCard(onGo: () -> Unit, onClose: () -> Unit, primaryFocusRequester: FocusRequester) {
    val panelShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .clip(panelShape)
            .background(MangoBackgroundElevated)
            // Two soft glows in the corners (blue top-left, violet top-right), like the web popup.
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(ArcBlue.copy(alpha = 0.28f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 0.8f))
                drawRect(Brush.radialGradient(listOf(ArcViolet.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.6f))
            }
            .border(1.dp, DividerSubtle, panelShape)
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcLogo(fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(ArcCyan, ArcBlue, ArcViolet)))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(text = "PLUS", color = MangoBackground, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = "Everything in Arc TV Plus.", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(3.dp))
        Text(text = "Here is what your Plus membership gives you.", color = TextSecondary, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { Items.forEach { WelcomeRow(it) } }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            MangoButton(
                text = "See my Plus settings",
                icon = Icons.Filled.ArrowForward,
                onClick = onGo,
                style = MangoButtonStyle.FILLED,
                focusRequester = primaryFocusRequester,
                compact = true
            )
            MangoButton(text = "Close", icon = Icons.Filled.Close, onClick = onClose, style = MangoButtonStyle.GLASS, clickSound = ClickSound.BACK, compact = true)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Parental controls are on the way",
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WelcomeRow(item: WelcomeItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(MangoSurface).border(1.dp, DividerSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = item.icon, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = item.title, color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(text = item.detail, color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
        }
    }
}
