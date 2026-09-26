package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import java.util.Locale

/** Settings snapshots are machine-readable and must never inherit decimal commas. */
internal fun formatSettingNumber(value: Float): String =
    String.format(Locale.US, "%.4f", value)

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
) {
    var canvas by remember { mutableStateOf(Offset.Zero) }
    var options by remember { mutableStateOf<LayoutOptions?>(null) }
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
                openOptions(
                    "stats", "STATS",
                    LayoutSlider("SIZE", statsScale, 0.65f..1.60f) { onSettingChange("layout.stats_scale", it) },
                    LayoutSlider("OPACITY", statsOpacity, 0.05f..1f) { onSettingChange("layout.stats_opacity", it) },
                )
            }) {
                HudPreviewPanel("STATS\nSCORE   9503\nKILLS      4\nRANK    8 / 46\nPING    64 ms\nFPS     61", with(density) { (142f * statsPreviewScale).toDp() }, with(density) { (132f * statsPreviewScale).toDp() }, statsOpacity)
            }
            Draggable(hudPosition(ArenaHudTarget.TEAM), area, { onMoveHud(ArenaHudTarget.TEAM, it) }, onLongPress = {
                openOptions("team", "TEAM")
            }) {
                HudPreviewPanel("TEAM\n● Om Rajput       9503\n● Northwind       2819\n○ Meadow           389", with(density) { 210f.toDp() }, with(density) { 98f.toDp() })
            }
            Draggable(hudPosition(ArenaHudTarget.CHAT), area, { onMoveHud(ArenaHudTarget.CHAT, it) }, onLongPress = {
                openOptions(
                    "chat", "CHAT",
                    LayoutSlider("SIZE", chatScale, 0.65f..1.60f) { onSettingChange("layout.chat_scale", it) },
                    LayoutSlider("OPACITY", chatOpacity, 0.05f..1f) { onSettingChange("layout.chat_opacity", it) },
                )
            }) {
                Box(
                    Modifier.width(with(density) { (124f * chatScale).toDp() }).height(with(density) { (56f * chatScale).toDp() })
                        .background(Wyrm.Card.copy(alpha = 0.94f * chatOpacity), wyrmRounded(28.dp))
                        .border(1.5.dp, Wyrm.Ink.copy(alpha = 0.45f), wyrmRounded(28.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("CHAT", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, color = Wyrm.Ink) }
            }
        }
        EditorFooter(onReset, onSave, onCancel, Modifier.align(Alignment.BottomCenter))
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
)

@Composable
private fun EditorOptionsPopup(options: LayoutOptions, onDismiss: () -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.width(300.dp).background(Wyrm.Card, wyrmRounded(20.dp))
                .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp)).padding(18.dp),
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
            Text("DONE", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Wyrm.Ink, modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(8.dp))
        }
    }
}

@Composable
private fun EditorFooter(onReset: () -> Unit, onSave: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    Row(
        modifier.padding(bottom = 14.dp).clip(wyrmRounded(999.dp)).background(Wyrm.Card.copy(alpha = 0.96f))
            .border(1.dp, Wyrm.Rule, wyrmRounded(999.dp)).padding(start = 14.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("HOLD ANY OBJECT FOR MORE OPTIONS", fontFamily = Wyrm.Body, fontSize = 9.sp, letterSpacing = 0.6.sp, color = Wyrm.Quiet)
        EditorFooterAction("CANCEL", Wyrm.Quiet, onCancel)
        EditorFooterAction("RESET", Wyrm.Quiet, onReset)
        EditorFooterAction("SAVE", Wyrm.OnInk, onSave, filled = true)
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
