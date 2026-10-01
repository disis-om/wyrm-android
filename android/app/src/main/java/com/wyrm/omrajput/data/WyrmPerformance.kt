package com.wyrm.omrajput.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Settings › Performance (OM, 2026-10-01). iOS twin: `WyrmPerformance.swift`.
 *
 * Three modes and a frame limit. Only how often the engine draws changes:
 * never the protocol, never the game.
 *
 *  - **Performance**: vsync off (IMMEDIATE where the phone offers it), no cap,
 *    the display asked for its highest refresh. Most frames, least input delay,
 *    and the phone runs warmer.
 *  - **Balanced**: vsync on, 60 FPS. Steady and cool.
 *  - **Auto** (the default): vsync on and the display's full refresh while the
 *    phone is cool; 60 FPS when it warms up, or when Battery Saver or the
 *    system's Battery game mode is on; 30 FPS when it is hot. Heat comes from
 *    the Android thermal status and the thermal headroom forecast
 *    (developer.android.com/games/optimize/adpf/thermal: headroom at most once
 *    every 10 s, 0.85+ = light throttling soon, 1.0 = severe).
 *
 * The frame limit is Auto (the mode decides), a number, or Max. In Auto mode
 * heat and Battery Saver can still bring it lower.
 *
 * Android 15 holds games at 60 Hz unless they ask for more with
 * `setFrameRate` (developer.android.com/games/optimize/display-refresh-rate-change),
 * so the activity asks for the policy's rate on the engine's surface.
 *
 * Saved in `wyrm_performance` (synced with the account, AccountSync.FILES).
 */
object WyrmPerformance {
    enum class Mode(val key: String, val title: String) {
        AUTO("auto", "Auto"),
        BALANCED("balanced", "Balanced"),
        PERFORMANCE("performance", "Performance"),
    }

    enum class Heat { COOL, WARM, HOT }

    /** What the engine is told. [cap] 0 = no cap. */
    data class Policy(val vsync: Boolean, val cap: Int, val displayHz: Int, val reason: String)

    /** Implemented by WyrmActivity: the engine's present mode and cap, and the display rate. */
    fun interface FrameSink {
        fun apply(vsync: Boolean, cap: Int, displayHz: Float, raising: Boolean)
    }

    const val LIMIT_AUTO = 0
    const val LIMIT_MAX = -1
    private const val PREFS = "wyrm_performance"
    private const val HEADROOM_EVERY_MS = 15_000L

    var mode by mutableStateOf(Mode.AUTO)
        private set
    var limit by mutableIntStateOf(LIMIT_AUTO)
        private set
    var displayMaxHz by mutableIntStateOf(60)
        private set
    var heat by mutableStateOf(Heat.COOL)
        private set
    var saver by mutableStateOf(false)
        private set
    var policy by mutableStateOf(Policy(true, 60, 60, ""))
        private set

    private var context: Context? = null
    private var sink: FrameSink? = null
    private var resumed = false
    private var statusHeat = Heat.COOL
    private var headroomHeat = Heat.COOL
    private var batteryGameMode = false
    private var applied: Policy? = null
    private val main = Handler(Looper.getMainLooper())

    private val headroomTick = object : Runnable {
        override fun run() {
            readHeadroom()
            if (resumed && mode == Mode.AUTO) main.postDelayed(this, HEADROOM_EVERY_MS)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            readSaver()
            publish()
        }
    }

