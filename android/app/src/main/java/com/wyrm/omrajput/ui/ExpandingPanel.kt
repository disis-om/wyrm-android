package com.wyrm.omrajput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Nested page enter/leave.
 *
 * A short iOS-style push from the right. The old row-morph (clip a full
 * screen out of the tapped rect, spring the corners, stretch glass) was
 * too heavy on the phone — do not bring it back. New drill-in pages go
 * through [Grown] and pick this up automatically.
 *
 * [origin] is unused. Kept so callers that still measure a row do not
 * have to change.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ExpandingPanel(
    origin: Rect?,
    expanded: Boolean,
    onCollapsed: () -> Unit,
    behind: @Composable () -> Unit = {},
    onExpanded: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(if (expanded) 0f else 1f) }

    // Wyrm iOS's `wyrmCinematicPush`: the page arrives 52 pt from the right
    // out of a 16 pt blur on spring(0.44, 0.84), and leaves 38 pt to the right
    // into a 12 pt blur on spring(0.38, 0.88), fading over the tab beneath.
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = if (expanded) iosSpring(0.44f, 0.84f) else iosSpring(0.38f, 0.88f),
        )
        if (expanded) onExpanded() else onCollapsed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val t = progress.value.coerceIn(0f, 1f)

        // Skin preview is drawn under Compose, so once the push has finished
        // the tab page must not cover it — but it stays composed (only made
        // invisible): rebuilt at the start of a pop, its first frame was empty
        // and the white window behind flashed through.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (progress.value < 0.999f) 1f else 0f }) {
            behind()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1.2f)
                    val away = (1f - p).coerceAtLeast(0f)
                    translationX = (if (expanded) 52f else 38f) * density * away
                    alpha = p.coerceIn(0f, 1f)
                    val radius = (if (expanded) 16f else 12f) * density * away
                    renderEffect = if (radius > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) {
                        androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal)
                    } else {
                        null
                    }
                },
        ) {
            content()
        }
    }
}

private val PushEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
