package com.wyrm.omrajput.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The slither.io Android (AIR) client's Build-a-Slither colour model — a
 * line-for-line port of Wyrm iOS's `WyrmAirSkin` (itself ported from AIR's
 * `gaim.Main`: `buildColorWheel`, the two pointer `touchMove`s and `setSkin`).
 * Units are the AIR wheel's own: radius 128, bezel 18 wide, bezel pointer on
 * radius 151.
 *
 * A wheel bead keeps its exact RGB and names its AIR texture in the alpha
 * byte; the engine (redraw.c) and the Compose preview both read it back.
 */
object AirSkin {
    const val PLAIN_MARKER = 0xFE000000.toInt() // nsk 0, kmc_ts[9][0]
    const val RIM_MARKER = 0xFD000000.toInt()   // nsk 1, kmc_ts[29][0]

    fun marker(kind: Int): Int = if (kind == 0) PLAIN_MARKER else RIM_MARKER

    fun kind(argb: Int): Int? = when ((argb ushr 24) and 0xFF) {
        0xFE -> 0
        0xFD -> 1
        else -> null
    }

    const val WHEEL_RADIUS = 128.0
    const val BEZEL_WIDTH = 18.0
    const val BEZEL_POINTER_RADIUS = 151.0
    /** `_loc11_ = _loc9_ - _loc10_ + 3`: how far the wheel pointer may go. */
    const val POINTER_LIMIT = 128.0 - 24.0 + 3.0

    /** `hypah.mod.Mod.closestMod`, including its AS3 remainder semantics. */
    fun closestMod(value: Double, target: Double, period: Double): Double {
        var base: Double
        if (value < 0) {
            base = period - (period - value).rem(period)
            if (base == period) base = 0.0
        } else {
            base = value.rem(period)
        }
        var best = if (base > target) base - target else target - base
        var other = if (base - period > target) base - period - target else target - (base - period)
        if (other < best) {
            best = other
            other = if (base + period > target) base + period - target else target - (base + period)
            return if (other < best) base + period else base - period
        }
        other = if (base + period > target) base + period - target else target - (base + period)
        return if (other < best) base + period else base
    }

    /** The unrounded wheel colour under the pointer. */
    fun pure(x: Double, y: Double): DoubleArray {
        val inner = 16.0
        val outer = 24.0
        val rad = 128.0
        var d = sqrt(x * x + y * y) - inner
        if (d < 0) d = 0.0
        if (d > rad - outer - inner) d = rad - outer - inner
        val saturation = minOf(1.0, maxOf(0.0, d) / (rad - outer - inner))
        val angle = atan2(y, x)
        val twoPi = PI * 2
        val k1 = PI * 2 / 3
        val k2 = PI * 4 / 3
        val raw = doubleArrayOf(
            (1 - abs(closestMod(angle, 0.0, twoPi)) / PI - 1.0 / 3) * 3,
            (1 - abs(closestMod(angle, k1, twoPi) - k1) / PI - 1.0 / 3) * 3,
            (1 - abs(closestMod(angle, k2, twoPi) - k2) / PI - 1.0 / 3) * 3,
        )
        return DoubleArray(3) { 128 + (256 * minOf(1.0, maxOf(0.0, raw[it])) - 128) * saturation }
    }

    /** `bsk_br` for a bezel angle: top half lightens to 1, bottom darkens to -0.5. */
    fun brightness(angle: Double): Double {
        if (angle <= 0) {
            val amount = abs(angle + PI / 2) / (PI / 2)
            return maxOf(0.0, minOf(1.0, 1.1 * (1 - amount)))
        }
        val amount = maxOf(0.0, minOf(1.0, 1.1 * (1 - abs(angle - PI / 2) / (PI / 2))))
        return maxOf(-0.5, -amount * 0.5)
    }

    /** Applies `bsk_br` and rounds, as both pointers do (AS3 Math.round). */
    fun shaded(colour: DoubleArray, br: Double): Int {
        fun channel(value: Double): Int {
            val v = if (br >= 0) value + (255 - value) * br else value + value * br
            return floor(v + 0.5).coerceIn(0.0, 255.0).toInt()
        }
        return (channel(colour[0]) shl 16) or (channel(colour[1]) shl 8) or channel(colour[2])
    }

