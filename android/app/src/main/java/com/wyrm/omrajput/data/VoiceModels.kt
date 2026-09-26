package com.wyrm.omrajput.data

data class VoiceVerification(
    val verified: Boolean = false,
    val verifiedAt: String = "",
)

data class VoiceChallenge(
    val id: String,
    val expiresAt: String,
    val resendAt: String,
)

data class VoiceCreator(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarKey: String,
    val avatarUrl: String,
)

data class VoiceCallRecord(
    val id: String,
    val startedAt: String,
    val endedAt: String,
    val peakParticipants: Int,
)

data class VoiceBan(
    val playerId: String,
    val displayName: String,
    val username: String,
    val createdAt: String,
)

data class VoiceRoom(
    val id: String,
    val name: String,
    val creator: VoiceCreator,
    val gate: String,
    val active: Boolean,
    val activeCount: Int,
    val activeSince: String,
    val revision: Int,
    val mine: Boolean,
    val member: Boolean = false,
    val managedPublic: Boolean = false,
    val capacity: Int = 10,
    val suspended: Boolean,
    val createdAt: String,
    val history: List<VoiceCallRecord> = emptyList(),
    val lifetimeCalls: Int = 0,
    val lifetimeDurationSeconds: Long = 0,
    val bans: List<VoiceBan> = emptyList(),
)

data class VoiceParticipant(
    val id: String,
    val playerId: String,
    val displayName: String,
    val username: String,
    val avatarKey: String,
    val avatarUrl: String,
    val muted: Boolean,
    val deafened: Boolean,
    val joinedAt: String,
    val trackName: String = "",
    val speaking: Boolean = false,
)

data class VoiceJoinTicket(
    val room: VoiceRoom,
    val participantId: String,
    val callId: String,
    val participants: List<VoiceParticipant>,
    val resumed: Boolean,
    val media: VoiceMediaTicket = VoiceMediaTicket(),
)

data class VoiceMediaTicket(
    val provider: String = "cloudflare",
    val url: String = "",
    val token: String = "",
    val expiresAt: String = "",
)

data class VoiceTrackRef(
    val participantId: String,
    val sessionId: String,
    val trackName: String,
)

data class VoiceMediaAnswer(
    val sessionId: String,
    val trackName: String,
    val sdpType: String,
    val sdp: String,
    val remoteTracks: List<VoiceTrackRef>,
    val reused: Boolean,
)

data class VoiceSubscribeAnswer(
    val requiresRenegotiation: Boolean,
    val sdpType: String,
    val sdp: String,
)

class VoiceApiException(
    val status: Int,
    val code: String,
    val retryAfterSeconds: Int = 0,
) : Exception(code)
