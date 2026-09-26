package com.wyrm.omrajput.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun fiftyPercentIsTheDesignedPaletteExactly() {
        WyrmThemeId.entries.forEach { theme ->
            assertEquals(theme.palette, theme.palette.withIntensity(0.5f))
        }
    }

    @Test
    fun zeroPercentIsPaperAndDarkThemesMoveTowardsIt() {
        val paper = WyrmThemeId.PAPER.palette
        listOf(WyrmThemeId.GRAPHITE, WyrmThemeId.MIDNIGHT).forEach { theme ->
            assertEquals(paper, theme.palette.withIntensity(0f))
            val quarter = theme.palette.withIntensity(0.25f)
            assertTrue(colourDistance(quarter.paper, paper.paper) < colourDistance(theme.palette.paper, paper.paper))
        }
    }

    @Test
    fun coreTextAndFilledActionsStayReadableAtEveryIntensity() {
        val intensities = (0..10).map { it / 10f }
        WyrmThemeId.entries.forEach { theme ->
            intensities.forEach { intensity ->
                val palette = theme.palette.withIntensity(intensity)
                assertContrast(theme, intensity, "paper / ink", palette.paper, palette.ink, 4.5f)
                assertContrast(theme, intensity, "card / ink", palette.card, palette.ink, 4.5f)
                assertContrast(theme, intensity, "well / ink", palette.well, palette.ink, 4.5f)
                assertContrast(theme, intensity, "ink / onInk", palette.ink, palette.onInk, 4.5f)
            }
        }
    }

    private fun assertContrast(
        theme: WyrmThemeId,
        intensity: Float,
        role: String,
        background: Color,
        foreground: Color,
        minimum: Float,
    ) {
        val lighter = maxOf(background.luminance(), foreground.luminance())
        val darker = minOf(background.luminance(), foreground.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue(
            "${theme.displayName} at ${(intensity * 100).toInt()}% $role contrast was $ratio",
            ratio >= minimum,
        )
    }

    private fun colourDistance(first: Color, second: Color): Float =
        kotlin.math.abs(first.red - second.red) +
            kotlin.math.abs(first.green - second.green) +
            kotlin.math.abs(first.blue - second.blue)
}
