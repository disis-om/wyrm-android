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

import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.wyrm.omrajput.WyrmActivity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.ceil

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
 * The worn skin on the Skin page's preview, live (OM, 2026-10-25): the arena's
 * own skin drawing (game/wyrm_skin_preview.c) draws a snake that swims, turns
 * and now and then boosts, every frame, for as long as the page is open. It
 * was a 32-frame picture loop. Until the engine library answers, the still
 * picture. [rgb] is the snake's colour (strip looks wear it).
 */
@Composable
internal fun WyrmSkinLive(number: Int, still: ImageBitmap?, modifier: Modifier = Modifier, rgb: Int = 0x8B8CF0) {
    val mesh = remember { WyrmSkinMesh() }
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(number) {
        while (true) withFrameNanos { frameNanos = it }
    }
    Canvas(modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        val now = frameNanos / 1_000_000_000.0
        val packed = if (frameNanos == 0L) 0L else WyrmActivity.wyrmSkinPreview(
            number, size.width, size.height, now, rgb and 0xFFFFFF, mesh.xy, mesh.argb, mesh.idx,
        )
        val vertices = (packed and 0xFFFF).toInt()
        val indices = (packed ushr 16).toInt()
        if (vertices <= 0 || indices <= 0) {
            if (still != null) drawFitted(still, null)
            return@Canvas
        }
        drawIntoCanvas { canvas ->
            mesh.canvas.draw(canvas.nativeCanvas, size.width, size.height,
                vertices, indices, mesh.xy, mesh.argb, mesh.idx, mesh.paint)
        }
    }
}

/** The live preview's buffers, kept across frames. */
private class WyrmSkinMesh {
    val canvas = WyrmMeshCanvas()
    val xy = FloatArray(MAX_VERTICES * 2)
    val argb = IntArray(MAX_VERTICES)
    val idx = ShortArray(MAX_VERTICES * 3)
    val paint = android.graphics.Paint().apply { isAntiAlias = true }

    companion object {
        const val MAX_VERTICES = 30000
    }
}

/** Validate JNI geometry before Skia sees it. Android 13 and earlier use a
 * bounded software mesh surface: reported Mali GLES RenderThread faults are
 * in HWUI drawMesh, so that path receives only a bitmap on those releases.
 * This is a driver mitigation; a device run is still required to prove it.
 */
internal class WyrmMeshCanvas {
    private var bitmap: android.graphics.Bitmap? = null
    private val bitmapPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

    fun draw(target: android.graphics.Canvas, width: Float, height: Float,
             vertices: Int, indices: Int, xy: FloatArray, colors: IntArray,
             order: ShortArray, paint: android.graphics.Paint) {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
            vertices <= 0 || vertices > 30000 || vertices > xy.size / 2 || vertices > colors.size ||
            indices <= 0 || indices > order.size || indices % 3 != 0) return
        for (i in 0 until vertices * 2) if (!xy[i].isFinite()) return
        for (i in 0 until indices) if ((order[i].toInt() and 0xFFFF) >= vertices) return
        if (android.os.Build.VERSION.SDK_INT > 33 || !target.isHardwareAccelerated) {
            target.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLES, vertices * 2,
                xy, 0, null, 0, colors, 0, order, 0, indices, paint)
            return
        }
        // At most 1536 x 1536 x 4 bytes per visible preview; reuse across frames.
        val scale = min(1f, 1536f / maxOf(width, height))
        val w = ceil((width * scale).toDouble()).toInt().coerceIn(1, 1536)
        val h = ceil((height * scale).toDouble()).toInt().coerceIn(1, 1536)
        var image = bitmap
        if (image == null || image.width != w || image.height != h) {
            image = runCatching { android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888) }
                .getOrNull() ?: return
            // Do not recycle the previous image while RenderThread may hold it.
            bitmap = image
        }
        image.eraseColor(android.graphics.Color.TRANSPARENT)
        val software = android.graphics.Canvas(image)
        software.scale(scale, scale)
        software.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLES, vertices * 2,
            xy, 0, null, 0, colors, 0, order, 0, indices, paint)
        target.drawBitmap(image, null, android.graphics.RectF(0f, 0f, width, height), bitmapPaint)
    }
}

/** Slither's colour for a skin group (rrs/ggs/bbs), for a strip look's colour. */
internal fun slitherGroupRgb(group: Int): Int {
    val g = if (group in SLITHER_R.indices) group else 0
    return (SLITHER_R[g] shl 16) or (SLITHER_G[g] shl 8) or SLITHER_B[g]
}

private val SLITHER_R = intArrayOf(192, 144, 128, 128, 238, 255, 255, 255, 224, 255, 144, 80, 255, 40, 100, 120, 72, 160, 255, 56, 56, 78, 255, 101, 128, 60, 0, 217, 255, 144, 32, 240, 240, 240, 240, 32, 40, 104, 0, 104, 0)
private val SLITHER_G = intArrayOf(128, 153, 208, 255, 238, 160, 144, 64, 48, 255, 153, 80, 192, 136, 117, 134, 84, 80, 224, 68, 68, 35, 86, 200, 132, 192, 255, 69, 64, 144, 32, 32, 240, 144, 32, 240, 60, 128, 0, 40, 0)
private val SLITHER_B = intArrayOf(255, 255, 208, 128, 112, 96, 144, 64, 224, 255, 255, 80, 80, 96, 255, 255, 255, 255, 64, 255, 255, 192, 9, 232, 144, 72, 83, 69, 64, 144, 240, 32, 32, 32, 240, 32, 173, 255, 112, 170, 0)

@Composable
internal fun WyrmSkinTile(selected: Boolean, label: String, image: ImageBitmap?, onClick: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val shape = wyrmRounded(15.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(wyrmScopedPalette.card.copy(alpha = 0.92f))
            .border(if (selected) 2.dp else 1.dp, if (selected) wyrmScopedPalette.ink else wyrmScopedPalette.rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (image != null) Canvas(Modifier.fillMaxSize()) { drawFitted(image, null) }
            else Text("NONE", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.7.sp, color = wyrmScopedPalette.ink)
            if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd))
        }
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
            color = if (selected) wyrmScopedPalette.ink else wyrmScopedPalette.quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WyrmSkinsCaption(text: String) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Text(text, fontFamily = Wyrm.Body, fontSize = 12.5.sp, lineHeight = 18.sp, color = wyrmScopedPalette.quiet,
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
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

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
                .background(wyrmScopedPalette.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$skinName is on", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = wyrmScopedPalette.ink)
            Text(
                "You are wearing the ${if (strip) "strip look" else "creature skin"} $skinName, so $what stay off. " +
                    "To use them, choose a default skin or make a custom one.",
                fontFamily = Wyrm.Body, fontSize = 13.5.sp, lineHeight = 19.sp, color = wyrmScopedPalette.quiet,
            )
            WyrmPopupButton("Choose a default skin", filled = true, onClick = onDefault)
            WyrmPopupButton("Make a custom skin", filled = false, onClick = onCustom)
            WyrmPopupButton("Keep $skinName", filled = false, quiet = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun WyrmPopupButton(label: String, filled: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val shape = wyrmRounded(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (filled) wyrmScopedPalette.ink else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (filled || quiet) 0.dp else 1.dp, wyrmScopedPalette.rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (filled) wyrmScopedPalette.onInk else if (quiet) wyrmScopedPalette.quiet else wyrmScopedPalette.ink)
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
