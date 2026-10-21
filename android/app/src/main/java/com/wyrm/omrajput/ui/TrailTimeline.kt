@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wyrm.omrajput.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import com.composables.icons.lucide.R as LucideR
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The video editor's timeline (OM, 2026-10-17): "professional, like Instagram's
 * editor". A trail video is one clip from the phone cut into pieces:
 *
 *   - pieces (`TrailSegment`) in order, each a stretch of the clip; Split cuts
 *     the piece under the playhead in two, Delete drops one (so a middle part
 *     can go), and the ends of the chosen piece are dragged to trim it. The
 *     whole video is at most 30 s, a piece at least half a second;
 *   - a transition after each piece but the last: None, Fade (through black),
 *     Flash (through white) or Zoom (a punch in), 0.2 s on each side of the cut;
 *   - a crop (`TrailCrop`): a shape (the clip's own, 9:16, 4:5, 1:1, 16:9) and
 *     where it sits on the clip, dragged and pinched like Instagram's;
 *   - the cover, a time in the finished video, chosen on a strip while the
 *     picture above shows that very frame.
 *
 * The editor previews all of it as it will look; the export
 * (`TrailVideoExport`) makes the same thing with Media3: one clipped item per
 * piece, the crop, the size, the look, the transitions (time-based colour and
 * matrix effects, `TrailTransitionTone`, `TrailTransitionZoom`) and the overlay.
 * Wyrm iOS: `WyrmTrailTimeline.swift`, the same rules.
 */

internal const val TRAIL_SEGMENT_MIN_MS = 500L
/** Each side of a cut: the transition runs this long before it and this long after. */
internal const val TRAIL_TRANSITION_HALF_MS = 200L

internal enum class TrailTransition(val label: String) { NONE("None"), FADE("Fade"), FLASH("Flash"), ZOOM("Zoom") }

/** One piece of the clip, in its own milliseconds, and how it hands over to the next. */
internal data class TrailSegment(val id: Long, val startMs: Long, val endMs: Long, val after: TrailTransition = TrailTransition.NONE) {
    val lengthMs: Long get() = endMs - startMs
}

internal enum class TrailCropAspect(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null), STORY("9:16", 9f / 16f), PORTRAIT("4:5", 0.8f), SQUARE("1:1", 1f), WIDE("16:9", 16f / 9f),
}

/**
 * The crop: a shape, a zoom (1 = the largest such shape that fits the clip)
 * and the middle of what is shown, in 0..1 of the clip's upright frame.
 */
internal data class TrailCrop(val aspect: TrailCropAspect = TrailCropAspect.ORIGINAL, val zoom: Float = 1f, val cx: Float = 0.5f, val cy: Float = 0.5f) {
    fun ratio(clip: TrailClip): Float = aspect.ratio ?: clip.aspect

    /** What is shown, in 0..1 of the clip (left, top, right, bottom). */
    fun rect(clip: TrailClip): RectF {
        val source = clip.aspect
        val target = ratio(clip)
        var w = if (target < source) target / source else 1f
        var h = if (target < source) 1f else source / target
        w /= zoom.coerceAtLeast(1f)
        h /= zoom.coerceAtLeast(1f)
        val left = (cx - w / 2).coerceIn(0f, 1f - w)
        val top = (cy - h / 2).coerceIn(0f, 1f - h)
        return RectF(left, top, left + w, top + h)
    }

    /** The same crop with its middle kept inside the clip. */
    fun clamped(clip: TrailClip): TrailCrop {
        val r = rect(clip)
        return copy(zoom = zoom.coerceIn(1f, 4f), cx = r.centerX(), cy = r.centerY())
    }

    /** A drag of [dx], [dy] canvas pixels on a canvas of [w] x [h], and a pinch by [scale]. */
    fun moved(clip: TrailClip, dx: Float, dy: Float, scale: Float, w: Float, h: Float): TrailCrop {
        val r = rect(clip)
        val next = copy(
            zoom = (zoom * scale).coerceIn(1f, 4f),
            cx = cx - dx / w.coerceAtLeast(1f) * r.width(),
            cy = cy - dy / h.coerceAtLeast(1f) * r.height(),
        )
        return next.clamped(clip)
    }

    val untouched: Boolean get() = aspect == TrailCropAspect.ORIGINAL && zoom <= 1.001f
}

