package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The eye editor (OM, 2026-10-12): the head, big, with its eyes. Drag an eye
 * anywhere; tap one to style it; hold one for Delete this eye / Delete every
 * eye / Duplicate / Mirror. Each eye has its own white (its background: shape
 * and colour), its own pupil (16 shapes, any colour), sizes and how far it
 * looks. "Pair" moves and styles an eye's mirror twin with it. Nothing is
 * kept until Done.
 */

@Composable
internal fun StudioEyeEditor(
    skin: StudioSkin,
    image: (String) -> ImageBitmap?,
    insetTop: Dp,
    insetBottom: Dp,
    onDone: () -> Unit,
) {
    var eyes by remember(skin.id) { mutableStateOf(skin.eyes) }
    var selected by remember(skin.id) { mutableStateOf(if (skin.eyes.isNotEmpty()) 0 else -1) }
    var paired by remember { mutableStateOf(true) }
    var menuFor by remember { mutableStateOf(-1) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) time += ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
        }
    }

    /** The eye mirrored from [index] across the head, if there is one. */
    fun twin(list: List<StudioEye>, index: Int): Int {
        val e = list.getOrNull(index) ?: return -1
        if (abs(e.side) < 0.6f) return -1
        var best = -1
        var bestD = 2.2f
        list.forEachIndexed { i, o ->
            if (i == index) return@forEachIndexed
            val d = hypot(o.fwd - e.fwd, o.side + e.side)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** Changes eye [index] (and its twin, mirrored, when paired). */
    fun edit(index: Int, change: (StudioEye) -> StudioEye) {
        val list = eyes.toMutableList()
        val e = list.getOrNull(index) ?: return
        val t = if (paired) twin(list, index) else -1
        val next = change(e)
        list[index] = next
        if (t >= 0) {
            val other = list[t]
            list[t] = next.copy(fwd = next.fwd, side = -next.side).let { mirrored ->
                // Position mirrors; the twin keeps nothing of its own but its side.
                if (next.fwd == e.fwd && next.side == e.side) mirrored.copy(fwd = other.fwd, side = other.side) else mirrored
            }
        }
        eyes = list
    }

    fun styleAll(from: StudioEye) {
        eyes = eyes.map {
            it.copy(white = from.white, pupil = from.pupil, look = from.look, whiteColor = from.whiteColor,
                pupilColor = from.pupilColor, whiteShape = from.whiteShape, pupilShape = from.pupilShape)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Wyrm.Paper)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        Column(Modifier.fillMaxSize().padding(top = insetTop)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioRoundButton(LucideR.drawable.lucide_ic_x, size = 38.dp) { onDone() }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("EYES · ${eyes.size} / ${SkinStudio.MAX_EYES}", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp, letterSpacing = 1.2.sp, color = Wyrm.Quiet)
                    Text(skin.name, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Wyrm.Ink, maxLines = 1)
                }
                StudioCapsule("Done", icon = LucideR.drawable.lucide_ic_check) {
                    SkinStudio.setEyes(skin.id, eyes)
                    onDone()
                }
            }
            // The head.
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(wyrmRounded(26.dp))
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(26.dp)),
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(paired) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val bead = size.height * 0.62f
                                val unit = bead / 29f
                                val head = Offset(size.width * 0.58f, size.height * 0.5f)
                                fun centre(e: StudioEye) = Offset(head.x + e.fwd * unit, head.y + e.side * unit)
                                val reach = maxOf(22.dp.toPx(), 0f)
                                val hit = eyes.indices.minByOrNull { hypot(centre(eyes[it]).x - down.position.x, centre(eyes[it]).y - down.position.y) }
                                    ?.takeIf { i ->
                                        val e = eyes[i]
                                        hypot(centre(e).x - down.position.x, centre(e).y - down.position.y) <= maxOf(e.white * unit, reach)
                                    }
                                if (hit == null) {
                                    selected = -1
                                    return@awaitEachGesture
                                }
                                selected = hit
                                down.consume()
                                var dragging = false
                                val outcome = withTimeoutOrNull(450L) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val c = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                        if (!c.pressed) return@withTimeoutOrNull false
                                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                            dragging = true
                                            return@withTimeoutOrNull false
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE") false
                                }
                                if (outcome == null) {
                                    menuFor = hit
                                    return@awaitEachGesture
                                }
                                if (!dragging) return@awaitEachGesture
                                var last = down.position
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    val delta = c.position - last
                                    last = c.position
                                    c.consume()
                                    edit(hit) {
                                        it.copy(
                                            fwd = (it.fwd + delta.x / unit).coerceIn(-20f, 20f),
                                            side = (it.side + delta.y / unit).coerceIn(-20f, 20f),
                                        )
                                    }
                                }
                            }
                        },
                ) {
                    drawRect(Brush.radialGradient(listOf(Wyrm.Ink.copy(alpha = 0.05f), Color.Transparent),
                        center = Offset(size.width * 0.58f, size.height * 0.5f), radius = size.width * 0.6f))
                    val bead = size.height * 0.62f
                    val unit = bead / 29f
                    val head = Offset(size.width * 0.58f, size.height * 0.5f)
                    val look = Offset(1.2f + 0.8f * cos(time * 0.8f), 1.6f * sin(time * 0.6f))
                    drawStudioSnake(skin, 16, bead, place = { i -> Offset(head.x - i * bead * STUDIO_BEAD_STEP, head.y) }, image = image,
                        look = look, eyes = eyes, shadow = false)
                    // The chosen eye, ringed.
                    eyes.getOrNull(selected)?.let { e ->
                        val c = Offset(head.x + e.fwd * unit, head.y + e.side * unit)
                        drawCircle(Wyrm.Link, radius = maxOf(e.white, e.pupil) * unit + 6f, center = c, style = Stroke(3f))
                    }
                }
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioCapsule("Add eye", icon = LucideR.drawable.lucide_ic_plus, enabled = eyes.size < SkinStudio.MAX_EYES) {
                        val style = eyes.getOrNull(selected) ?: eyes.lastOrNull() ?: StudioEye()
                        val spot = listOf(0f, 9f, -9f, 4.5f, -4.5f, 13f, -13f, 2f).firstOrNull { s -> eyes.none { abs(it.side - s) < 2f && abs(it.fwd - 6f) < 2f } } ?: 0f
                        eyes = eyes + style.copy(fwd = 6f, side = spot)
                        selected = eyes.lastIndex
                    }
                    StudioCapsule("Pair", icon = LucideR.drawable.lucide_ic_flip_horizontal_2, filled = paired) { paired = !paired }
                }
                Text("Drag an eye to move it. Touch and hold an eye to delete, copy or mirror it.", fontFamily = Wyrm.Body,
                    fontSize = 10.5.sp, lineHeight = 14.sp, color = Wyrm.Quiet,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp, end = 120.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                val eye = eyes.getOrNull(selected)
                if (eye == null) {
                    Spacer(Modifier.height(18.dp))
                    Text(if (eyes.isEmpty()) "No eyes. Add one, or keep the snake blind." else "Tap an eye to style it.",
                        fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
                } else {
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Pupil")
                    PupilShapes(eye) { shape -> edit(selected) { it.copy(pupilShape = shape) } }
                    Spacer(Modifier.height(10.dp))
                    StudioColorPicker(eye.pupilColor) { c -> edit(selected) { it.copy(pupilColor = c) } }
                    StudioValueSlider("Pupil size", eye.pupil, 0f..12f, { "%.1f".format(it) }) { v -> edit(selected) { it.copy(pupil = v) } }
                    StudioValueSlider("Looks around", eye.look, 0f..3f, ::percent) { v -> edit(selected) { it.copy(look = v) } }
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Sclera")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioArt.WHITE_SHAPES.forEachIndexed { index, name ->
                            StudioCapsule(name, filled = eye.whiteShape == index) { edit(selected) { it.copy(whiteShape = index) } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    StudioColorPicker(eye.whiteColor) { c -> edit(selected) { it.copy(whiteColor = c) } }
                    StudioValueSlider("Sclera size", eye.white, 0f..14f, { "%.1f".format(it) }) { v -> edit(selected) { it.copy(white = v) } }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioCapsule("Style every eye like this", icon = LucideR.drawable.lucide_ic_wand_sparkles, filled = false) { styleAll(eye) }
                    }
                }
                Spacer(Modifier.height(insetBottom + 40.dp))
            }
        }

        val menuEye = eyes.getOrNull(menuFor)
        if (menuEye != null) {
            val target = menuFor
            StudioActionSheet(
                title = "Eye ${target + 1}",
                insetBottom = insetBottom,
                actions = listOfNotNull(
                    StudioAction("Delete this eye", LucideR.drawable.lucide_ic_trash_2, destructive = true) {
                        eyes = eyes.filterIndexed { i, _ -> i != target }
                        selected = -1
                    },
                    if (eyes.size > 1) StudioAction("Delete every eye", LucideR.drawable.lucide_ic_eye_off, destructive = true) {
                        eyes = emptyList()
                        selected = -1
                    } else null,
                    if (eyes.size < SkinStudio.MAX_EYES) StudioAction("Duplicate", LucideR.drawable.lucide_ic_copy) {
                        eyes = eyes + menuEye.copy(fwd = (menuEye.fwd - 3f).coerceIn(-20f, 20f))
                        selected = eyes.lastIndex
                    } else null,
                    if (eyes.size < SkinStudio.MAX_EYES && abs(menuEye.side) > 0.6f && twin(eyes, target) < 0)
                        StudioAction("Mirror to the other side", LucideR.drawable.lucide_ic_flip_horizontal_2) {
                            eyes = eyes + menuEye.copy(side = -menuEye.side)
                            selected = eyes.lastIndex
                        } else null,
                ),
                onDismiss = { menuFor = -1 },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.1.sp,
        color = Wyrm.Quiet, modifier = Modifier.padding(bottom = 8.dp))
}

/** The pupil shapes, drawn in this eye's colours. */
@Composable
private fun PupilShapes(eye: StudioEye, onPick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(StudioArt.PUPIL_SHAPES) { index, name ->
            val chosen = eye.pupilShape == index
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(eye.whiteColor))
                        .border(if (chosen) 2.5.dp else 1.dp, if (chosen) Wyrm.Ink else Wyrm.Rule, CircleShape)
                        .clickableNoRipple { onPick(index) }
                        .padding(9.dp),
                ) {
                    val picture = StudioArt.pupilImage(eye.copy(pupilShape = index))
                    Canvas(Modifier.fillMaxSize()) { drawImageInto(picture, 0f, 0f, size.width, size.height) }
                }
                Text(name, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp,
                    color = if (chosen) Wyrm.Ink else Wyrm.Quiet, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
