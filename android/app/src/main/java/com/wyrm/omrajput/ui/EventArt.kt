package com.wyrm.omrajput.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wyrm.omrajput.data.EventKind
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/*
 * The event card art (OM, 2026-10-30), one look per event type, drawn as the
 * prototype draws it (tools/events-prototype). Every snake is the game's own
 * beads and eye bead out of tex_atlas_8k.png, close together like the arena's
 * body points, growing from a thin far tail to a full head (depth). The body
 * rises from the bottom-right corner towards the top centre, and each type's
 * emblem sits just above the head, inside both the small and the big card.
 */

internal data class EventLook(val bg: List<Color>, val skin: String, val skin2: String = "")

internal fun eventLook(kind: EventKind): EventLook = when (kind) {
    EventKind.LAST_ONE_STANDING -> EventLook(listOf(Color(0xFFF2B544), Color(0xFFD9601F), Color(0xFF6E2310)), "ggwwyy")
    EventKind.TEAM_LAST_STANDING -> EventLook(listOf(Color(0xFF5B9BFF), Color(0xFF3D55D6), Color(0xFF1A2366)), "ll00ss")
    EventKind.LAST_DUO_STANDING -> EventLook(listOf(Color(0xFF22C1B8), Color(0xFF6D5BD0), Color(0xFF3A1F6E)), "uuccss", "mmaass")
    EventKind.PAINTBALL -> EventLook(listOf(Color(0xFF3A3F4B), Color(0xFF22252D), Color(0xFF111317)), ",,nnwwppuuzz")
    EventKind.BATTLEDOME -> EventLook(listOf(Color(0xFF46C893), Color(0xFF15805A), Color(0xFF0A3527)), "ppoohh")
    EventKind.OTHER -> EventLook(listOf(Color(0xFF6E5BFF), Color(0xFF3B2A9E), Color(0xFF150F3D)), "zzxxuuppwwnnmm")
}

/** Official bead characters -> colour group (SkinCatalog groupCodes). */
private const val BEAD_CHARS = "zxcvbnm,asdfghjklqwertyuiop1234567890-"
internal fun beadGroup(c: Char): Int {
    val i = BEAD_CHARS.indexOf(c)
    return when { i < 0 -> 9; i < 36 -> i; c == '0' -> 37; else -> 39 }
}
private const val EYE_BEAD = 40

private fun DrawScope.atlasCell(atlas: ImageBitmap, group: Int, cx: Float, cy: Float, size: Float, alpha: Float = 1f, filter: ColorFilter? = null) {
    val cw = atlas.width / SkinAtlas.COLUMNS
    val ch = atlas.height / SkinAtlas.ROWS
    val s = size.toInt().coerceAtLeast(1)
    drawImage(atlas, srcOffset = IntOffset((group % SkinAtlas.COLUMNS) * cw, (group / SkinAtlas.COLUMNS) * ch), srcSize = IntSize(cw, ch),
        dstOffset = IntOffset((cx - size / 2f).toInt(), (cy - size / 2f).toInt()), dstSize = IntSize(s, s), alpha = alpha, colorFilter = filter)
}

private val PUPIL_INK = ColorFilter.tint(Color(0xFF0A0A0D), BlendMode.SrcIn)

/** A cubic from (x0,y0) to (x3,y3), in fractions of the card. */
private class Curve(val pts: FloatArray)

private fun cubic(w: Float, h: Float, vararg f: Float): Curve {
    val p = FloatArray(8) { if (it % 2 == 0) f[it] * w else f[it] * h }
    val n = 160
    val out = FloatArray((n + 1) * 2)
    for (i in 0..n) {
        val t = i / n.toFloat(); val u = 1 - t
        out[i * 2] = u * u * u * p[0] + 3 * u * u * t * p[2] + 3 * u * t * t * p[4] + t * t * t * p[6]
        out[i * 2 + 1] = u * u * u * p[1] + 3 * u * u * t * p[3] + 3 * u * t * t * p[5] + t * t * t * p[7]
    }
    return Curve(out)
}

