package com.wyrm.omrajput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* Skin Studio's chrome (OM, 2026-10-13: in the app's own style): the colour
   picker, the action sheet, the centred dialog and the touch-and-hold
   captions. */

/**
 * The colour picker: the chosen colour as a swatch with its hex, a row of
 * swatches, then three labelled tracks — Hue, Strength (saturation) and
 * Brightness — each a slim pill of its own range under a knob that wears the
 * colour it gives.
 */
@Composable
internal fun StudioColorPicker(color: Int, onChange: (Int) -> Unit) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    fun emit(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) =
        onChange(android.graphics.Color.HSVToColor(0xFF, floatArrayOf(h, s, v)))
    val opaque = Color(color or (0xFF shl 24))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(wyrmRounded(20.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(opaque).border(2.dp, Wyrm.Rule, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("COLOUR", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 1.1.sp, color = Wyrm.Quiet)
                Text("#%06X".format(color and 0xFFFFFF), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StudioSwatches.forEach { swatch ->
                val selected = (swatch and 0xFFFFFF) == (color and 0xFFFFFF)
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(swatch) },
                )
            }
        }
        StudioColorTrack("Hue", "${hsv[0].roundToInt()}°", hsv[0] / 360f,
            (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }, Color.hsv(hsv[0], 1f, 1f)) { emit(h = (it * 360f).coerceIn(0f, 359.9f)) }
        StudioColorTrack("Strength", percent(hsv[1]), hsv[1],
            listOf(Color.hsv(hsv[0], 0f, hsv[2].coerceAtLeast(0.12f)), Color.hsv(hsv[0], 1f, hsv[2].coerceAtLeast(0.12f))), opaque) { emit(s = it) }
        StudioColorTrack("Brightness", percent(hsv[2]), hsv[2],
            listOf(Color.Black, Color.hsv(hsv[0], hsv[1], 1f)), opaque) { emit(v = it) }
    }
}

/** One colour track: its name and value above a slim pill; tap it, or slide sideways. */
@Composable
internal fun StudioColorTrack(
    title: String,
    value: String,
    fraction: Float,
    colors: List<Color>,
    knob: Color,
    onPick: (Float) -> Unit,
) {
    val density = LocalDensity.current
    var width by remember { mutableFloatStateOf(1f) }
    val knobSize = 26.dp
    val knobPx = with(density) { knobSize.toPx() }
    val run = (width - knobPx).coerceAtLeast(1f)
    val travel = remember { mutableFloatStateOf(0f) }
    val position = fraction.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f))
            Text(value, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Wyrm.Quiet)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(knobSize)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(width) { detectTapGestures { onPick(((it.x - knobPx / 2f) / run).coerceIn(0f, 1f)) } }
                // Sideways only, so a scroll that starts on a track still scrolls the page.
                .draggable(
                    state = rememberDraggableState { delta ->
                        travel.floatValue = (travel.floatValue + delta).coerceIn(0f, run)
                        onPick((travel.floatValue / run).coerceIn(0f, 1f))
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { travel.floatValue = position * run },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 9.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(WyrmCapsule)
                    .background(Brush.horizontalGradient(colors))
                    .border(1.dp, Wyrm.Rule, WyrmCapsule),
            )
            Box(
                Modifier
                    .offset { IntOffset((run * position).roundToInt(), 0) }
                    .size(knobSize)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(knob),
            )
        }
    }
}

/** A small quiet line: what a touch and hold does here. */
@Composable
internal fun StudioCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, fontFamily = Wyrm.Body, fontSize = 11.5.sp, lineHeight = 15.sp, color = Wyrm.Quiet,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 2.dp))
}

/** One action in a [StudioActionSheet]. */
internal class StudioAction(val title: String, val icon: Int, val destructive: Boolean = false, val onClick: () -> Unit)

/** The whole window, top-left: a sheet lays itself out inside. */
private object StudioFullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

/**
 * The Studio's action sheet: over the whole window, a paper card that rises
 * with a spring over a dimmed screen — a grabber, the title in Wyrm's display
 * face, one row per action with its icon in a round well (the destructive one
 * in red) — and Cancel apart, as a capsule.
 */
@Composable
internal fun StudioActionSheet(
    title: String,
    subtitle: String = "",
    actions: List<StudioAction>,
    insetBottom: Dp,
    onDismiss: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { appear.animateTo(1f, iosSpring(0.42f, 0.84f)) }
    fun close(then: () -> Unit = {}) {
        scope.launch {
            appear.animateTo(0f, iosSpring(0.26f, 1f))
            onDismiss()
            then()
        }
    }
    Popup(popupPositionProvider = StudioFullWindow, onDismissRequest = { close() }, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * appear.value))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = insetBottom + 12.dp)
                    .graphicsLayer {
                        translationY = (1f - appear.value) * size.height * 1.15f
                        alpha = 0.3f + 0.7f * appear.value
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(18.dp, wyrmRounded(30.dp))
                        .clip(wyrmRounded(30.dp))
                        .background(Wyrm.Paper)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(30.dp))
                        .padding(bottom = 8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(40.dp).height(5.dp).clip(WyrmCapsule).background(Wyrm.Rule))
                    }
                    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 10.dp)) {
                        Text(title, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Wyrm.Ink, maxLines = 1)
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    actions.forEach { action ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val tint = if (action.destructive) Wyrm.Badge else Wyrm.Ink
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .scale(pressScale(pressed))
                                .clickable(interactionSource = interaction, indication = null) { close(action.onClick) }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (action.destructive) Wyrm.Badge.copy(alpha = 0.12f) else Wyrm.Card)
                                    .border(1.dp, Wyrm.Rule, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(painterResource(action.icon), null, tint = tint, modifier = Modifier.size(19.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Text(action.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = tint)
                        }
                    }
                }
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .scale(pressScale(pressed))
                        .clip(WyrmCapsule)
                        .background(Wyrm.Paper)
                        .border(1.dp, Wyrm.Rule, WyrmCapsule)
                        .clickable(interactionSource = interaction, indication = null) { close() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Wyrm.Ink)
                }
            }
        }
    }
}

/**
 * A centred dialog: in the middle of the page and lifted above the keyboard
 * when one is up (imePadding), over a dimmed page. Placed last inside a
 * full-size Box.
 */
@Composable
internal fun StudioDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, iosSpring(0.4f, 0.82f)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f * appear.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.92f + 0.08f * appear.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                }
                .shadow(20.dp, wyrmRounded(28.dp))
                .clip(wyrmRounded(28.dp))
                .background(Wyrm.Paper)
                .border(1.dp, Wyrm.Rule, wyrmRounded(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Wyrm.Ink)
            content()
        }
    }
}
