package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.TeamMessage
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch

/**
 * Writing to the team from the arena (OM, 2026-10-04).
 *
 * The messages themselves are in the engine's chat window, drawn over the
 * arena like the minimap and played through while open (`android_team.c`).
 * Tapping its message box opens only this: one line above the keyboard, with
 * Send. The match carries on behind it; Send, the keyboard's Send or a tap
 * anywhere else closes it. The messages are the app's team chat, the same
 * list the Team page shows.
 */
@Composable
fun ArenaChatScreen(
    @Suppress("UNUSED_PARAMETER") messages: List<TeamMessage>,
    draft: String,
    sending: Boolean,
    @Suppress("UNUSED_PARAMETER") handoverSeconds: Int,
    @Suppress("UNUSED_PARAMETER") insetTop: Dp,
    insetBottom: Dp,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onHandoverChange: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    /* Up from the bottom with the keys, and back down into it after Send or a
       tap outside, before the layer goes away (OM, 2026-10-09: as on iOS). */
    val rise = remember { Animatable(1f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        rise.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
    }
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            keyboard?.hide()
            scope.launch {
                rise.animateTo(1f, tween(durationMillis = 220, easing = FastOutLinearInEasing))
                onClose()
            }
        }
    }
    val send = {
        if (draft.isNotBlank() && !sending) {
            onSend()
            leave()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = leave,
            ),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = keyboardRoom(insetBottom + 10.dp), start = 12.dp, end = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = rise.value * 160.dp.toPx()
                    alpha = 1f - rise.value
                }
                .clip(wyrmRounded(Wyrm.Pill))
                .background(Wyrm.Card.copy(alpha = 0.97f))
                .border(1.dp, Wyrm.Rule, wyrmRounded(Wyrm.Pill))
                // Taps on the bar stay on the bar.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) {
                    Text(
                        text = "Message the team",
                        fontFamily = Wyrm.Body,
                        fontSize = 15.sp,
                        color = Wyrm.Quiet,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 280) onDraftChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink),
                    cursorBrush = SolidColor(Wyrm.Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (sending) "SENDING" else "SEND",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp,
                color = Wyrm.OnInk,
                modifier = Modifier
                    .clip(wyrmRounded(Wyrm.Pill))
                    .background(if (draft.isBlank()) Wyrm.Ink.copy(alpha = 0.35f) else Wyrm.Ink)
                    .clickable(enabled = draft.isNotBlank() && !sending) { send() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
