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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.shadow
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal data class ArrowOption(val label: String, val points: List<Offset>)

enum class ControlsTab { SNAKE, ON_SCREEN_BUTTONS }

/* Mirrored in `mobile_controls.c`: the preview and the arena are deliberately
 * fed the same normalized silhouettes rather than two artistic guesses. */
internal val ArrowOptions = listOf(
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
    // slither's own arrow (Near Original, 2026-10-02), from the original's 64 px shape.
    ArrowOption(
        "ORIGINAL",
        listOf(
            Offset(-0.56f, -0.3155f), Offset(-0.56f, 0.3155f), Offset(0f, 0.2227f),
            Offset(0f, 0.7423f), Offset(0.56f, 0f), Offset(0f, -0.7423f),
            Offset(0f, -0.2227f),
        ),
    ),
)

/** Near Original: shown faded and not touchable (the original's own values apply). */
private fun Modifier.nearOriginalLocked(locked: Boolean): Modifier =
    if (!locked) this else this.alpha(0.38f).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    .changes.forEach { it.consume() }
            }
        }
    }

internal fun arrowOptionLabel(index: Int): String =
    ArrowOptions.getOrNull(index)?.label?.replace('\n', ' ')?.lowercase()
        ?.replaceFirstChar { it.uppercase() } ?: "Arrow ${index + 1}"

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
    /** Play orientation (OM, 2026-10-01): the lobby, the match and the editor upright. */
    portraitPlay: Boolean = false,
    onPortraitPlay: (Boolean) -> Unit = {},
) {
    val steeringSetting = settings.named("controls.joystick_mode")
    val steering = steeringSetting?.index ?: 0
    // Upright play steers with the arrow only (OM, 2026-10-02; engine
    // mobile_controls_steering_mode); the sideways choice below is kept.
    val arrowSteering = portraitPlay || steering == 2
    // Home › Near Original (OM, 2026-10-02): slither's own joystick, boost and
    // arrow; only the arrow's size stays the player's. Nothing stored changes.
    val nearOriginal = NearOriginalStore.on
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
    var zoomOpen by remember { mutableStateOf(SettingsFocus.wants(zoomRows.map { it.id })) }
    var behaviourOpen by remember { mutableStateOf(false) }
    var arrowPickerOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.SideEffect { AdjustPreview.arrowSteering = arrowSteering }

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
                arrowSkin = ArrowSkinStore.skin,
                arrowBrightness = ArrowSkinStore.brightness,
                portrait = portraitPlay,
            )
        }

        SettingsSectionLabel("Play orientation")
        SettingsCard {
            Box(Modifier.settingAnchor("app.play-orientation")) {
                SettingsEnumBlock(
                    title = "Hold the phone",
                    detail = "Portrait turns the lobby, the match and the layout editor upright. Each way keeps its own layout.",
                    options = listOf("Landscape", "Portrait"),
                    selected = if (portraitPlay) 1 else 0,
                    first = true,
                    onSelect = { onPortraitPlay(it == 1) },
                )
            }
        }

        SettingsSectionLabel("Basic · steering")
        SettingsCard {
            if (!portraitPlay) Box(Modifier.settingAnchor("controls.joystick_mode")) { SettingsEnumBlock(
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
            ) }
            if (!arrowSteering && steeringSetting != null && !nearOriginal) {
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
            // Upright there is no left or right hand (OM, 2026-10-02): the row
            // hides; the stored choice stays for sideways play.
            handedness?.takeIf { !portraitPlay }?.let { setting ->
                Box(Modifier.settingAnchor(setting.id)) { SettingsEnumBlock(
                    title = setting.label,
                    detail = setting.hint,
                    options = listOf("Left", "Right"),
                    selected = setting.index.coerceIn(0, 1),
                    first = false,
                    onSelect = { onChange(setting, listOf(it.toFloat())) },
                ) }
            }
            boostMode?.let { setting ->
                Box(Modifier.settingAnchor(setting.id).nearOriginalLocked(nearOriginal)) { SettingsEnumBlock(
                    title = "Boost",
                    detail = setting.hint,
                    options = setting.options,
                    selected = setting.index.coerceIn(0, (setting.options.size - 1).coerceAtLeast(0)),
                    first = portraitPlay,
                    onSelect = { onChange(setting, listOf(it.toFloat())) },
                ) }
            }
        }

        if (portraitPlay) SettingsCaption("Upright you always steer with the arrow: the joystick is for sideways play, and your sideways choice is kept. No left or right hand: your first finger steers, a second finger boosts.")

        if (nearOriginal) SettingsCaption("Near Original is on: slither's own joystick, boost button and arrow, at their original places. Only the arrow's size is yours. Turn it off on Home to bring your own back.")

        SettingsSectionLabel("Basic · size")
        Box(Modifier.nearOriginalLocked(nearOriginal)) { SettingsCard {
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
        } }

        if (arrowSteering) {
            SettingsSectionLabel("Basic · arrow")
            SettingsCard {
                // The picker replaces the style row; colour only tints the drawn arrows.
                if (!nearOriginal) Box(Modifier.settingAnchor("arrow.style")) {
                    ArrowStyleRow(arrowStyleSetting, arrowColor, first = true) { arrowPickerOpen = true }
                }
                // Customise arrow movement (OM, 2026-10-05): on, the start distance
                // and lag below are the player's; off, the arrow moves exactly like
                // slither's and they fold away. Near Original always moves like slither's.
                if (!nearOriginal) SettingsBoolRow(
                    title = "Customise arrow movement",
                    detail = if (PlayFeelStore.customArrow) "Your own start distance and lag."
                    else "Off: the arrow moves exactly like slither's, from the start distance to the drift.",
                    on = PlayFeelStore.customArrow,
                    first = false,
                    onToggle = { PlayFeelStore.applyCustomArrow(it) },
                )
                arrowRows.filter { it.id != "arrow.style" && it.id != "arrow.color" }
                    .filter { !nearOriginal || it.id == "arrow.size" }
                    .forEach { setting ->
                        val movement = setting.id == "arrow.separation" || setting.id == "arrow.smoothness"
                        AnimatedVisibility(
                            visible = !movement || PlayFeelStore.customArrow,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            SettingTypedRow(setting = setting, first = nearOriginal, onChange = onChange)
                        }
                    }
                if (!nearOriginal) androidx.compose.runtime.CompositionLocalProvider(LocalAdjustSubject provides AdjustSubject.ARROW) {
                    Box(Modifier.settingAnchor("app.arrow-brightness")) { SettingsSliderRow(
                        title = "Brightness",
                        valueText = "${(ArrowSkinStore.brightness * 100).roundToInt()}%",
                        detail = "",
                        value = ArrowSkinStore.brightness,
                        range = 0.2f..1f,
                        steps = 0,
                        first = false,
                        onChange = { ArrowSkinStore.updateBrightness(it) },
                    ) }
                }
                if (ArrowSkinStore.skin < 0 && !nearOriginal) {
                    arrowRows.firstOrNull { it.id == "arrow.color" }?.let { setting ->
                        SettingTypedRow(setting = setting, first = false, onChange = onChange)
                    }
                }
            }
        }
        if (arrowPickerOpen) {
            ArrowPickerSheet(
                styleSetting = arrowStyleSetting,
                colour = arrowColor,
                onPickStyle = { index -> arrowStyleSetting?.let { onChange(it, listOf(index.toFloat())) } },
                onClose = { arrowPickerOpen = false },
            )
        }

        if (zoomRows.isNotEmpty()) {
            AdvancedFold(label = "Advanced · zoom bar", open = zoomOpen, onToggle = { zoomOpen = !zoomOpen })
            if (zoomOpen) {
                SettingsCard {
                    zoomRows.forEachIndexed { index, setting ->
                        SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
                    }
                    // The zoom bar's style (OM, 2026-10-05): the slider, or a spring
                    // whose knob rests in the middle and springs back.
                    SettingsHairline()
                    Column(Modifier.padding(14.dp).settingAnchor("app.zoom-style")) {
                        // The bar itself, so Slider and Spring can be felt here (OM, 2026-10-05).
                        ZoomBarActionPreview(vertical = zoomVertical, springStyle = PlayFeelStore.zoomSpring)
                        Spacer(Modifier.height(12.dp))
                        Text("Zoom bar style", fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                        Spacer(Modifier.height(8.dp))
                        PaperSegmented(
                            options = listOf("Slider", "Spring"),
                            selected = if (PlayFeelStore.zoomSpring) 1 else 0,
                            onSelect = { PlayFeelStore.applyZoomSpring(it == 1) },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (PlayFeelStore.zoomSpring) "Push the knob toward + to zoom in, toward - to zoom out; let go and it springs back to the middle."
                            else "Slide to the zoom you want; it stays there.",
                            fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet,
                        )
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

/** Slider stays where you leave it. Spring returns to the middle, as in a match. */
@Composable
private fun ZoomBarActionPreview(vertical: Boolean, springStyle: Boolean) {
    val length = if (vertical) 156.dp else 220.dp
    val thickness = 26.dp
    var held by remember { mutableFloatStateOf(0.45f) }
    var pull by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Same return as the arena bar: each frame multiplies the pull by 0.72.
    LaunchedEffect(dragging, springStyle) {
        if (!springStyle || dragging) return@LaunchedEffect
        while (abs(pull) > 0.01f) {
            pull *= 0.72f
            delay(16)
        }
        pull = 0f
    }
    val shown = if (springStyle) (0.5f + pull * 0.5f).coerceIn(0f, 1f) else held
    Column {
        Text("Preview", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Wyrm.Well, wyrmRounded(12.dp))
                .border(1.dp, Wyrm.Rule, wyrmRounded(12.dp))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .then(if (vertical) Modifier.width(48.dp).height(length) else Modifier.width(length).height(48.dp))
                    .pointerInput(vertical, springStyle) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            dragging = true
                            fun apply(x: Float, y: Float) {
                                val span = if (vertical) size.height.toFloat() else size.width.toFloat()
                                if (span <= 1f) return
                                val pos = if (vertical) y else x
                                val t = if (vertical) 1f - pos / span else pos / span
                                if (springStyle) pull = ((t - 0.5f) * 2f).coerceIn(-1f, 1f)
                                else held = t.coerceIn(0f, 1f)
                            }
                            apply(down.position.x, down.position.y)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    apply(change.position.x, change.position.y)
                                }
                            } finally {
                                dragging = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    Modifier
                        .then(if (vertical) Modifier.width(thickness).height(length) else Modifier.width(length).height(thickness))
                        .clip(wyrmRounded(999.dp))
                        .background(Wyrm.Card)
                        .border(1.dp, Wyrm.Ink, wyrmRounded(999.dp)),
                ) {
                    val along = if (vertical) 1f - shown else shown
                    val travel = (if (vertical) size.height else size.width) - size.let {
                        if (vertical) it.width else it.height
                    }
                    val knobAt = (if (vertical) size.width else size.height) / 2f + travel * along
                    val mid = (if (vertical) size.height else size.width) / 2f
                    val cross = if (vertical) size.width else size.height
                    if (springStyle) {
                        val from = min(mid, knobAt)
                        val span = abs(knobAt - mid).coerceAtLeast(1f)
                        if (vertical) {
                            drawRect(Wyrm.Track, topLeft = Offset(0f, from), size = androidx.compose.ui.geometry.Size(cross, span))
                        } else {
                            drawRect(Wyrm.Track, topLeft = Offset(from, 0f), size = androidx.compose.ui.geometry.Size(span, cross))
                        }
                        val mark = cross * 0.22f
                        val inset = cross * 1.1f
                        if (vertical) {
                            val x = size.width / 2f
                            val plus = inset
                            val minus = size.height - inset
                            drawLine(Wyrm.Ink, Offset(x - mark, plus), Offset(x + mark, plus), 2.5f)
                            drawLine(Wyrm.Ink, Offset(x, plus - mark), Offset(x, plus + mark), 2.5f)
                            drawLine(Wyrm.Ink, Offset(x - mark, minus), Offset(x + mark, minus), 2.5f)
                        } else {
                            val y = size.height / 2f
                            val minus = inset
                            val plus = size.width - inset
                            drawLine(Wyrm.Ink, Offset(minus - mark, y), Offset(minus + mark, y), 2.5f)
                            drawLine(Wyrm.Ink, Offset(plus - mark, y), Offset(plus + mark, y), 2.5f)
                            drawLine(Wyrm.Ink, Offset(plus, y - mark), Offset(plus, y + mark), 2.5f)
                        }
                    } else if (vertical) {
                        drawRect(
                            Wyrm.Track,
                            topLeft = Offset(0f, knobAt),
                            size = androidx.compose.ui.geometry.Size(cross, size.height - knobAt),
                        )
                    } else {
                        drawRect(Wyrm.Track, size = androidx.compose.ui.geometry.Size(knobAt, cross))
                    }
                    val knobR = 9.dp.toPx()
                    val center = if (vertical) Offset(size.width / 2f, knobAt) else Offset(knobAt, size.height / 2f)
                    drawCircle(Wyrm.Ink, knobR, center)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Move the zoom bar to see its action.",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                color = Wyrm.Quiet,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
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
    arrowSkin: Int = -1,
    arrowBrightness: Float = 1f,
    /** Playing upright: the preview is the shape of the phone held upright. */
    portrait: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (portrait) {
                    Modifier
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .width(160.dp)
                        .height(300.dp)
                } else {
                    Modifier.height(190.dp)
                },
            )
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
                    modifier = Modifier.adjustPlace(AdjustSubject.JOYSTICK),
                    opacity = opacity,
                )
            }
        }
        if (showArrow && arrowSkin >= 0) {
            // An image arrow, sized as the arena sizes it: a square 1.44 x its length.
            ArrowGlyph(
                codeStyle = arrowStyle,
                imageSkin = arrowSkin,
                colour = arrowColor,
                brightness = arrowBrightness,
                modifier = Modifier.align(Alignment.Center).size((52f * 1.44f * arrowSize).dp)
                    .adjustPlace(AdjustSubject.ARROW),
            )
        } else if (showArrow) {
            // The drawn arrow's reach (length -0.72..0.82, width +-0.72), for the adjust preview.
            Box(
                Modifier.align(Alignment.Center)
                    .size(width = (1.64f * 52f * arrowSize).dp, height = (1.44f * 30f * arrowSize).dp)
                    .adjustPlace(AdjustSubject.ARROW),
            )
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
                    modifier = Modifier.adjustPlace(AdjustSubject.BOOST),
                    opacity = opacity,
                )
            }
        }
        if (showZoom) {
            PreviewPositioned(position = zoomPosition) {
                PaperZoomBar(
                    length = (102 * zoomLength).dp,
                    modifier = Modifier.adjustPlace(AdjustSubject.ZOOM),
                    vertical = zoomVertical,
                    opacity = opacity,
                    value = 0.45f,
                )
            }
        }
    }
}

