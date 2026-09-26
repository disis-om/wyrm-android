package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Hotkey
import com.wyrm.omrajput.data.Setting
import com.wyrm.omrajput.data.SettingType
import kotlin.math.roundToInt

enum class SettingsSection(val title: String, val detail: String) {
    GENERAL("General", "Display, text and the bot"),
    ASSIST("Assist", "How the arena looks with assist on"),
    NORMAL("Normal mode", "How the arena looks normally"),
    NOTIFICATIONS("Notifications", "Choose exactly what reaches you"),
    BACKUP("Backup", "Protect and restore everything yours"),
    UPDATES("Check for updates", "Version and install"),
    PRIVACY("Privacy", "What Wyrm knows about you"),
}

private data class SettingsHubGroup(
    val title: String,
    val rows: List<SettingsHubRow>,
)

private data class SettingsHubRow(
    val title: String,
    val sub: String,
    val value: String,
    val onOpen: (Rect) -> Unit,
)

/**
 * Spec page 08 — Settings tab.
 *
 * Four groups: Arena, Playing help, Account, This device. Nested pages drill
 * in. Reset is a real engine action, not a mock.
 */
@Composable
fun SettingsScreen(
    settings: List<Setting>,
    hotkeys: List<Hotkey>,
    profileHandle: String,
    notificationsOn: Int,
    backupDetail: String,
    updateDetail: String,
    settingsVersion: String,
    appVersion: String,
    themeName: String,
    foodValue: String,
    unreadNotifications: Int,
    insetTop: Dp,
    insetBottom: Dp,
    showRootTabs: Boolean = true,
    onOpenDisplay: (Rect) -> Unit,
    onOpenControls: (Rect) -> Unit,
    onOpenButtons: (Rect) -> Unit,
    onOpenAssist: (Rect) -> Unit,
    onOpenBot: (Rect) -> Unit,
    onOpenProfile: (Rect) -> Unit,
    onOpenNotifications: (Rect) -> Unit,
    onOpenPrivacy: (Rect) -> Unit,
    onOpenAccessibility: (Rect) -> Unit,
    onOpenFood: (Rect) -> Unit,
    onOpenBackup: (Rect) -> Unit,
    onResetAll: () -> Unit,
    onTabNotifications: (Rect) -> Unit,
    onTabPlay: (Rect) -> Unit,
    onTabSocial: (Rect) -> Unit,
    onTabSkin: (Rect) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val steering = settings.named("controls.joystick_mode")
    val controlsValue = if ((steering?.index ?: 0) == 2) "Arrow" else "Joystick"
    val buttonsOn = hotkeys.count { it.visible }
    val groups = listOf(
        SettingsHubGroup(
            "Arena",
            listOf(
                SettingsHubRow("Display", "Scores, names, minimap, text sizes", "", onOpenDisplay),
                SettingsHubRow("Controls", "Steering, boost, zoom bar", controlsValue, onOpenControls),
                SettingsHubRow(
                    "On-screen buttons",
                    "Which buttons appear and how they fire",
                    if (hotkeys.isEmpty()) "" else "$buttonsOn on",
                    onOpenButtons,
                ),
            ),
        ),
        SettingsHubGroup(
            "Playing help",
            listOf(
                SettingsHubRow("Modes", "Normal, Assist, helper lines and arena colours", "", onOpenAssist),
                SettingsHubRow("Bot", "When it circles, how wide it swings", "", onOpenBot),
            ),
        ),
        SettingsHubGroup(
            "Food",
            listOf(
                SettingsHubRow(
                    "Food style",
                    "Original, rings and geometric shapes",
                    foodValue,
                    onOpenFood,
                ),
            ),
        ),
        SettingsHubGroup(
            "Account",
            listOf(
                SettingsHubRow("Profile", "Name, username, photo, bio", profileHandle, onOpenProfile),
                SettingsHubRow(
                    "Notifications",
                    "Invites, team pings, follows",
                    if (notificationsOn > 0) "$notificationsOn on" else "",
                    onOpenNotifications,
                ),
                SettingsHubRow("Privacy", "Who can reach you, what is stored", "", onOpenPrivacy),
            ),
        ),
        SettingsHubGroup(
            "Accessibility",
            listOf(
                SettingsHubRow(
                    "Themes",
                    "Paper, dark and colour appearances",
                    themeName,
                    onOpenAccessibility,
                ),
            ),
        ),
        SettingsHubGroup(
            "This device",
            listOf(
                SettingsHubRow(
                    "Backup & version",
                    backupDetail.ifBlank { "Skins, controls, settings and team keys" } +
                        " · Wyrm $appVersion" +
                        if (settingsVersion.isNotBlank()) " · format v$settingsVersion" else "",
                    updateDetail,
                    onOpenBackup,
                ),
            ),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    text = "WYRM",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.92.sp,
                    color = Wyrm.Quiet,
                )
                Text(
                    text = "Settings",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            groups.forEach { group ->
                SettingsSectionLabel(group.title, top = 0.dp)
                SettingsCard {
                    group.rows.forEachIndexed { index, row ->
                        SettingsHubLine(row = row, first = index == 0)
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
            SettingsCard {
                SettingsActionRow(
                    title = if (confirming) {
                        "Tap again to reset everything"
                    } else {
                        "Reset everything to defaults"
                    },
                    first = true,
                    danger = true,
                    onClick = {
                        if (confirming) {
                            confirming = false
                            onResetAll()
                        } else {
                            confirming = true
                        }
                    },
                )
            }
            Text(
                text = if (settingsVersion.isNotBlank()) {
                    "Wyrm · settings format v$settingsVersion"
                } else {
                    "Wyrm"
                },
                fontFamily = Wyrm.Body,
                fontSize = 12.sp,
                color = Wyrm.Quiet,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
            )
            Spacer(Modifier.height(LocalRootTabClearance.current))
        }
        if (showRootTabs) {
            RootTabs(
                selected = RootTab.SETTINGS,
                insetBottom = insetBottom,
                unreadNotifications = unreadNotifications,
                onNotifications = onTabNotifications,
                onPlay = onTabPlay,
                onSocial = onTabSocial,
                onSkin = onTabSkin,
                onSettings = {},
            )
        }
    }
}

@Composable
private fun SettingsHubLine(row: SettingsHubRow, first: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .scale(pressScale(pressed))
                .clickable(interactionSource = interaction, indication = null) { row.onOpen(bounds) }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(text = row.title, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                Text(
                    text = row.sub,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (row.value.isNotBlank()) {
                Text(text = row.value, fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Quiet)
                Spacer(Modifier.width(6.dp))
            }
            Text(text = "›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = Wyrm.Chevron)
        }
    }
}

/**
 * One group of settings, drawn from the engine's description.
 *
 * [groups] are matched as prefixes, so "general" also brings in "general.type"
 * and "general.bot" and they arrive already sorted into their own blocks.
 */
@Composable
fun SettingsGroupScreen(
    title: String,
    caption: String,
    settings: List<Setting>,
    groups: List<String>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    val visible = settings.filter { setting -> groups.any { setting.group == it || setting.group.startsWith("$it.") } }
    val blocks = visible.groupBy { it.group }.toList().sortedBy { (group, _) ->
        groups.indexOfFirst { group == it || group.startsWith("$it.") } * 100 + group.length
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = insetTop)
                .height(52.dp)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = "‹ Settings",
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
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = insetBottom),
        ) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Wyrm.Gutter),
            ) {
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        fontFamily = Wyrm.Body,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Wyrm.Grey,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                header?.invoke()

                blocks.forEach { (group, rows) ->
                    Spacer(Modifier.height(Wyrm.Gap))
                    val suffix = group.substringAfter('.', "")
                    if (suffix.isNotEmpty()) {
                        WyrmLabel(suffix.replace('_', ' '))
                        Spacer(Modifier.height(4.dp))
                    }
                    rows.forEach { setting ->
                        WyrmRule()
                        SettingRow(setting = setting, onChange = { onChange(setting, it) })
                    }
                    WyrmRule()
                }

                footer?.invoke()
                Spacer(Modifier.height(Wyrm.GapLarge))
            }
        }
    }
}

