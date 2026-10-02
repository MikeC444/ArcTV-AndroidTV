package com.mangotv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MangoColorScheme = darkColorScheme(
    primary = ArcAccent,
    onPrimary = MangoBackground,
    secondary = ArcViolet,
    onSecondary = MangoBackground,
    tertiary = ArcBlue,
    background = MangoBackground,
    onBackground = TextPrimary,
    surface = MangoSurface,
    onSurface = TextPrimary,
    surfaceVariant = MangoSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = DividerSubtle,
)

@Composable
fun MangoTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MangoColorScheme,
        typography = MangoTypography,
        content = content
    )
}
