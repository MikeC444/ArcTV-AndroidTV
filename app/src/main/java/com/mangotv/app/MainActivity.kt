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
