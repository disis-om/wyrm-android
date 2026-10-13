package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/* Skin Studio's shared pieces (OM, 2026-10-12): buttons, the colour picker,
   labelled sliders and the snake drawing every Studio screen uses. */

/** An ink capsule (filled) or a paper one (outline) with an optional Lucide icon. */
@Composable
internal fun StudioCapsule(
    label: String,
    icon: Int? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = if (filled) Wyrm.OnInk else Wyrm.Ink
    Row(
        modifier
            .scale(pressScale(pressed, enabled))
            .clip(WyrmCapsule)
            .background(if (filled) Wyrm.Ink.copy(alpha = if (enabled) 1f else 0.35f) else Wyrm.Card)
            .then(if (filled) Modifier else Modifier.border(1.dp, Wyrm.Rule, WyrmCapsule))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(15.dp))
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = fg)
    }
}

/** A round icon button; [active] fills it with ink. */
@Composable
internal fun StudioRoundButton(
    icon: Int,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 42.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(size)
            .scale(pressScale(pressed, enabled))
            .clip(CircleShape)
            .background(if (active) Wyrm.Ink else Wyrm.Card)
            .border(1.dp, if (active) Wyrm.Ink else Wyrm.Rule, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = (if (active) Wyrm.OnInk else Wyrm.Ink).copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size * 0.45f))
    }
}

/** A label, its value, and Wyrm's slider under them. */
@Composable
internal fun StudioValueSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    shown: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Wyrm.Ink,
                modifier = Modifier.weight(1f))
            Text(shown(value), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Wyrm.Quiet)
        }
        Spacer(Modifier.height(6.dp))
        WyrmSlider(value = value, minimum = range.start, maximum = range.endInclusive, onChange = onChange)
    }
}

internal fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

/** Colours a player reaches for first. */
internal val StudioSwatches = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFF3FEE96, 0xFF0F6B46, 0xFF22E3FF, 0xFF2F5BFF,
    0xFF8B5CFF, 0xFFFF4F9A, 0xFFFF3B30, 0xFFFF8A1F, 0xFFF2B33D, 0xFFFFF27A,
).map { it.toInt() }

/* ------------------------------------------------------------ the snake */

/** Bead to bead along the body, in bead widths: the classic Skin preview's (and the arena's) 8/48. */
internal const val STUDIO_BEAD_STEP = 8f / 48f

/**
 * A Studio snake laid along any line: [place] gives bead [i]'s centre
 * (0 = the head), the beads are drawn from the tail so the head sits on top,
 * each turned to where it travels, then the eyes. [unit] is a snake unit in
 * px (a bead is 29 units across, as in the arena). [look] is where the pupils
 * look, in snake units.
 */
internal fun DrawScope.drawStudioSnake(
    skin: StudioSkin,
    count: Int,
    bead: Float,
    place: (Int) -> Offset,
    image: (String) -> ImageBitmap?,
    look: Offset = Offset(1f, 0f),
    eyes: List<StudioEye> = skin.eyes,
    shadow: Boolean = true,
    eyesShown: Boolean = true,
) {
    if (skin.pattern.isEmpty() || count <= 0) return
    fun heading(i: Int): Float {
        val a = place(i)
        val b = if (i == 0) place(0) + (place(0) - place(1)) else place(i - 1)
        return atan2(b.y - a.y, b.x - a.x)
    }
    if (shadow) {
        val drop = Offset(bead * 0.04f, bead * 0.09f)
        for (i in count - 1 downTo 0) {
            val p = place(i) + drop
            drawCircle(Color.Black.copy(alpha = 0.10f), radius = bead * 0.5f, center = p)
        }
    }
    for (i in count - 1 downTo 0) {
        val img = image(skin.pattern[i % skin.pattern.size]) ?: continue
        val p = place(i)
        rotateRad(heading(i), pivot = p) {
            drawImageInto(img, p.x - bead / 2f, p.y - bead / 2f, bead, bead)
        }
    }
    if (eyesShown) drawStudioEyes(eyes, place(0), heading(0), bead / 29f, look)
}

/** The eyes on a head at [head] turned [angle] radians; [unit] px per snake unit. */
internal fun DrawScope.drawStudioEyes(eyes: List<StudioEye>, head: Offset, angle: Float, unit: Float, look: Offset) {
    val c = cos(angle)
    val s = sin(angle)
    fun at(fwd: Float, side: Float) = Offset(head.x + (c * fwd - s * side) * unit, head.y + (s * fwd + c * side) * unit)
    for (eye in eyes) {
        val centre = at(eye.fwd, eye.side)
        if (eye.white > 0f) {
            val d = eye.white * 2f * unit
            rotateRad(angle, pivot = centre) { drawImageInto(StudioArt.whiteImage(eye), centre.x - d / 2, centre.y - d / 2, d, d) }
        }
        if (eye.pupil > 0f) {
            val lx = look.x * eye.look
            val ly = look.y * eye.look
            val p = at(eye.fwd + 0.5f + lx, eye.side + ly)
            val d = eye.pupil * 2f * unit
            rotateRad(angle, pivot = p) { drawImageInto(StudioArt.pupilImage(eye), p.x - d / 2, p.y - d / 2, d, d) }
        }
    }
}

/** A short straight Studio snake for cards and tiles, head on the right. */
internal fun DrawScope.drawStudioStrip(skin: StudioSkin, image: (String) -> ImageBitmap?, beads: Int = 34) {
    val bead = size.height * 0.7f
    val step = bead * STUDIO_BEAD_STEP
    val span = bead + step * (beads - 1)
    val scale = if (span > size.width) size.width / span else 1f
    val b = bead * scale
    val st = step * scale
    val y = size.height / 2f
    val headX = (size.width + (b + st * (beads - 1))) / 2f - b / 2f
    drawStudioSnake(skin, beads, b, place = { i -> Offset(headX - i * st, y + sin(i * 0.25f) * b * 0.06f) }, image = image, shadow = false)
}
