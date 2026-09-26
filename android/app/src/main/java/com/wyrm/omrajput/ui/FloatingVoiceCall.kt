package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.voice.VoiceCallState
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.roundToInt

/** A draggable call surface that gets out of the app's way without hiding the call. */
@Composable
fun FloatingVoiceCall(
    state: VoiceCallState,
    visible: Boolean,
    onOpen: () -> Unit,
    onMute: () -> Unit,
    onSound: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(state.roomId) { mutableStateOf(true) }
    var position by remember(state.roomId) { mutableStateOf<Offset?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val density = LocalDensity.current
    val expandedSize = 154.dp
    val collapsedSize = 54.dp
    val surfaceSize by animateDpAsState(
        if (expanded) expandedSize else collapsedSize,
        spring(dampingRatio = .78f, stiffness = 430f),
        label = "call-surface-size",
    )
    val corner by animateDpAsState(
        if (expanded) Wyrm.CornerLarge else 27.dp,
        spring(dampingRatio = .8f, stiffness = 500f),
        label = "call-surface-corner",
    )
    val started = remember(state.callStartedAt) {
        runCatching { Instant.parse(state.callStartedAt).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }

    LaunchedEffect(state.callStartedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (!visible) return@BoxWithConstraints
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val sizePx = with(density) { surfaceSize.toPx() }
        val gutterPx = with(density) { 12.dp.toPx() }
        val current = position ?: Offset(
            (widthPx - with(density) { expandedSize.toPx() } - gutterPx).coerceAtLeast(gutterPx),
            gutterPx,
        ).also { position = it }
        val clamped = Offset(
            current.x.coerceIn(gutterPx, (widthPx - sizePx - gutterPx).coerceAtLeast(gutterPx)),
            current.y.coerceIn(gutterPx, (heightPx - sizePx - gutterPx).coerceAtLeast(gutterPx)),
        )
        if (clamped != current) position = clamped

        // Declared before the surface so the surface is hit first and keeps its
        // own presses. Only a press that missed the surface reaches this, and
        // this one shares, so the app underneath still receives it.
        if (expanded) {
            Box(Modifier.fillMaxSize().observeOutsidePress { expanded = false })
        }

        WyrmGlass(
            Modifier
                .offset { IntOffset(clamped.x.roundToInt(), clamped.y.roundToInt()) }
                .size(surfaceSize)
                .pointerInput(expanded, widthPx, heightPx) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        val activeSize = with(density) { surfaceSize.toPx() }
                        position = Offset(
                            ((position ?: clamped).x + amount.x).coerceIn(
                                gutterPx,
                                (widthPx - activeSize - gutterPx).coerceAtLeast(gutterPx),
                            ),
                            ((position ?: clamped).y + amount.y).coerceIn(
                                gutterPx,
                                (heightPx - activeSize - gutterPx).coerceAtLeast(gutterPx),
                            ),
                        )
                    }
                }
                .clip(wyrmRounded(corner))
                .clickable(enabled = !expanded) { expanded = true },
            corner = corner,
            lift = 1.55f,
        ) {
            AnimatedContent(
                targetState = expanded,
                modifier = Modifier.align(Alignment.Center),
                transitionSpec = {
                    (fadeIn(spring(stiffness = 520f)) togetherWith fadeOut(spring(stiffness = 620f)))
                        .using(SizeTransform(clip = false))
                },
                label = "call-surface-morph",
            ) { open ->
                if (open) {
                    ExpandedCall(
                        state = state,
                        duration = voiceDuration((now - started).coerceAtLeast(0L) / 1000L),
                        onOpen = onOpen,
                        onMute = onMute,
                        onSound = onSound,
                        onLeave = onLeave,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(LucideR.drawable.lucide_ic_phone_call),
                            "Open call controls",
                            Modifier.size(23.dp),
                            tint = Wyrm.Green,
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(9.dp).size(7.dp)
                                .clip(CircleShape).background(Wyrm.Green),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedCall(
    state: VoiceCallState,
    duration: String,
    onOpen: () -> Unit,
    onMute: () -> Unit,
    onSound: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.clickable(onClick = onOpen), horizontalAlignment = Alignment.CenterHorizontally) {
            WyrmLabel("VOICE CONNECTED", color = Wyrm.Green)
            Spacer(Modifier.height(3.dp))
            Text(
                state.roomName,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Wyrm.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(duration, fontFamily = Wyrm.Display, fontSize = 21.sp, color = Wyrm.SoftWhite)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareCallButton(
                if (state.muted || state.interrupted) LucideR.drawable.lucide_ic_mic_off else LucideR.drawable.lucide_ic_mic,
                if (state.muted || state.interrupted) Wyrm.Blood else Wyrm.Green,
                onMute,
            )
            SquareCallButton(
                if (state.deafened) LucideR.drawable.lucide_ic_volume_x else LucideR.drawable.lucide_ic_volume_2,
                if (state.deafened) Wyrm.Blood else Wyrm.White,
                onSound,
            )
            SquareCallButton(LucideR.drawable.lucide_ic_phone_off, Wyrm.Blood, onLeave)
        }
    }
}

@Composable
private fun SquareCallButton(icon: Int, tint: Color, onClick: () -> Unit) {
    // Same recipe as the call page's controls, one size down, so the bubble
    // reads as the call screen shrunk rather than as a different component.
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .background(tint.copy(alpha = .10f))
            .border(1.dp, tint.copy(alpha = .32f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = tint)
    }
}

/**
 * Reports a press without taking it away from anything underneath.
 *
 * A plain `pointerInput` cannot do this. Pointer input nodes do not share the
 * hit path with siblings by default, so a full-screen one wins every touch in
 * its `Box` and silently starves every sibling below it — even when it consumes
 * nothing and only listens on the Initial pass. That is how an active call once
 * left the voice page rendering live participant updates while none of its
 * buttons, and none of Home's rows, would respond to a tap: recomposition was
 * healthy and only pointer dispatch was being swallowed.
 *
 * Sharing makes the node an observer instead of an owner, so it must be layered
 * *under* anything whose presses it should not see. Ordering is the filter here;
 * there is no bounds test.
 */
private fun Modifier.observeOutsidePress(onOutsidePress: () -> Unit): Modifier =
    this then OutsidePressElement(onOutsidePress)

private data class OutsidePressElement(
    val onOutsidePress: () -> Unit,
) : ModifierNodeElement<OutsidePressNode>() {
    override fun create() = OutsidePressNode(onOutsidePress)

    override fun update(node: OutsidePressNode) {
        node.onOutsidePress = onOutsidePress
    }
}

private class OutsidePressNode(
    var onOutsidePress: () -> Unit,
) : Modifier.Node(), PointerInputModifierNode {

    /** The whole point: stay in the hit path without owning it. */
    override fun sharePointerInputWithSiblings() = true

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        if (pointerEvent.changes.any { it.pressed && !it.previousPressed }) onOutsidePress()
    }

    override fun onCancelPointerInput() = Unit
}

private fun voiceDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%02d:%02d".format(minutes, remainder)
}
