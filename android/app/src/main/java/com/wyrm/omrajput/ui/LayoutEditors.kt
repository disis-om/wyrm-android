package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.composables.icons.lucide.R as LucideR
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wyrm.omrajput.data.Hotkey
import com.wyrm.omrajput.data.Setting
import java.util.Locale

/**
 * Where the controls sit, arranged sideways.
 *
 * Landscape because that is how the phone is held in a match: a layout
 * arranged in portrait would be a layout arranged for a shape the arena never
 * has. Positions are kept as a fraction of the safe area rather than in pixels,
 * so the same layout survives a different phone, a notch, or a rotation.
 *
 * Everything is dragged directly — there is no handle, no selection step and
 * no mode. What you drag is the control itself, at the size and opacity it
 * will have in play.
 */
@Composable
fun ControlLayoutEditor(
    joystick: Offset,
    boost: Offset,
    zoom: Offset,
    showJoystick: Boolean,
    showBoost: Boolean,
    showZoom: Boolean,
    joystickSize: Float,
    boostSize: Float,
    zoomLength: Float,
    zoomVertical: Boolean,
    opacity: Float,
    safeInsets: SafeInsets,
    onMove: (target: LayoutTarget, position: Offset) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var size by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = Offset(it.width.toFloat(), it.height.toFloat()) },
    ) {
        ArenaHint()

        if (size.x > 0f) {
            val area = safeInsets.area(size)

            if (showJoystick) {
                Draggable(
                    position = joystick,
                    area = area,
                    onMove = { onMove(LayoutTarget.JOYSTICK, it) },
                ) {
                    PaperJoystick(diameter = (112 * joystickSize).dp, opacity = opacity)
                }
            }
            if (showBoost) {
                Draggable(
                    position = boost,
                    area = area,
                    onMove = { onMove(LayoutTarget.BOOST, it) },
                ) {
                    PaperBoostButton(diameter = (86 * boostSize).dp, opacity = opacity)
                }
            }
            if (showZoom) {
                Draggable(
                    position = zoom,
                    area = area,
                    onMove = { onMove(LayoutTarget.ZOOM, it) },
                ) {
                    PaperZoomBar(
                        length = (190 * zoomLength).dp,
                        vertical = zoomVertical,
                        opacity = opacity,
                        value = 0.45f,
                    )
                }
            }
        }

        EditorBar(
            caption = "Drag each control where your thumb sits",
            onReset = onReset,
            onSave = onSave,
            onCancel = onCancel,
            modifier = Modifier.align(Alignment.TopCenter),
            topInset = with(density) { safeInsets.top.toDp() },
        )
    }
}

/** The same, for the on-screen buttons. Only the visible ones are placed. */
@Composable
fun OnScreenButtonLayoutEditor(
    hotkeys: List<Hotkey>,
    keyScale: Float,
    opacity: Float,
    safeInsets: SafeInsets,
    onMove: (action: Int, position: Offset) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var size by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = Offset(it.width.toFloat(), it.height.toFloat()) },
    ) {
        ArenaHint()

        if (size.x > 0f) {
            val area = safeInsets.area(size)
            hotkeys.filter { it.visible }.forEach { hotkey ->
                Draggable(
                    position = Offset(hotkey.x, hotkey.y),
                    area = area,
                    onMove = { onMove(hotkey.action, it) },
                ) {
                    PaperKey(label = hotkey.name, opacity = opacity, scale = keyScale)
                }
            }
        }

        EditorBar(
            caption = "Drag each button into place",
            onReset = onReset,
            onSave = onSave,
            onCancel = onCancel,
            modifier = Modifier.align(Alignment.TopCenter),
            topInset = with(density) { safeInsets.top.toDp() },
        )
    }
}

