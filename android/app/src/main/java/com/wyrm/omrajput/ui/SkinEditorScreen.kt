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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Everything the editor needs from the engine, as the engine actually holds it. */
data class SkinTables(
    /** 0xRRGGBB per colour group, straight from the engine's palette. */
    val palette: IntArray = IntArray(0),
    /** The character each colour group is spelled with inside a skin code. */
    val codeChars: ByteArray = ByteArray(0),
    /** 66 presets, stride 64: [length, cg, cg, ...]. */
    val presets: ByteArray = ByteArray(0),
) {
    val presetCount: Int get() = if (presets.isEmpty()) 0 else presets.size / PRESET_STRIDE
    val ready: Boolean get() = palette.isNotEmpty() && presetCount > 0

    /** The colour groups a preset repeats along the body. */
    fun presetGroups(index: Int): List<Int> {
        val base = index * PRESET_STRIDE
        if (base >= presets.size) return emptyList()
        val length = presets[base].toInt() and 0xFF
        if (length <= 0) return emptyList()
        return (0 until minOf(length, PRESET_STRIDE - 1)).map { step ->
            presets[base + 1 + step].toInt() and 0xFF
        }
    }

    /** The same sequence as flat colours, for before the sprite sheet loads. */
    fun presetColours(index: Int): List<Color> =
        presetGroups(index).mapNotNull { colourOf(it) }

    fun colourOf(group: Int): Color? {
        if (group !in palette.indices) return null
        val rgb = palette[group]
        return Color(0xFF000000.toInt() or rgb)
    }

    /** The colour group a skin-code character stands for, or -1. */
    fun groupOf(character: Char): Int =
        codeChars.indexOfFirst { it.toInt().toChar() == character }

    companion object {
        const val PRESET_STRIDE = 64

        /**
         * Four of the 42 groups are rejected by the engine. They are left out
         * of the palette entirely rather than shown greyed — a grid with two
         * deliberate gaps reads better than one with four dead cells.
         */
        val DEAD_GROUPS = setOf(36, 38, 40, 41)
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** What the editor currently has on. */
data class SkinState(
    val custom: Boolean = false,
    val preset: Int = 0,
    val code: String = "",
    val accessory: Int = -1,
    /**
     * Packed ARGB per position of [code], for positions built with the picker.
     * A 0 entry — or a position past the end — renders from the palette, which
     * is every skin that predates the picker and every code typed by hand.
     *
     * Only the colour group behind each position ever reaches an arena. This is
     * what Wyrm draws instead, and the only place an alpha exists at all.
     */
    val colours: IntArray = IntArray(0),
) {
    fun colourAt(index: Int): Int = colours.getOrElse(index) { 0 }

    /** The same list resized to [length], so it always pairs with the code. */
    fun coloursFor(length: Int): IntArray =
        IntArray(length) { colours.getOrElse(it) { 0 } }

    override fun equals(other: Any?): Boolean =
        other is SkinState && custom == other.custom && preset == other.preset &&
            code == other.code && accessory == other.accessory &&
            colours.contentEquals(other.colours)

    override fun hashCode(): Int =
        (((custom.hashCode() * 31 + preset) * 31 + code.hashCode()) * 31 +
            accessory) * 31 + colours.contentHashCode()
}

/**
 * Skin editor, portrait.
 *
 * The snake at the top is the engine's own preview, drawn straight through a
 * transparent gap in this layout — the same body, shading and accessory sprites
 * the arena uses, rather than a second implementation that would drift from it.
 * Everything below is Compose, in the same numbered-ledger language as Home.
 *
 * The three groups are tabs rather than one long scroll because two of them are
 * engine-drawn: sprites cannot follow a Compose scroll without lagging a frame
 * behind it, so nothing that the engine draws is ever allowed to move.
 */
@Composable
fun SkinEditorScreen(
    tables: SkinTables,
    state: SkinState,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onPickPreset: (Int) -> Unit,
    onCodeChange: (String, IntArray) -> Unit,
    onPickAccessory: (Int) -> Unit,
    background: Int,
    onPickBackground: (Int) -> Unit,
    settings: List<com.wyrm.omrajput.data.Setting>,
    tagCode: String,
    tagFetchState: String,
    onSettingChange: (com.wyrm.omrajput.data.Setting, List<Float>) -> Unit,
    onTagCodeChange: (String) -> Unit,
    onFetchTag: (String) -> Unit,
    onPreviewLayout: (centreY: Float, scale: Float) -> Unit,
    onAccessoryLayout: (x: Float, y: Float, cell: Float, gap: Float) -> Unit,
    onWear: () -> Unit,
    startTab: Int = 0,
) {
    var tab by remember { mutableIntStateOf(startTab.coerceIn(0, 4)) }
    val atlas by rememberSkinAtlas()

    // Leaving the accessory tab has to retract the sprites the engine is
    // drawing, or they would hang over whichever tab replaced them.
    LaunchedEffect(tab) {
        if (tab != 2) onAccessoryLayout(0f, 0f, 0f, 0f)
    }

    // No backdrop anywhere on this screen. The engine is rendering behind the
    // whole Compose tree, and anything painted here — even the canvas colour —
    // would hide the preview and the accessory sprites. The engine clears to
    // Wyrm's black while it owns this screen, so the background is already right.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insetTop, bottom = insetBottom),
        ) {
            Rail(onBack = onBack, modifier = Modifier.padding(horizontal = Wyrm.Gutter))

            PreviewWindow(onLayout = onPreviewLayout)

            Tabs(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = Wyrm.Gutter),
            )

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> PresetTab(tables, state, atlas, onPickPreset)
                    1 -> PatternTab(tables, state, atlas, onCodeChange)
                    2 -> AccessoryTab(state, onPickAccessory, onAccessoryLayout)
                    3 -> BackgroundsTab(background, onPickBackground)
                    else -> TagsTab(
                        settings = settings,
                        onChange = onSettingChange,
                        onFetch = onFetchTag,
                        fetchState = tagFetchState,
                        code = tagCode,
                        onCodeChange = onTagCodeChange,
                    )
                }
            }

            WyrmPrimaryAction(
                // A skin and a floor are worn by nobody. Only the two tabs that
                // actually put something on the snake say so.
                label = when (tab) {
                    2, 4 -> "Wear it"
                    else -> "Use it"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Wyrm.Gutter)
                    .height(62.dp),
                onClick = onWear,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Rail(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "←",
            fontFamily = Wyrm.Body,
            fontSize = 20.sp,
            color = Wyrm.SoftWhite,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "SKIN",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 3.sp,
            color = Wyrm.White,
        )
    }
}

