package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Near Original (OM, 2026-10-02). iOS twin: `WyrmNearOriginalStore` in
 * WyrmNearOriginal.swift.
 *
 * A Home switch that turns the match into slither's own: the original
 * minimap top-left with "server N", the leaderboard top-right in each snake's
 * colour, no Wyrm stats, the fixed original joystick and boost button, and the
 * original arrow in the snake's colour (only its size follows the player).
 * The engine draws all of it (`ui_overlay.c`, `mobile_controls.c`) from the
 * original game's numbers; nothing the player set is overwritten, so turning
 * it off brings everything back as it was. Display and touch only.
 *
 * Saved in `wyrm_near_original` (AccountSync.FILES) and in the account's
 * shared document as `nearOriginal`, so it is the same on Android and iOS.
 */
object NearOriginalStore {
    private const val PREFS = "wyrm_near_original"
    private const val KEY_ON = "on"

    var on by mutableStateOf(false)
        private set

    /** The lobby arena's number, for the minimap's "server N" (0 = unknown). */
    private var server = 0
    private var prefs: android.content.SharedPreferences? = null
    private var sink: ((Boolean, Int) -> Unit)? = null

    /** Called once by the activity; publishes the saved choice to the engine. */
    @JvmStatic
    fun attach(context: Context, publish: (Boolean, Int) -> Unit) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        on = store.getBoolean(KEY_ON, false)
        sink = publish
        publish(on, server)
    }

    /** After a log in or log out rewrote the file (AccountSync): read it again and republish. */
    fun reload(context: Context) {
        val publish = sink ?: return
        attach(context, publish)
    }

    fun applyOn(value: Boolean) {
        on = value
        prefs?.edit()?.putBoolean(KEY_ON, value)?.apply()
        sink?.invoke(on, server)
    }

    fun applyServer(number: Int) {
        val next = number.coerceAtLeast(0)
        if (next == server) return
        server = next
        sink?.invoke(on, server)
    }
}
