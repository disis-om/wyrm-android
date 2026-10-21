package com.wyrm.omrajput.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.RainCharacter
import com.wyrm.omrajput.data.SnakeyRain
import kotlin.math.roundToInt

/*
 * Snakey Rain (OM, 2026-10-14): the page opens like Loadout's Controls and
 * Modes (back, title, the Wyrm look), with the worn character's snake drawn
 * the way the Skin tab draws it, and Characters / Settings / Login below.
 * A character's skin, presets, tags and accessories are the Skin tab's own
 * pickers; only what Rain's character packet carries is offered (slither
 * beads, presets, NTL tags, accessories). In the arena the Rain key (an
 * on-screen key, placed in Arrange the UI) opens [RainArenaPanel].
 */

private enum class RainTab(val title: String) { CHARACTERS("Characters"), SETTINGS("Settings"), LOGIN("Login") }
private enum class RainEditor(val title: String) { PATTERN("Build a pattern"), PRESETS("Presets"), TAGS("Tags"), ACCESSORIES("Accessories") }

/** A Rain skin as the Skin tab's preview wants it: "/n" is preset n, anything else bead letters. */
private fun RainCharacter.preset(): Int? =
    skin.takeIf { it.startsWith("/") || it.startsWith("\\") }?.drop(1)?.toIntOrNull()?.takeIf { it in SkinCatalog.presets.indices }

private fun RainCharacter.beads(): List<Int> = skin.mapNotNull { SkinCatalog.group(it) }

private fun RainCharacter.previewColors(): List<Int> {
    val beadColors = skin.mapIndexedNotNull { index, c -> SkinCatalog.group(c)?.let { colors.getOrElse(index) { 0 } } }
    return List(256) { if (preset() != null || beadColors.isEmpty()) 0 else beadColors[it % beadColors.size] }
}

private fun RainCharacter.tagIndex(): Int =
    tag.toIntOrNull()?.let { ntl -> SkinCatalog.tags.indexOfFirst { it.ntlId == ntl && !isWyrmTag(it.ntlId) } } ?: -1

private fun RainCharacter.tagLabel(): String = tagIndex().takeIf { it >= 0 }?.let { "#${SkinCatalog.tags[it].ntlId}" } ?: "None"

private fun RainCharacter.groups256(): List<Int> {
    val source = preset()?.let { SkinCatalog.presets[it] } ?: beads().ifEmpty { SkinCatalog.presets.getOrNull(0) ?: listOf(7) }
    return List(256) { source[it % source.size] }
}

@Composable
private fun RainSnakePreview(textures: SkinTextures?, character: RainCharacter?, modifier: Modifier) {
    val c = character ?: RainCharacter()
    SkinPreview(
        textures = textures,
        groups = c.groups256(),
        colors = c.previewColors(),
        preset = c.preset() ?: 0,
        custom = c.preset() == null && c.beads().isNotEmpty(),
        accessoryId = if (c.cosmetic == 255) -1 else c.cosmetic,
        tagId = c.tagIndex(),
        backgroundId = 0,
        chain = 1.0,
        swing = 1.0,
        tagScale = 1.0,
        look = WyrmLookSpec(),
        modifier = modifier,
    )
}

/** Rain's link in one word, with a dot: Connected (green) or Disconnected (OM, 2026-10-14). */
@Composable
internal fun RainStatusLine(rain: SnakeyRain, modifier: Modifier = Modifier, ink: Color = Wyrm.Ink) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (rain.connected) Wyrm.Live else Wyrm.Quiet))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (!rain.connected) "Disconnected" else if (rain.running) "Connected · ${rain.count} bots" else "Connected",
            fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = ink,
        )
    }
}

/** Where the Rain page is: the home tabs, a character, or one of its pickers. */
private data class RainPage(val id: String? = null, val editor: RainEditor? = null)

/**
 * The Skin tab's flow (OM, 2026-10-14: "yeh Wyrm app ka hi part hai"): the
 * preview stays put at the top and only changes with the character; under
 * the rule each page comes in with the Skin tab's spring, and a page inside
 * has the round back arrow at the rule's top-left (InlineHeader).
 */