/**
 * The hole the engine draws into.
 *
 * Nothing is painted here — the SDL surface is behind the whole Compose tree, so
 * leaving this band transparent is what makes the real preview visible. Its
 * measured geometry goes back to the engine so the snake lands inside it.
 */
@Composable
private fun PreviewWindow(onLayout: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .onGloballyPositioned { coordinates ->
                val centreY = coordinates.positionInRoot().y + coordinates.size.height / 2f
                // The body is 128 segments spaced a sixth of a segment apart, so
                // it spans a little over 22 segment widths; this is the scale
                // that makes that span most of the screen.
                val scale = coordinates.size.width * 0.94f / 22.17f
                onLayout(centreY, scale)
            },
    )
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Four tabs, so the ledger numbers that the other screens carry are gone
    // from here: a quarter of a phone's width is not enough for "03 Accessory",
    // and a fourth tab nobody can read is a fourth tab nobody finds.
    val labels = listOf("Preset", "Pattern", "Accessory", "Background", "Tags")
    Column(modifier = modifier.fillMaxWidth()) {
        WyrmRule()
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 14.dp),
                ) {
                    Text(
                        text = label,
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = if (active) Wyrm.White else Wyrm.Faint,
                    )
                    Spacer(Modifier.height(10.dp))
                    WyrmRule(color = if (active) Wyrm.White else Color.Transparent)
                }
            }
        }
        WyrmRule()
    }
}