@Composable
fun SettingsRail(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Wyrm.Gutter)
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The back arrow is the one control on every screen, so it gets a
        // surface of its own rather than being a character floating in space.
        Text(
            text = "←",
            fontFamily = Wyrm.Body,
            fontSize = 18.sp,
            color = Wyrm.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(glassFill())
                .border(1.dp, glassEdge(), CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 13.dp, vertical = 7.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 3.sp,
            color = Wyrm.White,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * Picks the control that suits the shape of the value.
 *
 * Shared with the skin editor's Tags tab, which shows a handful of these rows
 * beside the preview rather than in the tree.
 */
@Composable
internal fun SettingRow(setting: Setting, onChange: (List<Float>) -> Unit) {
    when (setting.type) {
        SettingType.BOOL -> ToggleRow(setting, onChange)
        SettingType.ENUM -> ChoiceRow(setting, onChange)
        SettingType.COLOR3, SettingType.COLOR4 -> ColourRow(setting, onChange)
        SettingType.INT, SettingType.FLOAT -> SliderRow(setting, onChange)
    }
}

@Composable
private fun ToggleRow(setting: Setting, onChange: (List<Float>) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(listOf(if (setting.enabled) 0f else 1f)) }
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = setting.label,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Wyrm.White,
            )
            if (setting.hint.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = setting.hint,
                    fontFamily = Wyrm.Body,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = Wyrm.Faint,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        WyrmSwitch(checked = setting.enabled)
    }
}