private fun polyline(points: List<Offset>): Curve = Curve(FloatArray(points.size * 2) { if (it % 2 == 0) points[it / 2].x else points[it / 2].y })

/** Points every `step` along the curve, from its end (the head) back. */
private fun walk(curve: Curve, step: Float): List<Offset> {
    val p = curve.pts
    val count = p.size / 2
    val cum = FloatArray(count)
    for (i in 1 until count) cum[i] = cum[i - 1] + hypot(p[i * 2] - p[i * 2 - 2], p[i * 2 + 1] - p[i * 2 - 1])
    val total = cum[count - 1]
    val out = ArrayList<Offset>()
    var d = total
    var j = count - 1
    while (d >= 0f) {
        while (j > 0 && cum[j - 1] > d) j--
        val a = if (j == 0) 0 else j - 1
        val span = (cum[j] - cum[a]).takeIf { it > 0f } ?: 1f
        val k = ((d - cum[a]) / span).coerceIn(0f, 1f)
        out.add(Offset(p[a * 2] + (p[j * 2] - p[a * 2]) * k, p[a * 2 + 1] + (p[j * 2 + 1] - p[a * 2 + 1]) * k))
        d -= step
    }
    return out
}

private fun DrawScope.snake(atlas: ImageBitmap?, curve: Curve, code: String, r: Float, alpha: Float = 1f, eyes: Boolean = true, look: Float = 0f, tail: Float = .42f) {
    if (atlas == null || code.isEmpty()) return
    val beads = walk(curve, r * .42f)
    val n = beads.size
    if (n == 0) return
    val groups = code.map(::beadGroup)
    for (i in n - 1 downTo 0) {
        val grow = tail + (1 - tail) * (1 - i / maxOf(1, n - 1).toFloat())
        atlasCell(atlas, groups[(i / 3) % groups.size], beads[i].x, beads[i].y, 2 * r * grow, alpha)
    }
    if (!eyes || n < 2) return
    val head = beads[0]; val back = beads[minOf(2, n - 1)]
    val ang = atan2(head.y - back.y, head.x - back.x)
    val u = r / 14.5f
    for (side in intArrayOf(-1, 1)) {
        val px = cos(ang + side * PI.toFloat() / 2); val py = sin(ang + side * PI.toFloat() / 2)
        val ex = head.x + cos(ang) * 6 * u + px * 6.5f * u
        val ey = head.y + sin(ang) * 6 * u + py * 6.5f * u
        atlasCell(atlas, EYE_BEAD, ex, ey, 12 * u, alpha)
        atlasCell(atlas, EYE_BEAD, ex + cos(ang + look) * 2 * u, ey + sin(ang + look) * 2 * u, 7 * u, alpha, PUPIL_INK)
    }
}

private fun DrawScope.sparkle(x: Float, y: Float, s: Float, color: Color = Color.White) {
    val p = Path().apply {
        moveTo(x, y - s); lineTo(x + s * .3f, y - s * .3f); lineTo(x + s, y); lineTo(x + s * .3f, y + s * .3f)
        lineTo(x, y + s); lineTo(x - s * .3f, y + s * .3f); lineTo(x - s, y); lineTo(x - s * .3f, y - s * .3f); close()
    }
    drawPath(p, color.copy(alpha = .85f))
}

