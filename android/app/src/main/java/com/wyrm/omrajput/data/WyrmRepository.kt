package com.wyrm.omrajput.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Another Wyrm player's built skin, for one snake in one arena. */
data class ArenaSkin(
    val snakeId: Int,
    val nickname: String,
    val colours: IntArray,
) {
    override fun equals(other: Any?): Boolean =
        other is ArenaSkin && snakeId == other.snakeId && nickname == other.nickname &&
            colours.contentEquals(other.colours)

    override fun hashCode(): Int =
        (snakeId * 31 + nickname.hashCode()) * 31 + colours.contentHashCode()
}

data class RenameAllowance(
    val displayName: Int = 2,
    val username: Int = 2,
)

data class ApiPlayer(
    val id: String,
    val ingameName: String? = null,
    val username: String? = null,
    val displayName: String,
    val avatarKey: String = "mono-ink",
    /** Absolute, and empty when the player has no photograph. */
    val avatarUrl: String = "",
    val bio: String = "",
    val highestScore: Long = 0,
    val kills: Long = 0,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val createdAt: String = "",
    /** Set on a closed account. The row stays; the screens say so. */
    val deletedAt: String = "",
    /** Where the viewer stands with this player, as the server sees it. */
    val isFollowing: Boolean = false,
    val followsYou: Boolean = false,
    /** Direct messages need both people to follow each other. */
    val canMessage: Boolean = false,
) {
    val isDeleted: Boolean get() = deletedAt.isNotEmpty()
    val handle: String get() = if (username.isNullOrBlank()) "" else "@$username"
}

/**
 * One line of chat, global or direct.
 *
 * Direct messages carry no author block — a thread has exactly two people in
 * it and the screen already knows both — so [authorId] alone decides which
 * side of the thread a line belongs on.
 */
data class ChatMessage(
    val id: String,
    val body: String,
    val createdAt: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorAvatarUrl: String,
    val authorAvatarKey: String,
    val authorDeleted: Boolean = false,
)

data class Conversation(
    val player: ApiPlayer,
    val lastMessage: String,
    val lastAt: String,
    val unread: Long,
)

/** One entry in the alerts panel — a broadcast, exactly as the operator sent it. */
data class ServerNotification(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val meta: Map<String, String>,
    val createdAt: String,
    val read: Boolean,
)

private class ApiException(val status: Int, message: String) : Exception(message)

/* Diagnostic kill switch. While true every backend call fails like a network
   outage (IOException, never 401), so the saved session stays signed in and
   runs stay queued. Normally false. */
internal const val WYRM_BACKEND_DISCONNECTED = false

class WyrmRepository(context: Context, baseUrl: String) {
    private val appContext = context.applicationContext
    private val api = baseUrl.trimEnd('/')
    private val prefs = appContext.getSharedPreferences("wyrm_session", Context.MODE_PRIVATE)

    val hasSession: Boolean get() = session != null

