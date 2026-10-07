package com.mangotv.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mangotv.app.ui.theme.MangoTvTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws the "Finish payment on your phone" page at TV size (-Dscreenshot.out=<file> saves it), to check it fits the screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi", sdk = [34])
class CheckoutScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun trialCheckoutPage() {
        val state = PlusCheckoutState.ShowingQr("monthly", "https://checkout.stripe.com/c/pay/cs_test_example", "£4.99", 5)
        rule.setContent { MangoTvTheme { CheckoutPageContent(state, "monthly", 1700, onClose = {}) } }
        rule.waitForIdle()
        System.getProperty("screenshot.out")?.let { base ->
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(base.removeSuffix(".png") + "-checkout.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
