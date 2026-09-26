package com.wyrm.omrajput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Alerts arrives as one full sheet laid over Home from the right.
 *
 * Home never shifts. Only the pill that summoned the sheet travels left while
 * the new surface rolls over it, then returns with the sheet on Back. This is
 * deliberately separate from [ExpandingPanel]: an alert is something arriving
 * over Home, not a place growing out of a Home index row.
 */
@Composable
fun AlertsSlidingPanel(
    origin: Rect?,
    expanded: Boolean,
    unread: Int,
    onCollapsed: () -> Unit,
    behind: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(if (expanded) 0f else 1f) }

    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.88f, stiffness = 340f),
        )
        if (!expanded) onCollapsed()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val t = progress.value.coerceIn(0f, 1f)
        val sheetLeft = width * (1f - t)
        val travellingCorner = Wyrm.CornerLarge * (1f - t)
        val sheetShape = RoundedCornerShape(
            topStart = travellingCorner,
            bottomStart = travellingCorner,
        )

        behind()

        Box(
            modifier = Modifier
                .offset(x = with(density) { sheetLeft.toDp() })
                .size(
                    width = with(density) { width.toDp() },
                    height = with(density) { height.toDp() },
                )
                .clip(sheetShape)
                .background(Wyrm.Paper),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(((t - 0.08f) / 0.56f).coerceIn(0f, 1f)),
            ) {
                content()
            }
        }

        val button = origin?.takeIf { it.width > 1f && it.height > 1f }
        if (button != null && t < 1f) {
            val finalLeft = -button.width - with(density) { Wyrm.Gutter.toPx() }
            val left = button.left + (finalLeft - button.left) * t
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { left.toDp() },
                        y = with(density) { button.top.toDp() },
                    )
                    .size(
                        width = with(density) { button.width.toDp() },
                        height = with(density) { button.height.toDp() },
                    ),
            ) {
                WyrmPill(
                    label = if (unread > 0) "$unread New" else "Alerts",
                    leading = if (unread > 0) Wyrm.Green else null,
                    modifier = Modifier.fillMaxSize(),
                    onClick = {},
                )
            }
        }
    }
}
