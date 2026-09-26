package com.wyrm.omrajput.voice

import com.wyrm.omrajput.data.VoiceParticipant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VoiceConnectionStage(val label: String) {
    IDLE("Voice is ready"),
    GETTING_SESSION("Getting session ID"),
    CONNECTING_EDGE("Connecting to voice edge"),
    PREPARING_AUDIO("Preparing encrypted audio"),
    SUBSCRIBING("Subscribing to room audio"),
    ENTERING("Entering room"),
    CONNECTED("Voice connected"),
    LEAVING("Leaving room"),
    FAILED("Voice connection failed"),
}

data class VoiceCallState(
    val active: Boolean = false,
    val roomId: String = "",
    val roomName: String = "",
    /** The room call's start, not this device's join time. */
    val callStartedAt: String = "",
    val participantId: String = "",
    val playerId: String = "",
    val stage: VoiceConnectionStage = VoiceConnectionStage.IDLE,
    val muted: Boolean = true,
    val interrupted: Boolean = false,
    val deafened: Boolean = false,
    val volume: Float = 1f,
    val audioRoute: String = "system",
    val participants: List<VoiceParticipant> = emptyList(),
    val speakingPlayerIds: Set<String> = emptySet(),
    val error: String = "",
)

object VoiceCallController {
    private val mutable = MutableStateFlow(VoiceCallState())
    val state: StateFlow<VoiceCallState> = mutable.asStateFlow()

    internal fun publish(next: VoiceCallState) { mutable.value = next }
    // LiveKit events, the connection coroutine and notification actions all
    // arrive on different threads. A read-then-write here could resurrect an
    // old connection stage and leave Compose on "Subscribing" until another
    // participant event happened to move it again.
    internal fun update(change: (VoiceCallState) -> VoiceCallState) { mutable.update(change) }
}
