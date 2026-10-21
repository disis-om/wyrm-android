package com.wyrm.omrajput.ui

/** Screen-space position, independent of Compose recomposition and moving handles. */
internal class RainPanelDragState {
    var left = Float.NaN; private set
    var top = Float.NaN; private set
    private var maxLeft = 0f
    private var maxTop = 0f
    private var downX = 0f
    private var downY = 0f
    private var startLeft = 0f
    private var startTop = 0f

    fun configure(stageWidth: Float, stageHeight: Float, width: Float, height: Float) {
        maxLeft = (stageWidth - width).coerceAtLeast(0f)
        maxTop = (stageHeight - height).coerceAtLeast(0f)
        left = (if (left.isFinite()) left else maxLeft / 2f).coerceIn(0f, maxLeft)
        top = (if (top.isFinite()) top else maxTop / 2f).coerceIn(0f, maxTop)
    }

    fun begin(x: Float, y: Float) {
        downX = x; downY = y; startLeft = left; startTop = top
    }

    fun move(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        left = (startLeft + x - downX).coerceIn(0f, maxLeft)
        top = (startTop + y - downY).coerceIn(0f, maxTop)
    }
}
