package com.wyrm.omrajput.data

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

data class RainCharacter(
    // Every new character is "wyrm.bot", the first one too (OM, 2026-10-14).
    val id: String = UUID.randomUUID().toString(), val label: String = DEFAULT_NAME,
    val name: String = DEFAULT_NAME, val skin: String = "uuuuuuuauuuuuuaauuuuuaaauuuuaaaauuuaaaaauuaaaaaauaaaaaaauuaaaaaauuuaaaaauuuuaaaauuuuuaaauuuuuuaa",
    val colors: List<Int> = emptyList(),
    val tag: String = "12", val cosmetic: Int = 11, val hash: String = "", val enabled: Boolean = true,
) {
    /** Typing preserves only the common prefix, as the Skin tab does. Presets reset it. */
    fun withSkin(next: String): RainCharacter {
        val code = next.take(250)
        val common = if (skin.startsWith("/") || skin.startsWith("\\") || code.startsWith("/") || code.startsWith("\\")) 0
            else skin.zip(code).takeWhile { it.first == it.second }.size
        return copy(skin = code, colors = List(code.length) { if (it < common) colors.getOrElse(it) { 0 } else 0 })
    }
    fun json() = JSONObject().put("id", id).put("label", label).put("name", name).put("skin", skin)
        .also { o ->
            val stored = colors.take(250).dropLastWhile { it == 0 }
            if (stored.isNotEmpty()) o.put("colors", JSONArray().also { a ->
                stored.forEach { a.put(if (it == 0) "" else it.toUInt().toString(16).padStart(8, '0')) }
            })
        }
        .put("tag", tag).put("cosmetic", cosmetic).put("hash", hash).put("enabled", enabled)
    companion object {
        const val DEFAULT_NAME = "wyrm.bot"
        fun from(o: JSONObject) = RainCharacter(
            id = o.optString("id").takeIf { it.length in 1..80 } ?: UUID.randomUUID().toString(),
            label = o.optString("label", DEFAULT_NAME).take(24),
            name = o.optString("name", DEFAULT_NAME).filter { it.code in 32..126 }.take(24),
            skin = o.optString("skin", "uu").filter { it.code in 32..126 }.take(250),
            colors = List(o.optString("skin", "uu").filter { it.code in 32..126 }.take(250).length) { i ->
                val raw = o.optJSONArray("colors")?.opt(i)
                if (raw is String && raw.length in 1..8 && raw.all { it in "0123456789abcdefABCDEF" }) raw.toLongOrNull(16)?.toInt() ?: 0 else 0
            },
            tag = o.optString("tag", "-1").filter { it.code in 32..126 }.take(255),
            cosmetic = o.optInt("cosmetic", 255).takeIf { it in 0..31 || it == 255 } ?: 255,
            hash = o.optString("hash").filter { it.code in 32..126 }.take(64), enabled = o.optBoolean("enabled", true),
        )
    }
}

