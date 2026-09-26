package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import com.wyrm.omrajput.data.SettingType

/**
 * Spec page 12 — Settings › Assist.
 *
 * Assist is an on-screen button, not a persistent engine flag — this page owns
 * the colours and helper drawing that apply when it is held. Laser left Bot.
 */
@Composable
fun SettingsAssistScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String = "Settings",
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
) {
    var mode by remember { mutableIntStateOf(1) }
    var advanced by remember { mutableStateOf(true) }
    val foodIds = setOf("food_type", "food_scale", "food_float", "food_flicker",
        "const_food_scale", "uniform_food_color", "food_color")
    val dotIds = setOf("show_crosshair", "head_dot_size", "head_dot_color")
    val laser = listOfNotNull(
        settings.named("general.laser_thickness"),
        settings.named("general.laser_color"),
    )

    SettingsDrillScaffold(
        title = "Modes",
        parent = backLabel,
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsCard {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 15.dp)) {
                Text(
                    text = "Arena modes",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                )
                Text(
                    text = "Tune the normal arena and the helper view independently.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        SettingsSectionLabel("Choose mode")
        SettingsCard {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
                PaperSegmented(
                    options = listOf("Assist mode", "Normal mode"),
                    selected = if (mode == 1) 0 else 1,
                    onSelect = { mode = if (it == 0) 1 else 0 },
                )
            }
        }

        AnimatedContent(
            targetState = mode,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                if (targetState > initialState) {
                    (fadeIn(tween(180)) + slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) { it / 7 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(190)) { -it / 9 })
                } else {
                    (fadeIn(tween(180)) + slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) { -it / 7 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(190)) { it / 9 })
                }
            },
            label = "mode-settings-content",
        ) { visibleMode ->
            val group = if (visibleMode == 1) "assist" else "normal"
            val modeSettings = settings.filter { it.group == group && it.label.isNotBlank() }
            val colours = modeSettings.filter {
                it.id.substringAfter('.') !in foodIds + dotIds &&
                    (it.type == SettingType.COLOR3 || it.type == SettingType.COLOR4)
            }
            val headDot = modeSettings.firstOrNull { it.id.substringAfter('.') == "show_crosshair" }
            val headDotSize = modeSettings.firstOrNull { it.id.substringAfter('.') == "head_dot_size" }
            val headDotColor = modeSettings.firstOrNull { it.id.substringAfter('.') == "head_dot_color" }
            val rest = modeSettings.filter {
                it !in colours && it.id.substringAfter('.') !in foodIds + dotIds
            }

            Column {
                SettingsSectionLabel("Arena colours")
                SettingsCard {
                    colours.forEachIndexed { index, setting ->
                        SettingsColourRow(setting = setting, first = index == 0, onChange = onChange)
                    }
                }

                SettingsSectionLabel("Joystick guide")
                SettingsCard {
                    HeadDotPreview(size = headDotSize, colour = headDotColor)
                    headDot?.let { setting ->
                        SettingTypedRow(setting = setting, first = false, onChange = onChange)
                    }
                    headDotSize?.let { setting ->
                        SettingTypedRow(setting = setting, first = false, onChange = onChange)
                    }
                    headDotColor?.let { setting ->
                        SettingsColourRow(setting = setting, first = false, onChange = onChange)
                    }
                }

                AdvancedFold(
                    label = "Advanced · helper lines",
                    open = advanced,
                    onToggle = { advanced = !advanced },
                )
                if (advanced) {
                    SettingsCard {
                        var first = true
                        laser.forEach { setting ->
                            SettingTypedRow(setting = setting, first = first, onChange = onChange)
                            first = false
                        }
                        rest.forEach { setting ->
                            SettingTypedRow(setting = setting, first = first, onChange = onChange)
                            first = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadDotPreview(size: Setting?, colour: Setting?) {
    val channels = colour?.channels ?: listOf(1f, 1f, 1f, 1f)
    val dot = Color(channels[0], channels[1], channels[2], 1f)
    val diameter = (size?.number ?: 10f).coerceIn(4f, 32f)

    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = "Size relative to snake head",
            fontFamily = Wyrm.Body,
            fontSize = 12.5.sp,
            color = Wyrm.Quiet,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(Wyrm.Well, wyrmRounded(12.dp))
                .border(1.dp, Wyrm.Rule, wyrmRounded(12.dp)),
        ) {
            Canvas(Modifier.fillMaxWidth().height(74.dp)) {
                val headRadius = 22.dp.toPx()
                val headCenter = Offset(this.size.width * 0.5f - headRadius * 0.35f, this.size.height * 0.5f)
                // The arena head is 29 world units wide. Both it and the dot
                // receive the same snake-scale and camera projection, so this
                // ratio is exactly what survives on screen at every zoom.
                drawCircle(Wyrm.Ink, headRadius, headCenter)
                drawCircle(
                    color = dot,
                    radius = headRadius * diameter / 29f,
                    center = Offset(headCenter.x + headRadius, headCenter.y),
                )
            }
        }
    }
}
