package com.mangotv.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.R

/**
 * The Arc TV mark with its wordmark, drawn from the transparent logo image (white lettering, so it only suits the
 * app's dark backgrounds). The image is wide (about 3.3:1) and is always drawn at its own proportions: only the height
 * is set, and the width follows.
 *
 * [fontSize] keeps the size scale the old text logo used, so every call site reads the same: the height is
 * [fontSize] times 1.3, which puts the wordmark's letters at about the height the old "MANGO TV" text had.
 */
@Composable
fun ArcLogo(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp
) {
    Image(
        painter = painterResource(R.drawable.logo_arctv),
        contentDescription = "Arc TV",
        contentScale = ContentScale.Fit,
        modifier = modifier.height((fontSize.value * LOGO_HEIGHT_PER_SP).dp)
    )
}

private const val LOGO_HEIGHT_PER_SP = 1.3f
