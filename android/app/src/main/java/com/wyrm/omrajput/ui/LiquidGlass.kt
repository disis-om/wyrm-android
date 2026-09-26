package com.wyrm.omrajput.ui

import android.os.Build
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.clickable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.wyrm.omrajput.ui.glass.DampedDragAnimation
import com.wyrm.omrajput.ui.glass.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Liquid Glass, for Compose.
 *
 * Wyrm iOS gets its switches, sliders, segmented pills and tab bar from the
 * system, and on iOS 26 the system draws them in Liquid Glass: a thumb that
 * lifts into a clear lens while a finger holds it, bends whatever is under
 * it, and lands again with a small overshoot. Android has no such thing, so
 * this file builds it — the lens is a real refraction of what sits behind
 * (Kyant's backdrop, a RuntimeShader on Android 13 and up), not a white
 * blob with a gradient on it.
 *
 * Sizes and springs are the iOS ones: SwiftUI's spring(response:damping:)
 * converts to stiffness (2π / response)² at the same damping ratio, so a
 * number read off the Swift source means the same motion here.
 */

/** What the page behind floating glass looks like; root screens record into it. */
val LocalPageBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/** Height the floating tab bar takes out of a root page's scroll content. */
val LocalRootTabClearance = staticCompositionLocalOf { 0.dp }

/** iOS draws every rounded corner with continuous curvature. */
val WyrmCapsule: Shape = Capsule()
fun wyrmRounded(radius: Dp): Shape = RoundedRectangle(radius)

/** SwiftUI `.spring(response:dampingFraction:)`. */
internal fun <T> iosSpring(response: Float, damping: Float, threshold: T? = null): SpringSpec<T> {
    val omega = 2f * PI.toFloat() / response
    return spring(dampingRatio = damping, stiffness = omega * omega, visibilityThreshold = threshold)
}

/** SwiftUI `.interpolatingSpring(stiffness:damping:)` at unit mass. */
internal fun <T> iosInterpolatingSpring(stiffness: Float, damping: Float): SpringSpec<T> =
    spring(dampingRatio = damping / (2f * kotlin.math.sqrt(stiffness)), stiffness = stiffness)

/** The system fills iOS paints unselected controls with. */
internal object IosFill {
    val switchOff: Color get() = if (Wyrm.currentPalette.dark) Color(0x52787880) else Color(0x29787880)
    val segmentTrack: Color get() = if (Wyrm.currentPalette.dark) Color(0x3D767680) else Color(0x1F767680)
    val segmentThumb: Color get() = if (Wyrm.currentPalette.dark) Color(0xFF636366) else Color.White
    val sliderTrack: Color get() = if (Wyrm.currentPalette.dark) Color(0x52787880) else Color(0x33787880)
}

private val glassAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * A clear pane of glass over [backdrop]: a touch of blur, lens refraction at
 * the rim, a specular edge and a whisper of [tint]. Below Android 12 there is
 * no backdrop effect at all, so the pane falls back to frosted paper.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape = WyrmCapsule,
    tint: Color = Color.Transparent,
    blur: Dp = 1.5.dp,
    refraction: Dp = 16.dp,
    depth: Dp = 26.dp,
    shadow: Shadow? = Shadow(radius = 14.dp, offset = DpOffset(0.dp, 6.dp), color = Color.Black.copy(alpha = 0.07f)),
    fallback: Color = Wyrm.TabBar,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        if (blur > 0.dp) blur(blur.toPx())
        lens(refraction.toPx(), depth.toPx(), chromaticAberration = true)
    },
    highlight = { Highlight.Default },
    shadow = { shadow },
    onDrawSurface = {
        if (!glassAvailable) drawRect(fallback)
        if (tint.alpha > 0f) drawRect(tint)
    },
)

/* ------------------------------------------------------------------ switch */

/**
 * The iOS 26 switch: 63 × 28 track, a 37 × 24 capsule thumb. Press and it
 * swells into a clear lens over the track; drag it or let go and it springs
 * across and lands. Tinted with the theme's live colour, as Wyrm iOS tints
 * its system toggles.
 */
