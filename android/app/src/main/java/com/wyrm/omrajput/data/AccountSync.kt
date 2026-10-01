package com.wyrm.omrajput.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Account-linked settings (OM, 2026-10-01). Backend: `backend/src/account-settings.mjs`.
 *
 * Everything a player sets lives in their account, not on the phone. The app
 * saves it when it goes to the background and when the player logs out, and
 * puts it back during the log-in animation. Log out wipes the device, so
 * nothing of one account is ever left for the next ("app logout = aatma
 * nikal gayi").
 *
 * Two documents per account:
 *  - **android** (this platform's own copy): the engine's settings table and
 *    on-screen buttons, and the app's own preference files listed in [FILES];
 *  - **shared** (the same on every platform): the skin, the Wyrm look, the
 *    arena background and the in-game name. Written by the Overlay, which
 *    knows those values; see [sharedDocument].
 *
 * **The rule for every future build** (CLAUDE.md): a new thing a player sets
 * goes into a file listed here, or it will not follow the account. A renamed
 * key or setting gets an entry in [PREF_RENAMES] / [ENGINE_RENAMES]. Restore is
 * per key: unknown, missing or invalid keys are skipped and never fail the rest.
 * `tools/check-account-sync.py` fails the build check when a preference file
 * in the code is not listed here.
 */
object AccountSync {
    const val PLATFORM = "android"
    /** Raise when the document's shape changes; [applyPlatform] migrates older ones. */
    const val SCHEMA = 1

    /** What happens to a preference file. */
    enum class Scope {
        /** Saved to the account, wiped on log out, restored on log in. */
        SYNC,
        /** Account data kept only as cache: wiped on log out, never uploaded. */
        WIPE,
        /** About this phone, not the player (update downloads, crash history): kept. */
        KEEP,
    }

    /**
     * One preference file. [keys] limits what is synced (null = every key);
     * the keys outside it follow [rest].
     */
    class PrefFile(val name: String, val scope: Scope, val keys: Set<String>? = null, val rest: Scope = Scope.WIPE)

    val FILES: List<PrefFile> = listOf(
        // Synced: the player's choices.
        PrefFile("wyrm_ui_preferences", Scope.SYNC),           // theme, intensity, nickname_chosen, …
        PrefFile("wyrm_arrows", Scope.SYNC),
        PrefFile("wyrm_notification_preferences", Scope.SYNC),
        PrefFile("wyrm_voice_preferences", Scope.SYNC),
        PrefFile("wyrm_voice_ui", Scope.SYNC),
        PrefFile("wyrm_saved_arenas", Scope.SYNC),
        PrefFile("wyrm_recent_arenas", Scope.SYNC),
        PrefFile("wyrm_local_stats", Scope.SYNC),
        PrefFile("wyrm_skin_studio", Scope.SYNC),
        PrefFile("wyrm_performance", Scope.SYNC),              // Settings › Performance: mode, FPS limit
        // Run receipts not yet uploaded: carried by the account so none is lost
        // (the server ignores a receipt it already has, by event id).
        PrefFile("wyrm_pending_runs", Scope.SYNC),
        PrefFile("wyrm_stats_sync", Scope.SYNC),
        PrefFile("wyrm_drop", Scope.SYNC, keys = setOf("auto_send")),
        PrefFile("wyrm_crash", Scope.SYNC, keys = setOf("auto_send"), rest = Scope.KEEP),
        PrefFile("vlither_update_state", Scope.SYNC, keys = setOf("beta_updates"), rest = Scope.KEEP),
        // Wiped: caches and session state of the account.
        PrefFile("wyrm_look", Scope.WIPE),                     // travels in the shared document
        PrefFile("wyrm_session", Scope.WIPE),
        PrefFile("wyrm_team_mode", Scope.WIPE),                // team credentials never leave the phone
        PrefFile("wyrm_voice_runtime", Scope.WIPE),
        PrefFile("wyrm_voice_call", Scope.WIPE),
        PrefFile("wyrm_social_cache", Scope.WIPE),
        PrefFile("wyrm_social", Scope.WIPE),
        PrefFile("wyrm_messages", Scope.WIPE),
        PrefFile("wyrm_notifications", Scope.WIPE),
        PrefFile("wyrm_alerts", Scope.WIPE),
        PrefFile("wyrm_receipts", Scope.WIPE),
        PrefFile("wyrm_local_notifications", Scope.WIPE),
        PrefFile("wyrm_support_seen", Scope.WIPE),
        PrefFile("wyrm_release_notes", Scope.WIPE),
        PrefFile("wyrm_account_sync", Scope.WIPE),             // an owed restore, per account
        // The retired manual backups (2026-10-01): their folder grant goes too.
        PrefFile("wyrm_backup_state", Scope.WIPE),
        PrefFile("vlither_backup_state", Scope.WIPE),
        // About the phone itself: kept.
        PrefFile("hidapi", Scope.KEEP),                        // SDL game controllers
    )

    /** Old preference key → new, per file, for documents saved by older builds. */
    private val PREF_RENAMES: Map<String, Map<String, String>> = emptyMap()

    /** Old engine setting id → new id. */
    private val ENGINE_RENAMES: Map<String, String> = emptyMap()

    /** How the Overlay reaches the engine's settings table (see WyrmOverlay.Host). */
    interface Engine {
        fun readSettings(): String
        fun readHotkeys(): String
        fun writeSetting(id: String, values: FloatArray)
        fun writeHotkey(action: Int, key: Int, mode: Int, visible: Boolean, x: Float, y: Float)
        /** Engine actions: 1 = every setting back to its default. */
        fun action(mask: Int)
    }

    class Report(val applied: Int, val skipped: Int)

    /* ------------------------------------------------------------ snapshot */

    /** This phone's copy, ready for `PUT /v1/me/settings/android`. */
    fun platformDocument(context: Context, engine: Engine?): JSONObject {
        val doc = JSONObject().put("schema", SCHEMA).put("platform", PLATFORM)
        if (engine != null) {
            val settings = SettingsCodec.settings(engine.readSettings())
            if (settings.isNotEmpty()) {
                val values = JSONObject()
                settings.forEach { values.put(it.id, it.raw) }
                doc.put("engine", values)
            }
            val keys = SettingsCodec.hotkeys(engine.readHotkeys())
            if (keys.isNotEmpty()) {
                doc.put("hotkeys", JSONArray().apply {
                    keys.forEach {
                        put(JSONObject().put("action", it.action).put("name", it.name).put("key", it.key)
                            .put("mode", it.mode).put("visible", it.visible)
                            .put("x", it.x.toDouble()).put("y", it.y.toDouble()))
                    }
                })
            }
        }
        val prefs = JSONObject()
        FILES.filter { it.scope == Scope.SYNC }.forEach { file ->
            val store = context.getSharedPreferences(file.name, Context.MODE_PRIVATE)
            val out = JSONObject()
            store.all.forEach { (key, value) ->
                if (file.keys != null && key !in file.keys) return@forEach
                encode(value)?.let { out.put(key, it) }
            }
            if (out.length() > 0) prefs.put(file.name, out)
        }
        doc.put("prefs", prefs)
        return doc
    }

    /** A typed value: `["b", true]`, `["i", 3]`, `["l", 9]`, `["f", 0.5]`, `["s", "x"]`, `["S", […]]`. */
    private fun encode(value: Any?): JSONArray? = when (value) {
        is Boolean -> JSONArray().put("b").put(value)
        is Int -> JSONArray().put("i").put(value)
        is Long -> JSONArray().put("l").put(value)
        is Float -> JSONArray().put("f").put(value.toDouble())
        is String -> JSONArray().put("s").put(value)
        is Set<*> -> JSONArray().put("S").put(JSONArray(value.filterIsInstance<String>()))
        else -> null
    }

    private fun put(editor: SharedPreferences.Editor, key: String, typed: JSONArray): Boolean = runCatching {
        when (typed.getString(0)) {
            "b" -> editor.putBoolean(key, typed.getBoolean(1))
            "i" -> editor.putInt(key, typed.getInt(1))
            "l" -> editor.putLong(key, typed.getLong(1))
            "f" -> editor.putFloat(key, typed.getDouble(1).toFloat().takeIf { it.isFinite() } ?: return false)
            "s" -> editor.putString(key, typed.getString(1))
            "S" -> {
                val rows = typed.getJSONArray(1)
                editor.putStringSet(key, (0 until rows.length()).map { rows.getString(it) }.toSet())
            }
            else -> return false
        }
        true
    }.getOrDefault(false)

    /* --------------------------------------------------------------- restore */

    /**
     * Puts this platform's saved copy back, one key at a time. Preference files
     * first (synchronously, so stores reloaded afterwards read them), then the
     * engine: only values that differ, in small batches, because the engine's
     * mailbox holds 128 changes per frame.
     */
    suspend fun applyPlatform(context: Context, doc: JSONObject, engine: Engine?): Report {
        var applied = 0
        var skipped = 0
        val prefs = doc.optJSONObject("prefs") ?: JSONObject()
        val known = FILES.associateBy { it.name }
        prefs.keys().forEach { name ->
            val file = known[name]
            val rows = prefs.optJSONObject(name)
            if (file == null || file.scope != Scope.SYNC || rows == null) { skipped += rows?.length() ?: 1; return@forEach }
            val renames = PREF_RENAMES[name].orEmpty()
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            rows.keys().forEach { stored ->
                val key = renames[stored] ?: stored
                val typed = rows.optJSONArray(stored)
                if (typed == null || (file.keys != null && key !in file.keys) || !put(editor, key, typed)) skipped++ else applied++
            }
            editor.commit()
        }

        if (engine != null) {
            val current = SettingsCodec.settings(engine.readSettings()).associateBy { it.id }
            val saved = doc.optJSONObject("engine") ?: JSONObject()
            val changes = mutableListOf<Pair<String, FloatArray>>()
            saved.keys().forEach { stored ->
                val id = ENGINE_RENAMES[stored] ?: stored
                val setting = current[id]
                val raw = saved.optString(stored, "")
                val values = raw.split(",").mapNotNull { it.trim().toFloatOrNull()?.takeIf(Float::isFinite) }
                if (setting == null || values.isEmpty()) { skipped++; return@forEach }
                val wanted = when (setting.type) {
                    SettingType.COLOR3, SettingType.COLOR4 -> values.map { it.coerceIn(0f, 1f) }
                    else -> listOf(values[0].coerceIn(minOf(setting.minimum, setting.maximum), maxOf(setting.minimum, setting.maximum)))
                }
                val now = setting.raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
                val same = now.size >= wanted.size && wanted.indices.all { kotlin.math.abs(now[it] - wanted[it]) < 0.0006f }
                if (!same) changes += id to wanted.toFloatArray()
                applied++
            }
            changes.chunked(40).forEachIndexed { index, batch ->
                if (index > 0) delay(140)
                batch.forEach { (id, values) -> engine.writeSetting(id, values) }
            }

            val buttons = SettingsCodec.hotkeys(engine.readHotkeys())
            val byName = buttons.associateBy { it.name }
            val byAction = buttons.associateBy { it.action }
            val rows = doc.optJSONArray("hotkeys") ?: JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val target = byName[row.optString("name")] ?: byAction[row.optInt("action", -1)]
                if (target == null) { skipped++; continue }
                val x = row.optDouble("x", Double.NaN).toFloat()
                val y = row.optDouble("y", Double.NaN).toFloat()
                if (!x.isFinite() || !y.isFinite()) { skipped++; continue }
                engine.writeHotkey(target.action, row.optInt("key", target.key),
                    if (target.fixedMode) target.mode else row.optInt("mode", target.mode),
                    row.optBoolean("visible", target.visible), x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                applied++
            }
        }
        return Report(applied, skipped)
    }

    /* ------------------------------------------------------------------ wipe */

    /**
     * Log out: every preference file that is not about the phone itself, the
     * engine back to its defaults (skin, controls, layouts, name), and the
     * app's caches. Files listed as KEEP, and the non-synced keys of mixed
     * files marked KEEP, stay.
     */
    fun wipeDevice(context: Context, engine: Engine?) {
        FILES.forEach { file ->
            val store = context.getSharedPreferences(file.name, Context.MODE_PRIVATE)
            when {
                file.scope == Scope.KEEP -> Unit
                file.keys == null -> store.edit().clear().commit()
                file.rest == Scope.KEEP -> store.edit().apply { file.keys.forEach { remove(it) } }.commit()
                else -> store.edit().clear().commit()
            }
        }
        engine?.action(1)
        // Cached pictures and drafts of the account. Folders stay (their owners
        // create them once); a downloaded update stays too.
        runCatching {
            context.cacheDir.listFiles()?.forEach { entry ->
                when {
                    entry.name == "updates" -> Unit
                    entry.isDirectory -> entry.listFiles()?.forEach(File::deleteRecursively)
                    else -> entry.delete()
                }
            }
        }
    }
}
