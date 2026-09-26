package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The on-screen controls, in glass.
 *
 * One definition, drawn in the layout editor and on the settings previews, so
 * what you arrange is what you play with. The look is built out of light
 * rather than colour: a translucent fill, a bright hairline where the edge
 * catches, and a highlight along the top as though the piece were lit from
 * above. Nothing here is tinted, because the arena underneath supplies all the
 * colour a control should have — the control's job is to be legible over
 * whatever happens to be behind it.
 */

/*
 * The controls' own mix, kept separate from the interface's.
 *
 * Same recipe as [glassFill] and [glassEdge] in the surface language, but
 * stronger: these are drawn over a live arena rather than over Wyrm's black,
 * and they have to stay legible with a snake underneath them. The interface
 * uses the quieter mix; naming them apart keeps a change to one from silently
 * restyling the other.
 */
private fun controlFill(opacity: Float) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.22f * opacity),
    0.55f to Color.White.copy(alpha = 0.10f * opacity),
    1f to Color.White.copy(alpha = 0.05f * opacity),
)

private fun controlEdge(opacity: Float) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.55f * opacity),
    1f to Color.White.copy(alpha = 0.16f * opacity),
)

/**
 * The steering joystick.
 *
 * [knob] is where the thumb has pulled it, from -1 to 1 on each axis, so the
 * same component draws both the resting control and a live one.
 */
@Composable
fun GlassJoystick(
    diameter: Dp,
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    knob: Offset = Offset.Zero,
) {
    val knobSize = diameter * 0.42f
    val travel = (diameter - knobSize) / 2f

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.72f to Color.Black.copy(alpha = 0.28f * opacity),
                        1f to Color.Transparent,
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(controlFill(opacity))
                .border(1.5.dp, controlEdge(opacity), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(diameter * 0.62f)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.12f * opacity), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = travel * knob.x.coerceIn(-1f, 1f), y = travel * knob.y.coerceIn(-1f, 1f))
                .size(knobSize)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.96f * opacity),
                        1f to Color.White.copy(alpha = 0.72f * opacity),
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.9f * opacity), CircleShape)
        )
    }
}

/** The boost button: the same glass, with the one glyph on it. */
@Composable
fun GlassBoostButton(
    diameter: Dp,
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    pressed: Boolean = false,
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.72f to Color.Black.copy(alpha = 0.26f * opacity),
                        1f to Color.Transparent,
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    if (pressed) {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.75f * opacity),
                            1f to Color.White.copy(alpha = 0.42f * opacity),
                        )
                    } else {
                        controlFill(opacity)
                    }
                )
                .border(1.5.dp, controlEdge(opacity), CircleShape)
        )
        Text(
            text = "»",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = (diameter.value * 0.44f).sp,
            color = if (pressed) {
                Wyrm.Black.copy(alpha = 0.85f)
            } else {
                Color.White.copy(alpha = 0.92f * opacity)
            },
        )
    }
}

/**
 * The zoom bar.
 *
 * A capsule track with a filled run and a round knob, the way a phone draws a
 * volume slider — the shape is doing the explaining, so there is no label on
 * it. [value] is 0 at the near end and 1 at the far one.
 */
@Composable
fun GlassZoomBar(
    length: Dp,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    opacity: Float = 1f,
    value: Float = 0.5f,
) {
    val thickness = 26.dp
    val knob = thickness - 8.dp
    val fraction = value.coerceIn(0f, 1f)
    val travel = length - thickness

    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(thickness).height(length) else Modifier.width(length).height(thickness)),
        contentAlignment = if (vertical) Alignment.TopCenter else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(wyrmRounded(999.dp))
                .background(controlFill(opacity))
                .border(1.dp, controlEdge(opacity), wyrmRounded(999.dp))
        )
        Box(
            modifier = Modifier
                .then(
                    if (vertical) {
                        Modifier.width(thickness).height(length * fraction)
                    } else {
                        Modifier.width(length * fraction).height(thickness)
                    }
                )
                .clip(wyrmRounded(999.dp))
                .background(Color.White.copy(alpha = 0.30f * opacity))
        )
        Box(
            modifier = Modifier
                .then(
                    if (vertical) {
                        Modifier.offset(y = travel * fraction + 4.dp)
                    } else {
                        Modifier.offset(x = travel * fraction + 4.dp)
                    }
                )
                .size(knob)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.98f * opacity),
                        1f to Color.White.copy(alpha = 0.78f * opacity),
                    )
                )
        )
    }
}

