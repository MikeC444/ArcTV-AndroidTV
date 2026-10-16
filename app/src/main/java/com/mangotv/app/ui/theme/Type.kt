package com.mangotv.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

// System sans-serif is used deliberately: Fire TV devices lack Google Play
// Services, so downloadable Google Fonts are not reliable. Premium feel comes
// from scale, weight and spacing instead of a custom typeface.
private val MangoFontFamily = FontFamily.SansSerif

val MangoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MangoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * This typography with every size (and line height) multiplied by [factor], and no size below [minSp] so small print stays readable from the
 * sofa. Used to fit more of a busy page (Settings) on a 540dp-tall TV screen without touching every Text on it.
 */
fun Typography.scaled(factor: Float, minSp: Float = 10f): Typography {
    fun TextStyle.s(): TextStyle {
        val size = if (fontSize.isSpecified) maxOf(fontSize.value * factor, minOf(fontSize.value, minSp)).sp else fontSize
        val line = if (lineHeight.isSpecified && fontSize.isSpecified) (lineHeight.value * (size.value / fontSize.value)).sp else lineHeight
        return copy(fontSize = size, lineHeight = line)
    }
    return copy(
        displayLarge = displayLarge.s(), displayMedium = displayMedium.s(), displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(), headlineMedium = headlineMedium.s(), headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(), titleMedium = titleMedium.s(), titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(), bodyMedium = bodyMedium.s(), bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(), labelMedium = labelMedium.s(), labelSmall = labelSmall.s()
    )
}