/**
 * The adjust preview's card (see AdjustPreview.kt): the control being changed,
 * drawn at its live size, opacity and colour at the top of the page while the
 * page's own preview is off screen. The same views as [ControlsPreview].
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.AdjustPreviewCard(settings: List<Setting>, top: Dp) {
    val shown = AdjustPreview.shown
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(if (shown) 220 else 280),
        label = "adjust preview",
    )
    val subject = AdjustPreview.subject ?: return
    if (alpha <= 0.001f) return
    val opacity = settings.named("controls.opacity")?.number ?: 1f
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = top)
            .alpha(alpha)
            .scale(0.96f + 0.04f * alpha)
            .shadow(18.dp, wyrmRounded(18.dp), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(wyrmRounded(18.dp))
            .background(Wyrm.Well)
            .border(1.dp, Wyrm.Rule, wyrmRounded(18.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .widthIn(min = 150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (subject) {
                AdjustSubject.ARROW -> "ARROW PREVIEW"
                AdjustSubject.JOYSTICK -> "JOYSTICK PREVIEW"
                AdjustSubject.BOOST -> "BOOST PREVIEW"
                AdjustSubject.ZOOM -> "ZOOM BAR PREVIEW"
            },
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            color = Wyrm.Quiet,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.defaultMinSize(minHeight = 70.dp), contentAlignment = Alignment.Center) {
            when (subject) {
                AdjustSubject.ARROW -> {
                    val size = settings.named("arrow.size")?.number ?: 1f
                    val style = (settings.named("arrow.style")?.index ?: 0).coerceIn(0, ArrowOptions.lastIndex)
                    val channels = settings.named("arrow.color")?.channels ?: listOf(1f, 1f, 1f, 1f)
                    val colour = Color(channels[0].coerceIn(0f, 1f), channels[1].coerceIn(0f, 1f), channels[2].coerceIn(0f, 1f))
                    if (ArrowSkinStore.skin >= 0) {
                        ArrowGlyph(
                            codeStyle = style,
                            imageSkin = ArrowSkinStore.skin,
                            colour = colour,
                            brightness = ArrowSkinStore.brightness,
                            modifier = Modifier.size((52f * 1.44f * size).dp).alpha(opacity.coerceIn(0f, 1f)),
                        )
                    } else {
                        Canvas(Modifier.size(width = (1.64f * 52f * size).dp + 8.dp, height = (1.44f * 30f * size).dp + 8.dp)) {
                            drawArrowShape(
                                style = style,
                                length = 52.dp.toPx() * size,
                                width = 30.dp.toPx() * size,
                                fill = colour,
                                outline = Color(0xFF040609),
                                alpha = opacity.coerceIn(0f, 1f),
                                outlineWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }
                AdjustSubject.JOYSTICK -> PaperJoystick(
                    diameter = (60 * (settings.named("controls.joystick_size")?.number ?: 1f)).dp,
                    opacity = opacity,
                )
                AdjustSubject.BOOST -> PaperBoostButton(
                    diameter = (46 * (settings.named("controls.boost_size")?.number ?: 1f)).dp,
                    opacity = opacity,
                )
                AdjustSubject.ZOOM -> PaperZoomBar(
                    length = (102 * (settings.named("controls.zoom_length")?.number ?: 1f)).dp,
                    vertical = settings.named("controls.zoom_orientation")?.index == 1,
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

internal fun DrawScope.drawArrowShape(
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
