package com.wyrm.omrajput.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wyrm.omrajput.BuildConfig
import com.wyrm.omrajput.R
import com.wyrm.omrajput.WyrmActivity
import com.wyrm.omrajput.data.VoiceJoinTicket
import com.wyrm.omrajput.data.VoiceApiException
import com.wyrm.omrajput.data.VoiceParticipant
import com.wyrm.omrajput.data.VoicePreferenceState
import com.wyrm.omrajput.data.VoicePreferences
import com.wyrm.omrajput.data.VoiceRepository
import com.wyrm.omrajput.data.VoiceTrackRef
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the microphone and PeerConnection independently of Compose and Vulkan.
 * UI actions are intents; state comes back through [VoiceCallController].
 */
class VoiceCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: VoiceRepository
    private lateinit var preferences: VoicePreferences
    private lateinit var audioManager: AudioManager
    private lateinit var voiceSocket: VoiceSocketClient
    private var peerFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var liveKitRoom: Room? = null
    private var liveKitEvents: Job? = null
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private val remoteAudio = ConcurrentHashMap<String, AudioTrack>()
    private val subscribed = linkedSetOf<String>()
    private var heartbeat: Job? = null
    private var recovery: Job? = null
    private var speakingPublish: Job? = null
    private var pendingSpeakers: Set<String> = emptySet()
    private var ticket: VoiceJoinTicket? = null
    private var connectionGeneration = 0L
    private var iceGathered = CompletableDeferred<Unit>()
    private var peerConnected = CompletableDeferred<Unit>()
    private var disposingPeer = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusHeld = false
    private var safetyMuted = false
    private var muteGeneration = 0L
    private var hasConnectedOnce = false
    private val reconcileMutex = Mutex()

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                safetyMuted = false
                localAudio?.setEnabled(!VoiceCallController.state.value.muted)
                liveKitRoom?.setMicrophoneMute(VoiceCallController.state.value.muted)
                VoiceCallController.update { it.copy(interrupted = false) }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                safetyMuted = true
                localAudio?.setEnabled(false)
                liveKitRoom?.setMicrophoneMute(true)
                VoiceCallController.update { it.copy(interrupted = true) }
            }
        }
        updateNotification()
    }

    override fun onCreate() {
        super.onCreate()
        repository = VoiceRepository(this, BuildConfig.WYRM_API_URL)
        preferences = VoicePreferences(this)
        audioManager = getSystemService(AudioManager::class.java)
        voiceSocket = VoiceSocketClient(this, BuildConfig.WYRM_API_URL, scope) {
            // Socket events announce a change; they must never publish another
            // heartbeat or the room becomes a self-sustaining request loop.
            scope.launch { runCatching { reconcile(publishPresence = false) } }
        }
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNow()
        when (intent?.action) {
            ACTION_MUTE -> setMuted(
                if (intent.hasExtra(EXTRA_STATE)) intent.getBooleanExtra(EXTRA_STATE, true)
                else !VoiceCallController.state.value.muted,
                remember = true,
            )
            ACTION_DEAFEN -> setDeafened(
                if (intent.hasExtra(EXTRA_STATE)) intent.getBooleanExtra(EXTRA_STATE, true)
                else !VoiceCallController.state.value.deafened,
                remember = true,
            )
            ACTION_LEAVE -> leave()
            ACTION_ROUTE -> setAudioRoute(intent.getStringExtra(EXTRA_ROUTE) ?: "system", remember = true)
            ACTION_VOLUME -> setVolume(intent.getFloatExtra(EXTRA_VOLUME, 1f), remember = true)
            ACTION_CONNECT -> {
                val participantId = intent.getStringExtra(EXTRA_PARTICIPANT).orEmpty()
                val pending = pendingTickets.remove(participantId)
                if (pending == null) fail("VOICE_SESSION_EXPIRED") else connect(pending)
            }
        }
        return START_NOT_STICKY
    }

    private fun connect(join: VoiceJoinTicket) {
        if (ticket?.participantId == join.participantId && (peerConnection != null || liveKitRoom != null) &&
            VoiceCallController.state.value.stage == VoiceConnectionStage.CONNECTED) return
        heartbeat?.cancel()
        recovery?.cancel()
        disposePeer()
        ticket = join
        hasConnectedOnce = false
        val generation = ++connectionGeneration
        val playerId = applicationContext.getSharedPreferences("wyrm_voice_runtime", MODE_PRIVATE)
            .getString("playerId", "").orEmpty()
        val saved = preferences.read(playerId)
        VoiceCallController.publish(
            VoiceCallState(
                active = true, roomId = join.room.id, roomName = join.room.name,
                callStartedAt = join.room.activeSince,
                participantId = join.participantId, playerId = playerId,
                stage = VoiceConnectionStage.GETTING_SESSION,
                muted = saved.muted, deafened = saved.deafened, volume = saved.volume,
                // Let LiveKit's AudioSwitch prefer a connected Bluetooth device.
                // Speaker/earpiece only become explicit after the player taps
                // the call control; an old preference must not steal a headset.
                audioRoute = "system", participants = join.participants,
            )
        )
        scope.launch {
            try {
                establishMedia(join, saved, generation)
                requireCurrent(generation)
                startHeartbeat()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (generation == connectionGeneration && ticket != null) {
                    fail(failure.message ?: failure.javaClass.simpleName)
                }
            }
        }
    }

    private suspend fun establishMedia(join: VoiceJoinTicket, saved: VoicePreferenceState, generation: Long) {
        if (join.media.provider == "livekit") {
            establishLiveKit(join, saved, generation)
            return
        }
        initializeWebRtc(saved)
        requireCurrent(generation)
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.CONNECTING_EDGE, error = "") }
        val offer = peerConnection!!.createOfferAwait()
        iceGathered = CompletableDeferred()
        peerConnection!!.setLocalAwait(offer)
        withTimeoutOrNull(1_800) { iceGathered.await() }
        requireCurrent(generation)
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.PREPARING_AUDIO) }
        val mid = peerConnection!!.transceivers.firstOrNull()?.mid ?: "0"
        val localSdp = peerConnection!!.localDescription?.description ?: offer.description
        val published = repository.publish(join.room.id, join.participantId, localSdp, mid)
        requireCurrent(generation)
        if (published.sdp.isNotBlank()) {
            peerConnection!!.setRemoteAwait(
                SessionDescription(SessionDescription.Type.fromCanonicalForm(published.sdpType), published.sdp)
            )
        }
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.SUBSCRIBING) }
        subscribeNew(published.remoteTracks)
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.ENTERING) }
        reconcile()
        withTimeoutOrNull(8_000) { peerConnected.await() }
            ?: error("VOICE_CONNECTION_TIMEOUT")
        requireCurrent(generation)
        hasConnectedOnce = true
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.CONNECTED, error = "") }
        voiceSocket.start()
    }

    private suspend fun establishLiveKit(join: VoiceJoinTicket, saved: VoicePreferenceState, generation: Long) {
        check(join.media.url.startsWith("wss://") && join.media.token.isNotBlank()) { "VOICE_MEDIA_NOT_CONFIGURED" }
        requireCurrent(generation)
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.CONNECTING_EDGE, error = "") }
        // LiveKit owns Android audio focus for its room. Taking focus here as
        // well makes LiveKit's own focus request look like a competing app;
        // our listener then safety-mutes the microphone forever.
        abandonAudioFocus()
        safetyMuted = false
        VoiceCallController.update { it.copy(interrupted = false) }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setAudioRoute("system", remember = false)

        val room = LiveKit.create(applicationContext)
        liveKitRoom = room
        liveKitEvents = scope.launch {
            room.events.collect { event -> handleLiveKitEvent(event) }
        }
        room.connect(join.media.url, join.media.token)
        requireCurrent(generation)
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.PREPARING_AUDIO) }
        if (!saved.muted) {
            check(room.localParticipant.setMicrophoneEnabled(true)) { "VOICE_MICROPHONE_FAILED" }
        } else {
            room.localParticipant.setMicrophoneEnabled(false)
        }
        room.setSpeakerMute(saved.deafened)
        applyLiveKitVolume(saved.volume)
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.SUBSCRIBING) }
        reconcile()
        requireCurrent(generation)
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.ENTERING) }
        hasConnectedOnce = true
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.CONNECTED, error = "") }
        voiceSocket.start()
    }

    private fun handleLiveKitEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.ActiveSpeakersChanged -> {
                pendingSpeakers = event.speakers.mapNotNullTo(linkedSetOf()) { it.identity?.value }
                if (speakingPublish?.isActive != true) {
                    speakingPublish = scope.launch {
                        // Do not debounce by cancelling: continuous speech can
                        // then postpone the border forever. Sample the latest
                        // set instead, at a bounded UI cadence.
                        delay(240)
                        val speakers = pendingSpeakers
                        VoiceCallController.update { state ->
                            if (state.speakingPlayerIds == speakers) state
                            else state.copy(speakingPlayerIds = speakers)
                        }
                    }
                }
            }
            is RoomEvent.TrackSubscribed -> (event.track as? RemoteAudioTrack)?.setVolume(
                VoiceCallController.state.value.volume.toDouble()
            )
            is RoomEvent.Reconnecting -> VoiceCallController.update {
                it.copy(stage = VoiceConnectionStage.CONNECTING_EDGE, error = "Connection is recovering")
            }
            is RoomEvent.Reconnected -> VoiceCallController.update {
                it.copy(stage = VoiceConnectionStage.CONNECTED, error = "")
            }
            is RoomEvent.Disconnected -> if (!disposingPeer && ticket != null) {
                requestRecovery(event.error?.message ?: "VOICE_CONNECTION_FAILED")
            }
            else -> Unit
        }
    }

    private fun requireCurrent(generation: Long) {
        check(generation == connectionGeneration && ticket != null) { "VOICE_OPERATION_CANCELLED" }
    }

    private fun initializeWebRtc(saved: VoicePreferenceState) {
        if (peerConnection != null) return
        peerConnected = CompletableDeferred()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        peerFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        audioSource = peerFactory!!.createAudioSource(MediaConstraints())
        localAudio = peerFactory!!.createAudioTrack("wyrm-microphone", audioSource).apply {
            setEnabled(!saved.muted)
        }
        val config = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer())
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = peerFactory!!.createPeerConnection(config, observer)
            ?: error("VOICE_PEER_CONNECTION_FAILED")
        peerConnection!!.addTrack(localAudio, listOf("wyrm-voice"))
        requestAudioFocus()
        localAudio?.setEnabled(!saved.muted && !safetyMuted)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setAudioRoute(saved.audioRoute, remember = false)
    }

    private suspend fun subscribeNew(tracks: List<VoiceTrackRef>) {
        val fresh = tracks.filter { it.trackName.isNotBlank() && subscribed.add("${it.sessionId}:${it.trackName}") }
        if (fresh.isEmpty()) return
        val current = ticket ?: return
        val answer = repository.subscribe(current.room.id, current.participantId, fresh)
        if (answer.requiresRenegotiation && answer.sdp.isNotBlank()) {
            peerConnection?.setRemoteAwait(
                SessionDescription(SessionDescription.Type.fromCanonicalForm(answer.sdpType), answer.sdp)
            )
            val localAnswer = peerConnection?.createAnswerAwait() ?: return
            peerConnection?.setLocalAwait(localAnswer)
            repository.renegotiate(current.room.id, current.participantId, localAnswer.description)
        }
    }

    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            var failures = 0
            while (true) {
                // Socket events carry room changes immediately. This poll is
                // only the liveness safety net; twenty seconds remains below
                // the backend's 45-second presence expiry and leaves the audio
                // device more headroom than two TLS requests every eight.
                delay(20_000)
                try {
                    reconcile()
                    failures = 0
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (failure is VoiceApiException && failure.status == 429) {
                        // Media can remain healthy while the control plane asks
                        // us to slow down. Back off instead of destroying and
                        // recreating the LiveKit session.
                        delay((failure.retryAfterSeconds.coerceAtLeast(4) * 1_000L).coerceAtMost(60_000L))
                        continue
                    }
                    failures++
                    VoiceCallController.update { state -> state.copy(error = "Connection is recovering") }
                    if (failures >= 3) requestRecovery("VOICE_CONTROL_PLANE_INTERRUPTED")
                }
            }
        }
    }

    private fun requestRecovery(reason: String) {
        val stage = VoiceCallController.state.value.stage
        if (disposingPeer || ticket == null || recovery?.isActive == true || !hasConnectedOnce ||
            (stage != VoiceConnectionStage.CONNECTED && stage != VoiceConnectionStage.CONNECTING_EDGE)) return
        recovery = scope.launch {
            heartbeat?.cancel()
            val current = ticket ?: return@launch
            val saved = preferences.read(VoiceCallController.state.value.playerId)
            VoiceCallController.update { it.copy(stage = VoiceConnectionStage.CONNECTING_EDGE, error = "Connection is recovering") }
            var lastFailure = reason
            for (attempt in 0 until 3) {
                if (attempt > 0) delay(1_000L shl attempt)
                val generation = ++connectionGeneration
                disposePeer()
                val result = runCatching { establishMedia(current, saved, generation) }
                if (result.isSuccess) {
                    startHeartbeat()
                    return@launch
                }
                lastFailure = result.exceptionOrNull()?.message ?: reason
            }
            fail(if (lastFailure == "VOICE_OPERATION_CANCELLED") lastFailure else "VOICE_RECONNECT_FAILED")
        }
    }

    private suspend fun reconcile(publishPresence: Boolean = true) {
        reconcileMutex.withLock {
            val current = ticket ?: return@withLock
            val state = VoiceCallController.state.value
            if (publishPresence) repository.presence(current.room.id, state.muted, state.deafened)
            val participants = repository.participants(current.room.id)
            VoiceCallController.update {
                if (it.participants == participants && it.error.isEmpty()) it
                else it.copy(participants = participants, error = "")
            }
            if (current.media.provider == "cloudflare") {
                subscribeNew(repository.tracks(current.room.id, current.participantId))
            }
            collectSpeaking(participants)
        }
    }

    private fun collectSpeaking(participants: List<VoiceParticipant>) {
        if (liveKitRoom != null) return
        val peer = peerConnection ?: return
        peer.getStats { report ->
            val trackLevels = mutableMapOf<String, Double>()
            report.statsMap.values.forEach { stat ->
                if (stat.type != "inbound-rtp") return@forEach
                val kind = stat.members["kind"] ?: stat.members["mediaType"]
                if (kind != "audio") return@forEach
                val id = stat.members["trackIdentifier"]?.toString().orEmpty()
                val level = (stat.members["audioLevel"] as? Number)?.toDouble() ?: 0.0
                if (id.isNotBlank()) trackLevels[id] = level
            }
            val speaking = participants.filter { (trackLevels[it.trackName] ?: 0.0) > 0.035 }
                .mapTo(linkedSetOf()) { it.playerId }
            VoiceCallController.update { it.copy(speakingPlayerIds = speaking) }
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                peerConnected.complete(Unit)
            }
            if (state == PeerConnection.IceConnectionState.FAILED || state == PeerConnection.IceConnectionState.DISCONNECTED) {
                if (!peerConnected.isCompleted) peerConnected.completeExceptionally(IllegalStateException("VOICE_ICE_FAILED"))
                requestRecovery("VOICE_ICE_FAILED")
            }
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
            if (state == PeerConnection.PeerConnectionState.CONNECTED) peerConnected.complete(Unit)
            if (state == PeerConnection.PeerConnectionState.FAILED || state == PeerConnection.PeerConnectionState.DISCONNECTED) {
                if (!peerConnected.isCompleted) peerConnected.completeExceptionally(IllegalStateException("VOICE_CONNECTION_FAILED"))
                requestRecovery("VOICE_CONNECTION_FAILED")
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) iceGathered.complete(Unit)
        }
        override fun onIceCandidate(candidate: IceCandidate?) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track() as? AudioTrack ?: return
            remoteAudio[track.id()] = track
            track.setEnabled(!VoiceCallController.state.value.deafened)
            runCatching { track.setVolume(VoiceCallController.state.value.volume.toDouble()) }
        }
    }

    private fun setMuted(muted: Boolean, remember: Boolean) {
        localAudio?.setEnabled(!muted && !safetyMuted)
        VoiceCallController.update { it.copy(muted = muted) }
        persist(remember)
        updateNotification()
        val generation = ++muteGeneration
        scope.launch {
            val mediaApplied = liveKitRoom?.localParticipant?.setMicrophoneEnabled(!muted) ?: true
            if (generation != muteGeneration) return@launch
            if (!mediaApplied) {
                VoiceCallController.update { it.copy(muted = true, error = "VOICE_MICROPHONE_FAILED") }
                persist(remember)
                updateNotification()
                return@launch
            }
            ticket?.let {
                runCatching { repository.presence(it.room.id, muted, VoiceCallController.state.value.deafened) }
            }
        }
    }

    private fun setDeafened(deafened: Boolean, remember: Boolean) {
        remoteAudio.values.forEach { it.setEnabled(!deafened) }
        liveKitRoom?.setSpeakerMute(deafened)
        VoiceCallController.update { it.copy(deafened = deafened) }
        persist(remember)
        scope.launch { ticket?.let { runCatching { repository.presence(it.room.id, VoiceCallController.state.value.muted, deafened) } } }
    }

    private fun setVolume(value: Float, remember: Boolean) {
        val volume = value.coerceIn(0f, 1f)
        remoteAudio.values.forEach { runCatching { it.setVolume(volume.toDouble()) } }
        applyLiveKitVolume(volume)
        VoiceCallController.update { it.copy(volume = volume) }
        persist(remember)
    }

    private fun setAudioRoute(route: String, remember: Boolean) {
        val chosen = route.takeIf { it in VoicePreferences.ROUTES } ?: "system"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val type = when (chosen) {
                "speaker" -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                "earpiece" -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                "bluetooth" -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                else -> null
            }
            type?.let { wanted ->
                audioManager.availableCommunicationDevices.firstOrNull { it.type == wanted }
                    ?.let(audioManager::setCommunicationDevice)
            } ?: audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = chosen == "speaker"
        }
        VoiceCallController.update { it.copy(audioRoute = chosen) }
        persist(remember)
    }

    private fun persist(remember: Boolean) {
        if (!remember) return
        val state = VoiceCallController.state.value
        if (state.playerId.isBlank()) return
        preferences.write(
            state.playerId,
            VoicePreferenceState(state.muted, state.deafened, state.volume, state.audioRoute, true),
        )
    }

    private fun fail(code: String) {
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.FAILED, error = code) }
        updateNotification()
    }

    private fun leave() {
        val current = ticket
        ++connectionGeneration
        ticket = null
        hasConnectedOnce = false
        VoiceCallController.update { it.copy(stage = VoiceConnectionStage.LEAVING) }
        heartbeat?.cancel()
        speakingPublish?.cancel()
        speakingPublish = null
        recovery?.cancel()
        voiceSocket.stop()
        scope.launch {
            if (current != null) runCatching { repository.leave(current.room.id) }
            shutdownMedia()
            VoiceCallController.publish(VoiceCallState())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun shutdownMedia() {
        disposePeer()
        abandonAudioFocus()
        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
    }

    private fun disposePeer() {
        disposingPeer = true
        liveKitEvents?.cancel(); liveKitEvents = null
        liveKitRoom?.disconnect(); liveKitRoom?.release(); liveKitRoom = null
        remoteAudio.clear()
        subscribed.clear()
        peerConnection?.close(); peerConnection?.dispose(); peerConnection = null
        localAudio?.dispose(); localAudio = null
        audioSource?.dispose(); audioSource = null
        peerFactory?.dispose(); peerFactory = null
        disposingPeer = false
    }

    private fun applyLiveKitVolume(volume: Float) {
        liveKitRoom?.remoteParticipants?.values?.forEach { participant ->
            participant.audioTrackPublications.forEach { (_, track) ->
                (track as? RemoteAudioTrack)?.setVolume(volume.toDouble())
            }
        }
    }

    private fun requestAudioFocus() {
        if (audioFocusHeld) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        }
        audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        safetyMuted = !audioFocusHeld
        VoiceCallController.update { it.copy(interrupted = safetyMuted) }
    }

    private fun abandonAudioFocus() {
        if (audioFocusHeld && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else if (audioFocusHeld) {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        audioFocusHeld = false
        audioFocusRequest = null
        safetyMuted = false
    }

    private fun startForegroundNow() {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): android.app.Notification {
        val state = VoiceCallController.state.value
        val open = PendingIntent.getActivity(
            this, 91, Intent(this, WyrmActivity::class.java).putExtra("wyrm.kind", "voice_call"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String, label: String, request: Int) = NotificationCompat.Action(
            0, label,
            PendingIntent.getService(
                this, request, Intent(this, VoiceCallService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (state.roomName.isBlank()) "Wyrm voice" else state.roomName)
            .setContentText(
                when {
                    state.interrupted -> "Microphone temporarily muted"
                    state.muted -> "Microphone muted"
                    else -> "Voice chat is active"
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(action(ACTION_MUTE, if (state.muted || state.interrupted) "Unmute" else "Mute", 92))
            .addAction(action(ACTION_LEAVE, "Leave", 93))
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "Voice calls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown only while a Wyrm voice room is connected"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        ++connectionGeneration
        ticket = null
        hasConnectedOnce = false
        heartbeat?.cancel()
        recovery?.cancel()
        voiceSocket.stop()
        shutdownMedia()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "wyrm_voice_call"
        private const val NOTIFICATION_ID = 5771
        private const val ACTION_CONNECT = "com.wyrm.omrajput.voice.CONNECT"
        private const val ACTION_MUTE = "com.wyrm.omrajput.voice.MUTE"
        private const val ACTION_DEAFEN = "com.wyrm.omrajput.voice.DEAFEN"
        private const val ACTION_LEAVE = "com.wyrm.omrajput.voice.LEAVE"
        private const val ACTION_ROUTE = "com.wyrm.omrajput.voice.ROUTE"
        private const val ACTION_VOLUME = "com.wyrm.omrajput.voice.VOLUME"
        private const val EXTRA_PARTICIPANT = "participant"
        private const val EXTRA_ROUTE = "route"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_STATE = "state"
        private val pendingTickets = ConcurrentHashMap<String, VoiceJoinTicket>()

        fun connect(context: Context, playerId: String, ticket: VoiceJoinTicket) {
            pendingTickets[ticket.participantId] = ticket
            context.getSharedPreferences("wyrm_voice_runtime", Context.MODE_PRIVATE)
                .edit().putString("playerId", playerId).apply()
            val intent = Intent(context, VoiceCallService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_PARTICIPANT, ticket.participantId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun toggleMute(context: Context) {
            val target = !VoiceCallController.state.value.muted
            VoiceCallController.update { it.copy(muted = target) }
            context.startService(
                Intent(context, VoiceCallService::class.java).setAction(ACTION_MUTE)
                    .putExtra(EXTRA_STATE, target)
            )
        }

        fun toggleDeafen(context: Context) {
            val target = !VoiceCallController.state.value.deafened
            VoiceCallController.update { it.copy(deafened = target) }
            context.startService(
                Intent(context, VoiceCallService::class.java).setAction(ACTION_DEAFEN)
                    .putExtra(EXTRA_STATE, target)
            )
        }
        fun leave(context: Context) = context.startService(Intent(context, VoiceCallService::class.java).setAction(ACTION_LEAVE))
        fun setRoute(context: Context, route: String) = context.startService(
            Intent(context, VoiceCallService::class.java).setAction(ACTION_ROUTE).putExtra(EXTRA_ROUTE, route)
        )
        fun setVolume(context: Context, volume: Float) = context.startService(
            Intent(context, VoiceCallService::class.java).setAction(ACTION_VOLUME).putExtra(EXTRA_VOLUME, volume)
        )
    }
}

private class AwaitSdp : SdpObserver {
    lateinit var success: (SessionDescription) -> Unit
    lateinit var failure: (Throwable) -> Unit
    override fun onCreateSuccess(description: SessionDescription) = success(description)
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(message: String) = failure(IllegalStateException(message))
    override fun onSetFailure(message: String) = failure(IllegalStateException(message))
}

private suspend fun PeerConnection.createOfferAwait(): SessionDescription = suspendCancellableCoroutine { continuation ->
    val observer = AwaitSdp().apply {
        success = { if (continuation.isActive) continuation.resume(it) }
        failure = { if (continuation.isActive) continuation.resumeWithException(it) }
    }
    createOffer(observer, MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    })
}

private suspend fun PeerConnection.createAnswerAwait(): SessionDescription = suspendCancellableCoroutine { continuation ->
    val observer = AwaitSdp().apply {
        success = { if (continuation.isActive) continuation.resume(it) }
        failure = { if (continuation.isActive) continuation.resumeWithException(it) }
    }
    createAnswer(observer, MediaConstraints())
}

private suspend fun PeerConnection.setLocalAwait(description: SessionDescription) = suspendCancellableCoroutine { continuation ->
    setLocalDescription(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
        override fun onCreateFailure(message: String?) = Unit
        override fun onSetFailure(message: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message)) }
    }, description)
}

private suspend fun PeerConnection.setRemoteAwait(description: SessionDescription) = suspendCancellableCoroutine { continuation ->
    setRemoteDescription(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
        override fun onCreateFailure(message: String?) = Unit
        override fun onSetFailure(message: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message)) }
    }, description)
}
