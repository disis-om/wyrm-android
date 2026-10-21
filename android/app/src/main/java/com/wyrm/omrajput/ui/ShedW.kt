package com.wyrm.omrajput.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Shed's W (OM, 2026-10-15): one dotted Wyrm W that breathes in 3D at rest
 * and, on Shed, turns into the snake's head bead (facing up, two eyes) and
 * back. A port of the HTML prototype's wyrmModes.js (`shedFrame`): the W is
 * the logo's exact stroke (WyrmMark.kt's three curves, stroke 0.16, round
 * caps) as three nested breathing outlines; the two middle peaks become the
 * eyes, the arms wrap into the head's border, every dot moves straight in at
 * its own angle (nothing turns). Eyes use the game's numbers (redraw.c /
 * oef.c): head radius 14.5, eye 6 forward 6.5 aside radius 6, pupil radius
 * 3.5 looking up to 2.3; the pupil is dots in clean rings, the rest of the
 * eye stays empty. Dots are painted like thinking-orbs' painter: matte, far
 * to near, in the theme's own ink.
 */
internal object ShedW {
    class Dot(val x: Float, val y: Float, val z: Float, val r: Float, val white: Float, val a: Float)

    private const val TAU = 2 * PI
    private const val H = 0.78
    private const val HALF = 0.08
    private val CURVES = arrayOf(
        doubleArrayOf(0.06, 0.10, 0.13, 0.92, 0.30, 0.96, 0.36, 0.38),
        doubleArrayOf(0.36, 0.38, 0.42, 0.94, 0.58, 0.94, 0.64, 0.38),
        doubleArrayOf(0.64, 0.38, 0.70, 0.96, 0.87, 0.92, 0.94, 0.10),
    )

    private fun bez(c: DoubleArray, t: Double): DoubleArray {
        val m = 1 - t
        val a = m * m * m; val b = 3 * m * m * t; val d = 3 * m * t * t; val e = t * t * t
        return doubleArrayOf(a * c[0] + b * c[2] + d * c[4] + e * c[6], (a * c[1] + b * c[3] + d * c[5] + e * c[7]) * H)
    }
    private fun bezD(c: DoubleArray, t: Double): DoubleArray {
        val m = 1 - t
        val dx = 3 * m * m * (c[2] - c[0]) + 6 * m * t * (c[4] - c[2]) + 3 * t * t * (c[6] - c[4])
        val dy = (3 * m * m * (c[3] - c[1]) + 6 * m * t * (c[5] - c[3]) + 3 * t * t * (c[7] - c[5])) * H
        val l = hypot(dx, dy).takeIf { it > 0 } ?: 1.0
        return doubleArrayOf(dx / l, dy / l)
    }

    // logo coordinates -> plane: centred on the origin, y up
    private val CENTER: DoubleArray by lazy {
        var x0 = 1e9; var x1 = -1e9; var y0 = 1e9; var y1 = -1e9
        for (c in CURVES) for (i in 0..200) {
            val p = bez(c, i / 200.0)
            x0 = min(x0, p[0]); x1 = max(x1, p[0]); y0 = min(y0, p[1]); y1 = max(y1, p[1])
        }
        doubleArrayOf((x0 + x1) / 2, (y0 + y1) / 2)
    }
    private fun plane(p: DoubleArray) = doubleArrayOf(p[0] - CENTER[0], -(p[1] - CENTER[1]))

    private class LinePt(val x: Double, val y: Double, val ci: Int) { var s = 0.0; var u = 0.0 }
    private class End(val x: Double, val y: Double, val tx: Double, val ty: Double, val nx: Double, val ny: Double)

