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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import kotlin.math.sin
import com.wyrm.omrajput.data.SettingType

/**
 * Spec page 12 — Settings › Assist.
 *
 * Assist is an on-screen button, not a persistent engine flag — this page owns
 * the colours and helper drawing that apply when it is held. Laser left Bot.
 */
@Composable
fun SettingsAssistScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String = "Settings",
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
    /** Opens the live background-size editor (the slider moved there, OM 2026-10-01). */
    onAdjustBackground: () -> Unit = {},
    /** The worn skin, so the Snake preview is the player's own snake (OM, 2026-10-05). */
    skin: SkinState = SkinState(),
) {
    // Settings search picks the tab that holds the row it opened.
    var mode by remember { mutableIntStateOf(if (SettingsFocus.target?.startsWith("normal.") == true) 0 else 1) }
    var advanced by remember { mutableStateOf(true) }
    val foodIds = setOf("food_type", "food_scale", "food_float", "food_flicker",
        "const_food_scale", "uniform_food_color", "food_color")
    val dotIds = setOf("show_crosshair", "head_dot_size", "head_dot_color")
    val snakeIds = setOf("render_mode", "spine", "hide_cosmetics")
    val laser = listOfNotNull(
        settings.named("general.laser_thickness"),
        settings.named("general.laser_color"),
    )

    SettingsDrillScaffold(
        title = "Modes",
        parent = backLabel,
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        // Clear of the header rule, as on iOS.
        Spacer(Modifier.height(18.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 15.dp)) {
                Text(
                    text = "Arena modes",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                )
                Text(
                    text = "Tune the normal arena and the helper view independently.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        SettingsSectionLabel("Choose mode")
        SettingsCard {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
                PaperSegmented(
                    options = listOf("Assist mode", "Normal mode"),
                    selected = if (mode == 1) 0 else 1,
                    onSelect = { mode = if (it == 0) 1 else 0 },
                )
            }
        }

        AnimatedContent(
            targetState = mode,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                if (targetState > initialState) {
                    (fadeIn(tween(180)) + slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) { it / 7 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(190)) { -it / 9 })
                } else {
                    (fadeIn(tween(180)) + slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) { -it / 7 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(190)) { it / 9 })
                }
            },
            label = "mode-settings-content",
        ) { visibleMode ->
            val group = if (visibleMode == 1) "assist" else "normal"
            val modeSettings = settings.filter { it.group == group && it.label.isNotBlank() }
            val colours = modeSettings.filter {
                it.id.substringAfter('.') !in foodIds + dotIds &&
                    (it.type == SettingType.COLOR3 || it.type == SettingType.COLOR4)
            }
            val headDot = modeSettings.firstOrNull { it.id.substringAfter('.') == "show_crosshair" }
            val headDotSize = modeSettings.firstOrNull { it.id.substringAfter('.') == "head_dot_size" }
            val headDotColor = modeSettings.firstOrNull { it.id.substringAfter('.') == "head_dot_color" }
            val rest = modeSettings.filter {
                it !in colours && it.id.substringAfter('.') !in foodIds + dotIds + snakeIds + "bg_scale"
            }
            val backgroundScale = modeSettings.firstOrNull { it.id.substringAfter('.') == "bg_scale" }

            Column {
                // Snake look (OM, 2026-10-05): follows the visible mode tab.
                SettingsSectionLabel("Snake")
                SettingsCard {
                    val render = modeSettings.firstOrNull { it.id.substringAfter('.') == "render_mode" }
                    val spineOn = modeSettings.firstOrNull { it.id.substringAfter('.') == "spine" }
                    val hideOn = visibleMode == 1 &&
                        modeSettings.firstOrNull { it.id.substringAfter('.') == "hide_cosmetics" }?.enabled == true
                    SnakeBodyPreview(
                        mode = render?.index ?: 0,
                        spine = spineOn?.enabled == true,
                        skin = skin,
                        hideCosmetics = hideOn,
                    )
                    render?.let { setting ->
                        SettingTypedRow(setting = setting, first = false, onChange = onChange)
                    }
                    modeSettings.firstOrNull { it.id.substringAfter('.') == "spine" }?.let { setting ->
                        SettingTypedRow(setting = setting, first = false, onChange = onChange)
                    }
                    if (visibleMode == 1) {
                        modeSettings.firstOrNull { it.id.substringAfter('.') == "hide_cosmetics" }?.let { setting ->
                            SettingTypedRow(setting = setting, first = false, onChange = onChange)
                        }
                    }
                }
                SettingsCaption("Skinless keeps the plain snake's width and length. Only the skin turns see-through. Spine is a thin white line down every snake.")

                SettingsSectionLabel("Arena colours")
                SettingsCard {
                    colours.forEachIndexed { index, setting ->
                        Box(Modifier.settingAnchor(setting.id)) {
                            SettingsColourRow(setting = setting, first = index == 0, onChange = onChange)
                        }
                    }
                }

                SettingsSectionLabel("Arena background")
                SettingsCard {
                    Box(Modifier.settingAnchor("app.bg-size")) {
                        SettingsValueRow(
                            title = "Adjust arena background size",
                            value = backgroundScale?.let { bgScaleLabel(it.number) } ?: "",
                            first = true,
                            onOpen = { onAdjustBackground() },
                        )
                    }
                }

                if (LocalTouchControls.current) {
                SettingsSectionLabel("Joystick guide")
                SettingsCard {
                    HeadDotPreview(size = headDotSize, colour = headDotColor)
                    headDot?.let { setting ->
                        SettingTypedRow(setting = setting, first = false, onChange = onChange)
                    }
                    headDotSize?.let { setting ->
                        SettingTypedRow(setting = setting, first = false, onChange = onChange)
                    }
                    headDotColor?.let { setting ->
                        Box(Modifier.settingAnchor(setting.id)) {
                            SettingsColourRow(setting = setting, first = false, onChange = onChange)
                        }
                    }
                }

                }

                // OM, 2026-10-01: the assist laser for joystick players, with a live preview.
                if (visibleMode == 1 && LocalTouchControls.current) {
                    val laserColour = settings.named("general.laser_color")?.channels
                        ?.let { Color(it[0], it[1], it[2], it.getOrElse(3) { 1f }) }
                        ?: Color(0.5f, 1f, 0.5f, 1f)
                    val laserThickness = settings.named("general.laser_thickness")?.number ?: 2f
                    SettingsSectionLabel("Assist laser in joystick")
                    SettingsCard {
                        Box(Modifier.settingAnchor("app.joystick-laser")) {
                            JoystickLaserPreview(
                                length = JoystickLaserStore.length,
                                on = JoystickLaserStore.on,
                                colour = laserColour,
                                thicknessPx = laserThickness,
                            )
                        }
                        SettingsBoolRow(
                            title = "Assist laser in joystick",
                            detail = "With assist on, a line from your head shows where your snake is heading.",
                            on = JoystickLaserStore.on,
                            first = false,
                            onToggle = { JoystickLaserStore.applyOn(it) },
                        )
                        if (JoystickLaserStore.on) {
                            SettingsSliderRow(
                                title = "Laser length",
                                valueText = joystickLaserLabel(JoystickLaserStore.length),
                                detail = "A share of the screen's short side",
                                value = JoystickLaserStore.length,
                                range = JoystickLaserStore.RANGE,
                                steps = 0,
                                first = false,
                                onChange = { JoystickLaserStore.applyLength(it) },
                            )
                        }
                    }
                    SettingsCaption("Colour and thickness are the laser's own, under Advanced · helper lines. Arrow steering keeps its own laser.")
                }

                AdvancedFold(
                    label = "Advanced · helper lines",
                    open = advanced,
                    onToggle = { advanced = !advanced },
                )
                if (advanced) {
                    SettingsCard {
                        var first = true
                        laser.forEach { setting ->
                            SettingTypedRow(setting = setting, first = first, onChange = onChange)
                            first = false
                        }
                        rest.forEach { setting ->
                            SettingTypedRow(setting = setting, first = first, onChange = onChange)
                            first = false
                        }
                    }
                }
            }
        }
    }
}

