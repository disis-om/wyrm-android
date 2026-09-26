package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import com.composables.icons.lucide.R as LucideR

private data class ArrowOption(val label: String, val points: List<Offset>)

enum class ControlsTab { SNAKE, ON_SCREEN_BUTTONS }

/* Mirrored in `mobile_controls.c`: the preview and the arena are deliberately
 * fed the same normalized silhouettes rather than two artistic guesses. */
private val ArrowOptions = listOf(
    ArrowOption(
        "CLASSIC",
        listOf(
            Offset(0.66f, 0f), Offset(0.08f, -0.56f), Offset(0.01f, -0.24f),
            Offset(-0.52f, -0.24f), Offset(-0.52f, 0.24f),
            Offset(0.01f, 0.24f), Offset(0.08f, 0.56f),
        ),
    ),
    ArrowOption(
        "CLASSIC\nWIDE",
        listOf(
            Offset(0.72f, 0f), Offset(0.02f, -0.72f), Offset(-0.06f, -0.30f),
            Offset(-0.58f, -0.30f), Offset(-0.58f, 0.30f),
            Offset(-0.06f, 0.30f), Offset(0.02f, 0.72f),
        ),
    ),
    ArrowOption(
        "NEEDLE",
        listOf(
            Offset(0.82f, 0f), Offset(0.05f, -0.22f), Offset(0.16f, -0.075f),
            Offset(-0.72f, -0.075f), Offset(-0.72f, 0.075f),
            Offset(0.16f, 0.075f), Offset(0.05f, 0.22f),
        ),
    ),
    ArrowOption(
        "BLADE",
        listOf(
            Offset(0.78f, 0f), Offset(0.12f, -0.42f), Offset(-0.10f, -0.22f),
            Offset(-0.25f, -0.16f), Offset(-0.70f, 0f),
            Offset(-0.25f, 0.16f), Offset(-0.10f, 0.22f), Offset(0.12f, 0.42f),
        ),
    ),
    ArrowOption(
        "TRIANGLE",
        listOf(Offset(0.82f, 0f), Offset(-0.64f, -0.26f), Offset(-0.64f, 0.26f)),
    ),
)

/**
 * Spec page 10 — Settings › Controls.
 *
 * Steering only. On-screen buttons are their own page. Preview is the same
 * silhouettes the arena uses.
 */
