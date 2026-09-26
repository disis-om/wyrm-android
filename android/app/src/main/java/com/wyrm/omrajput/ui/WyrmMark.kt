package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The Wyrm mark: one lean, continuous W stroke.
 *
 * [fill] between 0 and 1 wipes [ink] across the mark from left to right.
 * Launch uses [WyrmMarkShimmer] instead — a diagonal shine on the letter only.
 */
@Composable
fun WyrmMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    fill: Float = 1f,
    ink: Color = Wyrm.Ink,
    unfilled: Color = Wyrm.Track,
) {
    Canvas(modifier = modifier.size(size)) {
        drawWyrmMark(this, size.toPx(), fill, ink, unfilled)
    }
}

/**
 * Ink W on paper. A bright band sweeps top-left → bottom-right through the
 * stroke only, forever. Drawn as a second stroke (SrcOver) so the shine
 * actually lands on the letter — blend-mode SrcAtop was invisible on device.
 */
@Composable
fun WyrmMarkShimmer(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    shimmer: Float,
    ink: Color = Wyrm.Ink,
) {
    val canvas = size * 1.28f
    Canvas(modifier = modifier.size(canvas)) {
        val px = size.toPx()
        val path = wyrmPath(px)
        val stroke = Stroke(width = px * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path = path, color = ink, style = stroke)

        val t = shimmer.coerceIn(0f, 1f)
        val span = hypot(this.size.width, this.size.height)
        val band = px * 0.58f
        val n = Offset(1f / sqrt(2f), 1f / sqrt(2f))
        val travel = span + band * 2f
        val along = t * travel - band
        val centre = Offset(this.size.width / 2f, this.size.height / 2f) +
            n * (along - span / 2f)
        val half = band * 0.55f
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.28f to Color.Transparent,
                    0.44f to Color.White.copy(alpha = 0.55f),
                    0.50f to Color.White,
                    0.56f to Color.White.copy(alpha = 0.55f),
                    0.72f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                start = centre - n * half,
                end = centre + n * half,
            ),
            style = stroke,
        )
    }
}

private fun DrawScope.wyrmPath(px: Float): Path {
    val w = px
    val h = px * 0.78f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    return Path().apply {
        moveTo(left + w * 0.06f, top + h * 0.10f)
        cubicTo(
            left + w * 0.13f, top + h * 0.92f,
            left + w * 0.30f, top + h * 0.96f,
            left + w * 0.36f, top + h * 0.38f,
        )
        cubicTo(
            left + w * 0.42f, top + h * 0.94f,
            left + w * 0.58f, top + h * 0.94f,
            left + w * 0.64f, top + h * 0.38f,
        )
        cubicTo(
            left + w * 0.70f, top + h * 0.96f,
            left + w * 0.87f, top + h * 0.92f,
            left + w * 0.94f, top + h * 0.10f,
        )
    }
}

private fun drawWyrmMark(
    scope: DrawScope,
    px: Float,
    fill: Float,
    ink: Color,
    unfilled: Color,
) = with(scope) {
    val path = wyrmPath(px)
    val style = Stroke(width = px * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(path = path, color = unfilled, style = style)
    val progress = fill.coerceIn(0f, 1f)
    if (progress <= 0f) return
    val w = px
    val h = px * 0.78f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val stroke = w * 0.16f
    clipPath(
        path = Path().apply {
            addRect(
                androidx.compose.ui.geometry.Rect(
                    offset = Offset(left - stroke, top - stroke),
                    size = Size((w + stroke * 2f) * progress, h + stroke * 2f),
                )
            )
        }
    ) {
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(ink.copy(alpha = 0.72f), ink),
                start = Offset(left, top),
                end = Offset(left + w, top + h),
            ),
            style = style,
        )
    }
}

internal fun pathLength(path: Path): Float = PathMeasure().apply { setPath(path, false) }.length
