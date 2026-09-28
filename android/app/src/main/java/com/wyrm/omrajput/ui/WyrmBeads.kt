package com.wyrm.omrajput.ui

/**
 * Wyrm's own beads (OM, 2026-09-28): 24 patterned and material beads painted
 * into free atlas cells by Wyrm iOS `Scripts/generate-wyrm-beads.py`, as
 * `WyrmBead` on iOS.
 *
 * A bead is stored like an AIR wheel bead: its alpha byte (`0xE0 + kind`)
 * names the texture and the low 24 bits carry its colour. Tinted beads are
 * grey and take the picked colour; fixed-colour beads are drawn as painted and
 * their colour only chooses the nearest slither colour group, which is all the
 * arena (and every other player) ever sees. The engine reads the same bytes
 * (`wyrm_bead_kind` in game/redraw.c).
 */
internal object WyrmBeads {
    const val COUNT = 54
    /** Beads 0-23 are tagged 0xE0 + k; beads 24-53 are tagged 0xC0 + k - 24. */
    private const val FIRST = 24
    private const val TAG = 0xE0
    private const val TAG2 = 0xC0

    val names = listOf(
        "India", "Star", "Heart", "Dragon scales", "Stripes", "Dots", "Lightning", "Flame",
        "Crescent", "Chrome", "Gold", "Galaxy", "Honeycomb", "Argyle", "Zigzag", "Carbon fibre",
        "Tiger", "Circuit", "Leopard", "Lava", "Ice", "Marble", "Holographic", "Camo",
        "Checker", "Tartan", "Rainbow", "Sakura", "Snowflake", "Skull", "Music", "Paw",
        "Diamond", "Ruby", "Emerald", "Pearl", "Copper", "Rose gold", "Neon grid", "Sunset",
        "Waves", "Zebra", "Cow", "Giraffe", "Python", "Peacock", "Wood", "Denim",
        "Bubblegum", "Aurora", "Sun", "Electric", "Watermelon", "Pixel",
    )

    /** Every bead is painted in its own colours; none takes the wheel's colour. */
    val tinted = BooleanArray(COUNT)

    /** Each bead's main colour: the arena's nearest slither colour group is picked from it. */
    private val fixedRgb = mapOf(
        0 to 0xFF9933, 1 to 0x1F3A8A, 2 to 0xF7B6C8, 3 to 0x2FA45E, 4 to 0xD7263D,
        5 to 0x1F5FD6, 6 to 0x4B2A8C, 7 to 0xFF6A1A, 8 to 0x16245A, 9 to 0xC0C4CA,
        10 to 0xE0A838, 11 to 0x3A2A7A, 12 to 0xFFC43A, 13 to 0x1E2F5C, 14 to 0xFF8A1E,
        15 to 0x3A3E44, 16 to 0xF28A1C, 17 to 0x0E5A34, 18 to 0xD6A048, 19 to 0xC8501A,
        20 to 0x96CDF0, 21 to 0xECEAE4, 22 to 0xE8A0E8, 23 to 0x6A7A42,
        24 to 0x202022, 25 to 0xB21824, 26 to 0x00A848, 27 to 0xFCD6E0, 28 to 0x8CC0EC,
        29 to 0x16161A, 30 to 0x1E9E98, 31 to 0xECD0A8, 32 to 0x78D2F0, 33 to 0xC81432,
        34 to 0x14A05A, 35 to 0xF0E8EE, 36 to 0xCE7440, 37 to 0xE8AAA0, 38 to 0x24083C,
        39 to 0xFF783C, 40 to 0x2A7AC0, 41 to 0xF4F2EC, 42 to 0xF8F6F0, 43 to 0xC4782E,
        44 to 0xA89650, 45 to 0x148C78, 46 to 0xB0703A, 47 to 0x284C8C, 48 to 0xFFB4D7,
        49 to 0x0A1028, 50 to 0xFFB428, 51 to 0x0C143C, 52 to 0xEC4052, 53 to 0x3498DB,
    )

    /** Atlas cells, nine beads to a cell (row, column), as in the engine. */
    private val cells = listOf(7 to 3, 7 to 6, 8 to 0, 8 to 1, 8 to 2, 8 to 3)

    fun kind(argb: Int): Int? {
        val byte = (argb ushr 24) and 0xFF
        return when (byte) {
            in TAG until TAG + FIRST -> byte - TAG
            in TAG2 until TAG2 + (COUNT - FIRST) -> byte - TAG2 + FIRST
            else -> null
        }
    }

    /** The stored value for bead [kind]: its tag byte and its main colour. */
    fun argb(kind: Int, tint: Int): Int {
        val rgb = if (tinted[kind]) tint and 0xFFFFFF else fixedRgb[kind] ?: 0x808080
        val tag = if (kind < FIRST) TAG + kind else TAG2 + kind - FIRST
        return (tag shl 24) or rgb
    }

    /** Normalised atlas rectangle: x, y, width, height (nine beads to a cell). */
    fun uv(kind: Int): DoubleArray {
        val (row, column) = cells[kind / 9]
        val qx = (kind % 3) / 3.0
        val qy = ((kind % 9) / 3) / 3.0
        return doubleArrayOf((column + qx) / 7, (row + qy) / 9, (1.0 / 3) / 7, (1.0 / 3) / 9)
    }
}
