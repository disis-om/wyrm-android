package com.wyrm.omrajput.ui

import kotlin.math.*

internal object WardrobeGeometry {
    const val POINTS = 38
    const val ROW_GAP = 100f
    const val BODY_SPAN = 370f
    const val BEAD = 29f
    const val BEADS = 58
    const val TICKS_PER_SECOND = 125f
    const val TRANSITION_MS = 400
    fun wrap(index: Int, count: Int): Int = if (count <= 0) 0 else ((index % count) + count) % count
    fun ease(progress: Float): Float = ((1.0 - cos(PI * progress.coerceIn(0f, 1f))) * .5).toFloat()
    fun wave(seconds: Double, tailToHead: Float): Float =
        (cos(seconds * TICKS_PER_SECOND / 23.0 + tailToHead * (POINTS - 1) / 4.0) *
            15.0 * (.8 + .2 * (1.0 - tailToHead * (POINTS - 1) / POINTS))).toFloat()
    fun nearestOccurrence(cursor: Float, index: Int, count: Int): Int =
        index + ((cursor - index) / count.coerceAtLeast(1)).roundToInt() * count
}

/** AIR's two .925 damping passes, .025 spring, .12 drag smoothing and 333 ms
 * nearest-snake capture window. Physics uses 8 ms ticks, independent of display Hz. */
internal class WardrobeMotion(initial: Int) {
    var cursor = initial.toFloat(); private set
    var dragging = false; private set
    var selected = initial; private set
    private var velocity = 0f
    private var count = Int.MAX_VALUE
    private var looping = true
    private var pendingDrag = 0f
    private var dragSeconds = 0f
    private var downSelected = initial
    private var captureSeconds = 0f
    private var accumulator = 0f
    val canScroll: Boolean get() = count > 1

    fun configure(size: Int, infinite: Boolean) {
        count = size.coerceAtLeast(1); looping = infinite && size > 1
        if (!looping) { selected = selected.coerceIn(0, count - 1); cursor = cursor.coerceIn(-.22f, count - .78f) }
        if (size <= 1) rebase(0)
    }
    fun rebase(index: Int) {
        selected = if (looping) index else index.coerceIn(0, count - 1)
        cursor = selected.toFloat(); velocity = 0f; pendingDrag = 0f
        dragging = false; captureSeconds = 0f; accumulator = 0f
    }
    fun show(index: Int, count: Int, immediate: Boolean = false) {
        if (dragging || (if (looping) WardrobeGeometry.wrap(selected, count) else selected) == index) return
        val target = if (looping) WardrobeGeometry.nearestOccurrence(cursor, index, count) else index.coerceIn(0, this.count - 1)
        selected = target
        if (immediate) rebase(target)
    }
    fun begin() {
        if (dragging || !canScroll) return
        dragging = true; pendingDrag = 0f; dragSeconds = 0f; downSelected = selected
        captureSeconds = 0f; accumulator = 0f
    }
    fun drag(rows: Float) {
        if (!dragging || !rows.isFinite()) return
        val before = cursor
        cursor += rows
        if (!looping) cursor = cursor.coerceIn(-.22f, count - .78f)
        pendingDrag += cursor - before
        selected = nearest()
    }
    fun release(rowsPerSecond: Float = Float.NaN) {
        if (!dragging) return
        dragging = false
        if (abs(pendingDrag) > .00001f) {
            val sample = pendingDrag / 2.08f
            velocity += (sample - velocity) * (1f - .88f.pow(2.08f))
            pendingDrag = 0f
        }
        if (abs(velocity) < .00001f && rowsPerSecond.isFinite()) velocity = rowsPerSecond / WardrobeGeometry.TICKS_PER_SECOND
        // AIR advances one neighbour for a quick flick that did not cross a row.
        if (dragSeconds < .160f && selected == downSelected && abs(velocity) * 100f > .3f) {
            selected = bounded(downSelected + if (velocity > 0f) 1 else -1)
            captureSeconds = 0f
        } else { selected = nearest(); captureSeconds = .333f }
        accumulator = 0f
    }
    fun select(row: Int) { dragging = false; selected = bounded(row); captureSeconds = 0f }
    fun settle() { dragging = false; selected = nearest(); captureSeconds = 0f }
    private fun bounded(row: Int) = if (looping) row else row.coerceIn(0, count - 1)
    private fun nearest() = bounded(floor(cursor + .5f).toInt())
    fun step(seconds: Float) {
        if (!seconds.isFinite() || seconds <= 0f) return
        val dt = seconds.coerceAtMost(.1f)
        if (dragging) {
            dragSeconds += dt
            val ticks = (dt * WardrobeGeometry.TICKS_PER_SECOND).coerceAtLeast(.001f)
            velocity += (pendingDrag / ticks - velocity) * (1f - .88f.pow(ticks))
            pendingDrag = 0f
            return
        }
        accumulator += dt
        val tick = 1f / WardrobeGeometry.TICKS_PER_SECOND
        while (accumulator + .0000001f >= tick) {
            if (captureSeconds > 0f) { selected = nearest(); captureSeconds -= tick }
            // Main.as: cs_vy = cs_avy * m925; then cs_avy *= m925;
            // spring changes NEXT tick's velocity, not this tick's displacement.
            val displacement = velocity * .925f
            velocity = displacement * .925f - (cursor - selected) * .025f
            cursor += displacement
            if (!looping) cursor = cursor.coerceIn(-.22f, count - .78f)
            if (abs(cursor - selected) < .00005f && abs(velocity) < .00005f) {
                cursor = selected.toFloat(); velocity = 0f
            }
            accumulator -= tick
        }
    }
}

/** Shared by real Android input and the regression harness. A new/returning
 * finger only changes the anchor: it never contributes another finger's delta. */
internal class WardrobeTouchTracker {
    private val points = linkedMapOf<Long, Float>()
    var active: Long? = null; private set
    val pressed: Boolean get() = points.isNotEmpty()
    fun down(id: Long, y: Float): Boolean {
        val first = points.isEmpty(); points[id] = y; active = id; return first
    }
    fun move(id: Long, y: Float): Float {
        val previous = points.put(id, y) ?: y
        return if (active == id) y - previous else 0f
    }
    fun up(id: Long) {
        points.remove(id)
        if (active == id) active = points.keys.lastOrNull()
    }
    fun cancel() { points.clear(); active = null }
}

internal data class WardrobePattern(val groups: List<Int>, val colors: List<Int>)
internal class WardrobeHistory(initial: WardrobePattern) {
    private val undo = ArrayDeque<WardrobePattern>()
    private val redo = ArrayDeque<WardrobePattern>()
    var current = initial; private set
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun push(next: WardrobePattern) {
        if (next == current) return
        undo.addLast(current); if (undo.size > 256) undo.removeFirst()
        redo.clear(); current = next
    }
    fun undo(): WardrobePattern { if (canUndo) { redo.addLast(current); current = undo.removeLast() }; return current }
    fun redo(): WardrobePattern { if (canRedo) { undo.addLast(current); current = redo.removeLast() }; return current }
}
