package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Wyrm iOS's Skin studio (WyrmSkinStudio.swift), for Compose.
 *
 * The preview is drawn here, from the engine's own atlas — two rows of 128
 * beads, the eyes, the accessory and a swinging tag — on the paper itself,
 * not in a card and not by the engine. The engine keeps owning what is worn:
 * every change goes to it at once, it saves it, and it tells us back.
 */

/* Tags are switched off until Wyrm's own backend serves them: announcing NTL
   tags got snakes dropped from the arena. Flip to false to bring the picker back. */
private const val TAGS_DISABLED = true

private enum class SkinSection(val title: String) {
    OVERVIEW("Skin wardrobe"), PRESETS("Default skins"), PATTERN("Pattern"),
    ACCESSORIES("Accessory"), TAGS("Tag"), BACKGROUND("Arena background"),
}

private fun Int.rgbColor(): Color = Color(((this shr 16) and 0xFF) / 255f, ((this shr 8) and 0xFF) / 255f, (this and 0xFF) / 255f)

@Composable
fun IosSkinScreen(
    state: SkinState,
    background: Int,
    settings: List<Setting>,
    insetTop: Dp,
    onPickPreset: (Int) -> Unit,
    onCode: (code: String, colours: IntArray) -> Unit,
    onPickAccessory: (Int) -> Unit,
    onPickBackground: (Int) -> Unit,
    onSettingChange: (Setting, List<Float>) -> Unit,
) {
    val textures by rememberSkinTextures()
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("wyrm_skin_studio", android.content.Context.MODE_PRIVATE)
    var section by remember { mutableStateOf(SkinSection.OVERVIEW) }
    var editingPattern by remember { mutableStateOf(false) }
    var showingWheel by remember { mutableStateOf(false) }

    // What is worn, mirrored locally so a tap shows at once; the engine's echo re-syncs it.
    var preset by remember { mutableIntStateOf(state.preset) }
    var customEnabled by remember { mutableStateOf(state.custom) }
    var pattern by remember { mutableStateOf(state.code.mapNotNull { SkinCatalog.group(it) }) }
    var patternColors by remember { mutableStateOf(List(state.code.length) { state.colourAt(it) }) }
    var accessory by remember { mutableIntStateOf(state.accessory) }
    var backgroundId by remember { mutableIntStateOf(background) }
    LaunchedEffect(state) {
        preset = state.preset
        customEnabled = state.custom && state.code.isNotEmpty()
        val groups = state.code.mapNotNull { SkinCatalog.group(it) }
        pattern = groups
        patternColors = List(groups.size) { state.colourAt(it) }
        accessory = state.accessory
    }
    LaunchedEffect(background) { backgroundId = background }

    val tagSetting = settings.firstOrNull { it.id == "tags.index" }
    val chainSetting = settings.firstOrNull { it.id == "tags.chain" }
    val swingSetting = settings.firstOrNull { it.id == "tags.swing" }
    val scaleSetting = settings.firstOrNull { it.id == "tags.scale" }
    var tag by remember { mutableIntStateOf(if (TAGS_DISABLED) -1 else tagSetting?.number?.roundToInt() ?: -1) }
    LaunchedEffect(tagSetting?.number) { tag = if (TAGS_DISABLED) -1 else tagSetting?.number?.roundToInt() ?: -1 }
    val chain = chainSetting?.number?.toDouble() ?: 1.0
    val swing = swingSetting?.number?.toDouble() ?: 1.0
    val tagScale = scaleSetting?.number?.toDouble() ?: 1.0

    val customGroups = pattern.filter { it in SkinCatalog.validGroups }.take(256)
    val customColors = List(customGroups.size) { patternColors.getOrElse(it) { 0 } }
    val activeSource = if (customEnabled && customGroups.isNotEmpty()) customGroups
        else SkinCatalog.presets.getOrNull(preset) ?: listOf(7)
    val activeGroups = List(256) { activeSource[it % activeSource.size] }
    val activeColors = if (customEnabled && customGroups.isNotEmpty()) List(256) { customColors[it % customColors.size] } else List(256) { 0 }
    val previewGroups = if (editingPattern) List(256) { if (it < customGroups.size) customGroups[it] else -1 } else activeGroups
    val previewColors = if (editingPattern) List(256) { if (it < customGroups.size) customColors[it] else 0 } else activeColors

    fun savePattern(groups: List<Int>, colors: List<Int>) {
        editingPattern = true
        pattern = groups
        patternColors = colors.take(groups.size)
        customEnabled = groups.isNotEmpty()
        onCode(SkinCatalog.code(groups), IntArray(groups.size) { colors.getOrElse(it) { 0 } })
    }

    fun enter(target: SkinSection) {
        if (target != SkinSection.PATTERN && editingPattern) editingPattern = false
        section = target
    }

    Column(Modifier.fillMaxSize().background(Wyrm.Paper).padding(top = insetTop)) {
        IosScreenHeader(kicker = "WYRM", title = "Skin")
        SkinPreview(
            textures = textures,
            groups = previewGroups,
            colors = previewColors,
            preset = preset,
            custom = customEnabled,
            accessoryId = accessory,
            tagId = tag,
            backgroundId = backgroundId,
            chain = chain,
            swing = swing,
            tagScale = tagScale,
            modifier = Modifier.fillMaxWidth().height(218.dp),
        )
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                (fadeIn(iosSpring(0.42f, 0.86f)) + scaleIn(iosSpring(0.42f, 0.86f), initialScale = 0.985f, transformOrigin = TransformOrigin(0.5f, 0f))) togetherWith
                    (fadeOut(iosSpring(0.42f, 0.86f)) + scaleOut(iosSpring(0.42f, 0.86f), targetScale = 0.985f, transformOrigin = TransformOrigin(0.5f, 0f)))
            },
            modifier = Modifier.weight(1f),
            label = "skin-section",
        ) { shown ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (shown) {
                    SkinSection.OVERVIEW -> {
                        IosSectionLabel("Build your Wyrm")
                        IosPaperCard {
                            IosListRow(SkinSection.PRESETS.title, value = "${SkinCatalog.presets.size}") { enter(SkinSection.PRESETS) }
                            IosListRow(SkinSection.PATTERN.title, value = if (customEnabled) "Custom" else "Preset") { enter(SkinSection.PATTERN) }
                            IosListRow(SkinSection.ACCESSORIES.title, value = if (accessory < 0) "None" else "%02d".format(accessory + 1)) { enter(SkinSection.ACCESSORIES) }
                            IosListRow(SkinSection.TAGS.title, value = if (TAGS_DISABLED) "Coming soon" else SkinCatalog.tags.getOrNull(tag)?.let { "#${it.ntlId}" } ?: "None") { if (!TAGS_DISABLED) enter(SkinSection.TAGS) }
                            IosListRow(SkinSection.BACKGROUND.title, value = SkinCatalog.backgrounds.getOrNull(backgroundId)?.label ?: "Wyrm") { enter(SkinSection.BACKGROUND) }
                        }
                    }
                    SkinSection.PRESETS -> {
                        InlineHeader(shown.title) { enter(SkinSection.OVERVIEW) }
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            SkinCatalog.presets.forEachIndexed { index, groups ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(57.dp)
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            preset = index
                                            customEnabled = false
                                            onPickPreset(index)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        "%02d".format(index + 1),
                                        fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                                        color = if (!customEnabled && preset == index) Wyrm.Live else Wyrm.Quiet,
                                        modifier = Modifier.widthIn(min = 24.dp),
                                    )
                                    MiniSnake(textures, groups, Modifier.weight(1f).height(38.dp))
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
                            }
                        }
                    }
                    SkinSection.PATTERN -> {
                        InlineHeader(shown.title) { enter(SkinSection.OVERVIEW) }
                        Row(
                            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${customGroups.size} / 256 beads", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Wyrm.Quiet, modifier = Modifier.weight(1f))
                            Text("UNDO", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Wyrm.Ink,
                                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    val groups = customGroups.dropLast(1)
                                    savePattern(groups, customColors.take(groups.size))
                                })
                            Text("CLEAR", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color(0xFFFF3B30),
                                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    savePattern(emptyList(), emptyList())
                                })
                            AirWheelToggle(showingWheel) { showingWheel = !showingWheel }
                        }
                        val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Wyrm.Ink)
                        BasicTextField(
                            value = SkinCatalog.code(customGroups),
                            onValueChange = { typed ->
                                val groups = typed.lowercase().take(256).mapNotNull { SkinCatalog.group(it) }
                                val common = groups.zip(customGroups).takeWhile { it.first == it.second }.size
                                savePattern(groups, List(groups.size) { if (it < common) customColors[it] else 0 })
                            },
                            singleLine = true,
                            textStyle = codeStyle,
                            cursorBrush = SolidColor(Wyrm.Link),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .clip(wyrmRounded(11.dp))
                                .background(Wyrm.Card.copy(alpha = 0.9f))
                                .padding(13.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (customGroups.isEmpty()) Text("Type or paste a skin code", style = codeStyle.copy(color = Wyrm.Quiet.copy(alpha = 0.6f)))
                                    inner()
                                }
                            },
                        )
                        IosSectionLabel("Build a Wyrm")
                        AnimatedContent(
                            targetState = showingWheel,
                            transitionSpec = {
                                (fadeIn(iosSpring(0.38f, 0.84f)) + scaleIn(iosSpring(0.38f, 0.84f), initialScale = 0.96f)) togetherWith fadeOut(iosSpring(0.38f, 0.84f))
                            },
                            label = "wheel-or-beads",
                        ) { wheel ->
                            if (wheel) {
                                AirWheelPanel(textures, prefs) { kind, rgb ->
                                    if (customGroups.size < 256) {
                                        savePattern(customGroups + AirSkin.nearestGroup(rgb), customColors + (AirSkin.marker(kind) or rgb))
                                    }
                                }
                            } else {
                                BeadGrid(textures) { group ->
                                    if (customGroups.size < 256) savePattern(customGroups + group, customColors + 0)
                                }
                            }
                        }
                    }
                    SkinSection.ACCESSORIES -> {
                        InlineHeader(shown.title) { enter(SkinSection.OVERVIEW) }
                        TileGrid(minimum = 70.dp, count = SkinCatalog.accessories.size + 1) { index ->
                            if (index == 0) {
                                SelectionTile(accessory < 0, "None") { accessory = -1; onPickAccessory(-1) }
                            } else {
                                val item = SkinCatalog.accessories[index - 1]
                                ImageTile(accessory == item.id, textures?.accessoryThumbnails?.get(item.id), 8.dp) {
                                    accessory = item.id
                                    onPickAccessory(item.id)
                                }
                            }
                        }
                    }
                    SkinSection.TAGS -> {
                        InlineHeader(shown.title) { enter(SkinSection.OVERVIEW) }
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            chainSetting?.let { SkinSlider("Chain", it, 1f..3f, onSettingChange) }
                            swingSetting?.let { SkinSlider("Swing", it, 1f..2f, onSettingChange) }
                            scaleSetting?.let { SkinSlider("Size", it, 0.4f..2f, onSettingChange) }
                        }
                        IosSectionLabel("All original tags")
                        TileGrid(minimum = 76.dp, count = SkinCatalog.tags.size + 1) { index ->
                            if (index == 0) {
                                SelectionTile(tag < 0, "None") { tag = -1; tagSetting?.let { onSettingChange(it, listOf(-1f)) } }
                            } else {
                                val item = SkinCatalog.tags[index - 1]
                                ImageTile(tag == item.id, textures?.tagThumbnails?.get(item.id), 7.dp, badge = "${item.ntlId}") {
                                    tag = item.id
                                    tagSetting?.let { onSettingChange(it, listOf(item.id.toFloat())) }
                                }
                            }
                        }
                    }
                    SkinSection.BACKGROUND -> {
                        InlineHeader(shown.title) { enter(SkinSection.OVERVIEW) }
                        TileGrid(minimum = 104.dp, count = SkinCatalog.backgrounds.size, aspect = null) { index ->
                            val item = SkinCatalog.backgrounds[index]
                            BackgroundTile(item, textures?.backgrounds?.get(item.id), backgroundId == item.id) {
                                backgroundId = item.id
                                onPickBackground(item.id)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(LocalRootTabClearance.current.coerceAtLeast(108.dp)))
            }
        }
    }
}

