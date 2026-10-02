package com.mangotv.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mangotv.app.R
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.QrCodeImage
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * The full-screen "Finish payment on your phone" page: the plan chosen on the left, a big QR code on the right, and a
 * countdown while the TV waits for the payment. Back or "Change plan" returns to the plan list.
 */
@Composable
fun PlusCheckoutPage(state: PlusCheckoutState, remainingSeconds: Int, onClose: () -> Unit) {
    if (state !is PlusCheckoutState.ShowingQr && state !is PlusCheckoutState.Done) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val plan = if (state is PlusCheckoutState.ShowingQr) state.plan else (state as PlusCheckoutState.Done).plan
        val planName = PLUS_PLANS.firstOrNull { it.id == plan }?.label ?: "Plus"
        Box(modifier = Modifier.fillMaxSize().background(MangoBackground).padding(horizontal = 56.dp, vertical = 32.dp)) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(R.drawable.logo_arctv), contentDescription = "Arc TV", modifier = Modifier.height(36.dp))
                Spacer(Modifier.height(14.dp))
                Steps(done = state is PlusCheckoutState.Done)
                Spacer(Modifier.height(16.dp))
                if (state is PlusCheckoutState.Done) {
                    DonePanel(planName)
                } else if (state is PlusCheckoutState.ShowingQr) {
                    Text(
                        text = "Finish payment on your phone",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Scan the code, pay on Stripe's secure page, and Plus switches on here by itself.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlanCardPanel(plan = plan, planName = planName, price = state.priceLabel, onChange = onClose, modifier = Modifier.weight(1f))
                        QrPanel(url = state.url, remainingSeconds = remainingSeconds, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Secure payment by Stripe. Arc TV never sees your card.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Steps(done: Boolean) {
    val labels = listOf("Choose plan", "Pay on phone", "Start watching")
    // Choose plan is always complete here; paying is the current step until the payment lands.
    val current = if (done) 3 else 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val complete = index < current
            val active = index == current
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(if (complete) ArcAccent else Color.Transparent, CircleShape)
                    .border(2.dp, if (complete || active) ArcAccent else TextTertiary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (complete) Icon(Icons.Filled.Check, contentDescription = null, tint = MangoBackground, modifier = Modifier.size(15.dp))
                else Text(text = "${index + 1}", color = if (active) ArcAccent else TextTertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = label, color = if (complete || active) TextPrimary else TextTertiary, style = MaterialTheme.typography.labelLarge)
            if (index < labels.lastIndex) {
                Box(modifier = Modifier.padding(horizontal = 14.dp).width(48.dp).height(2.dp).background(if (complete) ArcAccent else MangoSurfaceHigh))
            }
        }
    }
}

@Composable
private fun PlanCardPanel(plan: String, planName: String, price: String?, onChange: () -> Unit, modifier: Modifier) {
    val changeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { changeFocus.requestFocus() } }
    Column(
        modifier = modifier
            .background(MangoSurface, RoundedCornerShape(MangoDimens.CardCornerRadius))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "YOUR PLAN", color = TextTertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(text = "Arc TV Plus · $planName", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = price ?: "Shown on your phone", color = ArcAccent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val suffix = billingSuffix(plan)
            if (price != null && suffix != null) {
                Spacer(Modifier.width(6.dp))
                Text(text = suffix, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PLUS_PERKS.filter { !it.comingSoon }.forEach { perk ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = perk.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(2.dp))
        InfoRow("Billing", billingLabel(plan))
        InfoRow("Due today", price ?: "-")
        Text(text = billingNote(plan), color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        MangoButton(text = "Change plan", icon = Icons.Filled.ArrowBack, onClick = onChange, focusRequester = changeFocus, compact = true)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QrPanel(url: String, remainingSeconds: Int, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(MangoSurface, RoundedCornerShape(MangoDimens.CardCornerRadius))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Scan to pay", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        QrCodeImage(content = url, modifier = Modifier.size(280.dp), sizePx = 840)
        Text(text = "Point your phone's camera at the code.", color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ArcAccent)
            Spacer(Modifier.width(8.dp))
            Text(text = "Waiting for payment…  ${formatCountdown(remainingSeconds)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ColumnScope.DonePanel(planName: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(72.dp).background(ArcAccent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MangoBackground, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(text = "You're all set", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = "Arc TV Plus ($planName) is on. Thank you for supporting Arc TV.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}
