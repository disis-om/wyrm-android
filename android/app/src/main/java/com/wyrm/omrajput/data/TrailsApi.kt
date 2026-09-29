package com.wyrm.omrajput.data

import org.json.JSONObject

/*
 * Trails (OM, 2026-09-28): photos or words from the Wyrm community, with bead
 * likes and replies. The routes are in `backend/src/trails.mjs`; the calls
 * live in `WyrmRepository` beside every other authenticated call. Wyrm iOS:
 * `WyrmTrails.swift`.
 */

/**
 * Trails are paused for the beta (OM, 2026-09-29). The code, routes and store
 * stay; only every player-facing entry point is hidden (Social teaser, profile
 * grid and Trails stat, trail badges, trail alerts and pushes, the Trails group
 * in Settings › Notifications). `true` brings every entry point back.
 * Java reads it as `com.wyrm.omrajput.data.TrailsApiKt.TRAILS_ENABLED`.
 */
const val TRAILS_ENABLED = false

/** Badges that only make sense with Trails on; hidden from the strip and its count while paused. */
val TRAIL_BADGE_IDS = setOf("trailblazer", "crowd-favourite")

data class TrailAuthor(
    val playerId: String,
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val avatarKey: String,
) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull() }
            .joinToString("").uppercase().ifEmpty { "W" }
}

data class TrailPhoto(val url: String, val width: Int, val height: Int)

data class Trail(
    val id: String,
    val kind: String,
    val caption: String,
    val photo: TrailPhoto?,
    val thumbUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val liked: Boolean,
    val mine: Boolean,
    val createdAt: String,
    val author: TrailAuthor,
) {
    /** Width over height, held between a tall 4:5 and a wide 1.91:1. */
    val aspect: Float
        get() = photo?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { (it.width.toFloat() / it.height).coerceIn(0.8f, 1.91f) } ?: 1f
}

data class TrailComment(
    val id: String,
    val body: String,
    val createdAt: String,
    val mine: Boolean,
    val author: TrailAuthor,
)

data class TrailPage(val trails: List<Trail>, val nextCursor: String?)
data class TrailCommentPage(val comments: List<TrailComment>, val nextCursor: String?)
data class TrailLike(val liked: Boolean, val likeCount: Int)
data class TrailReply(val comment: TrailComment, val commentCount: Int)

private fun String.absolute(base: String) = if (startsWith("/")) "$base$this" else this

private fun JSONObject.text(name: String): String =
    optString(name).takeIf { it.isNotBlank() && it != "null" }.orEmpty()

internal fun JSONObject.toTrailAuthor(base: String): TrailAuthor {
    val name = listOf(text("displayName"), text("username"), text("ingameName")).firstOrNull { it.isNotBlank() }
        ?: "Wyrm player"
    val username = text("username")
    return TrailAuthor(
        playerId = text("playerId"),
        name = name,
        handle = if (username.isBlank()) "" else "@$username",
        avatarUrl = text("avatarUrl").absolute(base),
        avatarKey = text("avatarKey").ifBlank { "mono-ink" },
    )
}

internal fun JSONObject.toTrail(base: String): Trail {
    val photo = optJSONObject("photo")?.let {
        TrailPhoto(it.getString("url").absolute(base), it.optInt("width"), it.optInt("height"))
    }
    return Trail(
        id = getString("id"),
        kind = text("kind").ifBlank { if (photo == null) "text" else "photo" },
        caption = optString("caption"),
        photo = photo,
        thumbUrl = text("thumbUrl").takeIf { it.isNotBlank() }?.absolute(base),
        likeCount = optInt("likeCount"),
        commentCount = optInt("commentCount"),
        liked = optBoolean("liked"),
        mine = optBoolean("mine"),
        createdAt = optString("createdAt"),
        author = getJSONObject("author").toTrailAuthor(base),
    )
}

/** The same shape the server sends, so a cached trail reads back through [toTrail]. */
internal fun Trail.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("kind", kind)
    .put("caption", caption)
    .put("photo", photo?.let { JSONObject().put("url", it.url).put("width", it.width).put("height", it.height) } ?: JSONObject.NULL)
    .put("thumbUrl", thumbUrl ?: JSONObject.NULL)
    .put("likeCount", likeCount)
    .put("commentCount", commentCount)
    .put("liked", liked)
    .put("mine", mine)
    .put("createdAt", createdAt)
    .put("author", JSONObject()
        .put("playerId", author.playerId)
        .put("displayName", author.name)
        .put("username", author.handle.removePrefix("@"))
        .put("avatarUrl", author.avatarUrl)
        .put("avatarKey", author.avatarKey))

internal fun JSONObject.toTrailComment(base: String) = TrailComment(
    id = getString("id"),
    body = optString("body"),
    createdAt = optString("createdAt"),
    mine = optBoolean("mine"),
    author = getJSONObject("author").toTrailAuthor(base),
)
