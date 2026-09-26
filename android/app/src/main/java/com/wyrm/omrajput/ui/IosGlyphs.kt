package com.wyrm.omrajput.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The glyphs Wyrm iOS draws with system symbols, redrawn for Android.
 *
 * Apple's symbol artwork only ships on Apple platforms, so these are drawn
 * here from scratch on the same 24-unit grid, at the same weights: medium for
 * a resting glyph, semibold for the chosen one. Each takes its colour from
 * the caller, as a template symbol does.
 */
enum class IosGlyph {
    BELL_BADGE, PERSON_2, PLAY_CIRCLE, HEXAGON_GRID, SLIDERS,
    TROPHY, MESSAGE, BUBBLES, MIC, PERSON_CIRCLE, PERSON_3, GAMECONTROLLER, SCOPE,
    GLOBE, MAGNIFIER, XMARK_CIRCLE, ARROW_UP_RIGHT, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_DOWN,
    PLUS, ELLIPSIS, ARROW_UP, GEAR, DELETE_LEFT, SHIFT, SHIFT_FILL, GLOBE_KEY, RETURN,
    CHECKMARK, XMARK, ARROW_RIGHT, ARROW_CLOCKWISE, CHECKMARK_SHIELD, WAVEFORM,
    ENVELOPE_SHIELD, NUMBER_SQUARE, CHECKMARK_SEAL,
}

@Composable
fun IosIcon(
    glyph: IosGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    semibold: Boolean = false,
    weight: Float? = null,
) {
    val vector = remember(glyph, semibold, weight) { buildGlyph(glyph, weight ?: if (semibold) 2.0f else 1.65f) }
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},$cy a$r,$r 0 1,0 ${r * 2},0 a$r,$r 0 1,0 ${-r * 2},0 Z"

private fun hexagon(cx: Float, cy: Float, r: Float): String {
    val points = (0 until 6).map { i ->
        val angle = PI / 6 + i * PI / 3
        (cx + r * cos(angle).toFloat()) to (cy + r * sin(angle).toFloat())
    }
    return buildString {
        append("M${points[0].first},${points[0].second}")
        points.drop(1).forEach { append(" L${it.first},${it.second}") }
        append(" Z")
    }
}

/** An × as one outline, so even-odd filling cuts it out of a disc. */
private fun cross(cx: Float, cy: Float, arm: Float, half: Float): String {
    val plus = listOf(
        half to half, arm to half, arm to -half, half to -half, half to -arm, -half to -arm,
        -half to -half, -arm to -half, -arm to half, -half to half, -half to arm, half to arm,
    )
    val c = cos(PI / 4).toFloat()
    val points = plus.map { (x, y) -> (cx + (x - y) * c) to (cy + (x + y) * c) }
    return buildString {
        append("M${points[0].first},${points[0].second}")
        points.drop(1).forEach { append(" L${it.first},${it.second}") }
        append(" Z")
    }
}