/** Everything the export needs, fixed at the moment Post is tapped. */
internal data class TrailEditSpec(
    val clip: TrailClip,
    val segments: List<TrailSegment>,
    val crop: TrailCrop,
    val muted: Boolean,
    /** In the finished video's own time. */
    val coverMs: Long,
) {
    val totalMs: Long get() = segments.sumOf { it.lengthMs }

    /** The clip's own time at [editedMs] of the finished video. */
    fun sourceAt(editedMs: Long): Long {
        var left = editedMs.coerceAtLeast(0L)
        for (s in segments) {
            if (left < s.lengthMs) return s.startMs + left
            left -= s.lengthMs
        }
        return segments.lastOrNull()?.endMs?.minus(1)?.coerceAtLeast(0L) ?: 0L
    }
}

/**
 * The cut being edited: pieces, crop, cover and sound. Compose state, so the
 * timeline and the preview follow every change.
 */
internal class TrailEdit(val clip: TrailClip) {
    private var nextId = 2L
    val segments = mutableStateListOf(TrailSegment(1L, 0L, min(clip.durationMs, TRAIL_VIDEO_MAX_MS)))
    var crop by mutableStateOf(TrailCrop())
    var coverMs by mutableLongStateOf(0L)
    var muted by mutableStateOf(false)
    /** The chosen piece: its ends can be trimmed, Delete removes it. */
    var selected by mutableStateOf<Long?>(1L)
    /** The cut whose transition is being picked (the index of the piece before it), or -1. */
    var transitionAt by mutableStateOf(-1)

    val totalMs: Long get() = segments.sumOf { it.lengthMs }

    fun startOf(index: Int): Long = segments.take(index.coerceIn(0, segments.size)).sumOf { it.lengthMs }

    /** The piece at [editedMs] of the finished video and how far into it. */
    fun locate(editedMs: Long): Pair<Int, Long> {
        var left = editedMs.coerceIn(0L, (totalMs - 1).coerceAtLeast(0L))
        segments.forEachIndexed { i, s ->
            if (left < s.lengthMs) return i to left
            left -= s.lengthMs
        }
        return (segments.size - 1).coerceAtLeast(0) to (segments.lastOrNull()?.lengthMs ?: 0L)
    }

    fun spec(): TrailEditSpec = TrailEditSpec(clip, segments.toList(), crop.clamped(clip), muted, coverMs.coerceIn(0L, totalMs))

    /** Cuts the piece under [editedMs] in two; false when either half would be too short. */
    fun split(editedMs: Long): Boolean {
        val (index, into) = locate(editedMs)
        val s = segments.getOrNull(index) ?: return false
        if (into < TRAIL_SEGMENT_MIN_MS || s.lengthMs - into < TRAIL_SEGMENT_MIN_MS) return false
        val cut = s.startMs + into
        val second = TrailSegment(nextId++, cut, s.endMs, s.after)
        segments[index] = s.copy(endMs = cut, after = TrailTransition.NONE)
        segments.add(index + 1, second)
        selected = second.id
        return true
    }

    /** Drops a piece; the last one stays. */
    fun delete(id: Long): Boolean {
        if (segments.size <= 1) return false
        val index = segments.indexOfFirst { it.id == id }
        if (index < 0) return false
        segments.removeAt(index)
        // The piece before a dropped last piece has no cut after it any more.
        if (index == segments.size) segments[index - 1] = segments[index - 1].copy(after = TrailTransition.NONE)
        selected = segments.getOrNull(index.coerceAtMost(segments.size - 1))?.id
        coverMs = coverMs.coerceIn(0L, totalMs)
        transitionAt = -1
        return true
    }

    /** New ends for a piece, kept inside the clip, at least half a second, the whole at most 30 s. */
    fun trim(id: Long, start: Long, end: Long) {
        val index = segments.indexOfFirst { it.id == id }
        if (index < 0) return
        val s = segments[index]
        val others = totalMs - s.lengthMs
        val room = (TRAIL_VIDEO_MAX_MS - others).coerceAtLeast(TRAIL_SEGMENT_MIN_MS)
        var a = start.coerceIn(0L, clip.durationMs - TRAIL_SEGMENT_MIN_MS)
        var b = end.coerceIn(a + TRAIL_SEGMENT_MIN_MS, clip.durationMs)
        if (b - a > room) {
            // The end that moved gives way.
            if (a != s.startMs) a = b - room else b = a + room
        }
        segments[index] = s.copy(startMs = a, endMs = b)
        coverMs = coverMs.coerceIn(0L, totalMs)
    }

    fun setTransition(index: Int, kind: TrailTransition) {
        val s = segments.getOrNull(index) ?: return
        if (index >= segments.size - 1) return
        segments[index] = s.copy(after = kind)
    }
}

