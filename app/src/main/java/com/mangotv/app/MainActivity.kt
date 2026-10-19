package com.mangotv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mangotv.app.navigation.MangoNavHost
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.mangotv.app.ui.components.StartupSplash
import com.mangotv.app.ui.components.StartupSplashState
import com.mangotv.app.ui.theme.MangoTvTheme

class MainActivity : ComponentActivity() {
    /**
     * A direction press on the remote can ask Compose to move focus to a screen element that is not on screen at that moment (a pinned "up" or "down"
     * target that was scrolled out of its list, or is between two states); Compose then throws "FocusRequester is not initialized" from inside its
     * key handling, which closed the app. Nothing useful can be done with that press, so it is dropped (focus stays where it was) and noted in the
     * log, and every other error is passed on untouched.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("FocusRequester is not initialized") == true) {
                android.util.Log.w("Focus", "Dropped a key press whose focus target was not on screen (keyCode ${event.keyCode})", e)
                true
            } else {
                throw e
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MangoTvTheme {
                // The app loads behind the splash (a black screen with the logo) at a cold start, then it fades away.
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().onPreviewKeyEvent { StartupSplashState.active }) {
                    MangoNavHost()
                    StartupSplash()
                }
            }
        }
    }
}
