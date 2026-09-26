package com.wyrm.omrajput.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The voice control plane. Audio never passes through this class: it carries
 * room authority and short-lived media credentials to Wyrm's backend. The
 * selected provider's master secret never leaves that control plane.
 */
class VoiceRepository(context: Context, baseUrl: String) {
    private val appContext = context.applicationContext
    private val api = baseUrl.trimEnd('/')
    private val sessionPrefs = appContext.getSharedPreferences("wyrm_session", Context.MODE_PRIVATE)

    suspend fun verificationStatus(): VoiceVerification = io {
        call("/v1/voice/verification/status").let {
            VoiceVerification(it.optBoolean("verified"), it.optString("verifiedAt"))
        }
    }

    suspend fun startVerification(email: String): VoiceChallenge = io {
        call("/v1/voice/verification/start", "POST", JSONObject().put("email", email)).challenge()
    }

    suspend fun resendVerification(challengeId: String, email: String): VoiceChallenge = io {
        call(
            "/v1/voice/verification/resend",
            "POST",
            JSONObject().put("challengeId", challengeId).put("email", email),
        ).challenge()
    }

    suspend fun confirmVerification(challengeId: String, code: String): VoiceVerification = io {
        call(
            "/v1/voice/verification/confirm",
            "POST",
            JSONObject().put("challengeId", challengeId).put("code", code),
        ).let { VoiceVerification(it.optBoolean("verified"), it.optString("verifiedAt")) }
    }

    suspend fun rooms(): List<VoiceRoom> = io {
        call("/v1/voice/rooms").optJSONArray("rooms").voiceRooms()
    }

    suspend fun room(id: String): VoiceRoom = io {
        call("/v1/voice/rooms/$id").getJSONObject("room").voiceRoom()
    }

    suspend fun createRoom(name: String, operationId: String): Pair<VoiceRoom, String> = io {
        val response = call(
            "/v1/voice/rooms", "POST",
            JSONObject().put("name", name).put("operationId", operationId),
        )
        response.getJSONObject("room").voiceRoom() to response.getString("password")
    }

    suspend fun updateRoom(roomId: String, revision: Int, name: String? = null, gate: String? = null): VoiceRoom = io {
        val body = JSONObject().put("roomRevision", revision)
        name?.let { body.put("name", it) }
        gate?.let { body.put("gate", it) }
        call("/v1/voice/rooms/$roomId", "PATCH", body).getJSONObject("room").voiceRoom()
    }

    suspend fun revealPassword(roomId: String): String = io {
        call("/v1/voice/rooms/$roomId/password/reveal", "POST").getString("password")
    }

    suspend fun regeneratePassword(roomId: String, revision: Int): Pair<VoiceRoom, String> = io {
        val response = call(
            "/v1/voice/rooms/$roomId/password/regenerate", "POST",
            JSONObject().put("roomRevision", revision),
        )
        response.getJSONObject("room").voiceRoom() to response.getString("password")
    }

    suspend fun deleteRoom(roomId: String) = io {
        call("/v1/voice/rooms/$roomId", "DELETE")
        Unit
    }

    suspend fun joinRoom(
        roomId: String,
        password: String,
        operationId: String,
        muted: Boolean,
        deafened: Boolean,
    ): VoiceJoinTicket = io {
        call(
            "/v1/voice/rooms/$roomId/join", "POST",
            JSONObject()
                .put("password", password)
                .put("operationId", operationId)
                .put("muted", muted)
                .put("deafened", deafened),
        ).joinTicket()
    }

    suspend fun acceptInvite(
        inviteId: String,
        operationId: String,
        muted: Boolean,
        deafened: Boolean,
    ): VoiceJoinTicket = io {
        call(
            "/v1/voice/invites/$inviteId/accept", "POST",
            JSONObject().put("operationId", operationId).put("muted", muted).put("deafened", deafened),
        ).joinTicket()
    }

    suspend fun leave(roomId: String) = io {
        call("/v1/voice/rooms/$roomId/leave", "POST")
        Unit
    }

    suspend fun presence(roomId: String, muted: Boolean, deafened: Boolean): VoiceParticipant = io {
        call(
            "/v1/voice/rooms/$roomId/presence", "PUT",
            JSONObject().put("muted", muted).put("deafened", deafened),
        ).getJSONObject("participant").voiceParticipant()
    }

    suspend fun participants(roomId: String): List<VoiceParticipant> = io {
        call("/v1/voice/rooms/$roomId/participants").optJSONArray("participants").voiceParticipants()
    }

    suspend fun tracks(roomId: String, participantId: String): List<VoiceTrackRef> = io {
        call("/v1/voice/rooms/$roomId/media/tracks?participantId=$participantId")
            .optJSONArray("tracks").voiceTracks()
    }

    suspend fun publish(
        roomId: String,
        participantId: String,
        sdp: String,
        mid: String,
    ): VoiceMediaAnswer = io {
        val response = call(
            "/v1/voice/rooms/$roomId/media/publish", "POST",
            JSONObject().put("participantId", participantId).put("sdp", sdp).put("mid", mid),
        )
        val description = response.optJSONObject("sessionDescription")
        VoiceMediaAnswer(
            sessionId = response.optString("sessionId"),
            trackName = response.optString("trackName"),
            sdpType = description?.optString("type").orEmpty(),
            sdp = description?.optString("sdp").orEmpty(),
            remoteTracks = response.optJSONArray("remoteTracks").voiceTracks(),
            reused = response.optBoolean("reused"),
        )
    }