/** Every movable screen-space arena element, independent of touch controls. */
@Composable
fun ArenaHudLayoutEditor(
    positions: Map<ArenaHudTarget, Offset>,
    minimapSize: Float,
    leaderboardFont: Int,
    statsFont: Int,
    safeInsets: SafeInsets,
    onMove: (ArenaHudTarget, Offset) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var size by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val mapDp = with(density) { minimapSize.coerceIn(128f, 512f).toDp() }
    val leaderboardScale = 1f + leaderboardFont.coerceIn(0, 2) * 0.16f
    val statsScale = 1f + statsFont.coerceIn(0, 2) * 0.14f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = Offset(it.width.toFloat(), it.height.toFloat()) },
    ) {
        ArenaHint()
        if (size.x > 0f) {
            val area = safeInsets.area(size)
            fun position(target: ArenaHudTarget) = positions[target] ?: target.fallback

            Draggable(position(ArenaHudTarget.MINIMAP), area, { onMove(ArenaHudTarget.MINIMAP, it) }) {
                Box(
                    modifier = Modifier
                        .size(mapDp)
                        .background(Wyrm.Card.copy(alpha = 0.18f), CircleShape)
                        .border(3.dp, Wyrm.Ink.copy(alpha = 0.76f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("MAP", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, color = Wyrm.Ink) }
            }
            Draggable(position(ArenaHudTarget.LEADERBOARD), area, { onMove(ArenaHudTarget.LEADERBOARD, it) }) {
                HudPreviewPanel(
                    "LEADERBOARD\n1  Wyrm Player     9503\n2  Northwind       2819\n3  Orbit            418\n4  Meadow           389\n5  Drift            248",
                    with(density) { (250f * leaderboardScale).toDp() },
                    with(density) { (132f * leaderboardScale).toDp() },
                )
            }
            Draggable(position(ArenaHudTarget.STATS), area, { onMove(ArenaHudTarget.STATS, it) }) {
                HudPreviewPanel(
                    "STATS\nSCORE   9503\nKILLS      4\nRANK    8 / 46\nPING    64 ms\nFPS     61",
                    with(density) { (142f * statsScale).toDp() },
                    with(density) { (132f * statsScale).toDp() },
                )
            }
            Draggable(position(ArenaHudTarget.TEAM), area, { onMove(ArenaHudTarget.TEAM, it) }) {
                HudPreviewPanel(
                    "TEAM\n● Om Rajput       9503\n● Northwind       2819\n○ Meadow           389",
                    with(density) { 210f.toDp() },
                    with(density) { 98f.toDp() },
                )
            }
            Draggable(position(ArenaHudTarget.CHAT), area, { onMove(ArenaHudTarget.CHAT, it) }) {
                Box(
                    modifier = Modifier
                        .width(with(density) { 124f.toDp() })
                        .height(with(density) { 56f.toDp() })
                        .background(Wyrm.Card.copy(alpha = 0.94f), wyrmRounded(28.dp))
                        .border(1.5.dp, Wyrm.Ink.copy(alpha = 0.45f), wyrmRounded(28.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("CHAT", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, color = Wyrm.Ink) }
            }
        }
        EditorBar(
            caption = "Drag the arena UI into place",
            onReset = onReset,
            onSave = onSave,
            onCancel = onCancel,
            modifier = Modifier.align(Alignment.TopCenter),
            topInset = with(density) { safeInsets.top.toDp() },
        )
    }
}

@Composable
private fun HudPreviewPanel(label: String, width: Dp, height: Dp, opacity: Float = 1f) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer(alpha = opacity.coerceIn(0.05f, 1f))
            .background(Wyrm.Card.copy(alpha = 0.92f), wyrmRounded(14.dp))
            .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
            .padding(12.dp),
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Wyrm.Ink,
        )
    }
}

/**
 * One editor for the whole arena.  The three settings pages are only different
 * entry points; splitting the canvas made a player remember where every piece
 * was before they could arrange the complete match surface.
 */
