package com.wyrm.omrajput.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Wyrm skins (OM, 2026-10-13): creatures and strip looks the engine draws
 * over a snake instead of its beads (game/wyrm_skins.c). The number is what
 * travels; never renumber, only append. Pictures: textures/wyrm_skins.webp
 * (tools/wyrm-skins/make.py), one cell per skin in [all]'s order.
 *
 * The worn skin is the engine setting `skin.wyrm` (0 = none), so it is saved
 * with the engine's settings and goes to the account (shared key `wyrmSkin`).
 * While one is worn the arena gets no tag and no accessory: the skin's number
 * rides where a tag would.
 */
internal data class WyrmSkin(val number: Int, val name: String) {
    val strip: Boolean get() = number > 100
}

internal object WyrmSkins {
    const val SHEET_COLUMNS = 6
    const val SHEET_ROWS = 7
    /** The moving preview: frames in one sheet per skin (tools/wyrm-skins/make.py --live). */
    const val LIVE_FRAMES = 32
    const val LIVE_COLUMNS = 8
    const val LIVE_ROWS = 4
    const val LIVE_MILLIS = 4000

    val creatures = listOf(
        WyrmSkin(1, "Centipede"), WyrmSkin(2, "Dragon"), WyrmSkin(3, "Skeleton"),
        WyrmSkin(4, "Chinese Dragon"), WyrmSkin(5, "Electric Eel"), WyrmSkin(6, "Train"),
        WyrmSkin(7, "Robot Snake"), WyrmSkin(8, "Phoenix"), WyrmSkin(9, "Ice Serpent"),
        WyrmSkin(10, "Caterpillar"), WyrmSkin(11, "Zombie Snake"), WyrmSkin(12, "Cyclops"),
        WyrmSkin(13, "Evil Eye"), WyrmSkin(14, "Argus"), WyrmSkin(15, "Spider Eyes"),
        WyrmSkin(16, "Mega Eye"), WyrmSkin(17, "Bean"), WyrmSkin(18, "Owl"),
        WyrmSkin(19, "Third Eye"), WyrmSkin(20, "Snail"), WyrmSkin(21, "Mantis"),
        WyrmSkin(22, "Eyeball Chain"), WyrmSkin(23, "Jelly"),
    )
    val strips = listOf(
        WyrmSkin(101, "Strip"), WyrmSkin(102, "Neon Strip"), WyrmSkin(103, "Glass Strip"),
        WyrmSkin(104, "Outline Strip"), WyrmSkin(105, "Pixel Strip"), WyrmSkin(106, "Rainbow Strip"),
        WyrmSkin(107, "Candy Strip"), WyrmSkin(108, "Tube Strip"), WyrmSkin(109, "Chrome Strip"),
        WyrmSkin(110, "Lava Strip"), WyrmSkin(111, "Ice Strip"), WyrmSkin(112, "Galaxy Strip"),
        WyrmSkin(113, "Ghost Strip"), WyrmSkin(114, "Bead Strip"), WyrmSkin(115, "Zigzag Strip"),
    )
    /** The sheet's order. */
    val all = creatures + strips

    fun byNumber(number: Int): WyrmSkin? = all.firstOrNull { it.number == number }
}

/**
 * The worn skin, moving gently and slowly on the Skin page's preview (OM,
 * 2026-10-13): its frames in turn, each blended into the next. Until the
 * frames are in, the still picture.
 */
