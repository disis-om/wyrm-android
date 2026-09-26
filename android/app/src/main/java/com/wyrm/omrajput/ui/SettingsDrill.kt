package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import com.wyrm.omrajput.data.SettingType
import com.composables.icons.lucide.R as LucideR
import kotlin.math.roundToInt

private val nums = TextStyle(fontFeatureSettings = "tnum")

@Composable
internal fun SettingsDrillScaffold(
    title: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    parent: String = "Settings",
    trailing: String? = null,
    trailingEnabled: Boolean = true,
    onTrailing: (() -> Unit)? = null,
    sectionTabs: (@Composable () -> Unit)? = null,
    contentOnly: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (contentOnly) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Wyrm.Paper.copy(alpha = 0.94f))
                .padding(top = insetTop)
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Text(
                    text = "‹ $parent",
                    fontFamily = Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
                Text(
                    text = title,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (trailing != null && onTrailing != null) {
                    Text(
                        text = trailing,
                        fontFamily = Wyrm.Body,
                        fontSize = 15.5.sp,
                        color = if (trailingEnabled) Wyrm.Link else Wyrm.TabIdle,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(enabled = trailingEnabled, onClick = onTrailing)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
        sectionTabs?.invoke()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Wyrm.Rule),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            Spacer(Modifier.height(24.dp + insetBottom.coerceAtLeast(8.dp)))
        }
    }
}

@Composable
internal fun SettingsSectionLabel(text: String, top: Dp = 22.dp) {
    Text(
        text = text.uppercase(),
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.92.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = wyrmRounded(14.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
        content = content,
    )
}

@Composable
internal fun SettingsHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Wyrm.RowRule),
    )
}

@Composable
internal fun AdvancedFold(
    label: String,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        label = "fold chevron",
    )
    val shape = wyrmRounded(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, if (open) Wyrm.Chevron else Wyrm.RowRule, shape)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(start = 15.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.78.sp,
            color = Wyrm.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (open) "HIDE" else "SHOW",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 0.7.sp,
            color = Wyrm.Quiet,
        )
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(wyrmRounded(9.dp))
                .background(if (open) Wyrm.Ink else Color(0x0D37352F)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_chevron_down),
                contentDescription = if (open) "Collapse $label" else "Expand $label",
                tint = if (open) Wyrm.Paper else Wyrm.Ink,
                modifier = Modifier.size(17.dp).rotate(chevronRotation),
            )
        }
    }
}

@Composable
internal fun SettingsCaption(text: String) {
    Text(
        text = text,
        fontFamily = Wyrm.Body,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
    )
}

/**
 * Wyrm iOS uses the system switch, so on iOS 26 it is Liquid Glass: a
 * capsule thumb that lifts into a lens while held, tinted with the theme's
 * live colour. [LiquidSwitch] is that control.
 */
@Composable
internal fun InkSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    LiquidSwitch(on = on, onToggle = onToggle)
}

@Composable
internal fun PaperSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    // The system segmented control on Wyrm iOS: a capsule with a lens thumb.
    LiquidSegmented(options = options, selected = selected, onSelect = onSelect, modifier = modifier)
}

@Composable
internal fun SettingsBoolRow(
    title: String,
    detail: String,
    on: Boolean,
    first: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable { onToggle(!on) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            InkSwitch(on = on, onToggle = onToggle)
        }
    }
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    valueText: String,
    detail: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    first: Boolean,
    onChange: (Float) -> Unit,
) {
    Column {
        if (!first) SettingsHairline()
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = title,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.5.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = valueText,
                    fontFamily = Wyrm.Body,
                    fontSize = 14.sp,
                    color = Wyrm.Mute,
                    style = nums,
                )
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            LiquidSlider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun SettingsEnumBlock(
    title: String,
    detail: String,
    options: List<String>,
    selected: Int,
    first: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column {
        if (!first) SettingsHairline()
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(11.dp))
            PaperSegmented(options = options, selected = selected, onSelect = onSelect)
        }
    }
}