@Composable
fun ControlsScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String = "Settings",
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
    onEditLayout: () -> Unit,
    onResetLayout: () -> Unit,
    workspaceTab: ControlsWorkspaceTab? = null,
    onWorkspaceTab: (ControlsWorkspaceTab) -> Unit = {},
    contentOnly: Boolean = false,
) {
    val steeringSetting = settings.named("controls.joystick_mode")
    val steering = steeringSetting?.index ?: 0
    val arrowSteering = steering == 2
    val opacity = settings.named("controls.opacity")
    val joystickSize = settings.named("controls.joystick_size")
    val boostSize = settings.named("controls.boost_size")
    val boostMode = settings.named("controls.boost_mode")
    val zoomOn = settings.named("controls.zoom_enabled")
    val boostButton = (boostMode?.index ?: 0) == 1
    val zoomVertical = settings.named("controls.zoom_orientation")?.index == 1
    val zoomLength = settings.named("controls.zoom_length")?.number ?: 1f
    val joystickPosition = settings.previewPosition("layout.joystick_x", "layout.joystick_y")
    val boostPosition = settings.previewPosition("layout.boost_x", "layout.boost_y")
    val zoomPosition = settings.previewPosition("layout.zoom_x", "layout.zoom_y")
    val arrowStyleSetting = settings.named("arrow.style")
    val arrowStyle = (arrowStyleSetting?.index ?: 0).coerceIn(0, ArrowOptions.lastIndex)
    val arrowSize = settings.named("arrow.size")?.number ?: 1f
    val arrowChannels = settings.named("arrow.color")?.channels ?: listOf(1f, 1f, 1f, 1f)
    val arrowColor = Color(
        red = arrowChannels[0].coerceIn(0f, 1f),
        green = arrowChannels[1].coerceIn(0f, 1f),
        blue = arrowChannels[2].coerceIn(0f, 1f),
        alpha = 1f,
    )
    val handedness = settings.named("controls.handedness")
    val zoomRows = settings.filter { it.group == "controls.zoom" }
    val arrowRows = settings.filter { it.group == "controls.arrow" }
    var zoomOpen by remember { mutableStateOf(false) }
    var behaviourOpen by remember { mutableStateOf(false) }

    SettingsDrillScaffold(
        title = "Controls",
        parent = backLabel,
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
        sectionTabs = workspaceTab?.let { selected ->
            { ControlsWorkspaceTabs(selected = selected, onSelect = onWorkspaceTab) }
        },
        contentOnly = contentOnly,
    ) {
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            ControlsPreview(
                showJoystick = !arrowSteering,
                showArrow = arrowSteering,
                showBoost = boostButton,
                showZoom = zoomOn?.enabled ?: true,
                opacity = opacity?.number ?: 1f,
                joystickSize = joystickSize?.number ?: 1f,
                boostSize = boostSize?.number ?: 1f,
                joystickPosition = joystickPosition,
                boostPosition = boostPosition,
                zoomPosition = zoomPosition,
                zoomVertical = zoomVertical,
                zoomLength = zoomLength,
                arrowStyle = arrowStyle,
                arrowSize = arrowSize,
                arrowColor = arrowColor,
            )
        }

        SettingsSectionLabel("Basic · steering")
        SettingsCard {
            SettingsEnumBlock(
                title = "Steering style",
                detail = "",
                options = listOf("Joystick", "Arrow"),
                selected = if (arrowSteering) 1 else 0,
                first = true,
                onSelect = { pick ->
                    steeringSetting?.let { setting ->
                        if (pick == 1) {
                            onChange(setting, listOf(2f))
                        } else {
                            val joystick = if (steering in 0..1) steering else 0
                            onChange(setting, listOf(joystick.toFloat()))
                        }
                    }
                },
            )
            if (!arrowSteering && steeringSetting != null) {
                val behaviour = steeringSetting.options.take(2)
                if (behaviour.size >= 2) {
                    SettingsValueRow(
                        title = "Joystick behaviour",
                        value = behaviour.getOrElse(steering.coerceIn(0, 1)) { behaviour.first() },
                        first = false,
                        onOpen = { behaviourOpen = !behaviourOpen },
                    )
                    AnimatedVisibility(visible = behaviourOpen) {
                        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                            PaperSegmented(
                                options = behaviour,
                                selected = steering.coerceIn(0, 1),
                                onSelect = { onChange(steeringSetting, listOf(it.toFloat())) },
                            )
                        }
                    }
                }
            }
            handedness?.let { setting ->
                SettingsEnumBlock(
                    title = setting.label,
                    detail = setting.hint,
                    options = listOf("Left", "Right"),
                    selected = setting.index.coerceIn(0, 1),
                    first = false,
                    onSelect = { onChange(setting, listOf(it.toFloat())) },
                )
            }
            boostMode?.let { setting ->
                SettingsEnumBlock(
                    title = "Boost",
                    detail = setting.hint,
                    options = setting.options,
                    selected = setting.index.coerceIn(0, (setting.options.size - 1).coerceAtLeast(0)),
                    first = false,
                    onSelect = { onChange(setting, listOf(it.toFloat())) },
                )
            }
        }

        SettingsSectionLabel("Basic · size")
        SettingsCard {
            var first = true
            if (!arrowSteering) {
                joystickSize?.let {
                    SettingTypedRow(it, first = first, onChange = onChange)
                    first = false
                }
            }
            if (boostButton) {
                boostSize?.let {
                    SettingTypedRow(it, first = first, onChange = onChange)
                    first = false
                }
            }
            opacity?.let { SettingTypedRow(it, first = first, onChange = onChange) }
        }

        if (arrowSteering && arrowRows.isNotEmpty()) {
            SettingsSectionLabel("Basic · arrow")
            SettingsCard {
                arrowRows.forEachIndexed { index, setting ->
                    SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
                }
            }
        }

        if (zoomRows.isNotEmpty()) {
            AdvancedFold(label = "Advanced · zoom bar", open = zoomOpen, onToggle = { zoomOpen = !zoomOpen })
            if (zoomOpen) {
                SettingsCard {
                    zoomRows.forEachIndexed { index, setting ->
                        SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
            PaperPrimaryButton(label = "Arrange the layout", onClick = onEditLayout)
            Spacer(Modifier.height(9.dp))
            PaperOutlineButton(label = "Reset positions", onClick = onResetLayout)
        }
        SettingsCaption("Opens sideways, the way you hold the phone in a match.")
    }
}

@Composable
private fun SteeringSelector(setting: Setting, onChange: (List<Float>) -> Unit) {
    val arrowSelected = setting.index == 2
    var joystickMode by remember { mutableIntStateOf(setting.index.takeIf { it in 0..1 } ?: 0) }
    LaunchedEffect(setting.index) {
        if (setting.index in 0..1) joystickMode = setting.index
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        WyrmLabel("steering")
        Spacer(Modifier.height(4.dp))
        SteeringChoiceRow(
            label = "Joystick",
            selected = !arrowSelected,
            dropdown = {
                GlassDropdown(
                    selected = setting.options.getOrElse(joystickMode) { "Joystick under finger" },
                    options = setting.options.take(2),
                    onSelect = { index ->
                        joystickMode = index
                        onChange(listOf(index.toFloat()))
                    },
                )
            },
            onSelect = { onChange(listOf(joystickMode.toFloat())) },
        )
        WyrmRule()
        SteeringChoiceRow(
            label = "Arrow",
            selected = arrowSelected,
            onSelect = { onChange(listOf(2f)) },
        )
    }
}

private fun List<Setting>.previewPosition(xId: String, yId: String): Offset =
    sanitizeLayoutPosition(
        Offset(
            firstOrNull { it.id == xId }?.number ?: 0.5f,
            firstOrNull { it.id == yId }?.number ?: 0.7f,
        )
    )

@Composable
private fun HandednessRow(setting: Setting, onChange: (List<Float>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Handedness",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.White,
            modifier = Modifier.weight(1f),
        )
        WyrmCompactSegmentedSelector(
            options = listOf("LEFT", "RIGHT"),
            selected = setting.index.coerceIn(0, 1),
            modifier = Modifier.width(142.dp),
            onSelect = { onChange(listOf(it.toFloat())) },
        )
    }
}

