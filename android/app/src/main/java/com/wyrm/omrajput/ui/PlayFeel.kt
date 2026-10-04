package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Play feel (OM, 2026-10-05), Settings › Controls. iOS twin:
 * `WyrmPlayFeelStore` in WyrmPlayFeel.swift, same meaning.
 *
 * - [customArrow]: on (the default) the arrow moves with the player's own
 *   start distance and lag; off it moves exactly like slither's (Main.as:
 *   start distance, 0.6 catch-up a frame, the 260-unit release drift). Near
 *   Original always moves like slither's.
 * - [lookAhead]: slither's look ahead: the camera sits ahead of the snake
 *   toward where it is going (further while boosting). Both modes.
 * - [zoomSpring]: the zoom bar as a spring: the knob rests in the middle,
 *   toward + zooms in, toward - zooms out, and it springs back.
 *
 * The engine draws and steers (`mobile_controls_set_play_feel`). Saved in
 * `wyrm_play_feel`, synced with the account (AccountSync.FILES) and common to
 * every platform (the shared document: arrowCustomMotion, lookAhead,
 * zoomSpring).
 */
object PlayFeelStore {
    private const val PREFS = "wyrm_play_feel"
    private const val KEY_CUSTOM_ARROW = "custom_arrow"
    private const val KEY_LOOK_AHEAD = "look_ahead"
    private const val KEY_ZOOM_SPRING = "zoom_spring"

    var customArrow by mutableStateOf(true)
        private set
    var lookAhead by mutableStateOf(false)
        private set
    var zoomSpring by mutableStateOf(false)
        private set

    private var prefs: android.content.SharedPreferences? = null
    private var sink: ((Boolean, Boolean, Int) -> Unit)? = null

    /** Called once by the activity; publishes the saved choices to the engine. */
    @JvmStatic
    fun attach(context: Context, publish: (Boolean, Boolean, Int) -> Unit) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        customArrow = store.getBoolean(KEY_CUSTOM_ARROW, true)
        lookAhead = store.getBoolean(KEY_LOOK_AHEAD, false)
        zoomSpring = store.getBoolean(KEY_ZOOM_SPRING, false)
        sink = publish
        publishNow()
    }

    /** After a log in or log out rewrote the file (AccountSync): read it again and republish. */
    fun reload(context: Context) {
        val publish = sink ?: return
        attach(context, publish)
    }

    fun applyCustomArrow(value: Boolean) {
        customArrow = value
        prefs?.edit()?.putBoolean(KEY_CUSTOM_ARROW, value)?.apply()
        publishNow()
    }

    fun applyLookAhead(value: Boolean) {
        lookAhead = value
        prefs?.edit()?.putBoolean(KEY_LOOK_AHEAD, value)?.apply()
        publishNow()
    }

    fun applyZoomSpring(value: Boolean) {
        zoomSpring = value
        prefs?.edit()?.putBoolean(KEY_ZOOM_SPRING, value)?.apply()
        publishNow()
    }

    private fun publishNow() {
        sink?.invoke(!customArrow, lookAhead, if (zoomSpring) 1 else 0)
    }
}