@Composable
fun LiquidSwitch(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Wyrm.Live,
    surface: Color = Wyrm.Card,
) {
    val trackW = 63.dp
    val trackH = 28.dp
    val thumbW = 37.dp
    val thumbH = 24.dp
    val inset = 2.dp
    val density = LocalDensity.current
    val travelPx = with(density) { (trackW - thumbW - inset * 2).toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val fraction = remember { Animatable(if (on) 1f else 0f) }
    val lift = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(on)
    val toggle by rememberUpdatedState(onToggle)

    LaunchedEffect(on) {
        if (!holding) fraction.animateTo(if (on) 1f else 0f, iosSpring(0.34f, 0.68f))
    }

    val trackBackdrop = rememberLayerBackdrop()
    val offTrack = IosFill.switchOff
    Box(
        modifier = modifier
            .size(trackW, trackH)
            .semantics {
                role = Role.Switch
                stateDescription = if (on) "On" else "Off"
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    holding = true
                    scope.launch { lift.animateTo(1f, iosSpring(0.3f, 0.48f)) }
                    val start = fraction.value
                    var dx = 0f
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dx += change.positionChange().x
                        if (!dragged && abs(dx) > slop) dragged = true
                        if (dragged) {
                            change.consume()
                            val next = (start + dx / travelPx).coerceIn(0f, 1f)
                            if ((next > 0.5f) != (fraction.value > 0.5f)) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            scope.launch { fraction.snapTo(next) }
                        }
                    }
                    val target = if (dragged) fraction.value > 0.5f else !current
                    haptics.performHapticFeedback(if (target) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    scope.launch { fraction.animateTo(if (target) 1f else 0f, iosSpring(0.34f, 0.62f)) }
                    scope.launch {
                        delay(if (dragged) 30 else 160)
                        lift.animateTo(0f, iosInterpolatingSpring(240f, 12f))
                        holding = false
                    }
                    if (target != current) toggle(target)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The lens refracts this: the track on the row's own card colour, a
        // little larger than the track so the swollen thumb has something to bend.
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(trackW + 20.dp, trackH + 20.dp)
                .layerBackdrop(trackBackdrop)
                .drawBehind {
                    drawRect(surface)
                    val pad = 10.dp.toPx()
                    val colour = lerp(offTrack, tint, fraction.value)
                    drawRoundRect(
                        color = if (enabled) colour else colour.copy(alpha = colour.alpha * 0.5f),
                        topLeft = Offset(pad, pad),
                        size = Size(size.width - pad * 2, size.height - pad * 2),
                        cornerRadius = CornerRadius((size.height - pad * 2) / 2f),
                    )
                },
        )
        GlassThumb(
            backdrop = trackBackdrop,
            lift = { lift.value },
            scaleX = 1.42f,
            scaleY = 1.5f,
            restColor = Color.White,
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset((inset.toPx() + travelPx * fraction.value).roundToInt(), 0) }
                .size(thumbW, thumbH),
        )
    }
}

/**
 * A thumb that is opaque at rest and a lens when lifted. One node does both:
 * the surface is painted white and fades as [lift] rises, uncovering the
 * refracted backdrop beneath, while the rim light and inner shade come in.
 */
@Composable
private fun GlassThumb(
    backdrop: Backdrop,
    lift: () -> Float,
    scaleX: Float,
    scaleY: Float,
    restColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = WyrmCapsule,
    lens: Dp = 9.dp,
    depth: Dp = 16.dp,
) {
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                lens(lens.toPx(), depth.toPx(), chromaticAberration = true)
            },
            highlight = {
                val l = lift()
                if (l <= 0.01f) null else Highlight.Default.copy(alpha = l)
            },
            shadow = {
                val l = lift()
                Shadow(
                    radius = lerp(4f, 12f, l).dp,
                    offset = DpOffset(0.dp, lerp(2.5f, 5f, l).dp),
                    color = Color.Black.copy(alpha = lerp(0.16f, 0.12f, l)),
                )
            },
            innerShadow = {
                val l = lift()
                if (l <= 0.01f) null else InnerShadow(radius = 6.dp, alpha = 0.55f * l)
            },
            layerBlock = {
                val l = lift()
                this.scaleX = lerp(1f, scaleX, l)
                this.scaleY = lerp(1f, scaleY, l)
            },
            onDrawSurface = {
                val l = lift()
                drawRect(restColor.copy(alpha = restColor.alpha * (1f - l)))
                if (l > 0f) drawRect(Color.White.copy(alpha = 0.06f * l))
            },
        ),
    )
}