/** A flat switch: a rail and a dot, no ripple, no colour but the state. */
@Composable
fun WyrmSwitch(checked: Boolean) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "wyrm-switch-thumb",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Wyrm.Black else Wyrm.Faint,
        animationSpec = tween(durationMillis = 200),
        label = "wyrm-switch-colour",
    )
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .wyrmSegment(checked),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun ChoiceRow(setting: Setting, onChange: (List<Float>) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(
            text = setting.label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.White,
        )
        if (setting.hint.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = setting.hint,
                fontFamily = Wyrm.Body,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Wyrm.Faint,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Stacked rather than side by side: some of these options are whole
        // phrases, and a segmented control would shrink them to nothing.
        val wide = setting.options.any { it.length > 12 }
        if (wide) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                setting.options.forEachIndexed { index, option ->
                    ChoiceChip(
                        label = option,
                        selected = index == setting.index,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onChange(listOf(index.toFloat())) },
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                setting.options.forEachIndexed { index, option ->
                    ChoiceChip(
                        label = option,
                        selected = index == setting.index,
                        modifier = Modifier.weight(1f),
                        onClick = { onChange(listOf(index.toFloat())) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .wyrmSegment(selected)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = if (selected) Wyrm.Black else Wyrm.SoftWhite,
        )
    }
}

@Composable
private fun SliderRow(setting: Setting, onChange: (List<Float>) -> Unit) {
    val whole = setting.type == SettingType.INT
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = setting.label,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Wyrm.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (whole) setting.number.roundToInt().toString()
                else String.format("%.2f", setting.number),
                fontFamily = Wyrm.Display,
                fontSize = 17.sp,
                color = Wyrm.SoftWhite,
            )
        }
        if (setting.hint.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = setting.hint,
                fontFamily = Wyrm.Body,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Wyrm.Faint,
            )
        }
        Spacer(Modifier.height(12.dp))
        WyrmSlider(
            value = setting.number,
            minimum = setting.minimum,
            maximum = setting.maximum,
        ) { picked ->
            onChange(listOf(if (whole) picked.roundToInt().toFloat() else picked))
        }
    }
}

/**
 * A hairline track with a filled run.
 *
 * Dragging anywhere on the row moves it, and so does a tap, because a 2dp line
 * is a cruel thing to ask a thumb to find.
 */
@Composable
fun WyrmSlider(
    value: Float,
    minimum: Float,
    maximum: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    var width by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val span = (maximum - minimum).takeIf { it > 0f } ?: 1f
    val fraction = ((value - minimum) / span).coerceIn(0f, 1f)

    val knobSize by animateDpAsState(
        targetValue = if (dragging) 28.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "knob",
    )
    val knobPx = with(density) { knobSize.toPx() }
    // The knob stays inside the track, so the run it travels is the control
    // minus itself — anything else lets it hang off both ends.
    val run = (width - knobPx).coerceAtLeast(1f)
    val travel = remember { mutableFloatStateOf(0f) }

    fun report(x: Float) = onChange(minimum + (x / run).coerceIn(0f, 1f) * span)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(minimum, maximum, width) {
                detectTapGestures { report(it.x - knobPx / 2f) }
            }
            /*
             * Horizontal drags only.
             *
             * This used to take the finger the instant it landed, in any
             * direction — so a scroll that happened to begin on a slider moved
             * the slider instead of the page. Asking for one orientation means
             * the gesture has to travel sideways before it is claimed, and a
             * vertical swipe passes straight through to the list.
             */
            .draggable(
                state = rememberDraggableState { delta ->
                    travel.floatValue = (travel.floatValue + delta).coerceIn(0f, run)
                    report(travel.floatValue)
                },
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    dragging = true
                    travel.floatValue = fraction * run
                },
                onDragStopped = { dragging = false },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The track: a capsule, the way a phone draws one, with the run behind
        // the knob filled in.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(wyrmRounded(Wyrm.Pill))
                .background(Color.Black.copy(alpha = 0.34f))
                .background(wellFill())
        )
        Box(
            modifier = Modifier
                .width(with(density) { (knobPx / 2f + run * fraction).toDp() })
                .height(5.dp)
                .clip(wyrmRounded(Wyrm.Pill))
                .background(Wyrm.White)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((run * fraction).roundToInt(), 0) }
                .size(knobSize)
                .clip(CircleShape)
                // Lit from above like everything else, with a shadow under it
                // so it sits on the track rather than in it.
                .background(
                    Brush.verticalGradient(
                        0f to Color.White,
                        1f to Color(0xFFE4E2DC),
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
        )
    }
}

