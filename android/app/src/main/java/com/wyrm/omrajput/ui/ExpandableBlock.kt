package com.wyrm.omrajput.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Content measured at its real height, then deliberately clipped until opened. */
@Composable
fun ExpandableBlock(
    modifier: Modifier = Modifier,
    collapsedHeight: Dp = 132.dp,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    SubcomposeLayout(modifier = modifier.animateContentSize(tween(180)).clipToBounds()) { constraints ->
        val body = subcompose("body", content).map { measurable ->
            measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        }
        val fullHeight = body.maxOfOrNull { it.height } ?: 0
        val collapsedPx = collapsedHeight.roundToPx()
        val needsToggle = fullHeight > collapsedPx
        val visibleHeight = if (expanded || !needsToggle) fullHeight else collapsedPx
        val toggle = if (needsToggle) {
            subcompose("toggle-$expanded") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Wyrm.Card)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { expanded = !expanded },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Read full message",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Wyrm.Link,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }.map { it.measure(constraints.copy(minHeight = 0)) }
        } else emptyList()
        val toggleHeight = toggle.maxOfOrNull { it.height } ?: 0
        val naturalWidth = maxOf(
            body.maxOfOrNull { it.width } ?: 0,
            toggle.maxOfOrNull { it.width } ?: 0,
        )
        val width = naturalWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (visibleHeight + toggleHeight)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            body.forEach { it.placeRelativeWithLayer(0, 0) { clip = true } }
            toggle.forEach { it.placeRelative(0, visibleHeight) }
        }
    }
}