/** The 66 presets, drawn from the engine's own colour sequences. */
@Composable
private fun PresetTab(
    tables: SkinTables,
    state: SkinState,
    atlas: ImageBitmap?,
    onPick: (Int) -> Unit,
) {
    if (!tables.ready) {
        Empty("Waiting for the engine")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Wyrm.Gutter, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (index in 0 until tables.presetCount) {
            PresetStrip(
                ordinal = index + 1,
                groups = tables.presetGroups(index),
                colours = tables.presetColours(index),
                atlas = atlas,
                selected = !state.custom && state.preset == index,
                onClick = { onPick(index) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * One preset per line, as a long strip of the sequence it repeats.
 *
 * A skin is a pattern running down a body, so it is shown as a length of body
 * rather than as a swatch — a two-colour skin reads as stripes, a long one reads
 * as the gradient it actually is.
 */
@Composable
private fun PresetStrip(
    ordinal: Int,
    groups: List<Int>,
    colours: List<Color>,
    atlas: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ordinal.toString().padStart(2, '0'),
            fontFamily = Wyrm.Display,
            fontSize = 13.sp,
            color = if (selected) Wyrm.White else Wyrm.Faint,
            modifier = Modifier.width(30.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(wyrmRounded(17.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Wyrm.White else Wyrm.Line,
                    shape = wyrmRounded(17.dp),
                )
                .padding(if (selected) 3.dp else 2.dp)
                .clip(wyrmRounded(15.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (groups.isEmpty()) return@Canvas
                if (atlas != null) {
                    // Real beads, overlapped the way a body overlaps, so a strip
                    // looks like a length of snake rather than a colour bar.
                    val bead = size.height
                    val step = bead * 0.42f
                    val count = kotlin.math.ceil(size.width / step).toInt() + 1
                    for (index in 0 until count) {
                        drawBead(
                            atlas,
                            groups[index % groups.size],
                            index * step,
                            0f,
                            bead,
                        )
                    }
                } else {
                    val cell = 15.dp.toPx()
                    val cells = kotlin.math.ceil(size.width / cell).toInt()
                    for (index in 0 until cells) {
                        drawRect(
                            color = colours[index % colours.size],
                            topLeft = Offset(index * cell, 0f),
                            size = Size(cell + 1f, size.height),
                        )
                    }
                }
            }
        }
    }
}

/** The custom skin: a sequence you build one colour at a time. */
@Composable
private fun PatternTab(
    tables: SkinTables,
    state: SkinState,
    atlas: ImageBitmap?,
    onCodeChange: (String, IntArray) -> Unit,
) {
    if (!tables.ready) {
        Empty("Waiting for the engine")
        return
    }
    var building by remember { mutableStateOf(false) }

    /** Append one position: its letter to the code, its colour beside it. */
    fun append(group: Int, argb: Int) {
        if (state.code.length >= MAX_SKIN_CODE) return
        val character = tables.codeChars.getOrNull(group)?.toInt()?.toChar() ?: return
        val colours = state.coloursFor(state.code.length) + argb
        onCodeChange(state.code + character, colours)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Wyrm.Gutter, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WyrmLabel("Your pattern")
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.code.length} / $MAX_SKIN_CODE",
                fontFamily = Wyrm.Body,
                fontSize = 11.sp,
                color = if (state.code.length >= MAX_SKIN_CODE) Wyrm.White else Wyrm.Faint,
            )
            Spacer(Modifier.width(10.dp))
            // Short, because this row already carries the section label and the
            // length counter: a longer word here is what pushed the text out of
            // its own pill. The section heading below says which one is open.
            SmallAction(if (building) "Palette" else "Build") {
                building = !building
            }
        }
        Spacer(Modifier.height(8.dp))
        CodeField(tables, state, onCodeChange)
        Spacer(Modifier.height(8.dp))
        CodeRibbon(tables, atlas, state)
        Spacer(Modifier.height(12.dp))

        val clipboard = LocalClipboardManager.current
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallAction("Undo", Modifier.weight(1f), enabled = state.code.isNotEmpty()) {
                val shorter = state.code.length - 1
                onCodeChange(state.code.dropLast(1), state.coloursFor(shorter))
            }
            SmallAction("Copy", Modifier.weight(1f), enabled = state.code.isNotEmpty()) {
                clipboard.setText(AnnotatedString(state.code))
            }
            SmallAction("Clear", Modifier.weight(1f), enabled = state.code.isNotEmpty()) {
                onCodeChange("", IntArray(0))
            }
        }

        Spacer(Modifier.height(Wyrm.GapLarge))
        WyrmLabel(if (building) "Mix a colour" else "Colours")
        Spacer(Modifier.height(10.dp))
        // The two ways of choosing occupy the same place, so the one you are
        // not using is not competing for the screen underneath the preview.
        AnimatedContent(
            targetState = building,
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) {
                    direction * it / 3
                } + fadeIn(tween(220)))
                    .togetherWith(
                        slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) {
                            -direction * it / 3
                        } + fadeOut(tween(160))
                    )
            },
            label = "skin-colour-source",
        ) { mixing ->
            if (mixing) {
                ColourStudio(tables, enabled = state.code.length < MAX_SKIN_CODE) { group, argb ->
                    append(group, argb)
                }
            } else {
                Palette(tables, atlas) { group -> append(group, 0) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The skin code itself, typed.
 *
 * A skin code is text — one character per colour — so this is a real text field:
 * you can type it, and long-press gives you Android's own Paste, which is how a
 * code shared by someone else gets in. Anything that is not a colour is dropped
 * as it arrives, so a pasted code carrying stray characters still lands clean.
 */
@Composable
private fun CodeField(
    tables: SkinTables,
    state: SkinState,
    onCodeChange: (String, IntArray) -> Unit,
) {
    val code = state.code
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(wyrmRounded(Wyrm.CornerSmall))
            .background(Color.Black.copy(alpha = 0.30f))
            .background(wellFill())
            .border(1.dp, wellEdge(), wyrmRounded(Wyrm.CornerSmall)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = code,
            onValueChange = { typed ->
                val cleaned = typed
                    .lowercase()
                    .filter { tables.groupOf(it) >= 0 }
                    .take(MAX_SKIN_CODE)
                // Colours are positional, so only the prefix that survived the
                // edit keeps its mixture; anything newly typed is a plain
                // palette letter until it is built again.
                if (cleaned != code) {
                    val kept = cleaned.commonPrefixWith(code).length
                    onCodeChange(
                        cleaned,
                        IntArray(cleaned.length) {
                            if (it < kept) state.colourAt(it) else 0
                        },
                    )
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
                color = Wyrm.White,
            ),
            cursorBrush = SolidColor(Wyrm.Green),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            decorationBox = { inner ->
                if (code.isEmpty()) {
                    Text(
                        text = "Type a code, paste one, or tap colours",
                        fontFamily = Wyrm.Body,
                        fontSize = 12.sp,
                        color = Wyrm.Faint,
                    )
                }
                inner()
            },
        )
    }
}

/**
 * What those characters actually are, as the beads they stand for.
 *
 * A position built with the picker is drawn as its own mixture over the
 * transparency tiles rather than as the palette bead its letter stands for, so
 * the ribbon shows the body you will actually see instead of the one the arena
 * will be told about.
 */
@Composable
private fun CodeRibbon(tables: SkinTables, atlas: ImageBitmap?, state: SkinState) {
    val code = state.code
    if (code.isEmpty()) return
    val scroll = rememberScrollState()
    val groups = code.map { tables.groupOf(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .horizontalScroll(scroll),
    ) {
        if (atlas != null) {
            Canvas(modifier = Modifier
                .width((groups.size * 10).dp)
                .fillMaxHeight()) {
                val bead = size.height
                val step = 10.dp.toPx()
                groups.forEachIndexed { index, group ->
                    val built = state.colourAt(index)
                    if (built != 0) {
                        drawCircle(Color(built), bead / 2f,
                                   Offset(index * step + bead / 2f, bead / 2f))
                    } else if (group >= 0) {
                        drawBead(atlas, group, index * step, 0f, bead)
                    }
                }
            }
        } else {
            groups.forEachIndexed { index, group ->
                val built = state.colourAt(index)
                Box(
                    modifier = Modifier
                        .width(9.dp)
                        .fillMaxHeight()
                        .background(
                            if (built != 0) Color(built)
                            else tables.colourOf(group) ?: Wyrm.Raised
                        )
                )
            }
        }
    }
}

/**
 * Mixing a colour of your own.
 *
 * An arena only ever hears about colour groups, so whatever is mixed here is
 * matched to the nearest group and it is that group's letter which goes into
 * the code and out over the wire. The exact mixture travels beside it and is
 * what Wyrm draws — including an alpha, which the protocol has no field for at
 * all and which therefore only ever exists on this device.
 *
 * Tapping the square chooses; the bead beside it is what you chose, and tapping
 * the bead is what commits it onto the body above.
 */
@Composable
internal fun ColourStudio(
    tables: SkinTables,
    enabled: Boolean,
    paper: Boolean = false,
    onAdd: (group: Int, argb: Int) -> Unit,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0.85f) }
    var value by remember { mutableFloatStateOf(1f) }
    var fade by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(1f) }

    val pure = remember(hue, saturation, value) { hsvColour(hue, saturation, value) }
    val faded = remember(pure, fade) { fadeColour(pure, fade) }
    val mixed = faded.copy(alpha = alpha)
    val group = remember(faded) { nearestGroup(tables, faded) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // The field: saturation across, brightness down.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(wyrmRounded(Wyrm.Corner))
                    .border(
                        1.dp,
                        if (paper) SolidColor(Wyrm.Rule) else glassEdge(),
                        wyrmRounded(Wyrm.Corner),
                    ),
            ) {
                val onPick: (Offset, Size) -> Unit = { position, area ->
                    if (area.width > 0f && area.height > 0f) {
                        saturation = (position.x / area.width).coerceIn(0f, 1f)
                        value = 1f - (position.y / area.height).coerceIn(0f, 1f)
                    }
                }
                var area by remember { mutableStateOf(Size.Zero) }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { onPick(it, area) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                onPick(change.position, area)
                            }
                        },
                ) {
                    area = size
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.White, hsvColour(hue, 1f, 1f))
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                    )
                    val cx = saturation * size.width
                    val cy = (1f - value) * size.height
                    drawCircle(Color.Black.copy(alpha = .55f), 9.dp.toPx(), Offset(cx, cy),
                               style = Stroke(width = 3.dp.toPx()))
                    drawCircle(Color.White, 9.dp.toPx(), Offset(cx, cy),
                               style = Stroke(width = 1.5.dp.toPx()))
                }
            }

            // What you mixed, as the bead it will become.
            Column(
                modifier = Modifier.width(88.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .checkerboard()
                        .background(mixed)
                        .border(
                            1.dp,
                            if (paper) SolidColor(Wyrm.Rule) else glassEdge(1.4f),
                            CircleShape,
                        )
                        .clickable(enabled = enabled) { onAdd(group, mixed.packed()) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (enabled) "Tap to add" else "Pattern full",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = if (paper) {
                        if (enabled) Wyrm.Ink else Wyrm.TabIdle
                    } else if (enabled) Wyrm.White else Wyrm.Faint,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = mixed.hex(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (paper) Wyrm.Quiet else Wyrm.Faint,
                )
                Spacer(Modifier.height(2.dp))
                // The one honest line on this screen: the wire carries a group,
                // not a mixture, so the letter it resolves to is shown.
                Text(
                    text = "sends ${tables.codeChars.getOrNull(group)
                        ?.toInt()?.toChar()?.uppercase().orEmpty()}",
                    fontFamily = Wyrm.Body,
                    fontSize = 9.sp,
                    color = if (paper) Wyrm.Quiet else Wyrm.Faint,
                    textAlign = TextAlign.Center,
                )
            }
        }

        StudioSlider(
            label = "Colour",
            value = hue / 360f,
            track = Brush.horizontalGradient(
                (0..6).map { hsvColour(it * 60f, 1f, 1f) }
            ),
            thumb = hsvColour(hue, 1f, 1f),
            paper = paper,
        ) { hue = it * 360f }

        StudioSlider(
            label = "Fade",
            value = (fade + 0.5f) / 1.5f,
            track = Brush.horizontalGradient(listOf(Color.Black, pure, Color.White)),
            thumb = faded,
            paper = paper,
        ) { fade = it * 1.5f - 0.5f }

        // Not called transparency, because it is not one. The body is drawn as
        // heavily overlapping stamps that each blend separately, so the alpha
        // compounds — six overlaps turn 50% into 98% — and what you actually
        // get is a softened, ghosted snake rather than one you can see through.
        // Naming it for the effect keeps the slider honest.
        StudioSlider(
            label = "Ghost",
            value = alpha,
            track = Brush.horizontalGradient(
                listOf(faded.copy(alpha = 0f), faded)
            ),
            thumb = faded,
            checkered = true,
            paper = paper,
        ) { alpha = it.coerceAtLeast(MIN_SKIN_ALPHA) }
    }
}

