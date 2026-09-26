package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import kotlin.math.roundToInt

/**
 * Spec page 07 — Skin tab.
 *
 * Basic on top: colour presets and the four old tabs as rows. Advanced tag
 * motion is folded. Pattern / Accessory / Tag / Background still open the
 * existing dark editor until those pages are named. Wear it commits and stays.
 */
@Composable
fun SkinScreen(
    tables: SkinTables,
    state: SkinState,
    background: Int,
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    showRootTabs: Boolean = true,
    unreadNotifications: Int,
    onAppear: () -> Unit,
    onPickPreset: (Int) -> Unit,
    onCodeChange: (String, IntArray) -> Unit,
    onPickAccessory: (Int) -> Unit,
    tagCode: String,
    tagFetchState: String,
    onTagCodeChange: (String) -> Unit,
    onFetchTag: (String) -> Unit,
    onPickBackground: (Int) -> Unit,
    onSettingChange: (Setting, List<Float>) -> Unit,
    onWear: () -> Unit,
    onPreview: (Boolean) -> Unit,
    onPreviewLayout: (centreY: Float, scale: Float) -> Unit,
    livePreview: Boolean = true,
    onTabNotifications: (Rect) -> Unit,
    onTabPlay: (Rect) -> Unit,
    onTabSocial: (Rect) -> Unit,
    onTabSettings: (Rect) -> Unit,
) {
    LaunchedEffect(Unit) { onAppear() }
    DisposableEffect(livePreview) {
        if (livePreview) onPreview(true)
        onDispose { if (livePreview) onPreview(false) }
    }
    var section by remember { mutableIntStateOf(if (state.custom) 1 else 0) }
    var holeInRoot by remember { mutableStateOf(Rect.Zero) }
    var rootInRoot by remember { mutableStateOf(Offset.Zero) }
    val skinAtlas by rememberSkinAtlas()
    val previewLabel = if (state.custom) "Custom" else "Preset ${(state.preset + 1).toString().padStart(2, '0')}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootInRoot = it.positionInRoot() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (livePreview && holeInRoot.width > 1f && holeInRoot.height > 1f) {
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(
                            holeInRoot.left - rootInRoot.x,
                            holeInRoot.top - rootInRoot.y,
                        ),
                        size = Size(holeInRoot.width, holeInRoot.height),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
            .background(Wyrm.Paper),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = insetTop),
        ) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp)) {
                Text(
                    text = "Loadout",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.92.sp,
                    color = Wyrm.Quiet,
                )
                Text(
                    text = "Skin",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            PreviewCard(
                label = previewLabel,
                livePreview = livePreview,
                onHole = { holeInRoot = it },
                onLayout = onPreviewLayout,
            )

            SkinCategoryTabs(selected = section, onSelect = { section = it })
            when (section) {
                0 -> {
                    SkinSectionLabel(
                        if (tables.presetCount == 0) "Default skins" else "Default skins · ${tables.presetCount}",
                        top = 18.dp,
                    )
                    BasicCard {
                        PresetList(
                            tables = tables,
                            selected = if (state.custom) -1 else state.preset,
                            atlas = skinAtlas,
                            onPick = onPickPreset,
                        )
                    }
                }
                1 -> SkinPatternScreen(
                    tables = tables,
                    state = state,
                    insetTop = 0.dp,
                    insetBottom = 0.dp,
                    onBack = {},
                    onCodeChange = onCodeChange,
                    onWear = onWear,
                    embedded = true,
                )
                2 -> SkinAccessoryContent(worn = state.accessory, onPick = onPickAccessory)
                3 -> SkinTagInline(
                    settings = settings,
                    tagCode = tagCode,
                    tagFetchState = tagFetchState,
                    onSettingChange = onSettingChange,
                    onTagCodeChange = onTagCodeChange,
                    onFetchTag = onFetchTag,
                )
                else -> SkinBackgroundContent(selected = background, onPick = onPickBackground)
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Wyrm.Paper.copy(alpha = 0.94f)),
        ) {
            WearButton(onClick = onWear)
            Spacer(Modifier.height(10.dp))
            if (showRootTabs) {
                RootTabs(
                    selected = RootTab.SKIN,
                    insetBottom = insetBottom,
                    unreadNotifications = unreadNotifications,
                    onNotifications = onTabNotifications,
                    onPlay = onTabPlay,
                    onSocial = onTabSocial,
                    onSkin = {},
                    onSettings = onTabSettings,
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    label: String,
    livePreview: Boolean,
    onHole: (Rect) -> Unit,
    onLayout: (centreY: Float, scale: Float) -> Unit,
) {
    val shape = wyrmRounded(16.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "PREVIEW",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.8.sp,
                color = Wyrm.Quiet,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = label.uppercase(),
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.8.sp,
                color = Wyrm.Quiet,
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    onHole(bounds)
                    if (livePreview) {
                        val centreY = bounds.top + bounds.height / 2f
                        val scale = coordinates.size.width * 0.94f / 22.17f
                        onLayout(centreY, scale)
                    }
                },
        )
    }
}

@Composable
private fun BasicCard(content: @Composable () -> Unit) {
    val shape = wyrmRounded(14.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
    ) {
        content()
    }
}

@Composable
private fun SkinCategoryTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Default", "Custom", "Accessories", "Tags", "Arena")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Text(
                text = label,
                fontFamily = Wyrm.Body,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.5.sp,
                color = if (active) Wyrm.OnInk else Wyrm.Mute,
                modifier = Modifier
                    .clip(wyrmRounded(18.dp))
                    .background(if (active) Wyrm.Ink else Wyrm.Card)
                    .border(1.dp, if (active) Wyrm.Ink else Wyrm.Rule, wyrmRounded(18.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun SkinTagInline(
    settings: List<Setting>,
    tagCode: String,
    tagFetchState: String,
    onSettingChange: (Setting, List<Float>) -> Unit,
    onTagCodeChange: (String) -> Unit,
    onFetchTag: (String) -> Unit,
) {
    val atlas by rememberTagAtlas()
    val chosen = settings.firstOrNull { it.id == "tags.index" }
    val selected = chosen?.number?.roundToInt() ?: -1
    val chain = settings.firstOrNull { it.id == "tags.chain" }
    val swing = settings.firstOrNull { it.id == "tags.swing" }
    val scale = settings.firstOrNull { it.id == "tags.scale" }
    val shrink = settings.firstOrNull { it.id == "tags.small" }
    val teamOnly = settings.firstOrNull { it.id == "tags.team_only" }
    val hideAll = settings.firstOrNull { it.id == "tags.hidden" }

    SkinSectionLabel("Pick a tag", top = 18.dp)
    SkinCard {
        Column(Modifier.padding(14.dp)) {
            val columns = 4
            val cells = TAG_ART.size + 1
            val rows = (cells + columns - 1) / columns
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until columns) {
                        val cell = row * columns + column
                        if (cell < cells) {
                            val index = cell - 1
                            TagPickCell(
                                atlas = atlas,
                                index = index,
                                selected = index == selected,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        end = if (column < columns - 1) 10.dp else 0.dp,
                                        bottom = if (row < rows - 1) 10.dp else 0.dp,
                                    ),
                                onPick = { chosen?.let { onSettingChange(it, listOf(index.toFloat())) } },
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    SkinSectionLabel("How it moves")
    SkinCard {
        if (chain != null) SliderRow(chain, "How far it trails behind the head.", true, onSettingChange)
        if (swing != null) SliderRow(swing, "", chain == null, onSettingChange)
        if (scale != null) SliderRow(scale, "", chain == null && swing == null, onSettingChange)
    }

    SkinSectionLabel("Visibility")
    SkinCard {
        if (shrink != null) {
            SwitchRow("Shrink large tags", "Keeps the arena readable.", shrink.enabled, true) {
                onSettingChange(shrink, listOf(if (it) 1f else 0f))
            }
        }
        if (teamOnly != null) {
            SwitchRow("Team tags only", "Hides tags outside your team.", teamOnly.enabled, shrink == null) {
                onSettingChange(teamOnly, listOf(if (it) 1f else 0f))
            }
        }
        if (hideAll != null) {
            SwitchRow("Hide all tags", "Including your own.", hideAll.enabled, shrink == null && teamOnly == null) {
                onSettingChange(hideAll, listOf(if (it) 1f else 0f))
            }
        }
    }

    SkinSectionLabel("Private tag")
    SkinCard {
        Column(Modifier.padding(14.dp)) {
            val shape = wyrmRounded(10.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shape)
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, shape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = tagCode,
                    onValueChange = onTagCodeChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Ink),
                    cursorBrush = SolidColor(Wyrm.Ink),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (tagCode.isEmpty()) Text("id password", fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.TabIdle)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(wyrmRounded(11.dp))
                    .background(Wyrm.Ink)
                    .clickable { onFetchTag(tagCode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tagFetchState.ifEmpty { "Fetch tag" },
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Wyrm.OnInk,
                )
            }
        }
    }
}

@Composable
private fun PresetList(
    tables: SkinTables,
    selected: Int,
    atlas: ImageBitmap?,
    onPick: (Int) -> Unit,
) {
    val count = tables.presetCount
    if (count == 0) {
        Text(
            text = "Presets load with the engine.",
            fontFamily = Wyrm.Body,
            fontSize = 13.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
        return
    }
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        for (preset in 0 until count) {
            if (preset > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp)
                        .height(1.dp)
                        .background(Wyrm.RowRule),
                )
            }
            OriginalPresetRow(
                ordinal = preset + 1,
                groups = tables.presetGroups(preset),
                colours = tables.presetColours(preset),
                atlas = atlas,
                selected = preset == selected,
                onClick = { onPick(preset) },
            )
        }
    }
}

/** One engine preset in its untouched order, rendered as the body it becomes. */
@Composable
private fun OriginalPresetRow(
    ordinal: Int,
    groups: List<Int>,
    colours: List<Color>,
    atlas: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale(pressed))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ordinal.toString().padStart(2, '0'),
            fontFamily = Wyrm.Body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.5.sp,
            color = if (selected) Wyrm.Ink else Wyrm.Quiet,
            modifier = Modifier.width(34.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(wyrmRounded(17.dp))
                .background(Wyrm.Well.copy(alpha = 0.62f))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Wyrm.Ink else Color(0x1637352F),
                    wyrmRounded(17.dp),
                )
                .padding(if (selected) 3.dp else 2.dp)
                .clip(wyrmRounded(15.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (groups.isEmpty()) return@Canvas
                val bead = size.height
                val step = bead * 0.42f
                val beads = kotlin.math.ceil(size.width / step).toInt() + 1
                for (index in 0 until beads) {
                    if (atlas != null) {
                        drawBead(atlas, groups[index % groups.size], index * step, 0f, bead)
                    } else if (colours.isNotEmpty()) {
                        drawCircle(
                            color = colours[index % colours.size],
                            radius = bead / 2f,
                            center = Offset(index * step + bead / 2f, bead / 2f),
                        )
                    }
                }
            }
        }
        Text(
            text = if (selected) "✓" else "",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.Ink,
            modifier = Modifier.width(24.dp).padding(start = 8.dp),
        )
    }
}

@Composable
private fun DrillRow(title: String, value: String, first: Boolean = false, onOpen: (Rect) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column {
        if (!first) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .scale(pressScale(pressed))
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .clickable(interactionSource = interaction, indication = null) { onOpen(bounds) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontFamily = Wyrm.Body,
                fontSize = 15.5.sp,
                color = Wyrm.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                color = Wyrm.Quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = Wyrm.Chevron)
        }
    }
}

