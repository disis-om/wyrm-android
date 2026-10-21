package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color

/**
 * The look of the arena's team roster and chat window (OM, 2026-10-04).
 *
 * The engine draws both (`platform/android_team.c`), in the match and in the
 * layout editor; these are the numbers it draws them with, set from the
 * editor's long-press options: roster size, opacity, back-plate, width, height,
 * name and data colours; chat width, height, name and message colours, and
 * its own back-plate. The chat's
 * size and opacity stay the engine's own `layout.chat_scale` /
 * `layout.chat_opacity`. Saved in `wyrm_team_hud`, synced with the account
 * (AccountSync.FILES, platform document: layouts are per device). iOS twin:
 * `WyrmTeamHudStore` in WyrmTeamHud.swift, same order and meaning.
 */
object TeamHudStore {
    private const val PREFS = "wyrm_team_hud"

    /** Editor ids are `teamhud.<key>`; the order is the engine's. */
    val KEYS = listOf(
        "team_scale", "team_opacity", "team_width", "team_height", "team_name", "team_data",
        "chat_width", "chat_height", "chat_name", "chat_text",
        "team_panel", "chat_panel", "stats_panel",
        // The stats panel's text colour (OM, 2026-10-11), appended: the engine
        // reads this order, and an older engine ignores the extra value.
        "stats_text",
        // The leaderboard's WIDTH and HEIGHT (OM, 2026-10-13), appended likewise:
        // multiples of the board's own width and row height.
        "lb_width", "lb_height",
        // The chat window's ONLINE tab (OM, 2026-10-14): name and
        // "@username · server" colours, appended likewise.
        "online_name", "online_data",
        // GLOBAL's own name and message colours, and the folded chat button
        // drawn like the keys: label + border, fill (9 = grey), size, opacity
        // (OM, 2026-10-18), appended likewise.
        "global_name", "global_text",
        "chatbtn_label", "chatbtn_fill", "chatbtn_scale", "chatbtn_opacity",
    )
    private val DEFAULTS = floatArrayOf(1f, 1f, 340f, 210f, 0f, 0f, 400f, 270f, 0f, 0f, 1f, 1f, 1f, 0f, 1f, 1f, 0f, 0f,
        0f, 0f, 0f, 9f, 1f, 0.9f)
    private val RANGES = listOf(
        0.65f..1.60f, 0.05f..1f, 220f..900f, 120f..800f, 0f..8f, 0f..8f,
        240f..1000f, 150f..900f, 0f..8f, 0f..8f,
        0f..1f, 0f..1f, 0f..1f,
        0f..8f,
        0.7f..2.2f, 1f..2.2f,
        0f..8f, 0f..8f,
        0f..8f, 0f..8f,
        0f..8f, 0f..9f, 0.6f..2.5f, 0.1f..1f,
    )

    /** Colour indexes: whole numbers. */
    private fun isColour(key: String) =
        key.endsWith("_name") || key.endsWith("_data") || key.endsWith("_text") ||
            key == "chatbtn_label" || key == "chatbtn_fill"

    /** The engine's palette; 0 keeps the theme's colour. */
    val COLOUR_NAMES = listOf("Theme", "White", "Black", "Yellow", "Cyan", "Green", "Pink", "Orange", "Red")
    val COLOUR_SWATCHES = listOf(
        Color.Unspecified, Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFFFFD54A), Color(0xFF4DD9FF),
        Color(0xFF5BE37D), Color(0xFFFF6FB5), Color(0xFFFF9A3C), Color(0xFFFF5A5A),
    )

    private val values = mutableStateMapOf<String, Float>()
    private var prefs: android.content.SharedPreferences? = null
    private var sink: ((FloatArray) -> Unit)? = null

    fun value(key: String): Float = values[key] ?: DEFAULTS[KEYS.indexOf(key)]

    fun range(key: String): ClosedFloatingPointRange<Float> = RANGES[KEYS.indexOf(key)]

    /** Called once by the activity; publishes the saved look to the engine. */
    @JvmStatic
    fun attach(context: Context, publish: (FloatArray) -> Unit) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        KEYS.forEachIndexed { index, key ->
            values[key] = store.getFloat(key, DEFAULTS[index]).let {
                if (it.isFinite()) it.coerceIn(RANGES[index]) else DEFAULTS[index]
            }
        }
        // GLOBAL used TEAM's colours until 2026-10-18: keep what was chosen.
        if (!store.contains("global_name")) values["global_name"] = values["chat_name"] ?: 0f
        if (!store.contains("global_text")) values["global_text"] = values["chat_text"] ?: 0f
        sink = publish
        publish(snapshot())
    }

    /** After a log in or log out rewrote the file (AccountSync): read it again and republish. */
    fun reload(context: Context) {
        val publish = sink ?: return
        attach(context, publish)
    }

    /** Whether an editor id is one of these (`teamhud.<key>`). */
    fun owns(id: String): Boolean = id.startsWith("teamhud.") && id.removePrefix("teamhud.") in KEYS

    fun apply(id: String, next: Float) {
        val key = id.removePrefix("teamhud.")
        val index = KEYS.indexOf(key)
        if (index < 0 || !next.isFinite()) return
        val clamped = next.coerceIn(RANGES[index]).let { if (isColour(key)) kotlin.math.round(it) else it }
        values[key] = clamped
        prefs?.edit()?.putFloat(key, clamped)?.apply()
        sink?.invoke(snapshot())
    }

    private fun snapshot(): FloatArray = FloatArray(KEYS.size) { value(KEYS[it]) }
}