/* ------------------------------------------------------------------ slider */

/**
 * The iOS 26 slider: a 6 dp track filled in [active] up to a 38 × 24 capsule
 * thumb. Touch the thumb and it lifts into a lens and follows the finger
 * relative to where it was caught; touch the track and the thumb comes to the
 * finger first. [steps] follows Material's meaning — the stops between ends.
 */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    active: Color = Wyrm.Ink,
    surface: Color = Wyrm.Card,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val thumbW = 38.dp
    val thumbH = 24.dp
    val trackH = 6.dp
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val shownFraction = if (dragFraction >= 0f) dragFraction else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val animatedFraction = remember { Animatable(shownFraction) }
    LaunchedEffect(shownFraction) {
        if (dragFraction >= 0f) animatedFraction.snapTo(shownFraction)
        else animatedFraction.animateTo(shownFraction, iosSpring(0.3f, 0.8f))
    }

    fun snap(f: Float): Float {
        if (steps <= 0) return f.coerceIn(0f, 1f)
        val segments = steps + 1
        return ((f * segments).roundToInt().toFloat() / segments).coerceIn(0f, 1f)
    }

    val trackBackdrop = rememberLayerBackdrop()
    val trackFill = IosFill.sliderTrack
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .semantics { role = Role.Button }
            .pointerInput(enabled, steps, valueRange) {
                if (!enabled) return@pointerInput
                val thumbPx = thumbW.toPx()
                val run = (size.width - thumbPx).coerceAtLeast(1f)
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val current = animatedFraction.value
                    val thumbCentre = thumbPx / 2f + run * current
                    val onThumb = abs(down.position.x - thumbCentre) <= thumbPx / 2f + 14.dp.toPx()
                    var start = current
                    if (!onThumb) start = ((down.position.x - thumbPx / 2f) / run).coerceIn(0f, 1f)
                    var lastStop = snap(start)
                    dragFraction = start
                    change(valueRange.start + snap(start) * span)
                    scope.launch { lift.animateTo(1f, iosSpring(0.3f, 0.5f)) }
                    var dx = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        dx += pointer.positionChange().x
                        if (abs(dx) > slop / 3f) pointer.consume()
                        val raw = (start + dx / run).coerceIn(0f, 1f)
                        val stop = snap(raw)
                        dragFraction = if (steps > 0) stop else raw
                        if (stop != lastStop) {
                            if (steps > 0 || stop == 0f || stop == 1f) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            lastStop = stop
                        }
                        change(valueRange.start + stop * span)
                    }
                    dragFraction = -1f
                    finished?.invoke()
                    scope.launch { lift.animateTo(0f, iosInterpolatingSpring(260f, 13f)) }
                }
            },
    ) {
        val run = maxWidth - thumbW
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(maxWidth + 24.dp, 34.dp + 24.dp)
                .layerBackdrop(trackBackdrop)
                .drawBehind {
                    drawRect(surface)
                    val pad = 12.dp.toPx()
                    val th = trackH.toPx()
                    val top = (size.height - th) / 2f
                    val width = size.width - pad * 2
                    val radius = CornerRadius(th / 2f)
                    drawRoundRect(trackFill, Offset(pad, top), Size(width, th), radius)
                    val thumbPx = thumbW.toPx()
                    val filled = thumbPx / 2f + (width - thumbPx) * animatedFraction.value
                    drawRoundRect(
                        if (enabled) active else active.copy(alpha = 0.35f),
                        Offset(pad, top),
                        Size(filled.coerceAtLeast(th), th),
                        radius,
                    )
                },
        )
        GlassThumb(
            backdrop = trackBackdrop,
            lift = { lift.value },
            scaleX = 1.5f,
            scaleY = 1.55f,
            restColor = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { androidx.compose.ui.unit.IntOffset((run.toPx() * animatedFraction.value).roundToInt(), 0) }
                .size(thumbW, thumbH),
        )
    }
}

