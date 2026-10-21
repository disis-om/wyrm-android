package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Trails photo cropper (OM, 2026-10-18), also Share run's screenshot: the whole picture on the stage with
 * a crop box over it, as the usual phone croppers have it. Outside the box is
 * dimmed, the rule of thirds is inside, and the four corners and four edges
 * resize it (Free: any shape from 1:2 to 2:1; the other choices keep their
 * shape). One finger inside the box moves it, two fingers zoom it. The box is
 * written straight into the draft's frame shape, zoom and pan, so the editor,
 * the overlays and the export all keep working on the same numbers.
 */

/** The crop box as fractions of the photo (0..1). */
internal data class CropFrac(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) / 2
    val cy: Float get() = (t + b) / 2
}

internal object PhotoCrop {
    /** The box's longer side never goes under a fifth of the photo: the editor's zoom stops at 5x. */
    const val MIN_FRACTION = 0.2f
    const val FREE_MIN_RATIO = 0.5f
    const val FREE_MAX_RATIO = 2f
    val FULL = CropFrac(0f, 0f, 1f, 1f)

    /** "Reset" under the stage puts the box back to the largest of its shape. */
    var resetTick by mutableIntStateOf(0)

    /** The picture being cropped: the photo, or Share run's screenshot (OM, 2026-10-18). */
    fun source(draft: StudioDraft): android.graphics.Bitmap? = draft.image ?: draft.shot

    /** The part of the picture a canvas of [w] x [h] shows now. */
    fun fromDraft(draft: StudioDraft, w: Float, h: Float): CropFrac {
        if (source(draft) == null || w <= 0f || h <= 0f) return FULL
        val r = if (draft.image != null) draft.photoRect(w, h) else draft.shotRect(w, h)
        if (r.width() <= 0f || r.height() <= 0f) return FULL
        val l = (-r.left / r.width()).coerceIn(0f, 1f)
        val t = (-r.top / r.height()).coerceIn(0f, 1f)
        val rr = ((w - r.left) / r.width()).coerceIn(0f, 1f)
        val b = ((h - r.top) / r.height()).coerceIn(0f, 1f)
        return if (rr - l > 0.01f && b - t > 0.01f) CropFrac(l, t, rr, b) else FULL
    }

    /** The largest box whose output is [ratio] wide per 1 high, around (cx, cy). */
    fun largest(ratio: Float, k: Float, cx: Float = 0.5f, cy: Float = 0.5f): CropFrac {
        val a = ratio / k // width / height in photo fractions
        val w = if (a >= 1f) 1f else a
        val h = if (a >= 1f) 1f / a else 1f
        val l = (cx - w / 2).coerceIn(0f, 1f - w)
        val t = (cy - h / 2).coerceIn(0f, 1f - h)
        return CropFrac(l, t, l + w, t + h)
    }

    /**
     * Writes [c] into the draft: the frame's shape (Free only), then the zoom and
     * pan that make a canvas of that shape show exactly the box. The canvas size
     * follows the editor's own layout (TrailStudio: min(width / ratio, height)).
     */
    fun apply(draft: StudioDraft, c: CropFrac, widthPx: Float, maxHeightPx: Float) {
        val bitmap = source(draft) ?: return
        val iw = bitmap.width.toFloat()
        val ih = bitmap.height.toFloat()
        if (iw <= 0f || ih <= 0f || c.w <= 0f || c.h <= 0f) return
        if (draft.aspect == StudioAspect.FREE) {
            draft.freeRatio = (c.w * iw / (c.h * ih)).coerceIn(FREE_MIN_RATIO, FREE_MAX_RATIO)
        }
        val ratio = draft.ratio
        val h = min(widthPx / ratio, maxHeightPx)
        val w = h * ratio
        val fill = max(w / iw, h / ih)
        val s = min(w / (iw * fill * c.w), h / (ih * fill * c.h)).coerceIn(1f, 5f)
        val pw = iw * fill * s
        val ph = ih * fill * s
        val offset = Offset(pw * (0.5f - c.cx), ph * (0.5f - c.cy))
        if (draft.image != null) {
            draft.photoScale = s
            draft.photoOffset = offset
            draft.clampOffset(w, h)
        } else {
            // The screenshot covers the canvas the same way (shotRect).
            draft.shotScale = s
            draft.shotOffset = offset
        }
    }

