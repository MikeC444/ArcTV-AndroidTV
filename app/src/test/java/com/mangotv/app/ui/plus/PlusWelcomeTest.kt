package com.mangotv.app.ui.plus

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mangotv.app.data.plus.welcomeDue
import com.mangotv.app.ui.theme.MangoTvTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The one-time Plus welcome: when it is due, and what its two buttons do. Saves a PNG with -PscreenshotOut=<file> (a desktop render, not a TV). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi", sdk = [34])
class PlusWelcomeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dueOnceEverUntilSeen() {
        assertTrue(welcomeDue(seen = false, shownThisSession = false))
        assertFalse(welcomeDue(seen = true, shownThisSession = false)) // once ever
        assertFalse(welcomeDue(seen = false, shownThisSession = true)) // never twice in one launch
    }

    @Test
    fun theTwoButtonsReachTheirActions() {
        val calls = mutableListOf<String>()
        rule.setContent {
            MangoTvTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    PlusWelcomeCard(onGo = { calls += "go" }, onClose = { calls += "close" }, primaryFocusRequester = FocusRequester())
                }
            }
        }
        rule.onNodeWithText("Everything in Arc TV Plus.").assertExists()
        rule.onNodeWithText("Smart source picking").assertExists()
        rule.onNodeWithText("See my Plus settings").performClick()
        rule.onNodeWithText("Close").performClick()
        assertEquals(listOf("go", "close"), calls)
        System.getProperty("screenshot.out")?.let { base ->
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(base.removeSuffix(".png") + "-plus-welcome.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
