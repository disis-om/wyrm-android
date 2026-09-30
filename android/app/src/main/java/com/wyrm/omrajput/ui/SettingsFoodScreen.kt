package com.wyrm.omrajput.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.Setting
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Settings › Food (redesigned, OM 2026-10-01): a live arena preview on top that
 * draws exactly what the settings below say (shape, size, colour, drift,
 * flicker), a grid of shape tiles with each food drawn as it glows in a match,
 * and one "Look" card for size, colour and motion. Only drawing changes:
 * position, value and eating stay original.
 */

/** Slither's own food hues, so the preview looks like a real arena. */
private val FOOD_HUES = listOf(
    0xFFC080FF, 0xFF9099FF, 0xFF80D0D0, 0xFF80FF80, 0xFFEEEE70,
    0xFFFFA060, 0xFFFF9090, 0xFFFF4040, 0xFFE030E0,
).map { Color(it) }

/** The arena floor behind the food: deep, so the glow reads as it does in a match. */
private val ARENA_FLOOR = Color(0xFF161B22)

/** Style index (Original, Rings, Mixed, Star, Triangle, Diamond, Hexagon, Square, Flower) → drawn shape. */
private fun shapeOf(style: Int): Int = if (style <= 1) style else style - 1

@Composable
fun SettingsFoodScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
) {
    // Settings search picks the tab holding its row.
    val sought = SettingsFocus.target
    var mode by remember { mutableIntStateOf(if (sought?.startsWith("assist.") == true) 1 else 0) }
    val group = if (mode == 0) "normal" else "assist"
    val food = settings.filter { it.group == group && it.isFoodSetting() }
    fun named(local: String) = food.firstOrNull { it.id.substringAfter('.') == local }
    val style = named("food_type")
    val scale = named("food_scale")
    val uniform = named("uniform_food_color")
    val colour = named("food_color")
    val drift = named("food_float")
    val flicker = named("food_flicker")
    val constant = named("const_food_scale")
    val shown = setOfNotNull(style, scale, uniform, colour, drift, flicker, constant)
    val others = food.filterNot { it in shown }

    SettingsDrillScaffold(
        title = "Food",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            FoodPreview(
                style = style?.index ?: 0,
                scale = scale?.number ?: 1f,
                uniform = uniform?.enabled == true,
                uniformColour = colour?.channels?.let { Color(it[0], it[1], it[2]) } ?: FOOD_HUES[3],
                drift = drift?.enabled == true,
                flicker = flicker?.enabled == true,
            )
            Spacer(Modifier.height(12.dp))
            PaperSegmented(
                options = listOf("Normal mode", "With assist"),
                selected = mode,
                onSelect = { mode = it },
            )
        }

        SettingsSectionLabel("Shape")
        if (style != null) {
            Column(
                Modifier.padding(horizontal = 16.dp).settingAnchor(style.id),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                style.options.chunked(3).forEachIndexed { row, labels ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        labels.forEachIndexed { column, label ->
                            val index = row * 3 + column
                            FoodTile(
                                style = index,
                                label = label,
                                selected = style.index == index,
                                modifier = Modifier.weight(1f),
                            ) { onChange(style, listOf(index.toFloat())) }
                        }
                        repeat(3 - labels.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        SettingsCaption("Mixed uses every shape and keeps each morsel the same shape for its whole life.")

        SettingsSectionLabel("Look")
        SettingsCard {
            var first = true
            listOfNotNull(scale, constant, uniform, colour?.takeIf { uniform?.enabled == true }, drift, flicker)
                .plus(others)
                .forEach { setting ->
                    Box(Modifier.settingAnchor(setting.id)) {
                        SettingTypedRow(setting, first = first, onChange = onChange)
                    }
                    first = false
                }
        }
        SettingsCaption("Only how food looks changes. Where it lies, what it is worth and eating it stay the arena's own.")
        Spacer(Modifier.height(22.dp))
    }
}

/**
 * A slice of arena with food on it, drawn by the same rules as the settings:
 * the shape (Mixed gives each morsel its own), the size, one colour or the
 * arena's hues, drift and flicker. It moves only when the settings say food moves.
 */
@Composable
private fun FoodPreview(style: Int, scale: Float, uniform: Boolean, uniformColour: Color, drift: Boolean, flicker: Boolean) {
    val time by rememberInfiniteTransition(label = "food-preview").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "food-time",
    )
    // Seeded morsels: the same field every time the page opens.
    val morsels = remember {
        val random = java.util.Random(0x5715)
        List(34) {
            floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat() * 6.283f,
                random.nextInt(FOOD_HUES.size).toFloat(), random.nextInt(8).toFloat())
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(wyrmRounded(22.dp))
            .background(ARENA_FLOOR)
            .border(1.dp, Wyrm.Rule, wyrmRounded(22.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // A faint hex-like dot lattice, as the arena floor has.
            val step = 22.dp.toPx()
            var y = step / 2f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) step / 2f else step
                while (x < size.width) {
                    drawCircle(Color.White.copy(alpha = 0.035f), 1.4.dp.toPx(), Offset(x, y))
                    x += step
                }
                y += step * 0.86f
                row++
            }
            val base = 5.5.dp.toPx() * scale.coerceIn(0.25f, 3f)
            val phase = time * 6.283f
            morsels.forEach { m ->
                val size01 = 0.65f + m[2] * 0.7f
                val wobble = if (drift) 4.dp.toPx() else 0f
                val c = Offset(
                    m[0] * size.width + cos(phase + m[3]) * wobble,
                    m[1] * size.height + sin(phase * 1.3f + m[3]) * wobble,
                )
                val glow = if (flicker) 0.55f + 0.45f * ((sin(phase * 3f + m[3] * 2f) + 1f) / 2f) else 1f
                val hue = if (uniform) uniformColour else FOOD_HUES[m[4].toInt()]
                val shape = if (style == 2) m[5].toInt() else shapeOf(style)
                drawGlowingFood(shape, c, base * size01, hue, glow)
            }
        }
        Text(
            "LIVE PREVIEW",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            letterSpacing = 1.2.sp,
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(WyrmCapsule)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

/** One shape: a small arena tile with the food glowing in it, its name, and a check when chosen. */
@Composable
private fun FoodTile(style: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .scale(pressScale(pressed))
            .clip(wyrmRounded(18.dp))
            .background(Wyrm.Card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, wyrmRounded(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(wyrmRounded(12.dp)).background(ARENA_FLOOR)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.17f
                if (style == 2) {
                    listOf(0, 2, 3, 5).forEachIndexed { spot, shape ->
                        val x = if (spot % 2 == 0) size.width * 0.33f else size.width * 0.67f
                        val y = if (spot < 2) size.height * 0.33f else size.height * 0.67f
                        drawGlowingFood(shape, Offset(x, y), r * 0.62f, FOOD_HUES[(spot * 2 + 1) % FOOD_HUES.size], 1f)
                    }
                } else {
                    drawGlowingFood(shapeOf(style), center, r, FOOD_HUES[style % FOOD_HUES.size], 1f)
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Wyrm.Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(LucideR.drawable.lucide_ic_check), null, tint = Wyrm.OnInk, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            label,
            fontFamily = Wyrm.Body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = if (selected) Wyrm.Ink else Wyrm.Mute,
            modifier = Modifier.padding(top = 7.dp, bottom = 2.dp),
            maxLines = 1,
        )
    }
}

internal fun Setting.isFoodSetting(): Boolean {
    val local = id.substringAfter('.')
    return local.startsWith("food_") || local == "const_food_scale" ||
        local == "uniform_food_color"
}

/** Food as the arena draws it: a soft halo, the body, and a small bright highlight. */
private fun DrawScope.drawGlowingFood(shape: Int, c: Offset, r: Float, colour: Color, glow: Float) {
    drawCircle(
        Brush.radialGradient(listOf(colour.copy(alpha = 0.55f * glow), colour.copy(alpha = 0f)), c, r * 2.8f),
        r * 2.8f,
        c,
    )
    drawFoodShape(shape, c, r, colour.copy(alpha = 0.55f + 0.45f * glow))
    if (shape != 1) drawCircle(Color.White.copy(alpha = 0.38f * glow), r * 0.28f, Offset(c.x - r * 0.3f, c.y - r * 0.32f))
}

private fun DrawScope.drawFoodShape(shape: Int, c: Offset, r: Float, colour: Color) {
    when (shape) {
        0 -> drawCircle(colour, r, c)
        1 -> drawCircle(colour, r * 0.88f, c, style = Stroke(r * 0.34f))
        2 -> drawPolygon(c, r, 10, colour) { point -> if (point % 2 == 0) 1f else 0.45f }
        3 -> drawPolygon(c, r, 3, colour)
        4 -> drawPolygon(c, r, 4, colour)
        5 -> drawPolygon(c, r, 6, colour)
        6 -> drawRect(colour, Offset(c.x - r * 0.85f, c.y - r * 0.85f), androidx.compose.ui.geometry.Size(r * 1.7f, r * 1.7f))
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
