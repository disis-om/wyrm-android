package com.wyrm.omrajput.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Account-scoped, stale-while-revalidate cache for social read models. */
class SocialCache(context: Context) {
    data class Entry<T>(val value: T, val savedAt: Long) {
        val fresh: Boolean get() = System.currentTimeMillis() - savedAt < FRESH_MS
    }

    private val prefs = context.applicationContext.getSharedPreferences("wyrm_social_cache", Context.MODE_PRIVATE)
    val activePlayerId: String get() = prefs.getString("active_player", "").orEmpty()

    fun switchAccount(playerId: String) {
        val old = activePlayerId
        if (old.isNotBlank() && old != playerId) clear()
        prefs.edit().putString("active_player", playerId).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun profile(): Entry<ApiPlayer>? = read("profile") { it.player() }
    fun saveProfile(player: ApiPlayer) = write("profile", player.json())

    fun leaderboard(sort: String): Entry<List<ApiPlayer>>? = read("board_$sort") { root ->
        root.getJSONArray("value").objects().map { it.player() }
    }
    fun saveLeaderboard(sort: String, players: List<ApiPlayer>) = write("board_$sort", JSONArray().apply {
        players.forEach { put(it.json()) }
    })

    fun voiceRooms(): Entry<List<VoiceRoom>>? = read("voice_rooms") { root ->
        root.getJSONArray("value").objects().map { it.room() }
    }
    fun saveVoiceRooms(rooms: List<VoiceRoom>) = write("voice_rooms", JSONArray().apply {
        rooms.forEach { put(it.json()) }
    })

    private fun write(key: String, value: Any) {
        prefs.edit().putString(key, JSONObject().put("savedAt", System.currentTimeMillis()).put("value", value).toString()).apply()
    }

    private fun <T> read(key: String, decode: (JSONObject) -> T): Entry<T>? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val savedAt = root.getLong("savedAt")
            if (System.currentTimeMillis() - savedAt > RETAIN_MS) {
                prefs.edit().remove(key).apply(); return null
            }
            Entry(decode(root), savedAt)
        }.getOrElse { prefs.edit().remove(key).apply(); null }
    }

    companion object {
        const val FRESH_MS = 2 * 60_000L
        const val RETAIN_MS = 7 * 24 * 60 * 60_000L
    }
}

private fun ApiPlayer.json() = JSONObject()
    .put("id", id).put("ingameName", ingameName).put("username", username)
    .put("displayName", displayName).put("avatarKey", avatarKey).put("avatarUrl", avatarUrl)
    .put("bio", bio).put("highestScore", highestScore).put("kills", kills)
    .put("followerCount", followerCount).put("followingCount", followingCount)
    .put("createdAt", createdAt).put("deletedAt", deletedAt).put("isFollowing", isFollowing)
    .put("followsYou", followsYou).put("canMessage", canMessage)

private fun JSONObject.player() = ApiPlayer(
    id = getString("id"), ingameName = optString("ingameName").takeIf { it.isNotBlank() && it != "null" },
    username = optString("username").takeIf { it.isNotBlank() && it != "null" }, displayName = optString("displayName", "Unnamed"),
    avatarKey = optString("avatarKey", "mono-ink"), avatarUrl = optString("avatarUrl"), bio = optString("bio"),
    highestScore = optLong("highestScore"), kills = optLong("kills"), followerCount = optLong("followerCount"),
    followingCount = optLong("followingCount"), createdAt = optString("createdAt"), deletedAt = optString("deletedAt"),
    isFollowing = optBoolean("isFollowing"), followsYou = optBoolean("followsYou"), canMessage = optBoolean("canMessage"),
)

private fun VoiceRoom.json() = JSONObject().put("id", id).put("name", name).put("creator", JSONObject()
    .put("id", creator.id).put("displayName", creator.displayName).put("username", creator.username)
    .put("avatarKey", creator.avatarKey).put("avatarUrl", creator.avatarUrl))
    .put("gate", gate).put("active", active).put("activeCount", activeCount).put("activeSince", activeSince)
    .put("revision", revision).put("mine", mine).put("member", member).put("managedPublic", managedPublic)
    .put("capacity", capacity).put("suspended", suspended).put("createdAt", createdAt)

private fun JSONObject.room(): VoiceRoom {
    val owner = getJSONObject("creator")
    return VoiceRoom(
        id = getString("id"), name = getString("name"), creator = VoiceCreator(owner.getString("id"),
            owner.optString("displayName"), owner.optString("username"), owner.optString("avatarKey"), owner.optString("avatarUrl")),
        gate = optString("gate", "open"), active = optBoolean("active"), activeCount = optInt("activeCount"),
        activeSince = optString("activeSince"), revision = optInt("revision", 1), mine = optBoolean("mine"),
        member = optBoolean("member"), managedPublic = optBoolean("managedPublic"), capacity = optInt("capacity", 10),
        suspended = optBoolean("suspended"), createdAt = optString("createdAt"),
    )
}

private fun JSONArray.objects() = buildList {
    for (index in 0 until length()) add(getJSONObject(index))
}
