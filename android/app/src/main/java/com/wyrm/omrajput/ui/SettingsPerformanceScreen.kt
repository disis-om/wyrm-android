package com.wyrm.omrajput.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.WyrmPerformance

/*
 * Settings › Performance (OM, 2026-10-01). iOS twin: WyrmPerformancePage.
 *
 * Laid out the way game settings explain a trade-off (Android Game Mode's
 * Performance / Standard / Battery, and the Frame Rate API guide's advice to
 * offer only the rates the display can show): what the phone is doing right
 * now on top, three mode cards that each say what they cost, then one frame
 * limit built from this display's own refresh rates.
 */

private data class ModeCopy(val mode: WyrmPerformance.Mode, val icon: Int, val line: String)

private val MODES = listOf(
    ModeCopy(WyrmPerformance.Mode.AUTO, LucideR.drawable.lucide_ic_wand_sparkles,
        "Full speed while the phone is cool. Steps down when it warms up or Battery Saver is on."),
    ModeCopy(WyrmPerformance.Mode.BALANCED, LucideR.drawable.lucide_ic_leaf,
        "Steady 60 FPS with vsync. A cooler phone and a longer battery."),
    ModeCopy(WyrmPerformance.Mode.PERFORMANCE, LucideR.drawable.lucide_ic_zap,
        "Highest frame rate, vsync off, least input delay. The phone runs warmer."),
)

