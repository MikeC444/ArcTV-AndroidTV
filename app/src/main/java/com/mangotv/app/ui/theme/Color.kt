package com.mangotv.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core surfaces — near-black, cinematic
val MangoBackground = Color(0xFF08080A)
val MangoBackgroundElevated = Color(0xFF141417)
val MangoSurface = Color(0xFF1C1C20)
val MangoSurfaceHigh = Color(0xFF26262B)

// Brand gradient -- the Arc TV logo's cyan, blue and violet, used sparingly as an accent. All three are well above 4.5:1
// against the near-black surfaces, so dark text sits on them (the same palette the web app uses).
val ArcCyan = Color(0xFF19E6FF)
val ArcBlue = Color(0xFF2F80FF)
val ArcViolet = Color(0xFF9B5CFF)

// The one accent colour: selected states, switches, spinners, focus glow, cursors, progress.
val ArcAccent = ArcCyan

// Warning text and icons only -- not part of the brand.
val ArcWarn = Color(0xFFFFB020)

// Errors and destructive actions only -- not part of the brand.
val ErrorCoral = Color(0xFFFF3D68)

val ArcBrandGradient = Brush.linearGradient(
    colors = listOf(ArcCyan, ArcBlue, ArcViolet)
)

// Per-tier accent colors for source/quality badges: 4K takes the brand accent (cyan), and these two cooler hues
// keep 1080p/720p distinct from it and from each other.
val MangoAzure = Color(0xFF3D8BFF)
val MangoTeal = Color(0xFF2DD9A8)

fun arcBrandGradient(angleColors: List<Color> = listOf(ArcCyan, ArcViolet)) =
    Brush.linearGradient(colors = angleColors)

// Text
val TextPrimary = Color(0xFFF6F6F8)
val TextSecondary = Color(0xFFAFAFB8)
val TextTertiary = Color(0xFF75757E)

// Structural
val DividerSubtle = Color(0x1FFFFFFF)
val ScrimColor = Color(0xFF08080A)

// Focus & interaction
val FocusGlow = ArcAccent
val FocusBorder = Color(0xFF8CF3FF)

// Ratings / progress
val ProgressTrack = Color(0x33FFFFFF)
val ProgressFill = ArcAccent

// Watched tick badge -- deliberately a distinct green rather than
// ArcAccent/MangoTeal (both already mean something else: focus/progress and
// quality tier, respectively), so "watched" reads unambiguously against a
// poster of any color.
val WatchedGreen = Color(0xFF2ECC71)
