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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.ui.theme.ArcBlue
import com.mangotv.app.ui.theme.ArcCyan
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.MangoBackground

/** The small "PLUS" badge, the same cyan-to-violet gradient as the logo badge on the invitation: put next to anything that needs Arc TV Plus. */
@Composable
fun PlusBadge(modifier: Modifier = Modifier, fontSize: TextUnit = 10.sp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.horizontalGradient(listOf(ArcCyan, ArcBlue, ArcViolet)))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = "PLUS", color = MangoBackground, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, letterSpacing = 0.8.sp)
    }
}
