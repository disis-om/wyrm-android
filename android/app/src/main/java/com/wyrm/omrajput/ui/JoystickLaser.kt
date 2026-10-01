package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Assist laser in joystick mode (OM, 2026-10-01). iOS twin: `WyrmJoystickLaser`
 * in WyrmLaser.swift.
 *
 * With assist on and a joystick (the arrow already has its laser), the engine
 * draws a line from the head where the snake is being steered: the stick's way
 * while it is held, the snake's own heading otherwise (`ui_overlay.c`). Its
 * length is a share of the screen's short side; colour and thickness are the
 * laser's own (Settings › Modes › Advanced). Saved in `wyrm_joystick_laser`,
 * synced with the account (AccountSync.FILES).
 */
object JoystickLaserStore {
    private const val PREFS = "wyrm_joystick_laser"
    private const val KEY_ON = "on"
    private const val KEY_LENGTH = "length"
    const val DEFAULT_LENGTH = 0.45f
    val RANGE = 0.1f..1.0f

    var on by mutableStateOf(true)
        private set
    var length by mutableFloatStateOf(DEFAULT_LENGTH)
        private set

    private var prefs: android.content.SharedPreferences? = null
    private var sink: ((Boolean, Float) -> Unit)? = null

    /** Called once by the activity; publishes the saved choice to the engine. */
    @JvmStatic
    fun attach(context: Context, publish: (Boolean, Float) -> Unit) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        on = store.getBoolean(KEY_ON, true)
        length = store.getFloat(KEY_LENGTH, DEFAULT_LENGTH).coerceIn(RANGE)
        sink = publish
        publish(on, length)
    }

    /** After a log in or log out rewrote the file (AccountSync): read it again and republish. */
    fun reload(context: Context) {
        val publish = sink ?: return
        attach(context, publish)
    }

    fun applyOn(value: Boolean) {
        on = value
        prefs?.edit()?.putBoolean(KEY_ON, value)?.apply()
        sink?.invoke(on, length)
    }

    fun applyLength(value: Float) {
        length = value.coerceIn(RANGE)
        prefs?.edit()?.putFloat(KEY_LENGTH, length)?.apply()
        sink?.invoke(on, length)
    }
}

/** "45%": the share of the screen's short side. */
internal fun joystickLaserLabel(length: Float): String = "${(length * 100).toInt()}%"

/**
 * The live preview: a slice of arena the shape of this phone held sideways, a
 * snake whose stick sways, and the laser at exactly the length, colour and
 * thickness the arena will draw (the length is a share of the short side, the
 * thickness in screen pixels scaled to this slice).
 */
@Composable
internal fun JoystickLaserPreview(length: Float, on: Boolean, colour: Color, thicknessPx: Float) {
    val sway by rememberInfiniteTransition(label = "laser-sway").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "laser-sway-value",
    )
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val shortSidePx = with(density) { min(configuration.screenWidthDp, configuration.screenHeightDp).dp.toPx() }
    val longSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
    val shortSide = min(configuration.screenWidthDp, configuration.screenHeightDp).toFloat().coerceAtLeast(1f)
    val aspect = (longSide / shortSide).coerceIn(1.3f, 2.4f)
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = "Live preview: the line follows your stick, at this length",
            fontFamily = Wyrm.Body,
            fontSize = 12.5.sp,
            color = Wyrm.Quiet,
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(150.dp)
                .clip(wyrmRounded(14.dp))
                .background(Color(0xFF0B0E12))
                .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // The slice is the phone's screen, scaled to fit this box.
                val sliceH = min(size.height, size.width / aspect)
                val sliceW = sliceH * aspect
                val left = (size.width - sliceW) / 2f
                val top = (size.height - sliceH) / 2f
                val scale = sliceH / shortSidePx
                // A faint floor grid, so the line reads as lying on the arena.
                val step = sliceH / 6f
                var gx = left + step / 2f
                while (gx < left + sliceW) {
                    var gy = top + step / 2f
                    while (gy < top + sliceH) {
                        drawCircle(Color.White.copy(alpha = 0.06f), 1.4.dp.toPx(), Offset(gx, gy))
                        gy += step
                    }
                    gx += step
                }
                val head = Offset(left + sliceW * 0.42f, top + sliceH * 0.56f)
                val angle = -0.35f + sway * 0.55f
                val dir = Offset(cos(angle), sin(angle))
                // The body trails behind the head, bending with the turn.
                val bead = sliceH * 0.055f
                for (i in 9 downTo 1) {
                    val bend = angle - i * 0.06f * sway
                    val back = Offset(cos(bend), sin(bend))
                    drawCircle(Color(0xFF6FD3A6).copy(alpha = 0.92f), bead, head - back * (i * bead * 1.25f))
                }
                drawCircle(Color(0xFF8BE9BF), bead * 1.1f, head)
                if (on) {
                    drawLine(
                        color = colour,
                        start = head,
                        end = head + dir * (length * sliceH),
                        strokeWidth = (thicknessPx * scale).coerceAtLeast(1f),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(Color.Black.copy(alpha = 0.55f), bead * 0.22f, head + dir * (bead * 0.45f))
            }
            if (!on) {
                Text(
                    "OFF",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}
