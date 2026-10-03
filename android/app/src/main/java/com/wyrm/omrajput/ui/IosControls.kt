package com.wyrm.omrajput.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Small controls shared by several screens (and by Wyrm Desktop).

/** UIActivityIndicatorView: eight spokes, the lit one stepping round. */
@Composable
fun IosSpinner(size: Dp = 20.dp, colour: Color = Wyrm.Quiet) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "spinner-step",
    )
    Canvas(Modifier.size(size)) {
        val head = turn.toInt() % 8
        val radius = this.size.minDimension / 2f
        val stroke = radius * 0.2f
        for (i in 0 until 8) {
            val age = (head - i + 8) % 8
            val alpha = 1f - age / 8f * 0.78f
            rotate(i * 45f) {
                drawLine(
                    color = colour.copy(alpha = colour.alpha * alpha),
                    start = Offset(center.x, center.y - radius * 0.46f),
                    end = Offset(center.x, center.y - radius + stroke / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun GradientTrack(fraction: Float, brush: Brush, onPick: (Float) -> Unit) {
    val density = LocalDensity.current
    var width by remember { mutableFloatStateOf(1f) }
    val knob = 26.dp
    val knobPx = with(density) { knob.toPx() }
    val run = (width - knobPx).coerceAtLeast(1f)
    val travel = remember { mutableFloatStateOf(0f) }
    val position = fraction.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(wyrmRounded(Wyrm.Pill))
            .background(brush)
            .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill))
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(width) {
                detectTapGestures { onPick(((it.x - knobPx / 2f) / run).coerceIn(0f, 1f)) }
            }
            // Sideways only, so a scroll that starts on a colour track scrolls.
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
            modifier = Modifier
                .offset { IntOffset((run * position).roundToInt(), 0) }
                .size(knob)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, Color.Black.copy(alpha = 0.40f), CircleShape)
        )
    }
}

/** SwiftUI `.refreshable`: pull, a spinner at the top, release to refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosRefreshable(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            val fraction = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = fraction
                        translationY = 14.dp.toPx() * fraction
                    },
            ) { if (fraction > 0.02f) IosSpinner(size = 22.dp) }
        },
    ) { content() }
}

/* Arena background size (Modes, Skin › Arena background, the size editor; also Wyrm Desktop). */

/** The engine's default background scale (599/4096, `user_settings.c`). */
internal const val DEFAULT_BG_SCALE = 599f / 4096f
private const val BG_MIN = 0.05f
private const val BG_MAX = 4f

/** Slider position (0..1) ⇄ background scale, on a log scale so small sizes get room. */
internal fun bgScaleAt(t: Float): Float = BG_MIN * Math.pow((BG_MAX / BG_MIN).toDouble(), t.coerceIn(0f, 1f).toDouble()).toFloat()
internal fun bgSliderOf(scale: Float): Float =
    (Math.log((scale.coerceIn(BG_MIN, BG_MAX) / BG_MIN).toDouble()) / Math.log((BG_MAX / BG_MIN).toDouble())).toFloat()

/** "100%" is the arena's own size; the label a player reads everywhere. */
internal fun bgScaleLabel(scale: Float): String = "${Math.round(scale / DEFAULT_BG_SCALE * 100f)}%"

/** Settings snapshots are machine-readable and must never inherit decimal commas. */
internal fun formatSettingNumber(value: Float): String =
    String.format(java.util.Locale.US, "%.4f", value)
