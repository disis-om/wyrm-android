package com.wyrm.omrajput.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Shed (OM, 2026-10-15): the skin generator behind Home › Shed.
 *
 * A line-for-line port of the HTML prototype's gen/beads.js + gen/skinGen.js
 * (Desktop/thinking-orbs), so the same recipe and lucky number make the same
 * skins on every platform: only the seeded dice drive choices. Every skin:
 *   - uses only the 38 official skin characters (the arena accepts no other),
 *   - fits the arena: at most 256 beads and 146 runs (NTL's 300-byte cut),
 *   - never puts two beads that look the same side by side,
 *   - is not a near copy of another skin in the batch or in the history.
 * Beads overlap and show 8/48 of their width, so a band of 1-2 beads is a
 * hairline: bands are built 3-9 beads wide, only a deliberate accent is 2.
 * Nothing here is synced to the account (OM): the recipe and the history of
 * skins already shown stay on the phone (prefs "wyrm_shed", WIPE).
 */

/** One official bead as it really looks (measured from tex_atlas_8k.png, not the palette hex). */
class ShedBead(val ch: Char, val id: Int, rgb: Int, val L: Double, val hue: Int, val sat: Double, val stars: Boolean = false) {
    val lab: DoubleArray = labOf(rgb)
    val family: String = when {
        sat < 0.15 -> "grey"
        hue < 12 || hue >= 345 -> "red"
        hue < 35 -> "orange"
        hue < 70 -> "yellow"
        hue < 170 -> "green"
        hue < 205 -> "aqua"
        hue < 250 -> "blue"
        hue < 285 -> "purple"
        else -> "pink"
    }

    private companion object {
        fun labOf(rgb: Int): DoubleArray {
            fun lin(v: Int): Double { val c = v / 255.0; return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
            val r = lin((rgb shr 16) and 255); val g = lin((rgb shr 8) and 255); val b = lin(rgb and 255)
            val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
            fun f(v: Double) = if (v > 0.008856) Math.cbrt(v) else 7.787 * v + 16.0 / 116.0
            return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
        }
    }
}

object ShedBeads {
    /** id = colour group = cell (id % 7, id / 7) of the bead atlas (SkinTextures.beads). */
    val all: List<ShedBead> = listOf(
        ShedBead('z', 0, 0xB377E8, 0.605, 272, 0.71),
        ShedBead('x', 1, 0x868FE8, 0.621, 235, 0.68),
        ShedBead('c', 2, 0x77C2C2, 0.739, 180, 0.38),
        ShedBead('v', 3, 0x77E877, 0.835, 120, 0.71),
        ShedBead('b', 4, 0xDCDC68, 0.859, 60, 0.63),
        ShedBead('n', 5, 0xE89559, 0.691, 25, 0.76),
        ShedBead('m', 6, 0xE88686, 0.665, 0, 0.68),
        ShedBead(',', 7, 0xE83B3B, 0.526, 0, 0.79),
        ShedBead('a', 8, 0xD12CD1, 0.518, 300, 0.65),
        ShedBead('s', 9, 0xE8E8E8, 0.92, 0, 0.0),
        ShedBead('d', 10, 0x8A92E9, 0.632, 235, 0.68, stars = true),
        ShedBead('f', 11, 0x4A4A4A, 0.316, 0, 0.0),
        ShedBead('g', 12, 0xE8B34A, 0.761, 40, 0.78),
        ShedBead('h', 13, 0x257F59, 0.473, 155, 0.55),
        ShedBead('j', 14, 0x5D6DE8, 0.508, 233, 0.75),
        ShedBead('k', 15, 0x707DE8, 0.56, 233, 0.72),
        ShedBead('l', 16, 0x434EE8, 0.42, 236, 0.78),
        ShedBead('q', 17, 0x954AE8, 0.485, 268, 0.78),
        ShedBead('w', 18, 0xE8D13B, 0.835, 52, 0.79),
        ShedBead('e', 19, 0x3540E8, 0.384, 236, 0.8, stars = true),
        ShedBead('r', 20, 0x3541E8, 0.385, 236, 0.8, stars = true),
        ShedBead('t', 21, 0x4920B3, 0.287, 256, 0.69),
        ShedBead('y', 22, 0xE85008, 0.55, 19, 0.93),
        ShedBead('u', 23, 0x5EBBD8, 0.714, 194, 0.61),
        ShedBead('i', 24, 0x828182, 0.541, 0, 0.0),
        ShedBead('o', 25, 0x38B343, 0.646, 125, 0.53),
        ShedBead('p', 26, 0x1CF55D, 0.852, 138, 0.92),
        ShedBead('1', 27, 0xD64444, 0.504, 0, 0.64),
        ShedBead('2', 28, 0xD63535, 0.484, 0, 0.66),
        ShedBead('3', 29, 0x505050, 0.338, 0, 0.0),
        ShedBead('4', 30, 0x16169C, 0.207, 240, 0.75),
        ShedBead('5', 31, 0x9C1616, 0.335, 0, 0.75),
        ShedBead('6', 32, 0x848425, 0.536, 60, 0.56),
        ShedBead('7', 33, 0x845322, 0.398, 30, 0.59),
        ShedBead('8', 34, 0x842584, 0.338, 300, 0.56),
        ShedBead('9', 35, 0x258425, 0.483, 120, 0.56),
        ShedBead('0', 37, 0x6177E8, 0.536, 230, 0.75),
        ShedBead('-', 39, 0x61259F, 0.303, 270, 0.62),
    )
    private val byCh: Map<Char, ShedBead> = all.associateBy { it.ch }
    operator fun get(ch: Char): ShedBead = byCh.getValue(ch)
    fun isOfficial(ch: Char): Boolean = ch in byCh

    /** How different two beads look (CIE76 delta E): ~0 same, 25+ clearly different. */
    fun look(a: Char, b: Char): Double {
        val x = get(a).lab; val y = get(b).lab
        return sqrt((x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2]))
    }
    /** Below this two beads read as the same colour (x/d, e/r, ,/2/1, f/3 ...). */
    const val SAME_LOOK = 12.0
}

/** What the player asked for. */
data class ShedRecipe(
    val count: Int = 8,
    val mood: String = "cute",
    /** auto | light | dark | mix */
    val tone: String = "auto",
    /** a theme key, or "" */
    val theme: String = "",
    /** the player's own beads, or empty */
    val colours: List<Char> = emptyList(),
    /** a style key, or "auto" */
    val style: String = "auto",
    /** short | medium | long */
    val size: String = "medium",
    /** auto | on | off */
    val symmetry: String = "auto",
    /** 0..1 */
    val wildness: Double = 0.25,
    val seed: Int = 1,
)

class ShedSkin(
    val code: String,
    val key: String,
    val runs: Int,
    val mood: String,
    val style: String,
    val rule: String,
    val seed: Int,
    val colours: List<Char>,
    val score: Double,
)

object ShedGenerator {
    const val MAX_BEADS = 256
    const val MAX_RUNS = 146
    private const val ACCENT = 2 // the thinnest band that still reads as a line
    private const val SAME_LOOK = ShedBeads.SAME_LOOK

    class Mood(val label: String, val lo: Int, val hi: Int)
    val MOODS: LinkedHashMap<String, Mood> = linkedMapOf(
        "cute" to Mood("Cute", 2, 4),
        "danger" to Mood("Dangerous", 2, 3),
        "random" to Mood("Random", 2, 5),
        "pattern" to Mood("Pattern", 2, 3),
        "unique" to Mood("Unique", 3, 4),
    )
    val STYLES: LinkedHashMap<String, String> = linkedMapOf(
        "stripes" to "Stripes", "thickThin" to "Thick & thin", "ramp" to "Gradient", "rainbow" to "Rainbow",
        "pulse" to "Pulse", "band" to "Band", "duo" to "Duo", "fib" to "Fibonacci", "weave" to "Weave",
        "chaos" to "Chaos", "starry" to "Starry",
    )
    private val MOOD_STYLES: Map<String, LinkedHashMap<String, Int>> = mapOf(
        "cute" to linkedMapOf("stripes" to 3, "ramp" to 3, "rainbow" to 3, "pulse" to 2, "duo" to 2),
        "danger" to linkedMapOf("thickThin" to 3, "pulse" to 3, "band" to 3, "stripes" to 1),
        "random" to linkedMapOf("stripes" to 2, "thickThin" to 1, "ramp" to 2, "rainbow" to 1, "pulse" to 1, "band" to 1, "duo" to 1, "fib" to 1, "weave" to 1, "chaos" to 1),
        "pattern" to linkedMapOf("duo" to 3, "stripes" to 2, "weave" to 3, "band" to 2, "thickThin" to 2, "fib" to 1),
        "unique" to linkedMapOf("fib" to 2, "weave" to 2, "chaos" to 2, "starry" to 2, "band" to 1, "ramp" to 1),
    )
    class Theme(val label: String, val chars: String)
    val THEMES: LinkedHashMap<String, Theme> = linkedMapOf(
        "fire" to Theme("Fire", "y7,5ngw"), "ice" to Theme("Ice", "scuxk"), "toxic" to Theme("Toxic", "fph9v"),
        "galaxy" to Theme("Galaxy", "4t-qzea"), "ocean" to Theme("Ocean", "4eljucs"), "candy" to Theme("Candy", "mszvbu"),
        "sunset" to Theme("Sunset", "-a,yngw"), "royal" to Theme("Royal", "t-qwgs"), "warning" to Theme("Warning", "fw3b"),
        "blood" to Theme("Blood", "f5,3"), "void" to Theme("Void", "4t-8f"), "forest" to Theme("Forest", "h9ov67"),
    )
    /** Band width in beads, per pattern size. */
    val SIZES: LinkedHashMap<String, IntArray> = linkedMapOf("short" to intArrayOf(3, 4), "medium" to intArrayOf(4, 6), "long" to intArrayOf(6, 9))

    // Earthy, muddy beads: lovely in a theme or a dark skin, dull in a cute one.
    private val EARTHY = setOf('6', '7', '9', 'i', '3', 'h')
    // Beads that glow against a dark base.
    private val GLOW = listOf('p', 'y', 'w', 's', ',', 'a', 'g', 'v', 'u', 'b', 'n')

    private fun B(c: Char) = ShedBeads[c]
    private fun look(a: Char, b: Char) = ShedBeads.look(a, b)

    // ------------------------------------------------------------ dice

    /** mulberry32, bit for bit as in JavaScript. */
    private class Dice(seed: Int) {
        private var a = seed
        fun next(): Double {
            a += 0x6d2b79f5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
        }
        fun int(lo: Int, hi: Int): Int = lo + floor(next() * (hi - lo + 1)).toInt()
        fun <T> pick(list: List<T>): T = list[floor(next() * list.size).toInt()]
        fun chance(p: Double): Boolean = next() < p
        fun weighted(map: LinkedHashMap<String, Int>): String {
            val sum = map.values.sum()
            var r = next() * sum
            for ((k, w) in map) { r -= w; if (r < 0) return k }
            return map.keys.last()
        }
        fun <T> shuffle(list: List<T>): List<T> {
            val out = list.toMutableList()
            for (i in out.size - 1 downTo 1) {
                val j = floor(next() * (i + 1)).toInt()
                val x = out[i]; out[i] = out[j]; out[j] = x
            }
            return out
        }
    }

    fun mixSeed(seed: Int, n: Int): Int {
        var h = seed xor ((n + 1) * 0x9e3779b9L.toInt())
        h = (h xor (h ushr 16)) * 0x85ebca6bL.toInt()
        h = (h xor (h ushr 13)) * 0xc2b2ae35L.toInt()
        return h xor (h ushr 16)
    }
    private fun unsigned(x: Int): Long = x.toLong() and 0xffffffffL
    private fun jsRound(x: Double): Int = Math.round(x).toInt()

    // ------------------------------------------------------------ colours

    private fun hueGap(a: Int, b: Int): Int { val d = abs(a - b) % 360; return if (d > 180) 360 - d else d }

    /** Add [ch] only if it looks clearly different from every colour already in. */
    private fun addDistinct(set: MutableList<Char>, ch: Char, minLook: Double = 18.0): Boolean {
        if (set.any { look(it, ch) < minLook }) return false
        set.add(ch)
        return true
    }

    private fun poolFor(r: ShedRecipe, mood: String): List<Char> {
        val tone = if (r.tone == "auto") mapOf("cute" to "light", "danger" to "dark", "random" to "mix", "pattern" to "mix", "unique" to "mix")[mood] else r.tone
        var pool = ShedBeads.all.filter { !it.stars }
        if (mood != "danger" && tone != "dark") pool = pool.filter { it.ch !in EARTHY }
        if (mood == "cute") pool = pool.filter { it.ch != 'p' && it.ch != 'y' }
        if (tone == "light") pool = pool.filter { it.L >= 0.6 }
        if (tone == "dark") pool = pool.filter { it.L < 0.55 }
        return pool.map { it.ch }
    }

    private fun sortedBy(list: List<Char>, key: (Char) -> Double): List<Char> =
        list.sortedWith(compareBy<Char>({ key(it) }, { B(it).id }))

    private fun ruleAnalogous(pool: List<Char>, k: Int, d: Dice): MutableList<Char> {
        val coloured = pool.filter { B(it).sat >= 0.3 }
        val first = d.pick(coloured.ifEmpty { pool })
        val out = mutableListOf(first)
        for (c in sortedBy(pool) { hueGap(B(it).hue, B(first).hue).toDouble() }) {
            if (out.size >= k) break
            if (hueGap(B(c).hue, B(first).hue) <= 70 && B(c).sat >= 0.3) addDistinct(out, c)
        }
        return out
    }
    private fun ruleComplement(pool: List<Char>, k: Int, d: Dice): MutableList<Char> {
        val coloured = pool.filter { B(it).sat >= 0.4 }
        val first = d.pick(coloured.ifEmpty { pool })
        val out = mutableListOf(first)
        for (c in sortedBy(pool) { abs(hueGap(B(it).hue, B(first).hue) - 180).toDouble() }) if (B(c).sat >= 0.3 && addDistinct(out, c, 30.0)) break
        for (c in d.shuffle(listOf('s', 'f', '4', 't', 'c')).filter { it in pool }) { if (out.size >= k) break; addDistinct(out, c, 25.0) }
        return out
    }
    private fun ruleTriad(pool: List<Char>, k: Int, d: Dice): MutableList<Char> {
        val coloured = pool.filter { B(it).sat >= 0.4 }
        val first = d.pick(coloured.ifEmpty { pool })
        val out = mutableListOf(first)
        for (shift in intArrayOf(120, 240)) {
            val target = (B(first).hue + shift) % 360
            for (c in sortedBy(coloured) { hueGap(B(it).hue, target).toDouble() }) if (addDistinct(out, c, 25.0)) break
        }
        return out.take(max(2, k)).toMutableList()
    }
    private fun ruleMono(pool: List<Char>, k: Int, d: Dice): MutableList<Char> {
        val coloured = pool.filter { B(it).sat >= 0.3 }
        val first = d.pick(coloured.ifEmpty { pool })
        val fam = pool.filter { B(it).family == B(first).family || hueGap(B(it).hue, B(first).hue) <= 25 }
        val out = mutableListOf<Char>()
        for (c in fam.sortedBy { B(it).L }) addDistinct(out, c, 14.0)
        if (out.size < 2) for (c in listOf('s', 'f')) if (c in pool) addDistinct(out, c)
        if (out.size > k) {
            val step = ceil(out.size.toDouble() / k).toInt()
            return out.filterIndexed { i, _ -> i % step == 0 }.take(k).toMutableList()
        }
        return out
    }
    private fun ruleContrast(pool: List<Char>, k: Int, d: Dice): MutableList<Char> {
        val lights = pool.filter { B(it).L >= 0.62 }
        val darks = pool.filter { B(it).L < 0.45 }
        val out = mutableListOf<Char>()
        if (lights.isNotEmpty()) out.add(d.pick(lights))
        if (darks.isNotEmpty()) addDistinct(out, d.pick(darks), 35.0)
        for (c in d.shuffle(pool)) { if (out.size >= k) break; addDistinct(out, c, 25.0) }
        return out
    }
    private fun ruleCandy(pool: List<Char>, k: Int, d: Dice): MutableList<Char> {
        val soft = pool.filter { B(it).L >= 0.6 && B(it).sat >= 0.3 }
        if (soft.isEmpty()) return mutableListOf()
        val first = d.pick(soft)
        val out = mutableListOf(first)
        if ('s' in pool && d.chance(0.6)) out.add('s')
        val near = sortedBy(soft.filter { hueGap(B(it).hue, B(first).hue) <= 60 }) { hueGap(B(it).hue, B(first).hue).toDouble() }
        for (c in near) { if (out.size >= k) break; addDistinct(out, c, 18.0) }
        return out
    }
    private fun ruleRainbow(pool: List<Char>, k: Int, d: Dice): MutableList<Char> {
        val coloured = sortedBy(pool.filter { B(it).sat >= 0.45 }) { B(it).hue.toDouble() }
        if (coloured.isEmpty()) return mutableListOf()
        val n = max(3, k)
        val off = d.int(0, coloured.size - 1)
        val out = mutableListOf<Char>()
        var i = 0
        while (i < coloured.size && out.size < n) {
            addDistinct(out, coloured[(off + jsRound(i.toDouble() * coloured.size / n)) % coloured.size], 20.0)
            i++
        }
        return out
    }
    private fun rule(name: String, pool: List<Char>, k: Int, d: Dice): MutableList<Char> = when (name) {
        "analogous" -> ruleAnalogous(pool, k, d)
        "complement" -> ruleComplement(pool, k, d)
        "triad" -> ruleTriad(pool, k, d)
        "mono" -> ruleMono(pool, k, d)
        "contrast" -> ruleContrast(pool, k, d)
        "candy" -> ruleCandy(pool, k, d)
        else -> ruleRainbow(pool, k, d)
    }

    private fun pickPalette(r: ShedRecipe, mood: String, d: Dice): Pair<List<Char>, String> {
        val m = MOODS.getValue(mood)
        if (r.colours.isNotEmpty()) {
            val own = mutableListOf<Char>()
            for (c in d.shuffle(r.colours)) addDistinct(own, c, SAME_LOOK)
            val k = min(own.size, max(2, d.int(m.lo, m.hi)))
            return own.take(max(2, k)) to "yours"
        }
        THEMES[r.theme]?.let { theme ->
            val out = mutableListOf<Char>()
            val k = d.int(max(2, m.lo), max(3, m.hi))
            for (c in d.shuffle(theme.chars.toList())) { if (out.size >= k) break; addDistinct(out, c, 16.0) }
            return out to "theme"
        }
        val pool = poolFor(r, mood)
        val k = d.int(m.lo, m.hi)
        if (mood == "danger") {
            val darks = pool.filter { B(it).L < 0.45 }
            val base = mutableListOf<Char>()
            for (c in d.shuffle(darks.ifEmpty { pool })) { if (base.size >= k - 1) break; addDistinct(base, c, 16.0) }
            val glows = GLOW.filter { c -> base.all { look(it, c) >= 45 } }
            return (base + (if (glows.isNotEmpty()) d.pick(glows) else 's')) to "dark + glow"
        }
        val weights = when (mood) {
            "cute" -> linkedMapOf("candy" to 4, "analogous" to 3, "mono" to 2, "rainbow" to 1)
            "random" -> linkedMapOf("analogous" to 2, "complement" to 2, "triad" to 2, "mono" to 1, "contrast" to 1, "rainbow" to 1)
            "pattern" -> linkedMapOf("contrast" to 3, "complement" to 3, "triad" to 1)
            else -> linkedMapOf("triad" to 2, "complement" to 2, "rainbow" to 1, "contrast" to 1)
        }
        val ruleName = d.weighted(weights)
        var colours = rule(ruleName, pool, k, d)
        if (mood == "cute" && ruleName != "candy" && colours.size < 4 && d.chance(0.3)) addDistinct(colours, 's', 20.0)
        if (mood == "unique" && d.chance(0.5)) {
            val star = d.pick(listOf('d', 'e'))
            if (colours.all { look(it, star) >= SAME_LOOK }) colours.add(star)
        }
        if (colours.size < 2) colours = ruleContrast(pool, 2, d)
        return colours to ruleName
    }

    // ------------------------------------------------------------ patterns

    private class Run(var ch: Char, var n: Int)
    private fun run(ch: Char, n: Double) = Run(ch, max(1, jsRound(n)))
    private fun run(ch: Char, n: Int) = Run(ch, max(1, n))
    private fun byL(cs: List<Char>) = cs.sortedWith(compareBy<Char>({ B(it).L }, { B(it).id }))
    private fun byHue(cs: List<Char>) = cs.sortedWith(compareBy<Char>({ B(it).hue }, { B(it).L }))

    private fun build(style: String, P: List<Char>, u: Int, d: Dice): MutableList<Run> = when (style) {
        "stripes" -> P.map { run(it, u) }.toMutableList()
        "thickThin" -> {
            val s = if (d.chance(0.6)) byL(P) else byL(P).reversed()
            val out = mutableListOf<Run>()
            for (c in s.drop(1)) { out.add(run(s[0], u * d.int(2, 3))); out.add(run(c, max(ACCENT, jsRound(u / 2.0)))) }
            out
        }
        "ramp" -> {
            val s = if (d.chance(0.5)) byL(P) else byHue(P)
            val seq = s + (if (s.size > 2) s.subList(1, s.size - 1).reversed() else emptyList())
            val w = max(3, jsRound(u * 0.8))
            seq.map { run(it, w) }.toMutableList()
        }
        "rainbow" -> byHue(P).map { run(it, u) }.toMutableList()
        "pulse" -> {
            val s = if (d.chance(0.5)) byL(P) else byL(P).reversed()
            val out = mutableListOf<Run>()
            for (c in s.drop(1)) { out.add(run(s[0], u * d.int(2, 4))); out.add(run(c, ACCENT)) }
            out
        }
        "band" -> {
            val s = byL(P)
            val a = if (d.chance(0.5)) s[0] else s[s.size - 1]
            val rest = s.filter { it != a }
            val edge = rest.lastOrNull() ?: a
            val mid = if (rest.size > 1) rest[0] else edge
            val first = run(a, u * d.int(3, 5))
            mutableListOf(first, run(edge, ACCENT), run(mid, u), run(edge, ACCENT))
        }
        "duo" -> {
            val w = max(3, u - 1)
            val s = d.shuffle(P)
            mutableListOf(run(s[0], w), run(s[1], w))
        }
        "fib" -> {
            val seq = intArrayOf(2, 3, 5, 8, 13)
            val s = if (d.chance(0.5)) byL(P) else byHue(P)
            seq.indices.map { i -> run(s[i % s.size], seq[i]) }.toMutableList()
        }
        "weave" -> {
            val s = d.shuffle(P)
            val a = s[0]; val b = s[1 % s.size]; val c = s[2 % s.size]
            mutableListOf(run(a, u), run(b, ACCENT), run(c, u), run(b, ACCENT))
        }
        "chaos" -> {
            val out = mutableListOf<Run>()
            var prev: Char? = null
            val n = d.int(5, 9)
            for (i in 0 until n) {
                var c = d.pick(P)
                var guard = 0
                while (prev != null && look(c, prev) < SAME_LOOK && guard++ < 10) c = d.pick(P)
                out.add(run(c, d.int(ACCENT, u + 2)))
                prev = c
            }
            out
        }
        else -> { // starry
            val plain = P.filter { !B(it).stars }
            val base = plain.ifEmpty { listOf('4') }
            val star = P.firstOrNull { B(it).stars } ?: d.pick(listOf('d', 'e'))
            val out = mutableListOf<Run>()
            for (c in base) { out.add(run(c, u * 2)); out.add(run(star, ACCENT + 1)) }
            out
        }
    }

    private fun toCode(runs: List<Run>): String = buildString { for (r in runs) repeat(r.n) { append(r.ch) } }
    private fun toRuns(code: String): MutableList<Run> {
        val out = mutableListOf<Run>()
        for (c in code) if (out.isNotEmpty() && out.last().ch == c) out.last().n++ else out.add(Run(c, 1))
        return out
    }
    /** Runs of the code as the arena counts them (a run longer than 255 splits). */
    fun arenaRuns(code: String): Int = toRuns(code).sumOf { (it.n + 254) / 255 }

    private fun fixNeighbours(start: MutableList<Run>, P: List<Char>, d: Dice): MutableList<Run> {
        var runs = start
        for (pass in 0 until 4) {
            var changed = false
            var i = 0
            while (i < runs.size && runs.size > 1) {
                val prev = runs[(i - 1 + runs.size) % runs.size].ch
                val next = runs[(i + 1) % runs.size].ch
                val c = runs[i].ch
                fun clash(x: Char) = x != c && look(x, c) < SAME_LOOK
                if (clash(prev) || clash(next)) {
                    val options = P.filter { look(it, prev) >= SAME_LOOK && look(it, next) >= SAME_LOOK }
                    runs[i].ch = if (options.isNotEmpty()) d.pick(options) else prev
                    changed = true
                }
                i++
            }
            runs = toRuns(toCode(runs))
            if (!changed) break
        }
        return runs
    }

    private fun mutate(runs: MutableList<Run>, P: List<Char>, wild: Double, d: Dice): MutableList<Run> {
        val n = jsRound(wild * runs.size * 0.6)
        for (k in 0 until n) {
            val i = d.int(0, runs.size - 1)
            when (d.int(0, 2)) {
                0 -> runs[i].ch = d.pick(P)
                1 -> runs[i].n = max(ACCENT, runs[i].n + (if (d.chance(0.5)) 1 else -1) * d.int(1, 2))
                else -> { val j = d.int(0, runs.size - 1); val x = runs[i]; runs[i] = runs[j]; runs[j] = x }
            }
        }
        return runs
    }

    /** Keep the code inside the arena: <= 256 beads, <= 146 runs. */
    private fun fitArena(start: MutableList<Run>): MutableList<Run> {
        var runs = start
        val total = runs.sumOf { it.n }
        if (total > MAX_BEADS) {
            val k = MAX_BEADS.toDouble() / total
            runs = runs.map { Run(it.ch, max(1, floor(it.n * k).toInt())) }.toMutableList()
        }
        while (runs.size > MAX_RUNS) {
            var i = 0
            for (j in 1 until runs.size) if (runs[j].n < runs[i].n) i = j
            runs[if (i > 0) i - 1 else 1].n += runs[i].n
            runs.removeAt(i)
            runs = toRuns(toCode(runs))
        }
        return runs
    }

    // ------------------------------------------------------------ judging

    /** The code as one canonical loop: smallest rotation of its smallest block. */
    fun canonical(code: String): String {
        var p = 1
        while (p <= code.length / 2) {
            if (code.length % p == 0 && code.substring(0, p).repeat(code.length / p) == code) return canonical(code.substring(0, p))
            p++
        }
        var best = code
        for (i in 1 until code.length) {
            val r = code.substring(i) + code.substring(0, i)
            if (r < best) best = r
        }
        return best
    }

    private fun distinctLooks(cs: Collection<Char>): Int { val out = mutableListOf<Char>(); for (c in cs) addDistinct(out, c, SAME_LOOK); return out.size }

    private fun score(code: String, mood: String, style: String, allowEarthy: Boolean): Double {
        val runs = toRuns(code)
        val chars = code.toSet().toList().sortedBy { code.indexOf(it) }
        var edgeScore = 0.0
        var edges = 0
        for (i in runs.indices) {
            val a = runs[i].ch; val b = runs[(i + 1) % runs.size].ch
            if (a == b) continue
            val dE = look(a, b)
            edgeScore += if (style == "ramp") max(0.0, 1 - abs(dE - 24) / 30) else min(1.0, dE / 45)
            edges++
        }
        edgeScore = if (edges > 0) edgeScore / edges else 0.0
        val widths = 1 - runs.count { it.n < ACCENT }.toDouble() / runs.size
        val families = chars.map { B(it).family }.toSet().size
        val calm = if (style == "rainbow" || style == "chaos") 1.0 else if (families <= 3) 1.0 else 0.7
        val earthy = chars.count { it in EARTHY }
        val clean = if (allowEarthy || earthy == 0) 1.0 else 0.6
        val ls = chars.map { B(it).L }
        val depth = min(1.0, (ls.max() - ls.min()) / 0.3)
        val share = chars.maxOf { c -> code.count { it == c } }.toDouble() / code.length
        val balance = if (style in setOf("pulse", "thickThin", "band", "starry")) 1.0 else if (share <= 0.66) 1.0 else 1 - (share - 0.66) * 2.5
        val meanL = code.sumOf { B(it).L } / code.length
        var moodFit = 1.0
        if (mood == "cute") moodFit = min(1.0, max(0.0, (meanL - 0.5) / 0.2))
        if (mood == "danger") moodFit = min(1.0, code.count { B(it).L < 0.45 }.toDouble() / code.length / 0.55)
        val visible = if (code.length <= 128) 1.0 else 0.7
        return 0.3 * edgeScore + 0.12 * widths + 0.1 * calm + 0.12 * clean + 0.1 * depth + 0.1 * balance + 0.11 * moodFit + 0.05 * visible
    }

    private fun similar(a: ShedSkin, b: ShedSkin): Double {
        if (a.key == b.key) return 1.0
        val sa = a.code.toSet(); val sb = b.code.toSet()
        val inter = sa.count { it in sb }
        val jac = inter.toDouble() / (sa.size + sb.size - inter)
        return 0.6 * jac + 0.3 * (if (a.style == b.style) 1 else 0) + 0.1 * (if (abs(a.code.length - b.code.length) <= 3) 1 else 0)
    }

    // ------------------------------------------------------------ one skin

    /** Build one skin from one seed, or null when it fails the checks. */
    fun makeSkin(r: ShedRecipe, seed: Int): ShedSkin? {
        val d = Dice(seed)
        val mood = r.mood
        val (P, ruleName) = pickPalette(r, mood, d)
        if (distinctLooks(P) < 2) return null
        var style = if (r.style != "auto" && r.style in STYLES) r.style else d.weighted(MOOD_STYLES.getValue(mood))
        if (style == "rainbow" && P.size < 3) style = "stripes"
        val size = SIZES[r.size] ?: SIZES.getValue("medium")
        val u = d.int(size[0], size[1])
        var runs = build(style, P, u, d)
        val symChance = mapOf("cute" to 0.5, "pattern" to 0.35)[mood] ?: 0.15
        val symOn = r.symmetry == "on" || (r.symmetry == "auto" && d.chance(symChance))
        if (symOn && runs.size > 2 && style != "ramp" && style != "duo") {
            runs = (runs + runs.subList(1, runs.size - 1).reversed().map { Run(it.ch, it.n) }).toMutableList()
        }
        runs = mutate(runs, P, r.wildness, d)
        runs = toRuns(toCode(runs))
        runs = fixNeighbours(runs, P, d)
        runs = fitArena(runs)
        val code = toCode(runs)
        if (code.isEmpty() || code.length > MAX_BEADS || arenaRuns(code) > MAX_RUNS) return null
        if (code.any { !ShedBeads.isOfficial(it) }) return null
        if (distinctLooks(code.toSet()) < 2) return null
        for (i in code.indices) {
            val a = code[i]; val b = code[(i + 1) % code.length]
            if (a != b && look(a, b) < SAME_LOOK) return null
        }
        val allowEarthy = mood == "danger" || r.theme.isNotEmpty() || r.colours.isNotEmpty() || r.tone == "dark"
        return ShedSkin(
            code = code, key = canonical(code), runs = arenaRuns(code), mood = mood, style = style, rule = ruleName,
            seed = seed, colours = code.toList().distinct(), score = score(code, mood, style, allowEarthy),
        )
    }

    // ------------------------------------------------------------ the batch

    /** [ShedRecipe.count] skins, best first; [history] = canonical codes already shown (never repeated). */
    fun generate(r: ShedRecipe, history: Set<String>): Pair<List<ShedSkin>, Int> {
        val want = r.count.coerceIn(1, 48)
        val pool = mutableListOf<ShedSkin>()
        val seen = HashSet<String>()
        var tried = 0
        var n = 0
        while (n < want * 80 && pool.size < want * 4) {
            tried++
            val s = makeSkin(r, mixSeed(r.seed, n))
            n++
            if (s == null || s.key in seen || s.key in history) continue
            seen.add(s.key)
            pool.add(s)
        }
        val sorted = pool.sortedWith(compareByDescending<ShedSkin> { it.score }.thenBy { unsigned(it.seed) })
        val out = mutableListOf<ShedSkin>()
        for (s in sorted) {
            if (out.size >= want) break
            if (out.any { similar(it, s) > 0.8 }) continue
            out.add(s)
        }
        for (s in sorted) { if (out.size >= want) break; if (s !in out) out.add(s) }
        return out to tried
    }
}
