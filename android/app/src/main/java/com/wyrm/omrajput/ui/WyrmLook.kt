package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * Wyrm looks: hair, ears and glasses (OM, 2026-09-28), as `WyrmLook` on iOS.
 *
 * Drawn only over the player's own snake and only on this phone: none of it
 * goes into the join packet or to anyone else. The art is one 8 x 8 atlas,
 * `textures/wyrm_accessories.png` (Wyrm iOS Scripts/generate-wyrm-accessories.py);
 * the engine draws it in platform/android_look.c and this file mirrors its
 * layout for the Skin Studio.
 */
internal object WyrmLook {
    val hairNames = listOf(
        "Fluffy", "Flame", "Pom", "Dreadlocks", "Mohawk", "Ponytail",
        "Pigtails", "Braid", "Long hair", "Space buns", "Man bun", "Bob",
    )
    val earNames = listOf(
        "Panda", "Bunny", "Lop bunny", "Cat", "Mouse", "Bear",
        "Koala", "Fox", "Wolf", "Tiger", "Bat", "Dragon",
    )
    val glassesNames = listOf(
        "Heart", "Cat-eye", "Flower", "Pastel", "Nerd", "Sparkle",
        "Aviator", "Pixel", "Cyber visor", "Evil visor", "Steampunk", "Punk",
    )
    /** Hair tints: the hair art is light grey and takes one of these. */
    val hairColours = listOf(
        "Brown" to 0x96603A, "Cream" to 0xF6E4C4, "Purple" to 0xAA7DF0,
        "Pink" to 0xFF8FC0, "White" to 0xFFFFFF, "Black" to 0x46464E,
    )

    const val CAP_SIDE = 4.4f
    const val CAP_BACK = 0.6f
    const val FLOW_SPAN = 3.4f

    /** A style's flows: (atlas cell, root x, root y) in head radii, x forward. */
    fun flows(style: Int): List<Triple<Int, Float, Float>> = when (style) {
        5 -> listOf(Triple(12, -0.9f, 0f))
        6 -> listOf(Triple(13, -0.55f, -0.8f), Triple(13, -0.55f, 0.8f))
        7 -> listOf(Triple(14, -0.95f, 0f))
        8 -> listOf(Triple(15, -0.6f, 0f))
        else -> emptyList()
    }

    fun rgbColor(rgb: Int) = Color(((rgb shr 16) and 0xFF) / 255f, ((rgb shr 8) and 0xFF) / 255f, (rgb and 0xFF) / 255f)
}

/**
 * The saved look, handed to the engine through `nativeSetWyrmLook`, the way
 * `ArrowSkinStore` hands over the arrow.
 */
object WyrmLookStore {
    private const val PREFS = "wyrm_look"

    var hair by mutableIntStateOf(-1)
        private set
    var hairColour by mutableIntStateOf(0)
        private set
    var ears by mutableIntStateOf(-1)
        private set
    var glasses by mutableIntStateOf(-1)
        private set

    private var prefs: android.content.SharedPreferences? = null
    private var sink: ((Int, Int, Int, Int) -> Unit)? = null

    val hairRgb: Int get() = WyrmLook.hairColours.getOrNull(hairColour)?.second ?: 0x96603A

    /** Called once by the activity; publishes the saved look to the engine. */
    @JvmStatic
    fun attach(context: Context, publish: (Int, Int, Int, Int) -> Unit) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        hair = store.getInt("hair", -1).takeIf { it in WyrmLook.hairNames.indices } ?: -1
        hairColour = store.getInt("hair_colour", 0).coerceIn(0, WyrmLook.hairColours.lastIndex)
        ears = store.getInt("ears", -1).takeIf { it in WyrmLook.earNames.indices } ?: -1
        glasses = store.getInt("glasses", -1).takeIf { it in WyrmLook.glassesNames.indices } ?: -1
        sink = publish
        publish()
    }

    fun pickHair(style: Int) { hair = if (style in WyrmLook.hairNames.indices) style else -1; save() }
    fun pickHairColour(index: Int) { hairColour = index.coerceIn(0, WyrmLook.hairColours.lastIndex); save() }
    fun pickEars(style: Int) { ears = if (style in WyrmLook.earNames.indices) style else -1; save() }
    fun pickGlasses(style: Int) { glasses = if (style in WyrmLook.glassesNames.indices) style else -1; save() }

    private fun save() {
        prefs?.edit()?.putInt("hair", hair)?.putInt("hair_colour", hairColour)?.putInt("ears", ears)
            ?.putInt("glasses", glasses)?.apply()
        publish()
    }

    private fun publish() = sink?.invoke(hair, hairRgb, ears, glasses)
}

/**
 * The look on a head drawn at [head] with radius [r] (pixels), facing +x:
 * hair at rest (flows straight back), then ears and glasses. [draw] paints one
 * atlas cell into a rectangle, tinted or not.
 */
internal fun DrawScope.drawWyrmLook(
    cells: Map<Int, ImageBitmap>,
    head: Offset,
    r: Float,
    hair: Int,
    hairRgb: Int,
    ears: Int,
    glasses: Int,
    draw: DrawScope.(ImageBitmap, Float, Float, Float, Float, Color?) -> Unit,
) {
    if (hair >= 0) {
        val tint = WyrmLook.rgbColor(hairRgb)
        for ((cell, rx, ry) in WyrmLook.flows(hair)) {
            val image = cells[cell] ?: continue
            val side = WyrmLook.FLOW_SPAN * r
            val rootX = head.x + rx * r
            val rootY = head.y + ry * r
            // The flow cell runs +x from its root; at rest it lies straight back.
            withTransform({ scale(-1f, 1f, pivot = Offset(rootX, rootY)) }) {
                draw(image, rootX, rootY - side / 2, side, side, tint)
            }
        }
        cells[hair]?.let { image ->
            val side = WyrmLook.CAP_SIDE * r
            val cx = head.x - WyrmLook.CAP_BACK * r
            draw(image, cx - side / 2, head.y - side / 2, side, side, tint)
        }
    }
    if (ears >= 0) cells[16 + ears]?.let { draw(it, head.x - 2 * r, head.y - 2 * r, 4 * r, 4 * r, null) }
    if (glasses >= 0) cells[28 + glasses]?.let { draw(it, head.x - 2 * r, head.y - 2 * r, 4 * r, 4 * r, null) }
}
