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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.plus.promoDue
import com.mangotv.app.ui.components.ArcLogo
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.TvFocusSurface
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

private data class PromoBenefit(val icon: ImageVector, val title: String, val detail: String)

private val Benefits = listOf(
    // One short line each: on a TV a long paragraph in a pop-up is too much to read from the sofa (the web popup has the longer wording).
    PromoBenefit(Icons.Filled.Favorite, "Picked for you", "A Home row chosen from movies you like"),
    PromoBenefit(Icons.Filled.Groups, "Up to 5 profiles", "Own My List and picks, kids profiles, PINs"),
    PromoBenefit(Icons.Filled.WorkspacePremium, "Smarter viewing", "Smart source picking and your watch stats")
)

/**
 * A gentle Arc TV Plus invitation on Home (same rules as the web app). It only appears for a signed-in adult without Plus once Plus is a paid tier,
 * a few seconds after landing on Home, once per launch. Close hides it for a week; "Don't show me again" ends it for this account. [onTakeMeThere]
 * opens Settings on the Arc TV Plus tab. It is a real dialog window, so the remote stays inside it and BACK means Close.
 */
@Composable
fun PlusPromoHost(onTakeMeThere: () -> Unit, blocked: Boolean = false) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val repository = container.plusPromoRepository
    val isGuest by container.guestGate.isGuest.collectAsStateWithLifecycle()
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    val profiles by container.profileRepository.state.collectAsStateWithLifecycle()
    val record by repository.record.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    // Eligible: signed in, the paywall is on (not early access), no Plus, and not a kids profile.
    val eligible = !isGuest && plus.paywall && !plus.active && profiles.active?.isKids != true
    val current = record
    val due = eligible && current != null && !blocked && promoDue(current, repository.shownThisSession, System.currentTimeMillis())

    // Leaving Home before the delay is up cancels this, so the popup is only "used up" once it has actually been on screen.
    LaunchedEffect(due) {
        if (due) {
            delay(SHOW_AFTER_MS)
            repository.markShown()
            open = true
        }
    }

    if (open && eligible) {
        PlusPromoDialog(
            onClose = {
                open = false
                repository.snooze()
            },
            onNever = {
                open = false
                repository.dismissForever()
            },
            onGo = {
                open = false
                repository.snooze() // like Close: going to look at the plans counts as answered, so it isn't shown again straight away
                onTakeMeThere()
            }
        )
    }
}

@Composable
private fun PlusPromoDialog(onClose: () -> Unit, onNever: () -> Unit, onGo: () -> Unit) {
    val primaryFocusRequester = remember { FocusRequester() }
    // A dialog window doesn't move D-pad focus by itself; start on the main button.
    LaunchedEffect(Unit) {
        runCatching { primaryFocusRequester.requestFocus() }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            PlusPromoCard(onGo = onGo, onClose = onClose, onNever = onNever, primaryFocusRequester = primaryFocusRequester)
        }
    }
}

/** The popup's card, with everything handed in (also drawn on its own by the screenshot test). */
@Composable
internal fun PlusPromoCard(onGo: () -> Unit, onClose: () -> Unit, onNever: () -> Unit, primaryFocusRequester: FocusRequester) {
    val panelShape = RoundedCornerShape(18.dp)
    // The same card size as the Plus welcome popup (it was once 600 dp wide and nearly the whole height of a 540 dp screen): 380 dp wide and a little over half the height.
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
        Text(
            text = "Get more from every movie night.",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Everything you use today stays free.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Benefits.forEach { benefit -> BenefitRow(benefit) }
        }
        Spacer(Modifier.height(12.dp))
        // Both buttons sit together in the middle of the card.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            MangoButton(
                text = "Take me there",
                icon = Icons.Filled.ArrowForward,
                onClick = onGo,
                style = MangoButtonStyle.FILLED,
                focusRequester = primaryFocusRequester,
                compact = true
            )
            MangoButton(
                text = "Close",
                icon = Icons.Filled.Close,
                onClick = onClose,
                style = MangoButtonStyle.GLASS,
                clickSound = ClickSound.BACK,
                compact = true
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Explore plans in Settings → Arc TV Plus",
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TvFocusSurface(
                onClick = onNever,
                shape = RoundedCornerShape(8.dp),
                focusedScale = 1.04f,
                clickSound = ClickSound.BACK
            ) {
                Text(
                    text = "Don't show me again",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(benefit: PromoBenefit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MangoSurface)
                .border(1.dp, DividerSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = benefit.icon, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = benefit.title, color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(text = benefit.detail, color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
        }
    }
}