    // centreline, each curve resampled evenly by arc length
    private val LINE: List<LinePt> by lazy {
        val pts = mutableListOf<LinePt>()
        CURVES.forEachIndexed { ci, c ->
            val n = 600
            val ts = DoubleArray(n + 1); val ls = DoubleArray(n + 1)
            var len = 0.0
            var prev = bez(c, 0.0)
            for (i in 0..n) {
                val t = i.toDouble() / n
                val p = bez(c, t)
                len += hypot(p[0] - prev[0], p[1] - prev[1])
                prev = p
                ts[i] = t; ls[i] = len
            }
            val per = 300
            var j = 0
            for (k in 0..per) {
                if (ci > 0 && k == 0) continue
                val s = k.toDouble() / per * len
                while (j < n - 1 && ls[j + 1] < s) j++
                val f = (s - ls[j]) / max(1e-9, ls[j + 1] - ls[j])
                val t = ts[j] + (ts[j + 1] - ts[j]) * f
                val p = plane(bez(c, t))
                pts.add(LinePt(p[0], p[1], ci))
            }
        }
        var acc = 0.0
        pts.forEachIndexed { i, p -> if (i > 0) acc += hypot(p.x - pts[i - 1].x, p.y - pts[i - 1].y); p.s = acc }
        pts.forEach { it.u = it.s / acc }
        pts
    }

    // the six stroke ends (round caps)
    private val ENDS: List<End> by lazy {
        val out = mutableListOf<End>()
        for (c in CURVES) for ((t, sgn) in listOf(0.0 to -1.0, 1.0 to 1.0)) {
            val p = plane(bez(c, t))
            val d = bezD(c, t)
            val tx = d[0]; val ty = -d[1]
            out.add(End(p[0], p[1], tx * sgn, ty * sgn, -ty, tx))
        }
        out
    }

    // distance to the centreline, as a grid (only a band around the line)
    private const val DF_RES = 0.005
    private const val DF_X0 = -0.62
    private const val DF_Y0 = -0.5
    private val DF_NX = ceil(1.24 / DF_RES).toInt() + 1
    private val DF_NY = ceil(1.0 / DF_RES).toInt() + 1
    private val DF: FloatArray by lazy {
        val grid = FloatArray(DF_NX * DF_NY) { 1f }
        val rc = ceil(0.16 / DF_RES).toInt()
        val line = LINE
        var k = 0
        while (k < line.size) {
            val p = line[k]
            val ci = ((p.x - DF_X0) / DF_RES).roundToInt(); val cj = ((p.y - DF_Y0) / DF_RES).roundToInt()
            for (j in max(0, cj - rc)..min(DF_NY - 1, cj + rc)) {
                val dy = DF_Y0 + j * DF_RES - p.y
                for (i in max(0, ci - rc)..min(DF_NX - 1, ci + rc)) {
                    val dx = DF_X0 + i * DF_RES - p.x
                    val d = sqrt(dx * dx + dy * dy).toFloat()
                    if (d < grid[j * DF_NX + i]) grid[j * DF_NX + i] = d
                }
            }
            k += 2
        }
        grid
    }
    private fun distLine(x: Double, y: Double): Double {
        val gx = (x - DF_X0) / DF_RES; val gy = (y - DF_Y0) / DF_RES
        if (gx < 0 || gy < 0 || gx >= DF_NX - 1 || gy >= DF_NY - 1) return 1.0
        val i = floor(gx).toInt(); val j = floor(gy).toInt(); val fx = gx - i; val fy = gy - j
        val g = DF; val n = DF_NX
        val a = g[j * n + i].toDouble(); val b = g[j * n + i + 1].toDouble()
        val c = g[(j + 1) * n + i].toDouble(); val d = g[(j + 1) * n + i + 1].toDouble()
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
    }

