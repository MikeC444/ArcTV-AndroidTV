package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.ErrorCoral
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Settings > Arc TV Plus: whether you have Plus, what it adds, and how to subscribe. While Plus is in early access the
 * tab just says so. Once the paywall is on, someone without Plus picks a plan and the TV shows the secure Stripe checkout
 * page as a QR code to scan with a phone -- paying with a remote is miserable -- then switches Plus on by itself when the
 * payment goes through. Plain text isn't focusable, so the perks and plan cards are, which is what lets the remote move
 * down the whole tab.
 */
@Composable
fun ColumnScope.PlusSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    viewModel: PlusSettingsViewModel = viewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val checkout by viewModel.checkout.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val showCheckout = checkout is PlusCheckoutState.Starting || checkout is PlusCheckoutState.Error
    val sellPlans = status.paywall && !status.active

    // The QR code gets a full-screen page of its own; Back or "Change plan" returns here.
    PlusCheckoutPage(state = checkout, remainingSeconds = remaining, onClose = viewModel::cancelCheckout)

    LazyColumn(
        modifier = Modifier.weight(1f),
        // Room for a focused row's scale-up, same as the other tabs.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        !status.paywall -> {
                            Pill(text = "Early access", container = ArcAccent, content = MangoBackground)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Arc TV Plus is in early access: its features are free for now and will need a Plus subscription once it launches.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        status.owned -> {
                            Pill(text = "Arc TV Plus", container = ArcAccent, content = MangoBackground)
                            Spacer(Modifier.width(10.dp))
                            Text(text = ownedSentence(status.plan, status.validUntil), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            Pill(text = "Free plan", container = MangoSurfaceHigh, content = TextSecondary)
                            Spacer(Modifier.width(10.dp))
                            Text(text = "You're on the free plan.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(text = PLUS_FREE_NOTE, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = ArcViolet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text = PLUS_PROCEEDS_NOTE, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item(key = "perks_header") {
            Text(
                text = "What Plus adds",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        PLUS_PERKS.forEachIndexed { index, perk ->
            item(key = "perk_${perk.title}") {
                PerkRow(
                    perk = perk,
                    paywall = status.paywall,
                    focusRequester = if (index == 0) contentFocusRequester else null,
                    focusUp = if (index == 0) navFocusRequester else null,
                    focusLeft = sidebarFocusRequester
                )
            }
        }

        if (sellPlans) {
            item(key = "steps") {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "How to subscribe", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(text = "1. Pick a plan below: monthly, yearly, or a one-time Lifetime payment.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "2. Scan the QR code with your phone and pay on the secure Stripe page.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "3. This screen switches Plus on by itself once the payment goes through.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item(key = "plans") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PLUS_PLANS.forEachIndexed { index, plan ->
                        PlanCard(
                            plan = plan,
                            starting = (checkout as? PlusCheckoutState.Starting)?.plan == plan.id,
                            onChoose = { viewModel.choose(plan.id) },
                            modifier = Modifier.weight(1f),
                            focusLeft = if (index == 0) sidebarFocusRequester else null
                        )
                    }
                }
            }
        }

        if (showCheckout) {
            item(key = "checkout") {
                CheckoutPanel(checkout = checkout, onCancel = viewModel::cancelCheckout, focusLeft = sidebarFocusRequester)
            }
        }

        item(key = "footer") {
            Text(
                text = if (sellPlans) "Payments are handled by Stripe's secure checkout page; Arc TV never sees your card." else "Plus features appear on your account by themselves.",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** "You have Arc TV Plus (Yearly). Your current period runs to 3 Jan 2027." or "...for life." */
private fun ownedSentence(plan: String?, validUntil: String?): String {
    val name = PLUS_PLANS.firstOrNull { it.id == plan }?.label
    val head = if (name != null) "You have Arc TV Plus ($name)" else "You have Arc TV Plus"
    val until = validUntil?.let { runCatching { formatDate(it) }.getOrNull() }
    return if (until != null) "$head. Your current period runs to $until. Thank you for supporting Arc TV." else "$head, for life. Thank you for supporting Arc TV."
}

private fun formatDate(iso: String): String {
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso.take(19)) ?: error("bad date")
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(parsed)
}

@Composable
private fun CheckoutPanel(checkout: PlusCheckoutState, onCancel: () -> Unit, focusLeft: FocusRequester?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MangoSurface, RoundedCornerShape(MangoDimens.CardCornerRadius))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (checkout) {
            is PlusCheckoutState.Starting -> Text(text = "Getting your checkout ready…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            is PlusCheckoutState.ShowingQr, is PlusCheckoutState.Done -> Unit
            is PlusCheckoutState.Error -> {
                Text(text = checkout.message, color = ErrorCoral, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                MangoButton(text = "Close", icon = Icons.Filled.Close, onClick = onCancel, focusLeft = focusLeft, compact = true)
            }
            is PlusCheckoutState.Idle -> Unit
        }
    }
}

@Composable
private fun PerkRow(perk: PlusPerk, paywall: Boolean, focusRequester: FocusRequester?, focusUp: FocusRequester?, focusLeft: FocusRequester?) {
    // Nothing to "click": the row is focusable so the remote can step down the tab and bring the rest into view.
    TvFocusSurface(
        onClick = {},
        clickSound = ClickSound.NONE,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusLeft = focusLeft
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = perk.title, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(10.dp))
                Pill(
                    text = when {
                        perk.comingSoon -> "Coming soon"
                        paywall -> "Plus"
                        else -> "Included in early access"
                    },
                    container = MangoSurfaceHigh,
                    content = if (perk.comingSoon) TextSecondary else ArcAccent
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(text = perk.detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanCard(plan: PlusPlan, starting: Boolean, onChoose: () -> Unit, modifier: Modifier, focusLeft: FocusRequester?) {
    TvFocusSurface(
        onClick = onChoose,
        modifier = modifier,
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        focusedScale = 1.03f,
        backgroundColor = MangoSurface,
        alwaysShowBorder = plan.note != null && plan.id == "yearly",
        borderColor = if (plan.id == "yearly") ArcAccent else TextPrimary,
        focusLeft = focusLeft
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            if (plan.note != null) {
                Pill(text = plan.note, container = MangoSurfaceHigh, content = ArcAccent)
                Spacer(Modifier.height(6.dp))
            }
            Text(text = plan.label, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                text = plan.price ?: "Price at checkout",
                color = if (plan.price != null) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = plan.per, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Text(text = plan.blurb, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    starting -> "Opening…"
                    plan.id == "lifetime" -> "Get Lifetime"
                    else -> "Choose ${plan.label}"
                },
                color = ArcAccent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Pill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(container, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}
