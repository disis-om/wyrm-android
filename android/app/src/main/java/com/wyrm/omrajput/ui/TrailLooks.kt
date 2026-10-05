@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wyrm.omrajput.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.media3.common.Effect
import androidx.media3.effect.RgbMatrix

/*
 * Trails looks (OM, 2026-10-05): the colour filters and adjustments of the
 * photo editor and the video editor, one definition for both.
 *
 * A look is a 4 x 5 colour matrix in 0..255 units (Android's ColorMatrix):
 * the photo editor paints with it, and the video editor turns the same matrix
 * into Media3's 4 x 4 RgbMatrix (column-major, applied as M * (r, g, b, 1) in
 * 0..1 units), for the player's preview and for the export alike. What the
 * player sees in the editor is what goes up. Wyrm iOS: `WyrmTrailLooks`.
 */

/** One named look: the editors' filter strip. */
internal data class TrailLook(
    val name: String,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    /** -1..1, about a quarter of the range at the ends. */
    val brightness: Float = 0f,
    /** -1..1: warm (red up, blue down) to cool. */
    val warmth: Float = 0f,
    /** -1..1: green to magenta. */
    val tint: Float = 0f,
    /** 0..1: lifted blacks, a softer picture. */
    val fade: Float = 0f,
    val sepia: Boolean = false,
)

/** The adjustment sliders, each -1..1 with 0 as "unchanged". */
internal data class TrailAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val untouched: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

internal object TrailLooks {
    val all = listOf(
        TrailLook("Normal"),
        TrailLook("Vivid", saturation = 1.35f, contrast = 1.12f),
        TrailLook("Arena", saturation = 1.15f, contrast = 1.08f, warmth = -0.25f, tint = -0.35f),
        TrailLook("Neon", saturation = 1.55f, contrast = 1.2f, tint = 0.35f, brightness = 0.04f),
        TrailLook("Sunset", saturation = 1.15f, warmth = 0.7f, contrast = 1.05f),
        TrailLook("Frost", saturation = 0.85f, warmth = -0.7f, brightness = 0.06f),
        TrailLook("Fade", saturation = 0.8f, contrast = 0.88f, fade = 0.45f),
        TrailLook("Retro", sepia = true, contrast = 1.05f, fade = 0.2f),
        TrailLook("Mono", saturation = 0f, contrast = 1.1f),
        TrailLook("Noir", saturation = 0f, contrast = 1.45f, brightness = -0.06f),
    )

    /** The look then the sliders, as one 4 x 5 matrix in 0..255 units. */
    fun matrix(look: TrailLook, adjust: TrailAdjust = TrailAdjust()): ColorMatrix {
        val out = ColorMatrix()
        if (look.sepia) {
            out.postConcat(ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        out.postConcat(saturation(look.saturation * (1f + adjust.saturation)))
        out.postConcat(contrast(look.contrast * (1f + adjust.contrast * 0.5f)))
        out.postConcat(offsets(
            brightness = look.brightness + adjust.brightness * 0.25f,
            warmth = look.warmth + adjust.warmth,
            tint = look.tint,
        ))
        if (look.fade > 0f) out.postConcat(fade(look.fade))
        return out
    }

    fun colorFilter(look: TrailLook, adjust: TrailAdjust = TrailAdjust()): ColorMatrixColorFilter =
        ColorMatrixColorFilter(matrix(look, adjust))

    /** For Compose's Paint / ColorFilter. */
    fun composeFilter(look: TrailLook, adjust: TrailAdjust = TrailAdjust()): androidx.compose.ui.graphics.ColorFilter =
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix(look, adjust).array))

    fun isIdentity(look: TrailLook, adjust: TrailAdjust): Boolean = look.name == "Normal" && adjust.untouched

    /**
     * The same matrix for Media3: 4 x 4 column-major in 0..1 units, the
     * offsets in the last column (the shader multiplies (r, g, b, 1)).
     */
    fun rgbMatrix(look: TrailLook, adjust: TrailAdjust = TrailAdjust()): FloatArray {
        val m = matrix(look, adjust).array
        fun a(row: Int, col: Int) = m[row * 5 + col]
        return floatArrayOf(
            a(0, 0), a(1, 0), a(2, 0), 0f,
            a(0, 1), a(1, 1), a(2, 1), 0f,
            a(0, 2), a(1, 2), a(2, 2), 0f,
            a(0, 4) / 255f, a(1, 4) / 255f, a(2, 4) / 255f, 1f,
        )
    }

    /** The video effect for a look, or nothing for Normal with no adjustment. */
    fun videoEffects(look: TrailLook, adjust: TrailAdjust): List<Effect> {
        if (isIdentity(look, adjust)) return emptyList()
        val values = rgbMatrix(look, adjust)
        return listOf(RgbMatrix { _, _ -> values })
    }

    private fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s.coerceIn(0f, 3f)) }

    private fun contrast(c: Float): ColorMatrix {
        val k = c.coerceIn(0.2f, 3f)
        val t = 128f * (1f - k)
        return ColorMatrix(floatArrayOf(
            k, 0f, 0f, 0f, t,
            0f, k, 0f, 0f, t,
            0f, 0f, k, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    private fun offsets(brightness: Float, warmth: Float, tint: Float): ColorMatrix {
        val b = brightness.coerceIn(-1f, 1f) * 64f
        val w = warmth.coerceIn(-1f, 1f) * 26f
        val g = tint.coerceIn(-1f, 1f) * 18f
        return ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, b + w + g * 0.5f,
            0f, 1f, 0f, 0f, b - g,
            0f, 0f, 1f, 0f, b - w + g * 0.5f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    private fun fade(f: Float): ColorMatrix {
        val k = 1f - 0.22f * f.coerceIn(0f, 1f)
        val lift = 255f * 0.16f * f.coerceIn(0f, 1f)
        return ColorMatrix(floatArrayOf(
            k, 0f, 0f, 0f, lift,
            0f, k, 0f, 0f, lift,
            0f, 0f, k, 0f, lift,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}
