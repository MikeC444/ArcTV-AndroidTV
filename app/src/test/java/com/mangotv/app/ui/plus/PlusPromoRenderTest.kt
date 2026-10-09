package com.mangotv.app.ui.plus

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mangotv.app.ui.theme.MangoTvTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Plus invitation (for people without Plus): its three buttons reach their actions. Saves a PNG with -PscreenshotOut=<file> (a desktop render, not a TV). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi", sdk = [34])
class PlusPromoRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theButtonsReachTheirActions() {
        val calls = mutableListOf<String>()
        rule.setContent {
            MangoTvTheme {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    PlusPromoCard(onGo = { calls += "go" }, onClose = { calls += "close" }, onNever = { calls += "never" }, primaryFocusRequester = FocusRequester())
                }
            }
        }
        rule.onNodeWithText("Get more from every movie night.").assertExists()
        rule.onNodeWithText("Take me there").performClick()
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Don't show me again").performClick()
        assertEquals(listOf("go", "close", "never"), calls)
        System.getProperty("screenshot.out")?.let { base ->
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(base.removeSuffix(".png") + "-plus-promo.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