@Composable
internal fun SettingsValueRow(
    title: String,
    value: String,
    first: Boolean,
    onOpen: ((Rect) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .then(
                    if (onOpen != null) {
                        Modifier
                            .scale(pressScale(pressed))
                            .clickable(interactionSource = interaction, indication = null) {
                                onOpen(bounds)
                            }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontFamily = Wyrm.Body,
                fontSize = 15.5.sp,
                color = Wyrm.Ink,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                Text(text = value, fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Quiet)
            }
            if (onOpen != null) {
                Spacer(Modifier.width(6.dp))
                Text(text = "›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = Wyrm.Chevron)
            }
        }
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    first: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        if (!first) SettingsHairline()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(if (danger && pressed) Wyrm.Badge.copy(alpha = 0.08f) else Color.Transparent)
                .scale(pressScale(pressed))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                fontFamily = Wyrm.Body,
                fontSize = 15.5.sp,
                color = if (danger) Wyrm.Badge else Wyrm.Ink,
            )
        }
    }
}

@Composable
internal fun SettingsLinkRow(
    title: String,
    value: String,
    first: Boolean,
    onClick: () -> Unit,
) {
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontFamily = Wyrm.Body,
                fontSize = 15.5.sp,
                color = Wyrm.Link,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                Text(text = value, fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Quiet)
            }
        }
    }
}

@Composable
internal fun PaperPrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .scale(pressScale(pressed, enabled = enabled))
            .clip(wyrmRounded(12.dp))
            .background(if (enabled) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
            color = Wyrm.OnInk,
        )
    }
}

@Composable
internal fun PaperOutlineButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .scale(pressScale(pressed, enabled = enabled))
            .clip(wyrmRounded(12.dp))
            .border(1.dp, Wyrm.Rule, wyrmRounded(12.dp))
            .background(if (pressed) Wyrm.Hover else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontSize = 15.sp,
            color = if (enabled) Wyrm.Mute else Wyrm.TabIdle,
        )
    }
}

@Composable
internal fun SettingsColourRow(
    setting: Setting,
    first: Boolean,
    onChange: (Setting, List<Float>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val channels = setting.channels
    val swatch = Color(channels[0], channels[1], channels[2], 1f)
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = setting.label, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                if (setting.hint.isNotBlank()) {
                    Text(
                        text = setting.hint,
                        fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(wyrmRounded(8.dp))
                    .background(swatch)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(8.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = Wyrm.Chevron)
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            PaperColourMixer(setting = setting, onChange = onChange)
        }
    }
}

@Composable
private fun PaperColourMixer(setting: Setting, onChange: (Setting, List<Float>) -> Unit) {
    val channels = setting.channels
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (channels[0] * 255).roundToInt().coerceIn(0, 255),
        (channels[1] * 255).roundToInt().coerceIn(0, 255),
        (channels[2] * 255).roundToInt().coerceIn(0, 255),
        hsv,
    )
    val withAlpha = setting.type == SettingType.COLOR4
    fun emit(h: Float, s: Float, v: Float, alpha: Float) {
        val packed = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        val values = listOf(
            android.graphics.Color.red(packed) / 255f,
            android.graphics.Color.green(packed) / 255f,
            android.graphics.Color.blue(packed) / 255f,
        )
        onChange(setting, if (withAlpha) values + alpha else values)
    }
    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        PaperHueTrack(hue = hsv[0]) { emit(it, hsv[1], hsv[2], channels[3]) }
        Spacer(Modifier.height(10.dp))
        PaperShadeTrack(
            label = "Strength",
            value = hsv[1],
            gradient = listOf(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2]))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2]))),
            ),
        ) { emit(hsv[0], it, hsv[2], channels[3]) }
        Spacer(Modifier.height(10.dp))
        PaperShadeTrack(
            label = "Brightness",
            value = hsv[2],
            gradient = listOf(
                Color.Black,
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))),
            ),
        ) { emit(hsv[0], hsv[1], it, channels[3]) }
        if (withAlpha) {
            Spacer(Modifier.height(10.dp))
            PaperShadeTrack(
                label = "Opacity",
                value = channels[3],
                gradient = listOf(
                    Color.Transparent,
                    Color(channels[0], channels[1], channels[2], 1f),
                ),
            ) { emit(hsv[0], hsv[1], hsv[2], it) }
        }
    }
}

