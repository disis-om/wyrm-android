package com.wyrm.omrajput.ui

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

// Kept apart so Wyrm Desktop can give the same name its own Skia version.

/** SwiftUI `.shadow(color:radius:y:)` behind a rounded rectangle. */
fun Modifier.iosShadow(color: Color, radius: Dp, y: Dp, corner: Dp): Modifier = drawBehind {
    if (color.alpha <= 0f) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(radius.toPx().coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        val r = corner.toPx()
        canvas.nativeCanvas.drawRoundRect(0f, y.toPx(), size.width, size.height + y.toPx(), r, r, paint)
    }
}
