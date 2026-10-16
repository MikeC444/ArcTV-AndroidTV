package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.mangotv.app.ui.theme.ArcBrandGradient
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mangotv.app.data.plus.PlusStatus
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.components.MangoButtonStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ArcWarn
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
    val cancelState by viewModel.cancel.collectAsStateWithLifecycle()
    val boardBusy by viewModel.boardBusy.collectAsStateWithLifecycle()
    val boardError by viewModel.boardError.collectAsStateWithLifecycle()
    val showCheckout = checkout is PlusCheckoutState.Error
    val context = androidx.compose.ui.platform.LocalContext.current
    val sellPlans = status.paywall && !status.active
    // The Cancel subscription button sits above the status block (where focus lands), so Up from the status block must reach it.
    val cancelFocus = remember { FocusRequester() }
    val hasCancelButton = status.owned && (status.plan == "monthly" || status.plan == "yearly") && !status.cancelAtPeriodEnd

    // The QR code gets a full-screen page of its own; Back or "Change plan" returns here.
    PlusCheckoutPage(state = checkout, remainingSeconds = remaining, onClose = viewModel::cancelCheckout)

    if (cancelState.confirming) {
        CancelSubscriptionDialog(status = status, state = cancelState, onKeep = viewModel::keepPlus, onConfirm = viewModel::confirmCancel)
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        // Room for a focused row's scale-up, same as the other tabs.
        contentPadding = SettingsListPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (status.owned && (status.plan == "monthly" || status.plan == "yearly")) {
            item(key = "subscription") {
                SubscriptionRow(status = status, onCancel = viewModel::askToCancel, focusLeft = sidebarFocusRequester, focusUp = navFocusRequester, focusDown = contentFocusRequester, focusRequester = cancelFocus)
            }
        }

        item(key = "status") {
            // Focusable so the remote can step back up to the top of the tab (plain text can't take focus, which left the
            // list stuck scrolled down), and it is where focus lands when the tab opens.
            TvFocusSurface(
                onClick = {},
                clickSound = ClickSound.NONE,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
                focusedScale = 1f,
                focusedElevation = 0f,
                backgroundColor = MangoBackground,
                borderColor = TextPrimary,
                focusRequester = contentFocusRequester,
                focusUp = if (hasCancelButton) cancelFocus else navFocusRequester,
                focusLeft = sidebarFocusRequester
            ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }

        if (status.owned && status.plan == "lifetime" && status.supporterBoard != null) {
            item(key = "supporters") {
                SupportersBoardOption(
                    on = status.supporterBoard == true,
                    anonymous = status.supporterAnonymous == true,
                    busy = boardBusy,
                    error = boardError,
                    onChange = viewModel::setSupporterBoard,
                    focusLeft = sidebarFocusRequester
                )
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
        // Two tiles to a row (equal heights, so Down moves along the page instead of sideways between uneven tiles), each with an icon, the
        // name, a Plus / Coming soon tag and what it does: far less scrolling than five full-width text panels.
        PLUS_PERKS.chunked(2).forEachIndexed { rowIndex, row ->
            item(key = "perks_row_$rowIndex") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { column, perk ->
                        PerkTile(
                            perk = perk,
                            paywall = status.paywall,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            focusLeft = if (column == 0) sidebarFocusRequester else null
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (sellPlans) {
            item(key = "plans") {
                // Equal-height cards: with uneven heights the taller card's bottom edge counted as "below" the others, so Down
                // moved sideways between cards instead of on down the tab.
                // The top padding sets the plans apart from the perk tiles above them.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(IntrinsicSize.Min)) {
                    PLUS_PLANS.forEachIndexed { index, plan ->
                        PlanCard(
                            plan = plan,
                            trialDays = status.trialDays,
                            priceText = planPriceText(plan.id, status.prices),
                            starting = (checkout as? PlusCheckoutState.Starting)?.plan == plan.id,
                            onChoose = { viewModel.choose(plan.id) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            focusLeft = if (index == 0) sidebarFocusRequester else null
                        )
                    }
                }
            }
        }

        if (sellPlans) {
            item(key = "board_note") {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = ArcViolet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text = LIFETIME_BOARD_NOTE, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
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
                text = if (sellPlans) {
                    (if (status.prices != null) "List prices; promo codes can be applied at checkout. " else "") +
                    (if (status.trialDays > 0) "Yearly free trial: your card is taken at checkout but nothing is charged for ${status.trialDays} days. Cancel before then and you pay nothing. Once per account. " else "") +
                        "Payments are handled by Stripe's secure checkout page; Arc TV never sees your card."
                } else "Plus features appear on your account by themselves.",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * For a Lifetime member: the choice to be on the supporters board on arctv.org. Off until they turn it on (buying never does); once on, a second
 * switch lists them as Anonymous (no name, no picture).
 */
@Composable
private fun SupportersBoardOption(on: Boolean, anonymous: Boolean, busy: Boolean, error: String?, onChange: (Boolean, Boolean) -> Unit, focusLeft: FocusRequester) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSwitchRow(
            title = "Supporters board",
            description = "Thank you for going Lifetime! Show your display name and profile picture on the supporters board on arctv.org. It is off until you turn it on, and you can turn it off any time.",
            checked = on,
            onToggle = { if (!busy) onChange(!on, false) },
            focusLeft = focusLeft
        )
        if (on) {
            SettingsSwitchRow(
                title = "List me as Anonymous",
                description = "Keep your name and picture private: you still show on the board, as \"Anonymous\".",
                checked = anonymous,
                onToggle = { if (!busy) onChange(true, !anonymous) },
                focusLeft = focusLeft
            )
        }
        if (error != null) Text(error, color = ArcWarn, style = MaterialTheme.typography.bodySmall)
    }
}

/** "You have Arc TV Plus (Yearly). Your current period runs to 3 Jan 2027." or "...for life." */
private fun ownedSentence(plan: String?, validUntil: String?): String {
    val name = PLUS_PLANS.firstOrNull { it.id == plan }?.label
    val head = if (name != null) "You have Arc TV Plus ($name)" else "You have Arc TV Plus"
    val until = validUntil?.let { runCatching { formatDate(it) }.getOrNull() }
    return if (until != null) "$head. Your current period runs to $until. Thank you for supporting Arc TV." else "$head, for life. Thank you for supporting Arc TV."
}

/** Under the plan status, for a paying monthly or yearly subscriber (not Lifetime): when it renews, and a way to cancel it. Same wording as the web app. */
@Composable
private fun SubscriptionRow(status: PlusStatus, onCancel: () -> Unit, focusLeft: FocusRequester, focusUp: FocusRequester, focusDown: FocusRequester, focusRequester: FocusRequester) {
    val planLabel = PLUS_PLANS.firstOrNull { it.id == status.plan }?.label.orEmpty()
    val until = status.validUntil?.let { runCatching { formatDate(it) }.getOrNull() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MangoDimens.CardCornerRadius))
            .background(MangoSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Arc TV Plus · $planLabel", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            Text(
                text = when {
                    status.cancelAtPeriodEnd -> "Your subscription won't renew." + (until?.let { " You keep Plus until $it." } ?: "")
                    until != null -> "Renews on $until."
                    else -> "Active."
                },
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!status.cancelAtPeriodEnd) {
            Spacer(Modifier.width(16.dp))
            MangoButton(text = "Cancel subscription", icon = Icons.Filled.Close, onClick = onCancel, compact = true, focusRequester = focusRequester, focusUp = focusUp, focusDown = focusDown, focusLeft = focusLeft)
        }
    }
}

/** "Cancel your monthly subscription?" -- Keep Plus is focused first so a stray press never cancels. */
@Composable
private fun CancelSubscriptionDialog(status: PlusStatus, state: CancelSubscriptionState, onKeep: () -> Unit, onConfirm: () -> Unit) {
    val planLabel = PLUS_PLANS.firstOrNull { it.id == status.plan }?.label.orEmpty().lowercase()
    val until = status.validUntil?.let { runCatching { formatDate(it) }.getOrNull() }
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keepFocus.requestFocus() } }
    Dialog(onDismissRequest = onKeep) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MangoBackgroundElevated)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Cancel your $planLabel subscription?", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (until != null) "You keep Arc TV Plus until $until. After that it won't renew and you won't be charged again."
                else "You keep Arc TV Plus until the end of the period you've paid for. After that it won't renew and you won't be charged again.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.error != null) Text(text = state.error, color = ErrorCoral, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MangoButton(text = "Keep Plus", icon = Icons.Filled.Check, onClick = onKeep, style = MangoButtonStyle.FILLED, compact = true, focusRequester = keepFocus)
                MangoButton(
                    text = if (state.busy) "Cancelling…" else "Cancel subscription",
                    icon = Icons.Filled.Close,
                    onClick = onConfirm,
                    compact = true,
                    dimmed = state.busy
                )
            }
        }
    }
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
            is PlusCheckoutState.Starting, is PlusCheckoutState.ShowingQr, is PlusCheckoutState.Done -> Unit
            is PlusCheckoutState.Error -> {
                Text(text = checkout.message, color = ErrorCoral, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                MangoButton(text = "Close", icon = Icons.Filled.Close, onClick = onCancel, focusLeft = focusLeft, compact = true)
            }
            is PlusCheckoutState.Idle -> Unit
        }
    }
}