/**
 * Colour, chosen the way a colour is chosen: hue first, then how strong and
 * how bright. Three tracks beat a rainbow square on a phone, where a fingertip
 * covers most of the square it is trying to aim at.
 */
@Composable
private fun ColourRow(setting: Setting, onChange: (List<Float>) -> Unit) {
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
        onChange(if (withAlpha) values + alpha else values)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = setting.label,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Wyrm.White,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(wyrmRounded(Wyrm.CornerSmall))
                    .background(Color(channels[0], channels[1], channels[2], 1f))
                    .border(1.dp, glassEdge(1.4f), wyrmRounded(Wyrm.CornerSmall))
            )
        }
        Spacer(Modifier.height(12.dp))
        HueTrack(hue = hsv[0]) { emit(it, hsv[1], hsv[2], channels[3]) }
        Spacer(Modifier.height(10.dp))
        ShadeTrack(
            label = "Strength",
            value = hsv[1],
            gradient = listOf(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2]))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2]))),
            ),
        ) { emit(hsv[0], it, hsv[2], channels[3]) }
        Spacer(Modifier.height(10.dp))
        ShadeTrack(
            label = "Brightness",
            value = hsv[2],
            gradient = listOf(
                Color.Black,
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))),
            ),
        ) { emit(hsv[0], hsv[1], it, channels[3]) }
        if (withAlpha) {
            Spacer(Modifier.height(10.dp))
            ShadeTrack(
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
private fun HueTrack(hue: Float, onPick: (Float) -> Unit) {
    val rainbow = Brush.horizontalGradient(
        (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
    )
    GradientTrack(fraction = hue / 360f, brush = rainbow) { onPick(it * 360f) }
}

@Composable
private fun ShadeTrack(
    label: String,
    value: Float,
    gradient: List<Color>,
    onPick: (Float) -> Unit,
) {
    Column {
        WyrmLabel(label)
        Spacer(Modifier.height(6.dp))
        GradientTrack(fraction = value, brush = Brush.horizontalGradient(gradient), onPick = onPick)
    }
}

@Composable
private fun GradientTrack(fraction: Float, brush: Brush, onPick: (Float) -> Unit) {
    val density = LocalDensity.current
    var width by remember { mutableFloatStateOf(1f) }
    val knob = 26.dp
    val knobPx = with(density) { knob.toPx() }
    val run = (width - knobPx).coerceAtLeast(1f)
    val travel = remember { mutableFloatStateOf(0f) }
    val position = fraction.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(wyrmRounded(Wyrm.Pill))
            .background(brush)
            .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill))
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(width) {
                detectTapGestures { onPick(((it.x - knobPx / 2f) / run).coerceIn(0f, 1f)) }
            }
            // Sideways only, so a scroll that starts on a colour track scrolls.
            .draggable(
                state = rememberDraggableState { delta ->
                    travel.floatValue = (travel.floatValue + delta).coerceIn(0f, run)
                    onPick((travel.floatValue / run).coerceIn(0f, 1f))
                },
                orientation = Orientation.Horizontal,
                onDragStarted = { travel.floatValue = position * run },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((run * position).roundToInt(), 0) }
                .size(knob)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, Color.Black.copy(alpha = 0.40f), CircleShape)
        )
    }
}