    /** WyrmActivity.onCreate: read the saved choice, learn the display, start listening. */
    fun attach(activity: Activity, frameSink: FrameSink) {
        context = activity.applicationContext
        displayMaxHz = readDisplayMax(activity)
        reload(activity)
        sink = frameSink
        val power = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                statusHeat = heatOfStatus(power.currentThermalStatus)
                power.addThermalStatusListener(activity.mainExecutor) { status ->
                    statusHeat = heatOfStatus(status)
                    publish()
                }
            }
        }
        runCatching {
            activity.applicationContext.registerReceiver(powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }
        readSaver()
        publish()
    }

    /** The saved choice again (after an account restore or a log out). */
    fun reload(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = Mode.entries.firstOrNull { it.key == prefs.getString("mode", null) } ?: Mode.AUTO
        limit = prefs.getInt("fps_limit", LIMIT_AUTO)
        publish()
    }

    fun onResume(activity: Activity) {
        resumed = true
        displayMaxHz = readDisplayMax(activity)
        readSaver()
        // A new surface after the background starts at the system's default rate.
        applied = null
        publish()
        main.removeCallbacks(headroomTick)
        if (mode == Mode.AUTO) main.postDelayed(headroomTick, 2_000L)
    }

    fun onPause() {
        resumed = false
        main.removeCallbacks(headroomTick)
    }

    fun applyMode(next: Mode) {
        if (mode == next) return
        mode = next
        save()
        publish()
        main.removeCallbacks(headroomTick)
        if (resumed && mode == Mode.AUTO) main.postDelayed(headroomTick, 1_000L)
    }

    fun applyLimit(next: Int) {
        if (limit == next) return
        limit = next
        save()
        publish()
    }

    /** The limits this display can use: Auto, 30, 60, 90 and 120 where they fit, Max. */
    fun limitChoices(): List<Int> =
        listOf(LIMIT_AUTO, 30, 60) + listOf(90, 120).filter { it <= displayMaxHz + 1 } + LIMIT_MAX

    fun limitLabel(value: Int): String = when (value) {
        LIMIT_AUTO -> "Auto"
        LIMIT_MAX -> "Max"
        else -> "$value"
    }

    /** One line for the hub row: the mode and what it draws at right now. */
    fun summary(): String {
        val p = policy
        val fps = if (p.cap > 0) "${p.cap} FPS" else "Max FPS"
        return "${mode.title} · $fps"
    }

    private fun save() {
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("mode", mode.key)
            ?.putInt("fps_limit", limit)
            ?.apply()
    }

    private fun heatOfStatus(status: Int): Heat = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> Heat.HOT
        status >= PowerManager.THERMAL_STATUS_MODERATE -> Heat.WARM
        else -> Heat.COOL
    }

    /** At most once every 10 s (NaN otherwise); unsupported phones return NaN or 0. */
    private fun readHeadroom() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val power = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val headroom = runCatching { power.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
        if (headroom.isNaN() || headroom <= 0f) return
        // Down only with a margin, so the rate does not flutter at a threshold.
        headroomHeat = when {
            headroom >= 1.0f -> Heat.HOT
            headroom >= 0.9f -> Heat.WARM
            headroom < 0.8f -> Heat.COOL
            else -> if (headroomHeat == Heat.HOT) Heat.WARM else headroomHeat
        }
        publish()
    }

    private fun readSaver() {
        val c = context ?: return
        val power = c.getSystemService(Context.POWER_SERVICE) as? PowerManager
        saver = power?.isPowerSaveMode == true
        batteryGameMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { Api31.batteryGameMode(c) }.getOrDefault(false)
    }

    /** Behind an API fence, so Android 8-11 never resolve GameManager (as WyrmGameMode). */
    private object Api31 {
        fun batteryGameMode(c: Context): Boolean =
            c.getSystemService(android.app.GameManager::class.java)?.gameMode == android.app.GameManager.GAME_MODE_BATTERY
    }

    private fun readDisplayMax(activity: Activity): Int {
        val display = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay
        }.getOrNull() ?: return 60
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxOfOrNull { it.refreshRate } ?: display.refreshRate
        return best.roundToInt().coerceAtLeast(30)
    }

    private fun resolve(): Policy {
        val max = displayMaxHz
        val chosen = when (limit) {
            LIMIT_AUTO -> null
            LIMIT_MAX -> 0
            else -> limit.coerceAtMost(max)
        }
        return when (mode) {
            Mode.PERFORMANCE -> {
                val cap = chosen ?: 0
                Policy(
                    vsync = false,
                    cap = cap,
                    displayHz = if (cap > 0) cap else max,
                    reason = if (cap > 0) "Vsync off, held at $cap FPS on a $max Hz display."
                    else "Vsync off and no cap: as many frames as the phone can draw on its $max Hz display, with the least input delay. The phone runs warmer.",
                )
            }
            Mode.BALANCED -> {
                val cap = chosen?.let { if (it == 0) max else it } ?: 60
                Policy(true, cap, cap, "Vsync on at $cap FPS: steady frames, a cooler phone and a longer battery.")
            }
            Mode.AUTO -> {
                val wanted = chosen?.let { if (it == 0) max else it } ?: max
                val hot = heat
                val lowPower = saver || batteryGameMode
                val ceiling = when {
                    hot == Heat.HOT -> 30
                    hot == Heat.WARM || lowPower -> 60
                    else -> max
                }
                val cap = minOf(wanted, ceiling)
                val reason = when {
                    cap < wanted && hot == Heat.HOT -> "The phone is hot, so Auto holds $cap FPS until it cools down."
                    cap < wanted && hot == Heat.WARM -> "The phone is warming up, so Auto holds $cap FPS for now."
                    cap < wanted && lowPower -> "Battery Saver is on, so Auto holds $cap FPS."
                    else -> "The phone is cool: $cap FPS with vsync. Auto steps down by itself if it warms up or Battery Saver comes on."
                }
                Policy(true, cap, cap, reason)
            }
        }
    }

    private fun publish() {
        heat = maxOf(statusHeat, headroomHeat)
        val next = resolve()
        policy = next
        val target = sink ?: return
        val before = applied
        if (before != null && before.vsync == next.vsync && before.cap == next.cap && before.displayHz == next.displayHz) return
        applied = next
        val raising = before == null || next.displayHz > before.displayHz
        target.apply(next.vsync, next.cap, next.displayHz.toFloat(), raising)
    }
}