@Composable
private fun PaperHueTrack(hue: Float, onPick: (Float) -> Unit) {
    val rainbow = Brush.horizontalGradient(
        (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) },
    )
    PaperGradientTrack(fraction = hue / 360f, brush = rainbow) { onPick(it * 360f) }
}

@Composable
private fun PaperShadeTrack(
    label: String,
    value: Float,
    gradient: List<Color>,
    onPick: (Float) -> Unit,
) {
    Column {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = Wyrm.Quiet,
        )
        Spacer(Modifier.height(6.dp))
        PaperGradientTrack(fraction = value, brush = Brush.horizontalGradient(gradient), onPick = onPick)
    }
}

/** Wyrm iOS's `WSGradientTrack`: a 22 dp gradient capsule and a 20 dp ink knob ringed in card. */
@Composable
private fun PaperGradientTrack(fraction: Float, brush: Brush, onPick: (Float) -> Unit) {
    val pick by androidx.compose.runtime.rememberUpdatedState(onPick)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(Unit) {
                val knob = 20.dp.toPx()
                val run = (size.width - knob).coerceAtLeast(1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(((down.position.x - knob / 2f) / run).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(((change.position.x - knob / 2f) / run).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        val run = maxWidth - 20.dp
        Box(
            Modifier
                .matchParentSize()
                .clip(WyrmCapsule)
                .background(brush)
                .border(1.dp, Wyrm.Rule, WyrmCapsule),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = run * fraction.coerceIn(0f, 1f))
                .size(20.dp)
                .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(CircleShape)
                .background(Wyrm.Ink)
                .border(2.dp, Wyrm.Card, CircleShape),
        )
    }
}

@Composable
internal fun SettingTypedRow(
    setting: Setting,
    first: Boolean,
    onChange: (Setting, List<Float>) -> Unit,
) {
    when (setting.type) {
        SettingType.BOOL -> SettingsBoolRow(
            title = setting.label,
            detail = setting.hint,
            on = setting.enabled,
            first = first,
            onToggle = { onChange(setting, listOf(if (it) 1f else 0f)) },
        )
        SettingType.ENUM -> SettingsEnumBlock(
            title = setting.label,
            detail = setting.hint,
            options = setting.options,
            selected = setting.index.coerceIn(0, (setting.options.size - 1).coerceAtLeast(0)),
            first = first,
            onSelect = { onChange(setting, listOf(it.toFloat())) },
        )
        SettingType.COLOR3, SettingType.COLOR4 -> SettingsColourRow(
            setting = setting,
            first = first,
            onChange = onChange,
        )
        SettingType.INT, SettingType.FLOAT -> {
            val intLike = setting.type == SettingType.INT
            val range = setting.minimum..setting.maximum
            val span = (setting.maximum - setting.minimum)
            val steps = if (intLike && span >= 1f) span.roundToInt() - 1 else 0
            SettingsSliderRow(
                title = setting.label,
                valueText = formatSettingValue(setting),
                detail = setting.hint,
                value = setting.number,
                range = range,
                steps = steps.coerceAtLeast(0),
                first = first,
                onChange = { raw ->
                    val written = if (intLike) raw.roundToInt().toFloat() else raw
                    onChange(setting, listOf(written))
                },
            )
        }
    }
}

internal fun formatSettingValue(setting: Setting): String = when (setting.type) {
    SettingType.BOOL -> if (setting.enabled) "On" else "Off"
    SettingType.ENUM -> setting.options.getOrNull(setting.index).orEmpty()
    SettingType.INT -> setting.number.roundToInt().toString()
    SettingType.FLOAT -> if (setting.id == "general.death_hold") {
        "%.2f s".format(setting.number)
    } else {
        "%.2f".format(setting.number)
    }
    SettingType.COLOR3, SettingType.COLOR4 -> ""
}

internal fun List<Setting>.named(id: String): Setting? = firstOrNull { it.id == id }
