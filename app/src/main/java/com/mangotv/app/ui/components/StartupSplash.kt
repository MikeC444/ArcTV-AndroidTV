package com.mangotv.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** How long the Arc TV logo is held on an all-black screen at a cold start, hiding the app while it loads behind it. */
const val STARTUP_SPLASH_HOLD_MS = 3_000L
private const val STARTUP_SPLASH_FADE_MS = 450

/** Set once the splash has been shown in this process, so only a cold start gets it (not a screen rotation or a reopened Activity). */
object StartupSplashState {
    @Volatile var shown: Boolean = false

    /** True while the splash is up; the root swallows remote presses then, so they never reach the half-loaded app behind it. */
    var active by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * A fully black screen with just the Arc TV logo, drawn over the whole app for [STARTUP_SPLASH_HOLD_MS] at a cold start. The app behind it
 * composes and loads (Home, sign-in, caches) during that time, so none of its half-drawn screens show and the first-start lag goes unseen.
 * It then fades away. It swallows the remote and touch input while it is up.
 */
@Composable
fun StartupSplash(holdMillis: Long = STARTUP_SPLASH_HOLD_MS) {
    var visible by remember { mutableStateOf(!StartupSplashState.shown) }
    LaunchedEffect(Unit) {
        if (visible) {
            StartupSplashState.active = true
            StartupSplashState.shown = true
            delay(holdMillis)
            StartupSplashState.active = false
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(STARTUP_SPLASH_FADE_MS))) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            ArcLogo(fontSize = 44.sp)
        }
    }
}