@Composable
fun SettingsPerformanceScreen(
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val mode = WyrmPerformance.mode
    val policy = WyrmPerformance.policy
    val choices = WyrmPerformance.limitChoices()
    SettingsDrillScaffold(
        title = "Performance",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        Spacer(Modifier.height(18.dp))
        NowCard(policy)

        SettingsSectionLabel("Mode")
        Column(
            Modifier.padding(horizontal = 16.dp).settingAnchor("app.performance"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MODES.forEach { copy ->
                ModeCard(copy, selected = copy.mode == mode) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    WyrmPerformance.applyMode(copy.mode)
                }
            }
        }
        SettingsCaption("Only how often a frame is drawn changes. The arena, your snake and how you join stay exactly the same.")

        SettingsSectionLabel("Frame limit")
        Box(Modifier.padding(horizontal = 16.dp).settingAnchor("app.fps-limit")) {
            PaperSegmented(
                options = choices.map(WyrmPerformance::limitLabel),
                selected = choices.indexOf(WyrmPerformance.limit).coerceAtLeast(0),
                onSelect = { index ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    WyrmPerformance.applyLimit(choices[index])
                },
            )
        }
        SettingsCaption(
            "Auto lets the mode decide. A number holds Wyrm at that rate; Max is as many as the mode allows. " +
                "In Auto mode, heat and Battery Saver can still bring it lower. This display goes up to ${WyrmPerformance.displayMaxHz} Hz.",
        )

        SettingsSectionLabel("Menus")
        SettingsCard {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconWell(LucideR.drawable.lucide_ic_snowflake, Wyrm.Ink)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("The arena rests under menus", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Wyrm.Ink)
                    Text(
                        "Home, Social and Settings no longer draw the game underneath, so they barely use the graphics chip. The lobby wakes it up.",
                        fontFamily = Wyrm.Body, fontSize = 12.5.sp, lineHeight = 17.sp, color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

/** What Wyrm draws at right now, why, and a strip that ticks at that rate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowCard(policy: WyrmPerformance.Policy) {
    val fps = if (policy.cap > 0) policy.cap else WyrmPerformance.displayMaxHz
    val shape = wyrmRounded(22.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(18.dp),
    ) {
        Text("RIGHT NOW", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = Wyrm.Quiet)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            if (policy.cap <= 0) {
                Text("Max", fontFamily = Wyrm.Display, fontWeight = FontWeight.SemiBold, fontSize = 50.sp, color = Wyrm.Ink)
            } else {
                Text("$fps", fontFamily = Wyrm.Display, fontWeight = FontWeight.SemiBold, fontSize = 50.sp, color = Wyrm.Ink)
            }
            Text(
                if (policy.cap <= 0) "FPS, uncapped" else "FPS",
                fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Wyrm.Mute,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
        }
        FrameStrip(fps)
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusChip(LucideR.drawable.lucide_ic_monitor, "${policy.displayHz} Hz display")
            StatusChip(LucideR.drawable.lucide_ic_activity, if (policy.vsync) "Vsync on" else "Vsync off")
            val heat = WyrmPerformance.heat
            StatusChip(
                if (heat == WyrmPerformance.Heat.COOL) LucideR.drawable.lucide_ic_thermometer else LucideR.drawable.lucide_ic_thermometer_sun,
                when (heat) {
                    WyrmPerformance.Heat.COOL -> "Phone cool"
                    WyrmPerformance.Heat.WARM -> "Phone warm"
                    WyrmPerformance.Heat.HOT -> "Phone hot"
                },
                alert = heat != WyrmPerformance.Heat.COOL,
            )
            if (WyrmPerformance.saver) StatusChip(LucideR.drawable.lucide_ic_battery_low, "Battery Saver", alert = true)
        }
        Text(policy.reason, fontFamily = Wyrm.Body, fontSize = 13.sp, lineHeight = 18.sp, color = Wyrm.Mute,
            modifier = Modifier.padding(top = 12.dp))
    }
}

/** 24 ticks; one lights per frame-ish, so a faster rate visibly runs faster. */
@Composable
private fun FrameStrip(fps: Int) {
    val ticks = 24
    // One sweep takes as long as `ticks` frames would at a quarter of the rate (readable, still relative).
    val sweepMs = (ticks * 4_000 / fps.coerceAtLeast(1)).coerceIn(400, 4_000)
    val phase by rememberInfiniteTransition(label = "frame-strip").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(sweepMs, easing = LinearEasing), RepeatMode.Restart),
        label = "frame-strip-phase",
    )
    val ink = Wyrm.Ink
    val rule = Wyrm.Rule
    Canvas(Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (ticks - 1)) / ticks
        val head = phase * ticks
        for (i in 0 until ticks) {
            val behind = ((head - i) + ticks) % ticks
            val glow = (1f - behind / 6f).coerceIn(0f, 1f)
            drawRoundRect(
                color = if (glow > 0f) ink.copy(alpha = 0.18f + 0.82f * glow) else rule,
                topLeft = Offset(i * (w + gap), 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

@Composable
private fun StatusChip(icon: Int, label: String, alert: Boolean = false) {
    val tint = if (alert) Wyrm.Badge else Wyrm.Ink
    Row(
        Modifier
            .clip(WyrmCapsule)
            .background(tint.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = tint)
    }
}

@Composable
private fun IconWell(icon: Int, tint: androidx.compose.ui.graphics.Color, filled: Boolean = false) {
    Box(
        Modifier.size(40.dp).clip(wyrmRounded(12.dp)).background(if (filled) Wyrm.Ink else tint.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, tint = if (filled) Wyrm.OnInk else tint, modifier = Modifier.size(19.dp)) }
}

/** One mode: its icon, name and cost, ringed in ink with a check when chosen. */
@Composable
private fun ModeCard(copy: ModeCopy, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val border by animateColorAsState(if (selected) Wyrm.Ink else Wyrm.Rule, label = "mode-border")
    val shape = wyrmRounded(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .scale(pressScale(pressed))
            .clip(shape)
            .background(Wyrm.Card)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(copy.icon, Wyrm.Ink, filled = selected)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(copy.mode.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Wyrm.Ink)
                if (copy.mode == WyrmPerformance.Mode.AUTO) {
                    Text(
                        "RECOMMENDED",
                        fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 8.5.sp, letterSpacing = 0.8.sp, color = Wyrm.Quiet,
                        modifier = Modifier.padding(start = 8.dp).clip(WyrmCapsule).background(Wyrm.Well).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Text(copy.line, fontFamily = Wyrm.Body, fontSize = 12.5.sp, lineHeight = 17.sp, color = Wyrm.Quiet,
                modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Wyrm.Ink else androidx.compose.ui.graphics.Color.Transparent)
                .border(if (selected) 0.dp else 1.5.dp, Wyrm.Rule, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(painterResource(LucideR.drawable.lucide_ic_check), null, tint = Wyrm.OnInk, modifier = Modifier.size(13.dp))
        }
    }
}