/**
 * One track with a thumb on it.
 *
 * Deliberately not a Material Slider: these show a colour continuum rather than
 * a magnitude, so the track is the information and a filled "active" portion in
 * front of it would only hide half of what you are choosing between.
 */
@Composable
private fun StudioSlider(
    label: String,
    value: Float,
    track: Brush,
    thumb: Color,
    checkered: Boolean = false,
    paper: Boolean = false,
    onValue: (Float) -> Unit,
) {
    Column {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            color = if (paper) Wyrm.Quiet else Wyrm.Faint,
        )
        Spacer(Modifier.height(6.dp))
        var width by remember { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(wyrmRounded(Wyrm.Pill))
                .then(if (checkered) Modifier.checkerboard() else Modifier)
                .background(track)
                .border(
                    1.dp,
                    if (paper) SolidColor(Wyrm.Rule) else glassEdge(),
                    wyrmRounded(Wyrm.Pill),
                )
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        if (width > 0f) onValue((position.x / width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (width > 0f) {
                            onValue((change.position.x / width).coerceIn(0f, 1f))
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                width = size.width
                val cx = value.coerceIn(0f, 1f) * size.width
                val r = size.height * 0.34f
                drawCircle(Color.Black.copy(alpha = .5f), r + 2.dp.toPx(),
                           Offset(cx, size.height / 2), style = Stroke(3.dp.toPx()))
                drawCircle(thumb, r, Offset(cx, size.height / 2))
                drawCircle(Color.White, r, Offset(cx, size.height / 2),
                           style = Stroke(2.dp.toPx()))
            }
        }
    }
}

/** The tiles behind anything that can be seen through. */
private fun Modifier.checkerboard(): Modifier = drawBehind {
    val cell = 7.dp.toPx()
    drawRect(Color(0xFF3A3A3A))
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            if ((row + column) % 2 == 0) {
                drawRect(
                    color = Color(0xFF565656),
                    topLeft = Offset(x, y),
                    size = Size(
                        minOf(cell, size.width - x),
                        minOf(cell, size.height - y),
                    ),
                )
            }
            x += cell
            column++
        }
        y += cell
        row++
    }
}