private fun buildGlyph(glyph: IosGlyph, weight: Float): ImageVector {
    val strokes = mutableListOf<String>()
    val fills = mutableListOf<String>()
    val holed = mutableListOf<String>()
    when (glyph) {
        IosGlyph.BELL_BADGE -> {
            strokes += "M18.4,10.6 V11.7 C18.4,13.3 18.9,14.5 19.7,15.4 L20.2,16 C20.7,16.6 20.3,17.5 19.5,17.5 H4.5 C3.7,17.5 3.3,16.6 3.8,16 L4.3,15.4 C5.1,14.5 5.6,13.3 5.6,11.7 V9.7 C5.6,6.2 8.2,3.5 11.6,3.5 C12.3,3.5 12.9,3.6 13.5,3.8"
            strokes += "M9.7,20.1 C10.1,21 11,21.5 12,21.5 C13,21.5 13.9,21 14.3,20.1"
            fills += circle(18.4f, 5.6f, 3.0f)
        }
        IosGlyph.PERSON_2 -> {
            strokes += circle(9f, 8.2f, 3.4f)
            strokes += "M2.8,19.8 C3.1,16.4 5.7,14.1 9,14.1 C12.3,14.1 14.9,16.4 15.2,19.8 Z"
            strokes += "M14.8,5.9 C15.2,5.7 15.7,5.6 16.2,5.6 C17.8,5.6 19,6.9 19,8.5 C19,10.1 17.8,11.4 16.2,11.4 C15.9,11.4 15.6,11.4 15.3,11.3"
            strokes += "M17,14.2 C19.4,14.6 21,16.5 21.2,19.3 H17.6"
        }
        IosGlyph.PLAY_CIRCLE -> {
            strokes += circle(12f, 12f, 9.4f)
            fills += "M10.1,8.1 L16,11.5 C16.4,11.7 16.4,12.3 16,12.5 L10.1,15.9 C9.7,16.1 9.2,15.8 9.2,15.4 V8.6 C9.2,8.2 9.7,7.9 10.1,8.1 Z"
        }
        IosGlyph.HEXAGON_GRID -> {
            strokes += circle(12f, 12f, 9.4f)
            val d = 3.55f
            listOf(0f to 0f, 0f to -d, 0f to d, d * 0.866f to -d / 2, d * 0.866f to d / 2,
                -d * 0.866f to -d / 2, -d * 0.866f to d / 2).forEach { (x, y) ->
                fills += hexagon(12f + x, 12f + y, 1.55f)
            }
        }
        IosGlyph.SLIDERS -> {
            strokes += "M3,6 H12.7 M17.3,6 H21"
            strokes += circle(15f, 6f, 2.3f)
            strokes += "M3,12 H5.7 M10.3,12 H21"
            strokes += circle(8f, 12f, 2.3f)
            strokes += "M3,18 H11.7 M16.3,18 H21"
            strokes += circle(14f, 18f, 2.3f)
        }
        IosGlyph.TROPHY -> {
            holed += "M6.6,2.6 H17.4 C17.9,2.6 18.2,2.9 18.2,3.4 V4 H20.4 C21,4 21.4,4.4 21.4,5 V6.9 C21.4,9.8 19.4,12 16.7,12.3 " +
                "C15.8,13.9 14.4,15 12.9,15.3 V17.5 H15.4 C16.4,17.5 17.1,18.2 17.1,19.2 V21.4 H6.9 V19.2 C6.9,18.2 7.6,17.5 8.6,17.5 H11.1 V15.3 " +
                "C9.6,15 8.2,13.9 7.3,12.3 C4.6,12 2.6,9.8 2.6,6.9 V5 C2.6,4.4 3,4 3.6,4 H5.8 V3.4 C5.8,2.9 6.1,2.6 6.6,2.6 Z " +
                "M5.8,5.8 H4.4 V6.9 C4.4,8.5 5.3,9.8 6.6,10.3 C6.1,9.5 5.8,8.6 5.8,7.6 Z " +
                "M18.2,5.8 V7.6 C18.2,8.6 17.9,9.5 17.4,10.3 C18.7,9.8 19.6,8.5 19.6,6.9 V5.8 Z"
        }
        IosGlyph.MESSAGE -> {
            fills += "M12,3.6 C16.9,3.6 20.8,7 20.8,11.2 C20.8,15.4 16.9,18.8 12,18.8 C11.1,18.8 10.2,18.7 9.4,18.5 L5.4,20.6 C4.9,20.9 4.3,20.4 4.5,19.9 L5.6,16.6 C4.1,15.2 3.2,13.3 3.2,11.2 C3.2,7 7.1,3.6 12,3.6 Z"
        }
        IosGlyph.BUBBLES -> {
            fills += "M10.2,2.8 H19.2 C20.8,2.8 22,4 22,5.6 V10.6 C22,12.2 20.8,13.4 19.2,13.4 H18.9 V15.7 C18.9,16.2 18.3,16.4 18,16.1 L15.5,13.4 H15.2 V11.2 C15.2,9.2 13.6,7.6 11.6,7.6 H7.4 V5.6 C7.4,4 8.6,2.8 10.2,2.8 Z"
            fills += "M4.6,9.2 H11.4 C12.9,9.2 14,10.3 14,11.8 V16.4 C14,17.9 12.9,19 11.4,19 H7.9 L5.2,21.5 C4.8,21.8 4.3,21.6 4.3,21.1 V19 C3,18.8 2,17.7 2,16.4 V11.8 C2,10.3 3.1,9.2 4.6,9.2 Z"
        }
        IosGlyph.MIC -> {
            fills += "M12,2.8 C13.9,2.8 15.3,4.3 15.3,6.1 V11.6 C15.3,13.5 13.9,14.9 12,14.9 C10.1,14.9 8.7,13.5 8.7,11.6 V6.1 C8.7,4.3 10.1,2.8 12,2.8 Z"
            strokes += "M5.9,11.3 C5.9,14.7 8.6,17.4 12,17.4 C15.4,17.4 18.1,14.7 18.1,11.3 M12,17.4 V21 M8.8,21 H15.2"
        }
        IosGlyph.PERSON_CIRCLE -> {
            holed += circle(12f, 12f, 9.6f) + " " + circle(12f, 9.6f, 3.3f) +
                " M6.2,18.3 C7.2,15.9 9.4,14.6 12,14.6 C14.6,14.6 16.8,15.9 17.8,18.3 C16.3,19.9 14.3,20.9 12,20.9 C9.7,20.9 7.7,19.9 6.2,18.3 Z"
        }
        IosGlyph.PERSON_3 -> {
            fills += circle(12f, 7.6f, 2.9f)
            fills += "M6.9,17.8 C7.2,14.9 9.3,12.9 12,12.9 C14.7,12.9 16.8,14.9 17.1,17.8 Z"
            fills += circle(5.6f, 9.4f, 2.2f)
            fills += "M1.7,17.2 C1.9,15 3.5,13.5 5.6,13.5 C6.1,13.5 6.5,13.6 6.9,13.7 C6.1,14.7 5.6,15.9 5.5,17.2 Z"
            fills += circle(18.4f, 9.4f, 2.2f)
            fills += "M22.3,17.2 C22.1,15 20.5,13.5 18.4,13.5 C17.9,13.5 17.5,13.6 17.1,13.7 C17.9,14.7 18.4,15.9 18.5,17.2 Z"
        }
        IosGlyph.GAMECONTROLLER -> {
            strokes += "M7.4,6.7 H16.6 C19.2,6.7 20.7,8.7 21.3,12 L21.9,15.3 C22.3,17.4 20.2,18.8 18.6,17.4 L16.4,15.4 C16,15.1 15.5,14.9 15,14.9 H9 C8.5,14.9 8,15.1 7.6,15.4 L5.4,17.4 C3.8,18.8 1.7,17.4 2.1,15.3 L2.7,12 C3.3,8.7 4.8,6.7 7.4,6.7 Z"
            strokes += "M7.3,9.4 V12.6 M5.7,11 H8.9"
            fills += circle(15.9f, 10f, 0.95f)
            fills += circle(17.7f, 11.9f, 0.95f)
        }
        IosGlyph.SCOPE -> {
            strokes += circle(12f, 12f, 7.4f)
            strokes += circle(12f, 12f, 2.6f)
            strokes += "M12,2.5 V5.6 M12,18.4 V21.5 M2.5,12 H5.6 M18.4,12 H21.5"
        }
        IosGlyph.GLOBE -> {
            strokes += circle(12f, 12f, 9.2f)
            strokes += "M12,2.8 C9.6,5.2 8.4,8.4 8.4,12 C8.4,15.6 9.6,18.8 12,21.2 C14.4,18.8 15.6,15.6 15.6,12 C15.6,8.4 14.4,5.2 12,2.8 Z"
            strokes += "M3.2,9 H20.8 M3.2,15 H20.8"
        }
        IosGlyph.MAGNIFIER -> {
            strokes += circle(10.4f, 10.4f, 6.4f)
            strokes += "M15.1,15.1 L20.6,20.6"
        }
        IosGlyph.XMARK_CIRCLE -> {
            holed += circle(12f, 12f, 9.6f) + " " + cross(12f, 12f, 4.6f, 0.95f)
        }
        IosGlyph.ARROW_UP_RIGHT -> strokes += "M7,17 L17,7 M9,7 H17 V15"
        IosGlyph.CHEVRON_LEFT -> strokes += "M15,4.5 L7.5,12 L15,19.5"
        IosGlyph.CHEVRON_RIGHT -> strokes += "M9,4.5 L16.5,12 L9,19.5"
        IosGlyph.CHEVRON_DOWN -> strokes += "M4.5,9 L12,16.5 L19.5,9"
        IosGlyph.PLUS -> strokes += "M12,4.5 V19.5 M4.5,12 H19.5"
        IosGlyph.ELLIPSIS -> {
            fills += circle(5.5f, 12f, 1.6f)
            fills += circle(12f, 12f, 1.6f)
            fills += circle(18.5f, 12f, 1.6f)
        }
        IosGlyph.ARROW_UP -> strokes += "M12,19.5 V5 M6,11 L12,5 L18,11"
        IosGlyph.GEAR -> {
            val teeth = buildString {
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val x1 = 12 + 6.6 * cos(a); val y1 = 12 + 6.6 * sin(a)
                    val x2 = 12 + 9.2 * cos(a); val y2 = 12 + 9.2 * sin(a)
                    append("M${x1.toFloat()},${y1.toFloat()} L${x2.toFloat()},${y2.toFloat()} ")
                }
            }
            strokes += teeth
            strokes += circle(12f, 12f, 6.6f)
            strokes += circle(12f, 12f, 2.6f)
        }
        IosGlyph.DELETE_LEFT -> {
            strokes += "M8.6,5.5 H19.4 C20.3,5.5 21,6.2 21,7.1 V16.9 C21,17.8 20.3,18.5 19.4,18.5 H8.6 C8.1,18.5 7.7,18.3 7.4,17.9 L2.8,12.6 C2.5,12.3 2.5,11.7 2.8,11.4 L7.4,6.1 C7.7,5.7 8.1,5.5 8.6,5.5 Z"
            strokes += "M11,9 L17,15 M17,9 L11,15"
        }
        IosGlyph.SHIFT -> strokes += "M12,3.8 L20.4,12.6 H15.6 V20 H8.4 V12.6 H3.6 Z"
        IosGlyph.SHIFT_FILL -> fills += "M12,3.8 L20.4,12.6 H15.6 V20 H8.4 V12.6 H3.6 Z"
        IosGlyph.GLOBE_KEY -> {
            strokes += circle(12f, 12f, 8.6f)
            strokes += "M12,3.4 C10,5.6 9,8.6 9,12 C9,15.4 10,18.4 12,20.6 C14,18.4 15,15.4 15,12 C15,8.6 14,5.6 12,3.4 Z M3.6,12 H20.4"
        }
        IosGlyph.RETURN -> strokes += "M19,5.5 V12 C19,13.1 18.1,14 17,14 H5.5 M9.5,10 L5.5,14 L9.5,18"
        IosGlyph.CHECKMARK -> strokes += "M5,12.8 L9.6,17.2 L19,6.8"
        IosGlyph.ENVELOPE_SHIELD -> {
            strokes += "M3.2,6.5 C3.2,5.6 3.9,4.9 4.8,4.9 H17.2 C18.1,4.9 18.8,5.6 18.8,6.5 V9 M3.2,6.5 V15.5 C3.2,16.4 3.9,17.1 4.8,17.1 H11.5 M3.6,5.6 L11,11.2 L18.4,5.6"
            holed += "M17.4,11 L21.6,12.6 V15.7 C21.6,18.2 19.9,20.2 17.4,21.1 C14.9,20.2 13.2,18.2 13.2,15.7 V12.6 Z"
        }
        IosGlyph.NUMBER_SQUARE -> holed += "M5.8,3 H18.2 C19.7,3 21,4.3 21,5.8 V18.2 C21,19.7 19.7,21 18.2,21 H5.8 C4.3,21 3,19.7 3,18.2 V5.8 C3,4.3 4.3,3 5.8,3 Z " +
            "M9.2,7 L8.8,9.4 H7.4 V10.9 H8.6 L8.2,13.1 H6.9 V14.6 H8 L7.6,17 H9.1 L9.5,14.6 H11.6 L11.2,17 H12.7 L13.1,14.6 H14.6 V13.1 H13.3 L13.7,10.9 H15.1 V9.4 H13.9 L14.3,7 H12.8 L12.4,9.4 H10.3 L10.7,7 Z M10.1,10.9 H12.2 L11.8,13.1 H9.7 Z"
        IosGlyph.CHECKMARK_SEAL -> {
            val seal = buildString {
                for (i in 0 until 24) {
                    val a = i * PI / 12 - PI / 2
                    val r = if (i % 2 == 0) 10.0 else 8.6
                    val x = 12 + r * cos(a); val y = 12 + r * sin(a)
                    append(if (i == 0) "M" else " L"); append("${x.toFloat()},${y.toFloat()}")
                }
                append(" Z M7.6,12.3 L8.9,11 L10.9,13 L15.2,8.7 L16.5,10 L10.9,15.6 Z")
            }
            holed += seal
        }
        IosGlyph.CHECKMARK_SHIELD -> holed += "M12,2.6 L19.6,5.4 V11.4 C19.6,16.2 16.4,19.8 12,21.4 C7.6,19.8 4.4,16.2 4.4,11.4 V5.4 Z M8.1,11.9 L9.3,10.7 L11,12.4 L14.8,8.6 L16,9.8 L11,14.8 Z"
        IosGlyph.WAVEFORM -> strokes += "M3.5,10.5 V13.5 M7,7.5 V16.5 M10.5,4 V20 M14,8 V16 M17.5,6 V18 M21,10.5 V13.5"
        IosGlyph.XMARK -> strokes += "M6.2,6.2 L17.8,17.8 M17.8,6.2 L6.2,17.8"
        IosGlyph.ARROW_RIGHT -> strokes += "M4,12 H19.5 M13.2,5.7 L19.5,12 L13.2,18.3"
        IosGlyph.ARROW_CLOCKWISE -> strokes += "M19.2,12 A7.2,7.2 0 1,1 16.9,6.7 M17.4,2.9 L17.2,7.2 L12.9,7"
    }
    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val parser = PathParser()
        fills.forEach { d ->
            addPath(pathData = parser.parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
        strokes.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = weight,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        holed.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
        }
    }.build()
}
