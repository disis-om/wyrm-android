package com.wyrm.omrajput.ui

import androidx.compose.ui.graphics.Color

/** Everything the editor needs from the engine, as the engine actually holds it. */
data class SkinTables(
    /** 0xRRGGBB per colour group, straight from the engine's palette. */
    val palette: IntArray = IntArray(0),
    /** The character each colour group is spelled with inside a skin code. */
    val codeChars: ByteArray = ByteArray(0),
    /** 66 presets, stride 64: [length, cg, cg, ...]. */
    val presets: ByteArray = ByteArray(0),
) {
    val presetCount: Int get() = if (presets.isEmpty()) 0 else presets.size / PRESET_STRIDE
    val ready: Boolean get() = palette.isNotEmpty() && presetCount > 0

    /** The colour groups a preset repeats along the body. */
    fun presetGroups(index: Int): List<Int> {
        val base = index * PRESET_STRIDE
        if (base >= presets.size) return emptyList()
        val length = presets[base].toInt() and 0xFF
        if (length <= 0) return emptyList()
        return (0 until minOf(length, PRESET_STRIDE - 1)).map { step ->
            presets[base + 1 + step].toInt() and 0xFF
        }
    }

    /** The same sequence as flat colours, for before the sprite sheet loads. */
    fun presetColours(index: Int): List<Color> =
        presetGroups(index).mapNotNull { colourOf(it) }

    fun colourOf(group: Int): Color? {
        if (group !in palette.indices) return null
        val rgb = palette[group]
        return Color(0xFF000000.toInt() or rgb)
    }

    /** The colour group a skin-code character stands for, or -1. */
    fun groupOf(character: Char): Int =
        codeChars.indexOfFirst { it.toInt().toChar() == character }

    companion object {
        const val PRESET_STRIDE = 64

        /**
         * Four of the 42 groups are rejected by the engine. They are left out
         * of the palette entirely rather than shown greyed — a grid with two
         * deliberate gaps reads better than one with four dead cells.
         */
        val DEAD_GROUPS = setOf(36, 38, 40, 41)
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** What the editor currently has on. */
data class SkinState(
    val custom: Boolean = false,
    val preset: Int = 0,
    val code: String = "",
    val accessory: Int = -1,
    /**
     * Packed ARGB per position of [code], for positions built with the picker.
     * A 0 entry — or a position past the end — renders from the palette, which
     * is every skin that predates the picker and every code typed by hand.
     *
     * Only the colour group behind each position ever reaches an arena. This is
     * what Wyrm draws instead, and the only place an alpha exists at all.
     */
    val colours: IntArray = IntArray(0),
) {
    fun colourAt(index: Int): Int = colours.getOrElse(index) { 0 }

    /** The same list resized to [length], so it always pairs with the code. */
    fun coloursFor(length: Int): IntArray =
        IntArray(length) { colours.getOrElse(it) { 0 } }

    override fun equals(other: Any?): Boolean =
        other is SkinState && custom == other.custom && preset == other.preset &&
            code == other.code && accessory == other.accessory &&
            colours.contentEquals(other.colours)

    override fun hashCode(): Int =
        (((custom.hashCode() * 31 + preset) * 31 + code.hashCode()) * 31 +
            accessory) * 31 + colours.contentHashCode()
}