    /** The logo's outline (union of the three round-capped strokes), unordered. */
    private fun outline(half: Double, spacing: Double): List<DoubleArray> {
        val out = mutableListOf<DoubleArray>()
        for (c in CURVES) for (side in intArrayOf(1, -1)) {
            var acc = spacing
            var prev: DoubleArray? = null
            for (i in 0..400) {
                val t = i / 400.0
                val b = plane(bez(c, t))
                val d = bezD(c, t)
                val tx = d[0]; val ty = -d[1]
                val p = doubleArrayOf(b[0] - ty * half * side, b[1] + tx * half * side)
                prev?.let { acc += hypot(p[0] - it[0], p[1] - it[1]) }
                prev = p
                if (acc >= spacing) { acc = 0.0; if (distLine(p[0], p[1]) >= half * 0.965) out.add(p) }
            }
        }
        val n = max(3, (PI * half / spacing).roundToInt())
        for (e in ENDS) for (k in 1 until n) {
            val a = k.toDouble() / n * PI
            val p = doubleArrayOf(e.x + (e.nx * cos(a) + e.tx * sin(a)) * half, e.y + (e.ny * cos(a) + e.ty * sin(a)) * half)
            if (distLine(p[0], p[1]) >= half * 0.965) out.add(p)
        }
        return out
    }