    suspend fun subscribe(roomId: String, participantId: String, tracks: List<VoiceTrackRef>): VoiceSubscribeAnswer = io {
        val bodyTracks = JSONArray()
        tracks.forEach { bodyTracks.put(JSONObject().put("sessionId", it.sessionId).put("trackName", it.trackName)) }
        val response = call(
            "/v1/voice/rooms/$roomId/media/subscribe", "POST",
            JSONObject().put("participantId", participantId).put("tracks", bodyTracks),
        )
        val description = response.optJSONObject("sessionDescription")
        VoiceSubscribeAnswer(
            requiresRenegotiation = response.optBoolean("requiresImmediateRenegotiation"),
            sdpType = description?.optString("type").orEmpty(),
            sdp = description?.optString("sdp").orEmpty(),
        )
    }

    suspend fun renegotiate(roomId: String, participantId: String, sdp: String) = io {
        call(
            "/v1/voice/rooms/$roomId/media/renegotiate", "PUT",
            JSONObject().put("participantId", participantId).put("sdp", sdp),
        )
        Unit
    }

    suspend fun invite(roomId: String, recipientId: String) = io {
        call(
            "/v1/voice/rooms/$roomId/invites", "POST",
            JSONObject().put("recipientId", recipientId),
        )
        Unit
    }

    suspend fun kick(roomId: String, playerId: String, ban: Boolean) = io {
        call(
            "/v1/voice/rooms/$roomId/${if (ban) "ban" else "kick"}", "POST",
            JSONObject().put("playerId", playerId),
        )
        Unit
    }

    suspend fun unban(roomId: String, playerId: String) = io {
        call("/v1/voice/rooms/$roomId/ban/$playerId", "DELETE")
        Unit
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun call(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        if (WYRM_BACKEND_DISCONNECTED) throw java.io.IOException("Wyrm backend disconnected")
        val connection = (URL("$api$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("skip_zrok_interstitial", "1")
            sessionPrefs.getString("token", null)?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                throw VoiceApiException(status, error.optString("error", "HTTP_$status"), error.optInt("retryAfterSeconds"))
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.challenge() = VoiceChallenge(
    id = getString("challengeId"), expiresAt = getString("expiresAt"), resendAt = getString("resendAt"),
)

private fun JSONObject.joinTicket() = VoiceJoinTicket(
    room = getJSONObject("room").voiceRoom(),
    participantId = getString("participantId"),
    callId = getString("callId"),
    participants = optJSONArray("participants").voiceParticipants(),
    resumed = optBoolean("resumed"),
    media = optJSONObject("media")?.let {
        VoiceMediaTicket(
            provider = it.optString("provider", "cloudflare"),
            url = it.optString("url"), token = it.optString("token"), expiresAt = it.optString("expiresAt"),
        )
    } ?: VoiceMediaTicket(),
)

private fun JSONObject.voiceRoom(): VoiceRoom {
    val owner = getJSONObject("creator")
    val records = optJSONArray("history")
    val history = buildList {
        for (index in 0 until (records?.length() ?: 0)) {
            val row = records!!.getJSONObject(index)
            add(VoiceCallRecord(row.optString("id"), row.optString("startedAt"), row.optString("endedAt"), row.optInt("peakParticipants")))
        }
    }
    val lifetime = optJSONObject("lifetime")
    val banRows = optJSONArray("bans")
    val bans = buildList {
        for (index in 0 until (banRows?.length() ?: 0)) {
            val row = banRows!!.getJSONObject(index)
            add(VoiceBan(row.getString("playerId"), row.optString("displayName", "Player"), row.optString("username"), row.optString("createdAt")))
        }
    }
    return VoiceRoom(
        id = getString("id"), name = getString("name"),
        creator = VoiceCreator(
            id = owner.getString("id"), displayName = owner.optString("displayName", "Unnamed"),
            username = owner.optString("username"), avatarKey = owner.optString("avatarKey", "mono-ink"),
            avatarUrl = owner.optString("avatarUrl"),
        ),
        gate = optString("gate", "open"), active = optBoolean("active"), activeCount = optInt("activeCount"),
        activeSince = optString("activeSince"), revision = optInt("revision", 1), mine = optBoolean("mine"),
        member = optBoolean("member"), managedPublic = optBoolean("managedPublic"), capacity = optInt("capacity", 10),
        suspended = optBoolean("suspended"), createdAt = optString("createdAt"), history = history,
        lifetimeCalls = lifetime?.optInt("calls") ?: 0,
        lifetimeDurationSeconds = lifetime?.optLong("durationSeconds") ?: 0,
        bans = bans,
    )
}

private fun JSONObject.voiceParticipant() = VoiceParticipant(
    id = getString("id"), playerId = getString("playerId"), displayName = optString("displayName", "Unnamed"),
    username = optString("username"), avatarKey = optString("avatarKey", "mono-ink"), avatarUrl = optString("avatarUrl"),
    muted = optBoolean("muted", true), deafened = optBoolean("deafened"), joinedAt = optString("joinedAt"),
    trackName = optString("trackName"),
)

private fun JSONArray?.voiceRooms() = buildList {
    for (index in 0 until (this@voiceRooms?.length() ?: 0)) add(this@voiceRooms!!.getJSONObject(index).voiceRoom())
}

private fun JSONArray?.voiceParticipants() = buildList {
    for (index in 0 until (this@voiceParticipants?.length() ?: 0)) add(this@voiceParticipants!!.getJSONObject(index).voiceParticipant())
}

private fun JSONArray?.voiceTracks() = buildList {
    for (index in 0 until (this@voiceTracks?.length() ?: 0)) {
        val row = this@voiceTracks!!.getJSONObject(index)
        add(VoiceTrackRef(row.optString("participantId"), row.getString("sessionId"), row.getString("trackName")))
    }
}