@Composable
internal fun SnakeyRainScreen(
    rain: SnakeyRain,
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String,
    onBack: () -> Unit,
    onArrange: () -> Unit,
) {
    val textures by rememberSkinTextures()
    var tab by remember { mutableStateOf(RainTab.CHARACTERS) }
    var page by remember { mutableStateOf(RainPage()) }
    val open = page.id?.let { id -> rain.characters.firstOrNull { it.id == id } }
    if (page.id != null && open == null) page = RainPage()
    androidx.activity.compose.BackHandler(enabled = page.id != null) {
        page = if (page.editor != null) page.copy(editor = null) else RainPage()
    }
    val previewed = open ?: rain.characters.firstOrNull { it.enabled } ?: rain.characters.firstOrNull()

    Column(Modifier.fillMaxSize().background(Wyrm.Paper).padding(top = insetTop)) {
        Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 14.dp)) {
            Text(
                "‹ $backLabel", fontFamily = Wyrm.Body, fontSize = 16.sp, color = Wyrm.Link,
                modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 8.dp),
            )
            Text("Snakey Rain", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Wyrm.Ink,
                modifier = Modifier.align(Alignment.Center))
        }
        Box(Modifier.fillMaxWidth().height(218.dp)) {
            RainSnakePreview(textures, previewed, Modifier.fillMaxSize())
        }
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (fadeIn(iosSpring(0.42f, 0.86f)) + scaleIn(iosSpring(0.42f, 0.86f), initialScale = 0.985f, transformOrigin = TransformOrigin(0.5f, 0f))) togetherWith
                    (fadeOut(iosSpring(0.42f, 0.86f)) + scaleOut(iosSpring(0.42f, 0.86f), targetScale = 0.985f, transformOrigin = TransformOrigin(0.5f, 0f)))
            },
            modifier = Modifier.weight(1f),
            label = "rain-page",
        ) { shown ->
            val c = shown.id?.let { id -> rain.characters.firstOrNull { it.id == id } }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when {
                    c != null && shown.editor != null -> {
                        InlineHeader(shown.editor.title) { page = shown.copy(editor = null) }
                        RainEditorPage(rain, c, shown.editor, textures)
                    }
                    c != null -> {
                        InlineHeader(c.label.ifBlank { "Character" }) { page = RainPage() }
                        RainCharacterPage(rain, c, { page = shown.copy(editor = it) }, { rain.remove(setOf(c.id)); page = RainPage() })
                    }
                    else -> {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RainStatusLine(rain, Modifier.weight(1f))
                            InkSwitch(on = rain.enabled, onToggle = rain::enable)
                        }
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                            PaperSegmented(RainTab.entries.map { it.title }, tab.ordinal, { tab = RainTab.entries[it] })
                        }
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(iosSpring(0.38f, 0.9f)) togetherWith fadeOut(iosSpring(0.38f, 0.9f)) },
                            label = "rain-tab",
                        ) { shownTab ->
                            Column(Modifier.fillMaxWidth()) {
                                when (shownTab) {
                                    RainTab.CHARACTERS -> RainCharactersTab(rain, textures) { page = RainPage(id = it) }
                                    RainTab.SETTINGS -> RainSettingsTab(rain, onArrange)
                                    RainTab.LOGIN -> RainLoginTab(rain)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp + insetBottom))
            }
        }
    }
}