@Composable
fun UnifiedArenaLayoutEditor(
    joystick: Offset,
    boost: Offset,
    zoom: Offset,
    showJoystick: Boolean,
    showBoost: Boolean,
    showZoom: Boolean,
    joystickSize: Float,
    boostSize: Float,
    zoomLength: Float,
    zoomVertical: Boolean,
    joystickOpacity: Float,
    boostOpacity: Float,
    zoomOpacity: Float,
    hotkeys: List<Hotkey>,
    keyScales: Map<Int, Float>,
    keyOpacities: Map<Int, Float>,
    hudPositions: Map<ArenaHudTarget, Offset>,
    minimapSize: Float,
    leaderboardFont: Int,
    statsFont: Int,
    statsScale: Float,
    statsOpacity: Float,
    chatScale: Float,
    chatOpacity: Float,
    onLeaderboardTap: () -> Unit,
    onMoveControl: (LayoutTarget, Offset) -> Unit,
    onMoveKey: (Int, Offset) -> Unit,
    onMoveHud: (ArenaHudTarget, Offset) -> Unit,
    onSettingChange: (String, Float) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    /** Play orientation (OM, 2026-10-01): the editor turns with it, and so does its layout. */
    portrait: Boolean = false,
    onToggleOrientation: (() -> Unit)? = null,
    /** Near Original: the original's map, board and stats are fixed, so not shown.
     *  The team roster and the chat window stay editable. */
    hudEditable: Boolean = true,
) {
    var canvas by remember { mutableStateOf(Offset.Zero) }
    var options by remember { mutableStateOf<LayoutOptions?>(null) }
    /* Editor chrome only. Not saved, not synced. Null until the player drags it. */
    var footerNorm by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    fun openOptions(
        key: String,
        title: String,
        primary: LayoutSlider? = null,
        secondary: LayoutSlider? = null,
        choice: LayoutChoice? = null,
    ) {
        options = LayoutOptions(key, title, listOfNotNull(primary, secondary), choice)
    }

    /* Team roster and chat window (OM, 2026-10-04): every slider and the two
       text colours, saved app side (TeamHudStore). */
    fun teamHudSlider(label: String, key: String) =
        LayoutSlider(label, TeamHudStore.value(key), TeamHudStore.range(key)) { onSettingChange("teamhud.$key", it) }
    fun teamHudColour(label: String, key: String) =
        LayoutColours(label, TeamHudStore.value(key).toInt()) { onSettingChange("teamhud.$key", it.toFloat()) }

    val minimapDp = with(density) { minimapSize.coerceIn(128f, 512f).toDp() }
    val leaderboardScale = 1f + leaderboardFont.coerceIn(0, 2) * 0.16f
    val statsPreviewScale = (1f + statsFont.coerceIn(0, 2) * 0.14f) * statsScale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvas = Offset(it.width.toFloat(), it.height.toFloat()) },
    ) {
        ArenaHint()
        if (canvas.x > 0f) {
            val area = SafeInsets().area(canvas)
            if (showJoystick) {
                Draggable(joystick, area, { onMoveControl(LayoutTarget.JOYSTICK, it) }, onLongPress = {
                    openOptions(
                        "joystick", "JOYSTICK",
                        LayoutSlider("SIZE", joystickSize, 0.65f..1.45f) { onSettingChange("controls.joystick_size", it) },
                        LayoutSlider("OPACITY", joystickOpacity, 0.05f..1f) { onSettingChange("layout.joystick_opacity", it) },
                    )
                }) { PaperJoystick(diameter = (112 * joystickSize).dp, opacity = joystickOpacity) }
            }
            if (showBoost) {
                Draggable(boost, area, { onMoveControl(LayoutTarget.BOOST, it) }, onLongPress = {
                    openOptions(
                        "boost", "BOOST",
                        LayoutSlider("SIZE", boostSize, 0.65f..1.45f) { onSettingChange("controls.boost_size", it) },
                        LayoutSlider("OPACITY", boostOpacity, 0.05f..1f) { onSettingChange("layout.boost_opacity", it) },
                    )
                }) { PaperBoostButton(diameter = (86 * boostSize).dp, opacity = boostOpacity) }
            }
            if (showZoom) {
                Draggable(zoom, area, { onMoveControl(LayoutTarget.ZOOM, it) }, onLongPress = {
                    openOptions(
                        "zoom", "ZOOM",
                        LayoutSlider("LENGTH", zoomLength, 0.65f..1.55f) { onSettingChange("controls.zoom_length", it) },
                        LayoutSlider("OPACITY", zoomOpacity, 0.05f..1f) { onSettingChange("layout.zoom_opacity", it) },
                        LayoutChoice("ORIENTATION", listOf("Horizontal", "Vertical"), if (zoomVertical) 1 else 0) {
                            onSettingChange("controls.zoom_orientation", it.toFloat())
                        },
                    )
                }) {
                    PaperZoomBar(
                        length = (190 * zoomLength).dp,
                        vertical = zoomVertical,
                        opacity = zoomOpacity,
                        value = 0.45f,
                    )
                }
            }
            hotkeys.filter { it.visible }.forEach { hotkey ->
                val keyScale = keyScales[hotkey.action] ?: 1f
                val keysOpacity = keyOpacities[hotkey.action] ?: 1f
                Draggable(Offset(hotkey.x, hotkey.y), area, { onMoveKey(hotkey.action, it) }, onLongPress = {
                    openOptions(
                        "key-${hotkey.action}", hotkey.name.uppercase(Locale.US),
                        LayoutSlider("SIZE", keyScale, 0.65f..1.60f) { onSettingChange("layout.key_${hotkey.action}_scale", it) },
                        LayoutSlider("OPACITY", keysOpacity, 0.05f..1f) { onSettingChange("layout.key_${hotkey.action}_opacity", it) },
                    )
                }) { PaperKey(label = hotkey.name, opacity = keysOpacity, scale = keyScale) }
            }
            fun hudPosition(target: ArenaHudTarget) = hudPositions[target] ?: target.fallback
            if (hudEditable) {
            Draggable(hudPosition(ArenaHudTarget.MINIMAP), area, { onMoveHud(ArenaHudTarget.MINIMAP, it) }, onLongPress = {
                openOptions(
                    "minimap", "MINIMAP",
                    LayoutSlider("SIZE", minimapSize, 128f..512f) { onSettingChange("general.minimap_size", it) },
                )
            }) {
                Box(
                    Modifier.size(minimapDp).background(Wyrm.Card.copy(alpha = 0.18f), CircleShape)
                        .border(3.dp, Wyrm.Ink.copy(alpha = 0.76f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("MAP", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, color = Wyrm.Ink) }
            }
            Draggable(hudPosition(ArenaHudTarget.LEADERBOARD), area, { onMoveHud(ArenaHudTarget.LEADERBOARD, it) }, onTap = onLeaderboardTap, onLongPress = {
                openOptions(
                    "leaderboard", "LEADERBOARD",
                    LayoutSlider("TEXT SIZE", leaderboardFont.toFloat(), 0f..2f) {
                        onSettingChange("general.lb_font", it.toInt().coerceIn(0, 2).toFloat())
                    },
                )
            }) {
                HudPreviewPanel("LEADERBOARD\n1  Wyrm Player     9503\n2  Northwind       2819\n3  Orbit            418\n4  Meadow           389\n5  Drift            248", with(density) { (250f * leaderboardScale).toDp() }, with(density) { (132f * leaderboardScale).toDp() })
            }
            Draggable(hudPosition(ArenaHudTarget.STATS), area, { onMoveHud(ArenaHudTarget.STATS, it) }, onLongPress = {
                options = LayoutOptions(
                    "stats", "STATS",
                    listOf(
                        LayoutSlider("SIZE", statsScale, 0.65f..1.60f) { onSettingChange("layout.stats_scale", it) },
                        LayoutSlider("OPACITY", statsOpacity, 0.05f..1f) { onSettingChange("layout.stats_opacity", it) },
                        // BACK (OM, 2026-10-05): the white plate only; the text keeps OPACITY.
                        teamHudSlider("BACK", "stats_panel"),
                    ),
                )
            }) {
                HudPreviewPanel("STATS\nSCORE   9503\nKILLS      4\nRANK    8 / 46\nPING    64 ms\nFPS     61", with(density) { (142f * statsPreviewScale).toDp() }, with(density) { (132f * statsPreviewScale).toDp() }, statsOpacity)
            }
            }
            // The roster and chat stay editable in Near Original. The engine draws
            // them over the AI arena; these are their hit boxes at the same size.
            val teamScale = TeamHudStore.value("team_scale")
            Draggable(hudPosition(ArenaHudTarget.TEAM), area, { onMoveHud(ArenaHudTarget.TEAM, it) }, onLongPress = {
                options = LayoutOptions(
                    "team", "TEAM",
                    listOf(
                        teamHudSlider("SIZE", "team_scale"),
                        teamHudSlider("OPACITY", "team_opacity"),
                        teamHudSlider("BACK", "team_panel"),
                        teamHudSlider("WIDTH", "team_width"),
                        teamHudSlider("HEIGHT", "team_height"),
                    ),
                    colours = listOf(
                        teamHudColour("PLAYER NAME AND SCORE", "team_name"),
                        teamHudColour("KEY NAME AND SERVER", "team_data"),
                    ),
                )
            }) {
                Box(
                    Modifier
                        .width(with(density) { (TeamHudStore.value("team_width") * teamScale).toDp() })
                        .height(with(density) { (TeamHudStore.value("team_height") * teamScale).toDp() })
                        .background(Wyrm.Card, wyrmRounded(14.dp)),
                )
            }
            Draggable(hudPosition(ArenaHudTarget.CHAT), area, { onMoveHud(ArenaHudTarget.CHAT, it) }, onLongPress = {
                options = LayoutOptions(
                    "chat", "CHAT",
                    listOf(
                        LayoutSlider("SIZE", chatScale, 0.65f..1.60f) { onSettingChange("layout.chat_scale", it) },
                        LayoutSlider("OPACITY", chatOpacity, 0.05f..1f) { onSettingChange("layout.chat_opacity", it) },
                        teamHudSlider("BACK", "chat_panel"),
                        teamHudSlider("WIDTH", "chat_width"),
                        teamHudSlider("HEIGHT", "chat_height"),
                    ),
                    colours = listOf(
                        teamHudColour("PLAYER NAME", "chat_name"),
                        teamHudColour("MESSAGES", "chat_text"),
                    ),
                )
            }) {
                Box(
                    Modifier
                        .width(with(density) { (TeamHudStore.value("chat_width") * chatScale).toDp() })
                        .height(with(density) { (TeamHudStore.value("chat_height") * chatScale).toDp() })
                        .background(Wyrm.Card, wyrmRounded(14.dp)),
                )
            }
        }
        EditorFooter(onReset, onSave, onCancel, canvas, footerNorm, { footerNorm = it }, portrait, onToggleOrientation)
        options?.let { EditorOptionsPopup(it) { options = null } }
    }
}

private data class LayoutSlider(
    val label: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val onChange: (Float) -> Unit,
)

private data class LayoutChoice(
    val label: String,
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

private data class LayoutOptions(
    val key: String,
    val title: String,
    val sliders: List<LayoutSlider>,
    val choice: LayoutChoice? = null,
    val colours: List<LayoutColours> = emptyList(),
)

/** A row of colour swatches; [selected] indexes TeamHudStore.COLOUR_NAMES. */
private data class LayoutColours(
    val label: String,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

@Composable
private fun EditorOptionsPopup(options: LayoutOptions, onDismiss: () -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.width(300.dp).heightIn(max = 340.dp).background(Wyrm.Card, wyrmRounded(20.dp))
                .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp))
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(options.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.4.sp, color = Wyrm.Ink)
            if (options.sliders.isEmpty()) {
                Text("POSITION ONLY", fontFamily = Wyrm.Body, fontSize = 11.sp, letterSpacing = 1.sp, color = Wyrm.Quiet)
            }
            options.sliders.forEach { option ->
                var value by remember(options.key, option.label) { mutableStateOf(option.value) }
                Text(option.label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = Wyrm.Quiet)
                LiquidSlider(
                    value = value,
                    onValueChange = { next -> value = next; option.onChange(next) },
                    valueRange = option.range,
                )
            }
            options.choice?.let { choice ->
                var selected by remember(options.key, choice.label) { mutableStateOf(choice.selected) }
                Text(choice.label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = Wyrm.Quiet)
                PaperSegmented(
                    options = choice.options,
                    selected = selected,
                    onSelect = { next -> selected = next; choice.onSelect(next) },
                )
            }
            options.colours.forEach { row ->
                var selected by remember(options.key, row.label) { mutableStateOf(row.selected) }
                Text("${row.label} · ${TeamHudStore.COLOUR_NAMES.getOrElse(selected) { "Theme" }.uppercase()}",
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = Wyrm.Quiet)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TeamHudStore.COLOUR_SWATCHES.forEachIndexed { index, swatch ->
                        val fill = if (swatch == Color.Unspecified) Wyrm.Ink else swatch
                        Box(
                            Modifier.size(24.dp)
                                .border(if (index == selected) 2.dp else 1.dp, if (index == selected) Wyrm.Ink else Wyrm.Rule, CircleShape)
                                .padding(3.dp).clip(CircleShape).background(fill)
                                .clickable { selected = index; row.onSelect(index) },
                        )
                    }
                }
            }
            Text("DONE", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Wyrm.Ink, modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(8.dp))
        }
    }
}

