package com.wyrm.omrajput.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Skin textures (OM, 2026-10-25), NTL 9.76's "Skin texture": the engine's
 * `skin.texture` (0 Default, 1 Rounded plain, 2 Squared plain, 3 NTL
 * textured, 4 Striped plain) and `skin.texture_mine` (on my snake only, or
 * every snake). The engine draws the arena (game/redraw.c
 * skin_texture_apply, bp.slang); here the Skin page's preview and the pattern
 * editor's beads wear the same texture, through [bead].
 */
internal object SkinTexture {
    val names = listOf(
        "Wyrm", "Rounded plain", "Squared plain", "NTL textured", "Striped plain",
        "Glossy", "Matte", "Neon", "Pearl", "Metal",
    )
    /**
     * What the picker offers (OM, 2026-10-26): eight. 2 and 4 (Squared and
     * Striped plain) are retired and draw Wyrm's beads.
     */
    val offered = listOf(0, 3, 1, 5, 6, 7, 8, 9)
    private val drawn = setOf(1, 3, 5, 6, 7, 8, 9)
    /** A drawn texture is on: beads are plain, upright, light at the top left. */
    val drawnNow: Boolean get() = current in drawn

    /** The texture the Skin page shows; kept in step with `skin.texture`. */
    var current by mutableIntStateOf(0)
    var mine by mutableStateOf(false)

    private val cache = HashMap<Int, ImageBitmap>()

    /** This colour's bead in the current texture, or null for Default (the atlas bead). */
    fun bead(rgb: Int, texture: Int = current): ImageBitmap? {
        if (texture !in drawn) return null
        val key = (texture shl 24) or (rgb and 0xFFFFFF)
        return cache.getOrPut(key) { render(texture, rgb).asImageBitmap() }
    }

