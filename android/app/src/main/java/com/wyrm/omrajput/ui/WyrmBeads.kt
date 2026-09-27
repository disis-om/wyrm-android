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
    const val COUNT = 24
    private const val TAG = 0xE0

    val names = listOf(
        "India", "Star", "Heart", "Dragon scales", "Stripes", "Dots", "Lightning", "Flame",
        "Crescent", "Chrome", "Gold", "Galaxy", "Honeycomb", "Argyle", "Zigzag", "Carbon fibre",
        "Tiger", "Circuit", "Leopard", "Lava", "Ice", "Marble", "Holographic", "Camo",
    )

    val tinted = booleanArrayOf(
        false, true, true, true, true, true, true, true, true, false, false, false,
        true, true, true, true, true, true, false, false, false, false, false, false,
    )

    /** Fixed-colour beads: the colour the arena's nearest group is picked from. */
    private val fixedRgb = mapOf(
        0 to 0xFF9933, 9 to 0xC0C4CA, 10 to 0xE0A838, 11 to 0x3A2A7A, 18 to 0xD6A048,
        19 to 0xC8501A, 20 to 0x96CDF0, 21 to 0xECEAE4, 22 to 0xE8A0E8, 23 to 0x6A7A42,
    )

    /** Atlas cells, four beads to a cell (row, column), as in the engine. */
    private val cells = listOf(7 to 3, 7 to 6, 8 to 0, 8 to 1, 8 to 2, 8 to 3)

    fun kind(argb: Int): Int? {
        val byte = (argb ushr 24) and 0xFF
        return if (byte in TAG until TAG + COUNT) byte - TAG else null
    }

    /** The stored value for bead [kind]: tinted beads keep [tint]. */
    fun argb(kind: Int, tint: Int): Int {
        val rgb = if (tinted[kind]) tint and 0xFFFFFF else fixedRgb[kind] ?: 0x808080
        return ((TAG + kind) shl 24) or rgb
    }

    /** Normalised atlas rectangle: x, y, width, height. */
    fun uv(kind: Int): DoubleArray {
        val (row, column) = cells[kind / 4]
        val qx = (kind % 2) * 0.5
        val qy = ((kind % 4) / 2) * 0.5
        return doubleArrayOf((column + qx) / 7, (row + qy) / 9, 0.5 / 7, 0.5 / 9)
    }
}
