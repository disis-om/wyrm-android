package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.FrameLayout

/** A stationary full-screen Compose host. Only a DOWN inside the panel belongs
 * to Rain; other touches fall through to SDL. The entire owned gesture stays
 * with Rain, even when its knob moves outside the panel or over a screen edge. */
internal class RainPanelTouchFrame(context: Context) : FrameLayout(context) {
    val panelBounds = RectF()
    private val handleBounds = RectF()
    private val position = RainPanelDragState()
    var onPanelMove: ((Float, Float) -> Unit)? = null
    private var panelWidth = 0f
    private var panelHeight = 0f
    private var scaledDp = 1f
    private var ownsGesture = false
    private var dragPointer = -1

    fun layoutPanel(stageWidth: Float, stageHeight: Float, width: Float, height: Float, dp: Float) {
        panelWidth = width; panelHeight = height; scaledDp = dp
        position.configure(stageWidth, stageHeight, width, height)
        publishPosition()
    }

    private fun publishPosition() {
        val x = position.left; val y = position.top
        panelBounds.set(x, y, x + panelWidth, y + panelHeight)
        handleBounds.set(x + 24f * scaledDp, y + panelHeight - 50f * scaledDp,
            x + panelWidth - 24f * scaledDp, y + panelHeight - 24f * scaledDp)
        onPanelMove?.invoke(x, y)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            ownsGesture = panelBounds.contains(event.x, event.y)
            dragPointer = if (ownsGesture && handleBounds.contains(event.x, event.y)) event.getPointerId(0) else -1
            if (dragPointer != -1) {
                position.begin(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        if (!ownsGesture) return false
        // Read coordinates on this stationary stage. Never feed a moving
        // Compose handle incremental local deltas or a captured centre origin.
        if (dragPointer != -1) {
            val index = event.findPointerIndex(dragPointer)
            if (index >= 0 && event.actionMasked == MotionEvent.ACTION_MOVE) {
                position.move(event.getX(index), event.getY(index))
                publishPosition()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.actionIndex) == dragPointer)) {
                dragPointer = -1
                ownsGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            ownsGesture = false
        }
        return true
    }
}