/** HSV, because a wheel of hue with a square of the rest is how this is chosen. */
private fun hsvColour(hue: Float, saturation: Float, value: Float): Color =
    Color.hsv(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f),
              value.coerceIn(0f, 1f))

/**
 * Tint towards white or shade towards black, on top of the colour already
 * chosen — the same one-dimensional lightening the reference client applies
 * after its wheel, kept because it is a far quicker way to reach a pastel or a
 * near-black than dragging the square into a corner.
 */
private fun fadeColour(colour: Color, fade: Float): Color {
    val amount = fade.coerceIn(-0.5f, 1f)
    val towards = if (amount >= 0f) 1f else 0f
    val mix = if (amount >= 0f) amount else -amount
    return Color(
        red = colour.red + (towards - colour.red) * mix,
        green = colour.green + (towards - colour.green) * mix,
        blue = colour.blue + (towards - colour.blue) * mix,
        alpha = colour.alpha,
    )
}

/** The palette entry a mixed colour will be sent as. */
private fun nearestGroup(tables: SkinTables, colour: Color): Int {
    var best = 0
    var bestDistance = Float.MAX_VALUE
    tables.palette.indices.forEach { group ->
        if (group in SkinTables.DEAD_GROUPS) return@forEach
        val candidate = tables.colourOf(group) ?: return@forEach
        // Weighted towards green, which is where the eye keeps most of its
        // ability to tell two nearby colours apart.
        val dr = (candidate.red - colour.red) * 0.30f
        val dg = (candidate.green - colour.green) * 0.59f
        val db = (candidate.blue - colour.blue) * 0.11f
        val distance = dr * dr + dg * dg + db * db
        if (distance < bestDistance) {
            bestDistance = distance
            best = group
        }
    }
    return best
}