/* ------------------------------------------------------------- characters */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RainCharactersTab(rain: SnakeyRain, textures: SkinTextures?, onOpen: (String) -> Unit) {
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val list = rain.characters
    selection = selection.filter { id -> list.any { it.id == id } }.toSet()
    androidx.activity.compose.BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection.isEmpty()) {
            Text(
                "CHARACTERS · ${list.size}/${SnakeyRain.MAX_CHARACTERS}",
                fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                letterSpacing = 0.92.sp, color = Wyrm.Quiet, modifier = Modifier.weight(1f),
            )
            RoundIconButton(IosGlyph.PLUS, "Add a character", enabled = list.size < SnakeyRain.MAX_CHARACTERS) {
                rain.add()?.let { onOpen(it.id) }
            }
        } else {
            RoundIconButton(IosGlyph.XMARK, "Cancel") { selection = emptySet() }
            Text(
                "${selection.size} selected", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text(
                "Delete", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Wyrm.Badge,
                modifier = Modifier
                    .clip(wyrmRounded(10.dp))
                    .clickable { rain.remove(selection); selection = emptySet() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
    if (list.isEmpty()) {
        SettingsCaption("No characters yet. Tap + to add one; it starts as wyrm.bot.")
        return
    }
    SettingsCard {
        list.forEachIndexed { index, c ->
            if (index > 0) SettingsHairline()
            val picked = c.id in selection
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 62.dp)
                    .background(if (picked) Wyrm.Hover else Color.Transparent)
                    .combinedClickable(
                        onClick = {
                            if (selection.isEmpty()) onOpen(c.id)
                            else selection = if (picked) selection - c.id else selection + c.id
                        },
                        onLongClick = { selection = selection + c.id },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selection.isNotEmpty()) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (picked) Wyrm.Ink else Color.Transparent)
                            .border(1.5.dp, if (picked) Wyrm.Ink else Wyrm.Chevron, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (picked) IosIcon(IosGlyph.CHECKMARK, Wyrm.Paper, size = 12.dp, weight = 3.2f) }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        c.label.ifBlank { "Character" }, fontFamily = Wyrm.Body, fontSize = 15.5.sp,
                        color = if (c.enabled) Wyrm.Ink else Wyrm.Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        c.name.ifBlank { "No name" } + if (c.enabled) "" else " · off",
                        fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp),
                    )
                }
                MiniSnake(textures, c.groups256(), Modifier.width(92.dp).height(26.dp), c.previewColors())
                if (selection.isEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = Wyrm.Chevron)
                }
            }
        }
    }
    SettingsCaption("Tap a character to edit it. Touch and hold to select, then delete.")
}

@Composable
private fun RoundIconButton(glyph: IosGlyph, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(36.dp)
            .scale(pressScale(pressed, enabled))
            .clip(CircleShape)
            .background(if (enabled) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.3f))
            .clickable(interaction, indication = null, enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { IosIcon(glyph, Wyrm.OnInk, size = 16.dp, weight = 2.6f) }
}

/** A labelled text field in a settings card, typed in place. */
@Composable
private fun RainFieldRow(
    title: String,
    value: String,
    first: Boolean,
    enabled: Boolean = true,
    secret: Boolean = false,
    monospace: Boolean = false,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    Column {
        if (!first) SettingsHairline()
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Quiet)
            val style = TextStyle(
                fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else Wyrm.Body,
                fontSize = 15.5.sp, color = if (enabled) Wyrm.Ink else Wyrm.Quiet,
            )
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Wyrm.Link),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = style.copy(color = Wyrm.Quiet.copy(alpha = 0.6f)))
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun RainCharacterPage(rain: SnakeyRain, c: RainCharacter, onEditor: (RainEditor) -> Unit, onDelete: () -> Unit) {
    SettingsSectionLabel("Character")
    SettingsCard {
        RainFieldRow("Character name", c.label, first = true, placeholder = RainCharacter.DEFAULT_NAME) { rain.update(c.copy(label = it.take(24))) }
        RainFieldRow("In-game name", c.name, first = false, placeholder = RainCharacter.DEFAULT_NAME) { rain.update(c.copy(name = it.take(24))) }
        RainFieldRow("Skin code · ${c.skin.length}/250", c.skin, first = false, monospace = true, placeholder = "Type or paste a skin code") {
            rain.update(c.withSkin(it))
        }
    }
    SettingsSectionLabel("Look")
    SettingsCard {
        SettingsValueRow(RainEditor.PATTERN.title, if (c.preset() == null && c.beads().isNotEmpty()) "${c.beads().size} beads" else "", first = true, onOpen = { onEditor(RainEditor.PATTERN) })
        SettingsValueRow(RainEditor.PRESETS.title, c.preset()?.let { "%02d".format(it + 1) } ?: "", first = false, onOpen = { onEditor(RainEditor.PRESETS) })
        SettingsValueRow(RainEditor.TAGS.title, c.tagLabel(), first = false, onOpen = { onEditor(RainEditor.TAGS) })
        SettingsValueRow(RainEditor.ACCESSORIES.title, if (c.cosmetic == 255) "None" else "%02d".format(c.cosmetic + 1), first = false, onOpen = { onEditor(RainEditor.ACCESSORIES) })
    }
    SettingsSectionLabel("Storm")
    SettingsCard {
        SettingsBoolRow("Use this character", "Rain spawns bots with it", c.enabled, first = true) { rain.update(c.copy(enabled = it)) }
    }
    Spacer(Modifier.height(18.dp))
    SettingsCard { SettingsActionRow("Delete character", first = true, danger = true, onClick = onDelete) }
}

