package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where the auth screen currently is. */
data class AuthState(
    val signingUp: Boolean = true,
    val busy: Boolean = false,
    val error: String = "",
)

/**
 * Spec round 2 — Welcome. One Google button for sign in and sign up.
 * Email and guest sit as text links underneath. No "which one am I on" fork.
 */
@Composable
fun AuthScreen(
    state: AuthState,
    insetTop: Dp,
    insetBottom: Dp,
    onGoogle: () -> Unit,
    onGuest: () -> Unit,
    onLogIn: () -> Unit,
    onReadPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        DottedHero(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(top = insetTop),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "WELCOME TO WYRM",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 1.1.sp,
                color = Wyrm.Quiet,
            )
            Text(
                text = "The arena\nand people in it.",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 31.sp,
                lineHeight = 35.sp,
                letterSpacing = (-0.7).sp,
                color = Wyrm.Ink,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "A High Performance Engine — plus assist mode, team play, global chat and a live leaderboard.",
                fontFamily = Wyrm.Body,
                fontSize = 15.5.sp,
                lineHeight = 23.sp,
                color = Wyrm.Mute,
                modifier = Modifier.padding(top = 11.dp),
            )
            if (state.error.isNotEmpty()) {
                Text(
                    text = state.error,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Badge,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = insetBottom + 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoogleInkButton(
                label = if (state.busy) "Opening Google…" else "Continue with Google",
                enabled = !state.busy,
                onClick = onGoogle,
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Use email",
                    fontFamily = Wyrm.Body,
                    fontSize = 14.5.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .clickable(enabled = !state.busy, onClick = onLogIn)
                        .padding(8.dp),
                )
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .width(1.dp)
                        .height(14.dp)
                        .background(Wyrm.Rule),
                )
                Text(
                    text = "Play as guest",
                    fontFamily = Wyrm.Body,
                    fontSize = 14.5.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .clickable(enabled = !state.busy, onClick = onGuest)
                        .padding(8.dp),
                )
            }
            Text(
                text = "Same button for sign in and sign up — we match your Google account, or make one. Guest scores stay on this phone.",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = Wyrm.Quiet,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                text = "Privacy",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                color = Wyrm.Link,
                modifier = Modifier
                    .clickable(onClick = onReadPrivacy)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
internal fun GoogleInkButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(pressScale(pressed, enabled))
            .clip(shape)
            .background(if (enabled) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.4f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(wyrmRounded(7.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "G",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Wyrm.Ink,
            )
        }
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.5.sp,
            color = Wyrm.OnInk,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
    }
}

@Composable
internal fun GoogleOutlineButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(pressScale(pressed, enabled))
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Color(0x2937352F), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(wyrmRounded(7.dp))
                .background(Wyrm.Well),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "G",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Wyrm.Mute,
            )
        }
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Wyrm.Ink,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
    }
}

@Composable
internal fun DottedHero(modifier: Modifier = Modifier) {
    val dot = Color(0x1A37352F)
    Box(
        modifier = modifier.drawBehind {
            val step = 19.dp.toPx()
            var y = step / 2f
            while (y < size.height) {
                var x = step / 2f
                while (x < size.width) {
                    drawCircle(color = dot, radius = 1.2.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        WyrmMark(size = 108.dp, ink = Wyrm.Ink, unfilled = Color.Transparent)
    }
}