/* ------------------------------------------------------------- the pieces */

@Composable
private fun InlineHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 15.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Wyrm.Card.copy(alpha = 0.92f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { IosIcon(IosGlyph.CHEVRON_LEFT, Wyrm.Ink, size = 15.dp, weight = 2.8f) }
        Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f))
        Text("AUTO-SAVED", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 8.5.sp, letterSpacing = 1.sp, color = Wyrm.Live)
    }
}

/** SwiftUI `LazyVGrid(.adaptive(minimum:), spacing: 10)` inside 16 dp margins. */
@Composable
private fun TileGrid(minimum: Dp, count: Int, aspect: Float? = 1f, cell: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        val spacing = 10.dp
        val columns = max(1, ((maxWidth + spacing) / (minimum + spacing)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (row in 0 until (count + columns - 1) / columns) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (column in 0 until columns) {
                        val index = row * columns + column
                        Box(Modifier.weight(1f).then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier)) {
                            if (index < count) cell(index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionCheck(modifier: Modifier) {
    Box(modifier.padding(6.dp).size(18.dp).clip(CircleShape).background(Wyrm.Ink), contentAlignment = Alignment.Center) {
        IosIcon(IosGlyph.CHECKMARK, Wyrm.Paper, size = 11.dp, weight = 3.4f)
    }
}

@Composable
private fun SelectionTile(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = wyrmRounded(15.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Wyrm.Card.copy(alpha = 0.92f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.7.sp, color = Wyrm.Ink)
        if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun ImageTile(selected: Boolean, image: ImageBitmap?, inset: Dp, badge: String? = null, onClick: () -> Unit) {
    val shape = wyrmRounded(15.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Wyrm.Card.copy(alpha = 0.92f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        if (image != null) {
            Canvas(Modifier.fillMaxSize().padding(inset)) { drawFitted(image) }
        }
        if (badge != null) {
            Text(badge, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 7.5.sp, color = Wyrm.Quiet,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun BackgroundTile(item: SkinBackgroundAsset, image: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = wyrmRounded(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Wyrm.Card.copy(alpha = 0.92f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(wyrmRounded(13.dp))
                .background(if (item.id == 1) Wyrm.Paper else Wyrm.Ink.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Canvas(Modifier.fillMaxSize()) { drawFilled(image) }
            } else if (item.id == 1) {
                Canvas(Modifier.size(16.dp)) {
                    drawCircle(Wyrm.Quiet, style = Stroke(1.6.dp.toPx()))
                    drawLine(Wyrm.Quiet, Offset(size.width * 0.18f, size.height * 0.82f), Offset(size.width * 0.82f, size.height * 0.18f), 1.6.dp.toPx())
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = Wyrm.Ink, maxLines = 1, modifier = Modifier.weight(1f))
            if (selected) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Wyrm.Ink), contentAlignment = Alignment.Center) {
                    IosIcon(IosGlyph.CHECKMARK, Wyrm.Paper, size = 9.dp, weight = 3.4f)
                }
            }
        }
    }
}

@Composable
private fun SkinSlider(title: String, setting: Setting, range: ClosedFloatingPointRange<Float>, onChange: (Setting, List<Float>) -> Unit) {
    var value by remember(setting.id) { mutableStateOf(setting.number) }
    LaunchedEffect(setting.number) { value = setting.number }
    Column(
        Modifier.fillMaxWidth().clip(wyrmRounded(14.dp)).background(Wyrm.Card.copy(alpha = 0.88f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = Wyrm.Quiet)
        }
        LiquidSlider(value = value, onValueChange = { value = it; onChange(setting, listOf(it)) }, valueRange = range)
    }
}

@Composable
private fun BeadGrid(textures: SkinTextures?, onPick: (Int) -> Unit) {
    val groups = SkinCatalog.validGroups
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until (groups.size + 6) / 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (column in 0 until 7) {
                    val index = row * 7 + column
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        val group = groups.getOrNull(index) ?: return@Box
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Wyrm.Card.copy(alpha = 0.72f))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(group) }
                                .padding(5.dp),
                        ) {
                            textures?.beads?.get(group)?.let { image -> Canvas(Modifier.fillMaxSize()) { drawFitted(image) } }
                        }
                    }
                }
            }
        }
    }
}

/** `WyrmMiniSnake`: a preset's beads in a line, 8/48 of a bead apart. */
@Composable
private fun MiniSnake(textures: SkinTextures?, groups: List<Int>, modifier: Modifier) {
    Canvas(modifier) {
        val beads = textures?.beads ?: return@Canvas
        val bead = min(size.height * 0.87f, 38.dp.toPx())
        val step = bead * (8f / 48f)
        val count = min(128, max(1, ceil(max(0f, size.width - bead) / step).toInt() + 1))
        for (index in 0 until count) {
            val group = if (groups.isEmpty()) 7 else groups[index % groups.size]
            val image = beads[group] ?: continue
            drawImageInto(image, index * step, (size.height - bead) * 0.5f, bead, bead)
        }
    }
}

/* ------------------------------------------------------------- drawing */

private fun DrawScope.drawImageInto(image: ImageBitmap, left: Float, top: Float, width: Float, height: Float, tint: Color? = null, alpha: Float = 1f) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(max(1, width.roundToInt()), max(1, height.roundToInt())),
        alpha = alpha,
        colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) },
    )
}