/** How strong a transition is at a moment: which kind, and 0..1 (1 at the cut itself). */
internal object TrailTransitions {
    /** In the finished video's time: the nearest cut with a transition. */
    fun at(segments: List<TrailSegment>, editedMs: Long): Pair<TrailTransition, Float> {
        var start = 0L
        for (i in 0 until segments.size - 1) {
            start += segments[i].lengthMs
            val kind = segments[i].after
            if (kind == TrailTransition.NONE) continue
            val d = abs(editedMs - start)
            val half = halfAfter(segments, i)
            if (half > 0 && d < half) return kind to (1f - d.toFloat() / half)
        }
        return TrailTransition.NONE to 0f
    }

    /** Inside one piece of [lengthUs]: its way in ([into], [inUs] long) and its way out ([out], [outUs] long). */
    fun inside(tUs: Long, lengthUs: Long, into: TrailTransition, inUs: Long, out: TrailTransition, outUs: Long): Pair<TrailTransition, Float> {
        if (into != TrailTransition.NONE && inUs > 0 && tUs < inUs) return into to (1f - tUs.toFloat() / inUs)
        val left = lengthUs - tUs
        if (out != TrailTransition.NONE && outUs > 0 && left < outUs) return out to (1f - left.coerceAtLeast(0L).toFloat() / outUs)
        return TrailTransition.NONE to 0f
    }

    /** Each side of the cut after piece [index]: as in [at], never more than half of either piece. */
    fun halfAfter(segments: List<TrailSegment>, index: Int): Long {
        val a = segments.getOrNull(index) ?: return 0L
        val b = segments.getOrNull(index + 1) ?: return 0L
        return min(TRAIL_TRANSITION_HALF_MS, min(a.lengthMs, b.lengthMs) / 2)
    }

    const val ZOOM = 0.22f
}

/**
 * Fade and Flash in the export: the piece's own frames darken to black or
 * brighten to white near a cut. Each piece has its own instance, timed from
 * its first frame (Media3 calls it only for real frames).
 */
internal class TrailTransitionTone(
    private val lengthUs: Long,
    private val into: TrailTransition,
    private val inUs: Long,
    private val out: TrailTransition,
    private val outUs: Long,
) : RgbMatrix {
    private var first = Long.MIN_VALUE

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        if (first == Long.MIN_VALUE) first = presentationTimeUs
        val (kind, k) = TrailTransitions.inside(presentationTimeUs - first, lengthUs, into, inUs, out, outUs)
        val keep = when (kind) { TrailTransition.FADE, TrailTransition.FLASH -> 1f - k; else -> 1f }
        val lift = if (kind == TrailTransition.FLASH) k else 0f
        return floatArrayOf(
            keep, 0f, 0f, 0f,
            0f, keep, 0f, 0f,
            0f, 0f, keep, 0f,
            lift, lift, lift, 1f,
        )
    }
}

/** Zoom in the export: a punch in towards the cut, the same instance-per-piece timing. */
internal class TrailTransitionZoom(
    private val lengthUs: Long,
    private val into: TrailTransition,
    private val inUs: Long,
    private val out: TrailTransition,
    private val outUs: Long,
) : MatrixTransformation {
    private var first = Long.MIN_VALUE

    override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
        if (first == Long.MIN_VALUE) first = presentationTimeUs
        val (kind, k) = TrailTransitions.inside(presentationTimeUs - first, lengthUs, into, inUs, out, outUs)
        val s = if (kind == TrailTransition.ZOOM) 1f + TrailTransitions.ZOOM * k else 1f
        return android.graphics.Matrix().apply { setScale(s, s) }
    }
}

// ----------------------------------------------------------------- the bar

/** The timeline's layout of the moment, read by its gesture. */
private class TrailPiecesLayout {
    var lefts = FloatArray(0)
    var pxPerMs = 1f
    var count = 0
}

/**
 * Under the playing clip: play and time, sound, the timeline of pieces with
 * a transition button at each cut, and Split / Delete. A drag on the
 * timeline scrubs; a drag on the chosen piece's ends trims it.
 */