@Composable
internal fun WyrmCompactSegmentedSelector(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(wyrmRounded(Wyrm.Pill))
            .background(glassFill())
            .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill)),
    ) {
        options.forEachIndexed { index, label ->
            val active = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(if (active) Wyrm.SoftWhite else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 0.55.sp,
                    color = if (active) Wyrm.Black else Wyrm.SoftWhite,
                )
            }
        }
    }
}

@Composable
private fun SteeringChoiceRow(
    label: String,
    selected: Boolean,
    dropdown: (@Composable () -> Unit)? = null,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.White,
            modifier = Modifier.weight(1f),
        )
        dropdown?.invoke()
        if (dropdown != null) Spacer(Modifier.width(10.dp))
        SelectionTicker(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun SelectionTicker(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) SolidColor(Wyrm.SoftWhite) else glassFill())
            .border(
                1.dp,
                if (selected) SolidColor(Wyrm.White) else glassEdge(),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Wyrm.Black))
    }
}

@Composable
private fun GlassDropdown(
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "dropdown chevron",
    )
    Box(modifier = modifier.widthIn(min = 138.dp, max = 174.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(wyrmRounded(Wyrm.CornerSmall))
                .background(glassFill())
                .border(1.dp, glassEdge(), wyrmRounded(Wyrm.CornerSmall))
                .clickable { expanded = !expanded }
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Wyrm.SoftWhite,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_chevron_down),
                contentDescription = if (expanded) "Close options" else "Open options",
                tint = Wyrm.SoftWhite,
                modifier = Modifier.size(15.dp).rotate(chevronRotation),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(174.dp)
                .background(Color(0xF2161917))
                .border(1.dp, glassEdge(), wyrmRounded(Wyrm.CornerSmall)),
        ) {
            options.forEachIndexed { index, option ->
                val active = index == options.indexOf(selected)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontFamily = Wyrm.Body,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Wyrm.SoftWhite,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                    trailingIcon = {
                        if (active) {
                            Icon(
                                painter = painterResource(LucideR.drawable.lucide_ic_check),
                                contentDescription = "Selected",
                                tint = Wyrm.SoftWhite,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DropdownSettingRow(setting: Setting, onChange: (List<Float>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = setting.label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.White,
            modifier = Modifier.weight(1f),
        )
        GlassDropdown(
            selected = setting.options.getOrElse(setting.index) { setting.options.firstOrNull().orEmpty() },
            options = setting.options,
            onSelect = { onChange(listOf(it.toFloat())) },
        )
    }
}

@Composable
private fun SettingsRowsBlock(
    label: String,
    rows: List<Setting>,
    dropdownIds: Set<String> = emptySet(),
    onChange: (Setting, List<Float>) -> Unit,
) {
    if (rows.isEmpty()) return
    Spacer(Modifier.height(Wyrm.Gap))
    WyrmLabel(label)
    Spacer(Modifier.height(4.dp))
    rows.forEach { setting ->
        WyrmRule()
        if (setting.id in dropdownIds) {
            DropdownSettingRow(setting = setting, onChange = { onChange(setting, it) })
        } else {
            SettingRow(setting = setting, onChange = { onChange(setting, it) })
        }
    }
    WyrmRule()
}

/** Five compact cells deliberately share one row; there is no sideways scroll. */
@Composable
private fun ArrowStylePicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(
            text = "Arrow style",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.White,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ArrowOptions.forEachIndexed { index, option ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val active = selected == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(78.dp)
                        .scale(pressScale(pressed))
                        .wyrmSegment(active, Wyrm.CornerSmall)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(index) },
                        )
                        .padding(horizontal = 3.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(38.dp)) {
                        drawArrowShape(
                            style = index,
                            length = size.width * 0.78f,
                            width = size.height * 0.72f,
                            fill = if (active) Wyrm.Black else Wyrm.White,
                            outline = if (active) Wyrm.Faint else Color(0xFF050708),
                            alpha = 0.96f,
                            outlineWidth = 1.2.dp.toPx(),
                        )
                    }
                    Text(
                        text = option.label,
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 6.5.sp,
                        lineHeight = 7.5.sp,
                        letterSpacing = 0.35.sp,
                        textAlign = TextAlign.Center,
                        color = if (active) Wyrm.Black else Wyrm.SoftWhite,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** The controls as they will actually appear, over a hint of arena. */
@Composable
private fun ControlsPreview(
    showJoystick: Boolean,
    showArrow: Boolean,
    showBoost: Boolean,
    showZoom: Boolean,
    opacity: Float,
    joystickSize: Float,
    boostSize: Float,
    joystickPosition: Offset,
    boostPosition: Offset,
    zoomPosition: Offset,
    zoomVertical: Boolean,
    zoomLength: Float,
    arrowStyle: Int,
    arrowSize: Float,
    arrowColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(wyrmRounded(10.dp))
            .background(Wyrm.Well)
            .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp)),
    ) {
        Text(
            text = "PREVIEW",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(12.dp),
        )
        if (showJoystick) {
            PreviewPositioned(position = joystickPosition) {
                PaperJoystick(
                    diameter = (60 * joystickSize).dp,
                    opacity = opacity,
                )
            }
        }
        if (showArrow) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
                drawArrowShape(
                    style = arrowStyle,
                    length = 52.dp.toPx() * arrowSize,
                    // The arena uses 52 px; the preview's shorter portrait
                    // window uses the same multiplier on a 42 dp base so the
                    // new 2.4x maximum remains completely visible.
                    width = 30.dp.toPx() * arrowSize,
                    fill = arrowColor,
                    outline = Color(0xFF040609),
                    alpha = opacity.coerceIn(0f, 1f),
                    outlineWidth = 2.dp.toPx(),
                )
            }
        }
        if (showBoost) {
            PreviewPositioned(position = boostPosition) {
                PaperBoostButton(
                    diameter = (46 * boostSize).dp,
                    opacity = opacity,
                )
            }
        }
        if (showZoom) {
            PreviewPositioned(position = zoomPosition) {
                PaperZoomBar(
                    length = (102 * zoomLength).dp,
                    vertical = zoomVertical,
                    opacity = opacity,
                    value = 0.45f,
                )
            }
        }
    }
}

