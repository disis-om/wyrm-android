package com.wyrm.omrajput.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned

/*
 * The adjust preview (OM, 2026-09-28), as Wyrm iOS's `WyrmAdjustPreview`.
 *
 * The arrow, joystick, boost and zoom-bar sliders sit below the Controls
 * preview, so dragging one usually scrolls the thing being changed off screen.
 * While such a slider is held, this checks whether that exact control is fully
 * on screen inside the page's preview. When it is not, a card fades in at the
 * top of the page drawing the real control at its live size, opacity and
 * colour (`AdjustPreviewCard` in ControlsScreen.kt). It fades out one second
 * after the finger lets go.
 *
 * "Fully" on purpose: a control only peeking under the header does not count.
 */
enum class AdjustSubject { ARROW, JOYSTICK, BOOST, ZOOM }

/** The control the slider below this point changes, if any. */
internal val LocalAdjustSubject = compositionLocalOf<AdjustSubject?> { null }

internal object AdjustPreview {
    /** What the card draws; it stays set while the card fades out. */
    var subject by mutableStateOf<AdjustSubject?>(null)
        private set
    var shown by mutableStateOf(false)
        private set
    /** Set by the Controls page: `controls.opacity` belongs to the arrow then. */
    var arrowSteering = false

    private var viewport = Rect.Zero
    private val onPage = HashMap<AdjustSubject, Rect>()
    private var held = false
    private val main = Handler(Looper.getMainLooper())
    private val hide = Runnable { if (!held) shown = false }

    /** The slider for [subject] was touched (true) or let go (false). */
    fun editing(subject: AdjustSubject, active: Boolean) {
        if (active) {
            main.removeCallbacks(hide)
            held = true
            this.subject = subject
            decide()
        } else {
            held = false
            main.removeCallbacks(hide)
            main.postDelayed(hide, 1_000)
        }
    }

    fun setViewport(bounds: Rect) {
        viewport = bounds
        if (held) decide()
    }

    fun place(subject: AdjustSubject, bounds: Rect?) {
        if (bounds == null) onPage.remove(subject) else onPage[subject] = bounds
        if (held && subject == this.subject) decide()
    }

    private fun decide() {
        val current = subject ?: return
        val rect = onPage[current]
        val seen = rect != null && viewport.width > 0f &&
            rect.left >= viewport.left && rect.top >= viewport.top &&
            rect.right <= viewport.right && rect.bottom <= viewport.bottom
        val next = held && !seen
        if (next != shown) shown = next
    }

    fun subjectFor(id: String): AdjustSubject? = when (id) {
        "arrow.size", "arrow.separation", "arrow.smoothness", "arrow.color", "app.arrow-brightness" -> AdjustSubject.ARROW
        "controls.joystick_size" -> AdjustSubject.JOYSTICK
        "controls.boost_size" -> AdjustSubject.BOOST
        "controls.zoom_length" -> AdjustSubject.ZOOM
        "controls.opacity" -> if (arrowSteering) AdjustSubject.ARROW else AdjustSubject.JOYSTICK
        else -> null
    }
}

/** Reports where the page preview drew one control. */
internal fun Modifier.adjustPlace(subject: AdjustSubject): Modifier = composed {
    DisposableEffect(subject) { onDispose { AdjustPreview.place(subject, null) } }
    onGloballyPositioned { AdjustPreview.place(subject, it.unclippedBounds()) }
}

/** The scroll area whose visible part counts as "on screen". */
internal fun Modifier.adjustViewport(): Modifier =
    onGloballyPositioned { AdjustPreview.setViewport(it.unclippedBounds()) }

/** Not `boundsInWindow`: that clips to the scroll, so a half-hidden control read as fully seen. */
private fun LayoutCoordinates.unclippedBounds(): Rect = Rect(positionInWindow(), size.toSize())