private fun DrawScope.splat(x: Float, y: Float, radius: Float, color: Color, rand: Random) {
    val n = 11
    val pts = (0 until n).map { i ->
        val a = i / n.toFloat() * 2 * PI.toFloat()
        val rr = radius * if (i % 2 == 1) .62f + rand.nextFloat() * .2f else .9f + rand.nextFloat() * .45f
        Offset(x + cos(a) * rr, y + sin(a) * rr)
    }
    val path = Path()
    for (i in 0 until n) {
        val p = pts[i]; val q = pts[(i + 1) % n]
        val m = Offset((p.x + q.x) / 2, (p.y + q.y) / 2)
        if (i == 0) path.moveTo(m.x, m.y) else path.quadraticBezierTo(p.x, p.y, m.x, m.y)
    }
    val first = pts[0]; val m0 = Offset((first.x + pts[1].x) / 2, (first.y + pts[1].y) / 2)
    path.quadraticBezierTo(first.x, first.y, m0.x, m0.y)
    path.close()
    drawPath(path, color.copy(alpha = .8f))
    repeat(5) {
        val a = rand.nextFloat() * 2 * PI.toFloat(); val d = radius * (1.4f + rand.nextFloat() * 1.1f)
        drawCircle(color.copy(alpha = .8f), radius * (.08f + rand.nextFloat() * .14f), Offset(x + cos(a) * d, y + sin(a) * d))
    }
}

/**
 * Every event picture is drawn once at its pixel size and kept (OM, 2026-10-30):
 * a card used to redraw hundreds of beads, dots and sparkles on every frame of a
 * scroll and every countdown tick (the 20 FPS lists). Up to 24 MB of pictures.
 */
internal object EventArtCache {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(atlas: ImageBitmap, kind: EventKind, seed: String, big: Boolean, width: Int, height: Int,
            density: androidx.compose.ui.unit.Density): ImageBitmap {
        val key = "$kind|$seed|$big|$width|$height"
        cache.get(key)?.let { return it }
        val picture = ImageBitmap(width, height)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density, androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(picture),
            androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()),
        ) { drawEventArt(atlas, kind, seed, big) }
        cache.put(key, picture)
        return picture
    }
}

@Composable
internal fun EventArt(kind: EventKind, seed: String, big: Boolean, modifier: Modifier = Modifier) {
    val atlas = rememberSkinAtlas().value
    Canvas(modifier) {
        val width = size.width.toInt()
        val height = size.height.toInt()
        if (width <= 0 || height <= 0) return@Canvas
        // Before the atlas exists (only the first second of a fresh start): the
        // ground and emblem without beads, never kept.
        if (atlas == null) { drawEventArt(null, kind, seed, big); return@Canvas }
        drawImage(EventArtCache.get(atlas, kind, seed, big, width, height, this))
    }
}

