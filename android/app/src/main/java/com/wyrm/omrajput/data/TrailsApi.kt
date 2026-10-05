package com.wyrm.omrajput.data

import org.json.JSONObject

/*
 * Trails (OM, 2026-09-28): photos or words from the Wyrm community, with bead
 * likes and replies. The routes are in `backend/src/trails.mjs`; the calls
 * live in `WyrmRepository` beside every other authenticated call. Wyrm iOS:
 * `WyrmTrails.swift`.
 */

/**
 * Trails are switched off while they are finished (OM, 2026-10-02; earlier
 * paused 2026-09-29). `false` hides the feed, trail, studio, Share run / Share
 * this skin, profile grid and Trails stat, trail badges, trail alerts and
 * pushes, and the Trails group in Settings › Notifications. The Social card
 * still shows, looking as it did, and opens the "in development" page
 * (`TrailsComingSoonScreen`). `true` brings everything back.
 * Java reads it as `com.wyrm.omrajput.data.TrailsApiKt.TRAILS_ENABLED`.
 */
// ON for test builds only (OM, 2026-10-04). Set back to false before any
// beta or stable release: releases ship the coming-soon placeholder until
// Trails is final. iOS: WyrmTrailsFeature.enabled.
const val TRAILS_ENABLED = true

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

/** A video trail's clip (OM, 2026-10-05): H.264 MP4, at most 30 s and 720p; the trail's photo is its poster. */
data class TrailVideo(val url: String, val width: Int, val height: Int, val durationMs: Long)

/** The Wyrm look in a shared skin: -1 is "none"; the hair colour is the slider's 0..1 position. */
data class TrailSkinLook(val hair: Int = -1, val hairTone: Float = 0.22f, val ears: Int = -1, val glasses: Int = -1)

/**
 * A poster's skin for "Try this skin" (OM, 2026-09-30). The same JSON from
 * both apps, stored by the backend as text and returned only when the poster
 * chose to share it:
 * `{"v":1,"custom","preset","code","colours":["AARRGGBB"…],"accessory","look":{"hair","hairTone","ears","glasses"},"tag"}`.
 * `tag` (OM, 2026-10-04) is the NTL tag worn, in NTL's numbering, and is
 * written only when one is worn; older skins have none.
 * [colours] pairs with [code] position by position; 0 ("00000000") is the
 * palette colour, and the alpha byte keeps the AIR / Wyrm bead tags.
 */
data class TrailSkin(
    val custom: Boolean,
    val preset: Int,
    val code: String,
    val colours: List<Int>,
    val accessory: Int,
    val look: TrailSkinLook,
    /** The NTL tag worn (NTL's numbering), -1 for none. */
    val tag: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("custom", custom)
        .put("preset", preset.coerceIn(0, 255))
        .put("code", code)
        .put("colours", org.json.JSONArray().apply { colours.take(256).forEach { put("%08X".format(it)) } })
        .put("accessory", accessory.coerceIn(-1, 255))
        .put("look", JSONObject()
            .put("hair", look.hair.coerceIn(-1, 255))
            .put("hairTone", Math.round(look.hairTone.coerceIn(0f, 1f) * 10000.0) / 10000.0)
            .put("ears", look.ears.coerceIn(-1, 255))
            .put("glasses", look.glasses.coerceIn(-1, 255)))
        .apply { if (tag in 0..65535) put("tag", tag) }

    companion object {
        private val HEX8 = Regex("^[0-9A-Fa-f]{8}$")

        /** Another player's JSON, checked and clamped; anything unreadable is no skin at all. */
        fun from(json: JSONObject?): TrailSkin? {
            if (json == null || json.optInt("v", 0) != 1) return null
            fun slot(value: Int) = if (value in 0..255) value else -1
            val code = json.optString("code").filter { it.code in 0x20..0x7E }.take(256)
            val rows = json.optJSONArray("colours")
            val colours = (0 until minOf(rows?.length() ?: 0, 256)).map { index ->
                rows!!.optString(index).takeIf { HEX8.matches(it) }?.toLong(16)?.toInt() ?: 0
            }
            val look = json.optJSONObject("look")
            return TrailSkin(
                custom = json.optBoolean("custom"),
                preset = json.optInt("preset", 0).coerceIn(0, 255),
                code = code,
                colours = colours,
                accessory = slot(json.optInt("accessory", -1)),
                look = TrailSkinLook(
                    hair = slot(look?.optInt("hair", -1) ?: -1),
                    hairTone = (look?.optDouble("hairTone", 0.22) ?: 0.22).toFloat().takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: 0.22f,
                    ears = slot(look?.optInt("ears", -1) ?: -1),
                    glasses = slot(look?.optInt("glasses", -1) ?: -1),
                ),
                tag = json.optInt("tag", -1).takeIf { it in 0..65535 } ?: -1,
            )
        }
    }
}

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
    /** The poster's look, only when they shared it ("Try this skin"). */
    val skin: TrailSkin? = null,
    /** A video trail's clip; [photo] is then its poster frame. */
    val video: TrailVideo? = null,
) {
    /**
     * Width over height, held between a tall 4:5 and a wide 1.91:1. A video
     * may stand a little taller, 9:16 shown as 4:5 like a reel in a feed.
     */
    val aspect: Float
        get() = (video?.let { TrailPhoto(it.url, it.width, it.height) } ?: photo)
            ?.takeIf { it.width > 0 && it.height > 0 }
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
        skin = TrailSkin.from(optJSONObject("skin")),
        video = optJSONObject("video")?.let {
            val url = it.optString("url")
            if (url.isBlank() || url == "null") null
            else TrailVideo(url.absolute(base), it.optInt("width"), it.optInt("height"), it.optLong("durationMs"))
        },
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
    .put("skin", skin?.toJson() ?: JSONObject.NULL)
    .put("video", video?.let {
        JSONObject().put("url", it.url).put("width", it.width).put("height", it.height).put("durationMs", it.durationMs)
    } ?: JSONObject.NULL)

internal fun JSONObject.toTrailComment(base: String) = TrailComment(
    id = getString("id"),
    body = optString("body"),
    createdAt = optString("createdAt"),
    mine = optBoolean("mine"),
    author = getJSONObject("author").toTrailAuthor(base),
)
