package com.wyrm.omrajput.data

import android.content.Context
import java.io.File

/**
 * Preference files that builds before 7.1.2 kept under an older prefix
 * (2026-10-12). Found by suffix, moved once into Wyrm's own name, then
 * deleted: the updater's state (beta switch, What's new seen, a pending
 * download) and the retired backup folder grant. A key the new file already
 * has (the install receiver can write first) is kept.
 */
object LegacyPrefs {
    private const val UPDATE = "wyrm_update_state"
    private const val BACKUP = "wyrm_backup_state"

    @JvmStatic
    fun migrate(context: Context) {
        runCatching {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            val names = dir.listFiles()?.map { it.name.removeSuffix(".xml") } ?: return
            names.forEach { name ->
                when {
                    name.endsWith("_update_state") && name != UPDATE -> move(context, name, UPDATE)
                    name.endsWith("_backup_state") && name != BACKUP -> context.deleteSharedPreferences(name)
                }
            }
        }
    }

    /** An old account document names the update file the old way. */
    fun currentName(name: String): String =
        if (name.endsWith("_update_state")) UPDATE else name

    private fun move(context: Context, from: String, to: String) {
        val old = context.getSharedPreferences(from, Context.MODE_PRIVATE).all
        val target = context.getSharedPreferences(to, Context.MODE_PRIVATE)
        val editor = target.edit()
        old.forEach { (key, value) ->
            if (target.contains(key)) return@forEach
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        if (editor.commit()) context.deleteSharedPreferences(from)
    }
}
