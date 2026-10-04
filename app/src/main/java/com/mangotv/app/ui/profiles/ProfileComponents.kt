package com.mangotv.app.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.data.profile.PIN_LENGTH
import com.mangotv.app.data.profile.avatarById
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ErrorCoral
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/** A profile's picture: its illustration, clipped to a rounded square. */
@Composable
fun ProfileAvatarTile(avatar: String, size: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = 14.dp) {
    val a = avatarById(avatar)
    Image(
        painter = painterResource(a.res),
        contentDescription = a.label,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
    )
}

/**
 * A PIN pad you can drive with a D-pad: digits in a 3x4 grid, dots above. Calls [onComplete] with the PIN once [PIN_LENGTH] digits
 * are in. [resetKey] clears the digits when it changes (a wrong PIN, or moving to the next step).
 */
@Composable
fun PinPad(
    title: String,
    message: String?,
    resetKey: Any?,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var digits by remember(resetKey) { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PIN_LENGTH) { index ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (index < digits.length) ArcAccent else Color(0x33FFFFFF))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = message.orEmpty(),
            color = if (message != null) ErrorCoral else Color.Transparent,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(20.dp)
        )
        Spacer(Modifier.height(10.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("DEL", "0", "CANCEL"))
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { colIndex, key ->
                    PinKey(
                        label = when (key) {
                            "DEL" -> "⌫"
                            "CANCEL" -> "Cancel"
                            else -> key
                        },
                        focusRequester = if (rowIndex == 0 && colIndex == 0) firstKey else null,
                        onClick = {
                            when (key) {
                                "DEL" -> digits = digits.dropLast(1)
                                "CANCEL" -> onCancel()
                                else -> if (digits.length < PIN_LENGTH) {
                                    digits += key
                                    if (digits.length == PIN_LENGTH) onComplete(digits)
                                }
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PinKey(label: String, focusRequester: FocusRequester?, onClick: () -> Unit) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MangoSurface,
        focusRequester = focusRequester,
        modifier = Modifier.size(width = 84.dp, height = 56.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (label.length > 1 && label != "⌫") 14.sp else 22.sp
            )
        }
    }
}

/** A small label under or beside a tile ("Kids"). */
@Composable
fun SmallCaption(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = modifier)
}

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