/** Top-left of the editor bar. [norm] is a fraction of the canvas; null is the bottom centre. */
private fun footerTopLeft(canvas: Offset, bar: Offset, norm: Offset?, margin: Float, bottomInset: Float): Offset {
    if (canvas.x <= 1f) return Offset(0f, 100000f)
    if (bar.x <= 1f) return Offset(0f, canvas.y)
    val rawCx = if (norm == null) canvas.x / 2f else norm.x * canvas.x
    val rawCy = if (norm == null) canvas.y - bottomInset - bar.y / 2f else norm.y * canvas.y
    val maxLeft = (canvas.x - bar.x - margin).coerceAtLeast(margin)
    val maxTop = (canvas.y - bar.y - margin).coerceAtLeast(margin)
    return Offset(
        (rawCx - bar.x / 2f).coerceIn(margin, maxLeft),
        (rawCy - bar.y / 2f).coerceIn(margin, maxTop),
    )
}

@Composable
private fun EditorFooter(
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    canvas: Offset,
    norm: Offset?,
    onNorm: (Offset) -> Unit,
    portrait: Boolean = false,
    onToggleOrientation: (() -> Unit)? = null,
) {
    // Upright the bar is too narrow for the hint and the actions on one line:
    // the hint goes above, in its own small pill. The grip moves the whole bar
    // (this session only) so a control can sit where the bar was.
    val density = LocalDensity.current
    var bar by remember { mutableStateOf(Offset.Zero) }
    val normState = rememberUpdatedState(norm)
    val canvasState = rememberUpdatedState(canvas)
    val barState = rememberUpdatedState(bar)
    val report = rememberUpdatedState(onNorm)
    val margin = with(density) { 8.dp.toPx() }
    val bottomInset = with(density) { 14.dp.toPx() }
    val topLeft = footerTopLeft(canvas, bar, norm, margin, bottomInset)
    BoxWithConstraints(
        Modifier
            .offset(x = with(density) { topLeft.x.toDp() }, y = with(density) { topLeft.y.toDp() })
            .onSizeChanged { bar = Offset(it.width.toFloat(), it.height.toFloat()) },
    ) {
        val narrow = maxWidth < 560.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (narrow) {
                // Upright: the knob sits at the bar's left end, below this pill.
                Column(
                    Modifier.padding(bottom = 6.dp).clip(wyrmRounded(14.dp))
                        .background(Wyrm.Card.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EditorBarHint(LucideR.drawable.lucide_ic_arrow_down_left)
                }
            }
            Row(
                Modifier.clip(wyrmRounded(999.dp)).background(Wyrm.Card.copy(alpha = 0.96f))
                    .border(1.dp, Wyrm.Rule, wyrmRounded(999.dp)).padding(start = 6.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(36.dp).pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val c = canvasState.value
                            val b = barState.value
                            if (c.x <= 1f || b.x <= 1f) return@detectDragGestures
                            val m = 8.dp.toPx()
                            val inset = 14.dp.toPx()
                            val placed = footerTopLeft(c, b, normState.value, m, inset)
                            val nextCx = (placed.x + b.x / 2f + drag.x).coerceIn(m + b.x / 2f, (c.x - m - b.x / 2f).coerceAtLeast(m + b.x / 2f))
                            val nextCy = (placed.y + b.y / 2f + drag.y).coerceIn(m + b.y / 2f, (c.y - m - b.y / 2f).coerceAtLeast(m + b.y / 2f))
                            report.value(Offset(nextCx / c.x, nextCy / c.y))
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) {
                            Box(Modifier.width(16.dp).height(2.dp).clip(wyrmRounded(1.dp)).background(Wyrm.Quiet))
                        }
                    }
                }
                if (!narrow) {
                    Column { EditorBarHint(LucideR.drawable.lucide_ic_arrow_left) }
                }
                // Turns the phone (and swaps to that orientation's layout).
                onToggleOrientation?.let { toggle ->
                    EditorFooterAction(if (portrait) "LANDSCAPE" else "PORTRAIT", Wyrm.Ink, toggle)
                }
                EditorFooterAction("CANCEL", Wyrm.Quiet, onCancel)
                EditorFooterAction("RESET", Wyrm.Quiet, onReset)
                EditorFooterAction("SAVE", Wyrm.OnInk, onSave, filled = true)
            }
        }
    }
}

