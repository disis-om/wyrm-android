package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Accessibility starts with appearance; text scale will join this page later. */
@Composable
fun SettingsAccessibilityScreen(
    selected: WyrmThemeId,
    intensity: Float,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onTheme: (WyrmThemeId) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onResetIntensity: () -> Unit,
) {
    var advancedOpen by remember { mutableStateOf(false) }
    SettingsDrillScaffold(
        title = "Accessibility",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsSectionLabel("Themes", top = 0.dp)
        SettingsCard {
            WyrmThemeId.entries.forEachIndexed { index, theme ->
                ThemeChoiceRow(
                    theme = theme,
                    selected = theme == selected,
                    intensity = intensity,
                    first = index == 0,
                    onClick = { onTheme(theme) },
                )
            }
        }
        Text(
            text = "Themes colour the app and arena interface only. Skins, arena background and gameplay stay untouched.",
            fontFamily = Wyrm.Body,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        AdvancedFold(
            label = "Advanced settings",
            open = advancedOpen,
            onToggle = { advancedOpen = !advancedOpen },
        )
        AnimatedVisibility(visible = advancedOpen) {
            SettingsCard {
                SettingsSliderRow(
                    title = "Theme intensity",
                    valueText = "${(intensity * 100f).roundToInt()}%",
                    detail = "50% is the original theme look. Lower moves towards Paper; higher is richer.",
                    value = intensity,
                    range = 0f..1f,
                    steps = 0,
                    first = true,
                    onChange = onIntensityChange,
                )
                SettingsHairline()
                Box(Modifier.padding(14.dp)) {
                    PaperOutlineButton(
                        label = "Reset",
                        enabled = kotlin.math.abs(intensity - 0.5f) >= 0.001f,
                        onClick = onResetIntensity,
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun ThemeChoiceRow(
    theme: WyrmThemeId,
    selected: Boolean,
    intensity: Float,
    first: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val palette = theme.palette.withIntensity(intensity)
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .scale(pressScale(pressed))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 42.dp)
                    .clip(wyrmRounded(10.dp))
                    .background(palette.paper)
                    .border(1.dp, palette.rule, wyrmRounded(10.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 38.dp, height = 23.dp)
                        .clip(wyrmRounded(6.dp))
                        .background(palette.card)
                        .border(1.dp, palette.rule, wyrmRounded(6.dp)),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(palette.ink, palette.live, palette.link).forEach { colour ->
                        Box(Modifier.size(5.dp).clip(CircleShape).background(colour))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = theme.displayName,
                    fontFamily = Wyrm.Body,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 15.5.sp,
                    color = Wyrm.Ink,
                )
                Text(
                    text = theme.description,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
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
