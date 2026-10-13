package com.wyrm.omrajput.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Skin Studio's pictures (OM, 2026-10-12): every bead, eye white and pupil is
 * drawn here, the same way for the Studio's previews and for the atlas the
 * engine wears (game/studio.h: 8 x 8 cells of 128 px; beads 0-47, eye whites
 * 48-55, pupils 56-63). Everything is baked into the picture — the finish,
 * the painting, hue, saturation, brightness, smoothness and opacity — so the
 * arena draws a Studio bead exactly as cheaply as any other.
 *
 * A bead is drawn facing +x, the way the arena lays it along the body; the
 * engine turns it with the body and an eye with the head.
 */
internal object StudioArt {
    const val FINISH_SLITHER = 0
    const val FINISH_FLAT = 1
    const val FINISH_GLOSSY = 2
    const val FINISH_MATTE = 3
    const val FINISH_METAL = 4
    const val FINISH_PEARL = 5
    const val FINISH_GLASS = 6
    const val FINISH_NEON = 7
    const val FINISH_STRIPES = 8
    const val FINISH_DOTS = 9
    const val FINISH_SCALES = 10
    const val FINISH_GLITTER = 11
    const val FINISH_MARBLE = 12
    const val FINISH_CARBON = 13
    const val FINISH_GRADIENT = 14
    const val FINISH_RINGS = 15

    val FINISHES = listOf(
        "Slither", "Flat", "Glossy", "Matte", "Metal", "Pearl", "Glass", "Neon",
        "Stripes", "Dots", "Scales", "Glitter", "Marble", "Carbon", "Gradient", "Rings",
    )

    val WHITE_SHAPES = listOf("Round", "Almond", "Outline", "Square", "Glow")

    const val PUPIL_ROUND = 0
    const val PUPIL_CAT = 2
    val PUPIL_SHAPES = listOf(
        "Round", "Dot", "Cat", "Goat", "Heart", "Star", "Diamond", "Cross",
        "Plus", "Square", "Ring", "Spiral", "Flower", "Moon", "Spark", "Twin",
    )

    const val CELL = 128
    const val GRID = 8
    const val ATLAS = CELL * GRID
    const val FIRST_WHITE = 48
    const val FIRST_PUPIL = 56

    /** The bead's disc inside its square: a hair inside, so mip levels never bleed. */
    const val DISC = 0.94f

    // ------------------------------------------------------------ beads

