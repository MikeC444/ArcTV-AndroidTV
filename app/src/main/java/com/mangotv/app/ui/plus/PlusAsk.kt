package com.mangotv.app.ui.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.MangoTvApplication

/** What an ask says: a headline and the line under it (the benefits and buttons are the same on every ask). */
data class PlusAsk(val headline: String, val subline: String)

/** Opened when someone without Plus presses Like or Not for me (they feed "Picked for you"). */
val PICKED_ASK = PlusAsk(
    headline = "Rate movies to tune your Home row.",
    subline = "Like and Not for me are part of Plus. Everything you use today stays free."
)

/** Opened by itself, once in a while, after a title has been watched through (see [PlusNudgeHost]). */
val AFTER_WATCH_ASK = PlusAsk(
    headline = "Enjoyed that? Get picks like it.",
    subline = "Plus chooses what to watch next from what you like. Everything you use today stays free."
)

/** Opened when someone without Plus presses the locked Your stats tab in Settings. */
val STATS_ASK = PlusAsk(
    headline = "See what you've been watching.",
    subline = "Your hours, streak and busiest day are part of Plus. Everything you use today stays free."
)

/** Opened when someone without Plus presses the locked Plus settings tab in Settings. */
val PLUS_SETTINGS_ASK = PlusAsk(
    headline = "Smart source picking, and more.",
    subline = "Plus settings are part of Plus. Everything you use today stays free."
)

/** Opened when someone without Plus presses Add profile. */
val PROFILES_ASK = PlusAsk(
    headline = "Give everyone their own profile.",
    subline = "Up to 5 profiles are part of Plus. Everything you use today stays free."
)

/**
 * Whether the Plus asks may be shown: signed in, Plus is a paid tier (not early access), this account has no Plus, and it is not a kids profile.
 * The same rules as the Home invitation had.
 */
@Composable
fun rememberPlusAskEligible(): Boolean {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val isGuest by container.guestGate.isGuest.collectAsStateWithLifecycle()
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    val profiles by container.profileRepository.state.collectAsStateWithLifecycle()
    return !isGuest && plus.paywall && !plus.active && profiles.active?.isKids != true
}

/**
 * The invitation as a dialog: opened by pressing something Plus-only (never on a timer). "Take me there" ([onGo]) goes to Settings -> Arc TV Plus;
 * Close and BACK ([onClose]) just close it. It is a real dialog window, so the remote stays inside it.
 */
@Composable
fun PlusAskDialog(ask: PlusAsk, onClose: () -> Unit, onGo: () -> Unit) {
    val primaryFocusRequester = remember { FocusRequester() }
    // A dialog window doesn't move D-pad focus by itself; start on the main button.
    LaunchedEffect(Unit) { runCatching { primaryFocusRequester.requestFocus() } }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            PlusInviteCard(
                headline = ask.headline,
                subline = ask.subline,
                onGo = onGo,
                onClose = onClose,
                onNever = null,
                primaryFocusRequester = primaryFocusRequester
            )
        }
    }
}
