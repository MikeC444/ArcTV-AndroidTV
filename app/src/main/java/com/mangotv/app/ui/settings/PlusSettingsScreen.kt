package com.mangotv.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Settings > Arc TV Plus: what Plus adds and how to subscribe. Nothing here changes what the free app does, and no
 * payment happens on the TV: a plan with a checkout page opens it in whatever app the person picks (their phone's
 * browser is the usual place), and until then every plan says it opens soon. Plain text isn't focusable, so the
 * perks and plan cards are, which is what lets the remote move down the whole tab.
 */
@Composable
fun ColumnScope.PlusSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        // Room for a focused row's scale-up, same as the other tabs.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(text = "Free plan", container = ArcAccent, content = MangoBackground)
                    Spacer(Modifier.width(10.dp))
                    Text(text = "You're on the free plan.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
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
                    focusRequester = if (index == 0) contentFocusRequester else null,
                    focusUp = if (index == 0) navFocusRequester else null,
                    focusLeft = sidebarFocusRequester
                )
            }
        }

        item(key = "steps") {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "How to subscribe", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(text = "1. You're already signed in to your Arc TV account.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(text = "2. Pick a plan below: monthly, yearly, or a one-time Lifetime payment.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(text = "3. Complete the secure checkout. Plus is added to your account.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item(key = "plans") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PLUS_PLANS.forEachIndexed { index, plan ->
                    PlanCard(
                        plan = plan,
                        modifier = Modifier.weight(1f),
                        focusLeft = if (index == 0) sidebarFocusRequester else null
                    )
                }
            }
        }

        item(key = "footer") {
            Text(
                text = if (plusIsOnSale()) {
                    "Payments are handled by a secure checkout page."
                } else {
                    "Plus isn't on sale yet. When it is, you'll subscribe right here — there's nothing to do now."
                },
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PerkRow(perk: PlusPerk, focusRequester: FocusRequester?, focusUp: FocusRequester?, focusLeft: FocusRequester?) {
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
                if (perk.comingSoon) {
                    Spacer(Modifier.width(10.dp))
                    Pill(text = "Coming soon", container = MangoSurfaceHigh, content = TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = perk.detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanCard(plan: PlusPlan, modifier: Modifier, focusLeft: FocusRequester?) {
    val context = LocalContext.current
    TvFocusSurface(
        onClick = {
            if (plan.checkoutUrl.isBlank()) {
                Toast.makeText(context, "Plus isn't on sale yet", Toast.LENGTH_SHORT).show()
            } else {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(plan.checkoutUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { Toast.makeText(context, "Couldn't open the checkout page", Toast.LENGTH_SHORT).show() }
            }
        },
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
                text = plan.price ?: "Price announced soon",
                color = if (plan.price != null) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (plan.price != null) {
                Text(text = plan.per, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(text = plan.blurb, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    plan.checkoutUrl.isBlank() -> "Opens soon"
                    plan.id == "lifetime" -> "Get Lifetime"
                    else -> "Choose ${plan.label}"
                },
                color = if (plan.checkoutUrl.isBlank()) TextTertiary else ArcAccent,
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