    /** [bead] as a [size]-px picture, [paint] laid over its finish. */
    fun renderBead(bead: StudioBead, paint: Bitmap?, size: Int, textures: SkinTextures?): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(layer)
        val half = size / 2f
        val r = half * DISC
        drawFinish(c, bead, half, half, r, textures)
        if (paint != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val soft = bead.smoothness * size / 64f
            val source = if (soft >= 0.75f) blurred(paint, soft * paint.width / size) else paint
            c.save()
            c.clipPath(Path().apply { addCircle(half, half, r, Path.Direction.CW) })
            c.drawBitmap(source, Rect(0, 0, source.width, source.height), RectF(half - r, half - r, half + r, half + r), p)
            c.restore()
            if (source !== paint) source.recycle()
        }
        // Hue, saturation and brightness over the whole bead, then its edge and opacity.
        val o = Canvas(out)
        val adjust = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(adjustMatrix(bead.hue, bead.saturation, bead.brightness))
            alpha = (bead.opacity.coerceIn(0.15f, 1f) * 255).roundToInt()
        }
        o.drawBitmap(layer, 0f, 0f, adjust)
        layer.recycle()
        featherEdge(o, half, r, bead.smoothness)
        return out
    }

    private fun drawFinish(c: Canvas, bead: StudioBead, cx: Float, cy: Float, r: Float, textures: SkinTextures?) {
        val col = bead.color or (0xFF shl 24)
        val detail = bead.detail.coerceIn(0f, 1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val disc = RectF(cx - r, cy - r, cx + r, cy + r)
        fun fill(color: Int) { p.shader = null; p.color = color; c.drawCircle(cx, cy, r, p) }
        fun shade(strength: Float) {
            // A soft dark rim and a light top-left, as a lit sphere.
            p.color = -1
            p.shader = RadialGradient(cx - r * 0.32f, cy - r * 0.36f, r * 1.45f,
                intArrayOf(withAlpha(-1, 0.30f * strength), 0, withAlpha(0xFF000000.toInt(), 0.45f * strength)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            p.shader = null
        }
        fun clip(block: () -> Unit) {
            c.save()
            c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
            block()
            c.restore()
        }
        when (bead.finish) {
            FINISH_SLITHER -> {
                val white = textures?.beads?.get(40)?.asAndroidBitmap()
                if (white == null) {
                    fill(col); shade(0.9f)
                } else {
                    fill(blend(col, darker(col, 0.25f), 0.5f))
                    val q = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        colorFilter = PorterDuffColorFilter(col, PorterDuff.Mode.MULTIPLY)
                        alpha = (255 * (0.55f + 0.45f * detail)).roundToInt()
                    }
                    // The white bead's own art fills its cell a little past the disc.
                    val grow = r * 1.04f
                    clip { c.drawBitmap(white, Rect(0, 0, white.width, white.height), RectF(cx - grow, cy - grow, cx + grow, cy + grow), q) }
                }
            }
            FINISH_FLAT -> fill(col)
            FINISH_GLOSSY -> {
                p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.5f,
                    intArrayOf(lighter(col, 0.45f * detail), col, darker(col, 0.45f * detail)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
                p.shader = RadialGradient(cx - r * 0.34f, cy - r * 0.42f, r * 0.42f,
                    intArrayOf(withAlpha(-1, 0.75f * detail), 0), null, Shader.TileMode.CLAMP)
                c.drawOval(RectF(cx - r * 0.72f, cy - r * 0.78f, cx + r * 0.05f, cy - r * 0.08f), p)
            }
            FINISH_MATTE -> {
                p.shader = RadialGradient(cx, cy - r * 0.15f, r * 1.2f,
                    intArrayOf(lighter(col, 0.12f * detail), darker(col, 0.28f * detail)), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
            }
            FINISH_METAL -> {
                val hi = lighter(col, 0.6f * detail)
                val lo = darker(col, 0.55f * detail)
                p.shader = SweepGradient(cx, cy, intArrayOf(hi, col, lo, col, hi, col, lo, col, hi), null)
                c.drawCircle(cx, cy, r, p)
                shade(0.8f * detail)
                p.shader = RadialGradient(cx, cy, r * 0.5f, intArrayOf(withAlpha(-1, 0.35f * detail), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r * 0.5f, p)
            }
            FINISH_PEARL -> {
                val a = lighter(hueShift(col, 40f), 0.35f)
                val b = lighter(hueShift(col, -40f), 0.35f)
                p.shader = SweepGradient(cx, cy, intArrayOf(lighter(col, 0.3f), a, col, b, lighter(col, 0.3f)), null)
                c.drawCircle(cx, cy, r, p)
                p.shader = RadialGradient(cx - r * 0.2f, cy - r * 0.25f, r,
                    intArrayOf(withAlpha(-1, 0.65f * detail), withAlpha(-1, 0.1f * detail), 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
            }
            FINISH_GLASS -> {
                p.shader = RadialGradient(cx, cy, r, intArrayOf(withAlpha(col, 0.28f), withAlpha(col, 0.55f), col),
                    floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * (0.08f + 0.12f * detail)
                p.color = lighter(col, 0.35f)
                c.drawCircle(cx, cy, r - p.strokeWidth / 2, p)
                p.style = Paint.Style.FILL
                p.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 0.35f,
                    intArrayOf(withAlpha(-1, 0.85f), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx - r * 0.35f, cy - r * 0.4f, r * 0.35f, p)
            }
            FINISH_NEON -> {
                fill(darker(col, 0.82f))
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.36f
                p.color = withAlpha(col, 0.55f)
                p.maskFilter = BlurMaskFilter(r * 0.2f, BlurMaskFilter.Blur.NORMAL)
                clip { c.drawCircle(cx, cy, r * 0.66f, p) }
                p.maskFilter = null
                p.strokeWidth = r * (0.10f + 0.12f * detail)
                p.color = lighter(col, 0.25f)
                c.drawCircle(cx, cy, r * 0.66f, p)
                p.style = Paint.Style.FILL
            }
            FINISH_STRIPES -> {
                fill(col)
                p.color = withAlpha(lighter(col, 0.55f), 0.35f + 0.6f * detail)
                val band = r * 0.26f
                clip {
                    var x = cx - r - band * 0.5f
                    while (x < cx + r) {
                        c.drawRect(x, cy - r, x + band, cy + r, p)
                        x += band * 2.2f
                    }
                }
                shade(0.7f)
            }
            FINISH_DOTS -> {
                fill(col)
                p.color = withAlpha(lighter(col, 0.6f), 0.4f + 0.6f * detail)
                val step = r * 0.55f
                clip {
                    var row = 0
                    var y = cy - r
                    while (y < cy + r + step) {
                        var x = cx - r + if (row % 2 == 0) 0f else step / 2
                        while (x < cx + r + step) {
                            c.drawCircle(x, y, step * 0.2f, p)
                            x += step
                        }
                        y += step * 0.87f
                        row++
                    }
                }
                shade(0.7f)
            }
            FINISH_SCALES -> {
                fill(col)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.07f
                p.color = withAlpha(darker(col, 0.6f), 0.35f + 0.6f * detail)
                val s = r * 0.5f
                clip {
                    var row = 0
                    var y = cy - r - s
                    while (y < cy + r + s) {
                        var x = cx - r - s + if (row % 2 == 0) 0f else s
                        while (x < cx + r + s) {
                            // The open side of each scale faces the tail (-x).
                            c.drawArc(RectF(x - s, y - s, x + s, y + s), 90f, 180f, false, p)
                            x += s * 2f
                        }
                        y += s
                        row++
                    }
                }
                p.style = Paint.Style.FILL
                shade(0.8f)
            }
            FINISH_GLITTER -> {
                fill(col)
                shade(0.6f)
                val rnd = Random(bead.id.hashCode())
                val count = (40 + 180 * detail).roundToInt()
                clip {
                    repeat(count) {
                        val a = rnd.nextFloat() * 2f * PI.toFloat()
                        val d = r * kotlin.math.sqrt(rnd.nextFloat())
                        p.color = if (rnd.nextFloat() < 0.4f) withAlpha(-1, 0.9f) else lighter(hueShift(col, rnd.nextFloat() * 60f - 30f), 0.5f)
                        c.drawCircle(cx + cos(a) * d, cy + sin(a) * d, r * (0.015f + rnd.nextFloat() * 0.035f), p)
                    }
                }
            }
            FINISH_MARBLE -> {
                fill(col)
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                val rnd = Random(bead.id.hashCode())
                clip {
                    repeat(5) { k ->
                        p.color = withAlpha(if (k % 2 == 0) lighter(col, 0.7f) else darker(col, 0.4f), 0.25f + 0.5f * detail)
                        p.strokeWidth = r * (0.03f + rnd.nextFloat() * 0.06f)
                        p.maskFilter = BlurMaskFilter(r * 0.03f, BlurMaskFilter.Blur.NORMAL)
                        val path = Path()
                        var y = cy - r + rnd.nextFloat() * 2 * r
                        path.moveTo(cx - r, y)
                        var x = cx - r
                        while (x < cx + r) {
                            val nx = x + r * 0.4f
                            val ny = y + (rnd.nextFloat() - 0.5f) * r * 0.8f
                            path.quadTo(x + r * 0.2f, y + (rnd.nextFloat() - 0.5f) * r * 0.9f, nx, ny)
                            x = nx; y = ny
                        }
                        c.drawPath(path, p)
                    }
                    p.maskFilter = null
                }
                p.style = Paint.Style.FILL
                shade(0.6f)
            }
            FINISH_CARBON -> {
                fill(darker(col, 0.35f))
                val s = r * 0.24f
                p.color = withAlpha(lighter(col, 0.15f), 0.5f + 0.5f * detail)
                clip {
                    var j = 0
                    var y = cy - r
                    while (y < cy + r) {
                        var i = 0
                        var x = cx - r
                        while (x < cx + r) {
                            if ((i + j) % 2 == 0) c.drawRect(x, y, x + s, y + s, p)
                            x += s; i++
                        }
                        y += s; j++
                    }
                }
                shade(0.9f)
            }
            FINISH_GRADIENT -> {
                p.shader = LinearGradient(cx - r, cy, cx + r, cy,
                    intArrayOf(darker(hueShift(col, -30f * detail), 0.2f), col, lighter(hueShift(col, 45f * detail), 0.3f)),
                    null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
                shade(0.6f)
            }
            FINISH_RINGS -> {
                fill(col)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.12f
                p.color = withAlpha(darker(col, 0.5f), 0.4f + 0.55f * detail)
                var rr = r * 0.82f
                while (rr > r * 0.1f) {
                    c.drawCircle(cx, cy, rr, p)
                    rr -= r * 0.3f
                }
                p.style = Paint.Style.FILL
                shade(0.6f)
            }
            else -> fill(col)
        }
    }

    private fun featherEdge(c: Canvas, half: Float, r: Float, smoothness: Float) {
        val soft = smoothness.coerceIn(0f, 1f)
        val inner = (1f - 0.04f - soft * 0.32f).coerceIn(0.4f, 0.99f)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = RadialGradient(half, half, r, intArrayOf(-1, -1, 0), floatArrayOf(0f, inner, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, half * 2, half * 2, mask)
    }

    /** A small two-pass box blur, radius [radius] px. */
    private fun blurred(source: Bitmap, radius: Float): Bitmap {
        val rad = radius.roundToInt().coerceIn(1, 12)
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        fun pass(from: IntArray, to: IntArray, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                    for (k in -rad..rad) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val px = if (horizontal) from[line * w + j] else from[j * w + line]
                        val pa = px ushr 24
                        a += pa
                        rr += ((px shr 16) and 0xFF) * pa
                        g += ((px shr 8) and 0xFF) * pa
                        b += (px and 0xFF) * pa
                        n++
                    }
                    val idx = if (horizontal) line * w + i else i * w + line
                    to[idx] = if (a == 0) 0 else ((a / n) shl 24) or ((rr / a) shl 16) or ((g / a) shl 8) or (b / a)
                }
            }
        }
        pass(src, tmp, true)
        pass(tmp, src, false)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(src, 0, w, 0, 0, w, h)
        return out
    }

    /** Hue (degrees), saturation and brightness as one colour matrix. */
    /** The same adjustment for Compose (the bead editor's live view). */
    fun composeMatrix(hue: Float, saturation: Float, brightness: Float): androidx.compose.ui.graphics.ColorMatrix =
        androidx.compose.ui.graphics.ColorMatrix(adjustMatrix(hue, saturation, brightness).array)

    /** A bead's finish alone, unadjusted, for the bead editor's canvas. */
    fun renderBase(bead: StudioBead, size: Int, textures: SkinTextures?): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val half = size / 2f
        drawFinish(Canvas(out), bead, half, half, half * DISC, textures)
        return out
    }

    internal fun adjustMatrix(hue: Float, saturation: Float, brightness: Float): ColorMatrix {
        val m = ColorMatrix()
        if (hue != 0f) {
            // The luminance-preserving hue turn (the W3C feColorMatrix hueRotate).
            val a = hue * PI.toFloat() / 180f
            val cs = cos(a)
            val sn = sin(a)
            m.postConcat(ColorMatrix(floatArrayOf(
                0.213f + cs * 0.787f - sn * 0.213f, 0.715f - cs * 0.715f - sn * 0.715f, 0.072f - cs * 0.072f + sn * 0.928f, 0f, 0f,
                0.213f - cs * 0.213f + sn * 0.143f, 0.715f + cs * 0.285f + sn * 0.140f, 0.072f - cs * 0.072f - sn * 0.283f, 0f, 0f,
                0.213f - cs * 0.213f - sn * 0.787f, 0.715f - cs * 0.715f + sn * 0.715f, 0.072f + cs * 0.928f + sn * 0.072f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        if (saturation != 1f) m.postConcat(ColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 2f)) })
        if (brightness != 1f) {
            val b = brightness.coerceIn(0f, 2f)
            val lift = if (b > 1f) (b - 1f) * 90f else 0f
            val gain = if (b > 1f) 1f else b
            m.postConcat(ColorMatrix(floatArrayOf(
                gain, 0f, 0f, 0f, lift,
                0f, gain, 0f, 0f, lift,
                0f, 0f, gain, 0f, lift,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        return m
    }

    // ------------------------------------------------------------ eyes

    fun renderWhite(eye: StudioEye, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val half = size / 2f
        val r = half * DISC
        val col = eye.whiteColor or (0xFF shl 24)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (eye.whiteShape) {
            1 -> { // Almond: long across the head
                p.color = col
                c.drawOval(RectF(half - r * 0.62f, half - r, half + r * 0.62f, half + r), p)
            }
            2 -> { // Outline: a dark rim
                p.color = darker(col, 0.75f)
                c.drawCircle(half, half, r, p)
                p.color = col
                c.drawCircle(half, half, r * 0.8f, p)
            }
            3 -> { // Square
                p.color = col
                c.drawRoundRect(RectF(half - r * 0.7f, half - r * 0.7f, half + r * 0.7f, half + r * 0.7f), r * 0.18f, r * 0.18f, p)
            }
            4 -> { // Glow: a soft lit ball
                p.shader = RadialGradient(half, half, r, intArrayOf(lighter(col, 0.4f), col, withAlpha(col, 0f)),
                    floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(half, half, r, p)
            }
            else -> {
                p.color = col
                c.drawCircle(half, half, r, p)
            }
        }
        return out
    }

    fun renderPupil(eye: StudioEye, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val h = size / 2f
        val r = h * DISC
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eye.pupilColor or (0xFF shl 24) }
        fun star(points: Int, inner: Float, turn: Float = -90f): Path = Path().apply {
            for (i in 0 until points * 2) {
                val a = (turn + i * 180f / points) * PI.toFloat() / 180f
                val d = if (i % 2 == 0) r else r * inner
                val x = h + cos(a) * d
                val y = h + sin(a) * d
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        when (eye.pupilShape) {
            1 -> c.drawCircle(h, h, r * 0.5f, p)                                      // Dot
            2 -> c.drawOval(RectF(h - r * 0.3f, h - r, h + r * 0.3f, h + r), p)        // Cat
            3 -> c.drawRoundRect(RectF(h - r, h - r * 0.32f, h + r, h + r * 0.32f), r * 0.3f, r * 0.3f, p) // Goat
            4 -> c.drawPath(Path().apply {                                             // Heart
                moveTo(h, h + r * 0.85f)
                cubicTo(h - r * 1.35f, h - r * 0.05f, h - r * 0.55f, h - r * 1.05f, h, h - r * 0.42f)
                cubicTo(h + r * 0.55f, h - r * 1.05f, h + r * 1.35f, h - r * 0.05f, h, h + r * 0.85f)
                close()
            }, p)
            5 -> c.drawPath(star(5, 0.45f), p)                                         // Star
            6 -> c.drawPath(Path().apply {                                             // Diamond
                moveTo(h, h - r); lineTo(h + r * 0.7f, h); lineTo(h, h + r); lineTo(h - r * 0.7f, h); close()
            }, p)
            7 -> {                                                                     // Cross
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.42f; p.strokeCap = Paint.Cap.ROUND
                val k = r * 0.62f
                c.drawLine(h - k, h - k, h + k, h + k, p); c.drawLine(h - k, h + k, h + k, h - k, p)
            }
            8 -> {                                                                     // Plus
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.42f; p.strokeCap = Paint.Cap.ROUND
                val k = r * 0.72f
                c.drawLine(h - k, h, h + k, h, p); c.drawLine(h, h - k, h, h + k, p)
            }
            9 -> c.drawRect(RectF(h - r * 0.68f, h - r * 0.68f, h + r * 0.68f, h + r * 0.68f), p) // Square
            10 -> {                                                                    // Ring
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.3f
                c.drawCircle(h, h, r * 0.75f, p)
            }
            11 -> {                                                                    // Spiral
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.2f; p.strokeCap = Paint.Cap.ROUND
                val path = Path()
                var t = 0f
                path.moveTo(h, h)
                while (t < 3.2f * PI.toFloat()) {
                    val d = r * 0.9f * t / (3.2f * PI.toFloat())
                    path.lineTo(h + cos(t) * d, h + sin(t) * d)
                    t += 0.15f
                }
                c.drawPath(path, p)
            }
            12 -> {                                                                    // Flower
                for (i in 0 until 6) {
                    val a = i * PI.toFloat() / 3f
                    c.drawCircle(h + cos(a) * r * 0.5f, h + sin(a) * r * 0.5f, r * 0.38f, p)
                }
            }
            13 -> c.drawPath(Path().apply {                                            // Moon
                addCircle(h, h, r * 0.9f, Path.Direction.CW)
                op(Path().apply { addCircle(h + r * 0.45f, h - r * 0.2f, r * 0.75f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            }, p)
            14 -> c.drawPath(star(4, 0.28f), p)                                        // Spark
            15 -> {                                                                    // Twin
                c.drawCircle(h, h - r * 0.45f, r * 0.42f, p)
                c.drawCircle(h, h + r * 0.45f, r * 0.42f, p)
            }
            else -> c.drawCircle(h, h, r, p)                                           // Round
        }
        return out
    }

    // ------------------------------------------------------------ preview cache

    private val beadCache = LruCache<String, ImageBitmap>(96)
    private val eyeCache = LruCache<String, ImageBitmap>(48)

    /** [bead] for the Studio's screens, cached by its revision. */
    fun beadImage(bead: StudioBead, textures: SkinTextures?, size: Int = CELL): ImageBitmap {
        val key = "${bead.id}:${bead.rev}:${size}:${textures != null}"
        beadCache.get(key)?.let { return it }
        val image = renderBead(bead, SkinStudio.paint(bead.id).takeIf { bead.painted }, size, textures).asImageBitmap()
        beadCache.put(key, image)
        return image
    }

    fun whiteImage(eye: StudioEye): ImageBitmap {
        val key = "w:${eye.whiteShape}:${eye.whiteColor}"
        eyeCache.get(key)?.let { return it }
        return renderWhite(eye, CELL).asImageBitmap().also { eyeCache.put(key, it) }
    }

    fun pupilImage(eye: StudioEye): ImageBitmap {
        val key = "p:${eye.pupilShape}:${eye.pupilColor}"
        eyeCache.get(key)?.let { return it }
        return renderPupil(eye, CELL).asImageBitmap().also { eyeCache.put(key, it) }
    }

    // ------------------------------------------------------------ the atlas

    class Baked(val atlas: Bitmap, val text: String)

    /** The engine's atlas and text for [skin] (game/studio.h). Null when nothing can be worn. */
    fun bakeAtlas(skin: StudioSkin, textures: SkinTextures?): Baked? {
        val unique = skin.pattern.distinct().take(SkinStudio.MAX_DISTINCT)
        if (unique.isEmpty()) return null
        val atlas = Bitmap.createBitmap(ATLAS, ATLAS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(atlas)
        val colours = IntArray(GRID * GRID)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        fun place(cell: Int, bitmap: Bitmap) {
            canvas.drawBitmap(bitmap, ((cell % GRID) * CELL).toFloat(), ((cell / GRID) * CELL).toFloat(), paint)
            colours[cell] = averageRgb(bitmap)
            bitmap.recycle()
        }
        unique.forEachIndexed { cell, id ->
            val bead = SkinStudio.bead(id) ?: StudioBead(id)
            place(cell, renderBead(bead, SkinStudio.paint(id).takeIf { bead.painted }, CELL, textures))
        }
        val eyes = skin.eyes.take(SkinStudio.MAX_EYES)
        eyes.forEachIndexed { i, eye ->
            place(FIRST_WHITE + i, renderWhite(eye, CELL))
            place(FIRST_PUPIL + i, renderPupil(eye, CELL))
        }
        val index = unique.withIndex().associate { (cell, id) -> id to cell }
        val text = buildString {
            append("P:")
            append(skin.pattern.take(SkinStudio.MAX_PATTERN).joinToString(",") { (index[it] ?: 0).toString() })
            append("|C:")
            append(colours.joinToString(",") { String.format(java.util.Locale.US, "%06x", it and 0xFFFFFF) })
            if (eyes.isNotEmpty()) {
                append("|E:")
                append(eyes.withIndex().joinToString(";") { (i, e) ->
                    listOf(SkinStudio.num(e.fwd), SkinStudio.num(e.side), SkinStudio.num(e.white),
                        SkinStudio.num(e.pupil), SkinStudio.num(e.look),
                        (FIRST_WHITE + i).toString(), (FIRST_PUPIL + i).toString()).joinToString(",")
                })
            }
        }
        return Baked(atlas, text)
    }

    /** The picture's own colour: its pixels weighted by how opaque they are. */
    fun averageRgb(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(1, min(w, h) / 32)
        var r = 0L; var g = 0L; var b = 0L; var a = 0L
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bitmap.getPixel(x, y)
                val pa = (px ushr 24).toLong()
                r += ((px shr 16) and 0xFF) * pa
                g += ((px shr 8) and 0xFF) * pa
                b += (px and 0xFF) * pa
                a += pa
                x += step
            }
            y += step
        }
        if (a == 0L) return 0x000000
        return ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
    }

    // ------------------------------------------------------------ colour

    fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (color and 0xFFFFFF)

    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (ch((a shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or
            (ch((a shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or
            ch(a and 0xFF, b and 0xFF)
    }

    fun lighter(color: Int, t: Float): Int = blend(color, 0xFFFFFFFF.toInt(), t.coerceIn(0f, 1f))
    fun darker(color: Int, t: Float): Int = blend(color, 0xFF000000.toInt(), t.coerceIn(0f, 1f))

    fun hueShift(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
        return android.graphics.Color.HSVToColor(0xFF, hsv)
    }
}