/** An on-screen button: the same glass, sized to its action label. */
@Composable
fun GlassKey(
    label: String,
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    scale: Float = 1f,
    dimmed: Boolean = false,
) {
    val alpha = if (dimmed) opacity * 0.35f else opacity
    Box(
        modifier = modifier
            .width((104 * scale).dp)
            .height((54 * scale).dp)
            .clip(RoundedCornerShape((16 * scale).dp))
            .background(controlFill(alpha))
            .border(1.dp, controlEdge(alpha), RoundedCornerShape((16 * scale).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = (12 * scale).sp,
            letterSpacing = 1.2.sp,
            color = Color.White.copy(alpha = if (dimmed) 0.45f else 0.94f),
        )
    }
}

/** Paper counterparts used only by Settings previews and layout editors. */
@Composable
fun PaperJoystick(
    diameter: Dp,
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    knob: Offset = Offset.Zero,
) {
    val knobSize = diameter * 0.42f
    val travel = (diameter - knobSize) / 2f
    Box(
        modifier = modifier.size(diameter).alpha(opacity.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Wyrm.Card)
                .border(1.5.dp, Wyrm.Ink, CircleShape),
        )
        Box(
            Modifier
                .size(diameter * 0.62f)
                .clip(CircleShape)
                .border(1.dp, Wyrm.Rule, CircleShape),
        )
        Box(
            Modifier
                .offset(
                    x = travel * knob.x.coerceIn(-1f, 1f),
                    y = travel * knob.y.coerceIn(-1f, 1f),
                )
                .size(knobSize)
                .clip(CircleShape)
                .background(Wyrm.Ink),
        )
    }
}

@Composable
fun PaperBoostButton(
    diameter: Dp,
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Wyrm.Card.copy(alpha = opacity.coerceIn(0f, 1f)))
            .border(1.5.dp, Wyrm.Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "»",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = (diameter.value * 0.44f).sp,
            color = Wyrm.Ink,
        )
    }
}

@Composable
fun PaperZoomBar(
    length: Dp,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    opacity: Float = 1f,
    value: Float = 0.5f,
) {
    val thickness = 26.dp
    val knob = thickness - 8.dp
    val fraction = value.coerceIn(0f, 1f)
    val travel = length - thickness
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(thickness).height(length) else Modifier.width(length).height(thickness))
            .alpha(opacity.coerceIn(0f, 1f))
            .clip(wyrmRounded(999.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Ink, wyrmRounded(999.dp)),
        contentAlignment = if (vertical) Alignment.TopCenter else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .then(
                    if (vertical) Modifier.width(thickness).height(length * fraction)
                    else Modifier.width(length * fraction).height(thickness)
                )
                .background(Wyrm.Track),
        )
        Box(
            Modifier
                .then(
                    if (vertical) Modifier.offset(y = travel * fraction + 4.dp)
                    else Modifier.offset(x = travel * fraction + 4.dp)
                )
                .size(knob)
                .clip(CircleShape)
                .background(Wyrm.Ink),
        )
    }
}

@Composable
fun PaperKey(
    label: String,
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    scale: Float = 1f,
) {
    Box(
        modifier = modifier
            .width((104 * scale).dp)
            .height((54 * scale).dp)
            .clip(RoundedCornerShape((14 * scale).dp))
            .background(Wyrm.Card.copy(alpha = opacity.coerceIn(0f, 1f)))
            .border(1.25.dp, Wyrm.Ink, RoundedCornerShape((14 * scale).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = (12 * scale).sp,
            letterSpacing = 0.7.sp,
            color = Wyrm.Ink,
        )
    }
}
