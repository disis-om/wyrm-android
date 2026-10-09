package com.wyrm.omrajput.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A tiny durable outbox for finished runs.
 *
 * A death can happen with Auto Respawn on, with the app about to be closed, or
 * during a short network outage. The event id survives all three and makes a
 * later retry safe: the backend already ignores the same event twice.
 */
class PendingRunStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun enqueue(playerId: String, score: Int, kills: Int, durationMs: Long = -1, arena: String = ""): PendingRun = synchronized(lock) {
        val run = PendingRun(
            eventId = UUID.randomUUID().toString(),
            playerId = playerId,
            score = score.coerceAtLeast(0),
            kills = kills.coerceAtLeast(0),
            durationMs = durationMs,
            arena = arena,
        )
        // commit(), not apply(): the run is on disk before this returns, so
        // even a crash or a killed process right after a death cannot lose it
        // (2026-10-09). Removals may stay lazy: a lost removal only resends a
        // run, and the server ignores an event id it already counted.
        writeLocked(readLocked() + run, durable = true)
        run
    }

    fun pendingFor(playerId: String): List<PendingRun> = synchronized(lock) {
        readLocked().filter { it.playerId == playerId }
    }

    fun remove(eventId: String) = synchronized(lock) {
        writeLocked(readLocked().filterNot { it.eventId == eventId })
    }

    fun removeAll(eventIds: Collection<String>) = synchronized(lock) {
        val gone = eventIds.toSet()
        writeLocked(readLocked().filterNot { it.eventId in gone })
    }

    private fun readLocked(): List<PendingRun> {
        val array = runCatching {
            JSONArray(preferences.getString(KEY_RUNS, "[]").orEmpty())
        }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val eventId = row.optString("eventId")
                val playerId = row.optString("playerId")
                val score = row.optInt("score", -1)
                val kills = row.optInt("kills", -1)
                if (eventId.isNotBlank() && playerId.isNotBlank() && score >= 0 && kills >= 0) {
                    add(PendingRun(eventId, playerId, score, kills,
                        row.optLong("durationMs", -1L), row.optString("arena", "")))
                }
            }
        }
    }

    private fun writeLocked(runs: List<PendingRun>, durable: Boolean = false) {
        val array = JSONArray()
        runs.forEach { run ->
            array.put(
                JSONObject()
                    .put("eventId", run.eventId)
                    .put("playerId", run.playerId)
                    .put("score", run.score)
                    .put("kills", run.kills)
                    .put("durationMs", run.durationMs)
                    .put("arena", run.arena)
            )
        }
        val edit = preferences.edit().putString(KEY_RUNS, array.toString())
        if (durable) edit.commit() else edit.apply()
    }

    private companion object {
        const val PREFS = "wyrm_pending_runs"
        const val KEY_RUNS = "runs"
    }
}
