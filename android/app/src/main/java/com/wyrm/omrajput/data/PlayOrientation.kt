package com.wyrm.omrajput.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Play orientation (OM, 2026-10-01): a match can be played upright. iOS twin:
 * `WyrmPlayOrientation` in WyrmOrientation.swift.
 *
 * Everything stays the same, only turned: the lobby, the match and the layout
 * editors are portrait when this is on. Each orientation keeps its own layout
 * (where the joystick, boost, zoom bar, on-screen buttons and HUD pieces sit),
 * because a layout arranged for a wide screen is wrong for a tall one. The
 * engine holds only the layout in use; the other one waits here, and the two
 * swap when the orientation does (WyrmOverlay.switchPlayOrientation).
 *
 * Saved in `wyrm_orientation` (synced with the account, AccountSync.FILES):
 * `portrait`, and the stored layout of each orientation as JSON.
 */
object PlayOrientation {
    private const val PREFS = "wyrm_orientation"
    private const val KEY_PORTRAIT = "portrait"
    private const val KEY_LANDSCAPE_LAYOUT = "layout_landscape"
    private const val KEY_PORTRAIT_LAYOUT = "layout_portrait"

    var portrait by mutableStateOf(false)
        private set

    private var prefs: android.content.SharedPreferences? = null

    fun load(context: Context) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        portrait = store.getBoolean(KEY_PORTRAIT, false)
    }

    /** After a log in or log out rewrote the file (AccountSync). */
    fun reload(context: Context) = load(context)

    fun applyPortrait(value: Boolean) {
        portrait = value
        prefs?.edit()?.putBoolean(KEY_PORTRAIT, value)?.apply()
    }

    /** The layout last used in [portraitLayout] orientation, or null if there is none yet. */
    fun savedLayout(portraitLayout: Boolean): JSONObject? {
        val raw = prefs?.getString(if (portraitLayout) KEY_PORTRAIT_LAYOUT else KEY_LANDSCAPE_LAYOUT, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun saveLayout(portraitLayout: Boolean, layout: JSONObject) {
        prefs?.edit()?.putString(if (portraitLayout) KEY_PORTRAIT_LAYOUT else KEY_LANDSCAPE_LAYOUT, layout.toString())?.apply()
    }
}
