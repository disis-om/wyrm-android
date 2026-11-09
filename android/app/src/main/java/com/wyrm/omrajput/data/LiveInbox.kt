package com.wyrm.omrajput.data

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

/**
 * The live inbox (OM, 2026-10-01): `GET /v1/me/live` held open while Wyrm is on
 * screen. The server says only "something new for you" (`{type:"inbox", kind,
 * id}`); [onInbox] refetches, so the Notifications page, the DM count and the
 * badges update without a refresh. FCM still covers the app in the background.
 */
internal class LiveInbox(
    context: Context,
    baseUrl: String,
    private val scope: CoroutineScope,
    private val onInbox: (kind: String, id: String) -> Unit,
    /** Presence's `platform` (Wyrm Desktop passes "windows"). */
    private val platform: String = "android",
    private val onMessageDeleted: (type: String, id: String) -> Unit = { _, _ -> },
    /** A global or direct message was edited (2026-10-30): "chat.edited" / "direct.edited". */
    private val onMessageEdited: (type: String) -> Unit = { },
    /** Chat, the next level (2026-10-30): "chat.message", "direct.message", "chat.reaction", "chat.pins", "typing". */
    private val onChatEvent: (JSONObject) -> Unit = { },
) {
    private val session = context.applicationContext.getSharedPreferences("wyrm_session", Context.MODE_PRIVATE)
    private val url = baseUrl.trimEnd('/').replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://") + "/v1/me/live"
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var reconnect: Job? = null
    private var burst: Job? = null
    @Volatile private var running = false
    private var failures = 0
    private val pendingKinds = mutableMapOf<String, String>()
    /** The last presence said (2026-10-09), said again after every reconnect. */
    @Volatile private var presence: String? = null

    /**
     * What the player is doing (backend presence.mjs): home, lobby, playing
     * with the arena, practice or idle. Sent when it changes; a reconnect
     * repeats the last one.
     */
    fun setPresence(state: String, arena: String?) {
        val json = JSONObject().put("type", "presence").put("state", state).put("platform", platform)
            .apply { if (state == "playing" && !arena.isNullOrBlank()) put("arena", arena) }
            .toString()
        if (json == presence) return
        presence = json
        socket?.send(json)
    }

    /**
     * Friends on the minimap (OM, 2026-11-07, backend friend-locations.mjs):
     * the server says when a mutual follow plays in the same arena
     * ([locationWanted]); then the app sends its place about once a second.
     * Friends' places arrive on [onFriendLocation]: id, x, y, icon.
     */
    @Volatile var locationWanted = false
        private set
    @Volatile var onFriendLocation: ((String, Int, Int, Int) -> Unit)? = null
    fun sendLocation(x: Int, y: Int, icon: Int) {
        if (!locationWanted) return
        socket?.send(JSONObject().put("type", "loc").put("x", x).put("y", y).put("icon", icon).toString())
    }

    /** "I am typing" (2026-10-30): global chat, or a direct thread to [to]. The server throttles it too. */
    private var typingAt = 0L
    fun sendTyping(direct: Boolean, to: String = "") {
        val now = System.currentTimeMillis()
        if (now - typingAt < 2_500) return
        typingAt = now
        val json = JSONObject().put("type", "typing").put("room", if (direct) "direct" else "global")
        if (direct) json.put("to", to)
        socket?.send(json.toString())
    }

    fun start() {
        if (running) return
        running = true
        failures = 0
        connect()
    }

    fun stop() {
        running = false
        reconnect?.cancel()
        reconnect = null
        socket?.close(1000, "Wyrm left the screen")
        socket = null
    }

    private fun connect() {
        if (!running) return
        val token = session.getString("token", null).orEmpty()
        if (token.isBlank()) { running = false; return }
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .build()
        socket = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            failures = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (event.optString("type")) {
                "chat.deleted", "direct.deleted" -> scope.launch {
                    if (running && socket === webSocket) onMessageDeleted(event.optString("type"), event.optString("id"))
                }
                "chat.edited", "direct.edited" -> scope.launch {
                    if (running && socket === webSocket) onMessageEdited(event.optString("type"))
                }
                "chat.message", "direct.message", "chat.reaction", "chat.pins", "typing" -> scope.launch {
                    if (running && socket === webSocket) onChatEvent(event)
                }
                "loc.want" -> locationWanted = event.optBoolean("on", false)
                "friend.loc" -> onFriendLocation?.invoke(event.optString("id"),
                    event.optInt("x"), event.optInt("y"), event.optInt("icon"))
                // A reconnect may have missed something: one catch-up refresh.
                "live.ready" -> {
                    locationWanted = false
                    presence?.let { webSocket.send(it) }
                    scope.launch { onInbox("", "") }
                }
                "inbox" -> {
                    // A burst (a broadcast, several follows) becomes one refresh per kind.
                    synchronized(pendingKinds) { pendingKinds[event.optString("kind")] = event.optString("id") }
                    burst?.cancel()
                    burst = scope.launch {
                        delay(220)
                        val kinds = synchronized(pendingKinds) { pendingKinds.toMap().also { pendingKinds.clear() } }
                        kinds.forEach { (kind, id) -> onInbox(kind, id) }
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // 1008: the session is no longer valid; do not hammer the server.
            if (running && code != 1008) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            if (running) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnect?.isActive == true) return
        reconnect = scope.launch {
            failures++
            delay((1_000L shl failures.coerceAtMost(5)).coerceAtMost(30_000L))
            connect()
        }
    }
}
