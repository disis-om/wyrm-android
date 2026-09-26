package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SettingsFoodScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) }
    var advanced by remember { mutableStateOf(false) }
    val group = if (mode == 0) "normal" else "assist"
    val food = settings.filter { it.group == group && it.isFoodSetting() }
    val style = food.firstOrNull { it.id.endsWith(".food_type") }
    val details = food.filterNot { it == style }

    SettingsDrillScaffold(
        title = "Food",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsCard {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Arena food",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                )
                Text(
                    text = "Change only how food is drawn. Position, value and eating stay original.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
                PaperSegmented(
                    options = listOf("Normal mode", "With assist"),
                    selected = mode,
                    onSelect = { mode = it },
                )
            }
        }

        SettingsSectionLabel("Shape")
        SettingsCard {
            style?.options?.forEachIndexed { index, label ->
                FoodStyleRow(
                    index = index,
                    label = label,
                    selected = style.index == index,
                    first = index == 0,
                    onClick = { onChange(style, listOf(index.toFloat())) },
                )
            }
        }
        Text(
            text = "Mixed uses every shape and keeps each morsel stable for its whole life.",
            fontFamily = Wyrm.Body,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )

        if (details.isNotEmpty()) {
            AdvancedFold(
                label = "Advanced · size, motion and colour",
                open = advanced,
                onToggle = { advanced = !advanced },
            )
            if (advanced) {
                SettingsCard {
                    details.forEachIndexed { index, setting ->
                        SettingTypedRow(setting, first = index == 0, onChange = onChange)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

private fun Setting.isFoodSetting(): Boolean {
    val local = id.substringAfter('.')
    return local.startsWith("food_") || local == "const_food_scale" ||
        local == "uniform_food_color"
}

@Composable
private fun FoodStyleRow(
    index: Int,
    label: String,
    selected: Boolean,
    first: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .scale(pressScale(pressed))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 42.dp)
                    .clip(wyrmRounded(10.dp))
                    .background(Wyrm.Well)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(width = 46.dp, height = 30.dp)) {
                    if (index == 2) {
                        val mini = size.minDimension * 0.12f
                        listOf(0, 2, 3, 5).forEachIndexed { spot, shape ->
                            val x = if (spot % 2 == 0) size.width * 0.30f else size.width * 0.70f
                            val y = if (spot < 2) size.height * 0.30f else size.height * 0.70f
                            drawFoodShape(shape, Offset(x, y), mini, Wyrm.Live)
                        }
                    } else {
                        val shape = when {
                            index <= 1 -> index
                            else -> index - 1
                        }
                        drawFoodShape(shape, center, size.minDimension * 0.34f, Wyrm.Live)
                    }
                }
            }
            Text(
                text = label,
                fontFamily = Wyrm.Body,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.5.sp,
                color = Wyrm.Ink,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (selected) Wyrm.Ink else Wyrm.Chevron, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(Wyrm.Ink))
            }
        }
    }
}

private fun DrawScope.drawFoodShape(shape: Int, c: Offset, r: Float, colour: Color) {
    when (shape) {
        0 -> drawCircle(colour, r, c)
        1 -> drawCircle(colour, r * 0.88f, c, style = Stroke(r * 0.34f))
        2 -> drawPolygon(c, r, 10, colour) { point -> if (point % 2 == 0) 1f else 0.45f }
        3 -> drawPolygon(c, r, 3, colour)
        4 -> drawPolygon(c, r, 4, colour)
        5 -> drawPolygon(c, r, 6, colour)
        6 -> drawRect(colour, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
        else -> drawPolygon(c, r, 24, colour) { point ->
            0.82f + 0.18f * cos(point * 6.0 * 2.0 * PI / 24.0).toFloat()
        }
    }
}

private fun DrawScope.drawPolygon(
    c: Offset,
    r: Float,
    points: Int,
    colour: Color,
    radius: (Int) -> Float = { 1f },
) {
    val path = Path()
    repeat(points) { point ->
        val angle = -PI / 2.0 + point * 2.0 * PI / points
        val rr = r * radius(point)
        val x = c.x + cos(angle).toFloat() * rr
        val y = c.y + sin(angle).toFloat() * rr
        if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, colour)
}