/** The Skin tab's own pickers, writing to this character. */
@Composable
private fun RainEditorPage(rain: SnakeyRain, c: RainCharacter, editor: RainEditor, textures: SkinTextures?) {
    val context = LocalContext.current
    when (editor) {
        RainEditor.PATTERN -> {
            val prefs = remember { context.getSharedPreferences("wyrm_skin_studio", Context.MODE_PRIVATE) }
            val groups = if (c.preset() == null) c.beads() else emptyList()
            // Rain's character packet carries the bead letters only (250 at most).
            SkinPatternBuilder(textures, prefs, groups, c.previewColors().take(groups.size), { next, colors ->
                rain.update(c.copy(skin = SkinCatalog.code(next).take(250), colors = colors.take(250)))
            }, maxBeads = 250)
        }
        RainEditor.PRESETS -> Column(Modifier.padding(horizontal = 20.dp)) {
            SkinCatalog.presets.forEachIndexed { index, groups ->
                val picked = c.preset() == index
                Row(
                    Modifier.fillMaxWidth().height(57.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            rain.update(c.withSkin("/$index"))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "%02d".format(index + 1), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                        color = if (picked) Wyrm.Live else Wyrm.Quiet, modifier = Modifier.width(24.dp),
                    )
                    MiniSnake(textures, groups, Modifier.weight(1f).height(38.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
            }
        }
        RainEditor.TAGS -> {
            // Rain's bots carry NTL's tags; Wyrm's own stickers cannot travel in its packet.
            val order = remember { TagPickerOrder.filter { !isWyrmTag(SkinCatalog.tags[it].ntlId) } }
            val current = c.tagIndex()
            TileGrid(minimum = 76.dp, count = order.size + 1) { index ->
                if (index == 0) {
                    SelectionTile(current < 0, "None") { rain.update(c.copy(tag = "-1", hash = "")) }
                } else {
                    val item = SkinCatalog.tags[order[index - 1]]
                    ImageTile(current == item.id, textures?.tagThumbnails?.get(item.id), 7.dp, badge = "${item.ntlId}") {
                        rain.update(c.copy(tag = item.ntlId.toString(), hash = ""))
                    }
                }
            }
        }
        RainEditor.ACCESSORIES -> {
            val current = if (c.cosmetic == 255) -1 else c.cosmetic
            TileGrid(minimum = 70.dp, count = SkinCatalog.accessories.size + 1, fixedColumns = 4) { index ->
                if (index == 0) {
                    SelectionTile(current < 0, "None") { rain.update(c.copy(cosmetic = 255)) }
                } else {
                    val item = SkinCatalog.accessories[index - 1]
                    ImageTile(current == item.id, textures?.accessoryThumbnails?.get(item.id), 4.dp) {
                        rain.update(c.copy(cosmetic = item.id))
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------- settings */

internal fun rainSettingLabel(key: String): String = when (key) {
    "maxBots" -> "Maximum bots"; "size" -> "Tornado size"; "separation" -> "Tornado separation"
    "finalAng" -> "Final angle"; "rOC" -> "Angle change rate"; "rotationDir" -> "Clockwise direction"
    "period" -> "Wigwag period"; "amplitude" -> "Wigwag amplitude"; "ranRate" -> "Random rate"
    "evolveTime" -> "Cycle time"; "evolveBots" -> "Characters per cycle"
    "panelSize" -> "Panel size"; "panelOpacity" -> "Panel opacity"; "panelBack" -> "Back"
    else -> key
}

private fun rainSettingValue(key: String, value: Double): String = when (key) {
    "rOC" -> "%.2f".format(value)
    "period", "ranRate" -> "${value.roundToInt()} ms"
    "evolveTime" -> "${value.roundToInt()} s"
    "finalAng" -> "${value.roundToInt()}°"
    "panelSize", "panelOpacity", "panelBack" -> "${(value * 100).roundToInt()}%"
    else -> value.roundToInt().toString()
}

@Composable
internal fun RainSliders(rain: SnakeyRain, keys: List<String>) {
    RainCard {
        keys.forEachIndexed { index, key ->
            val range = SnakeyRain.ranges.getValue(key)
            val value = rain.config.optDouble(key, SnakeyRain.defaults.getValue(key))
            val look = LocalRainLook.current
            if (look != null) Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(rainSettingLabel(key), fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = look.ink, modifier = Modifier.weight(1f))
                    Text(rainSettingValue(key, value), fontFamily = Wyrm.Body, fontSize = 14.sp, color = look.ink)
                }
                LiquidSlider(value.toFloat(), { rain.set(key, it.toDouble()) }, valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
                    active = look.ink, surface = look.fill.copy(alpha = LocalRainBack.current), modifier = Modifier.padding(top = 4.dp))
            } else SettingsSliderRow(
                title = rainSettingLabel(key), valueText = rainSettingValue(key, value), detail = "",
                value = value.toFloat(), range = range.start.toFloat()..range.endInclusive.toFloat(), steps = 0,
                first = index == 0,
            ) { rain.set(key, it.toDouble()) }
        }
    }
}

/**
 * The panel's Back (OM, 2026-10-14): how much of every fill shows, the plate,
 * the cards and the keys; type and borders stay whole. 1 on the Rain page.
 */
internal val LocalRainBack = androidx.compose.runtime.compositionLocalOf { 1f }
private data class RainPanelLook(val ink: Color, val fill: Color)
private val LocalRainLook = androidx.compose.runtime.compositionLocalOf<RainPanelLook?> { null }
private fun rainSwatch(index: Int, fallback: Color): Color =
    TeamHudStore.COLOUR_SWATCHES.getOrNull(index)?.takeIf { index > 0 && it != Color.Unspecified } ?: fallback

@Composable
private fun RainColourRows(rain: SnakeyRain) {
    val look = LocalRainLook.current
    val ink = look?.ink ?: Wyrm.Ink
    RainCard {
        listOf("panelTextColour" to "TEXT AND BORDER", "panelFillColour" to "FILL").forEach { (key, title) ->
            val selected = rain.config.optInt(key).coerceIn(0, 8)
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("$title · ${TeamHudStore.COLOUR_NAMES[selected].uppercase()}", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, color = ink, letterSpacing = 0.8.sp)
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TeamHudStore.COLOUR_SWATCHES.forEachIndexed { index, swatch ->
                        Box(Modifier.size(28.dp).border(if (index == selected) 2.dp else 1.dp, ink.copy(alpha = if (index == selected) 1f else 0.35f), CircleShape)
                            .clickable(onClickLabel = "$title ${TeamHudStore.COLOUR_NAMES[index]}") { rain.set(key, index.toDouble()) }
                            .padding(4.dp).clip(CircleShape).background(if (swatch == Color.Unspecified) ink else swatch))
                    }
                }
            }
        }
    }
}

@Composable
private fun RainPanelTabs(selected: Int, onSelect: (Int) -> Unit) {
    val ink = LocalRainLook.current?.ink ?: Wyrm.Ink
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(ink.copy(alpha = 0.06f)).padding(3.dp)) {
        RainPanelTab.entries.forEachIndexed { index, tab ->
            Box(Modifier.weight(1f).height(36.dp).clip(CircleShape)
                .background(if (selected == index) ink.copy(alpha = 0.14f) else Color.Transparent)
                .clickable { onSelect(index) }, contentAlignment = Alignment.Center) {
                Text(tab.title, fontFamily = Wyrm.Body, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp, color = ink, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** SettingsCard whose fill follows [LocalRainBack]; the edge stays. */
@Composable
private fun RainCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val back = LocalRainBack.current
    val look = LocalRainLook.current
    if (look == null && back >= 0.999f) { SettingsCard(content); return }
    val shape = wyrmRounded(14.dp)
    Column(
        Modifier.padding(horizontal = 16.dp).clip(shape)
            .background((look?.fill ?: Wyrm.Card).copy(alpha = back))
            .border(1.dp, (look?.ink ?: Wyrm.Ink).copy(alpha = 0.35f), shape),
        content = content,
    )
}

@Composable
private fun RainSettingsTab(rain: SnakeyRain, onArrange: () -> Unit) {
    SettingsSectionLabel("Arena panel", top = 14.dp)
    RainColourRows(rain)
    SettingsSectionLabel("Bots", top = 14.dp)
    RainSliders(rain, listOf("maxBots", "size", "separation", "finalAng", "rOC", "rotationDir"))
    SettingsSectionLabel("Movement")
    RainSliders(rain, listOf("period", "amplitude", "ranRate"))
    SettingsSectionLabel("Characters")
    SettingsCard {
        SettingsBoolRow("Cycle through characters", "Bots change character over time", rain.config.optBoolean("evolveEnabled"), first = true) { rain.flag("evolveEnabled", it) }
        SettingsBoolRow("Player rotation", "Rotate your snake with the storm", rain.config.optBoolean("rotationEnabled", true), first = false) { rain.flag("rotationEnabled", it) }
    }
    RainSliders(rain, listOf("evolveTime", "evolveBots").takeIf { rain.config.optBoolean("evolveEnabled") } ?: emptyList())
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
        PaperPrimaryButton(label = "Arrange arena UI", onClick = onArrange)
        Spacer(Modifier.height(9.dp))
        PaperOutlineButton(label = "Reset settings") { rain.reset() }
    }
    SettingsCaption(
        if (rain.enabled) "The Rain button sits with your on-screen buttons. Drag it, or touch and hold it for size and opacity."
        else "Turn on Snakey Rain to place its button in the arena."
    )
}

/* ------------------------------------------------------------------ login */

@Composable
private fun RainLoginTab(rain: SnakeyRain) {
    var personal by remember(rain.personal) { mutableStateOf(rain.personal) }
    var user by remember(rain.personal) { mutableStateOf(if (rain.personal) rain.username else SnakeyRain.PUBLIC_USER) }
    var pass by remember(rain.personal) { mutableStateOf(if (rain.personal) rain.password else SnakeyRain.PUBLIC_PASSWORD) }
    SettingsSectionLabel("Account", top = 14.dp)
    SettingsCard {
        SettingsBoolRow("Use my personal account", if (personal) "Sign in with your own Rain account" else "The public account is signed in", personal, first = true) { on ->
            personal = on
            if (on) { user = ""; pass = "" }
            else {
                user = SnakeyRain.PUBLIC_USER; pass = SnakeyRain.PUBLIC_PASSWORD
                if (rain.personal) rain.credentials("", "", false)
            }
        }
        RainFieldRow("Username", user, first = false, enabled = personal, placeholder = "Username") { user = it.take(128) }
        RainFieldRow("Password", pass, first = false, enabled = personal, secret = true, placeholder = "Password") { pass = it.take(256) }
    }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.padding(horizontal = 16.dp)) {
        PaperPrimaryButton("Connect", enabled = !personal || (user.isNotBlank() && pass.isNotEmpty())) {
            rain.credentials(user, pass, personal); rain.enable(true)
        }
    }
    SettingsCaption("Your password stays on this phone, locked. The public account is shared by everyone.")
}

/* ------------------------------------------------------------ arena panel */

private enum class RainPanelTab(val title: String) { CONTROLS("Controls"), MODES("Modes"), TARGET("Target"), SETTINGS("Settings") }

@Composable
private fun RainStepButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val ink = LocalRainLook.current?.ink ?: Wyrm.Ink
    val fill = LocalRainLook.current?.fill ?: Wyrm.Paper
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.size(36.dp).scale(pressScale(pressed, enabled)).clip(CircleShape)
            .background(fill.copy(alpha = LocalRainBack.current)).border(1.dp, ink.copy(alpha = if (enabled) 0.8f else 0.3f), CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), label, tint = ink, modifier = Modifier.size(18.dp))
    }
}

/** A key as the arena draws its own: paper, an ink edge, ink and a green ring while it is on. */
@Composable
private fun RainKey(label: String, lit: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = LocalRainLook.current?.ink ?: Wyrm.Ink
    val fill = LocalRainLook.current?.fill ?: Wyrm.Paper
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(14.dp)
    val back = LocalRainBack.current

    Box(
        modifier
            .heightIn(min = 46.dp)
            .scale(pressScale(pressed))
            .padding(3.dp)
            .clip(shape)
            .background(fill.copy(alpha = back))
            .border(if (lit || pressed) 2.5.dp else 1.5.dp, if (lit || pressed) Wyrm.Live else ink.copy(alpha = 0.6f), shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
            color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RainKeyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

/**
 * The arena's Rain panel (OM, 2026-10-14): opened by the Rain key, tabs for
 * the controls, modes, nearby snakes and live settings. Its size, opacity
 * and back plate are the player's ("panelSize", "panelOpacity", "panelBack").
 */
@Composable
internal fun RainArenaPanel(rain: SnakeyRain, onClose: () -> Unit, modifier: Modifier = Modifier) {
    // Panel size scales the whole panel, type and keys too, not only its box (OM, 2026-10-14).
    val requested = rain.config.optDouble("panelSize", 1.0).toFloat().coerceIn(0.7f, 1.3f)
    val base = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = minOf(requested, maxWidth.value / 428f, maxHeight.value / 388f).coerceAtLeast(0.01f)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * scale, base.fontScale),
            LocalRainBack provides rain.config.optDouble("panelBack", 0.94).toFloat().coerceIn(0f, 1f),
            LocalRainLook provides RainPanelLook(
                rainSwatch(rain.config.optInt("panelTextColour"), Wyrm.Ink),
                rainSwatch(rain.config.optInt("panelFillColour"), Wyrm.Paper)) ,
        ) { RainArenaPanelBody(rain, onClose) }
    }
}

@Composable
private fun RainArenaPanelBody(rain: SnakeyRain, onClose: () -> Unit) {
    val ink = LocalRainLook.current?.ink ?: Wyrm.Ink
    val fill = LocalRainLook.current?.fill ?: Wyrm.Paper
    var tab by remember { mutableIntStateOf(0) }
    val opacity = rain.config.optDouble("panelOpacity", 1.0).toFloat().coerceIn(0.3f, 1f)
    val back = rain.config.optDouble("panelBack", 0.94).toFloat().coerceIn(0f, 1f)
    val shape = wyrmRounded(22.dp)
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = opacity
                clip = false
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .padding(24.dp)
            .then(if (back > 0.05f) Modifier.shadow(18.dp * back, shape, clip = false, ambientColor = ink.copy(alpha = 0.12f), spotColor = ink.copy(alpha = 0.12f)) else Modifier)
            .clip(shape)
            .background(fill.copy(alpha = back))
            .border(1.dp, ink.copy(alpha = 0.35f), shape),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Snakey Rain", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink)
                RainStatusLine(rain, Modifier.padding(top = 2.dp), ink)
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(fill.copy(alpha = back))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { IosIcon(IosGlyph.XMARK, ink, size = 13.dp, weight = 2.6f) }
        }
        Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
            RainPanelTabs(tab) { tab = it }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 10.dp)) {
            when (RainPanelTab.entries[tab]) {
                RainPanelTab.CONTROLS -> {
                    RainKeyRow {
                        RainKey(if (rain.running) "Stop" else "Start", rain.running, Modifier.weight(1f)) { if (rain.running) rain.stop() else rain.start() }
                        RainKey("Boost", rain.boosting, Modifier.weight(1f)) { rain.boost() }
                        RainKey("Reconnect", false, Modifier.weight(1f)) { rain.disconnect(); rain.connect() }
                    }
                    if (rain.config.optBoolean("rotationEnabled", true)) RainKeyRow {
                        RainKey("Rotate left", rain.rotation == -1, Modifier.weight(1f)) { rain.rotate(if (rain.rotation == -1) 0 else -1) }
                        RainKey("Rotate right", rain.rotation == 1, Modifier.weight(1f)) { rain.rotate(if (rain.rotation == 1) 0 else 1) }
                    }
                    Text(
                        "Mode · ${rain.modes.getOrElse(rain.mode - 1) { "" }}", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = ink,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
                RainPanelTab.MODES -> rain.modes.chunked(2).forEachIndexed { row, pair ->
                    RainKeyRow {
                        pair.forEachIndexed { col, name ->
                            val id = row * 2 + col + 1
                            RainKey(name, rain.mode == id, Modifier.weight(1f)) { rain.mode(id) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                RainPanelTab.TARGET -> {
                    // My own bots crowd me; they carry my characters' in-game names (OM, 2026-10-14).
                    val mine = rain.characters.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                    // Nearby snakes refresh only while this tab is open, so the panel
                    // does not redraw ten times a second over the game (OM, 2026-10-14).
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        rain.liveTargets = true
                        onDispose { rain.liveTargets = false }
                    }
                    val all = rain.snapshot.optJSONArray("targets")
                    val shownTargets = (0 until (all?.length() ?: 0)).mapNotNull { all?.optJSONObject(it) }
                        .filter { it.optString("name").trim().lowercase() !in mine }
                    val count = shownTargets.size
                    if (count == 0) {
                        Text(
                            "No snakes nearby", fontFamily = Wyrm.Body, fontSize = 13.sp, color = ink,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    shownTargets.forEach { t ->
                        val name = t.optString("name").trim().ifEmpty { "Unnamed snake" }
                        RainKeyRow {
                            RainKey(name, false, Modifier.weight(1f)) { rain.target(t.optInt("id"), t.optDouble("x"), t.optDouble("y")) }
                        }
                    }
                }
                RainPanelTab.SETTINGS -> {
                    Spacer(Modifier.height(6.dp))
                    RainCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Panel size", fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = ink, modifier = Modifier.weight(1f))
                            val size = rain.config.optDouble("panelSize", 1.0)
                            RainStepButton(com.composables.icons.lucide.R.drawable.lucide_ic_minus, "Smaller", size > 0.71) { rain.set("panelSize", size - 0.1) }
                            Text("${(size * 100).roundToInt()}%", fontFamily = Wyrm.Body, fontSize = 14.sp, color = ink,
                                modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            RainStepButton(com.composables.icons.lucide.R.drawable.lucide_ic_plus, "Bigger", size < 1.29) { rain.set("panelSize", size + 0.1) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    RainColourRows(rain)
                    Spacer(Modifier.height(10.dp))
                    RainSliders(rain, listOf("panelOpacity", "panelBack"))
                    Spacer(Modifier.height(10.dp))
                    RainSliders(rain, listOf("maxBots", "size", "separation", "finalAng", "rOC", "rotationDir", "period", "amplitude", "ranRate"))
                    Spacer(Modifier.height(10.dp))
                    RainCard {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cycle through characters", fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = ink, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(checked = rain.config.optBoolean("evolveEnabled"), onCheckedChange = { rain.flag("evolveEnabled", it) },
                                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = ink, checkedThumbColor = fill))
                        }
                    }
                }
            }
        }
        // The knob at the bottom centre drags the panel anywhere (OM, 2026-10-14).
        Box(
            // RainPanelTouchFrame owns this handle in stationary screen coordinates.
            Modifier.fillMaxWidth().height(26.dp),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(42.dp, 5.dp).clip(CircleShape).background(ink.copy(alpha = 0.32f))) }
    }
}