    // Handles: what a finger took hold of.
    const val NONE = -1
    const val INSIDE = 0
    const val LEFT = 1
    const val TOP = 2
    const val RIGHT = 3
    const val BOTTOM = 4
    const val TOP_LEFT = 5
    const val TOP_RIGHT = 6
    const val BOTTOM_RIGHT = 7
    const val BOTTOM_LEFT = 8

    private fun hasLeft(h: Int) = h == LEFT || h == TOP_LEFT || h == BOTTOM_LEFT
    private fun hasRight(h: Int) = h == RIGHT || h == TOP_RIGHT || h == BOTTOM_RIGHT
    private fun hasTop(h: Int) = h == TOP || h == TOP_LEFT || h == TOP_RIGHT
    private fun hasBottom(h: Int) = h == BOTTOM || h == BOTTOM_LEFT || h == BOTTOM_RIGHT

    /** The box moved inside the photo by (dx, dy) fractions. */
    fun moved(c: CropFrac, dx: Float, dy: Float): CropFrac {
        val l = (c.l + dx).coerceIn(0f, 1f - c.w)
        val t = (c.t + dy).coerceIn(0f, 1f - c.h)
        return CropFrac(l, t, l + c.w, t + c.h)
    }

    /**
     * The box after a handle moved by (dx, dy) photo fractions. [k] is the photo's
     * width / height, [locked] the output shape (null = Free), [minW]/[minH] the
     * smallest box a finger can still hold, in fractions.
     */
    fun resized(c: CropFrac, handle: Int, dx: Float, dy: Float, k: Float, locked: Float?, minW: Float, minH: Float): CropFrac {
        if (handle == INSIDE) return moved(c, dx, dy)
        return if (locked == null) resizedFree(c, handle, dx, dy, k, minW, minH)
        else resizedLocked(c, handle, dx, dy, locked / k, minW, minH)
    }

    private fun resizedFree(c: CropFrac, handle: Int, dx: Float, dy: Float, k: Float, minW: Float, minH: Float): CropFrac {
        var l = c.l
        var t = c.t
        var r = c.r
        var b = c.b
        if (hasLeft(handle)) l = (l + dx).coerceIn(0f, r - minW)
        if (hasRight(handle)) r = (r + dx).coerceIn(l + minW, 1f)
        if (hasTop(handle)) t = (t + dy).coerceIn(0f, b - minH)
        if (hasBottom(handle)) b = (b + dy).coerceIn(t + minH, 1f)
        // The output stays between 1:2 and 2:1: the side that moved gives way.
        val widthMoved = hasLeft(handle) || hasRight(handle)
        val ratio = (r - l) * k / (b - t)
        if (ratio > FREE_MAX_RATIO || ratio < FREE_MIN_RATIO) {
            val target = ratio.coerceIn(FREE_MIN_RATIO, FREE_MAX_RATIO)
            if (widthMoved) {
                val nw = min(target * (b - t) / k, 1f)
                if (hasLeft(handle)) l = (r - nw).coerceAtLeast(0f) else r = (l + nw).coerceAtMost(1f)
            } else {
                val nh = min((r - l) * k / target, 1f)
                if (hasTop(handle)) t = (b - nh).coerceAtLeast(0f) else b = (t + nh).coerceAtMost(1f)
            }
        }
        val next = CropFrac(l, t, r, b)
        val nextRatio = next.w * k / next.h
        val ok = max(next.w, next.h) >= MIN_FRACTION - 1e-4f &&
            nextRatio in (FREE_MIN_RATIO - 0.01f)..(FREE_MAX_RATIO + 0.01f)
        return if (ok) next else c
    }