/**
 * The bar's hint (OM, 2026-10-05): "hold any object" moved up, and under it an
 * arrow pointing at the grip knob with "drag this knob to move this bar".
 */
@Composable
private fun EditorBarHint(arrow: Int) {
    Text("HOLD ANY OBJECT FOR MORE OPTIONS", fontFamily = Wyrm.Body, fontSize = 9.sp, letterSpacing = 0.6.sp, color = Wyrm.Quiet)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(painterResource(arrow), contentDescription = null, tint = Wyrm.Ink, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text("DRAG THIS KNOB TO MOVE THIS BAR", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold,
            fontSize = 8.sp, letterSpacing = 0.6.sp, color = Wyrm.Ink.copy(alpha = 0.8f))
    }
}

@Composable
private fun EditorFooterAction(label: String, color: Color, onClick: () -> Unit, filled: Boolean = false) {
    Text(
        label,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.sp,
        color = color,
        modifier = Modifier.clip(wyrmRounded(999.dp)).then(
            if (filled) Modifier.background(Wyrm.Ink) else Modifier
        ).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}


/**
 * Adjust arena background size (OM, 2026-10-01). The AI arena in landscape
 * with only its real minimap and leaderboard (the engine's bare editor: no
 * controls, no buttons, assist off), and one slider along the bottom. The engine redraws the
 * floor at the new size every frame, so what the player sees is what they get.
 * The floor is the one chosen in Skin › Arena background.
 */
@Composable
fun ArenaBackgroundSizeEditor(
    scale: Float,
    minimap: Offset,
    leaderboard: Offset,
    minimapSize: Float,
    leaderboardFont: Int,
    safeInsets: SafeInsets,
    onScale: (Float) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        // The engine draws the real minimap and leaderboard (bare editor); nothing on top.
        ArenaHint()
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { safeInsets.bottom.toDp() } + 14.dp)
                .width(460.dp)
                .clip(wyrmRounded(24.dp))
                .background(Wyrm.Card.copy(alpha = 0.96f))
                .border(1.dp, Wyrm.Rule, wyrmRounded(24.dp))
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ARENA BACKGROUND SIZE", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    letterSpacing = 1.2.sp, color = Wyrm.Quiet, modifier = Modifier.weight(1f))
                Text(bgScaleLabel(scale), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
            }
            WyrmSlider(
                value = bgSliderOf(scale),
                minimum = 0f,
                maximum = 1f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onChange = { onScale(bgScaleAt(it)) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Smaller looks further away", fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = Wyrm.Quiet,
                    modifier = Modifier.weight(1f))
                EditorFooterAction("CANCEL", Wyrm.Quiet, onCancel)
                EditorFooterAction("RESET", Wyrm.Quiet, onReset)
                EditorFooterAction("SAVE", Wyrm.OnInk, onSave, filled = true)
            }
        }
    }
}

