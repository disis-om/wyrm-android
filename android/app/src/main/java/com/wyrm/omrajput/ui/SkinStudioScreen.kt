package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/*
 * Skin Studio (OM, 2026-10-12): the Skin tab's default page. A stage where
 * the snake being made slithers, a shelf of the player's snakes, and three
 * workshops: Snake (the bead pattern), Beads (the bead library; each opens
 * the bead editor) and Eyes (opens the eye editor). "Default editor" opens
 * the classic Skin page. See StudioModel.kt for what is stored and worn.
 */

private enum class StudioTab(val title: String) { SNAKE("Snake"), BEADS("Beads"), EYES("Eyes") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SkinStudioScreen(
    insetTop: Dp,
    insetBottom: Dp,
    onOpenClassic: () -> Unit,
    onWear: (String) -> Unit,
    onTakeOff: () -> Unit,
) {
    val textures by rememberSkinTextures()
    val beads = SkinStudio.beads
    val skins = SkinStudio.skins
    val skin = SkinStudio.skin(SkinStudio.selected) ?: skins.firstOrNull()
    @Suppress("UNUSED_VARIABLE") val paintRevision = SkinStudio.paintRevision
    val image: (String) -> ImageBitmap? = { id -> SkinStudio.bead(id)?.let { StudioArt.beadImage(it, textures) } }

    var tab by remember { mutableStateOf(StudioTab.SNAKE) }
    var editingBead by remember { mutableStateOf<String?>(null) }
    /** A bead opened from the Snake tab goes on the snake when it is done. */
    var appendAfterEdit by remember { mutableStateOf(false) }
    /** The bead in the editor was made just now: closing with X removes it again. */
    var freshBead by remember { mutableStateOf(false) }
    var editingEyes by remember { mutableStateOf(false) }
    var skinMenu by remember { mutableStateOf<String?>(null) }
    var beadMenu by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }

    // The phone's Back (OM, 2026-10-13): an open editor closes as its X does,
    // a sheet or the rename dialog closes without acting.
    androidx.activity.compose.BackHandler(enabled = skinMenu != null || beadMenu != null || renaming != null) {
        skinMenu = null
        beadMenu = null
        renaming = null
    }
    androidx.activity.compose.BackHandler(enabled = editingEyes) { editingEyes = false }
    androidx.activity.compose.BackHandler(enabled = editingBead != null) {
        val id = editingBead
        if (id != null && freshBead) SkinStudio.deleteBead(id)
        appendAfterEdit = false
        freshBead = false
        editingBead = null
    }