    /** [a] is the box's width / height in photo fractions. */
    private fun resizedLocked(c: CropFrac, handle: Int, dx: Float, dy: Float, a: Float, minW: Float, minH: Float): CropFrac {
        // The smallest width: a finger can hold it, and the zoom stays <= 5x.
        val minWidth = maxOf(minW, minH * a, if (a >= 1f) MIN_FRACTION else MIN_FRACTION * a)
        val corner = handle >= TOP_LEFT
        return if (corner) {
            // The opposite corner stays put; the larger of the two pulls wins.
            val ax = if (hasLeft(handle)) c.r else c.l
            val ay = if (hasTop(handle)) c.b else c.t
            val fromX = c.w + if (hasLeft(handle)) -dx else dx
            val fromY = (c.h + if (hasTop(handle)) -dy else dy) * a
            var nw = if (abs(fromX - c.w) >= abs(fromY - c.w)) fromX else fromY
            val maxW = min(if (hasLeft(handle)) ax else 1f - ax, (if (hasTop(handle)) ay else 1f - ay) * a)
            nw = nw.coerceIn(min(minWidth, maxW), maxW)
            val nh = nw / a
            val l = if (hasLeft(handle)) ax - nw else ax
            val t = if (hasTop(handle)) ay - nh else ay
            CropFrac(l, t, l + nw, t + nh)
        } else {
            // An edge: that side moves, the other axis grows around its middle.
            val horizontal = handle == LEFT || handle == RIGHT
            var nw = if (horizontal) c.w + (if (handle == LEFT) -dx else dx) else (c.h + (if (handle == TOP) -dy else dy)) * a
            val maxW = if (horizontal) {
                min(if (handle == LEFT) c.r else 1f - c.l, a) // a: the height may use the whole photo
            } else {
                min(1f, (if (handle == TOP) c.b else 1f - c.t) * a)
            }
            nw = nw.coerceIn(min(minWidth, maxW), maxW)
            val nh = nw / a
            if (horizontal) {
                val l = if (handle == LEFT) c.r - nw else c.l
                val t = (c.cy - nh / 2).coerceIn(0f, 1f - nh)
                CropFrac(l, t, l + nw, t + nh)
            } else {
                val t = if (handle == TOP) c.b - nh else c.t
                val l = (c.cx - nw / 2).coerceIn(0f, 1f - nw)
                CropFrac(l, t, l + nw, t + nh)
            }
        }
    }

    /** Two fingers: the box grows or shrinks around its middle ([zoom] > 1 = zoom in = smaller box). */
    fun pinched(c: CropFrac, zoom: Float, k: Float, locked: Float?, minW: Float, minH: Float): CropFrac {
        if (zoom <= 0f) return c
        val a = if (locked != null) locked / k else c.w / c.h
        val minWidth = maxOf(minW, minH * a, if (a >= 1f) MIN_FRACTION else MIN_FRACTION * a)
        val maxWidth = min(1f, a)
        val nw = (c.w / zoom).coerceIn(min(minWidth, maxWidth), maxWidth)
        val nh = nw / a
        val l = (c.cx - nw / 2).coerceIn(0f, 1f - nw)
        val t = (c.cy - nh / 2).coerceIn(0f, 1f - nh)
        return CropFrac(l, t, l + nw, t + nh)
    }
}

/**
 * The crop stage. [canvasW] x [canvasH] is the editor canvas when cropping
 * starts (to read the box from the draft); [widthPx] / [maxHeightPx] are the
 * editor's limits for the canvas, so [PhotoCrop.apply] lays it out the same way.
 */
