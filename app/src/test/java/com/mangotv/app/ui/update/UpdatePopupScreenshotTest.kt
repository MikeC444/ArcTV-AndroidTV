package com.mangotv.app.ui.update

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mangotv.app.data.update.AppUpdate
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoTvTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws the update pop-up with the real "Unreleased" release notes at TV size (-Dscreenshot.out=<file> saves it). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi", sdk = [34])
class UpdatePopupScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun popupWithPatchNotes() {
        val notes = File("../RELEASE_NOTES.md").readText().substringAfter("## Unreleased").substringBefore("\n## ").trim()
        val update = AppUpdate(tag = "0.3.0", notes = notes, assetUrl = "https://example.invalid/arctv.apk", assetSizeBytes = 96_000_000L)
        rule.setContent {
            MangoTvTheme {
                Box(Modifier.fillMaxSize().background(MangoBackground)) {
                    UpdatePopupBody(UpdateUiState(update = update, showBanner = true), update, "Download size 91.6 MB", FocusRequester(), {}, {}, {})
                }
            }
        }
        rule.waitForIdle()
        System.getProperty("screenshot.out")?.let { base ->
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(base.removeSuffix(".png") + "-update.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
