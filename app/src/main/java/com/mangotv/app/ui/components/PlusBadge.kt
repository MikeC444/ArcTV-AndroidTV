package com.mangotv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.ui.theme.ArcBlue
import com.mangotv.app.ui.theme.ArcCyan
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.MangoBackground

/**
 * The small "PLUS" badge, the same cyan-to-violet gradient as the logo badge on the invitation: put next to anything that needs Arc TV Plus.
 * Kept tiny: its own text style (line height equal to the size) so the badge is only as tall as its letters, not as tall
 * as the line of the text it sits in.
 */
@Composable
fun PlusBadge(modifier: Modifier = Modifier, fontSize: TextUnit = 8.sp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(ArcCyan, ArcBlue, ArcViolet)))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = "PLUS",
            color = MangoBackground,
            style = TextStyle(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        )
    }
}