    private fun render(texture: Int, rgb: Int): Bitmap {
        val s = 96
        val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colour = 0xFF000000.toInt() or (rgb and 0xFFFFFF)
        when (texture) {
            1 -> {
                paint.color = colour
                canvas.drawCircle(s / 2f, s / 2f, s / 2f - 1f, paint)
            }
            3 -> {
                // NTL's soft shaded ball: light from the upper left, a darker rim (bp.slang).
                fun scaled(f: Float): Int {
                    val r = (((rgb shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
                    val g = (((rgb shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
                    val b = ((rgb and 0xFF) * f).toInt().coerceIn(0, 255)
                    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                }
                paint.shader = RadialGradient(
                    s * 0.35f, s * 0.33f, s * 0.62f,
                    intArrayOf(scaled(1.2f), scaled(0.95f), scaled(0.62f)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(s / 2f, s / 2f, s / 2f - 1f, paint)
            }
            else -> drawNew(canvas, texture, rgb, s.toFloat())
        }
        return bitmap
    }

    /** The five of 2026-10-26, as bp.slang draws them: upright, light at the top left. */
    private fun drawNew(canvas: android.graphics.Canvas, texture: Int, rgb: Int, s: Float) {
        val r = s / 2f - 1f
        val cx = s / 2f
        fun c(f: Float, add: Float = 0f): Int {
            fun ch(v: Int) = (v * f + add * 255).toInt().coerceIn(0, 255)
            return 0xFF000000.toInt() or (ch((rgb shr 16) and 0xFF) shl 16) or (ch((rgb shr 8) and 0xFF) shl 8) or ch(rgb and 0xFF)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (texture) {
            5 -> { // Glossy
                paint.shader = RadialGradient(s * 0.35f, s * 0.33f, s * 0.62f,
                    intArrayOf(c(1.08f), c(0.85f), c(0.5f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cx, r, paint)
                paint.shader = RadialGradient(s * 0.32f, s * 0.28f, s * 0.2f,
                    intArrayOf(0xD9FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                canvas.drawCircle(s * 0.32f, s * 0.28f, s * 0.2f, paint)
            }
            6 -> { // Matte
                paint.shader = RadialGradient(cx, cx, r, intArrayOf(c(1.0f), c(0.98f), c(0.72f)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cx, r, paint)
            }
            7 -> { // Neon
                paint.color = c(0.22f)
                canvas.drawCircle(cx, cx, r, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.36f
                paint.color = c(1.6f, 0.12f)
                canvas.drawCircle(cx, cx, r * 0.78f, paint)
            }
            8 -> { // Pearl
                fun pearl(f: Float, add: Float): Int {
                    fun ch(v: Int) = ((v * 0.7f + 255 * 0.3f) * f + add * 255).toInt().coerceIn(0, 255)
                    return 0xFF000000.toInt() or (ch((rgb shr 16) and 0xFF) shl 16) or (ch((rgb shr 8) and 0xFF) shl 8) or ch(rgb and 0xFF)
                }
                paint.shader = RadialGradient(s * 0.35f, s * 0.32f, s * 0.75f,
                    intArrayOf(pearl(1.08f, 0.1f), pearl(0.9f, 0.04f), pearl(0.78f, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cx, r, paint)
            }
            else -> { // Metal
                paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, s,
                    intArrayOf(c(0.95f), c(1.0f, 0.3f), c(0.75f), c(0.45f), c(0.8f)),
                    floatArrayOf(0f, 0.3f, 0.45f, 0.75f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cx, r, paint)
            }
        }
    }
}

/**
 * Skin tab › Pattern: "Select texture" and, once open, the five textures with
 * Mine / Everyone. [onPick] and [onMine] write the engine settings.
 */
@Composable
internal fun SkinTextureSelector(onPick: (Int) -> Unit, onMine: (Boolean) -> Unit, initiallyOpen: Boolean = false) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var open by androidx.compose.runtime.remember { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(wyrmRounded(16.dp)).background(wyrmScopedPalette.card)
                .border(1.dp, wyrmScopedPalette.rule, wyrmRounded(16.dp))
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Select texture", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = wyrmScopedPalette.ink, modifier = Modifier.weight(1f))
            Text(SkinTexture.names.getOrElse(SkinTexture.current.takeIf { it in SkinTexture.offered } ?: 0) { "Wyrm" }, fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = wyrmScopedPalette.mute)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            SkinTexture.offered.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { index ->
                        val selected = SkinTexture.current == index ||
                            (index == 0 && SkinTexture.current !in SkinTexture.offered)
                        SkinTextureTile(index, SkinTexture.names[index], selected, Modifier.weight(1f)) {
                            SkinTexture.current = index
                            onPick(index)
                        }
                    }
                }
            }
            PaperSegmented(
                options = listOf("Mine", "Everyone"),
                selected = if (SkinTexture.mine) 0 else 1,
                onSelect = {
                    SkinTexture.mine = it == 0
                    onMine(it == 0)
                },
            )
            Text(
                "Mine puts the texture on your snake only. Everyone draws every snake in it. " +
                    "Every texture is light to draw, and far away fewer beads are drawn, so the game stays smooth.",
                fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.mute,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun SkinTextureTile(texture: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(
        modifier.clip(wyrmRounded(14.dp)).background(wyrmScopedPalette.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) wyrmScopedPalette.ink else wyrmScopedPalette.rule, wyrmRounded(14.dp))
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp)) {
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val hues = intArrayOf(0xC080FF, 0x9099FF, 0x80D0D0, 0x80FF80, 0xEEEE70, 0xFFA060)
                val d = size.height * 0.8f
                val step = (size.width - d) / 5f
                for (i in 0 until 6) {
                    val left = i * step
                    val top = (size.height - d) / 2f
                    val image = SkinTexture.bead(hues[i], texture)
                    if (image != null) {
                        drawImage(image, dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(d.toInt(), d.toInt()))
                    } else {
                        drawCircle(androidx.compose.ui.graphics.Color(0xFF000000.toInt() or hues[i]), d / 2f,
                            androidx.compose.ui.geometry.Offset(left + d / 2f, top + d / 2f))
                    }
                }
            }
        }
        Text(label, fontFamily = Wyrm.Body, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp, color = if (selected) wyrmScopedPalette.ink else wyrmScopedPalette.mute, maxLines = 1,
            modifier = Modifier.padding(top = 6.dp))
    }
}

/** The wardrobe dock opens directly: eight textures and one compact scope switch. */
@Composable
internal fun WardrobeTextureDock(onPick: (Int) -> Unit, onMine: (Boolean) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SkinTexture.offered.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { column, index ->
                    val reveal = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay((rowIndex * 4 + column) * 24L)
                        reveal.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .58f, stiffness = 420f))
                    }
                    SkinTextureTile(index, SkinTexture.names[index], SkinTexture.current == index ||
                        (index == 0 && SkinTexture.current !in SkinTexture.offered),
                        Modifier.weight(1f).graphicsLayer {
                            translationY = (1f - reveal.value) * 24.dp.toPx()
                            scaleX = .82f + .18f * reveal.value; scaleY = scaleX
                            alpha = reveal.value.coerceIn(0f, 1f)
                        }) { SkinTexture.current = index; onPick(index) }
                }
            }
        }
        val thumb = androidx.compose.animation.core.animateFloatAsState(if (SkinTexture.mine) 0f else 1f, label = "texture-scope")
        Row(Modifier.height(44.dp).clip(wyrmRounded(15.dp)).semantics {
            contentDescription = "Texture scope"; stateDescription = if (SkinTexture.mine) "Mine" else "Everyone"
        }.clickable(role = androidx.compose.ui.semantics.Role.Switch) {
            SkinTexture.mine = !SkinTexture.mine; onMine(SkinTexture.mine)
        }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mine", fontFamily = Wyrm.Body, fontSize = 12.sp, color = if (SkinTexture.mine) wyrmScopedPalette.ink else wyrmScopedPalette.mute)
            Box(Modifier.width(36.dp).height(22.dp).clip(wyrmRounded(11.dp)).background(wyrmScopedPalette.ink)) {
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((thumb.value * 14.dp.toPx()).toInt(), 0) }
                    .padding(3.dp).size(16.dp).clip(wyrmRounded(8.dp)).background(wyrmScopedPalette.onInk))
            }
            Text("Everyone", fontFamily = Wyrm.Body, fontSize = 12.sp, color = if (!SkinTexture.mine) wyrmScopedPalette.ink else wyrmScopedPalette.mute)
        }
    }
}