/** Maps the editor's normalized landscape position into the preview rectangle. */
@Composable
internal fun PreviewPositioned(position: Offset, content: @Composable () -> Unit) {
    Layout(modifier = Modifier.fillMaxSize(), content = content) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0),
        )
        val origin = previewItemTopLeft(
            position = position,
            container = Offset(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
            child = Offset(placeable.width.toFloat(), placeable.height.toFloat()),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(origin.x.toInt(), origin.y.toInt())
        }
    }
}

private fun DrawScope.drawArrowShape(
    style: Int,
    length: Float,
    width: Float,
    fill: Color,
    outline: Color,
    alpha: Float,
    outlineWidth: Float,
) {
    val shape = ArrowOptions[style.coerceIn(0, ArrowOptions.lastIndex)].points
    val path = Path()
    shape.forEachIndexed { index, point ->
        val x = center.x + point.x * length
        val y = center.y + point.y * width
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = fill.copy(alpha = alpha))
    drawPath(
        path = path,
        color = outline.copy(alpha = alpha),
        style = Stroke(width = outlineWidth),
    )
}

/** A quiet full-width action for the things that undo rather than do. */
@Composable
fun OutlineAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .alpha(if (enabled) 1f else 0.46f)
            .clip(wyrmRounded(Wyrm.Pill))
            .background(glassFill())
            .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Wyrm.SoftWhite,
                    strokeWidth = 1.5.dp,
                )
            }
            Text(
                text = label.uppercase(),
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = Wyrm.SoftWhite,
            )
        }
    }
}
