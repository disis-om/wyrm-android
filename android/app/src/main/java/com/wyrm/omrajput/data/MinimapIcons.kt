package com.wyrm.omrajput.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Minimap icons (OM, 2026-11-07), Settings › Display › Minimap. iOS twin:
 * `WyrmMinimapIcons` in WyrmMinimapIcons.swift, same keys and meaning.
 *
 * - [ntl]: the icon NTL teammates see for you on their minimap, NTL's own
 *   numbering (`di` on the team poll): 0..741, or 1000 for NTL's plain dot.
 * - [wyrm]: the icon Wyrm friends (mutual follows) see for you, sent on the
 *   Wyrm live socket: 0..19 of Wyrm's own icons (never NTL's art).
 *
 * Saved in `wyrm_minimap_icons` (AccountSync SYNC) and account-wide: the
 * shared document keys `ntlMinimapIcon`, `wyrmMinimapIcon`.
 */
object MinimapIcons {
    private const val PREFS = "wyrm_minimap_icons"
    const val NTL_DEFAULT = 1000
    const val WYRM_DEFAULT = 0
    const val WYRM_COUNT = 20

    var ntl by mutableIntStateOf(NTL_DEFAULT)
        private set
    var wyrm by mutableIntStateOf(WYRM_DEFAULT)
        private set

    private var prefs: android.content.SharedPreferences? = null
    var onChange: (() -> Unit)? = null

    fun validNtl(value: Int) = value in 0..741 || value in 996..1000
    fun validWyrm(value: Int) = value in 0 until WYRM_COUNT

    @JvmStatic
    fun reload(context: Context) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        ntl = store.getInt("ntl", NTL_DEFAULT).takeIf(::validNtl) ?: NTL_DEFAULT
        wyrm = store.getInt("wyrm", WYRM_DEFAULT).takeIf(::validWyrm) ?: WYRM_DEFAULT
    }

    fun applyNtl(value: Int) {
        if (!validNtl(value)) return
        ntl = value
        prefs?.edit()?.putInt("ntl", value)?.apply()
        onChange?.invoke()
    }

    fun applyWyrm(value: Int) {
        if (!validWyrm(value)) return
        wyrm = value
        prefs?.edit()?.putInt("wyrm", value)?.apply()
        onChange?.invoke()
    }

    /** Restore from the shared document: each key on its own, bad ones skipped. */
    fun restore(context: Context, ntlValue: Int?, wyrmValue: Int?) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        store.edit().apply {
            ntlValue?.takeIf(::validNtl)?.let { putInt("ntl", it) }
            wyrmValue?.takeIf(::validWyrm)?.let { putInt("wyrm", it) }
        }.commit()
        reload(context)
    }
}