    Box(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        Column(Modifier.fillMaxSize().padding(top = insetTop)) {
            IosScreenHeader(kicker = "WYRM", title = "Skin Studio") {
                StudioCapsule("Default skin editor", icon = LucideR.drawable.lucide_ic_shirt, filled = false, onClick = onOpenClassic)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (skin != null) {
                    StudioStage(
                        skin = skin,
                        image = image,
                        worn = SkinStudio.worn == skin.id,
                        onWear = { onWear(skin.id) },
                        onTakeOff = onTakeOff,
                        onRename = { renaming = skin.id },
                    )
                }
                // The shelf: every snake, then a new one.
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                ) {
                    item(key = "new") {
                        ShelfCard(selected = false, onClick = { SkinStudio.newSkin(); tab = StudioTab.SNAKE }, onLongClick = {}) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(LucideR.drawable.lucide_ic_plus), null, tint = Wyrm.Ink, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("New snake", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Wyrm.Ink)
                            }
                        }
                    }
                    items(skins, key = { it.id }) { item ->
                        ShelfCard(
                            selected = item.id == skin?.id,
                            onClick = { SkinStudio.select(item.id) },
                            onLongClick = { skinMenu = item.id },
                        ) {
                            Column(Modifier.fillMaxSize().padding(8.dp)) {
                                Canvas(Modifier.fillMaxWidth().weight(1f)) { drawStudioStrip(item, image) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                        color = Wyrm.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                                    if (SkinStudio.worn == item.id) {
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(Wyrm.Live))
                                    }
                                }
                            }
                        }
                    }
                }
                StudioCaption("Touch and hold a snake to rename, copy or delete it.", Modifier.padding(bottom = 12.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    PaperSegmented(options = StudioTab.entries.map { it.title }, selected = tab.ordinal, onSelect = { tab = StudioTab.entries[it] })
                }
                Spacer(Modifier.height(14.dp))
                if (notice.isNotEmpty()) {
                    Text(notice, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Badge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    LaunchedEffect(notice) { kotlinx.coroutines.delay(2600); notice = "" }
                }
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(iosSpring(0.4f, 0.86f)) + scaleIn(iosSpring(0.4f, 0.86f), initialScale = 0.985f)) togetherWith
                            fadeOut(iosSpring(0.3f, 1f))
                    },
                    label = "studio-tab",
                ) { shown ->
                    Column {
                        when (shown) {
                            StudioTab.SNAKE -> if (skin != null) PatternWorkshop(
                                skin = skin,
                                beads = beads,
                                image = image,
                                onTooMany = { notice = "A snake can wear ${SkinStudio.MAX_DISTINCT} different beads." },
                                onNewBead = {
                                    val bead = SkinStudio.newBead()
                                    appendAfterEdit = true
                                    freshBead = true
                                    editingBead = bead.id
                                },
                            )
                            StudioTab.BEADS -> BeadWorkshop(
                                beads = beads,
                                image = image,
                                textures = textures,
                                onOpen = { appendAfterEdit = false; freshBead = false; editingBead = it },
                                onLongPress = { beadMenu = it },
                                onNew = {
                                    val bead = SkinStudio.newBead()
                                    appendAfterEdit = false
                                    freshBead = true
                                    editingBead = bead.id
                                },
                            )
                            StudioTab.EYES -> if (skin != null) EyeWorkshop(skin, image, onOpenEditor = { editingEyes = true })
                        }
                    }
                }
                Spacer(Modifier.height(LocalRootTabClearance.current.coerceAtLeast(108.dp)))
            }
        }

        // The editors rise over the whole page.
        AnimatedVisibility(
            visible = editingBead != null,
            enter = slideInVertically(iosSpring(0.46f, 0.88f)) { it / 3 } + fadeIn(iosSpring(0.4f, 1f)),
            exit = slideOutVertically(iosSpring(0.36f, 1f)) { it / 3 } + fadeOut(iosSpring(0.3f, 1f)),
        ) {
            // The last bead opened stays on screen while the editor slides away.
            var shownBead by remember { mutableStateOf<String?>(null) }
            editingBead?.let { shownBead = it }
            val id = shownBead
            if (id != null) {
                StudioBeadEditor(
                    beadId = id,
                    skin = skin,
                    insetTop = insetTop,
                    insetBottom = insetBottom,
                    onDone = { saved ->
                        if (saved && appendAfterEdit && skin != null && SkinStudio.bead(id) != null) {
                            if (!SkinStudio.setPattern(skin.id, skin.pattern + id)) notice = "A snake can wear ${SkinStudio.MAX_DISTINCT} different beads."
                        }
                        if (!saved && freshBead) SkinStudio.deleteBead(id)
                        appendAfterEdit = false
                        freshBead = false
                        editingBead = null
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = editingEyes && skin != null,
            enter = slideInVertically(iosSpring(0.46f, 0.88f)) { it / 3 } + fadeIn(iosSpring(0.4f, 1f)),
            exit = slideOutVertically(iosSpring(0.36f, 1f)) { it / 3 } + fadeOut(iosSpring(0.3f, 1f)),
        ) {
            if (skin != null) StudioEyeEditor(skin, image, insetTop, insetBottom, onDone = { editingEyes = false })
        }

        skinMenu?.let { id ->
            val item = SkinStudio.skin(id)
            if (item != null) {
                StudioActionSheet(
                    title = item.name,
                    subtitle = "${item.pattern.size} beads · ${item.eyes.size} eyes",
                    insetBottom = insetBottom,
                    actions = listOf(
                        StudioAction("Rename", LucideR.drawable.lucide_ic_pencil) { renaming = id },
                        StudioAction("Duplicate", LucideR.drawable.lucide_ic_copy) { SkinStudio.duplicateSkin(id) },
                        StudioAction("Delete", LucideR.drawable.lucide_ic_trash_2, destructive = true) { SkinStudio.deleteSkin(id) },
                    ),
                    onDismiss = { skinMenu = null },
                )
            } else skinMenu = null
        }
        beadMenu?.let { id ->
            StudioActionSheet(
                title = "Bead",
                subtitle = StudioArt.FINISHES.getOrElse(SkinStudio.bead(id)?.finish ?: 0) { "" },
                insetBottom = insetBottom,
                actions = listOf(
                    StudioAction("Edit", LucideR.drawable.lucide_ic_paintbrush) { appendAfterEdit = false; freshBead = false; editingBead = id },
                    StudioAction("Duplicate", LucideR.drawable.lucide_ic_copy) { SkinStudio.bead(id)?.let { SkinStudio.newBead(it) } },
                    StudioAction("Delete", LucideR.drawable.lucide_ic_trash_2, destructive = true) {
                        if (!SkinStudio.deleteBead(id)) notice = "This bead is on a snake. Take it off the snake first."
                    },
                ),
                onDismiss = { beadMenu = null },
            )
        }
        renaming?.let { id ->
            StudioRenameSheet(SkinStudio.skin(id)?.name.orEmpty(), insetBottom, onDone = { name ->
                if (name != null) SkinStudio.renameSkin(id, name)
                renaming = null
            })
        }
    }
}

/* ------------------------------------------------------------ the stage */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioStage(
    skin: StudioSkin,
    image: (String) -> ImageBitmap?,
    worn: Boolean,
    onWear: () -> Unit,
    onTakeOff: () -> Unit,
    onRename: () -> Unit,
) {
    var time by remember { mutableFloatStateOf(0f) }
    var boost by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                    time += dt * (1f + boost * 1.6f)
                    boost = (boost - dt * 0.9f).coerceAtLeast(0f)
                }
                last = now
            }
        }
    }
    // The stage's light takes the snake's own colour.
    val glow = remember(skin.pattern.firstOrNull(), SkinStudio.beads) {
        val first = skin.pattern.firstOrNull()?.let { image(it) }
        Color(0xFF000000.toInt() or (first?.let { StudioArt.averageRgb(it.asAndroidBitmap()) } ?: 0x3FEE96))
    }
    val appear = remember(skin.id) { Animatable(0f) }
    LaunchedEffect(skin.id) { appear.snapTo(0f); appear.animateTo(1f, iosSpring(0.6f, 0.82f)) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(horizontal = 16.dp)
            .clip(wyrmRounded(26.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(26.dp))
            .pointerInput(Unit) { detectTapGestures { boost = 1f } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value; translationX = (1f - appear.value) * -60f }) {
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f + 0.15f * boost), Color.Transparent),
                center = Offset(size.width * 0.6f, size.height * 0.48f), radius = size.width * 0.62f))
            val bead = size.height * 0.2f
            val step = bead * STUDIO_BEAD_STEP
            val count = ((size.width + bead * 2) / step).toInt().coerceIn(8, 256)
            val head = Offset(size.width * 0.82f, size.height * 0.48f)
            val wave = bead * 0.75f
            // The wave per bead follows the bead spacing, so the body bends as before.
            val k = 0.32f * STUDIO_BEAD_STEP / 0.36f
            val place = { i: Int ->
                val x = head.x - i * step
                val y = head.y + sin(time * 3.2f - i * k) * wave * (0.25f + 0.75f * (i / 30f).coerceAtMost(1f))
                Offset(x, y)
            }
            val look = Offset(1.4f + 0.6f * cos(time * 0.9f), 1.8f * sin(time * 0.7f))
            drawStudioSnake(skin, count, bead, place, image, look = look)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp)) {
            Text(if (worn) "WORN" else "STUDIO SNAKE", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp,
                letterSpacing = 1.2.sp, color = if (worn) Wyrm.Live else Wyrm.Quiet)
            Text(skin.name, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Wyrm.Ink,
                modifier = Modifier.combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = onRename))
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (worn) StudioCapsule("Take off", filled = false, onClick = onTakeOff)
            else StudioCapsule("Wear", icon = LucideR.drawable.lucide_ic_sparkles, onClick = onWear)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(132.dp)
            .height(78.dp)
            .clip(wyrmRounded(18.dp))
            .background(Wyrm.Card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, wyrmRounded(18.dp))
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/* ------------------------------------------------------------ Snake: the pattern */