    /** Outline dots as one closed loop, resampled to exactly [m], counter-clockwise from the top left. */
    private fun contour(m: Int, half: Double): List<DoubleArray> {
        val pts = outline(half, 0.006)
        var cur = pts.reduce { b, p -> if (p[1] - p[0] > b[1] - b[0]) p else b }
        val used = HashSet<DoubleArray>().apply { add(cur) }
        val loop = mutableListOf(cur)
        while (used.size < pts.size) {
            var best: DoubleArray? = null
            var bd = 1e9
            for (p in pts) {
                if (p in used) continue
                val d = (p[0] - cur[0]) * (p[0] - cur[0]) + (p[1] - cur[1]) * (p[1] - cur[1])
                if (d < bd) { bd = d; best = p }
            }
            if (best == null || bd > 0.05 * 0.05) break
            used.add(best); loop.add(best); cur = best
        }
        var area = 0.0
        for (i in loop.indices) { val a = loop[i]; val b = loop[(i + 1) % loop.size]; area += a[0] * b[1] - b[0] * a[1] }
        if (area < 0) loop.reverse()
        val len = DoubleArray(loop.size + 1)
        for (i in 1..loop.size) { val a = loop[i - 1]; val b = loop[i % loop.size]; len[i] = len[i - 1] + hypot(b[0] - a[0], b[1] - a[1]) }
        val total = len[loop.size]
        val out = mutableListOf<DoubleArray>()
        var j = 0
        for (k in 0 until m) {
            val s = k.toDouble() / m * total
            while (len[j + 1] < s) j++
            val f = (s - len[j]) / max(1e-9, len[j + 1] - len[j])
            val a = loop[j]; val b = loop[(j + 1) % loop.size]
            out.add(doubleArrayOf(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
        }
        return out
    }

    // ---------------------------------------------------------- the face

    private const val HEAD_X = 0.0
    private const val HEAD_Y = -0.02
    private const val HEAD_R = 0.31
    private const val U = HEAD_R / 14.5
    private const val EYE_R = 6 * U
    private const val PUPIL_R = 3.5 * U
    private const val LOOK_R = 2.3 * U
    private val EYE_C = arrayOf(doubleArrayOf(HEAD_X - 6.5 * U, HEAD_Y + 6 * U), doubleArrayOf(HEAD_X + 6.5 * U, HEAD_Y + 6 * U))
    private val PUPIL_C = arrayOf(doubleArrayOf(HEAD_X - 6 * U, HEAD_Y + 6.5 * U), doubleArrayOf(HEAD_X + 6 * U, HEAD_Y + 6.5 * U))

    // the pupil in clean rings so it reads as a round disc: centre, 9, 18
    private val PUPIL_DISK: List<DoubleArray> = buildList {
        add(doubleArrayOf(0.0, 0.0))
        for ((n, r, off) in listOf(Triple(9, 0.47, 0.0), Triple(18, 0.94, PI / 18))) for (i in 0 until n) {
            val a = off + i.toDouble() / n * TAU
            add(doubleArrayOf(cos(a) * r, sin(a) * r))
        }
    }

    private val LANE_HALF = doubleArrayOf(1.0, 0.7, 0.4)
    private val LANE_N = intArrayOf(240, 200, 160)
    private val BREATH = doubleArrayOf(0.9, 1.06)
    private val HEAD_RING = doubleArrayOf(1.0, 0.93, 0.86)
    private val RING_SLOTS = intArrayOf(150, 138, 126)
    private const val EYE_SLOTS = 40

    private enum class Kind { RING, EYE, PUPIL }
    private class Move(val i: Int, val kind: Kind, val toX: Double, val toY: Double, val eye: Int, val pj: Int, val fade: Boolean, val delay: Double)
    private class Born(val kind: Kind, val x: Double, val y: Double, val eye: Int, val pj: Int)
    private class Lane(val lo: List<DoubleArray>, val hi: List<DoubleArray>, val moves: List<Move>, val born: List<Born>)

    private fun inEye(x: Double, y: Double) = EYE_C.any { hypot(x - it[0], y - it[1]) < EYE_R * 1.12 }
    private fun slotOf(a: Double, n: Int): Int = (((((a + PI) / TAU) * n).let { Math.round(it).toInt() } % n) + n) % n

    private val LANES: List<Lane> by lazy {
        val peaks = arrayOf(doubleArrayOf(ENDS[1].x, ENDS[1].y), doubleArrayOf(ENDS[3].x, ENDS[3].y))
        LANE_HALF.indices.map { L ->
            val lh = LANE_HALF[L]
            val lo = contour(LANE_N[L], HALF * lh * BREATH[0])
            val hi = contour(LANE_N[L], HALF * lh * BREATH[1])
            val moves = mutableListOf<Move>()
            val born = mutableListOf<Born>()
            val ringFill = BooleanArray(RING_SLOTS[L])
            val eyeFill = Array(2) { BooleanArray(EYE_SLOTS) }
            val pupilFill = Array(2) { BooleanArray(PUPIL_DISK.size) }
            fun ringPos(k: Int): DoubleArray {
                val a = -PI + k.toDouble() / RING_SLOTS[L] * TAU
                val r = HEAD_R * HEAD_RING[L]
                return doubleArrayOf(HEAD_X + cos(a) * r, HEAD_Y + sin(a) * r)
            }
            fun eyePos(e: Int, k: Int): DoubleArray {
                val a = -PI + k.toDouble() / EYE_SLOTS * TAU
                return doubleArrayOf(EYE_C[e][0] + cos(a) * EYE_R, EYE_C[e][1] + sin(a) * EYE_R)
            }
            lo.forEachIndexed { i, p ->
                val x = p[0]; val y = p[1]
                var e = -1
                for (k in 0 until 2) if (hypot(x - peaks[k][0], y - peaks[k][1]) < HALF * lh * 1.1 + 0.075) e = k
                val delay = clamp01(0.55 - hypot(x - HEAD_X, y - HEAD_Y)) * 0.2
                when {
                    e < 0 -> {
                        val k = slotOf(atan2(y - HEAD_Y, x - HEAD_X), RING_SLOTS[L])
                        val to = ringPos(k)
                        ringFill[k] = true
                        moves.add(Move(i, Kind.RING, to[0], to[1], -1, -1, inEye(to[0], to[1]), delay))
                    }
                    L == 0 -> {
                        val k = slotOf(atan2(y - peaks[e][1], x - peaks[e][0]), EYE_SLOTS)
                        eyeFill[e][k] = true
                        val to = eyePos(e, k)
                        moves.add(Move(i, Kind.EYE, to[0], to[1], e, -1, false, delay))
                    }
                    else -> {
                        val span = HALF * lh * 1.1 + 0.075
                        val rx = (x - peaks[e][0]) / span; val ry = (y - peaks[e][1]) / span
                        var j = 0; var bd = 1e9
                        PUPIL_DISK.forEachIndexed { n, q -> val d = (q[0] - rx) * (q[0] - rx) + (q[1] - ry) * (q[1] - ry); if (d < bd) { bd = d; j = n } }
                        pupilFill[e][j] = true
                        moves.add(Move(i, Kind.PUPIL, 0.0, 0.0, e, j, false, delay))
                    }
                }
            }
            for (k in 0 until RING_SLOTS[L]) { val p = ringPos(k); if (!ringFill[k] && !inEye(p[0], p[1])) born.add(Born(Kind.RING, p[0], p[1], -1, -1)) }
            if (L == 0) for (e in 0 until 2) for (k in 0 until EYE_SLOTS) if (!eyeFill[e][k]) { val p = eyePos(e, k); born.add(Born(Kind.EYE, p[0], p[1], e, -1)) }
            if (L == 1) for (e in 0 until 2) for (j in PUPIL_DISK.indices) if (!pupilFill[e][j]) born.add(Born(Kind.PUPIL, 0.0, 0.0, e, j))
            Lane(lo, hi, moves, born)
        }
    }

    /** Build the geometry (a few hundred ms the first time); call off the main thread. */
    fun prepare() { LANES.size }

    // ---------------------------------------------------------- one frame

    private const val T_W = 0.9
    private const val T_M = 1.3
    private const val T_F = 3.6
    /** One W -> face -> W journey, seconds. */
    const val RUN = T_W + T_M + T_F + T_M
    private val LOOK_TL = doubleArrayOf(-sqrt(0.5), sqrt(0.5))
    private val LOOK_TR = doubleArrayOf(sqrt(0.5), sqrt(0.5))

    private fun clamp01(x: Double) = min(1.0, max(0.0, x))
    private fun smooth(x: Double) = x * x * (3 - 2 * x)
    private fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /**
     * One frame in a [w] x [h] box (dp units). [tb] = page clock (the breath
     * never restarts); [run] = seconds into a shed, null at rest; [half] =
     * which look order (0: top-left then top-right); [depth] = 3D sway 0..1.
     */
    fun frame(w: Float, h: Float, tb: Double, run: Double?, half: Int, depth: Double): List<Dot> {
        val lt = run?.let { min(it, RUN) }
        val span = w * 0.8
        val rs = (w / 300.0).pow(0.6)
        var hd = 0.0
        if (lt != null) hd = when {
            lt < T_W -> 0.0
            lt < T_W + T_M -> (lt - T_W) / T_M
            lt < T_W + T_M + T_F -> 1.0
            else -> clamp01(1 - (lt - T_W - T_M - T_F) / T_M)
        }
        val from = if (half == 0) LOOK_TL else LOOK_TR
        val to = if (half == 0) LOOK_TR else LOOK_TL
        val fl = (lt ?: 0.0) - T_W - T_M
        val g = if (fl < 1.4) 0.0 else smooth(clamp01((fl - 1.4) / 0.6))
        val lx = from[0] + (to[0] - from[0]) * g; val ly = from[1] + (to[1] - from[1]) * g
        val ll = hypot(lx, ly).takeIf { it > 0 } ?: 1.0
        fun pupilX(e: Int, j: Int) = PUPIL_C[e][0] + lx / ll * LOOK_R + PUPIL_DISK[j][0] * PUPIL_R
        fun pupilY(e: Int, j: Int) = PUPIL_C[e][1] + ly / ll * LOOK_R + PUPIL_DISK[j][1] * PUPIL_R

        // the 3D sway: a slow turn and nod; outer outline in front (thinking-orbs makeProj)
        val yaw = 0.34 * depth * sin(tb * 0.45)
        val tilt = 0.2 * depth * sin(tb * 0.31 + 1.1)
        val sy = sin(yaw); val cyw = cos(yaw); val st = sin(tilt); val ct = cos(tilt)
        val cx = w / 2.0; val cy = h / 2.0
        val dots = ArrayList<Dot>(700)
        fun put(x: Double, y: Double, L: Int, rBase: Double, rDepth: Double, white: Double, a: Double) {
            if (a < 0.02) return
            val z = (1 - L) * 0.045 * depth
            val x1 = x * cyw + z * sy
            val z1 = -x * sy + z * cyw
            val y1 = y * ct - z1 * st
            val z2 = y * st + z1 * ct
            val d = clamp01(0.5 + z2 * 7)
            val r = (rBase + rDepth * (0.4 + depth) * d) * rs
            dots.add(Dot((cx + x1 * span).toFloat(), (cy - y1 * span).toFloat(), z2.toFloat(), max(0.3, r).toFloat(), (white - 0.14 * depth * (d - 0.5)).toFloat(), a.toFloat()))
        }

        for (L in 0 until 3) {
            val lane = LANES[L]
            val b = 0.5 + 0.5 * sin(tb * 0.8 - L * 0.7)
            val laneA = (1 - 0.28 * L) * (0.75 + 0.25 * b)
            val ringR = 0.75 + 0.35 * b - 0.15 * L
            val ringW = 0.2 + 0.18 * L
            for (mv in lane.moves) {
                val p = ease(clamp01((hd - mv.delay) / 0.8))
                val lo = lane.lo[mv.i]; val hi = lane.hi[mv.i]
                val wx = lo[0] + (hi[0] - lo[0]) * b
                val wy = lo[1] + (hi[1] - lo[1]) * b
                val fx = if (mv.kind == Kind.PUPIL) pupilX(mv.eye, mv.pj) else mv.toX
                val fy = if (mv.kind == Kind.PUPIL) pupilY(mv.eye, mv.pj) else mv.toY
                var x = wx + (fx - wx) * p
                var y = wy + (fy - wy) * p
                // compact: everything squeezes towards the centre mid-way (straight in)
                val sq = 1 - 0.12 * sin(PI * p)
                x = HEAD_X + (x - HEAD_X) * sq
                y = HEAD_Y + (y - HEAD_Y) * sq
                var a = laneA
                if (mv.fade) a *= 1 - p
                when (mv.kind) {
                    Kind.EYE -> put(x, y, L, 0.85, 0.4, 0.2 - 0.1 * p, a)
                    Kind.PUPIL -> put(x, y, L, 0.75 + 0.25 * p, 0.4, 0.2 + 0.18 * L * (1 - p) - 0.1 * p, laneA + (1 - laneA) * p)
                    Kind.RING -> put(x, y, L, ringR, 0.4, ringW, a)
                }
            }
            // places nobody reached: a dot fades in where it stands
            val grow = clamp01((hd - 0.45) / 0.55)
            if (grow > 0.01) for (bn in lane.born) {
                when (bn.kind) {
                    Kind.RING -> put(bn.x, bn.y, L, ringR, 0.4, ringW, laneA * grow)
                    Kind.EYE -> put(bn.x, bn.y, L, 0.85, 0.4, 0.1, grow)
                    Kind.PUPIL -> put(pupilX(bn.eye, bn.pj), pupilY(bn.eye, bn.pj), L, 1.0, 0.4, 0.1, grow)
                }
            }
        }
        dots.sortBy { it.z }
        return dots
    }

}

/**
 * thinking-orbs' painter: matte dots far to near, in [ink]; on dark themes
 * the ink value is mirrored so near dots read bright.
 */
internal fun DrawScope.paintShedW(dots: List<ShedW.Dot>, ink: Color, dark: Boolean, scale: Float, origin: Offset = Offset.Zero) {
    val r = ink.red; val gg = ink.green; val bb = ink.blue
    for (d in dots) {
        val w = d.white.coerceIn(0f, 1f)
        val c = if (dark) Color(r * (1 - w), gg * (1 - w), bb * (1 - w), d.a.coerceIn(0f, 1f))
        else Color(r + (1 - r) * w, gg + (1 - gg) * w, bb + (1 - bb) * w, d.a.coerceIn(0f, 1f))
        drawCircle(c, radius = d.r * scale, center = Offset(origin.x + d.x * scale, origin.y + d.y * scale))
    }
}
