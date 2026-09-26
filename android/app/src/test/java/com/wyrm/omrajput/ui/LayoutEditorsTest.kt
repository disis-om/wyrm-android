package com.wyrm.omrajput.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LayoutEditorsTest {
    private val area = floatArrayOf(40f, 20f, 1000f, 500f)
    private val control = Offset(120f, 120f)

    @Test
    fun decimalCommaLocaleCannotTurnPositionIntoZero() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val encoded = formatSettingNumber(0.2f)
            assertEquals("0.2000", encoded)
            assertEquals(0.2f, encoded.toFloat(), 0f)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun leftHandedFixedJoystickAccumulatesAwayFromCorner() {
        var centre = Offset(240f, 380f)
        repeat(20) { centre = moveLayoutCentre(centre, Offset(5f, -2f), area, control) }
        val normalized = normalizeLayoutCentre(centre, area)
        assertEquals(0.30f, normalized.x, 0.0001f)
        assertEquals(0.64f, normalized.y, 0.0001f)
    }

    @Test
    fun rightHandedFixedJoystickMovesInBothDirections() {
        val centre = moveLayoutCentre(Offset(840f, 380f), Offset(-75f, 35f), area, control)
        val normalized = normalizeLayoutCentre(centre, area)
        assertEquals(0.725f, normalized.x, 0.0001f)
        assertEquals(0.79f, normalized.y, 0.0001f)
    }

    @Test
    fun dynamicJoystickUsesTheSameStablePositionPipeline() {
        val centre = moveLayoutCentre(Offset(240f, 380f), Offset(260f, -100f), area, control)
        val normalized = normalizeLayoutCentre(centre, area)
        assertEquals(0.46f, normalized.x, 0.0001f)
        assertEquals(0.52f, normalized.y, 0.0001f)
    }

    @Test
    fun everyControlStaysFullyInsideSafeArea() {
        val topLeft = moveLayoutCentre(Offset(300f, 250f), Offset(-5000f, -5000f), area, control)
        val bottomRight = moveLayoutCentre(Offset(300f, 250f), Offset(5000f, 5000f), area, control)
        assertEquals(100f, topLeft.x, 0f)
        assertEquals(80f, topLeft.y, 0f)
        assertEquals(980f, bottomRight.x, 0f)
        assertEquals(460f, bottomRight.y, 0f)
    }

    @Test
    fun malformedBackupCoordinatesCannotBecomeTopLeft() {
        val repaired = sanitizeLayoutPosition(Offset(Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(0.5f, repaired.x, 0f)
        assertEquals(0.7f, repaired.y, 0f)
    }

    @Test
    fun resizeAndOversizedControlRemainFinite() {
        val tinyArea = floatArrayOf(0f, 0f, 1f, 1f)
        val centre = moveLayoutCentre(
            centre = Offset(Float.NaN, Float.NaN),
            delta = Offset.Zero,
            area = tinyArea,
            childSize = Offset(500f, 500f),
        )
        val normalized = normalizeLayoutCentre(centre, tinyArea)
        assertTrue(normalized.x.isFinite())
        assertTrue(normalized.y.isFinite())
        assertEquals(0.5f, normalized.x, 0f)
        assertEquals(0.5f, normalized.y, 0f)
    }

    @Test
    fun previewPositionUsesSavedNormalizedCenter() {
        val origin = previewItemTopLeft(
            position = Offset(0.25f, 0.75f),
            container = Offset(400f, 200f),
            child = Offset(40f, 20f),
        )

        assertEquals(80f, origin.x, 0.001f)
        assertEquals(140f, origin.y, 0.001f)
    }

    @Test
    fun previewPositionKeepsControlInsideEdges() {
        val topLeft = previewItemTopLeft(
            position = Offset(0f, 0f),
            container = Offset(400f, 200f),
            child = Offset(40f, 20f),
        )
        val bottomRight = previewItemTopLeft(
            position = Offset(1f, 1f),
            container = Offset(400f, 200f),
            child = Offset(40f, 20f),
        )

        assertEquals(0f, topLeft.x, 0.001f)
        assertEquals(0f, topLeft.y, 0.001f)
        assertEquals(360f, bottomRight.x, 0.001f)
        assertEquals(180f, bottomRight.y, 0.001f)
    }
}