/** Network traffic is outside the engine; UI state is only written on Main. */
class SnakeyRain(private val context: Context, private val read: () -> String,
                 private val enableEngine: (Boolean) -> Unit, private val rotateEngine: (Int) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("wyrm_rain", Context.MODE_PRIVATE)
    private val auth = context.getSharedPreferences("wyrm_rain_auth", Context.MODE_PRIVATE)
    // No WebSocket ping: Rain's server never answers one, so OkHttp dropped the
    // link after ~20 s (OM, 2026-10-14). The service keeps it alive with 'P'.
    private val http = OkHttpClient.Builder().build()
    private var socket: WebSocket? = null
    private var epoch = 0
    private var sentGeneration = -1L
    private var lastAlive = false
    private var lastPointer = 0L
    private var poll: Job? = null
    private var settingsSend: Job? = null
    private var characterSend: Job? = null
    private var secretSignature = ""
    var config by mutableStateOf(JSONObject()); private set
    var characters by mutableStateOf(listOf(RainCharacter())); private set
    var enabled by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set
    var running by mutableStateOf(false); private set
    var count by mutableStateOf(0); private set
    var mode by mutableStateOf(1); private set
    var boosting by mutableStateOf(false); private set
    var status by mutableStateOf("Public account"); private set
    var personal by mutableStateOf(false); private set
    var username by mutableStateOf(PUBLIC_USER); private set
    var password by mutableStateOf(PUBLIC_PASSWORD); private set
    /** What the panel's Target tab shows; only published while it is open (no 10 Hz redraw otherwise). */
    var snapshot by mutableStateOf(JSONObject()); private set
    /** The engine's latest snapshot, read by the controller; never drives the UI. */
    private var latest = JSONObject()
    /** True while the panel's Target tab is on screen. */
    var liveTargets = false
    /** Our snake is in an arena now (from the engine's latest snapshot). */
    val alive: Boolean get() = latest.optBoolean("alive")
    private var saveJob: Job? = null
    var granted by mutableStateOf(1000); private set
    var rotation by mutableStateOf(0); private set
    /** Grows on each tap of the arena's Rain key (engine `open` count): the panel opens or closes. */
    var panelRequests by mutableStateOf(0); private set
    private var lastOpen = -1
    val modes = listOf("Follow", "Unfollow", "Random", "Straight", "Copy", "Follow pointer", "Hold pointer", "Rain", "Tornado", "Circle CW", "Circle CCW", "Target", "Heart", "Wigwag")
    init { loadCredentials(); reload() }
    fun reload() {
        loadCredentials()
        if (!prefs.contains("settings")) {
            characters = listOf(RainCharacter()); config = JSONObject(); enable(false)
        }
        val o = runCatching { JSONObject(prefs.getString("settings", "{}")!!) }.getOrDefault(JSONObject())
        restore(o)
    }
    fun document(): JSONObject = JSONObject(config.toString()).put("v", 1).put("enabled", enabled)
        .put("characters", JSONArray().also { a -> characters.forEach { a.put(it.json().put("hash", "").put("tag", if (it.tag.startsWith("!")) "-1" else it.tag)) } })
    fun restore(o: JSONObject) {
        val next = JSONObject()
        ranges.forEach { (key, range) ->
            val previous = config.optDouble(key, defaults.getValue(key))
            val n = o.optDouble(key, previous)
            val bounded = if (n.isFinite()) n.coerceIn(range) else previous
            next.put(key, if (key == "panelTextColour" || key == "panelFillColour") kotlin.math.round(bounded) else bounded)
        }
        next.put("rotationEnabled", o.optBoolean("rotationEnabled", config.optBoolean("rotationEnabled", true)))
        next.put("evolveEnabled", o.optBoolean("evolveEnabled", config.optBoolean("evolveEnabled", false)))
        val keys = JSONObject(config.optJSONObject("keys")?.toString() ?: JSONObject(defaultKeys).toString())
        o.optJSONObject("keys")?.let { raw -> defaultKeys.keys.forEach { if (raw.opt(it) is String) keys.put(it, raw.getString(it).take(30)) } }
        next.put("keys", keys)
        config = next
        o.optJSONArray("characters")?.let { list ->
            val values = (0 until minOf(list.length(), 100)).mapNotNull { list.optJSONObject(it)?.let(RainCharacter::from) }
            characters = values.distinctBy { it.id } // An empty list is the player's choice.
        }
        loadBotSecrets()
        enable(o.optBoolean("enabled", enabled))
    }
    // A slider tick no longer writes the file each time: the game stuttered
    // while the panel was touched (OM, 2026-10-14). The last value lands 400 ms later.
    private fun save() {
        saveJob?.cancel()
        saveJob = scope.launch { delay(400); saveNow() }
    }
    private fun saveNow() {
        saveJob?.cancel(); saveJob = null
        prefs.edit().putString("settings", document().toString()).apply()
        val secrets = JSONObject()
        characters.filter { it.tag.startsWith("!") || it.hash.isNotEmpty() }.forEach { secrets.put(it.id, JSONObject().put("tag", it.tag).put("hash", it.hash)) }
        val signature = secrets.toString()
        if (signature == secretSignature) return
        secretSignature = signature
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, TeamVaultKey.key(context, "wyrm_rain_credentials"))
            val bytes = cipher.doFinal(secrets.toString().toByteArray(Charsets.UTF_8))
            auth.edit().putString("tag_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("tag_blob", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        }
    }
    private fun loadBotSecrets() {
        runCatching {
            val blob = auth.getString("tag_blob", null) ?: return
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, TeamVaultKey.key(context, "wyrm_rain_credentials"), GCMParameterSpec(128, Base64.decode(auth.getString("tag_iv", ""), Base64.NO_WRAP)))
            val secrets = JSONObject(String(cipher.doFinal(Base64.decode(blob, Base64.NO_WRAP)), Charsets.UTF_8))
            characters = characters.map { c -> secrets.optJSONObject(c.id)?.let { c.copy(tag = it.optString("tag", c.tag), hash = it.optString("hash")) } ?: c }
        }
    }
    fun enable(on: Boolean) {
        enabled = on; enableEngine(on)
        if (!on) { disconnect(); poll?.cancel(); poll = null }
        else if (poll == null) {
            poll = scope.launch { while (isActive) { observe(); delay(30) } }
            connect()
        }
        save()
    }
    fun set(key: String, value: Double) {
        val range = ranges[key] ?: return
        if (!value.isFinite()) return
        val bounded = value.coerceIn(range)
        config = JSONObject(config.toString()).put(key, if (key == "panelTextColour" || key == "panelFillColour") kotlin.math.round(bounded) else bounded); save(); sendSettings()
    }
    fun flag(key: String, on: Boolean) {
        if (key !in setOf("evolveEnabled", "rotationEnabled")) return
        config = JSONObject(config.toString()).put(key, on); if (!on && key == "rotationEnabled") rotate(0)
        save(); sendSettings()
    }
    fun update(character: RainCharacter) { characters = characters.map { if (it.id == character.id) RainCharacter.from(character.json()) else it }; save(); sendCharacters() }
    /** Adds a "wyrm.bot" character and returns it (null at Rain's 100). */
    fun add(): RainCharacter? {
        if (characters.size >= MAX_CHARACTERS) return null
        val made = RainCharacter(); characters = characters + made; save(); sendCharacters(); return made
    }
    fun remove(ids: Set<String>) { characters = characters.filterNot { it.id in ids }; save(); sendCharacters() }
    fun move(id: String, delta: Int) {
        val list = characters.toMutableList(); val at = list.indexOfFirst { it.id == id }; val to = at + delta
        if (at >= 0 && to in list.indices) { list.add(to, list.removeAt(at)); characters = list; save(); sendCharacters() }
    }
    fun all(on: Boolean) { characters = characters.map { it.copy(enabled = on) }; save(); sendCharacters() }
    fun reset() { characters = listOf(RainCharacter()); config = JSONObject(); restore(JSONObject().put("enabled", enabled)); save(); sendSettings(); sendCharacters() }
    fun credentials(user: String, pass: String, usePersonal: Boolean) {
        disconnect(); personal = usePersonal
        username = if (usePersonal) user.trim().take(128) else PUBLIC_USER
        password = if (usePersonal) pass.take(256) else PUBLIC_PASSWORD
        if (usePersonal) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, TeamVaultKey.key(context, "wyrm_rain_credentials"))
            val bytes = cipher.doFinal(JSONObject().put("user", username).put("pass", password).toString().toByteArray(Charsets.UTF_8))
            auth.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("blob", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        } else auth.edit().remove("iv").remove("blob").apply()
        if (enabled) connect()
    }
    private fun loadCredentials() {
        if (!auth.contains("blob")) { personal = false; username = PUBLIC_USER; password = PUBLIC_PASSWORD }
        // Public drafts never persist; restarting always restores factory values.
        runCatching {
            val blob = auth.getString("blob", null) ?: return
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, TeamVaultKey.key(context, "wyrm_rain_credentials"), GCMParameterSpec(128, Base64.decode(auth.getString("iv", ""), Base64.NO_WRAP)))
            val o = JSONObject(String(cipher.doFinal(Base64.decode(blob, Base64.NO_WRAP)), Charsets.UTF_8))
            username = o.getString("user"); password = o.getString("pass"); personal = true
        }.onFailure { auth.edit().remove("iv").remove("blob").apply(); personal = false; username = PUBLIC_USER; password = PUBLIC_PASSWORD }
    }
    fun connect() {
        if (!enabled || socket != null) return
        if (username.isBlank() || password.isEmpty()) { status = "Enter your Rain credentials"; return }
        val generation = ++epoch; status = "Connecting…"
        socket = http.newWebSocket(Request.Builder().url("wss://snakeyrain.com:8421/").header("Origin", "http://slither.io").build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { scope.launch { if (generation == epoch) ws.send(RainWire.login(username, password).toByteString()) } }
            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) { scope.launch { if (generation == epoch) message(bytes.toByteArray()) } }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { scope.launch { if (generation == epoch) ended("Disconnected") } }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { scope.launch { if (generation == epoch) ended("Rain connection failed. Tap reconnect.") } }
        })
    }
    private fun ended(message: String) { socket = null; connected = false; running = false; count = 0; boosting = false; sentGeneration = -1; status = message; rotate(0) }
    fun disconnect() { ++epoch; if (connected) send(RainWire.pair('x'.code, 0)); socket?.close(1000, ""); socket = null; ended(if (personal) "Personal account" else "Public account") }
    private fun send(data: ByteArray) { if (connected) socket?.send(data.toByteString()) }
    private fun message(data: ByteArray) {
        runCatching {
            val r = RainWire.Reader(data)
            when (r.u8().toChar()) {
                'l' -> { val accepted = r.u8() != 0; val limit = if (accepted) r.u16() else 0
                    if (!accepted) { disconnect(); status = "Rain login was refused" } else { connected = true; granted = if (limit > 0) limit else 1000; status = if (personal) "Personal account connected" else "Public account connected"; sendSettings(); sendCharacters(); sentGeneration = -1 } }
                'g' -> mode = r.u8().coerceIn(1, 14)
                'b' -> boosting = r.u8() != 0
                'i' -> count = r.u16()
                'w' -> running = r.u8() != 0
                'm' -> { status = r.text(true).take(240); if (r.remaining() > 0) r.text(false) }
                'd' -> status = r.text(false).take(240)
                'P' -> send(RainWire.pair('P'.code, 1))
            }
        }.onFailure { disconnect(); status = "Rain sent an invalid response" }
    }
    private fun observe() {
        val next = runCatching { JSONObject(read()) }.getOrDefault(JSONObject())
        // Published at 10 Hz for UI; packet cadence stays 30 ms.
        val alive = next.optBoolean("alive"); val gen = next.optLong("generation")
        val open = next.optInt("open", -1)
        if (open >= 0) { if (lastOpen in 0 until open) panelRequests++; lastOpen = open }
        // Like Rain 6.36: a death or a new join never stops the storm; every
        // join is relayed again below and the bots carry on (OM, 2026-10-14).
        lastAlive = alive
        if (connected && alive && gen != sentGeneration) {
            val hex = next.optString("join")
            if (hex.isNotEmpty() && hex.length % 2 == 0) {
                val bytes = runCatching { hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
                if (bytes != null) { send(RainWire('S'.code).raw(bytes).bytes()); sentGeneration = gen }
            }
        }
        if (connected && alive && mode in setOf(1, 6, 9)) send(RainWire.xy('p'.code, next.optDouble("x", 0.0), next.optDouble("y", 0.0)))
        latest = next
        if (liveTargets && System.currentTimeMillis() - lastPointer >= 250) { snapshot = next; lastPointer = System.currentTimeMillis() }
    }
    fun start() {
        if (!connected) { connect(); status = "Wait for Rain login, then tap Start"; return }
        if (characters.none { it.enabled }) { status = "Add a character first"; return }
        val address = latest.optString("arena")
        if (!latest.optBoolean("alive") || address.isBlank()) { status = "Join an arena before starting Rain"; return }
        val at = address.lastIndexOf(':'); if (at < 1) return
        send(RainWire('r'.code).text(address.substring(0, at).removeSurrounding("[", "]"), false).text(address.substring(at + 1), false).bytes())
    }
    fun stop() { send(RainWire.pair('x'.code, 0)); rotate(0) }
    fun boost() { send(RainWire.pair('b'.code, if (boosting) 0 else 1)) }
    fun mode(id: Int) {
        if (id !in 1..14) return
        if (id in listOf(8, 9, 14)) send(RainWire.xy('o'.code, latest.optDouble("x", 0.0), latest.optDouble("y", 0.0)))
        send(RainWire.pair('d'.code, id))
    }
    fun target(id: Int, x: Double, y: Double) { send(RainWire('t'.code).u16(id.coerceIn(0, 65535)).u16(RainWire.coord(x)).u16(RainWire.coord(y)).bytes()); mode(12) }
    fun rotate(direction: Int) { rotation = if (config.optBoolean("rotationEnabled", true)) direction.coerceIn(-1, 1) else 0; rotateEngine(rotation) }
    fun handleKey(key: String): Boolean {
        if (!enabled) return false
        val bindings = config.optJSONObject("keys") ?: return false
        val action = defaultKeys.keys.firstOrNull { bindings.optString(it, defaultKeys.getValue(it)).equals(key, true) } ?: return false
        when (action) {
            "boost" -> boost(); "follow" -> mode(if (mode == 1) 2 else 1); "random" -> mode(3); "straight" -> mode(4)
            "copy" -> mode(5); "mouse" -> mode(if (mode == 6) 7 else 6); "rain" -> mode(8); "tornado" -> mode(9)
            "circle" -> mode(if (mode == 10) 11 else 10); "target" -> mode(12); "heart" -> mode(13); "wigwag" -> mode(14)
            "startAndStopBots" -> if (running) stop() else start()
            "rotateLeft" -> rotate(if (rotation == -1) 0 else -1); "rotateRight" -> rotate(if (rotation == 1) 0 else 1)
        }
        return true
    }
    private fun sendSettings() {
        settingsSend?.cancel()
        settingsSend = scope.launch { delay(100); sendSettingsNow() }
    }
    private fun sendSettingsNow() {
        val c = config
        send(RainWire('T'.code).u16(c.optInt("size")).u16(c.optInt("separation")).u8(c.optInt("finalAng"))
            .u16((c.optDouble("rOC") * 100).toInt()).u8(c.optInt("rotationDir")).u16(c.optInt("period"))
            .u8(c.optInt("amplitude")).u16(c.optInt("ranRate")).bytes())
        send(RainWire('B'.code).u16(c.optInt("maxBots").coerceAtMost(granted)).bytes())
        send(RainWire('E'.code).u8(if (c.optBoolean("evolveEnabled")) 1 else 0).u16(c.optInt("evolveTime")).u8(c.optInt("evolveBots")).bytes())
    }
    private fun sendCharacters() {
        characterSend?.cancel()
        characterSend = scope.launch { delay(2000); sendCharactersNow() }
    }
    private fun sendCharactersNow() {
        val selected = characters.filter { it.enabled }; val wire = RainWire('c'.code).u8(6).u8(36).u8(selected.size)
        selected.forEach { wire.text(it.name, false).text(it.skin, false).text(it.tag, false).u8(it.cosmetic).text(it.hash, false) }
        send(wire.bytes())
    }
    fun background() { saveNow(); stop(); disconnect() } // No hidden storm after leaving the app.
    fun accountCleared() { enable(false); auth.edit().clear().apply(); username = PUBLIC_USER; password = PUBLIC_PASSWORD; personal = false }
    fun close() { saveNow(); disconnect(); enableEngine(false); scope.cancel(); http.dispatcher.executorService.shutdown() }
    companion object {
        val ranges = linkedMapOf("maxBots" to 1.0..1000.0, "size" to 100.0..3000.0, "separation" to 0.0..3000.0,
            "finalAng" to 0.0..90.0, "rOC" to 0.0..1.0, "rotationDir" to 0.0..1.0, "period" to 100.0..10000.0,
            "amplitude" to 0.0..62.0, "ranRate" to 100.0..5000.0, "evolveTime" to 10.0..300.0, "evolveBots" to 1.0..5.0,
            // The arena panel's own look (OM, 2026-10-14): size, opacity, back plate. Never sent to Rain.
            "panelSize" to 0.7..1.3, "panelOpacity" to 0.3..1.0, "panelBack" to 0.0..1.0, "panelTextColour" to 0.0..8.0, "panelFillColour" to 0.0..8.0)
        /** What Rain's service sends to the bots; the panel keys stay on the phone and the account. */
        val stormKeys = listOf("maxBots", "size", "separation", "finalAng", "rOC", "rotationDir", "period", "amplitude", "ranRate", "evolveTime", "evolveBots")
        const val MAX_CHARACTERS = 100
        /** Rain 6.36's factory account, shared by everyone. */
        const val PUBLIC_USER = "snakeyuser"
        const val PUBLIC_PASSWORD = "wormfood"
        val defaults = mapOf("maxBots" to 1000.0, "size" to 2000.0, "separation" to 800.0, "finalAng" to 90.0,
            "rOC" to 0.5, "rotationDir" to 1.0, "period" to 2000.0, "amplitude" to 45.0, "ranRate" to 1000.0,
            "evolveTime" to 30.0, "evolveBots" to 1.0, "panelSize" to 1.0, "panelOpacity" to 1.0, "panelBack" to 0.94, "panelTextColour" to 0.0, "panelFillColour" to 0.0)
        val defaultKeys = linkedMapOf("boost" to "x", "follow" to "1", "random" to "2", "straight" to "3", "copy" to "4",
            "mouse" to "5", "rain" to "6", "tornado" to "7", "circle" to "F1", "target" to "F2", "heart" to "F3", "wigwag" to "F4",
            "startAndStopBots" to "=", "rotateLeft" to "a", "rotateRight" to "d")
    }
}