@Composable
private fun PatternWorkshop(
    skin: StudioSkin,
    beads: List<StudioBead>,
    image: (String) -> ImageBitmap?,
    onTooMany: () -> Unit,
    onNewBead: () -> Unit,
) {
    val history = remember(skin.id) { mutableStateListOf<List<String>>() }
    var selected by remember(skin.id) { mutableStateOf<Int?>(null) }
    val pattern = skin.pattern
    fun change(next: List<String>) {
        if (next == pattern) return
        val before = pattern
        if (SkinStudio.setPattern(skin.id, next)) {
            history.add(before)
            if (history.size > 40) history.removeAt(0)
        } else onTooMany()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(pattern.size) { if (selected == null && pattern.isNotEmpty()) listState.animateScrollToItem(pattern.size - 1) }

    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${pattern.size} / ${SkinStudio.MAX_PATTERN} beads · ${pattern.distinct().size} kinds",
            fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Wyrm.Quiet, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudioRoundButton(LucideR.drawable.lucide_ic_undo_2, enabled = history.isNotEmpty(), size = 34.dp) {
                val last = history.removeAt(history.lastIndex)
                SkinStudio.setPattern(skin.id, last)
            }
            StudioRoundButton(LucideR.drawable.lucide_ic_repeat_2, enabled = pattern.size * 2 <= SkinStudio.MAX_PATTERN, size = 34.dp) {
                change(pattern + pattern)
            }
            StudioRoundButton(LucideR.drawable.lucide_ic_arrow_left_right, enabled = pattern.size > 1, size = 34.dp) {
                change(pattern.reversed())
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    LazyRow(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().height(62.dp),
    ) {
        itemsIndexed(pattern) { index, id ->
            val chosen = selected == index
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(if (chosen) 2.5.dp else 0.dp, if (chosen) Wyrm.Ink else Color.Transparent, CircleShape)
                        .clickableNoRipple { selected = if (chosen) null else index },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(38.dp)) { image(id)?.let { drawImageInto(it, 0f, 0f, size.width, size.height) } }
                }
                Text("${index + 1}", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 8.5.sp,
                    color = if (chosen) Wyrm.Ink else Wyrm.Quiet)
            }
        }
    }
    val pick = selected
    AnimatedVisibility(visible = pick != null) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val at = pick ?: 0
            StudioCapsule("Remove", icon = LucideR.drawable.lucide_ic_trash_2, filled = false, enabled = pattern.size > 1) {
                change(pattern.filterIndexed { i, _ -> i != at }); selected = null
            }
            StudioCapsule("Copy", icon = LucideR.drawable.lucide_ic_copy, filled = false, enabled = pattern.size < SkinStudio.MAX_PATTERN) {
                change(pattern.take(at + 1) + pattern[at] + pattern.drop(at + 1))
            }
            StudioCapsule("◀", filled = false, enabled = at > 0) {
                val next = pattern.toMutableList(); val v = next.removeAt(at); next.add(at - 1, v); change(next); selected = at - 1
            }
            StudioCapsule("▶", filled = false, enabled = at < pattern.lastIndex) {
                val next = pattern.toMutableList(); val v = next.removeAt(at); next.add(at + 1, v); change(next); selected = at + 1
            }
        }
    }
    StudioCaption("Tap a bead in the snake to remove, copy or move it.", Modifier.padding(top = 4.dp))
    IosSectionLabel("Tap a bead to add it")
    TileGrid(minimum = 54.dp, count = beads.size + 1, fixedColumns = 6) { index ->
        if (index == 0) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Wyrm.Card).border(1.dp, Wyrm.Rule, CircleShape).clickableNoRipple(onNewBead),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(LucideR.drawable.lucide_ic_plus), null, tint = Wyrm.Ink, modifier = Modifier.size(20.dp)) }
        } else {
            val bead = beads[index - 1]
            Canvas(Modifier.fillMaxSize().clickableNoRipple {
                if (pattern.size < SkinStudio.MAX_PATTERN) change(pattern + bead.id)
            }) { image(bead.id)?.let { drawImageInto(it, 0f, 0f, size.width, size.height) } }
        }
    }
}

