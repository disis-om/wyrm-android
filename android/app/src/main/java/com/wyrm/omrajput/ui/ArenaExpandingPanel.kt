package com.wyrm.omrajput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** A conventional bottom sheet: the Home control stays put and the sheet slides up. */
@Composable
fun ArenaExpandingPanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCollapsed: () -> Unit,
    onExpanded: () -> Unit,
    behind: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(if (expanded) 0f else 1f) }

    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(durationMillis = 420, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)),
        )
        if (expanded) onExpanded() else onCollapsed()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullHeight = constraints.maxHeight.toFloat()
        val targetHeight = fullHeight
        val t = progress.value.coerceIn(0f, 1f)

        behind()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1C1A).copy(alpha = 0.18f * t))
                .clickable(
                    enabled = expanded && t > 0.7f,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )

        val sheetRadius = 0.dp
        val shape = RoundedCornerShape(
            topStart = sheetRadius,
            topEnd = sheetRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )

        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .offset(y = with(density) { (targetHeight * (1f - t)).toDp() })
                .fillMaxWidth()
                .height(with(density) { targetHeight.toDp() })
                .clip(shape)
                .background(Wyrm.Paper),
        ) {
            content()
        }
    }
}