@Composable
internal fun WyrmSkinLive(number: Int, still: ImageBitmap?, modifier: Modifier = Modifier) {
    val sheet by rememberWyrmSkinFrames(number)
    val clock = rememberInfiniteTransition(label = "wyrm-skin-live")
    val phase by clock.animateFloat(
        initialValue = 0f, targetValue = WyrmSkins.LIVE_FRAMES.toFloat(),
        animationSpec = infiniteRepeatable(tween(WyrmSkins.LIVE_MILLIS, easing = LinearEasing)),
        label = "wyrm-skin-frame",
    )
    Canvas(modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        val s = sheet
        if (s == null) {
            if (still != null) drawFitted(still, null)
            return@Canvas
        }
        val cw = s.width / WyrmSkins.LIVE_COLUMNS
        val ch = s.height / WyrmSkins.LIVE_ROWS
        val scale = min(size.width / cw, size.height / ch)
        val w = (cw * scale).roundToInt()
        val h = (ch * scale).roundToInt()
        val at = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt())
        val f0 = phase.toInt().coerceIn(0, WyrmSkins.LIVE_FRAMES - 1)
        val f1 = (f0 + 1) % WyrmSkins.LIVE_FRAMES
        val mix = (phase - f0).coerceIn(0f, 1f)
        fun frame(f: Int, alpha: Float) = drawImage(
            s, srcOffset = IntOffset((f % WyrmSkins.LIVE_COLUMNS) * cw, (f / WyrmSkins.LIVE_COLUMNS) * ch),
            srcSize = IntSize(cw, ch), dstOffset = at, dstSize = IntSize(w, h), alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
        frame(f0, 1f)
        if (mix > 0.01f) frame(f1, mix)
    }
}

@Composable
internal fun WyrmSkinTile(selected: Boolean, label: String, image: ImageBitmap?, onClick: () -> Unit) {
    val shape = wyrmRounded(15.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Wyrm.Card.copy(alpha = 0.92f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (image != null) Canvas(Modifier.fillMaxSize()) { drawFitted(image, null) }
            else Text("NONE", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.7.sp, color = Wyrm.Ink)
            if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd))
        }
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
            color = if (selected) Wyrm.Ink else Wyrm.Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WyrmSkinsCaption(text: String) {
    Text(text, fontFamily = Wyrm.Body, fontSize = 12.5.sp, lineHeight = 18.sp, color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp))
}

/**
 * Tapping Tags, Accessories or Wyrm accessories while a Wyrm skin is worn
 * (OM, 2026-10-13): which skin is on, that the tapped thing stays off with
 * it, and the two ways out.
 */
@Composable
internal fun WyrmSkinBlockedPopup(
    skinName: String,
    strip: Boolean,
    what: String,
    onDefault: () -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.38f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(wyrmRounded(22.dp))
                .background(Wyrm.Card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$skinName is on", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Wyrm.Ink)
            Text(
                "You are wearing the ${if (strip) "strip look" else "creature skin"} $skinName, so $what stay off. " +
                    "To use them, choose a default skin or make a custom one.",
                fontFamily = Wyrm.Body, fontSize = 13.5.sp, lineHeight = 19.sp, color = Wyrm.Quiet,
            )
            WyrmPopupButton("Choose a default skin", filled = true, onClick = onDefault)
            WyrmPopupButton("Make a custom skin", filled = false, onClick = onCustom)
            WyrmPopupButton("Keep $skinName", filled = false, quiet = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun WyrmPopupButton(label: String, filled: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    val shape = wyrmRounded(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (filled) Wyrm.Ink else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (filled || quiet) 0.dp else 1.dp, Wyrm.Rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (filled) Wyrm.OnInk else if (quiet) Wyrm.Quiet else Wyrm.Ink)
    }
}

/** The Wyrm skins page: None, the creatures, then the strip looks. */
@Composable
internal fun WyrmSkinsPage(textures: SkinTextures?, selected: Int, onPick: (Int) -> Unit) {
    WyrmSkinsCaption("Pick one and your snake becomes it in the arena.")
    TileGrid(minimum = 150.dp, count = WyrmSkins.creatures.size + 1, aspect = 1.45f, fixedColumns = 2) { index ->
        if (index == 0) {
            WyrmSkinTile(selected == 0, "None", null) { onPick(0) }
        } else {
            val skin = WyrmSkins.creatures[index - 1]
            WyrmSkinTile(selected == skin.number, skin.name, textures?.wyrmSkins?.get(skin.number)) { onPick(skin.number) }
        }
    }
    IosSectionLabel("Strip looks")
    WyrmSkinsCaption("A strip look keeps your own skin's colour.")
    TileGrid(minimum = 150.dp, count = WyrmSkins.strips.size, aspect = 1.45f, fixedColumns = 2) { index ->
        val skin = WyrmSkins.strips[index]
        WyrmSkinTile(selected == skin.number, skin.name, textures?.wyrmSkins?.get(skin.number)) { onPick(skin.number) }
    }
}