/**
 * Snake look (OM, 2026-10-05). The same bare arena as the background-size
 * editor, with the real snake. Normal and Assist are the engine's two modes;
 * every change is written live so the snake redraws before Done.
 */
@Composable
fun SnakeLookPreviewEditor(
    settings: List<Setting>,
    assist: Boolean,
    safeInsets: SafeInsets,
    onAssist: (Boolean) -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val group = if (assist) "assist" else "normal"
    val render = settings.named("$group.render_mode")
    val spine = settings.named("$group.spine")
    val spineWidth = settings.named("$group.spine_width")
    val shadow = settings.named("$group.snake_shadow")
    val hide = settings.named("assist.hide_cosmetics")
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        ArenaHint()
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { safeInsets.bottom.toDp() } + 14.dp)
                .width(460.dp)
                .clip(wyrmRounded(24.dp))
                .background(Wyrm.Card.copy(alpha = 0.96f))
                .border(1.dp, Wyrm.Rule, wyrmRounded(24.dp))
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
        ) {
            Text(
                "SNAKE LOOK",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = Wyrm.Quiet,
            )
            PaperSegmented(
                options = listOf("Normal", "Assist"),
                selected = if (assist) 1 else 0,
                onSelect = { onAssist(it == 1) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            PaperSegmented(
                options = listOf("Texture", "Solid", "Flat", "Skinless"),
                selected = (render?.index ?: 0).coerceIn(0, 3),
                onSelect = { index -> render?.let { onChange(it, listOf(index.toFloat())) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SettingsBoolRow(
                title = "Spine",
                detail = "A thin white line down the middle of every snake.",
                on = spine?.enabled == true,
                first = true,
                onToggle = { on -> spine?.let { onChange(it, listOf(if (on) 1f else 0f)) } },
            )
            if (spine?.enabled == true && spineWidth != null) {
                SettingsSliderRow(
                    title = "Spine width",
                    valueText = spineWidthLabel(spineWidth.number),
                    detail = "From a thin thread to as wide as the snake",
                    value = spineWidth.number.coerceIn(0f, 1f),
                    range = 0f..1f,
                    steps = 0,
                    first = false,
                    onChange = { onChange(spineWidth, listOf(it)) },
                )
            }
            SettingsBoolRow(
                title = "Snake shadow",
                detail = "The soft shadow the original app draws under every snake.",
                on = shadow?.enabled == true,
                first = false,
                onToggle = { on -> shadow?.let { onChange(it, listOf(if (on) 1f else 0f)) } },
            )
            if (assist) {
                SettingsBoolRow(
                    title = "Hide own tag and accessories",
                    detail = "While assist is on, your tag, accessory and Wyrm look are hidden.",
                    on = hide?.enabled == true,
                    first = false,
                    onToggle = { on -> hide?.let { onChange(it, listOf(if (on) 1f else 0f)) } },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorFooterAction("CANCEL", Wyrm.Quiet, onCancel)
                EditorFooterAction("DONE", Wyrm.OnInk, onDone, filled = true)
            }
        }
    }
}

enum class LayoutTarget { JOYSTICK, BOOST, ZOOM }

enum class ArenaHudTarget(val prefix: String, val fallback: Offset) {
    MINIMAP("hud.minimap", Offset(0.095f, 0.205f)),
    LEADERBOARD("hud.leaderboard", Offset(0.905f, 0.155f)),
    STATS("hud.stats", Offset(0.945f, 0.530f)),
    TEAM("hud.team", Offset(0.095f, 0.610f)),
    CHAT("hud.chat", Offset(0.790f, 0.075f)),
}

/**
 * The window insets, kept as pixels because that is what the layout maths and
 * the engine both work in.
 */
data class SafeInsets(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f,
) {
    /** The rectangle a control may live in: x, y, width, height, in pixels. */
    fun area(size: Offset): FloatArray = floatArrayOf(
        left,
        top,
        (size.x - left - right).coerceAtLeast(1f),
        (size.y - top - bottom).coerceAtLeast(1f),
    )
}

/**
 * One draggable piece.
 *
 * The child is centred on the position and moved by the drag, and the result
 * is clamped so nothing can be pushed off the edge of the screen and lost.
 *
 * The gesture reads the live position rather than the one it was composed
 * with. A `detectDragGestures` block outlives every recomposition it triggers,
 * so a captured position goes stale the instant the first drag event is
 * applied — and since each event carries only its own small delta, adding that
 * delta to a stale origin puts the control straight back where it started.
 * That is the difference between something that jitters under your thumb and
 * something that follows it.
 */
@Composable
private fun Draggable(
    position: Offset,
    area: FloatArray,
    onMove: (Offset) -> Unit,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var childSize by remember { mutableStateOf(Offset.Zero) }

    val safePosition = sanitizeLayoutPosition(position)
    val centreX = area[0] + safePosition.x * area[2]
    val centreY = area[1] + safePosition.y * area[3]
    val centre by rememberUpdatedState(Offset(centreX, centreY))
    val bounds by rememberUpdatedState(area)
    val measuredChild by rememberUpdatedState(childSize)
    val move by rememberUpdatedState(onMove)
    val moreOptions by rememberUpdatedState(onLongPress)
    val tapAction by rememberUpdatedState(onTap)
    var dragCentre by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { (centreX - childSize.x / 2f).toDp() },
                y = with(density) { (centreY - childSize.y / 2f).toDp() },
            )
            .onSizeChanged { childSize = Offset(it.width.toFloat(), it.height.toFloat()) }
            .graphicsLayer(alpha = 0.01f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapAction?.invoke() },
                    onLongPress = { moreOptions?.invoke() },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragCentre = centre },
                    onDrag = { change, dragged ->
                        change.consume()
                        dragCentre = moveLayoutCentre(
                            centre = dragCentre,
                            delta = dragged,
                            area = bounds,
                            childSize = measuredChild,
                        )
                        move(normalizeLayoutCentre(dragCentre, bounds))
                    },
                )
            },
    ) {
        content()
    }
}