private fun Color.packed(): Int =
    (((alpha * 255).toInt().coerceIn(0, 255)) shl 24) or
        (((red * 255).toInt().coerceIn(0, 255)) shl 16) or
        (((green * 255).toInt().coerceIn(0, 255)) shl 8) or
        ((blue * 255).toInt().coerceIn(0, 255))

private fun Color.hex(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val percent = (alpha * 100).toInt().coerceIn(0, 100)
    return "#%02X%02X%02X · %d%%".format(r, g, b, percent)
}

/**
 * Fully invisible is indistinguishable from a position that was never built,
 * which is how the engine tells the two apart, so the slider stops just short.
 */
private const val MIN_SKIN_ALPHA = 0.04f

/** The live colour groups, as the arena's own beads. */
@Composable
private fun Palette(
    tables: SkinTables,
    atlas: ImageBitmap?,
    onPick: (Int) -> Unit,
) {
    val groups = tables.palette.indices.filter { it !in SkinTables.DEAD_GROUPS }
    val columns = 7
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.chunked(columns).forEach { rowGroups ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowGroups.forEach { group ->
                    val colour = tables.colourOf(group) ?: Wyrm.Raised
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable { onPick(group) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (atlas != null) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawBead(atlas, group, 0f, 0f, size.minDimension)
                            }
                        } else {
                            // Until the sheet is decoded, the flat colour at
                            // least keeps the grid in the right places.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(colour)
                            )
                        }
                        // The letter this bead is spelled with, so the code in
                        // the field above is readable rather than a cipher.
                        Text(
                            text = tables.codeChars.getOrNull(group)
                                ?.toInt()?.toChar()?.uppercase().orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (isLight(colour)) Wyrm.Black else Wyrm.White,
                        )
                    }
                }
                repeat(columns - rowGroups.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Accessories are sprites in the game's atlas, so the engine draws them and
 * Compose only marks which one is chosen. The grid never scrolls, so the
 * sprites and the rings they sit in can never drift apart.
 */
@Composable
private fun AccessoryTab(
    state: SkinState,
    onPick: (Int) -> Unit,
    onLayout: (Float, Float, Float, Float) -> Unit,
) {
    val columns = 7
    val rows = (ACCESSORY_COUNT + columns - 1) / columns
    val gap = 6.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Wyrm.Gutter, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WyrmLabel("Worn")
            Spacer(Modifier.weight(1f))
            SmallAction("None", Modifier.width(96.dp), enabled = state.accessory >= 0) {
                onPick(-1)
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    // The same arithmetic Compose's weights perform, so the
                    // sprites land exactly inside the rings drawn for them.
                    val cell = (coordinates.size.width - gapPx * (columns - 1)) / columns
                    onLayout(
                        coordinates.positionInRoot().x,
                        coordinates.positionInRoot().y,
                        cell,
                        gapPx,
                    )
                },
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until columns) {
                        val id = row * columns + column
                        if (id < ACCESSORY_COUNT) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(wyrmRounded(Wyrm.CornerSmall))
                                    .background(glassFill())
                                    .border(
                                        width = if (state.accessory == id) 2.dp else 1.dp,
                                        brush = if (state.accessory == id) SolidColor(Wyrm.White)
                                        else glassEdge(),
                                        shape = wyrmRounded(Wyrm.CornerSmall),
                                    )
                                    .clickable { onPick(id) }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(wyrmRounded(Wyrm.Pill))
            .background(glassFill(if (enabled) 1f else 0.4f))
            .border(1.dp, glassEdge(if (enabled) 1f else 0.4f), wyrmRounded(Wyrm.Pill))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
            color = if (enabled) Wyrm.SoftWhite else Wyrm.Faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Every caller before this one passed a fixed width, so the label
            // had never had to keep itself off the edge of its own pill.
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Empty(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Faint)
    }
}

/** Whether a swatch needs dark ink on it rather than light. */
private fun isLight(colour: Color): Boolean =
    (0.299f * colour.red + 0.587f * colour.green + 0.114f * colour.blue) > 0.55f

private const val MAX_SKIN_CODE = 256