internal fun DrawScope.drawEventArt(atlas: ImageBitmap?, kind: EventKind, seed: String, big: Boolean) {
    val w = size.width; val h = size.height
    val look = eventLook(kind)
    val rand = Random(seed.hashCode())
    val unit = w / 390f
    val r = (if (big) 17f else 14f) * unit
    // Ground: the type's gradient, a dot grid, a soft light top right.
    drawRect(Brush.linearGradient(listOf(look.bg[0], look.bg[1], look.bg[2]), Offset.Zero, Offset(w, h)))
    val step = 14f * unit
    var gy = 2f * unit
    while (gy < h) { var gx = 2f * unit; while (gx < w) { drawCircle(Color.White.copy(alpha = .12f), 1.1f * unit, Offset(gx, gy)); gx += step }; gy += step }
    fun c(vararg f: Float) = cubic(w, h, *f)
    val hx = .64f; val hy = .36f
    val main = c(1.06f, .98f, .82f, .94f, .94f, .52f, hx, hy)
    val ex = w * hx; val ey = h * hy - r * 2.8f
    val ghost = "ii33"; val enemy = "55,,"
    when (kind) {
        EventKind.LAST_ONE_STANDING -> {
            drawPath(Path().apply { moveTo(w * .5f, 0f); lineTo(w * .78f, 0f); lineTo(w * 1.06f, h); lineTo(w * .56f, h); close() },
                Brush.verticalGradient(listOf(Color.White.copy(alpha = .3f), Color.Transparent)))
            repeat(24) { drawCircle(Color.White.copy(alpha = .1f + rand.nextFloat() * .3f), (1f + rand.nextFloat() * 2.6f) * unit, Offset(w * (.56f + rand.nextFloat() * .44f), h * (.3f + rand.nextFloat() * .6f))) }
            snake(atlas, c(.56f, .94f, .58f, .88f, .62f, .9f, .64f, .82f), ghost, r * .6f, .3f, eyes = false)
            snake(atlas, c(1.04f, .44f, 1f, .4f, .98f, .46f, .95f, .4f), ghost, r * .55f, .25f, eyes = false)
            snake(atlas, main, look.skin, r, look = -.5f)
            translate(ex, ey) {
                rotate(-6f, Offset.Zero) {
                    scale(.85f * unit, Offset.Zero) {
                        val crown = Path().apply { moveTo(-20f, 10f); lineTo(-24f, -9f); lineTo(-11f, 0f); lineTo(0f, -16f); lineTo(11f, 0f); lineTo(24f, -9f); lineTo(20f, 10f); close() }
                        drawPath(crown, Color(0xFFFDE68A))
                        drawPath(crown, Color.White, style = Stroke(2.4f, join = StrokeJoin.Round))
                        drawRoundRect(Color(0xFFF59E0B), Offset(-20f, 10f), androidx.compose.ui.geometry.Size(40f, 6f), androidx.compose.ui.geometry.CornerRadius(2f))
                        for (o in listOf(Offset(-24f, -9f), Offset(0f, -16f), Offset(24f, -9f))) drawCircle(Color.White, 3.2f, o)
                    }
                }
            }
            sparkle(ex - 34 * unit, ey + 8 * unit, 5 * unit); sparkle(ex + 30 * unit, ey - 2 * unit, 4 * unit)
        }
        EventKind.TEAM_LAST_STANDING -> {
            // A squad in three lanes: the leader in front, two behind it on either side.
            fun lane(side: Int): Curve {
                val ox = (.95f * 30f + .31f * 27f * side) * unit / w; val oy = (.31f * 30f - .95f * 27f * side) * unit / h
                return c(1.06f + ox, .98f + oy, .82f + ox, .94f + oy, .94f + ox, .52f + oy, hx + ox, hy + oy)
            }
            snake(atlas, c(.58f, .94f, .6f, .88f, .62f, .9f, .64f, .84f), enemy, r * .55f, .4f)
            snake(atlas, lane(1), look.skin, r * .74f, look = -.5f)
            snake(atlas, lane(-1), look.skin, r * .74f, look = -.5f)
            snake(atlas, main, look.skin, r * .86f, look = -.5f)
            translate(ex, ey) {
                scale(.85f * unit, Offset.Zero) {
                    val shield = Path().apply { moveTo(0f, -20f); lineTo(18f, -13f); lineTo(18f, 3f); cubicTo(18f, 15f, 9f, 22f, 0f, 26f); cubicTo(-9f, 22f, -18f, 15f, -18f, 3f); lineTo(-18f, -13f); close() }
                    drawPath(shield, Color.White.copy(alpha = .2f)); drawPath(shield, Color.White, style = Stroke(2.4f))
                    for (o in listOf(Offset(-6f, 0f), Offset(6f, 0f), Offset(0f, 9f))) drawCircle(Color.White, 3.6f, o)
                }
            }
        }
        EventKind.LAST_DUO_STANDING -> {
            drawPath(Path().apply { moveTo(w * .55f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(w * .4f, h); close() }, Color.White.copy(alpha = .06f))
            val x0 = w * 1.04f; val y0 = h * .98f; val x1 = w * hx; val y1 = h * hy
            val len = hypot(x1 - x0, y1 - y0); val px = -(y1 - y0) / len; val py = (x1 - x0) / len
            fun wave(phase: Float, side: Int) = polyline((0..40).map { i ->
                val s = i / 40f; val off = sin(s * PI.toFloat() * 1.6f + phase) * r * .7f + side * r * .62f
                Offset(x0 + (x1 - x0) * s + px * off, y0 + (y1 - y0) * s + py * off)
            })
            snake(atlas, wave(0f, -1), look.skin, r * .84f, look = -.5f)
            snake(atlas, wave(PI.toFloat(), 1), look.skin2, r * .84f, look = -.5f)
            drawCircle(Color.White, 9f * unit, Offset(ex - 7 * unit, ey), style = Stroke(2.6f * unit))
            drawCircle(Color.White, 9f * unit, Offset(ex + 7 * unit, ey), style = Stroke(2.6f * unit))
        }
        EventKind.PAINTBALL -> {
            val colors = listOf(Color(0xFFFB7185), Color(0xFFFDE047), Color(0xFF22D3EE), Color(0xFFA3E635), Color(0xFFC084FC), Color(0xFFFB923C))
            repeat(7) { i -> splat(w * (.56f + rand.nextFloat() * .44f), h * (.25f + rand.nextFloat() * .7f), h * (.06f + rand.nextFloat() * .09f), colors[i % colors.size], rand) }
            repeat(3) { i ->
                val x = w * (.74f + rand.nextFloat() * .22f); val y0 = h * (.22f + rand.nextFloat() * .2f); val dl = h * (.12f + rand.nextFloat() * .16f)
                val col = colors[(i + 2) % colors.size].copy(alpha = .7f)
                drawLine(col, Offset(x, y0), Offset(x, y0 + dl), (2f + rand.nextFloat() * 2.5f) * unit, StrokeCap.Round)
                drawCircle(col, 3f * unit, Offset(x, y0 + dl + 4 * unit))
            }
            snake(atlas, main, look.skin, r, look = -.5f)
            drawCircle(Color.White, 11f * unit, Offset(ex, ey))
            drawCircle(Color(0xFFFB7185), 4.5f * unit, Offset(ex, ey))
            for ((a, b) in listOf(Offset(-18f, 0f) to Offset(-13f, 0f), Offset(13f, 0f) to Offset(18f, 0f), Offset(0f, -18f) to Offset(0f, -13f), Offset(0f, 13f) to Offset(0f, 18f))) {
                drawLine(Color.White, Offset(ex + a.x * unit, ey + a.y * unit), Offset(ex + b.x * unit, ey + b.y * unit), 2.4f * unit, StrokeCap.Round)
            }
        }
        EventKind.BATTLEDOME -> {
            val cx = w * .8f; val cy = h * if (big) .4f else .5f; val rad = h * if (big) .27f else .3f
            drawCircle(Color.White.copy(alpha = .55f), rad, Offset(cx, cy), style = Stroke(2.5f * unit, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * unit, 9f * unit))))
            drawCircle(Color.White.copy(alpha = .09f), rad * .78f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = .34f), rad * .78f, Offset(cx, cy), style = Stroke(1.6f * unit))
            for (i in 0 until 4) {
                val a = i * PI.toFloat() / 2 + PI.toFloat() / 4
                val o = Offset(cx + cos(a) * rad * 1.16f, cy + sin(a) * rad * 1.16f); val t = Offset(cx + cos(a) * rad * 1.03f, cy + sin(a) * rad * 1.03f)
                drawLine(Color.White.copy(alpha = .8f), o, t, 2.4f * unit, StrokeCap.Round)
                val dir = atan2(t.y - o.y, t.x - o.x); val s = 4.5f * unit
                drawPath(Path().apply { moveTo(t.x + cos(dir) * s, t.y + sin(dir) * s); lineTo(t.x + cos(dir + 2.4f) * s, t.y + sin(dir + 2.4f) * s); lineTo(t.x + cos(dir - 2.4f) * s, t.y + sin(dir - 2.4f) * s); close() }, Color.White.copy(alpha = .8f))
            }
            val coil = (0..60).map { i -> val a = -PI.toFloat() * .1f + i / 60f * PI.toFloat() * 1.9f; val rr = rad * (.64f - i / 60f * .36f); Offset(cx + cos(a) * rr, cy + sin(a) * rr) }
            snake(atlas, polyline(coil), look.skin, r * .86f)
            snake(atlas, c(.56f, .94f, .58f, .88f, .62f, .9f, .64f, .84f), enemy, r * .55f, .55f)
        }
        EventKind.OTHER -> {
            // Northern lights, bead confetti, one bright snake and a star.
            val aurora = Brush.horizontalGradient(listOf(Color(0x005EEAD4), Color(0x615EEAD4), Color(0x57C084FC), Color(0x00F472B6)), w * .3f, w * 1.05f)
            for (i in 0 until 3) {
                val y = h * (.12f + i * .16f); val amp = h * (.08f + i * .03f)
                drawPath(Path().apply { moveTo(w * .3f, y + amp); cubicTo(w * .5f, y - amp, w * .7f, y + amp * 1.6f, w * 1.05f, y - amp * .4f) }, aurora,
                    alpha = .9f - i * .25f, style = Stroke(h * (.09f - i * .02f), cap = StrokeCap.Round))
            }
            repeat(40) { drawCircle(Color.White.copy(alpha = .2f + rand.nextFloat() * .6f), (.6f + rand.nextFloat() * 1.3f) * unit, Offset(w * (.3f + rand.nextFloat() * .7f), h * rand.nextFloat())) }
            val conf = "zxcvbnm,wpu"
            if (atlas != null) repeat(9) {
                val s = r * (.5f + rand.nextFloat() * .55f)
                atlasCell(atlas, beadGroup(conf[rand.nextInt(conf.length)]), w * (.6f + rand.nextFloat() * .38f), h * (.18f + rand.nextFloat() * .62f), s, .55f + rand.nextFloat() * .4f)
            }
            snake(atlas, main, look.skin, r, look = -.5f)
            translate(ex, ey) {
                scale(unit, Offset.Zero) {
                    val star = Path().apply {
                        val pts = floatArrayOf(0f, -15f, 4.4f, -4.6f, 15.6f, -4.4f, 6.8f, 2.6f, 9.8f, 13.6f, 0f, 7.2f, -9.8f, 13.6f, -6.8f, 2.6f, -15.6f, -4.4f, -4.4f, -4.6f)
                        moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }; close()
                    }
                    drawPath(star, Color(0xFFFDE68A)); drawPath(star, Color.White, style = Stroke(2.2f, join = StrokeJoin.Round))
                }
            }
            sparkle(ex - 30 * unit, ey + 2 * unit, 4.5f * unit); sparkle(ex + 26 * unit, ey + 10 * unit, 3.5f * unit)
        }
    }
    // Shade for the text on the left and the buttons along the bottom.
    drawRect(Brush.linearGradient(listOf(Color.Black.copy(alpha = .32f), Color.Transparent, Color.Black.copy(alpha = .25f)), Offset.Zero, Offset(w, h)))
}

/** The minimap of an arena, drawn from the monitor's cells (three tones). */
internal fun DrawScope.drawArenaMinimap(map: com.wyrm.omrajput.data.ArenaMinimap?) {
    val d = minOf(size.width, size.height)
    drawCircle(Color(0xFF101713), d / 2f, Offset(size.width / 2, size.height / 2))
    drawCircle(Color(0xFF78B998), d / 2f * .98f, Offset(size.width / 2, size.height / 2), style = Stroke(1f))
    if (map == null || map.size <= 0) return
    val cell = d / map.size
    val ox = (size.width - d) / 2; val oy = (size.height - d) / 2
    map.bitmap?.let { image ->
        drawImage(image.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(d.toInt(), d.toInt()))
        return
    }
    val tones = listOf(Color(0xF5F4F6F2), Color(0xE69BD9BA), Color(0xBFD8EADF))
    map.cells.forEachIndexed { tone, cells ->
        val col = tones[tone % tones.size]
        for (index in cells) {
            val x = index % map.size; val y = index / map.size
            drawRect(col, Offset(ox + (x + .2f) * cell, oy + (y + .2f) * cell), androidx.compose.ui.geometry.Size(cell * .6f + .6f, cell * .6f + .6f))
        }
    }
}