/* ------------------------------------------------------------ Beads: the library */

@Composable
private fun BeadWorkshop(
    beads: List<StudioBead>,
    image: (String) -> ImageBitmap?,
    textures: SkinTextures?,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onNew: () -> Unit,
) {
    IosSectionLabel("Your beads")
    StudioCaption("Tap a bead to edit it. Touch and hold a bead to copy or delete it.", Modifier.padding(bottom = 8.dp))
    TileGrid(minimum = 64.dp, count = beads.size + 1, fixedColumns = 5) { index ->
        if (index == 0) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Wyrm.Card).border(1.dp, Wyrm.Rule, CircleShape).clickableNoRipple(onNew),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(LucideR.drawable.lucide_ic_plus), null, tint = Wyrm.Ink, modifier = Modifier.size(22.dp)) }
        } else {
            val bead = beads[index - 1]
            BeadTile(bead.id, image, onClick = { onOpen(bead.id) }, onLongClick = { onLongPress(bead.id) })
        }
    }
    Spacer(Modifier.height(18.dp))
    IosSectionLabel("Start from a default skin")
    // slither's presets: each becomes Studio beads in the Slither finish and a new snake.
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(SkinCatalog.presets) { index, groups ->
            Column(
                Modifier
                    .width(150.dp)
                    .clip(wyrmRounded(16.dp))
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(16.dp))
                    .clickableNoRipple { studioFromPreset(index, groups) }
                    .padding(10.dp),
            ) {
                MiniSnake(textures, groups, Modifier.fillMaxWidth().height(30.dp))
                Text("Preset %02d".format(index + 1), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                    color = Wyrm.Quiet, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BeadTile(id: String, image: (String) -> ImageBitmap?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Canvas(
        Modifier
            .fillMaxSize()
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = onClick, onLongClick = onLongClick),
    ) { image(id)?.let { drawImageInto(it, 0f, 0f, size.width, size.height) } }
}

/** A slither preset as Studio beads (one per colour) and a new snake wearing them. */
private fun studioFromPreset(index: Int, groups: List<Int>) {
    if (groups.isEmpty()) return
    val ids = HashMap<Int, String>()
    val pattern = groups.take(SkinStudio.MAX_PATTERN).map { group ->
        ids.getOrPut(group) {
            SkinStudio.newBead(StudioBead(id = "", color = 0xFF000000.toInt() or AirSkin.groupRgb(group), finish = StudioArt.FINISH_SLITHER)).id
        }
    }
    SkinStudio.newSkin("Preset %02d".format(index + 1), pattern)
}

/* ------------------------------------------------------------ Eyes */

@Composable
private fun EyeWorkshop(skin: StudioSkin, image: (String) -> ImageBitmap?, onOpenEditor: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(150.dp)
            .clip(wyrmRounded(22.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(22.dp))
            .clickableNoRipple(onOpenEditor),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bead = size.height * 0.62f
            val head = Offset(size.width * 0.62f, size.height * 0.5f)
            drawStudioSnake(skin, 30, bead, place = { i -> Offset(head.x - i * bead * STUDIO_BEAD_STEP, head.y) }, image = image, shadow = false)
        }
        StudioCapsule("Edit eyes", icon = LucideR.drawable.lucide_ic_eye, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), onClick = onOpenEditor)
    }
    IosSectionLabel("Quick eyes")
    val presets = remember {
        listOf(
            "Classic" to StudioEye.pair(),
            "Cyclops" to listOf(StudioEye(fwd = 5f, side = 0f, white = 8.5f, pupil = 4.5f)),
            "Three" to listOf(StudioEye(side = -7f, white = 5f, pupil = 3f), StudioEye(fwd = 9f, side = 0f, white = 4.5f, pupil = 2.8f), StudioEye(side = 7f, white = 5f, pupil = 3f)),
            "Spider" to listOf(-8f, -3.5f, 3.5f, 8f).flatMap { s -> listOf(StudioEye(fwd = 8f, side = s, white = 3f, pupil = 2f), StudioEye(fwd = 2.5f, side = s * 0.9f, white = 2.6f, pupil = 1.7f)) }.take(8),
            "Cat" to StudioEye.pair().map { it.copy(pupilShape = StudioArt.PUPIL_CAT, whiteColor = 0xFFC8F55A.toInt()) },
            "Hearts" to StudioEye.pair().map { it.copy(pupilShape = 4, pupil = 4.2f) },
            "Stars" to StudioEye.pair().map { it.copy(pupilShape = 5, pupil = 4.4f, whiteColor = 0xFF1A1A40.toInt(), pupilColor = 0xFFFFE15A.toInt()) },
            "None" to emptyList(),
        )
    }
    StudioCaption("Tap one to give this snake those eyes.", Modifier.padding(bottom = 8.dp))
    val head = skin.pattern.firstOrNull()?.let { image(it) }
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(presets) { (name, eyes) ->
            val chosen = skin.eyes == eyes
            // The snake's own head bead wearing those eyes.
            Box(
                Modifier
                    .size(76.dp)
                    .clip(wyrmRounded(20.dp))
                    .background(Wyrm.Card)
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) Wyrm.Ink else Wyrm.Rule, wyrmRounded(20.dp))
                    .clickableNoRipple { SkinStudio.setEyes(skin.id, eyes) }
                    .semantics { contentDescription = name },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val bead = size.minDimension * 0.62f
                    val centre = Offset(size.width * 0.46f, size.height * 0.5f)
                    head?.let { drawImageInto(it, centre.x - bead / 2, centre.y - bead / 2, bead, bead) }
                    drawStudioEyes(eyes, centre, 0f, bead / 29f, Offset(1f, 0f))
                }
            }
        }
    }
}

/* ------------------------------------------------------------ small pieces */

internal fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) })

@Composable
internal fun StudioRenameSheet(start: String, insetBottom: Dp, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(start, androidx.compose.ui.text.TextRange(start.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); runCatching { focus.requestFocus() } }
    StudioDialog("Name this snake", onDismiss = { onDone(null) }) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.copy(text = it.text.take(28)) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Wyrm.Ink),
            cursorBrush = SolidColor(Wyrm.Link),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (text.text.isNotBlank()) onDone(text.text) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .clip(wyrmRounded(14.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
                .padding(14.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            StudioCapsule("Cancel", filled = false) { onDone(null) }
            StudioCapsule("Save", icon = LucideR.drawable.lucide_ic_check, enabled = text.text.isNotBlank()) { onDone(text.text) }
        }
    }
}
