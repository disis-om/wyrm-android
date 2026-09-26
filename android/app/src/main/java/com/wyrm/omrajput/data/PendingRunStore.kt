package com.wyrm.omrajput.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A finished run waiting for the account backend to acknowledge it. */
data class PendingRun(
    val eventId: String,
    val playerId: String,
    val score: Int,
    val kills: Int,
)

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

    fun enqueue(playerId: String, score: Int, kills: Int): PendingRun = synchronized(lock) {
        val run = PendingRun(
            eventId = UUID.randomUUID().toString(),
            playerId = playerId,
            score = score.coerceAtLeast(0),
            kills = kills.coerceAtLeast(0),
        )
        writeLocked(readLocked() + run)
        run
    }

    fun pendingFor(playerId: String): List<PendingRun> = synchronized(lock) {
        readLocked().filter { it.playerId == playerId }
    }

    fun remove(eventId: String) = synchronized(lock) {
        writeLocked(readLocked().filterNot { it.eventId == eventId })
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
                    add(PendingRun(eventId, playerId, score, kills))
                }
            }
        }
    }

    private fun writeLocked(runs: List<PendingRun>) {
        val array = JSONArray()
        runs.forEach { run ->
            array.put(
                JSONObject()
                    .put("eventId", run.eventId)
                    .put("playerId", run.playerId)
                    .put("score", run.score)
                    .put("kills", run.kills)
            )
        }
        preferences.edit().putString(KEY_RUNS, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "wyrm_pending_runs"
        const val KEY_RUNS = "runs"
    }
}