@Composable
internal fun TrailCropStage(
    draft: StudioDraft, canvasW: Float, canvasH: Float, widthPx: Float, maxHeightPx: Float, modifier: Modifier = Modifier,
    /** Share run / Share skin (OM, 2026-10-18): the whole board to crop, the box to start from, and where each box goes. */
    board: android.graphics.Bitmap? = null,
    start: CropFrac? = null,
    onSet: ((CropFrac) -> Unit)? = null,
) {
    val bitmap = board ?: PhotoCrop.source(draft) ?: return
    val density = LocalDensity.current
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val k = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
    var crop by remember(bitmap) { mutableStateOf(start ?: PhotoCrop.fromDraft(draft, canvasW, canvasH)) }
    var holding by remember { mutableStateOf(false) }
    var lastAspect by remember(bitmap) { mutableStateOf(draft.aspect) }
    var lastReset by remember(bitmap) { mutableIntStateOf(PhotoCrop.resetTick) }

    fun locked(): Float? = if (draft.aspect == StudioAspect.FREE) null else draft.ratio
    fun set(next: CropFrac) {
        crop = next
        if (onSet != null) onSet(next) else PhotoCrop.apply(draft, next, widthPx, maxHeightPx)
    }

    // A new shape from the chips, or Reset: the largest box of that shape.
    LaunchedEffect(draft.aspect, PhotoCrop.resetTick) {
        val reset = PhotoCrop.resetTick != lastReset
        if (draft.aspect == lastAspect && !reset) return@LaunchedEffect
        lastAspect = draft.aspect
        lastReset = PhotoCrop.resetTick
        val shape = locked()
        set(
            when {
                shape != null -> PhotoCrop.largest(shape, k, if (reset) 0.5f else crop.cx, if (reset) 0.5f else crop.cy)
                reset -> PhotoCrop.FULL.let { full ->
                    // Free reset: the whole photo, inside 1:2..2:1.
                    if (k in PhotoCrop.FREE_MIN_RATIO..PhotoCrop.FREE_MAX_RATIO) full
                    else PhotoCrop.largest(k.coerceIn(PhotoCrop.FREE_MIN_RATIO, PhotoCrop.FREE_MAX_RATIO), k)
                }
                else -> crop
            },
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val pad = with(density) { 28.dp.toPx() }
        val stageW = constraints.maxWidth.toFloat()
        val stageH = constraints.maxHeight.toFloat()
        val areaW = (stageW - pad * 2).coerceAtLeast(1f)
        val areaH = (stageH - pad * 2).coerceAtLeast(1f)
        val fit = min(areaW / bitmap.width, areaH / bitmap.height)
        val dw = bitmap.width * fit
        val dh = bitmap.height * fit
        val dl = (stageW - dw) / 2
        val dt = (stageH - dh) / 2
        val minPx = with(density) { 64.dp.toPx() }
        val minW = (minPx / dw).coerceAtMost(1f)
        val minH = (minPx / dh).coerceAtMost(1f)
        val grab = with(density) { 30.dp.toPx() }

        fun boxPx(c: CropFrac) = floatArrayOf(dl + c.l * dw, dt + c.t * dh, dl + c.r * dw, dt + c.b * dh)

        fun hit(p: Offset): Int {
            val (l, t, r, b) = boxPx(crop).let { arrayOf(it[0], it[1], it[2], it[3]) }
            val nearL = abs(p.x - l) < grab
            val nearR = abs(p.x - r) < grab
            val nearT = abs(p.y - t) < grab
            val nearB = abs(p.y - b) < grab
            val inX = p.x > l - grab && p.x < r + grab
            val inY = p.y > t - grab && p.y < b + grab
            return when {
                nearL && nearT -> PhotoCrop.TOP_LEFT
                nearR && nearT -> PhotoCrop.TOP_RIGHT
                nearR && nearB -> PhotoCrop.BOTTOM_RIGHT
                nearL && nearB -> PhotoCrop.BOTTOM_LEFT
                nearL && inY -> PhotoCrop.LEFT
                nearR && inY -> PhotoCrop.RIGHT
                nearT && inX -> PhotoCrop.TOP
                nearB && inX -> PhotoCrop.BOTTOM
                p.x in l..r && p.y in t..b -> PhotoCrop.INSIDE
                else -> PhotoCrop.NONE
            }
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap, dw, dh, draft.aspect) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var handle = hit(down.position)
                    holding = handle != PhotoCrop.NONE
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // Two fingers: zoom the box around its middle and carry it.
                            handle = PhotoCrop.INSIDE
                            holding = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            var next = PhotoCrop.pinched(crop, zoom, k, locked(), minW, minH)
                            next = PhotoCrop.moved(next, pan.x / dw, pan.y / dh)
                            if (next != crop) set(next)
                        } else if (handle != PhotoCrop.NONE) {
                            val change = pressed.firstOrNull() ?: event.changes.first()
                            val d = change.positionChange()
                            if (d != Offset.Zero) {
                                val next = PhotoCrop.resized(crop, handle, d.x / dw, d.y / dh, k, locked(), minW, minH)
                                if (next != crop) set(next)
                            }
                        }
                        event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                    } while (event.changes.any { it.pressed })
                    holding = false
                }
            },
        ) {
            // The editor's own dark stage (a photo makes it black), over the canvas below.
            drawRect(Color.Black)
            // Looks belong to a photo; the screenshot is drawn as it is.
            val filter = if (board != null || draft.image == null || TrailLooks.isIdentity(draft.look, draft.adjust)) null else TrailLooks.composeFilter(draft.look, draft.adjust)
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(dl.roundToInt(), dt.roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                colorFilter = filter,
                filterQuality = FilterQuality.Medium,
            )
            val box = boxPx(crop)
            val l = box[0]
            val t = box[1]
            val r = box[2]
            val b = box[3]
            // Outside the box, dimmed (on the photo only).
            val dim = Color.Black.copy(alpha = if (holding) 0.45f else 0.6f)
            drawRect(dim, Offset(dl, dt), Size(dw, t - dt))
            drawRect(dim, Offset(dl, b), Size(dw, dt + dh - b))
            drawRect(dim, Offset(dl, t), Size(l - dl, b - t))
            drawRect(dim, Offset(r, t), Size(dl + dw - r, b - t))
            // The rule of thirds; brighter while a finger is on the box.
            val line = Color.White.copy(alpha = if (holding) 0.8f else 0.4f)
            val thin = 1.dp.toPx()
            for (i in 1..2) {
                val x = l + (r - l) * i / 3
                val y = t + (b - t) * i / 3
                drawLine(line, Offset(x, t), Offset(x, b), thin)
                drawLine(line, Offset(l, y), Offset(r, y), thin)
            }
            // The frame.
            drawRect(Color.White.copy(alpha = 0.9f), Offset(l, t), Size(r - l, b - t), style = Stroke(1.5.dp.toPx()))
            // Corner brackets, just outside the frame.
            val arm = min(22.dp.toPx(), min(r - l, b - t) / 2)
            val bar = 3.dp.toPx()
            val o = bar / 2
            val white = Color.White
            fun bracket(x: Float, y: Float, sx: Float, sy: Float) {
                drawLine(white, Offset(x - sx * o, y - sy * o), Offset(x + sx * arm, y - sy * o), bar, cap = StrokeCap.Square)
                drawLine(white, Offset(x - sx * o, y - sy * o), Offset(x - sx * o, y + sy * arm), bar, cap = StrokeCap.Square)
            }
            bracket(l, t, 1f, 1f)
            bracket(r, t, -1f, 1f)
            bracket(r, b, -1f, -1f)
            bracket(l, b, 1f, -1f)
            // Edge handles in the middle of each side.
            val half = min(12.dp.toPx(), min(r - l, b - t) / 4)
            drawLine(white, Offset((l + r) / 2 - half, t - o), Offset((l + r) / 2 + half, t - o), bar, cap = StrokeCap.Round)
            drawLine(white, Offset((l + r) / 2 - half, b + o), Offset((l + r) / 2 + half, b + o), bar, cap = StrokeCap.Round)
            drawLine(white, Offset(l - o, (t + b) / 2 - half), Offset(l - o, (t + b) / 2 + half), bar, cap = StrokeCap.Round)
            drawLine(white, Offset(r + o, (t + b) / 2 - half), Offset(r + o, (t + b) / 2 + half), bar, cap = StrokeCap.Round)
        }
    }
}
