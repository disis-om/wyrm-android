package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.roundToInt

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
    /**
     * The hair colour slider (OM, 2026-09-28: one slider, not six beads). The
     * hair art is light grey and takes the colour at the slider's position.
     */
    val hairStops = listOf(
        0.00f to 0x2A2A30, 0.12f to 0x4A2E1A, 0.22f to 0x96603A, 0.32f to 0xB0452A,
        0.40f to 0xE07A30, 0.48f to 0xF2D28A, 0.54f to 0xF6E4C4, 0.60f to 0xFFFFFF,
        0.70f to 0xFF8FC0, 0.78f to 0xAA7DF0, 0.86f to 0x5A8CF0, 0.93f to 0x3EC6C0,
        1.00f to 0x4CC05A,
    )

    /** The colour at [tone] (0..1) along [hairStops]. */
    fun hairTone(tone: Float): Int {
        val t = tone.coerceIn(0f, 1f)
        val next = hairStops.indexOfFirst { it.first >= t }.coerceAtLeast(1)
        val (a, ca) = hairStops[next - 1]
        val (b, cb) = hairStops[next]
        val f = if (b > a) ((t - a) / (b - a)).coerceIn(0f, 1f) else 0f
        fun mix(shift: Int): Int {
            val x = (ca shr shift) and 0xFF
            val y = (cb shr shift) and 0xFF
            return (x + (y - x) * f).roundToInt().coerceIn(0, 255) shl shift
        }
        return mix(16) or mix(8) or mix(0)
    }

    /** Where the six old hair colours sit on the slider, for saved looks. */
    val oldHairTones = floatArrayOf(0.22f, 0.54f, 0.78f, 0.70f, 0.60f, 0.0f)

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
 * A look as plain values: the saved one ([WyrmLookStore.spec]), a skin being
 * tried from a trail, or the one drawn on a "Share run" sticker.
 */
internal data class WyrmLookSpec(val hair: Int = -1, val hairTone: Float = 0.22f, val ears: Int = -1, val glasses: Int = -1) {
    val hairRgb: Int get() = WyrmLook.hairTone(hairTone)

    /** Only styles this app has; anything else is "none". */
    fun checked() = WyrmLookSpec(
        hair = hair.takeIf { it in WyrmLook.hairNames.indices } ?: -1,
        hairTone = hairTone.coerceIn(0f, 1f),
        ears = ears.takeIf { it in WyrmLook.earNames.indices } ?: -1,
        glasses = glasses.takeIf { it in WyrmLook.glassesNames.indices } ?: -1,
    )
}

/**
 * The saved look, handed to the engine through `nativeSetWyrmLook`, the way
 * `ArrowSkinStore` hands over the arrow.
 */
object WyrmLookStore {
    private const val PREFS = "wyrm_look"

    var hair by mutableIntStateOf(-1)
        private set
    /** The hair colour slider's position, 0..1 along `WyrmLook.hairStops`. */
    var hairTone by mutableFloatStateOf(0.22f)
        private set
    var ears by mutableIntStateOf(-1)
        private set
    var glasses by mutableIntStateOf(-1)
        private set

    private var prefs: android.content.SharedPreferences? = null
    private var sink: ((Int, Int, Int, Int) -> Unit)? = null

    val hairRgb: Int get() = WyrmLook.hairTone(hairTone)

    /** Called once by the activity; publishes the saved look to the engine. */
    @JvmStatic
    fun attach(context: Context, publish: (Int, Int, Int, Int) -> Unit) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        hair = store.getInt("hair", -1).takeIf { it in WyrmLook.hairNames.indices } ?: -1
        hairTone = if (store.contains("hair_tone")) {
            store.getFloat("hair_tone", 0.22f).coerceIn(0f, 1f)
        } else {
            WyrmLook.oldHairTones.getOrElse(store.getInt("hair_colour", 0)) { 0.22f }
        }
        ears = store.getInt("ears", -1).takeIf { it in WyrmLook.earNames.indices } ?: -1
        glasses = store.getInt("glasses", -1).takeIf { it in WyrmLook.glassesNames.indices } ?: -1
        sink = publish
        publish()
    }

    /** After a log in or log out rewrote the file (AccountSync): read it again and republish. */
    fun reload(context: Context) {
        val publish = sink ?: return
        attach(context, publish)
    }

    fun pickHair(style: Int) { hair = if (style in WyrmLook.hairNames.indices) style else -1; save() }
    fun pickHairTone(tone: Float) { hairTone = tone.coerceIn(0f, 1f); save() }
    fun pickEars(style: Int) { ears = if (style in WyrmLook.earNames.indices) style else -1; save() }
    fun pickGlasses(style: Int) { glasses = if (style in WyrmLook.glassesNames.indices) style else -1; save() }

    /** The saved look as values. Read in composition, it follows every change. */
    internal fun spec() = WyrmLookSpec(hair, hairTone, ears, glasses)

    /** Wears a whole look at once ("Try this skin" › Wear): saved and published once. */
    internal fun wear(look: WyrmLookSpec) {
        val next = look.checked()
        hair = next.hair
        hairTone = next.hairTone
        ears = next.ears
        glasses = next.glasses
        save()
    }

    private fun save() {
        prefs?.edit()?.putInt("hair", hair)?.putFloat("hair_tone", hairTone)?.putInt("ears", ears)
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
