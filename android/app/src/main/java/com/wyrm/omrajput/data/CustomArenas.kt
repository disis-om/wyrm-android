package com.wyrm.omrajput.data

import android.content.Context
import java.security.SecureRandom

/**
 * Custom arenas (OM, 2026-10-13; reworked 2026-10-24). iOS twin:
 * `WyrmCustomArenas.swift`, same meaning.
 *
 * Community-run arenas know a player by a 16-character id ([0-9A-Za-z]) that
 * NTL sends right after the challenge answer: 'C' + the id. The engine sends
 * it to every arena (network/callback.c, `WyrmActivity.setCustomArenas`), as
 * NTL's `!idforce` does: NTL 9.76 has no online list any more, so the old
 * list from `useplayerid.php` was always empty and the id never went out.
 *
 * The id belongs to this phone (OM, 2026-10-24): made once (crypto random,
 * NTL's alphabet, bytes >= 248 thrown away) under [KEY_ID], kept through
 * restarts, updates, log out and log in, and gone only when the app is
 * deleted (`allowBackup` is false). It is not in the account any more. The
 * account-era key `player_id` is dropped once, so every phone makes a fresh
 * id with this update and keeps it.
 */
object CustomArenas {
    const val PREFS = "wyrm_custom_arena"
    const val KEY_ID = "device_id"
    /** 2026-10-13 to 2026-10-23: the account's id and NTL's list cache. */
    private val OLD_KEYS = listOf("player_id", "list_lines", "list_version", "list_checked_at")
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val VALID = Regex("^[0-9A-Za-z]{16}$")

    private var context: Context? = null

    fun isValid(id: String?): Boolean = id != null && VALID.matches(id)

    /** WyrmActivity.onCreate (after the engine library is loaded). */
    @JvmStatic
    fun start(activity: Context) {
        context = activity.applicationContext
        publish()
    }

    /** WyrmActivity.onResume: nothing to fetch any more; the engine keeps the id. */
    @JvmStatic
    fun onResume() = Unit

    /** This phone's id, made the first time it is asked for and kept for good. */
    @Synchronized
    fun id(c: Context): String {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.takeIf(::isValid)?.let { return it }
        val random = SecureRandom()
        val bytes = ByteArray(16)
        val made = StringBuilder()
        while (made.length < 16) {
            random.nextBytes(bytes)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                if (v < 248 && made.length < 16) made.append(ALPHABET[v % 62])
            }
        }
        val edit = prefs.edit().putString(KEY_ID, made.toString())
        OLD_KEYS.forEach { edit.remove(it) }
        edit.commit()
        return made.toString()
    }

    /** The id reaches the engine (also after a log in or log out). */
    fun publish() {
        val c = context ?: return
        try {
            com.wyrm.omrajput.WyrmActivity.setCustomArenas(id(c), "")
        } catch (_: UnsatisfiedLinkError) {
            // The engine is not loaded yet; start() runs again with the activity.
        }
    }
}
