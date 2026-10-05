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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
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

/* Tags are on again (OM, 2026-10-04; off 2026-09-26 to 2026-10-04). true
   hides the picker and shows "Coming soon". */
private const val TAGS_DISABLED = false

private enum class SkinSection(val title: String) {
    OVERVIEW("Skin wardrobe"), PRESETS("Default skins"), PATTERN("Pattern"),
    ACCESSORIES("Accessory"), TAGS("Tag"), BACKGROUND("Arena background"),
    WYRM_ACCESSORIES("Wyrm accessories"),
}

private fun Int.rgbColor(): Color = Color(((this shr 16) and 0xFF) / 255f, ((this shr 8) and 0xFF) / 255f, (this and 0xFF) / 255f)

@Composable
internal fun IosSkinScreen(
    state: SkinState,
    background: Int,
    settings: List<Setting>,
    insetTop: Dp,
    onPickPreset: (Int) -> Unit,
    onCode: (code: String, colours: IntArray) -> Unit,
    onPickAccessory: (Int) -> Unit,
    onPickBackground: (Int) -> Unit,
    onSettingChange: (Setting, List<Float>) -> Unit,
    /**
     * A look being tried ("Try this skin", OM 2026-09-30): the preview and the
     * Wyrm accessories page show it and every pick goes to [onLook], never to
     * [WyrmLookStore]. Null is the player's own saved look.
     */
    look: WyrmLookSpec? = null,
    onLook: ((WyrmLookSpec) -> Unit)? = null,
    /** Drawn under the header: the "Trying …'s skin" banner. */
    banner: (@Composable () -> Unit)? = null,
    /** "Share this skin": the Share editor with this skin; null hides the button. */
    onShareSkin: (() -> Unit)? = null,
    /** Arena background open: "Adjust size" in line with Share this skin, far right (OM, 2026-10-01). */
    onAdjustBackgroundSize: (() -> Unit)? = null,
) {
    val textures by rememberSkinTextures()
    val lookNow = look ?: WyrmLookStore.spec()
    fun pickLook(next: WyrmLookSpec, saved: () -> Unit) {
        val draft = onLook
        if (look != null && draft != null) draft(next) else saved()
    }
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

    // The colour wheel's colour, which Wyrm's patterned beads take.
    var wheelRgb by remember { mutableIntStateOf(prefs.getInt("air-rgb", 0x808080) and 0xFFFFFF) }

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
        banner?.invoke()
        Box(Modifier.fillMaxWidth().height(218.dp)) {
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
                look = lookNow,
                modifier = Modifier.fillMaxSize(),
            )
            // Share this skin: bottom left of the preview, just above the rule (OM).
            onShareSkin?.let { share -> ShareSkinButton(share, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp)) }
            if (section == SkinSection.BACKGROUND) {
                onAdjustBackgroundSize?.let { adjust ->
                    AdjustBackgroundButton(adjust, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp))
                }
            }
        }
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
                        // Wyrm's own looks (hair, ears, glasses): only this phone sees them.
                        IosSectionLabel("Wyrm accessories")
                        IosPaperCard {
                            val worn = listOf(lookNow.hair, lookNow.ears, lookNow.glasses).count { it >= 0 }
                            IosListRow(SkinSection.WYRM_ACCESSORIES.title, value = if (worn == 0) "None" else "$worn on") {
                                enter(SkinSection.WYRM_ACCESSORIES)
                            }
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
                            AirWheelToggle(showingWheel, enabled = !WyrmBeads.BUILT_BEADS_OFF) { showingWheel = !showingWheel }
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
                        // The wheel opens above the beads; the beads stay below it
                        // (OM, 2026-10-03: one group, every bead the same size).
                        AnimatedVisibility(
                            visible = showingWheel && !WyrmBeads.BUILT_BEADS_OFF,
                            enter = fadeIn(iosSpring(0.38f, 0.84f)) + scaleIn(iosSpring(0.38f, 0.84f), initialScale = 0.96f),
                            exit = fadeOut(iosSpring(0.38f, 0.84f)),
                            label = "wheel",
                        ) {
                            Column {
                                AirWheelPanel(textures, prefs, onColour = { wheelRgb = it }) { kind, rgb ->
                                    if (!WyrmBeads.BUILT_BEADS_OFF && customGroups.size < 256) {
                                        savePattern(customGroups + AirSkin.nearestGroup(rgb), customColors + (AirSkin.marker(kind) or rgb))
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        // Slither's beads first, then Wyrm's own; the arena gets
                        // each Wyrm bead's nearest slither colour.
                        AllBeadGrid(
                            textures,
                            wheelRgb,
                            wyrmEnabled = !WyrmBeads.BUILT_BEADS_OFF,
                            onPickGroup = { group ->
                                if (customGroups.size < 256) savePattern(customGroups + group, customColors + 0)
                            },
                            onPickWyrm = { kind ->
                                if (!WyrmBeads.BUILT_BEADS_OFF && customGroups.size < 256) {
                                    val argb = WyrmBeads.argb(kind, wheelRgb)
                                    savePattern(customGroups + AirSkin.nearestGroup(argb and 0xFFFFFF), customColors + argb)
                                }
                            },
                        )
                    }
                    SkinSection.ACCESSORIES -> {
                        InlineHeader(shown.title) { enter(SkinSection.OVERVIEW) }
                        TileGrid(minimum = 70.dp, count = SkinCatalog.accessories.size + 1, fixedColumns = 4) { index ->
                            if (index == 0) {
                                SelectionTile(accessory < 0, "None") { accessory = -1; onPickAccessory(-1) }
                            } else {
                                val item = SkinCatalog.accessories[index - 1]
                                ImageTile(accessory == item.id, textures?.accessoryThumbnails?.get(item.id), 4.dp) {
                                    accessory = item.id
                                    onPickAccessory(item.id)
                                }
                            }
                        }
                    }
                    SkinSection.WYRM_ACCESSORIES -> {
                        InlineHeader(shown.title) { enter(SkinSection.OVERVIEW) }
                        // Hair, ears and glasses, one tab each; big pictures like the
                        // original accessories, no names (OM, 2026-09-28).
                        var lookTab by rememberSaveable { mutableIntStateOf(0) }
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                            PaperSegmented(options = listOf("Hair", "Ears", "Glasses"), selected = lookTab, onSelect = { lookTab = it })
                        }
                        when (lookTab) {
                            0 -> {
                                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
                                    Text("Hair colour", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Wyrm.Quiet)
                                    Spacer(Modifier.height(8.dp))
                                    GradientTrack(
                                        fraction = lookNow.hairTone,
                                        brush = Brush.horizontalGradient(*WyrmLook.hairStops.map { (at, rgb) -> at to WyrmLook.rgbColor(rgb) }.toTypedArray()),
                                        onPick = { tone -> pickLook(lookNow.copy(hairTone = tone.coerceIn(0f, 1f))) { WyrmLookStore.pickHairTone(tone) } },
                                    )
                                }
                                TileGrid(minimum = 70.dp, count = WyrmLook.hairNames.size + 1, fixedColumns = 4) { index ->
                                    if (index == 0) {
                                        SelectionTile(lookNow.hair < 0, "None") { pickLook(lookNow.copy(hair = -1)) { WyrmLookStore.pickHair(-1) } }
                                    } else {
                                        val style = index - 1
                                        ImageTile(lookNow.hair == style, textures?.lookThumbnails?.get(style), 6.dp,
                                            tint = WyrmLook.rgbColor(lookNow.hairRgb)) { pickLook(lookNow.copy(hair = style)) { WyrmLookStore.pickHair(style) } }
                                    }
                                }
                            }
                            else -> {
                                val ears = lookTab == 1
                                val names = if (ears) WyrmLook.earNames else WyrmLook.glassesNames
                                val current = if (ears) lookNow.ears else lookNow.glasses
                                fun pick(style: Int) = pickLook(if (ears) lookNow.copy(ears = style) else lookNow.copy(glasses = style)) {
                                    if (ears) WyrmLookStore.pickEars(style) else WyrmLookStore.pickGlasses(style)
                                }
                                TileGrid(minimum = 70.dp, count = names.size + 1, fixedColumns = 4) { index ->
                                    if (index == 0) {
                                        SelectionTile(current < 0, "None") { pick(-1) }
                                    } else {
                                        val style = index - 1
                                        val cell = (if (ears) 16 else 28) + style
                                        ImageTile(current == style, textures?.lookThumbnails?.get(cell), 6.dp) { pick(style) }
                                    }
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
                        TileGrid(minimum = 104.dp, count = SkinCatalog.backgroundOrder.size, aspect = null, fixedColumns = 3) { index ->
                            val item = SkinCatalog.backgrounds[SkinCatalog.backgroundOrder[index]]
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

/**
 * "Share this skin" (OM, 2026-09-30): a small ink capsule at the preview's
 * bottom left, above the rule. Opens the Trails Share editor with the snake
 * exactly as the preview draws it.
 */
@Composable
internal fun ShareSkinButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .height(34.dp)
            .scale(pressScale(pressed))
            .clip(CircleShape)
            .background(Wyrm.Ink)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.composables.icons.lucide.R.drawable.lucide_ic_share),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(Wyrm.OnInk),
        )
        Spacer(Modifier.width(7.dp))
        Text("Share this skin", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Wyrm.OnInk)
    }
}

@Composable
internal fun AdjustBackgroundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .height(34.dp)
            .scale(pressScale(pressed))
            .clip(CircleShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.composables.icons.lucide.R.drawable.lucide_ic_scaling),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(Wyrm.Ink),
        )
        Spacer(Modifier.width(7.dp))
        Text("Adjust size", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Wyrm.Ink)
    }
}