/**
 * The player's own snake as the arena draws it in each mode (OM, 2026-10-05:
 * "asli skin ... jaisi arena me dikhegi vaisi"). Texture is the Skin tab's own
 * bead drawing (the arena's sprites); Solid and Flat follow the engine's
 * render modes 1 and 2 (`redraw.c`): Solid paints every bead in its pattern
 * colour from the head back, Flat the first bead's colour; Skinless is one
 * see-through stroke (.8) of the plain body's width in that first colour,
 * round at both ends. The head (eyes, accessory, Wyrm look) is drawn in every
 * mode, as in the arena; in assist with "Hide own tag and accessories" on,
 * the accessory and the look are left off.
 */
@Composable
private fun SnakeBodyPreview(mode: Int, spine: Boolean, skin: SkinState, hideCosmetics: Boolean) {
    val textures by rememberSkinTextures()
    val savedLook = WyrmLookStore.spec()
    val custom = skin.custom && skin.code.isNotEmpty()
    val codeGroups = skin.code.mapNotNull { SkinCatalog.group(it) }.filter { it in SkinCatalog.validGroups }.take(256)
    val source = if (custom && codeGroups.isNotEmpty()) codeGroups
        else SkinCatalog.presets.getOrNull(skin.preset) ?: listOf(7)
    val total = 160
    // Index 0 is the head, as the engine counts a pattern.
    val groups = List(total) { source[it % source.size] }
    val colors = if (custom && codeGroups.isNotEmpty()) List(total) { skin.colourAt(it % codeGroups.size) } else List(total) { 0 }
    val accessory = if (hideCosmetics) -1 else skin.accessory
    val look = if (hideCosmetics) WyrmLookSpec() else savedLook

    /* One bead's colour as the engine paints it in Solid/Flat/Skinless: an
       exact colour picked with the wheel when there is one (Wyrm beads opaque),
       else the colour group's own. */
    fun beadColour(codeIndex: Int): Color {
        val argb = colors.getOrElse(codeIndex) { 0 }
        if (argb != 0) {
            val alpha = if (WyrmBeads.kind(argb) != null) 1f else ((argb ushr 24) and 0xFF) / 255f
            return Color(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, alpha)
        }
        val rgb = AirSkin.groupRgb(groups[codeIndex])
        return Color(((rgb shr 16) and 0xFF) / 255f, ((rgb shr 8) and 0xFF) / 255f, (rgb and 0xFF) / 255f)
    }

    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text("Preview", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(Wyrm.Well, wyrmRounded(12.dp))
                .border(1.dp, Wyrm.Rule, wyrmRounded(12.dp)),
        ) {
            Canvas(Modifier.fillMaxWidth().height(88.dp)) {
                val px = 1.dp.toPx()
                val span = 1f + (total - 1) * (8f / 48f) + 0.7f
                val scale = minOf(size.height * 0.34f, (size.width - 28.dp.toPx()) / span)
                val step = 8f * (scale / 48f)
                val left = (size.width - span * scale) / 2f
                val amp = size.height * 0.16f
                // Segment 0 is the tail (left), total - 1 the head (right).
                fun place(segment: Int): Offset {
                    val along = segment / (total - 1f)
                    return Offset(left + scale * 0.5f + segment * step, size.height * 0.5f + amp * sin(along * 6.2831855f * 1.15f))
                }
                fun heading(segment: Int): Float {
                    val a = place((segment - 1).coerceAtLeast(0))
                    val b = place((segment + 1).coerceAtMost(total - 1))
                    return Math.toDegrees(kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
                }
                val t = textures
                val head = place(total - 1)
                when {
                    mode == 0 && t != null -> drawSkinAlong(
                        t, groups, colors, skin.preset, custom, accessory, look, scale, px, total,
                        place = { place(it) }, heading = { heading(it) },
                    ) { img, l, tp, iw, ih, tint, alpha -> drawImageInto(img, l, tp, iw, ih, tint, alpha) }
                    mode == 3 -> {
                        val path = Path()
                        val first = place(0)
                        path.moveTo(first.x, first.y)
                        for (segment in 1 until total) place(segment).let { path.lineTo(it.x, it.y) }
                        drawPath(
                            path,
                            beadColour(0).copy(alpha = 0.8f), // the engine's strip: first colour, .8
                            style = Stroke(width = scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    else -> for (segment in 0 until total) {
                        // Solid: each bead its own colour; Flat (and Texture before
                        // the sprites load): Flat uses the first bead's.
                        val codeIndex = total - 1 - segment
                        drawCircle(beadColour(if (mode == 2) 0 else codeIndex), scale * 0.5f, place(segment))
                    }
                }
                if (spine) {
                    val path = Path()
                    val first = place(0)
                    path.moveTo(first.x, first.y)
                    for (segment in 1 until total) place(segment).let { path.lineTo(it.x, it.y) }
                    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 3.4f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(width = 1.7f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // The head over the spine, as the arena draws its eyes last (Texture
                // drew its head already; again only when a spine crosses it).
                if (t != null && (mode != 0 || spine)) {
                    rotate(heading(total - 1), pivot = head) {
                        drawSkinHead(t, head, scale, px, skin.preset, custom, accessory, look) { img, l, tp, iw, ih, tint ->
                            drawImageInto(img, l, tp, iw, ih, tint, 1f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadDotPreview(size: Setting?, colour: Setting?) {
    val channels = colour?.channels ?: listOf(1f, 1f, 1f, 1f)
    val dot = Color(channels[0], channels[1], channels[2], 1f)
    val diameter = (size?.number ?: 10f).coerceIn(4f, 32f)

    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = "Size relative to snake head",
            fontFamily = Wyrm.Body,
            fontSize = 12.5.sp,
            color = Wyrm.Quiet,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(Wyrm.Well, wyrmRounded(12.dp))
                .border(1.dp, Wyrm.Rule, wyrmRounded(12.dp)),
        ) {
            Canvas(Modifier.fillMaxWidth().height(74.dp)) {
                val headRadius = 22.dp.toPx()
                val headCenter = Offset(this.size.width * 0.5f - headRadius * 0.35f, this.size.height * 0.5f)
                // The arena head is 29 world units wide. Both it and the dot
                // receive the same snake-scale and camera projection, so this
                // ratio is exactly what survives on screen at every zoom.
                drawCircle(Wyrm.Ink, headRadius, headCenter)
                drawCircle(
                    color = dot,
                    radius = headRadius * diameter / 29f,
                    center = Offset(headCenter.x + headRadius, headCenter.y),
                )
            }
        }
    }
}
