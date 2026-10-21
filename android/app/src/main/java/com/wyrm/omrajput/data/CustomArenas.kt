package com.wyrm.omrajput.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import org.json.JSONObject

/**
 * Custom arenas (OM, 2026-10-13). iOS twin: `WyrmCustomArenas.swift`, same
 * meaning.
 *
 * Community-run arenas know a player by a 16-character id ([0-9A-Za-z]) that
 * NTL sends right after the challenge answer: 'C' + the id, only to the
 * arenas on NTL's list (`ntl-slither.com/slither/useplayerid.php?v=<n>`
 * answers `{"v":n,"d":["ip:port",...]}`; official arenas are not on it). The
 * engine sends it (network/callback.c, `WyrmActivity.setCustomArenas`).
 *
 *  - The id is made once (crypto random, NTL's alphabet, bytes >= 248 thrown
 *    away) and follows the account: `player_id` in [PREFS] (synced) and the
 *    shared document key `customArenaId`, the same on every platform.
 *  - The list is this phone's cache (the file's other keys, kept on log out),
 *    asked for again at most every 6 hours, as NTL does.
 */
object CustomArenas {
    const val PREFS = "wyrm_custom_arena"
    const val KEY_ID = "player_id"
    private const val KEY_LINES = "list_lines"
    private const val KEY_VERSION = "list_version"
    private const val KEY_CHECKED = "list_checked_at"
    private const val LIST_URL = "https://ntl-slither.com/slither/useplayerid.php"
    private const val EVERY_MS = 6 * 60 * 60_000L
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val TAG = "WyrmCustomArenas"
    private val VALID = Regex("^[0-9A-Za-z]{16}$")

    private var context: Context? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var fetching = false

    fun isValid(id: String?): Boolean = id != null && VALID.matches(id)

    /** WyrmActivity.onCreate (after the engine library is loaded). */
    @JvmStatic
    fun start(activity: Context) {
        context = activity.applicationContext
        publish()
        refresh()
    }

    /** WyrmActivity.onResume: a newer list, at most every 6 hours. */
    @JvmStatic
    fun onResume() = refresh()

    /** The player's id, made the first time it is asked for. */
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
        prefs.edit().putString(KEY_ID, made.toString()).commit()
        return made.toString()
    }

    /** The account's id (shared document `customArenaId`); anything else is skipped. */
    fun adopt(c: Context, value: Any?) {
        val text = value as? String ?: return
        if (!isValid(text)) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ID, text).commit()
    }

    /** The id and the list reach the engine (also after a log in or log out). */
    fun publish() {
        val c = context ?: return
        val lines = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LINES, "").orEmpty()
        try {
            com.wyrm.omrajput.WyrmActivity.setCustomArenas(id(c), lines)
        } catch (_: UnsatisfiedLinkError) {
            // The engine is not loaded yet; start() runs again with the activity.
        }
    }

    /** NTL's list, at most every 6 hours; a newer version replaces the cache. */
    fun refresh(force: Boolean = false) {
        val c = context ?: return
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (fetching || (!force && now - prefs.getLong(KEY_CHECKED, 0L) < EVERY_MS)) return
        fetching = true
        val version = prefs.getInt(KEY_VERSION, 0)
        Thread({
            try {
                val connection = (URL("$LIST_URL?v=$version").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    useCaches = false
                    // The service answers pages from the game's own site only.
                    setRequestProperty("Origin", "https://slither.io")
                    setRequestProperty("Referer", "https://slither.io/")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        val edit = prefs.edit().putLong(KEY_CHECKED, now)
                        val newer = body.optInt("v", 0)
                        val list = body.optJSONArray("d")
                        if (newer > version && list != null) {
                            val lines = (0 until list.length())
                                .mapNotNull { (list.opt(it) as? String)?.trim() }
                                .filter { it.isNotEmpty() && '\n' !in it }
                            edit.putInt(KEY_VERSION, newer).putString(KEY_LINES, lines.joinToString("\n"))
                            Log.i(TAG, "list v$newer: ${lines.size} arenas")
                        }
                        edit.commit()
                        main.post { publish() }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "list not fetched: ${e.message}")
            } finally {
                fetching = false
            }
        }, "wyrm-custom-arenas").start()
    }
}