/** "Try this skin": whose look the preview shows, and the two ways out. Nothing is worn until Wear. */
@Composable
internal fun SkinTrialBanner(author: String, onWear: () -> Unit, onBack: () -> Unit) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp)
            .fillMaxWidth()
            .clip(wyrmRounded(16.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(16.dp))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Trying $author's skin",
            fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Wyrm.Ink,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.clip(CircleShape).background(Wyrm.Well)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack)
                .padding(horizontal = 12.dp).height(32.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Back to mine", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Wyrm.Ink, maxLines = 1) }
        Box(
            Modifier.clip(CircleShape).background(Wyrm.Ink)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWear)
                .padding(horizontal = 16.dp).height(32.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Wear", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Wyrm.OnInk, maxLines = 1) }
    }
}

@Composable
internal fun InlineHeader(title: String, onBack: () -> Unit) {
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
internal fun TileGrid(
    minimum: Dp,
    count: Int,
    aspect: Float? = 1f,
    fixedColumns: Int? = null,
    cell: @Composable (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        val spacing = 10.dp
        // Accessories 4 and backgrounds 3 on every phone, as OM chose.
        val columns = fixedColumns ?: max(1, ((maxWidth + spacing) / (minimum + spacing)).toInt())
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
internal fun SelectionCheck(modifier: Modifier) {
    Box(modifier.padding(6.dp).size(18.dp).clip(CircleShape).background(Wyrm.Ink), contentAlignment = Alignment.Center) {
        IosIcon(IosGlyph.CHECKMARK, Wyrm.Paper, size = 11.dp, weight = 3.4f)
    }
}

@Composable
internal fun SelectionTile(selected: Boolean, label: String, onClick: () -> Unit) {
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
internal fun ImageTile(
    selected: Boolean,
    image: ImageBitmap?,
    inset: Dp,
    badge: String? = null,
    tint: Color? = null,
    onClick: () -> Unit,
) {
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
            Canvas(Modifier.fillMaxSize().padding(inset)) { drawFitted(image, tint) }
        }
        if (badge != null) {
            Text(badge, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 7.5.sp, color = Wyrm.Quiet,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
internal fun BackgroundTile(item: SkinBackgroundAsset, image: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
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
                // Black is the floor itself: true black, as assist mode draws it.
                .background(
                    when (item.id) {
                        1 -> Wyrm.Paper
                        22 -> androidx.compose.ui.graphics.Color.Black
                        else -> Wyrm.Ink.copy(alpha = 0.05f)
                    },
                ),
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
internal fun SkinSlider(title: String, setting: Setting, range: ClosedFloatingPointRange<Float>, onChange: (Setting, List<Float>) -> Unit) {
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
internal fun BeadGrid(textures: SkinTextures?, onPick: (Int) -> Unit) {
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

/**
 * Every bead in one grid, all one size (`WyrmAllBeadsGrid` on iOS): slither's
 * beads first, then Wyrm's own (`WyrmBeads`); patterned Wyrm beads take the
 * colour wheel's colour.
 */
@Composable
internal fun AllBeadGrid(
    textures: SkinTextures?,
    tint: Int,
    wyrmEnabled: Boolean = true,
    onPickGroup: (Int) -> Unit,
    onPickWyrm: (Int) -> Unit,
) {
    val groups = SkinCatalog.validGroups
    val total = groups.size + WyrmBeads.COUNT
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until (total + 6) / 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (column in 0 until 7) {
                    val index = row * 7 + column
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (index >= total) return@Box
                        val group = groups.getOrNull(index)
                        val kind = index - groups.size
                        // Wyrm's beads, when switched off, stay in view but faded
                        // and cannot be picked.
                        val usable = group != null || wyrmEnabled
                        Box(
                            Modifier
                                .fillMaxSize()
                                .alpha(if (usable) 1f else 0.28f)
                                .clip(CircleShape)
                                .background(Wyrm.Card.copy(alpha = 0.72f))
                                .clickable(enabled = usable, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    if (group != null) onPickGroup(group) else onPickWyrm(kind)
                                }
                                .padding(5.dp),
                        ) {
                            if (group != null) {
                                textures?.beads?.get(group)?.let { image -> Canvas(Modifier.fillMaxSize()) { drawFitted(image) } }
                            } else {
                                textures?.wyrmBeads?.get(kind)?.let { image ->
                                    Canvas(Modifier.fillMaxSize()) {
                                        drawFitted(image, if (WyrmBeads.tinted[kind]) tint.rgbColor() else null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Wyrm's own beads (`WyrmBeads`). Patterned ones take the colour wheel's colour. */
@Composable
internal fun WyrmBeadGrid(textures: SkinTextures?, tint: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until (WyrmBeads.COUNT + 5) / 6) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (column in 0 until 6) {
                    val kind = row * 6 + column
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (kind >= WyrmBeads.COUNT) return@Box
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Wyrm.Card.copy(alpha = 0.72f))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(kind) }
                                .padding(4.dp),
                        ) {
                            textures?.wyrmBeads?.get(kind)?.let { image ->
                                Canvas(Modifier.fillMaxSize()) {
                                    drawFitted(image, if (WyrmBeads.tinted[kind]) tint.rgbColor() else null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** `WyrmMiniSnake`: a preset's beads in a line, 8/48 of a bead apart. */
@Composable
internal fun MiniSnake(textures: SkinTextures?, groups: List<Int>, modifier: Modifier) {
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

internal fun DrawScope.drawImageInto(image: ImageBitmap, left: Float, top: Float, width: Float, height: Float, tint: Color? = null, alpha: Float = 1f) {
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
internal fun SkinPreview(
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
    look: WyrmLookSpec,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.background(Wyrm.Paper)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val segmentsPerRow = SKIN_ROW
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

        // No arena background behind the snake: the preview is the skin alone (OM).
        if (false) textures?.backgrounds?.get(backgroundId)?.let { image ->
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
            drawSkinRows(t, groups, colors, preset, custom, accessoryId, look, x, headY, tailY, scale, px) { img, l, tp, iw, ih, tint, alpha ->
                drawImageInto(img, l, tp, iw, ih, tint, alpha)
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

/**
 * One body position's bead and tint, as the arena and the preview choose it:
 * a Wyrm bead or an AIR wheel bead named by the alpha byte, otherwise the
 * colour group's own bead (tinted when an exact colour was picked).
 */
private fun skinBead(t: SkinTextures, group: Int, argb: Int): Pair<ImageBitmap, Color?>? {
    val air = AirSkin.kind(argb)
    val wyrm = WyrmBeads.kind(argb)
    val bead = (wyrm?.let { t.wyrmBeads[it] }) ?: (air?.let { t.airBeads[it] })
        ?: t.beads[if (argb == 0) group else 40] ?: return null
    val tint = when {
        wyrm != null -> if (WyrmBeads.tinted[wyrm]) argb.rgbColor() else null
        air != null -> AirSkin.bodyTint(argb).rgbColor()
        argb != 0 -> argb.rgbColor()
        else -> null
    }
    return bead to tint
}

/**
 * The head facing +x at [head], a bead [scale] px wide: the eyes (preset eye
 * colours included), the accessory and the Wyrm look (hair at rest). [draw]
 * paints one image into a rectangle, tinted or not.
 */
internal fun DrawScope.drawSkinHead(
    t: SkinTextures,
    head: Offset,
    scale: Float,
    px: Float,
    preset: Int,
    custom: Boolean,
    accessoryId: Int,
    look: WyrmLookSpec,
    draw: DrawScope.(ImageBitmap, Float, Float, Float, Float, Color?) -> Unit,
) {
    val unit = scale / 29f
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
            draw(eye, cx - iris / 2, head.y + ey - iris / 2, iris, iris, irisColor)
            val py = if (side == 0) -6 * unit else 6 * unit
            draw(eye, cx + 0.5f * px + 2 * unit - pupil / 2, head.y + py - pupil / 2, pupil, pupil, pupilColor)
        }
    }
    val item = SkinCatalog.accessories.getOrNull(accessoryId)
    val image = if (item != null) t.accessories[accessoryId] else null
    if (item != null && image != null) {
        val size = scale * item.scale
        val cx = head.x + item.offset * 6 * unit
        val fit = min(size / image.width, size / image.height)
        draw(image, cx - image.width * fit / 2, head.y - image.height * fit / 2, image.width * fit, image.height * fit, null)
    }
    // Wyrm looks, placed as platform/android_look.c places them (hair at rest).
    drawWyrmLook(t.looks, head, scale / 2, look.hair, look.hairRgb, look.ears, look.glasses, draw)
}

/** An image into a rectangle at float precision, so a scaled-up export keeps every bead in step. */
private fun DrawScope.drawImageExact(image: ImageBitmap, left: Float, top: Float, width: Float, height: Float, tint: Color?, alpha: Float = 1f) {
    if (width <= 0f || height <= 0f || image.width <= 0 || image.height <= 0) return
    withTransform({
        translate(left, top)
        scale(width / image.width, height / image.height, pivot = Offset.Zero)
    }) {
        drawImage(image, alpha = alpha, colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) })
    }
}

/** Beads in each of the Skin preview's two rows. */
private const val SKIN_ROW = 128

/**
 * The Skin preview's snake, the one drawing the preview and the Share sticker
 * both use, so they match bead for bead: [SKIN_ROW] beads a row, 8/48 of a
 * bead apart, the tail row under the head row turned half a revolution, AIR
 * shadows in AIR's order, then the eyes, the accessory and the Wyrm look. [x]
 * is the body's left edge; the head ends the row at [headY] on the right.
 * [draw] paints one image into a rectangle, tinted or not, at an alpha.
 */
private fun DrawScope.drawSkinRows(
    t: SkinTextures,
    groups: List<Int>,
    colors: List<Int>,
    preset: Int,
    custom: Boolean,
    accessoryId: Int,
    look: WyrmLookSpec,
    x: Float,
    headY: Float,
    tailY: Float,
    scale: Float,
    px: Float,
    draw: DrawScope.(ImageBitmap, Float, Float, Float, Float, Color?, Float) -> Unit,
) {
    val step = 8f * (scale / 48f)
    val total = SKIN_ROW * 2
    fun point(segment: Int): Offset {
        val local = segment % SKIN_ROW
        val slot = if (segment < SKIN_ROW) SKIN_ROW - 1 - local else local
        return Offset(x + scale * 0.5f + slot * step, if (segment < SKIN_ROW) tailY else headY)
    }
    fun groupAt(codeIndex: Int) = if (groups.isEmpty()) 7 else groups[codeIndex % groups.size]
    fun airKind(codeIndex: Int): Int? {
        if (codeIndex !in 0 until total || codeIndex >= colors.size) return null
        return if (groupAt(codeIndex) < 0) null else AirSkin.kind(colors[codeIndex])
    }
    val shadowSize = scale * 102f / 64f
    fun airShadow(codeIndex: Int, alpha: Float) {
        if (!AirSkin.BEAD_SHADOW) return
        val shadow = t.airShadow ?: return
        if (airKind(codeIndex) == null) return
        val p = point(total - 1 - codeIndex)
        draw(shadow, p.x - shadowSize / 2, p.y - shadowSize / 2, shadowSize, shadowSize, null, alpha.coerceIn(0f, 1f))
    }
    fun shadowAlpha(codeIndex: Int) = if (codeIndex < 9) codeIndex / 9f else 1f
    for (codeIndex in 8 downTo 0) airShadow(codeIndex, 1f - codeIndex / 9f)
    for (n in 1..4) airShadow(total - n, shadowAlpha(total - n))
    for (row in 0 until 2) {
        for (local in 0 until SKIN_ROW) {
            val segment = row * SKIN_ROW + local
            val codeIndex = total - 1 - segment
            if (codeIndex >= 4) airShadow(codeIndex - 4, shadowAlpha(codeIndex - 4))
            val group = groupAt(codeIndex)
            if (group < 0) continue
            val (bead, tint) = skinBead(t, group, colors.getOrElse(codeIndex) { 0 }) ?: continue
            val p = point(segment)
            if (row == 1) {
                rotate(180f, pivot = p) { draw(bead, p.x - scale / 2, p.y - scale / 2, scale, scale, tint, 1f) }
            } else {
                draw(bead, p.x - scale / 2, p.y - scale / 2, scale, scale, tint, 1f)
            }
        }
    }
    // The eyes, the accessory and the Wyrm look.
    val head = Offset(x + scale * 0.5f + step * (SKIN_ROW - 1), headY)
    drawSkinHead(t, head, scale, px, preset, custom, accessoryId, look) { img, l, tp, w, h, tint -> draw(img, l, tp, w, h, tint, 1f) }
}

/** Bead widths a one-line snake of [total] beads spans, head room included. */
internal fun skinStripSpan(total: Int = SKIN_ROW * 2): Float = 1f + (total - 1) * (8f / 48f) + 0.7f

/**
 * The same snake as [drawSkinRows] in one line (Wyrm Desktop's wide preview,
 * OM 2026-10-02): all 256 beads from the tail on the left to the head on the
 * right at [y], with the same beads, AIR shadows, eyes, accessory and look.
 * [total] shortens the line (the desktop's preset pictures draw 160).
 */
internal fun DrawScope.drawSkinStrip(
    t: SkinTextures,
    groups: List<Int>,
    colors: List<Int>,
    preset: Int,
    custom: Boolean,
    accessoryId: Int,
    look: WyrmLookSpec,
    x: Float,
    y: Float,
    scale: Float,
    px: Float,
    total: Int = SKIN_ROW * 2,
    draw: DrawScope.(ImageBitmap, Float, Float, Float, Float, Color?, Float) -> Unit,
) {
    val step = 8f * (scale / 48f)
    drawSkinAlong(t, groups, colors, preset, custom, accessoryId, look, scale, px, total,
        place = { segment -> Offset(x + scale * 0.5f + segment * step, y) },
        heading = { 0f },
        draw = draw)
}

/**
 * The snake laid along any line (Wyrm Desktop's slithering stage): [place]
 * gives each segment's centre (0 = tail end, [total] - 1 = head) and
 * [heading] the direction it travels in degrees (0 = +x). Same beads, AIR
 * shadows, eyes, accessory and Wyrm look as [drawSkinRows].
 */
internal fun DrawScope.drawSkinAlong(
    t: SkinTextures,
    groups: List<Int>,
    colors: List<Int>,
    preset: Int,
    custom: Boolean,
    accessoryId: Int,
    look: WyrmLookSpec,
    scale: Float,
    px: Float,
    total: Int,
    place: (Int) -> Offset,
    heading: (Int) -> Float,
    draw: DrawScope.(ImageBitmap, Float, Float, Float, Float, Color?, Float) -> Unit,
) {
    fun groupAt(codeIndex: Int) = if (groups.isEmpty()) 7 else groups[codeIndex % groups.size]
    fun airKind(codeIndex: Int): Int? {
        if (codeIndex !in 0 until total || codeIndex >= colors.size) return null
        return if (groupAt(codeIndex) < 0) null else AirSkin.kind(colors[codeIndex])
    }
    val shadowSize = scale * 102f / 64f
    fun airShadow(codeIndex: Int, alpha: Float) {
        if (!AirSkin.BEAD_SHADOW) return
        val shadow = t.airShadow ?: return
        if (airKind(codeIndex) == null) return
        val p = place(total - 1 - codeIndex)
        draw(shadow, p.x - shadowSize / 2, p.y - shadowSize / 2, shadowSize, shadowSize, null, alpha.coerceIn(0f, 1f))
    }
    fun shadowAlpha(codeIndex: Int) = if (codeIndex < 9) codeIndex / 9f else 1f
    for (codeIndex in 8 downTo 0) airShadow(codeIndex, 1f - codeIndex / 9f)
    for (n in 1..4) airShadow(total - n, shadowAlpha(total - n))
    for (segment in 0 until total) {
        val codeIndex = total - 1 - segment
        if (codeIndex >= 4) airShadow(codeIndex - 4, shadowAlpha(codeIndex - 4))
        val group = groupAt(codeIndex)
        if (group < 0) continue
        val (bead, tint) = skinBead(t, group, colors.getOrElse(codeIndex) { 0 }) ?: continue
        val p = place(segment)
        // Heading right is the head row of the two-row preview (turned 180).
        rotate(180f + heading(segment), pivot = p) { draw(bead, p.x - scale / 2, p.y - scale / 2, scale, scale, tint, 1f) }
    }
    val head = place(total - 1)
    rotate(heading(total - 1), pivot = head) {
        drawSkinHead(t, head, scale, px, preset, custom, accessoryId, look) { img, l, tp, w, h, tint -> draw(img, l, tp, w, h, tint, 1f) }
    }
}

/* The "Share" skin sticker (OM, 2026-09-30): the Skin preview itself, both
   rows, in bead widths. Its box leaves room around the head for the
   accessory and the Wyrm look. */
private const val STICKER_GAP = 0.16f
private const val STICKER_BODY_W = 1f + (SKIN_ROW - 1) * (8f / 48f)
private const val STICKER_W = STICKER_BODY_W + 1.2f
private const val STICKER_H = 2f + STICKER_GAP + 1.4f

/** The sticker's box for a bead [bead] px wide, centred on the origin when drawn. */
internal fun skinStickerSize(bead: Float): androidx.compose.ui.geometry.Size =
    androidx.compose.ui.geometry.Size(STICKER_W * bead, STICKER_H * bead)

/**
 * The player's snake exactly as the Skin preview shows it — the same two rows,
 * textures, beads, wheel colours, accessory and Wyrm look — centred on the
 * origin. Draws into any DrawScope, including a bitmap for the export.
 */
internal fun DrawScope.drawSkinSticker(t: SkinTextures, skin: SkinState, look: WyrmLookSpec, bead: Float, tagId: Int = -1) {
    // The preview's own choice of beads: the custom pattern, else the preset, 256 long.
    val customGroups = skin.code.mapNotNull { SkinCatalog.group(it) }.filter { it in SkinCatalog.validGroups }.take(256)
    val active = skin.custom && customGroups.isNotEmpty()
    val source = if (active) customGroups else SkinCatalog.presets.getOrNull(skin.preset) ?: listOf(7)
    val groups = List(256) { source[it % source.size] }
    val colours = if (active) List(256) { skin.colourAt(it % customGroups.size) } else List(256) { 0 }
    val gap = bead * STICKER_GAP
    val x = -STICKER_BODY_W * bead / 2
    val headY = -bead * 0.5f - gap * 0.5f
    val tailY = bead * 0.5f + gap * 0.5f
    // The preview's 1 dp against its usual ~17 dp bead.
    drawSkinRows(t, groups, colours, skin.preset, active, skin.accessory, look.checked(), x, headY, tailY, bead, bead / 17f) { img, l, tp, iw, ih, tint, alpha ->
        drawImageExact(img, l, tp, iw, ih, tint, alpha)
    }
    // The tag (OM, 2026-10-04): the rope and art of [SwingTag] at rest (chain 1,
    // size 1), hanging back from the head along the head row.
    val item = SkinCatalog.tags.getOrNull(tagId) ?: return
    val image = t.tags[tagId] ?: return
    val step = 8f * (bead / 48f)
    val head = Offset(x + bead * 0.5f + step * (SKIN_ROW - 1), headY)
    val unit = bead / 29f
    val anchor = Offset(head.x - 8 * unit, head.y)
    val end = Offset(anchor.x - 9 * 4f * unit, anchor.y)
    val width = item.width * 0.285f * unit
    val height = item.height * 0.285f * unit
    // At rest the rope points straight back (angle pi), so the art turns half way.
    val centre = Offset(end.x - (item.anchorX * 0.285f * unit + width * 0.5f), end.y - (item.anchorY * 0.285f * unit + height * 0.5f))
    drawLine(item.accentA.rgbColor(), anchor, end, strokeWidth = 5 * unit, cap = StrokeCap.Round)
    for (lineWidth in listOf(4f, 3f, 2f)) {
        drawLine(item.accentB.rgbColor().copy(alpha = 0.5f), anchor, end, strokeWidth = lineWidth * unit, cap = StrokeCap.Round)
    }
    rotate(180f, pivot = centre) {
        val fit = min(width / image.width, height / image.height)
        val dw = image.width * fit
        val dh = image.height * fit
        drawImageExact(image, centre.x - dw / 2, centre.y - dh / 2, dw, dh, null, 1f)
    }
}

/**
 * NTL 9.68's tag rope as its skin chooser runs it (N5 with `Ce`; OM,
 * 2026-10-05: "same to same original NTL"), the same numbers as the engine's
 * `tags.c`: one step per drawn frame on NTL's default path (mb = 6.94: push
 * .2 mb, stiffness .005 mb, damping .05 mb, advance mb/17), before it a pull
 * of .3 back and a sway of .14 cos(frame/23 - 7 i/9), angles from NTL's `Zu`
 * table, sums in doubles stored as floats, and the bobble turning .15 of the
 * way to the last link each frame. Units are preview pixels: [unit] is one
 * snake-width (the head is 29).
 */
internal class NtlPreviewRope {
    val x = FloatArray(10)
    val y = FloatArray(10)
    private val vx = FloatArray(10)
    private val vy = FloatArray(10)
    /** The bobble's turn (NTL's `EA`). */
    var angle = 0.0
        private set
    private var seeded = false
    private var frame = 0

    private fun ntlAngle(dx: Double, dy: Double): Double {
        val s = when {
            dx >= -4.0 && dy >= -4.0 && dx < 4.0 && dy < 4.0 -> 32.0
            dx >= -8.0 && dy >= -8.0 && dx < 8.0 && dy < 8.0 -> 16.0
            dx >= -16.0 && dy >= -16.0 && dx < 16.0 && dy < 16.0 -> 8.0
            dx >= -127.0 && dy >= -127.0 && dx < 127.0 && dy < 127.0 -> 1.0
            else -> return atan2(dy, dx).toFloat().toDouble()
        }
        val qx = (s * dx + 128.0).toInt() - 128
        val qy = (s * dy + 128.0).toInt() - 128
        return atan2(qy.toDouble(), qx.toDouble()).toFloat().toDouble()
    }

    fun step(anchorX: Float, anchorY: Float, unit: Float, chain: Double) {
        val links = max(1.0, chain)
        val segment = 4.0 * links * unit
        if (!seeded || x.any { !it.isFinite() } || y.any { !it.isFinite() }) {
            // NTL (`Y3`): every point starts on the head, and the chooser never
            // lays the rope out, so it falls out of the head and hangs.
            for (i in 0 until 10) {
                x[i] = anchorX + 8f * unit
                y[i] = anchorY
                vx[i] = 0f
                vy[i] = 0f
            }
            angle = 0.0
            seeded = true
        }
        x[0] = anchorX
        y[0] = anchorY
        frame += 1
        for (i in 1 until 10) {
            vx[i] = (vx[i] - 0.3 * unit).toFloat()
            vy[i] = (vy[i] + 0.14 * unit * cos(frame / 23.0 - 7.0 * i / 9.0)).toFloat()
        }
        val mb = 6.94
        val push = 0.2 * mb * links
        val stiffness = 0.005 * mb
        val advance = mb / 17.0
        val damping = 0.05 * mb
        for (i in 1 until 10) {
            val px = x[i - 1].toDouble()
            val py = y[i - 1].toDouble()
            var dx = x[i] - px
            var dy = y[i] - py
            val a = if (dx == 0.0 && dy == 0.0) 0.0 else ntlAngle(dx, dy)
            val tx = px + push * cos(a) * unit
            val ty = py + push * sin(a) * unit
            vx[i] = (vx[i] + stiffness * (tx - x[i])).toFloat()
            vy[i] = (vy[i] + stiffness * (ty - y[i])).toFloat()
            x[i] = (x[i] + advance * vx[i]).toFloat()
            y[i] = (y[i] + advance * vy[i]).toFloat()
            vx[i] = (vx[i] * damping).toFloat()
            vy[i] = (vy[i] * damping).toFloat()
            dx = x[i].toDouble() - x[i - 1]
            dy = y[i].toDouble() - y[i - 1]
            if (sqrt(dx * dx + dy * dy) > segment) {
                val b = atan2(dy, dx)
                x[i] = (x[i - 1] + segment * cos(b)).toFloat()
                y[i] = (y[i - 1] + segment * sin(b)).toFloat()
            }
        }
        val he = 2.0 * PI
        var d = atan2(y[9].toDouble() - y[8], x[9].toDouble() - x[8]) - angle
        if (d < 0.0 || d >= he) d %= he
        if (d < -PI) d += he else if (d > PI) d -= he
        angle = (angle + 0.15 * d) % he
    }
}

/** `WyrmSwingTag`: the Skin preview's tag on NTL's own chooser rope ([NtlPreviewRope]), stepped every frame. */
@Composable
private fun SwingTag(
    item: SkinTagAsset,
    image: ImageBitmap,
    head: Offset,
    headSize: Float,
    boundsW: Float,
    boundsH: Float,
    chain: Double,
    @Suppress("UNUSED_PARAMETER") swing: Double, // NTL's chooser always takes the default path
    tagScale: Double,
) {
    val unit = headSize / 29f
    val anchor = Offset(head.x - 8 * unit, head.y)
    val rope = remember { NtlPreviewRope() }
    var tick by remember { mutableIntStateOf(0) }
    val latestAnchor by rememberUpdatedState(anchor)
    val latestUnit by rememberUpdatedState(unit)
    val latestChain by rememberUpdatedState(chain)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            rope.step(latestAnchor.x, latestAnchor.y, latestUnit, latestChain)
            tick++
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") tick
        val px = rope.x
        val py = rope.y
        val last = 9
        val rawWidth = item.width * 0.285f * unit * tagScale.toFloat()
        val rawHeight = item.height * 0.285f * unit * tagScale.toFloat()
        val fit = min(1f, 108.dp.toPx() / max(1f, max(rawWidth, rawHeight)))
        val width = rawWidth * fit
        val height = rawHeight * fit
        val attachX = item.anchorX * 0.285f * unit * tagScale.toFloat() * fit
        val attachY = item.anchorY * 0.285f * unit * tagScale.toFloat() * fit
        fun ropePath(to: Int, close: Boolean): Path = Path().apply {
            moveTo(px[last], py[last])
            for (index in last - 1 downTo to) {
                quadraticTo(px[index], py[index], (px[index] + px[index - 1]) * 0.5f, (py[index] + py[index - 1]) * 0.5f)
            }
            if (close) quadraticTo(px[1], py[1], px[0], py[0])
        }
        drawPath(ropePath(1, false), item.accentA.rgbColor(), style = Stroke(5 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (lineWidth in listOf(4f, 3f, 2f)) {
            drawPath(ropePath(2, true), item.accentB.rgbColor().copy(alpha = 0.5f), style = Stroke(lineWidth * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // NTL: translate to the rope's end, turn by the bobble's angle, draw the
        // box at its anchor offset.
        withTransform({
            translate(px[last], py[last])
            rotateRad(rope.angle.toFloat(), pivot = Offset.Zero)
        }) {
            val fitScale = min(width / image.width, height / image.height)
            val dw = image.width * fitScale
            val dh = image.height * fitScale
            drawImageInto(image, attachX + (width - dw) / 2, attachY + (height - dh) / 2, dw, dh)
        }
    }
}

/* ----------------------------------------------------------- AIR wheel */

/** The pattern toggle: a colour-wheel glyph while the beads show, a bead grid while the wheel does. */
@Composable
internal fun AirWheelToggle(showingWheel: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .alpha(if (enabled) 1f else 0.28f)
            .glassDisc()
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
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
internal fun AirWheelPanel(
    textures: SkinTextures?,
    prefs: android.content.SharedPreferences,
    onColour: (Int) -> Unit = {},
    onAdd: (kind: Int, rgb: Int) -> Unit,
) {
    var pointerX by remember { mutableDoubleStateOf(prefs.getFloat("air-pointer-x", 0f).toDouble()) }
    var pointerY by remember { mutableDoubleStateOf(prefs.getFloat("air-pointer-y", 0f).toDouble()) }
    var bezelAngle by remember { mutableDoubleStateOf(prefs.getFloat("air-bezel", 0f).toDouble()) }
    var rgb by remember { mutableIntStateOf(prefs.getInt("air-rgb", 0x808080) and 0xFFFFFF) }
    val pure = AirSkin.pure(pointerX, pointerY)
    val brightness = AirSkin.brightness(bezelAngle)

    // The wheel sits on the left and the two beads stand to its right (OM,
    // 2026-09-28), so the whole builder fits without scrolling the page.
    val guideText = androidx.compose.ui.text.rememberTextMeasurer()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f).widthIn(max = 300.dp).aspectRatio(1f)) {
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
                            val sx = ((down.position.x - centre.x) / unitPx).toDouble()
                            val sy = ((down.position.y - centre.y) / unitPx).toDouble()
                            val knobX = cos(bezelAngle) * AirSkin.BEZEL_POINTER_RADIUS
                            val knobY = sin(bezelAngle) * AirSkin.BEZEL_POINTER_RADIUS
                            // The brightness knob turns only when it is the thing
                            // held; anywhere else on the ring the touch is left to
                            // the page, so scrolling never spins it.
                            drag = when {
                                hypot(sx - pointerX, sy - pointerY) <= 30 -> Triple(0, pointerX, pointerY)
                                sqrt(sx * sx + sy * sy) <= AirSkin.WHEEL_RADIUS -> Triple(0, sx, sy)
                                hypot(sx - knobX, sy - knobY) <= 34 -> Triple(1, 0.0, 0.0)
                                else -> null
                            }
                            if (drag == null) return@awaitEachGesture
                            down.consume()
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
                            onColour(rgb)
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
                drawWheelGuides(centre, unitPx.toFloat(), guideText)
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
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
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

/**
 * Accessibility marks on the wheel (OM, 2026-09-28), as `WyrmAirWheelGuides`
 * on iOS, so a colour can be found again: a faint 12 x 12 grid over the colour
 * disc, and a clock face round the bezel: a dash every 6 degrees, a longer one
 * every 30 with its hour, 1 to 12. The numbers keep one size at any wheel size.
 */
private fun DrawScope.drawWheelGuides(centre: Offset, unitPx: Float, measurer: androidx.compose.ui.text.TextMeasurer) {
    val r = (AirSkin.WHEEL_RADIUS * unitPx).toFloat()
    val cell = 2 * r / 12
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(centre, r)) }
    val hair = 0.5.dp.toPx()
    clipPath(disc) {
        for (i in 1 until 12) {
            val o = -r + i * cell
            // Dark then light, so the grid shows on every hue.
            drawLine(Color.Black.copy(alpha = 0.10f), Offset(centre.x + o, centre.y - r), Offset(centre.x + o, centre.y + r), hair)
            drawLine(Color.Black.copy(alpha = 0.10f), Offset(centre.x - r, centre.y + o), Offset(centre.x + r, centre.y + o), hair)
            drawLine(Color.White.copy(alpha = 0.12f), Offset(centre.x + o + hair, centre.y - r), Offset(centre.x + o + hair, centre.y + r), hair)
            drawLine(Color.White.copy(alpha = 0.12f), Offset(centre.x - r, centre.y + o + hair), Offset(centre.x + r, centre.y + o + hair), hair)
        }
    }
    val inner = ((AirSkin.WHEEL_RADIUS + AirSkin.BEZEL_WIDTH) * unitPx).toFloat() + 2.dp.toPx()
    for (step in 0 until 60) {
        val a = step / 60f * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
        val long = step % 5 == 0
        val reach = inner + (if (long) 6.dp else 3.dp).toPx()
        drawLine(
            if (long) Wyrm.Mute else Wyrm.Quiet.copy(alpha = 0.55f),
            Offset(centre.x + cos(a) * inner, centre.y + sin(a) * inner),
            Offset(centre.x + cos(a) * reach, centre.y + sin(a) * reach),
            (if (long) 1.3.dp else 0.8.dp).toPx(),
        )
    }
    val labelRadius = inner + 14.dp.toPx()
    val style = TextStyle(fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = Wyrm.Mute)
    for (hour in 1..12) {
        val a = hour / 12f * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
        val layout = measurer.measure(hour.toString(), style)
        val at = Offset(centre.x + cos(a) * labelRadius, centre.y + sin(a) * labelRadius)
        drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

@Suppress("unused")
private val keepShadow = Modifier.shadow(0.dp)