@Composable
private fun AdvancedHeader(open: Boolean, onToggle: () -> Unit) {
    AdvancedFold(label = "Advanced · tag motion", open = open, onToggle = onToggle)
}

@Composable
private fun AdvancedCard(content: @Composable () -> Unit) {
    val shape = wyrmRounded(14.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
    ) {
        content()
    }
}

@Composable
internal fun SliderRow(
    setting: Setting,
    caption: String,
    first: Boolean,
    onChange: (Setting, List<Float>) -> Unit,
) {
    val nums = TextStyle(fontFeatureSettings = "tnum")
    Column(modifier = Modifier.padding(14.dp)) {
        if (!first) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Wyrm.RowRule)
                    .padding(bottom = 0.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = setting.label.ifBlank { setting.id.substringAfter('.') }.replaceFirstChar { it.uppercase() },
                fontFamily = Wyrm.Body,
                fontSize = 15.5.sp,
                color = Wyrm.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%.2f".format(setting.number),
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                color = Wyrm.Mute,
                style = nums,
            )
        }
        if (caption.isNotBlank()) {
            Text(
                text = caption,
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                color = Wyrm.Quiet,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        LiquidSlider(
            value = setting.number.coerceIn(setting.minimum, setting.maximum),
            onValueChange = { onChange(setting, listOf(it)) },
            valueRange = setting.minimum..setting.maximum,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    detail: String,
    on: Boolean,
    first: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        if (!first) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onToggle(!on) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                Text(
                    text = detail,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            PaperSwitch(on = on, onToggle = onToggle)
        }
    }
}

@Composable
internal fun PaperSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    LiquidSwitch(on = on, onToggle = onToggle)
}

@Composable
internal fun WearButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .fillMaxWidth()
            .height(46.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(12.dp))
            .background(Wyrm.Ink)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Wear it",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
            color = Wyrm.OnInk,
        )
    }
}