/** Rejects malformed restored values before Compose can turn NaN into (0, 0). */
internal fun sanitizeLayoutPosition(
    position: Offset,
    fallback: Offset = Offset(0.5f, 0.7f),
): Offset = Offset(
    x = if (position.x.isFinite()) position.x.coerceIn(0f, 1f) else fallback.x,
    y = if (position.y.isFinite()) position.y.coerceIn(0f, 1f) else fallback.y,
)

/** Accumulates every gesture delta locally and keeps the complete control visible. */
internal fun moveLayoutCentre(
    centre: Offset,
    delta: Offset,
    area: FloatArray,
    childSize: Offset,
): Offset {
    val left = area.getOrElse(0) { 0f }
    val top = area.getOrElse(1) { 0f }
    val width = area.getOrElse(2) { 1f }.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = area.getOrElse(3) { 1f }.takeIf { it.isFinite() && it > 0f } ?: 1f
    val halfWidth = ((childSize.x.takeIf { it.isFinite() } ?: 0f) / 2f)
        .coerceIn(0f, width / 2f)
    val halfHeight = ((childSize.y.takeIf { it.isFinite() } ?: 0f) / 2f)
        .coerceIn(0f, height / 2f)
    val baseX = centre.x.takeIf { it.isFinite() } ?: left + width / 2f
    val baseY = centre.y.takeIf { it.isFinite() } ?: top + height / 2f
    val dx = delta.x.takeIf { it.isFinite() } ?: 0f
    val dy = delta.y.takeIf { it.isFinite() } ?: 0f
    return Offset(
        x = (baseX + dx).coerceIn(left + halfWidth, left + width - halfWidth),
        y = (baseY + dy).coerceIn(top + halfHeight, top + height - halfHeight),
    )
}

