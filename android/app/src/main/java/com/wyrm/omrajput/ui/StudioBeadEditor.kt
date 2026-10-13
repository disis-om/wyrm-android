package com.wyrm.omrajput.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The bead editor (OM, 2026-10-12): the bead as big as the screen allows, to
 * paint on with a brush, an eraser, a fill and a colour picker, with mirror
 * or radial symmetry and undo; and its look: a finish, colour, hue,
 * saturation, brightness, opacity, smoothness and detail. A strip of the bead
 * along a snake shows the finished bead as the arena will draw it. Nothing is
 * kept until Done; the X throws the changes away.
 */

private enum class BrushTool { BRUSH, ERASER, FILL, PICKER }
private enum class Symmetry(val label: String) { OFF("Off"), MIRROR("Mirror"), QUAD("4 way"), RADIAL("6 way") }

/** The painting's resolution: the bead's disc square. */
private const val PAINT_SIZE = 256

@Composable
internal fun StudioBeadEditor(
    beadId: String,
    skin: StudioSkin?,
    insetTop: Dp,
    insetBottom: Dp,
    onDone: (saved: Boolean) -> Unit,
) {
    val textures by rememberSkinTextures()
    val start = remember(beadId) { SkinStudio.bead(beadId) ?: StudioBead(beadId) }
    var draft by remember(beadId) { mutableStateOf(start) }
    // The painting being worked on: a private copy until Done.
    val paint = remember(beadId) {
        SkinStudio.paint(beadId)?.takeIf { start.painted }?.copy(Bitmap.Config.ARGB_8888, true)
            ?.let { if (it.width == PAINT_SIZE && it.height == PAINT_SIZE) it else Bitmap.createScaledBitmap(it, PAINT_SIZE, PAINT_SIZE, true) }
            ?: Bitmap.createBitmap(PAINT_SIZE, PAINT_SIZE, Bitmap.Config.ARGB_8888)
    }
    val paintImage = remember(paint) { paint.asImageBitmap() }
    var paintChanged by remember(beadId) { mutableStateOf(false) }
    var paintTick by remember { mutableIntStateOf(0) }
    /** Bumped when a stroke ends (or a fill, clear, undo): the finished bead redraws then. */
    var strokeDone by remember { mutableIntStateOf(0) }
    val undo = remember(beadId) { mutableStateListOf<Bitmap>() }
    val redo = remember(beadId) { mutableStateListOf<Bitmap>() }
    DisposableEffect(beadId) { onDispose { undo.forEach { it.recycle() }; redo.forEach { it.recycle() } } }

    var tool by remember { mutableStateOf(BrushTool.BRUSH) }
    var symmetry by remember { mutableStateOf(Symmetry.OFF) }
    var brushColor by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var brushSize by remember { mutableStateOf(18f) }
    var brushSoft by remember { mutableStateOf(0.2f) }
    var panel by remember { mutableIntStateOf(0) }

    // The finish alone, unadjusted, as large as the canvas needs.
    val base = remember(draft.finish, draft.color, draft.detail, textures, beadId) {
        StudioArt.renderBase(draft, 512, textures).asImageBitmap()
    }
    val matrix = remember(draft.hue, draft.saturation, draft.brightness) {
        StudioArt.composeMatrix(draft.hue, draft.saturation, draft.brightness)
    }
    // The finished bead as the arena draws it (painting, edge and all).
    val finished = remember(draft, strokeDone, textures) {
        StudioArt.renderBead(draft.copy(painted = true), paint, 160, textures).asImageBitmap()
    }

    fun snapshot() {
        undo.add(paint.copy(Bitmap.Config.ARGB_8888, true))
        if (undo.size > 25) undo.removeAt(0).recycle()
        redo.forEach { it.recycle() }
        redo.clear()
    }
    fun restore(from: Bitmap) {
        val c = android.graphics.Canvas(paint)
        c.drawColor(0, PorterDuff.Mode.CLEAR)
        c.drawBitmap(from, 0f, 0f, null)
        paintChanged = true
        paintTick++
        strokeDone++
    }

    fun brush(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = brushSize
        if (tool == BrushTool.ERASER) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            color = brushColor
        }
        if (brushSoft > 0.02f) maskFilter = BlurMaskFilter(brushSize * brushSoft * 0.6f + 0.5f, BlurMaskFilter.Blur.NORMAL)
    }

    /** Every place a point lands with the symmetry on. */
    fun mirrored(x: Float, y: Float): List<Pair<Float, Float>> {
        val h = PAINT_SIZE / 2f
        return when (symmetry) {
            Symmetry.OFF -> listOf(x to y)
            Symmetry.MIRROR -> listOf(x to y, x to (PAINT_SIZE - y))
            Symmetry.QUAD -> listOf(x to y, (PAINT_SIZE - x) to y, x to (PAINT_SIZE - y), (PAINT_SIZE - x) to (PAINT_SIZE - y))
            Symmetry.RADIAL -> (0 until 6).map { k ->
                val a = k * PI.toFloat() / 3f
                val dx = x - h
                val dy = y - h
                (h + dx * cos(a) - dy * sin(a)) to (h + dx * sin(a) + dy * cos(a))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Wyrm.Paper)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }, // nothing falls through
    ) {
        Column(Modifier.fillMaxSize().padding(top = insetTop)) {
            // Top bar.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // The working painting is a copy: closing simply drops it.
                StudioRoundButton(LucideR.drawable.lucide_ic_x, size = 38.dp) { onDone(false) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("BEAD", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 1.2.sp, color = Wyrm.Quiet)
                    Text(StudioArt.FINISHES.getOrElse(draft.finish) { "Bead" }, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Wyrm.Ink)
                }
                StudioRoundButton(LucideR.drawable.lucide_ic_undo_2, enabled = undo.isNotEmpty(), size = 38.dp) {
                    redo.add(paint.copy(Bitmap.Config.ARGB_8888, true))
                    val last = undo.removeAt(undo.lastIndex)
                    restore(last); last.recycle()
                }
                Spacer(Modifier.size(8.dp))
                StudioRoundButton(LucideR.drawable.lucide_ic_redo_2, enabled = redo.isNotEmpty(), size = 38.dp) {
                    undo.add(paint.copy(Bitmap.Config.ARGB_8888, true))
                    val next = redo.removeAt(redo.lastIndex)
                    restore(next); next.recycle()
                }
                Spacer(Modifier.size(10.dp))
                StudioCapsule("Done", icon = LucideR.drawable.lucide_ic_check) {
                    SkinStudio.updateBead(draft.copy(painted = start.painted))
                    if (paintChanged) {
                        val empty = paint.sameAs(Bitmap.createBitmap(PAINT_SIZE, PAINT_SIZE, Bitmap.Config.ARGB_8888))
                        SkinStudio.savePaint(beadId, if (empty) null else paint)
                    }
                    onDone(true)
                }
            }
            // The bead along a snake, as the arena draws it.
            Canvas(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp)) {
                val bead = size.height * 0.8f
                val step = bead * STUDIO_BEAD_STEP
                val count = (size.width / step).toInt().coerceAtLeast(2)
                for (i in count - 1 downTo 0) {
                    val x = size.width - bead / 2 - i * step
                    val y = size.height / 2 + sin(i * 0.5f) * bead * 0.08f
                    drawImageInto(finished, x - bead / 2, y - bead / 2, bead, bead)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // The canvas.
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, 360.dp)
                    Box(Modifier.size(side).aspectRatio(1f)) {
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(tool, symmetry, brushColor, brushSize, brushSoft) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val s = size.width.toFloat()
                                        val rd = s / 2f * StudioArt.DISC
                                        val left = s / 2f - rd
                                        fun toPaint(o: Offset) = Offset((o.x - left) / (2 * rd) * PAINT_SIZE, (o.y - left) / (2 * rd) * PAINT_SIZE)
                                        val first = toPaint(down.position)
                                        when (tool) {
                                            BrushTool.PICKER -> {
                                                val px = first.x.roundToInt().coerceIn(0, PAINT_SIZE - 1)
                                                val py = first.y.roundToInt().coerceIn(0, PAINT_SIZE - 1)
                                                val painted = paint.getPixel(px, py)
                                                brushColor = if ((painted ushr 24) > 40) painted or (0xFF shl 24) else draft.color
                                                tool = BrushTool.BRUSH
                                                down.consume()
                                            }
                                            BrushTool.FILL -> {
                                                snapshot()
                                                android.graphics.Canvas(paint).drawColor(brushColor)
                                                paintChanged = true
                                                paintTick++
                                                strokeDone++
                                                down.consume()
                                            }
                                            else -> {
                                                snapshot()
                                                val c = android.graphics.Canvas(paint)
                                                val p = brush()
                                                var last = first
                                                var mid = first
                                                // A dot for a tap.
                                                val dot = Paint(p).apply { style = Paint.Style.FILL }
                                                mirrored(first.x, first.y).forEach { (x, y) -> c.drawCircle(x, y, brushSize / 2f, dot) }
                                                paintChanged = true
                                                paintTick++
                                                down.consume()
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!change.pressed) break
                                                    if (change.positionChange() == Offset.Zero) continue
                                                    val now = toPaint(change.position)
                                                    val nextMid = Offset((last.x + now.x) / 2f, (last.y + now.y) / 2f)
                                                    // Quadratic through the midpoints: a smooth line from a jittery finger.
                                                    val lastSet = mirrored(last.x, last.y)
                                                    val fromSet = mirrored(mid.x, mid.y)
                                                    val toSet = mirrored(nextMid.x, nextMid.y)
                                                    for (k in lastSet.indices) {
                                                        val path = android.graphics.Path()
                                                        path.moveTo(fromSet[k].first, fromSet[k].second)
                                                        path.quadTo(lastSet[k].first, lastSet[k].second, toSet[k].first, toSet[k].second)
                                                        c.drawPath(path, p)
                                                    }
                                                    mid = nextMid
                                                    last = now
                                                    paintTick++
                                                    change.consume()
                                                }
                                                strokeDone++
                                            }
                                        }
                                    }
                                },
                        ) {
                            @Suppress("UNUSED_EXPRESSION") paintTick
                            val s = size.width
                            val rd = s / 2f * StudioArt.DISC
                            val left = s / 2f - rd
                            // A checkerboard shows through where the bead is see-through.
                            val cell = s / 16f
                            for (yy in 0 until 16) for (xx in 0 until 16) {
                                drawRect(if ((xx + yy) % 2 == 0) Wyrm.Card else Wyrm.Rule.copy(alpha = 0.5f),
                                    topLeft = Offset(xx * cell, yy * cell), size = androidx.compose.ui.geometry.Size(cell, cell))
                            }
                            val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(left, left, left + 2 * rd, left + 2 * rd)) }
                            clipPath(disc) {
                                drawImage(base, srcOffset = IntOffset.Zero, srcSize = IntSize(base.width, base.height),
                                    dstOffset = IntOffset(0, 0), dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                                    alpha = draft.opacity, colorFilter = ColorFilter.colorMatrix(matrix))
                                drawImage(paintImage, srcOffset = IntOffset.Zero, srcSize = IntSize(PAINT_SIZE, PAINT_SIZE),
                                    dstOffset = IntOffset(left.roundToInt(), left.roundToInt()),
                                    dstSize = IntSize((2 * rd).roundToInt(), (2 * rd).roundToInt()),
                                    alpha = draft.opacity, colorFilter = ColorFilter.colorMatrix(matrix))
                            }
                            // The part a body shows: the front (+x) half; the back is under the next bead.
                            drawArc(Wyrm.Ink.copy(alpha = 0.18f), startAngle = 90f, sweepAngle = 180f, useCenter = false,
                                topLeft = Offset(left, left), size = androidx.compose.ui.geometry.Size(2 * rd, 2 * rd),
                                style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                            drawCircle(Wyrm.Rule, radius = rd, center = Offset(s / 2, s / 2), style = Stroke(1.5f))
                            when (symmetry) {
                                Symmetry.OFF -> Unit
                                else -> {
                                    val guide = Wyrm.Link.copy(alpha = 0.35f)
                                    drawLine(guide, Offset(left, s / 2), Offset(left + 2 * rd, s / 2), 1.5f)
                                    if (symmetry != Symmetry.MIRROR) drawLine(guide, Offset(s / 2, left), Offset(s / 2, left + 2 * rd), 1.5f)
                                }
                            }
                        }
                    }
                }
                Text("The front half (right) is what shows along the body.", fontFamily = Wyrm.Body, fontSize = 11.sp,
                    color = Wyrm.Quiet, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    PaperSegmented(options = listOf("Paint", "Look"), selected = panel, onSelect = { panel = it })
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 20.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (panel == 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioRoundButton(LucideR.drawable.lucide_ic_paintbrush, active = tool == BrushTool.BRUSH) { tool = BrushTool.BRUSH }
                            StudioRoundButton(LucideR.drawable.lucide_ic_eraser, active = tool == BrushTool.ERASER) { tool = BrushTool.ERASER }
                            StudioRoundButton(LucideR.drawable.lucide_ic_paint_bucket, active = tool == BrushTool.FILL) { tool = BrushTool.FILL }
                            StudioRoundButton(LucideR.drawable.lucide_ic_pipette, active = tool == BrushTool.PICKER) { tool = BrushTool.PICKER }
                            Spacer(Modifier.weight(1f))
                            StudioRoundButton(LucideR.drawable.lucide_ic_trash_2) {
                                snapshot()
                                android.graphics.Canvas(paint).drawColor(0, PorterDuff.Mode.CLEAR)
                                paintChanged = true
                                paintTick++
                                strokeDone++
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Symmetry.entries.forEach { mode ->
                                StudioCapsule(mode.label, icon = if (mode == Symmetry.OFF) null else LucideR.drawable.lucide_ic_flip_horizontal_2,
                                    filled = symmetry == mode) { symmetry = mode }
                            }
                        }
                        StudioColorPicker(brushColor) { brushColor = it; if (tool == BrushTool.ERASER || tool == BrushTool.PICKER) tool = BrushTool.BRUSH }
                        StudioValueSlider("Brush size", brushSize, 2f..80f, { "${it.roundToInt()}" }) { brushSize = it }
                        StudioValueSlider("Brush softness", brushSoft, 0f..1f, ::percent) { brushSoft = it }
                    } else {
                        Text("Finish", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Wyrm.Ink)
                        FinishGrid(draft, textures) { draft = draft.copy(finish = it) }
                        Text("Colour", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Wyrm.Ink)
                        StudioColorPicker(draft.color) { draft = draft.copy(color = it) }
                        StudioValueSlider("Hue", draft.hue, -180f..180f, { "${it.roundToInt()}°" }) { draft = draft.copy(hue = it) }
                        StudioValueSlider("Saturation", draft.saturation, 0f..2f, ::percent) { draft = draft.copy(saturation = it) }
                        StudioValueSlider("Brightness", draft.brightness, 0f..2f, ::percent) { draft = draft.copy(brightness = it) }
                        StudioValueSlider("Opacity", draft.opacity, 0.15f..1f, ::percent) { draft = draft.copy(opacity = it) }
                        StudioValueSlider("Smoothness", draft.smoothness, 0f..1f, ::percent) { draft = draft.copy(smoothness = it) }
                        StudioValueSlider("Detail", draft.detail, 0f..1f, ::percent) { draft = draft.copy(detail = it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioCapsule("Reset look", icon = LucideR.drawable.lucide_ic_rotate_ccw, filled = false) {
                                draft = draft.copy(hue = 0f, saturation = 1f, brightness = 1f, opacity = 1f, smoothness = 0f, detail = 0.7f)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(insetBottom + 40.dp))
            }
        }
    }
}

/** Every finish, drawn in this bead's own colour. */
@Composable
private fun FinishGrid(bead: StudioBead, textures: SkinTextures?, onPick: (Int) -> Unit) {
    val previews = remember(bead.color, bead.detail, textures) {
        StudioArt.FINISHES.indices.map { finish ->
            StudioArt.renderBase(bead.copy(finish = finish), 96, textures).asImageBitmap()
        }
    }
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until (previews.size + columns - 1) / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (index < previews.size) {
                            val chosen = bead.finish == index
                            Box(
                                Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .border(if (chosen) 2.5.dp else 1.dp, if (chosen) Wyrm.Ink else Wyrm.Rule, CircleShape)
                                    .clickableNoRipple { onPick(index) }
                                    .padding(4.dp),
                            ) {
                                val image: ImageBitmap = previews[index]
                                Canvas(Modifier.fillMaxSize()) { drawImageInto(image, 0f, 0f, size.width, size.height) }
                            }
                            Text(StudioArt.FINISHES[index], fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp,
                                color = if (chosen) Wyrm.Ink else Wyrm.Quiet, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