/** The icon a perk is shown with. */
private fun perkIcon(title: String): ImageVector = when (title) {
    "Picked for you" -> Icons.Filled.Favorite
    "Profiles" -> Icons.Filled.Groups
    "Parental controls" -> Icons.Filled.Shield
    "Smart source picking" -> Icons.Filled.Bolt
    "Your stats" -> Icons.Filled.BarChart
    "Download" -> Icons.Filled.Download
    else -> Icons.Filled.WorkspacePremium
}

@Composable
private fun PerkTile(perk: PlusPerk, paywall: Boolean, modifier: Modifier, focusLeft: FocusRequester?) {
    // Nothing to "click": the tile is focusable so the remote can step down the tab and bring the rest into view.
    TvFocusSurface(
        onClick = {},
        clickSound = ClickSound.NONE,
        modifier = modifier,
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusLeft = focusLeft
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .let { if (perk.comingSoon) it.background(MangoSurfaceHigh) else it.background(ArcBrandGradient) },
                contentAlignment = Alignment.Center
            ) {
                Icon(perkIcon(perk.title), contentDescription = null, tint = if (perk.comingSoon) TextSecondary else MangoBackground, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = perk.title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Pill(
                    text = when {
                        perk.comingSoon -> "Coming soon"
                        perk.otherDevicesOnly -> "Web, Mac and Windows"
                        paywall -> "Plus"
                        else -> "Included in early access"
                    },
                    container = MangoSurfaceHigh,
                    content = if (perk.comingSoon) TextSecondary else ArcAccent
                )
                Spacer(Modifier.height(6.dp))
                Text(text = perk.detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlusPlan, trialDays: Int, priceText: PlanPriceText?, starting: Boolean, onChoose: () -> Unit, modifier: Modifier, focusLeft: FocusRequester?) {
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
            val pillText = if (hasTrial(plan.id, trialDays)) "$trialDays days free" else plan.note
            if (pillText != null) {
                Pill(text = pillText, container = MangoSurfaceHigh, content = ArcAccent)
                Spacer(Modifier.height(6.dp))
            }
            Text(text = plan.label, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            val saving = priceText?.saving
            val perDay = priceText?.perDay
            val was = priceText?.was
            val versus = priceText?.versus
            val shownPrice = priceText?.price ?: plan.price
            Text(
                text = shownPrice ?: "Price at checkout",
                color = if (shownPrice != null) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = plan.per, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            if (saving != null) {
                Spacer(Modifier.height(4.dp))
                Pill(text = saving, container = MangoSurfaceHigh, content = ArcAccent)
            }
            if (perDay != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        // The monthly price, struck through: what paying monthly costs against this.
                        if (was != null) {
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append("$was/mo") }
                            append("  ")
                        }
                        append(perDay)
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (versus != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = versus, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            if (plan.id == "lifetime") {
                // Lifetime buyers are asked at checkout whether to be on the supporters board on arctv.org (see the web app's plan card).
                Spacer(Modifier.height(6.dp))
                Pill(text = "Join the supporters board", container = MangoSurfaceHigh, content = ArcAccent)
            }
            Spacer(Modifier.height(6.dp))
            Text(text = plan.blurb, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (starting) "Opening…" else planButtonLabel(plan.id, plan.label, trialDays),
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