/* --------------------------------------------------------------- segmented */

/**
 * The iOS 26 segmented control: a capsule well with a capsule thumb. Hold
 * the thumb and it lifts into a lens you can drag across the segments; tap a
 * segment and it lifts, travels and lands. Type is Manrope, medium at rest
 * and bold when chosen, as Wyrm iOS sets it through UIAppearance.
 */
@Composable
fun LiquidSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 34.dp,
    fontSize: TextUnit = 13.sp,
    /** Quiet text after each label, e.g. a count. */
    counts: List<String>? = null,
    /** A live position (0..n-1) to follow, e.g. a pager's scroll. */
    follow: Float? = null,
) {
    if (options.isEmpty()) return
    val safe = selected.coerceIn(options.indices)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    val position = remember { Animatable(safe.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val pick by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(safe)
    LaunchedEffect(safe) {
        if (!dragging && follow == null) position.animateTo(safe.toFloat(), iosSpring(0.36f, 0.72f))
    }
    LaunchedEffect(follow) {
        if (follow != null && !dragging) position.snapTo(follow.coerceIn(0f, options.size - 1f))
    }
    val inset = 2.dp
    val contentBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .pointerInput(options.size) {
                val insetPx = inset.toPx()
                val segment = (size.width - insetPx * 2) / options.size
                fun indexAt(x: Float) = ((x - insetPx) / segment).toInt().coerceIn(0, options.size - 1)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startIndex = indexAt(down.position.x)
                    val onThumb = startIndex == current
                    dragging = true
                    scope.launch { lift.animateTo(1f, iosSpring(0.3f, 0.5f)) }
                    var lastPreview = startIndex
                    var moved = false
                    val downX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        val x = pointer.position.x
                        if (abs(x - downX) > viewConfiguration.touchSlop) moved = true
                        if (onThumb && moved) {
                            pointer.consume()
                            val continuous = ((x - insetPx) / segment - 0.5f).coerceIn(0f, options.size - 1f)
                            scope.launch { position.animateTo(continuous, iosSpring(0.22f, 0.62f)) }
                            val preview = indexAt(x)
                            if (preview != lastPreview) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                lastPreview = preview
                            }
                        }
                    }
                    val target = if (onThumb && moved) lastPreview else startIndex
                    if (target != current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch { position.animateTo(target.toFloat(), iosSpring(0.34f, 0.7f)) }
                    scope.launch {
                        delay(if (moved) 40 else 200)
                        lift.animateTo(0f, iosInterpolatingSpring(240f, 11f))
                        dragging = false
                    }
                    if (target != current) pick(target)
                }
            },
    ) {
        val segmentWidth = (maxWidth - inset * 2) / options.size
        val track = IosFill.segmentTrack
        val thumb = IosFill.segmentThumb
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(contentBackdrop)
                .clip(WyrmCapsule)
                .background(Wyrm.Card)
                .background(track),
        ) {
            // At rest the thumb is a plain raised capsule beneath the words.
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(((inset + segmentWidth * position.value).toPx()).roundToInt(), inset.roundToPx()) }
                    .size(segmentWidth, height - inset * 2)
                    .graphicsLayer { alpha = 1f - lift.value }
                    .drawBehind {
                        val r = CornerRadius(size.height / 2f)
                        drawRoundRect(Color.Black.copy(alpha = 0.06f), Offset(0f, 1.dp.toPx()), size, r)
                        drawRoundRect(thumb, cornerRadius = r)
                    },
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = inset)) {
                options.forEachIndexed { index, label ->
                    val chosen = index == safe
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                fontFamily = Wyrm.Body,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                fontSize = fontSize,
                                color = if (chosen) Wyrm.Ink else Wyrm.Mute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            counts?.getOrNull(index)?.let { count ->
                                Text(
                                    text = "  $count",
                                    fontFamily = Wyrm.Body,
                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = fontSize,
                                    color = Wyrm.Quiet,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Held: the thumb is a lens above the words, so they swell through it.
        // Always composed and faded by the lift, so its glass layer already
        // exists when a finger lands: creating it on the press was the pop.
        GlassThumb(
            backdrop = contentBackdrop,
            lift = { lift.value },
            scaleX = 1.12f,
            scaleY = 1.32f,
            restColor = thumb.copy(alpha = 0f),
            lens = 8.dp,
            depth = 14.dp,
            modifier = Modifier
                .graphicsLayer { alpha = lift.value }
                .offset { androidx.compose.ui.unit.IntOffset(((inset + segmentWidth * position.value).toPx()).roundToInt(), inset.roundToPx()) }
                .size(segmentWidth, height - inset * 2),
        )
    }
}

/* ---------------------------------------------------------------- tab bar */

/**
 * The iOS 26 tab bar, built the way the backdrop library's own reference
 * `LiquidBottomTabs` builds it (Kyant0/AndroidLiquidGlass 1.0.6, Apache 2.0).
 *
 * Three layers, all drawn from the first frame so nothing pops in on a press:
 * the bar (refracting the page), an ink-tinted copy of the tabs recorded as a
 * backdrop, and the selection pill. At rest the pill is a quiet tinted thumb;
 * a press or a drag raises it into a lens over the tinted copy, which is how
 * the icon under the finger turns ink and swells through the glass. The pill
 * follows the finger with damped physics, squashes with speed, the bar swells
 * and shifts a little with the drag, and a touch light follows the finger.
 */
@Composable
fun LiquidTabBar(
    tabs: List<LiquidTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onExpand: () -> Unit = {},
) {
    // iOS 26 `.tabBarMinimizeBehavior(.onScrollDown)`: the bar folds towards
    // the leading edge into a glass circle carrying the chosen tab, and a tap
    // on that circle (or scrolling back up) unfolds it.
    val fold = remember { Animatable(if (collapsed) 1f else 0f) }
    LaunchedEffect(collapsed) { fold.animateTo(if (collapsed) 1f else 0f, iosSpring(0.42f, 0.78f)) }
    val pageBackdrop = LocalPageBackdrop.current
    Box(modifier.fillMaxWidth()) {
        if (fold.value < 0.999f) {
            ExpandedLiquidTabBar(
                tabs = tabs,
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.graphicsLayer {
                    val f = fold.value
                    alpha = 1f - f
                    val s = lerp(1f, 0.82f, f)
                    scaleX = s
                    scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
            )
        }
        if (fold.value > 0.001f) {
            Box(
                Modifier
                    .padding(start = 18.dp)
                    .align(Alignment.CenterStart)
                    .size(56.dp)
                    .graphicsLayer {
                        val f = fold.value
                        alpha = f
                        val s = lerp(0.6f, 1f, f)
                        scaleX = s
                        scaleY = s
                    }
                    .liquidGlass(backdrop = pageBackdrop, shape = WyrmCapsule, tint = Wyrm.Paper.copy(alpha = 0.4f))
                    .clip(WyrmCapsule)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onExpand() },
                contentAlignment = Alignment.Center,
            ) { tabs.getOrNull(selected)?.content?.invoke(true) }
        }
    }
}

@Composable
private fun ExpandedLiquidTabBar(
    tabs: List<LiquidTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = tabs.size
    val haptics = LocalHapticFeedback.current
    val pageBackdrop = LocalPageBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val pick by rememberUpdatedState(onSelect)
    // Read fresh each time: the effect below outlives any one value of it, and
    // comparing against the value the bar was first composed with is how a
    // tap on Settings could move the pill without ever opening Settings.
    val chosen by rememberUpdatedState(selected)
    val container = Wyrm.Paper.copy(alpha = 0.4f)
    val restingThumb = Wyrm.Ink.copy(alpha = if (Wyrm.currentPalette.dark) 0.16f else 0.1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val barWidth = constraints.maxWidth.toFloat()
        val tabWidth = with(density) { (barWidth - 8.dp.toPx()) / count }
        val scope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, barWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / barWidth).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * kotlin.math.sign(fraction) * EaseOut.transform(abs(fraction)) }
            }
        }
        var currentIndex by remember { mutableIntStateOf(selected) }
        val drag = remember(scope, count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selected.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.roundToInt().coerceIn(0, count - 1)
                    currentIndex = target
                    animateToValue(target.toFloat())
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, amount ->
                    val before = targetValue.roundToInt()
                    updateValue((targetValue + amount.x / tabWidth).coerceIn(0f, (count - 1).toFloat()))
                    if (targetValue.roundToInt() != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                },
            )
        }
        // The page decides the tab; a finger only proposes one.
        LaunchedEffect(selected) { if (currentIndex != selected) currentIndex = selected }
        LaunchedEffect(drag) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    drag.animateToValue(index.toFloat())
                    if (index != chosen) pick(index)
                }
        }
        val highlight = remember(scope) {
            InteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    Offset((drag.value + 0.5f) * tabWidth + panelOffset, size.height / 2f)
                },
            )
        }
        // Icons grow with the bar and never more than it.
        fun barScale(width: Float): Float = lerp(1f, 1f + with(density) { 16.dp.toPx() } / width, drag.pressProgress)
        fun tabRow(tinted: Boolean): @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            tabs.forEachIndexed { index, tab ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(WyrmCapsule)
                        .then(
                            if (tinted) Modifier else Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                            ) {
                                if (index != currentIndex) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                currentIndex = index
                            },
                        ),
                    contentAlignment = Alignment.Center,
                    // The copy under the lens draws every tab as the selected
                    // one, in the theme's own ink, so the pill shows that ink
                    // and a badge keeps its colour instead of turning black.
                ) { tab.content(tinted || index == selected) }
            }
        }

        // The bar: the page refracted through clear glass, swelling on press.
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = pageBackdrop,
                    shape = { WyrmCapsule },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val s = lerp(1f, 1f + 16.dp.toPx() / size.width, drag.pressProgress)
                        scaleX = s
                        scaleY = s
                    },
                    shadow = { Shadow(radius = 14.dp, offset = DpOffset(0.dp, 6.dp), color = Color.Black.copy(alpha = 0.07f)) },
                    onDrawSurface = {
                        if (!glassAvailable) drawRect(Wyrm.TabBar)
                        drawRect(container)
                    },
                )
                .then(highlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = tabRow(tinted = false),
        )

        // An ink copy of the tabs, never shown directly: the pill's lens reads
        // it, so the tab under the glass is always drawn in the selected ink.
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = pageBackdrop,
                    shape = { WyrmCapsule },
                    effects = {
                        val p = drag.pressProgress
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx() * p, 24.dp.toPx() * p)
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier)
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    val s = barScale(size.width)
                    scaleX = s
                    scaleY = s
                },
            verticalAlignment = Alignment.CenterVertically,
            content = tabRow(tinted = true),
        )

        // The selection pill: a thumb at rest, a lens while held or dragged.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer { translationX = drag.value * tabWidth + panelOffset }
                .then(highlight.gestureModifier)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(pageBackdrop, tabsBackdrop),
                    shape = { WyrmCapsule },
                    effects = {
                        val p = drag.pressProgress
                        lens(10.dp.toPx() * p, 14.dp.toPx() * p, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    shadow = { Shadow(alpha = drag.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * drag.pressProgress, alpha = drag.pressProgress) },
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val v = drag.velocity / 10f
                        scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val p = drag.pressProgress
                        drawRect(restingThumb, alpha = 1f - p)
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / count),
        )
    }
}

/** One tab: its own drawing, told whether it is the chosen one. */
class LiquidTab(val content: @Composable (selected: Boolean) -> Unit)