    /** `Math.round` then clamp: the stored `bsk_orr/ogg/obb`. */
    fun rounded(colour: DoubleArray): DoubleArray = DoubleArray(3) { floor(colour[it] + 0.5).coerceIn(0.0, 255.0) }

    /** `setSkin`: the colour a bead is drawn with once it is on the body. */
    fun bodyTint(argb: Int): Int {
        val c = doubleArrayOf(((argb shr 16) and 0xFF).toDouble(), ((argb shr 8) and 0xFF).toDouble(), (argb and 0xFF).toDouble())
        val lo = c.min()
        val hi = c.max()
        val mid = c.sum() - lo - hi
        if (mid + hi < 255) {
            val lift = 1 + (255 - (mid + hi)) / 2
            for (i in 0..2) c[i] = minOf(255.0, c[i] + lift)
        }
        return (c[0].toInt() shl 16) or (c[1].toInt() shl 8) or c[2].toInt()
    }

    /** The engine's colour-group palette (game_data.c cg_colors). */
    private val palette = arrayOf(
        doubleArrayOf(0.75, 0.5, 0.99609375), doubleArrayOf(0.5625, 0.59765625, 0.99609375), doubleArrayOf(0.5, 0.8125, 0.8125),
        doubleArrayOf(0.5, 0.99609375, 0.5), doubleArrayOf(0.9296875, 0.9296875, 0.4375), doubleArrayOf(0.99609375, 0.625, 0.375),
        doubleArrayOf(0.99609375, 0.5625, 0.5625), doubleArrayOf(0.99609375, 0.25, 0.25), doubleArrayOf(0.875, 0.1875, 0.875),
        doubleArrayOf(0.99609375, 0.99609375, 0.99609375), doubleArrayOf(0.5625, 0.59765625, 0.99609375), doubleArrayOf(0.3125, 0.3125, 0.3125),
        doubleArrayOf(0.99609375, 0.75, 0.3125), doubleArrayOf(0.15625, 0.53125, 0.375), doubleArrayOf(0.390625, 0.45703125, 0.99609375),
        doubleArrayOf(0.46875, 0.5234375, 0.99609375), doubleArrayOf(0.28125, 0.328125, 0.99609375), doubleArrayOf(0.625, 0.3125, 0.99609375),
        doubleArrayOf(0.99609375, 0.875, 0.25), doubleArrayOf(0.21875, 0.265625, 0.99609375), doubleArrayOf(0.21875, 0.265625, 0.99609375),
        doubleArrayOf(0.3046875, 0.13671875, 0.75), doubleArrayOf(0.99609375, 0.3359375, 0.03515625), doubleArrayOf(0.39453125, 0.78125, 0.90625),
        doubleArrayOf(0.5, 0.515625, 0.5625), doubleArrayOf(0.234375, 0.75, 0.28125), doubleArrayOf(0.0, 0.99609375, 0.32421875),
        doubleArrayOf(0.84765625, 0.26953125, 0.26953125), doubleArrayOf(0.99609375, 0.25, 0.25), doubleArrayOf(0.5625, 0.5625, 0.5625),
        doubleArrayOf(0.125, 0.125, 0.9375), doubleArrayOf(0.9375, 0.125, 0.125), doubleArrayOf(0.9375, 0.9375, 0.125),
        doubleArrayOf(0.9375, 0.5625, 0.125), doubleArrayOf(0.9375, 0.125, 0.9375), doubleArrayOf(0.125, 0.9375, 0.125),
        doubleArrayOf(0.15625, 0.234375, 0.67578125), doubleArrayOf(0.40625, 0.5, 0.99609375), doubleArrayOf(0.0, 0.0, 0.4375),
        doubleArrayOf(0.40625, 0.15625, 0.6640625), doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(0.5, 0.5, 0.99609375),
    )

    /** Android Wyrm's `nearestGroup`: green-weighted, dead groups excluded. */
    fun nearestGroup(rgb: Int): Int {
        val r = ((rgb shr 16) and 0xFF) / 255.0
        val g = ((rgb shr 8) and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        var best = SkinCatalog.validGroups.first()
        var bestDistance = Double.MAX_VALUE
        for (group in SkinCatalog.validGroups) {
            if (group !in palette.indices) continue
            val c = palette[group]
            val dr = (c[0] - r) * 0.30
            val dg = (c[1] - g) * 0.59
            val db = (c[2] - b) * 0.11
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = group
            }
        }
        return best
    }
}