/** `scaledToFit` centred in the canvas. */
private fun DrawScope.drawFitted(image: ImageBitmap, tint: Color? = null) {
    val scale = min(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    drawImageInto(image, (size.width - w) / 2f, (size.height - h) / 2f, w, h, tint)
}

/** `scaledToFill` centred and clipped by the caller. */
private fun DrawScope.drawFilled(image: ImageBitmap, alpha: Float = 1f) {
    val scale = max(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    drawImageInto(image, (size.width - w) / 2f, (size.height - h) / 2f, w, h, alpha = alpha)
}

/**
 * The hero preview: iOS's geometry exactly — 128 beads a row, 8/48 of a bead
 * apart, the tail row above the head row turned half a revolution, AIR beads
 * with their `ksmc_t` shadows in AIR's order, the eyes, accessory and tag.
 */
@Composable
private fun SkinPreview(
    textures: SkinTextures?,
    groups: List<Int>,
    colors: List<Int>,
    preset: Int,
    custom: Boolean,
    accessoryId: Int,
    tagId: Int,
    backgroundId: Int,
    chain: Double,
    swing: Double,
    tagScale: Double,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.background(Wyrm.Paper)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val segmentsPerRow = 128
        val nativeSpan = 1f + (segmentsPerRow - 1) * (8f / 48f)
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }
        val scale = min((w - 28 * px) / nativeSpan, (h - 24 * px) / 2.16f)
        val step = 8f * (scale / 48f)
        val gap = scale * 0.16f
        val bodyWidth = scale + step * (segmentsPerRow - 1)
        val x = w * 0.5f - bodyWidth * 0.5f
        val centreY = h * 0.48f
        val headY = centreY - scale * 0.5f - gap * 0.5f
        val tailY = centreY + scale * 0.5f + gap * 0.5f
        val head = Offset(x + scale * 0.5f + step * (segmentsPerRow - 1), headY)
        val unit = scale / 29f

        // The chosen arena background, faint, fading out from the centre.
        textures?.backgrounds?.get(backgroundId)?.let { image ->
            Canvas(Modifier.fillMaxSize()) {
                // iOS masks the 13% image with a radial gradient from 10 pt
                // (opaque) through 0.42 at the midpoint to clear at 56% of the
                // short side; paper laid over with the inverse gives the same pixels.
                drawFilled(image, alpha = 0.13f)
                val radius = min(size.width, size.height) * 0.56f
                val inner = (10.dp.toPx() / radius).coerceIn(0f, 0.99f)
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(0f to Color.Transparent, inner to Color.Transparent, (inner + 1f) / 2f to Wyrm.Paper.copy(alpha = 0.58f), 1f to Wyrm.Paper),
                        center = center,
                        radius = radius,
                    ),
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = textures ?: return@Canvas
            val total = segmentsPerRow * 2
            fun point(segment: Int): Offset {
                val local = segment % segmentsPerRow
                val slot = if (segment < segmentsPerRow) segmentsPerRow - 1 - local else local
                return Offset(x + scale * 0.5f + slot * step, if (segment < segmentsPerRow) tailY else headY)
            }
            fun groupAt(codeIndex: Int) = if (groups.isEmpty()) 7 else groups[codeIndex % groups.size]
            fun airKind(codeIndex: Int): Int? {
                if (codeIndex !in 0 until total || codeIndex >= colors.size) return null
                return if (groupAt(codeIndex) < 0) null else AirSkin.kind(colors[codeIndex])
            }
            val shadowSize = scale * 102f / 64f
            fun airShadow(codeIndex: Int, alpha: Float) {
                val shadow = t.airShadow ?: return
                if (airKind(codeIndex) == null) return
                val p = point(total - 1 - codeIndex)
                drawImageInto(shadow, p.x - shadowSize / 2, p.y - shadowSize / 2, shadowSize, shadowSize, alpha = alpha.coerceIn(0f, 1f))
            }
            fun shadowAlpha(codeIndex: Int) = if (codeIndex < 9) codeIndex / 9f else 1f
            for (codeIndex in 8 downTo 0) airShadow(codeIndex, 1f - codeIndex / 9f)
            for (n in 1..4) airShadow(total - n, shadowAlpha(total - n))
            for (row in 0 until 2) {
                for (local in 0 until segmentsPerRow) {
                    val segment = row * segmentsPerRow + local
                    val codeIndex = total - 1 - segment
                    if (codeIndex >= 4) airShadow(codeIndex - 4, shadowAlpha(codeIndex - 4))
                    val group = groupAt(codeIndex)
                    if (group < 0) continue
                    val argb = colors.getOrElse(codeIndex) { 0 }
                    val air = AirSkin.kind(argb)
                    val bead = (air?.let { t.airBeads[it] }) ?: t.beads[if (argb == 0) group else 40] ?: continue
                    val tint = when {
                        air != null -> AirSkin.bodyTint(argb).rgbColor()
                        argb != 0 -> argb.rgbColor()
                        else -> null
                    }
                    val p = point(segment)
                    if (row == 1) {
                        rotate(180f, pivot = p) { drawImageInto(bead, p.x - scale / 2, p.y - scale / 2, scale, scale, tint) }
                    } else {
                        drawImageInto(bead, p.x - scale / 2, p.y - scale / 2, scale, scale, tint)
                    }
                }
            }
            // The eyes.
            val eye = t.beads[40]
            if (eye != null) {
                val iris = 12 * unit
                val pupil = (if (custom) 7f else if (preset == 63) 5f else 7f) * unit
                val irisColor = when {
                    !custom && preset == 63 -> Color.Black
                    !custom && preset == 64 -> Color(1f, 1f, 0.50196f)
                    !custom && preset == 25 -> Color(1f, 0.3373f, 0.0353f)
                    !custom && preset == 44 -> Color(0.8314f, 0.8314f, 0.8314f)
                    else -> Color.White
                }
                val pupilColor = if (!custom && preset == 63) Color(0.8f, 0.8f, 0.8f) else Color.Black
                val cx = head.x + 6 * unit
                for (side in 0 until 2) {
                    val ey = if (side == 0) -6 * unit - 0.5f * px else 6 * unit
                    drawImageInto(eye, cx - iris / 2, head.y + ey - iris / 2, iris, iris, irisColor)
                    val py = if (side == 0) -6 * unit else 6 * unit
                    drawImageInto(eye, cx + 0.5f * px + 2 * unit - pupil / 2, head.y + py - pupil / 2, pupil, pupil, pupilColor)
                }
            }
            // The accessory.
            val item = SkinCatalog.accessories.getOrNull(accessoryId)
            val image = if (item != null) t.accessories[accessoryId] else null
            if (item != null && image != null) {
                val size = scale * item.scale
                val cx = head.x + item.offset * 6 * unit
                val fit = min(size / image.width, size / image.height)
                drawImageInto(image, cx - image.width * fit / 2, head.y - image.height * fit / 2, image.width * fit, image.height * fit)
            }
        }
        val tagItem = SkinCatalog.tags.getOrNull(tagId)
        val tagImage = textures?.tags?.get(tagId)
        if (tagItem != null && tagImage != null) {
            SwingTag(tagItem, tagImage, head, scale, w, h, chain, swing, tagScale)
        }
        if (textures == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { IosSpinner(size = 20.dp, colour = Wyrm.Ink) }
        }
    }
}