@Composable
internal fun TrailTimelineBar(session: TrailVideoSession) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val edit = session.edit
    var frames by remember(session.clip) { mutableStateOf(emptyList<Bitmap>()) }
    LaunchedEffect(session.clip) { frames = withContext(Dispatchers.IO) { TrailClips.strip(context, session.clip, 24, 120) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrailIconButton(if (session.playing) LucideR.drawable.lucide_ic_pause else LucideR.drawable.lucide_ic_play) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                session.togglePlay()
            }
            Text("${trailClock(session.playhead)} / ${trailClock(edit.totalMs)}", fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Wyrm.Ink)
            Spacer(Modifier.weight(1f))
            TrailIconButton(if (edit.muted) LucideR.drawable.lucide_ic_volume_x else LucideR.drawable.lucide_ic_volume_2) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                session.toggleSound()
            }
        }
        TrailPieces(session, frames)
        val at = edit.transitionAt
        if (at >= 0 && at < edit.segments.size - 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Transition", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Wyrm.Mute)
                TrailTransition.entries.forEach { kind ->
                    TrailPill(kind.label, edit.segments[at].after == kind) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        edit.setTransition(at, kind)
                        // Shows it: from just before the cut.
                        session.seekEdited((edit.startOf(at + 1) - 700L).coerceAtLeast(0L), play = true)
                    }
                }
                TrailPill("Done", true) { edit.transitionAt = -1 }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TrailPill("Split", false, LucideR.drawable.lucide_ic_scissors) {
                    if (edit.split(session.playhead)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        session.reload()
                    } else TrailsStore.toast = "Move the playhead into a longer piece to split it."
                }
                val chosen = edit.selected
                if (edit.segments.size > 1 && chosen != null) {
                    TrailPill("Delete", false, LucideR.drawable.lucide_ic_trash_2) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (edit.delete(chosen)) session.reload()
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${"%.1f".format(edit.totalMs / 1000f)} s of ${TRAIL_VIDEO_MAX_MS / 1000} s", fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = if (edit.totalMs >= TRAIL_VIDEO_MAX_MS) Wyrm.Badge else Wyrm.Quiet)
            }
        }
    }
}

