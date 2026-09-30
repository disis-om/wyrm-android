package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR

/*
 * Log out (OM, 2026-10-01): the row sits at the very bottom of Settings, and
 * asks once, the way iOS and most apps do — a sheet naming the account, one
 * red action and Cancel. What happens next is said plainly: the settings go to
 * the account first, then the phone forgets everything.
 */

/** The question: who is leaving, what is kept, one red action, Cancel. */
@Composable
internal fun LogOutSheet(
    visible: Boolean,
    name: String,
    handle: String,
    avatarUrl: String,
    avatarKey: String,
    insetBottom: Dp,
    onLogOut: () -> Unit,
    onCancel: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(iosSpring(0.42f, 0.86f)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = insetBottom + 10.dp)
                    .clip(wyrmRounded(30.dp))
                    .background(Wyrm.Card)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(38.dp).height(5.dp).clip(wyrmRounded(3.dp)).background(Wyrm.Rule))
                Spacer(Modifier.height(18.dp))
                WyrmAvatar(url = avatarUrl, avatarKey = avatarKey, initial = name.take(1).uppercase(), size = 64.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Log out of Wyrm?",
                    fontFamily = Wyrm.Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Wyrm.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (handle.isBlank()) name else "$name · $handle",
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Wyrm.Quiet,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(wyrmRounded(16.dp)).background(Wyrm.Well).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(painterResource(LucideR.drawable.lucide_ic_cloud_upload), null, tint = Wyrm.Ink, modifier = Modifier.size(19.dp))
                    Text(
                        "Your settings, skin and layouts are saved to your account first. " +
                            "Then this phone forgets everything, and it all comes back when you log in again.",
                        fontFamily = Wyrm.Body, fontSize = 13.5.sp, lineHeight = 19.sp, color = Wyrm.Mute,
                    )
                }
                Spacer(Modifier.height(18.dp))
                SheetButton("Log out", filled = Wyrm.Badge, text = Color.White, icon = LucideR.drawable.lucide_ic_log_out, onClick = onLogOut)
                Spacer(Modifier.height(8.dp))
                SheetButton("Cancel", filled = Wyrm.Well, text = Wyrm.Ink, onClick = onCancel)
            }
        }
    }
}

/**
 * The account could not take the settings (offline, server down). Said
 * plainly under the W, with the three honest choices.
 */
@Composable
internal fun LogOutSaveFailed(
    message: String,
    onRetry: () -> Unit,
    onLogOutAnyway: () -> Unit,
    onStay: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Wyrm.Paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WyrmBrandMark(size = 84.dp)
            Spacer(Modifier.height(22.dp))
            Icon(painterResource(LucideR.drawable.lucide_ic_cloud_off), null, tint = Wyrm.Badge, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Your settings could not be saved",
                fontFamily = Wyrm.Display, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Wyrm.Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                fontFamily = Wyrm.Body, fontSize = 14.sp, lineHeight = 20.sp, color = Wyrm.Mute, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            SheetButton("Try again", filled = Wyrm.Ink, text = Wyrm.OnInk, icon = LucideR.drawable.lucide_ic_refresh_cw, onClick = onRetry)
            Spacer(Modifier.height(8.dp))
            SheetButton("Log out anyway", filled = Wyrm.Well, text = Wyrm.Badge, onClick = onLogOutAnyway)
            Spacer(Modifier.height(8.dp))
            SheetButton("Stay logged in", filled = Color.Transparent, text = Wyrm.Mute, onClick = onStay)
        }
    }
}

@Composable
private fun SheetButton(label: String, filled: Color, text: Color, icon: Int? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(25.dp))
            .background(filled)
            .clickable(interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = text, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = text)
    }
}
