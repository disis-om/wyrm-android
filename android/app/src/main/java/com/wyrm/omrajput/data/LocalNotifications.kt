package com.wyrm.omrajput.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-only receipts, kept under the signed-in account that produced them. */
class LocalNotifications(context: Context) {
    private val preferences = context.getSharedPreferences("wyrm_local_notifications", Context.MODE_PRIVATE)

    fun all(playerId: String): List<WyrmNotification> {
        if (playerId.isBlank()) return emptyList()
        val rows = runCatching { JSONArray(preferences.getString(key(playerId), "[]")) }
            .getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val createdAt = row.optString("createdAt")
                add(
                    WyrmNotification(
                        id = row.optString("id"),
                        kind = NotificationKind.BACKUP,
                        title = row.optString("title"),
                        body = row.optString("body"),
                        timestamp = relativeTime(createdAt),
                        createdAt = createdAt,
                        read = row.optBoolean("read"),
                    ),
                )
            }
        }
    }

    fun add(playerId: String, title: String, body: String): WyrmNotification {
        val createdAt = java.time.Instant.now().toString()
        val notification = WyrmNotification(
            id = "local-backup-${java.util.UUID.randomUUID()}",
            kind = NotificationKind.BACKUP,
            title = title,
            body = body,
            timestamp = relativeTime(createdAt),
            createdAt = createdAt,
        )
        val existing = all(playerId).take(49)
        save(playerId, listOf(notification) + existing)
        return notification
    }

    fun markRead(playerId: String, id: String) {
        save(playerId, all(playerId).map { if (it.id == id) it.copy(read = true) else it })
    }

    fun setRead(playerId: String, id: String, read: Boolean) {
        save(playerId, all(playerId).map { if (it.id == id) it.copy(read = read) else it })
    }

    fun delete(playerId: String, id: String) {
        save(playerId, all(playerId).filterNot { it.id == id })
    }

    fun markAllRead(playerId: String) {
        save(playerId, all(playerId).map { it.copy(read = true) })
    }

    private fun save(playerId: String, notifications: List<WyrmNotification>) {
        if (playerId.isBlank()) return
        val rows = JSONArray()
        notifications.take(50).forEach { notification ->
            rows.put(
                JSONObject()
                    .put("id", notification.id)
                    .put("title", notification.title)
                    .put("body", notification.body)
                    .put("createdAt", notification.createdAt)
                    .put("read", notification.read),
            )
        }
        preferences.edit().putString(key(playerId), rows.toString()).apply()
    }

    private fun key(playerId: String) = "player:$playerId"
}