/** The pieces side by side, each as wide as it is long; a diamond at each cut. */
@Composable
private fun TrailPieces(session: TrailVideoSession, frames: List<Bitmap>) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val edit = session.edit
    val gold = Color(0xFFF2B84B)
    val images = remember(frames) { frames.map { it.asImageBitmap() } }
    BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val gap = with(density) { 16.dp.toPx() }
        val handle = with(density) { 12.dp.toPx() }
        val count = edit.segments.size
        val usable = (widthPx - gap * (count - 1)).coerceAtLeast(1f)
        val pxPerMs = usable / edit.totalMs.coerceAtLeast(1L)
        // Where each piece starts on screen.
        val lefts = FloatArray(count)
        run {
            var x = 0f
            for (i in 0 until count) { lefts[i] = x; x += edit.segments[i].lengthMs * pxPerMs + gap }
        }
        // The gesture below reads the layout of the moment through this box, so
        // a trim that relays the pieces does not restart it mid-drag.
        val now = remember { TrailPiecesLayout() }
        now.lefts = lefts
        now.pxPerMs = pxPerMs
        now.count = count
        fun xOfEdited(ms: Long): Float {
            val (i, into) = edit.locate(ms)
            return lefts.getOrElse(i) { 0f } + into * pxPerMs
        }
        fun editedOfX(x: Float): Long {
            val ls = now.lefts
            for (i in 0 until now.count) {
                val l = ls[i]
                val r = l + edit.segments[i].lengthMs * now.pxPerMs
                if (x <= r + gap / 2 || i == now.count - 1) {
                    val into = ((x - l) / now.pxPerMs).toLong().coerceIn(0L, edit.segments[i].lengthMs - 1)
                    return edit.startOf(i) + into
                }
            }
            return 0L
        }
        val stripH = with(density) { 48.dp.toPx() }
        val top = with(density) { 5.dp.toPx() }
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val chosen = edit.segments.indexOfFirst { it.id == edit.selected }
                    // 1 left end, 2 right end of the chosen piece, 0 scrub.
                    var grab = 0
                    if (chosen >= 0 && chosen < now.count) {
                        val l = now.lefts[chosen]
                        val r = l + edit.segments[chosen].lengthMs * now.pxPerMs
                        grab = when {
                            abs(down.position.x - l) < handle * 1.8f -> 1
                            abs(down.position.x - r) < handle * 1.8f -> 2
                            else -> 0
                        }
                    }
                    val start = edit.segments.getOrNull(chosen)
                    // The scale when the finger landed: the pieces relay as a piece grows.
                    val scale = now.pxPerMs
                    var moved = 0f
                    val wasPlaying = session.playing
                    if (grab != 0) { session.pause(); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        moved += abs(change.position.x - change.previousPosition.x)
                        val dxMs = ((change.position.x - down.position.x) / scale).toLong()
                        when {
                            grab == 1 && start != null -> {
                                edit.trim(start.id, start.startMs + dxMs, start.endMs)
                                session.showSource(edit.segments[chosen].startMs)
                            }
                            grab == 2 && start != null -> {
                                edit.trim(start.id, start.startMs, start.endMs + dxMs)
                                session.showSource(edit.segments[chosen].endMs - 1)
                            }
                            moved > 6f -> {
                                if (session.playing) session.pause()
                                session.seekEdited(editedOfX(change.position.x), play = false)
                            }
                        }
                        if (change.positionChanged()) change.consume()
                    } while (event.changes.any { it.pressed })
                    when {
                        grab != 0 -> session.reload()
                        moved <= 6f -> {
                            // A tap chooses the piece and puts the playhead there.
                            val at = editedOfX(down.position.x)
                            val (i, _) = edit.locate(at)
                            edit.selected = edit.segments.getOrNull(i)?.id
                            edit.transitionAt = -1
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            session.seekEdited(at, play = wasPlaying)
                        }
                        wasPlaying -> session.play()
                    }
                }
            },
        ) {
            for (i in 0 until count) {
                val s = edit.segments[i]
                val l = lefts[i]
                val w = s.lengthMs * pxPerMs
                // Frames from the clip's own strip, nearest to each tile's time.
                val tile = stripH * 0.62f
                var x = 0f
                while (x < w) {
                    val tw = min(tile, w - x)
                    val ms = s.startMs + ((x + tw / 2) / pxPerMs).toLong()
                    val image = images.getOrNull(((ms.toFloat() / session.clip.durationMs.coerceAtLeast(1L)) * images.size).toInt()
                        .coerceIn(0, (images.size - 1).coerceAtLeast(0)))
                    if (image != null) {
                        val frame = image
                        val k = max(tw / frame.width, stripH / frame.height)
                        val srcW = (tw / k).roundToInt().coerceIn(1, frame.width)
                        val srcH = (stripH / k).roundToInt().coerceIn(1, frame.height)
                        drawImage(image,
                            srcOffset = IntOffset((frame.width - srcW) / 2, (frame.height - srcH) / 2),
                            srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                            dstOffset = IntOffset((l + x).roundToInt(), top.roundToInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(tw.roundToInt().coerceAtLeast(1), stripH.roundToInt()))
                    } else {
                        drawRect(Color.Gray.copy(alpha = 0.3f), Offset(l + x, top), Size(tw, stripH))
                    }
                    x += tile
                }
                if (s.id == edit.selected) {
                    drawRect(gold, Offset(l, top), Size(w, stripH), style = Stroke(3.dp.toPx()))
                    drawRect(gold, Offset(l, top), Size(handle, stripH))
                    drawRect(gold, Offset(l + w - handle, top), Size(handle, stripH))
                    val grip = Color.Black.copy(alpha = 0.55f)
                    drawLine(grip, Offset(l + handle / 2, top + stripH * 0.35f), Offset(l + handle / 2, top + stripH * 0.65f), 2.dp.toPx())
                    drawLine(grip, Offset(l + w - handle / 2, top + stripH * 0.35f), Offset(l + w - handle / 2, top + stripH * 0.65f), 2.dp.toPx())
                } else {
                    drawRect(Color.Black.copy(alpha = 0.18f), Offset(l, top), Size(w, stripH))
                }
            }
            // The playhead.
            val px = xOfEdited(session.playhead)
            drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 3.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(px + 1.5.dp.toPx(), 0f), Offset(px + 1.5.dp.toPx(), size.height), 1.dp.toPx())
        }
        // A diamond at each cut: its transition.
        for (i in 0 until count - 1) {
            val cutX = lefts[i + 1] - gap / 2
            val kind = edit.segments[i].after
            val on = edit.transitionAt == i
            Box(
                Modifier.offset { IntOffset((cutX - with(density) { 12.dp.toPx() }).roundToInt(), with(density) { 17.dp.toPx() }.roundToInt()) }
                    .size(24.dp).rotate(45f).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(if (on || kind != TrailTransition.NONE) Wyrm.Ink else Wyrm.Card)
                    .border(1.5.dp, Wyrm.Ink, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        edit.transitionAt = if (on) -1 else i
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (kind == TrailTransition.NONE) "+" else kind.label.take(1), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, color = if (on || kind != TrailTransition.NONE) Wyrm.OnInk else Wyrm.Ink,
                    modifier = Modifier.rotate(-45f))
            }
        }
    }
}