/** `WyrmSwingTag`: a preview-only rope at 30 Hz; the arena keeps its own physics. */
@Composable
private fun SwingTag(
    item: SkinTagAsset,
    image: ImageBitmap,
    head: Offset,
    headSize: Float,
    boundsW: Float,
    boundsH: Float,
    chain: Double,
    swing: Double,
    tagScale: Double,
) {
    val unit = headSize / 29f
    val anchor = Offset(head.x - 8 * unit, head.y)
    val segment = 4f * max(1.0, chain).toFloat() * unit
    val points = remember { Array(10) { Offset.Zero } }
    val velocity = remember { Array(10) { Offset.Zero } }
    var tick by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    val initial = remember(anchor, segment) {
        for (i in 0 until 10) {
            points[i] = Offset(anchor.x - i * segment, anchor.y)
            velocity[i] = Offset.Zero
        }
        true
    }
    LaunchedEffect(anchor, segment, swing) {
        while (true) {
            delay(33)
            elapsed += 1.0 / 30.0
            val amplitude = (max(0.0, min(2.0, swing - 1)) * unit * 1.1).toFloat()
            val stiffness = (0.08333 + 0.01667 * (swing - 1)).toFloat()
            val damping = min(0.985, 0.838 + 0.145 * (swing - 1)).toFloat()
            points[0] = anchor
            for (index in 1 until 10) {
                val prior = points[index - 1]
                val dx = points[index].x - prior.x
                val dy = points[index].y - prior.y
                val direction = if (dx == 0f && dy == 0f) PI.toFloat() else atan2(dy, dx)
                val targetX = prior.x + segment * cos(direction)
                val targetY = prior.y + segment * sin(direction)
                val sway = (sin(elapsed * 2.1 - index * 0.32) * amplitude * index / 9).toFloat()
                val vx = (velocity[index].x + stiffness * (targetX - points[index].x) - 0.10f * unit) * damping
                val vy = (velocity[index].y + stiffness * (targetY + sway - points[index].y)) * damping
                velocity[index] = Offset(vx, vy)
                var px = points[index].x + vx
                var py = points[index].y + vy
                val deltaX = px - prior.x
                val deltaY = py - prior.y
                val distance = hypot(deltaX, deltaY)
                if (distance > segment) {
                    px = prior.x + segment * deltaX / distance
                    py = prior.y + segment * deltaY / distance
                }
                px = px.coerceIn(0f, anchor.x - segment * 0.25f)
                py = py.coerceIn(0f, boundsH)
                points[index] = Offset(px, py)
            }
            tick++
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") tick
        if (!initial) return@Canvas
        val rope = points.toList()
        val end = rope.last()
        val before = rope[rope.size - 2]
        val angle = atan2(end.y - before.y, end.x - before.x)
        val rawWidth = item.width * 0.285f * unit * tagScale.toFloat()
        val rawHeight = item.height * 0.285f * unit * tagScale.toFloat()
        val fit = min(1f, 108.dp.toPx() / max(1f, max(rawWidth, rawHeight)))
        val width = rawWidth * fit
        val height = rawHeight * fit
        val attachX = item.anchorX * 0.285f * unit * tagScale.toFloat() * fit
        val attachY = item.anchorY * 0.285f * unit * tagScale.toFloat() * fit
        val localX = attachX + width * 0.5f
        val localY = attachY + height * 0.5f
        val proposedX = end.x + cos(angle) * localX - sin(angle) * localY
        val proposedY = end.y + sin(angle) * localX + cos(angle) * localY
        val centreX = min(max(width * 0.5f, proposedX), min(head.x - headSize * 0.1f, boundsW - width * 0.5f))
        val centreY = min(max(height * 0.5f, proposedY), boundsH - height * 0.5f)
        fun ropePath(to: Int, close: Boolean): Path = Path().apply {
            moveTo(end.x, end.y)
            for (index in rope.size - 2 downTo to) {
                quadraticTo(rope[index].x, rope[index].y, (rope[index].x + rope[index - 1].x) * 0.5f, (rope[index].y + rope[index - 1].y) * 0.5f)
            }
            if (close) quadraticTo(rope[1].x, rope[1].y, rope[0].x, rope[0].y)
        }
        drawPath(ropePath(1, false), item.accentA.rgbColor(), style = Stroke(5 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (lineWidth in listOf(4f, 3f, 2f)) {
            drawPath(ropePath(2, true), item.accentB.rgbColor().copy(alpha = 0.5f), style = Stroke(lineWidth * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        withTransform({ rotateRad(angle, pivot = Offset(centreX, centreY)) }) {
            val fitScale = min(width / image.width, height / image.height)
            val dw = image.width * fitScale
            val dh = image.height * fitScale
            drawImageInto(image, centreX - dw / 2, centreY - dh / 2, dw, dh)
        }
    }
}

/* ----------------------------------------------------------- AIR wheel */

/** The pattern toggle: a colour-wheel glyph while the beads show, a bead grid while the wheel does. */
@Composable
private fun AirWheelToggle(showingWheel: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .glassDisc()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showingWheel) {
            Canvas(Modifier.size(14.dp)) {
                val r = size.minDimension / 9f
                for (i in 0 until 3) for (j in 0 until 3) {
                    drawCircle(Wyrm.Ink, r, Offset(size.width * (i + 0.5f) / 3f, size.height * (j + 0.5f) / 3f))
                }
            }
        } else {
            Canvas(Modifier.size(20.dp)) {
                drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color(0xFF800080), Color.Red)))
                drawCircle(Brush.radialGradient(listOf(Color(0.5f, 0.5f, 0.5f), Color(0.5f, 0.5f, 0.5f, 0f)), radius = 7.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 4.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** Glass-styled disc: a card lens with a white rim, as iOS draws its glass before 26. */
private fun Modifier.glassDisc(): Modifier = this
    .clip(CircleShape)
    .background(Wyrm.Card.copy(alpha = 0.55f))
    .border(0.8.dp, Color.White.copy(alpha = 0.55f), CircleShape)
    .border(0.5.dp, Wyrm.Ink.copy(alpha = 0.1f), CircleShape)

/**
 * `WyrmAirWheelPanel`: AIR's colour wheel, bezel knob for brightness,
 * pointer for hue and saturation, and AIR's first two bead buttons. Live
 * values stay in this panel; they are saved when the finger lifts.
 */
@Composable
private fun AirWheelPanel(textures: SkinTextures?, prefs: android.content.SharedPreferences, onAdd: (kind: Int, rgb: Int) -> Unit) {
    var pointerX by remember { mutableDoubleStateOf(prefs.getFloat("air-pointer-x", 0f).toDouble()) }
    var pointerY by remember { mutableDoubleStateOf(prefs.getFloat("air-pointer-y", 0f).toDouble()) }
    var bezelAngle by remember { mutableDoubleStateOf(prefs.getFloat("air-bezel", 0f).toDouble()) }
    var rgb by remember { mutableIntStateOf(prefs.getInt("air-rgb", 0x808080) and 0xFFFFFF) }
    val pure = AirSkin.pure(pointerX, pointerY)
    val brightness = AirSkin.brightness(bezelAngle)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BoxWithConstraints(Modifier.padding(horizontal = 20.dp).widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f)) {
            val side = min(constraints.maxWidth, constraints.maxHeight).toFloat()
            val unitPx = side / (2 * 172f)
            val centre = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
            var drag by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) } // kind 0 pointer (origin), 1 bezel
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(unitPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val sx = ((down.position.x - centre.x) / unitPx).toDouble()
                            val sy = ((down.position.y - centre.y) / unitPx).toDouble()
                            drag = when {
                                hypot(sx - pointerX, sy - pointerY) <= 30 -> Triple(0, pointerX, pointerY)
                                sqrt(sx * sx + sy * sy) <= AirSkin.WHEEL_RADIUS -> Triple(0, sx, sy)
                                else -> Triple(1, 0.0, 0.0)
                            }
                            fun apply(position: Offset) {
                                val current = drag ?: return
                                if (current.first == 0) {
                                    var nx = current.second + (position.x - down.position.x) / unitPx
                                    var ny = current.third + (position.y - down.position.y) / unitPx
                                    val d = sqrt(nx * nx + ny * ny)
                                    if (d > AirSkin.POINTER_LIMIT) {
                                        nx *= AirSkin.POINTER_LIMIT / d
                                        ny *= AirSkin.POINTER_LIMIT / d
                                    }
                                    pointerX = nx
                                    pointerY = ny
                                    rgb = AirSkin.shaded(AirSkin.pure(nx, ny), AirSkin.brightness(bezelAngle))
                                } else {
                                    val bx = ((position.x - centre.x) / unitPx).toDouble()
                                    val by = ((position.y - centre.y) / unitPx).toDouble()
                                    bezelAngle = atan2(by, bx)
                                    rgb = AirSkin.shaded(AirSkin.rounded(AirSkin.pure(pointerX, pointerY)), AirSkin.brightness(bezelAngle))
                                }
                            }
                            apply(down.position)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                apply(change.position)
                            }
                            drag = null
                            prefs.edit()
                                .putFloat("air-pointer-x", pointerX.toFloat())
                                .putFloat("air-pointer-y", pointerY.toFloat())
                                .putFloat("air-bezel", bezelAngle.toFloat())
                                .putInt("air-rgb", rgb)
                                .apply()
                        }
                    },
            ) {
                val rounded = AirSkin.rounded(pure)
                val pureRgb = (rounded[0].toInt() shl 16) or (rounded[1].toInt() shl 8) or rounded[2].toInt()
                // Bezel: tinted with the unshaded colour, lit top, shaded bottom, rim.
                val bezelRadius = ((AirSkin.WHEEL_RADIUS + AirSkin.BEZEL_WIDTH) * unitPx).toFloat()
                drawCircle(Color.Black.copy(alpha = 0.14f), bezelRadius, centre + Offset(0f, 3.dp.toPx()))
                drawCircle(pureRgb.rgbColor().copy(alpha = 0.88f), bezelRadius, centre)
                drawCircle(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.42f), 0.46f to Color.White.copy(alpha = 0.06f), 1f to Color.Black.copy(alpha = 0.16f),
                        startY = centre.y - bezelRadius, endY = centre.y + bezelRadius,
                    ),
                    bezelRadius, centre,
                )
                drawCircle(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.18f)), startY = centre.y - bezelRadius, endY = centre.y + bezelRadius),
                    bezelRadius, centre, style = Stroke(1.2.dp.toPx()),
                )
                // The wheel itself.
                val wheelSize = (256 * unitPx).toFloat()
                val wheel = textures?.airWheel
                if (wheel != null) {
                    drawImageInto(wheel, centre.x - wheelSize / 2, centre.y - wheelSize / 2, wheelSize, wheelSize)
                } else {
                    drawCircle(Color.Gray, wheelSize / 2, centre)
                }
                // `bsk_cwt_ii`: white or black over the wheel, alpha |bsk_br|.
                drawCircle(
                    (if (brightness > 0) Color.White else Color.Black).copy(alpha = kotlin.math.abs(brightness).toFloat().coerceIn(0f, 1f)),
                    (128 * 1.005 * unitPx).toFloat(), centre,
                )
                fun knob(at: Offset, diameter: Float) {
                    val r = diameter / 2
                    drawCircle(Color.Black.copy(alpha = 0.25f), r, at + Offset(0f, 2.dp.toPx()))
                    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.30f)), startY = at.y - r, endY = at.y + r), r, at)
                    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.35f)), startY = at.y - r, endY = at.y + r), r, at, style = Stroke(1.6.dp.toPx()))
                    val inner = r * (1 - 0.34f)
                    drawCircle(rgb.rgbColor(), inner, at)
                    drawCircle(Color.White.copy(alpha = 0.95f), inner, at, style = Stroke(1.5.dp.toPx()))
                }
                knob(centre + Offset((pointerX * unitPx).toFloat(), (pointerY * unitPx).toFloat()), (46 * unitPx).toFloat())
                knob(
                    centre + Offset((cos(bezelAngle) * AirSkin.BEZEL_POINTER_RADIUS * unitPx).toFloat(), (sin(bezelAngle) * AirSkin.BEZEL_POINTER_RADIUS * unitPx).toFloat()),
                    (42 * unitPx).toFloat(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            for (kind in 0 until 2) {
                Box(
                    Modifier
                        .size(66.dp)
                        .glassDisc()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAdd(kind, rgb) }
                        .padding(9.dp),
                ) {
                    textures?.airBeads?.get(kind)?.let { image ->
                        Canvas(Modifier.fillMaxSize()) {
                            rotate(180f) { drawFitted(image, rgb.rgbColor()) }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val keepShadow = Modifier.shadow(0.dp)