    private var session: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("token") else putString("token", value)
            }.apply()
        }

    suspend fun googleSignIn(idToken: String): ApiPlayer = withContext(Dispatchers.IO) {
        val response = call(
            path = "/v1/auth/google",
            method = "POST",
            body = JSONObject().put("idToken", idToken),
            authenticated = false,
        )
        session = response.getString("token")
        response.getJSONObject("player").toPlayer(api)
    }

    /**
     * An account with no Google behind it.
     *
     * The username is the identity, so it is what signs you back in later.
     * There is no email and therefore no way to send a reset — the screen that
     * calls this says so before the password is chosen, not afterwards.
     */
    suspend fun guestSignUp(
        displayName: String,
        username: String,
        password: String,
    ): ApiPlayer = withContext(Dispatchers.IO) {
        val response = call(
            path = "/v1/auth/guest",
            method = "POST",
            body = JSONObject()
                .put("displayName", displayName)
                .put("username", username)
                .put("password", password),
            authenticated = false,
        )
        session = response.getString("token")
        response.getJSONObject("player").toPlayer(api)
    }

    /** Whether a username is still free, asked before the password is chosen. */
    suspend fun usernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
        call(
            path = "/v1/auth/username-availability?username=" + java.net.URLEncoder.encode(username, "UTF-8"),
            authenticated = false,
        ).getBoolean("available")
    }

    suspend fun logIn(username: String, password: String): ApiPlayer = withContext(Dispatchers.IO) {
        val response = call(
            path = "/v1/auth/login",
            method = "POST",
            body = JSONObject().put("username", username).put("password", password),
            authenticated = false,
        )
        session = response.getString("token")
        response.getJSONObject("player").toPlayer(api)
    }

    suspend fun me(): ApiPlayer = withContext(Dispatchers.IO) {
        call("/v1/me").toPlayer(api)
    }

    /** How many display-name and username changes remain in the 30-day window. */
    suspend fun renameAllowance(): RenameAllowance = withContext(Dispatchers.IO) {
        val json = call("/v1/me/renames")
        RenameAllowance(
            displayName = json.optInt("displayName", 2),
            username = json.optInt("username", 2),
        )
    }

    /**
     * Writes whatever the caller passes and leaves the rest alone.
     *
     * Every field is optional on the server too, so an edit screen can send only
     * what changed. The server owns the rules — a name it rejects comes back as
     * an [ApiError] carrying its own code, which the screens show verbatim
     * rather than second-guessing with a local copy of the same regex.
     */
    suspend fun updateProfile(
        displayName: String? = null,
        ingameName: String? = null,
        username: String? = null,
        bio: String? = null,
        avatarKey: String? = null,
        phoneNumber: String? = null,
    ): ApiPlayer = withContext(Dispatchers.IO) {
        val body = JSONObject()
        displayName?.let { body.put("displayName", it) }
        ingameName?.let { body.put("ingameName", it) }
        username?.let { body.put("username", it) }
        bio?.let { body.put("bio", it) }
        avatarKey?.let { body.put("avatarKey", it) }
        phoneNumber?.let { body.put("phoneNumber", it) }
        call("/v1/me", method = "PATCH", body = body).toPlayer(api)
    }

    /**
     * Reports one finished run.
     *
     * [eventId] makes it idempotent, so a retry after a dropped connection
     * cannot count the same match twice. The server keeps the best score and
     * adds the kills, exactly as it does for its own game-server route.
     */
    /**
     * Tell other Wyrm players what this snake looks like, for this arena only.
     *
     * The arena carries colour-group indices and has no field for an exact
     * colour, so a skin built in the picker can only reach anyone through here.
     */
    suspend fun publishArenaSkin(
        arena: String,
        snakeId: Int,
        nickname: String,
        code: String,
        colours: IntArray,
        generation: String,
    ): Unit = withContext(Dispatchers.IO) {
        call(
            path = "/v1/arena/skin",
            method = "POST",
            body = JSONObject()
                .put("arena", arena)
                .put("snakeId", snakeId)
                .put("nickname", nickname)
                .put("code", code)
                .put("colours", colours.joinToString("") { "%08X".format(it) })
                .put("generation", generation),
        )
        Unit
    }

    /** On the way Home, or on leaving the arena. */
    suspend fun clearArenaSkin(generation: String): Unit = withContext(Dispatchers.IO) {
        call(
            path = "/v1/arena/skin",
            method = "DELETE",
            body = JSONObject().put("generation", generation),
        )
        Unit
    }

    /** One batched question for every snake the viewport has just met. */
    suspend fun arenaSkins(arena: String, snakeIds: List<Int>): List<ArenaSkin> =
        withContext(Dispatchers.IO) {
            val response = call(
                path = "/v1/arena/skins",
                method = "POST",
                body = JSONObject()
                    .put("arena", arena)
                    .put("snakeIds", JSONArray(snakeIds)),
            )
            val rows = response.optJSONArray("skins")
            buildList {
                for (index in 0 until (rows?.length() ?: 0)) {
                    val row = rows!!.getJSONObject(index)
                    val hex = row.optString("colours")
                    add(
                        ArenaSkin(
                            snakeId = row.optInt("snakeId", -1),
                            nickname = row.optString("nickname"),
                            colours = IntArray(hex.length / 8) {
                                hex.substring(it * 8, it * 8 + 8).toLong(16).toInt()
                            },
                        )
                    )
                }
            }
        }

    suspend fun reportRun(eventId: String, score: Int, kills: Int): List<ServerNotification> = withContext(Dispatchers.IO) {
        val response = call(
            path = "/v1/me/stats",
            method = "POST",
            body = JSONObject()
                .put("eventId", eventId)
                .put("score", score)
                .put("kills", kills),
        )
        val earned = response.optJSONArray("achievements")
        buildList {
            for (index in 0 until (earned?.length() ?: 0)) {
                add(earned!!.getJSONObject(index).toServerNotification())
            }
        }
    }

    /** Idempotent recovery from absolute device totals; it never lowers server data. */
    suspend fun reconcileStats(highestScore: Long, kills: Long): ApiPlayer = withContext(Dispatchers.IO) {
        call(
            path = "/v1/me/stats/reconcile",
            method = "POST",
            body = JSONObject()
                .put("highestScore", highestScore.coerceAtLeast(0L))
                .put("kills", kills.coerceAtLeast(0L)),
        ).getJSONObject("player").toPlayer(api)
    }

    /** [sort] is "score" or "kills"; the server ranks, this only reads. */
    suspend fun leaderboard(sort: String): List<ApiPlayer> = withContext(Dispatchers.IO) {
        val players = call("/v1/leaderboard?sort=$sort").optJSONArray("players")
        buildList {
            for (index in 0 until (players?.length() ?: 0)) {
                val player = players!!.getJSONObject(index).toPlayer(api)
                // During a rolling backend update an older node can still
                // return tombstones. Never flash those rows in the app.
                if (!player.isDeleted) add(player)
            }
        }
    }

    /**
     * Replaces the player's photograph.
     *
     * The image goes up as itself rather than as base64 inside JSON — a third
     * fewer bytes over a phone connection, and the server stores exactly what
     * it receives.
     */
    suspend fun uploadAvatar(bytes: ByteArray): ApiPlayer = withContext(Dispatchers.IO) {
        callBinary("/v1/me/avatar", "PUT", "image/jpeg", bytes).toPlayer(api)
    }

    /** Back to a drawn avatar. */
    suspend fun removeAvatar(): ApiPlayer = withContext(Dispatchers.IO) {
        call("/v1/me/avatar", method = "DELETE").toPlayer(api)
    }

    /**
     * Closes the account.
     *
     * A soft delete on the server: the row survives so that anything the player
     * touched — leaderboard history, chat, follows — still resolves to a name
     * instead of a dangling id, and shows as deleted. Signing in with the same
     * Google account afterwards starts a fresh account rather than resurrecting
     * this one.
     */
    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        call("/v1/me", method = "DELETE")
        Unit
    }

    /* -------------------------------------------------------------- social */

    /** Another player, as everyone else sees them, plus where you stand. */
    suspend fun player(id: String): ApiPlayer = withContext(Dispatchers.IO) {
        call("/v1/players/$id").toPlayer(api)
    }

    suspend fun setFollow(id: String, following: Boolean): ApiPlayer = withContext(Dispatchers.IO) {
        call("/v1/players/$id/follow", method = if (following) "PUT" else "DELETE").toPlayer(api)
    }

    /** [kind] is "followers" or "following". */
    suspend fun connections(id: String, kind: String): List<ApiPlayer> = withContext(Dispatchers.IO) {
        call("/v1/players/$id/connections?kind=$kind").players(api)
    }

    suspend fun searchPlayers(query: String): List<ApiPlayer> = withContext(Dispatchers.IO) {
        call("/v1/players?q=${java.net.URLEncoder.encode(query, "UTF-8")}").players(api)
    }

    /* ---------------------------------------------------------------- chat */

    suspend fun globalMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        call("/v1/chat/messages").messages(api)
    }

    suspend fun sendGlobal(body: String): Unit = withContext(Dispatchers.IO) {
        call("/v1/chat/messages", method = "POST", body = JSONObject().put("body", body))
        Unit
    }

    suspend fun reportMessage(id: String, reason: String): Unit = withContext(Dispatchers.IO) {
        call(
            "/v1/chat/messages/$id/report",
            method = "POST",
            body = JSONObject().put("reason", reason),
        )
        Unit
    }

    /** Everyone you have exchanged direct messages with, most recent first. */
    suspend fun conversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val array = call("/v1/direct").optJSONArray("conversations")
        buildList {
            for (index in 0 until (array?.length() ?: 0)) {
                val row = array!!.getJSONObject(index)
                add(
                    Conversation(
                        player = row.getJSONObject("player").toPlayer(api),
                        lastMessage = row.optString("lastMessage", ""),
                        lastAt = row.optString("lastAt", ""),
                        unread = row.optLong("unreadCount", 0),
                    )
                )
            }
        }
    }

    suspend fun directMessages(playerId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val response = call("/v1/direct/$playerId/messages")
        val array = response.optJSONArray("messages")
        buildList {
            for (index in 0 until (array?.length() ?: 0)) {
                val row = array!!.getJSONObject(index)
                add(
                    ChatMessage(
                        id = row.optString("id"),
                        body = row.optString("body"),
                        createdAt = row.optString("createdAt"),
                        authorId = row.optString("senderId"),
                        authorName = "",
                        authorUsername = "",
                        authorAvatarUrl = "",
                        authorAvatarKey = "mono-ink",
                    )
                )
            }
        }
    }

    suspend fun sendDirect(playerId: String, body: String): Unit = withContext(Dispatchers.IO) {
        call(
            "/v1/direct/$playerId/messages",
            method = "POST",
            body = JSONObject().put("body", body),
        )
        Unit
    }

    /**
     * Hands the phone's push token to the backend.
     *
     * Called once after sign-in and again whenever Firebase issues a new one —
     * a token can change on its own, and a stale one just means a push nobody
     * reads rather than an error, so this is fire-and-forget by design.
     */
    suspend fun registerDeviceToken(token: String): Unit = withContext(Dispatchers.IO) {
        call(
            "/v1/me/device-token",
            method = "POST",
            body = JSONObject().put("token", token),
        )
        Unit
    }

    /** The alerts panel's feed: every broadcast, marked against this player's own read point. */
    suspend fun notifications(): List<ServerNotification> = withContext(Dispatchers.IO) {
        val array = call("/v1/notifications").optJSONArray("notifications")
        buildList {
            for (index in 0 until (array?.length() ?: 0)) {
                add(array!!.getJSONObject(index).toServerNotification())
            }
        }
    }

    suspend fun markNotificationsRead(): Unit = withContext(Dispatchers.IO) {
        call("/v1/notifications/read", method = "POST")
        Unit
    }

    suspend fun setNotificationRead(id: String, read: Boolean): Unit = withContext(Dispatchers.IO) {
        call(
            "/v1/notifications/$id/read",
            method = "PUT",
            body = JSONObject().put("read", read),
        )
        Unit
    }

    suspend fun deleteNotification(id: String): Unit = withContext(Dispatchers.IO) {
        call("/v1/notifications/$id", method = "DELETE")
        Unit
    }

    /** The server's own error code, so screens report the real reason. */
    fun errorCode(error: Throwable): String =
        (error as? ApiException)?.message.orEmpty()

    fun isUnauthorized(error: Throwable): Boolean =
        error is ApiException && error.status == HttpURLConnection.HTTP_UNAUTHORIZED

    suspend fun signOut() {
        session = null
        runCatching {
            CredentialManager.create(appContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }

    fun clearSession() {
        session = null
    }

    fun close() = Unit

    private fun JSONObject.toServerNotification(): ServerNotification {
        val metaObject = optJSONObject("meta")
        val meta = buildMap {
            metaObject?.keys()?.forEach { key -> put(key, metaObject.optString(key)) }
        }
        return ServerNotification(
            id = optString("id"),
            kind = optString("kind", "broadcast"),
            title = optString("title"),
            body = optString("body"),
            meta = meta,
            createdAt = optString("createdAt"),
            read = optBoolean("read", false),
        )
    }

    private fun call(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        authenticated: Boolean = true,
    ): JSONObject {
        if (WYRM_BACKEND_DISCONNECTED) throw java.io.IOException("Wyrm backend disconnected")
        val connection = (URL("$api$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("skip_zrok_interstitial", "1")
            if (authenticated) session?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP_$status"
                throw ApiException(status, message)
            }
            // A 204, or any success with nothing to say, is still a success.
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    /** The same call, with bytes for a body instead of a document. */
    private fun callBinary(
        path: String,
        method: String,
        contentType: String,
        body: ByteArray,
    ): JSONObject {
        if (WYRM_BACKEND_DISCONNECTED) throw java.io.IOException("Wyrm backend disconnected")
        val connection = (URL("$api$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("skip_zrok_interstitial", "1")
            setRequestProperty("Content-Type", contentType)
            setFixedLengthStreamingMode(body.size)
            session?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP_$status"
                throw ApiException(status, message)
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * [base] resolves the avatar address.
 *
 * The server returns its own avatars as a path rather than a full address, so
 * that the same database works behind any hostname; a Google picture arrives
 * absolute and is left exactly as it is.
 */
private fun JSONObject.toPlayer(base: String) = ApiPlayer(
    id = getString("id"),
    ingameName = optString("ingameName").takeIf { it.isNotBlank() && it != "null" },
    username = optString("username").takeIf { it.isNotBlank() && it != "null" },
    displayName = optString("displayName", "Unnamed"),
    avatarKey = optString("avatarKey", "mono-ink"),
    avatarUrl = optString("avatarUrl").orEmptyish().let {
        if (it.startsWith("/")) "$base$it" else it
    },
    bio = optString("bio", ""),
    highestScore = optLong("highestScore", 0),
    kills = optLong("kills", 0),
    followerCount = optLong("followerCount", 0),
    followingCount = optLong("followingCount", 0),
    createdAt = optString("createdAt", ""),
    deletedAt = optString("deletedAt").orEmptyish(),
    isFollowing = optBoolean("isFollowing", false),
    followsYou = optBoolean("followsYou", false),
    canMessage = optBoolean("canMessage", false),
)

private fun JSONObject.players(base: String): List<ApiPlayer> {
    val array = optJSONArray("players")
    return buildList {
        for (index in 0 until (array?.length() ?: 0)) {
            add(array!!.getJSONObject(index).toPlayer(base))
        }
    }
}

private fun JSONObject.messages(base: String): List<ChatMessage> {
    val array = optJSONArray("messages")
    return buildList {
        for (index in 0 until (array?.length() ?: 0)) {
            val row = array!!.getJSONObject(index)
            val avatar = row.optString("avatarUrl").orEmptyish()
            add(
                ChatMessage(
                    id = row.optString("id"),
                    body = row.optString("body"),
                    createdAt = row.optString("createdAt"),
                    authorId = row.optString("playerId"),
                    authorName = row.optString("displayName", "Unnamed"),
                    authorUsername = row.optString("username").orEmptyish(),
                    authorAvatarUrl = if (avatar.startsWith("/")) "$base$avatar" else avatar,
                    authorAvatarKey = row.optString("avatarKey", "mono-ink"),
                    authorDeleted = row.optString("deletedAt").orEmptyish().isNotEmpty(),
                )
            )
        }
    }
}

/** org.json turns a JSON null into the four characters "null". */
private fun String?.orEmptyish(): String =
    if (this.isNullOrBlank() || this == "null") "" else this

suspend fun googleIdToken(activity: Activity, webClientId: String): String {
    check(webClientId.isNotBlank() && !webClientId.startsWith("REPLACE_")) {
        "Google login is not configured."
    }
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
    val result = CredentialManager.create(activity).getCredential(activity, request)
    val credential = result.credential
    check(
        credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) { "Google account unavailable." }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