/**
 * The cover (OM, 2026-10-17): a strip of the finished video and a window on
 * it. While the window is dragged the picture above shows that frame, and
 * the window itself shows it too.
 */
@Composable
internal fun TrailCoverBar(session: TrailVideoSession, onDone: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val edit = session.edit
    var frames by remember(session.clip, edit.segments.toList()) { mutableStateOf(emptyList<Bitmap>()) }
    val spec = edit.spec()
    LaunchedEffect(session.clip, edit.segments.toList()) {
        frames = withContext(Dispatchers.IO) {
            (0 until 10).mapNotNull { i ->
                TrailClips.frame(context, session.clip, spec.sourceAt((spec.totalMs * (i + 0.5) / 10).toLong()), 120)
            }
        }
    }
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(edit.coverMs, edit.segments.toList()) {
        delay(90)
        cover = withContext(Dispatchers.IO) { TrailClips.frame(context, session.clip, edit.spec().sourceAt(edit.coverMs), 200) }
    }
    LaunchedEffect(Unit) { session.pause(); session.seekEdited(edit.coverMs, play = false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cover", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Wyrm.Ink)
            Spacer(Modifier.width(8.dp))
            Text("Drag to pick the picture people see first", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet,
                modifier = Modifier.weight(1f))
            TrailPill("Done", true, onClick = onDone)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(70.dp)) {
            val widthPx = with(density) { maxWidth.toPx() }
            val windowW = with(density) { 48.dp.toPx() }
            val total = edit.totalMs.coerceAtLeast(1L)
            fun xOf(ms: Long) = (ms.toFloat() / total) * (widthPx - windowW)
            Row(Modifier.fillMaxWidth().height(48.dp).align(Alignment.Center).clip(wyrmRounded(8.dp))) {
                if (frames.isEmpty()) Box(Modifier.fillMaxSize().background(Wyrm.Well))
                frames.forEach { bitmap ->
                    Image(remember(bitmap) { bitmap.asImageBitmap() }, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            Box(Modifier.fillMaxWidth().height(48.dp).align(Alignment.Center).background(Color.Black.copy(alpha = 0.35f)))
            val x = xOf(edit.coverMs)
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), 0) }.size(48.dp, 70.dp).clip(wyrmRounded(9.dp))
                    .border(3.dp, Color.White, wyrmRounded(9.dp)).background(Wyrm.Well),
            ) {
                // The nearest strip frame at once, the exact one a moment later.
                val shown = cover ?: frames.getOrNull(((edit.coverMs.toFloat() / total) * frames.size).toInt().coerceIn(0, (frames.size - 1).coerceAtLeast(0)))
                shown?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Box(
                Modifier.fillMaxSize().pointerInput(total, widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        fun pick(px: Float) {
                            val ms = (((px - windowW / 2) / (widthPx - windowW)).coerceIn(0f, 1f) * (total - 1)).toLong()
                            edit.coverMs = ms
                            cover = null
                            session.seekEdited(ms, play = false)
                        }
                        pick(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.positionChanged()) { pick(change.position.x); change.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
            )
        }
    }
}

/** The crop shapes for a video, and Done. */
@Composable
internal fun TrailCropBar(session: TrailVideoSession, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val edit = session.edit
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrailCropAspect.entries.forEach { aspect ->
                TrailPill(aspect.label, edit.crop.aspect == aspect) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    edit.crop = TrailCrop(aspect = aspect).clamped(session.clip)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!edit.crop.untouched) TrailPill("Reset", false) { edit.crop = TrailCrop() }
            TrailPill("Done", true, onClick = onDone)
        }
        Text("Drag and pinch the video to frame it", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun TrailPill(label: String, selected: Boolean, icon: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (selected) Wyrm.Ink else Wyrm.Well)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(painterResource(it), null, tint = if (selected) Wyrm.OnInk else Wyrm.Ink, modifier = Modifier.size(15.dp)) }
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (selected) Wyrm.OnInk else Wyrm.Ink)
    }
}

@Composable
private fun TrailIconButton(icon: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Wyrm.Well)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, tint = Wyrm.Ink, modifier = Modifier.size(17.dp)) }
}

/** "0:04.2": the timeline's clock. */
internal fun trailClock(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    return "%d:%02d.%d".format(t / 60_000, (t / 1000) % 60, (t / 100) % 10)
}