/** The preview uses the exact normalized centre saved by the landscape editor. */
internal fun previewItemTopLeft(position: Offset, container: Offset, child: Offset): Offset {
    val safe = sanitizeLayoutPosition(position)
    val maxX = (container.x - child.x).coerceAtLeast(0f)
    val maxY = (container.y - child.y).coerceAtLeast(0f)
    return Offset(
        x = (safe.x * container.x - child.x / 2f).coerceIn(0f, maxX),
        y = (safe.y * container.y - child.y / 2f).coerceIn(0f, maxY),
    )
}

internal fun normalizeLayoutCentre(centre: Offset, area: FloatArray): Offset {
    val left = area.getOrElse(0) { 0f }
    val top = area.getOrElse(1) { 0f }
    val width = area.getOrElse(2) { 1f }.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = area.getOrElse(3) { 1f }.takeIf { it.isFinite() && it > 0f } ?: 1f
    return sanitizeLayoutPosition(
        Offset((centre.x - left) / width, (centre.y - top) / height)
    )
}

/**
 * A suggestion of the arena underneath.
 *
 * Glass on black is invisible, and a layout arranged against a black screen is
 * arranged against something the player will never see. This is not the arena
 * — it is enough of one to judge contrast by.
 */
@Composable
private fun ArenaHint() {
    Box(modifier = Modifier.fillMaxSize())
}

@Composable
private fun EditorBar(
    caption: String,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp,
) {
    Row(
        modifier = modifier
            .padding(top = topInset + 14.dp)
            .clip(wyrmRounded(999.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(999.dp))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = caption,
            fontFamily = Wyrm.Body,
            fontSize = 12.sp,
            color = Wyrm.Ink,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = "CANCEL",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = Wyrm.Quiet,
            modifier = Modifier
                .clip(wyrmRounded(999.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Text(
            text = "RESET",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = Wyrm.Quiet,
            modifier = Modifier
                .clip(wyrmRounded(999.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(40.dp)
                .clip(wyrmRounded(999.dp))
                .background(Wyrm.Ink)
                .clickable(onClick = onSave),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SAVE",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = Wyrm.OnInk,
            )
        }
    }
}
