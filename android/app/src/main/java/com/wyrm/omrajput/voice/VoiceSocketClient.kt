package com.wyrm.omrajput.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Authenticated wake-up path; room state still comes from authorized REST. */
internal class VoiceSocketClient(
    context: Context,
    baseUrl: String,
    private val scope: CoroutineScope,
    private val onWake: () -> Unit,
) {
    private val session = context.applicationContext
        .getSharedPreferences("wyrm_session", Context.MODE_PRIVATE)
    private val url = baseUrl.trimEnd('/').replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://") + "/v1/voice/socket"
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var reconnect: Job? = null
    private var wake: Job? = null
    @Volatile private var running = false
    private var failures = 0

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        reconnect?.cancel()
        reconnect = null
        wake?.cancel()
        wake = null
        socket?.close(1000, "Voice call ended")
        socket = null
    }

    private fun connect() {
        if (!running) return
        val token = session.getString("token", null).orEmpty()
        if (token.isBlank()) return
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("skip_zrok_interstitial", "1")
            .build()
        socket = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            failures = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val type = runCatching { JSONObject(text).optString("type") }.getOrDefault("")
            if (type.startsWith("voice.") && type != "voice.session.ready") {
                // Join/leave and state updates arrive in bursts. One refresh
                // after the burst sees the final room snapshot.
                wake?.cancel()
                wake = scope.launch {
                    delay(180)
                    onWake()
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (running) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            if (running) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnect?.isActive == true) return
        reconnect = scope.launch {
            failures++
            delay((1_000L shl failures.coerceAtMost(4)).coerceAtMost(30_000L))
            connect()
        }
    }
}
