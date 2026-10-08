package com.wyrm.omrajput.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlin.random.Random
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/**
 * One arena, exactly as the official directory describes it.
 *
 * [players] is the load the directory reports for that machine. The leading
 * byte of each record is deliberately not surfaced: it was read as a liveness
 * flag, and on a live directory it marks arenas with three hundred people
 * playing in them, so whatever it means it does not mean that. The ping is the
 * honest answer to the same question, and it is measured rather than claimed.
 */
data class Arena(
    val address: String,
    val port: Int,
    val players: Int,
    val id: Int,
    val cluster: Int,
) {
    val endpoint: String get() = "$address:$port"
}

/**
 * The live list of arenas.
 *
 * The directory is a single text file of rolling Caesar-shifted hexadecimal
 * nibbles — the format Slither has always published — so decoding it is a
 * dozen lines and there is no server of ours in the path. This used to be
 * decoded in C for a native screen that no longer exists; it lives here now,
 * beside the only screen that shows it.
 */
object ArenaDirectory {
    private const val DIRECTORY_URL = "https://slither.io/i80124.txt"
    private const val MAX_RESPONSE_BYTES = 128 * 1024

    /** Machines measured at once (the directory has about fifty). */
    private const val PING_CONCURRENCY = 12
    private const val PING_TIMEOUT_MS = 1500

    /*
     * The ping is the web client's own (OM, 2026-10-09): `ws://ip:80/ptc`,
     * one byte 112 ('p') out, the same byte back, three round trips, the
     * fastest kept (`game1107241958.js`, the `/ptc` sockets after `loadSos`).
     * It needs the page's Origin; without it the server refuses the upgrade.
     * Port 80, never the arena's game port, so a ping no longer counts towards
     * the game port's per-IP connect limit, and arenas whose game port ignores
     * a bare TCP dial still get a number. One machine (one IP) answers for
     * every arena on it. A custom address with no `/ptc` falls back to the old
     * TCP dial of its own port.
     *
     * The WebSocket is the minimum by hand on a plain socket (one upgrade, one
     * byte each way), so the file needs no library and Wyrm Desktop, which
     * shares it, compiles it as it is.
     *
     * Probes exist only while the arena picker is open: not in the lobby, not
     * in the background, never while Play owns the arena. Closing the picker
     * or pressing Play closes every probe still in flight. */
    private const val PTC_TIMEOUT_MS = 2_500L
    private const val PTC_ROUNDS = 3
    private const val PTC_ORIGIN = "https://slither.io"
    private val probeLock = Any()
    /** How to stop each probe in flight (a ptc socket or a TCP dial). */
    private val activeProbes = mutableSetOf<() -> Unit>()
    private var playOwnsArenaPort = false
    private var pickerOpen = false

    /* A round trip is remembered for a minute (per machine for listed arenas,
     * per address for custom ones) and not measured again inside it, however
     * often the picker is opened or refreshed. Kept from the TCP-probe days
     * (2026-09-25: thirty bare game-port connects a minute got the IP reset). */
    private const val PROBE_REUSE_MS = 60_000L
    private val measured = HashMap<String, Pair<Long, Int>>()

    internal fun forgetMeasurements() {
        synchronized(probeLock) { measured.clear() }
    }

    private fun closeActiveProbes() {
        val probes = synchronized(probeLock) { activeProbes.toList() }
        probes.forEach { stop -> runCatching { stop() } }
    }

    /** Registers a probe unless the picker is closed or Play owns the arena. */
    private fun register(stop: () -> Unit): Boolean = synchronized(probeLock) {
        if (playOwnsArenaPort || !pickerOpen) false else { activeProbes.add(stop); true }
    }

    private fun unregister(stop: () -> Unit) = synchronized(probeLock) { activeProbes.remove(stop) }

    fun openPicker() {
        synchronized(probeLock) { pickerOpen = true }
    }

    fun closePicker() {
        synchronized(probeLock) { pickerOpen = false }
        closeActiveProbes()
    }

    fun beginArenaPlay() {
        synchronized(probeLock) { playOwnsArenaPort = true }
        closeActiveProbes()
    }

    fun endArenaPlay() {
        synchronized(probeLock) { playOwnsArenaPort = false }
    }

    /*
     * The country of each arena machine (OM, 2026-10-09: its flag and "IN"
     * beside an arena), asked the way NTL asks it: NTL's own service,
     * `https://ntl-slither.com/ss/flags.php?ips=a,b,...` (at most 60 a call),
     * answering `{"ok":true,"flags":{"ip":"in"}}` (NTL's `I0` also takes
     * "in.png" or a path ending in it). Kept by IPv4, lower case, as Compose
     * state so every arena row fills in when it lands; a machine it does not
     * know shows no flag. Asked again only for machines still unknown, at most
     * every five minutes.
     */
    val countries = mutableStateMapOf<String, String>()
    private const val FLAGS_URL = "https://ntl-slither.com/ss/flags.php"
    private const val FLAGS_PER_CALL = 60
    private const val COUNTRIES_EVERY_MS = 5 * 60_000L
    @Volatile private var countriesAt = 0L
    private val COUNTRY = Regex("""(?:^|/)([a-z]{2})(?:\.png)?$""")

    suspend fun loadCountries(addresses: Collection<String>) {
        val missing = addresses.filter { isValidEndpoint("$it:1") && it !in countries }.distinct()
        val now = System.currentTimeMillis()
        if (missing.isEmpty() || now - countriesAt < COUNTRIES_EVERY_MS) return
        countriesAt = now
        val found = withContext(Dispatchers.IO) {
            buildMap {
                missing.chunked(FLAGS_PER_CALL).forEach { chunk ->
                    runCatching {
                        val query = URLEncoder.encode(chunk.joinToString(","), "UTF-8")
                        val connection = (URL("$FLAGS_URL?ips=$query").openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8_000
                            readTimeout = 12_000
                            setRequestProperty("Accept", "application/json")
                        }
                        try {
                            check(connection.responseCode == HttpURLConnection.HTTP_OK)
                            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                            if (body.optBoolean("ok")) {
                                val flags = body.optJSONObject("flags") ?: JSONObject()
                                flags.keys().forEach { ip ->
                                    COUNTRY.find(flags.optString(ip).trim().lowercase())?.let { put(ip, it.groupValues[1]) }
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
            }
        }
        if (found.isEmpty()) countriesAt = 0L
        withContext(Dispatchers.Main) { countries.putAll(found) }
    }

    /** The upper-case country code of the machine at [address], or "". */
    fun countryOf(address: String): String = countries[address]?.uppercase().orEmpty()

    /** A crisp flag picture for a country code (flagcdn, 160 px wide PNG). */
    fun flagUrl(country: String): String = "https://flagcdn.com/w160/${country.lowercase()}.png"

    /** A ping that never came back. Sorted last, drawn as a dash. */
    const val UNREACHABLE = -1

    /** The native client only dials numeric IPv4 arenas. Keep invalid manual
     * input out of both its settings file and the entry state machine. */
    fun isValidEndpoint(endpoint: String): Boolean {
        val pieces = endpoint.split(':')
        if (pieces.size != 2) return false
        val port = pieces[1].toIntOrNull() ?: return false
        val octets = pieces[0].split('.')
        return port in 1..65535 && octets.size == 4 &&
            octets.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }

    /**
     * Pull an `ip:port` out of whatever was pasted: a bare address, a room
     * invite line, or a longer chat dump. Returns null when nothing in the
     * text is a numeric arena the native client can dial.
     */
    fun extractEndpoint(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (isValidEndpoint(trimmed)) return trimmed
        val match = ENDPOINT_IN_TEXT.find(trimmed) ?: return null
        val candidate = "${match.groupValues[1]}:${match.groupValues[2]}"
        return candidate.takeIf(::isValidEndpoint)
    }

    private val ENDPOINT_IN_TEXT = Regex("""(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})""")

    suspend fun load(): List<Arena> = withContext(Dispatchers.IO) {
        val connection = (URL(DIRECTORY_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "text/plain")
        }
        val payload = try {
            val status = connection.responseCode
            check(status == HttpURLConnection.HTTP_OK) { "The arena directory returned HTTP $status." }
            ByteArrayOutputStream(8192).use { sink ->
                connection.inputStream.use { source ->
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        total += read
                        check(total <= MAX_RESPONSE_BYTES) { "The arena directory is unexpectedly large." }
                        sink.write(buffer, 0, read)
                    }
                }
                sink.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
        parse(payload)
    }

    /**
     * Round trip to the machine an arena runs on, in milliseconds: the web
     * client's `/ptc` ping (see above). [UNREACHABLE] when nothing came back.
     */
    suspend fun ping(arena: Arena): Int = withContext(Dispatchers.IO) {
        val key = if (arena.id < 0) arena.endpoint else arena.address
        synchronized(probeLock) {
            val last = measured[key]
            val now = System.nanoTime() / 1_000_000L
            if (last != null && now - last.first < PROBE_REUSE_MS) return@withContext last.second
            if (playOwnsArenaPort || !pickerOpen) return@withContext UNREACHABLE
        }
        var value = ptc(arena.address)
        if (value == UNREACHABLE && arena.id < 0) value = tcpProbe(arena)
        /* A probe closed by Play or by the picker closing measured nothing;
           only a completed one counts. */
        synchronized(probeLock) {
            if (!playOwnsArenaPort && pickerOpen)
                measured[key] = System.nanoTime() / 1_000_000L to value
        }
        value
    }

    /** Three `p` round trips over `ws://address:80/ptc`; the fastest, or UNREACHABLE. */
    private fun ptc(address: String): Int {
        val socket = Socket()
        val stop: () -> Unit = { runCatching { socket.close() } }
        if (!register(stop)) {
            runCatching { socket.close() }
            return UNREACHABLE
        }
        val times = ArrayList<Long>(PTC_ROUNDS)
        val deadline = System.nanoTime() + PTC_TIMEOUT_MS * 1_000_000L
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = PTC_TIMEOUT_MS.toInt()
            socket.connect(InetSocketAddress(address, 80), PING_TIMEOUT_MS)
            val output = socket.getOutputStream()
            val input = BufferedInputStream(socket.getInputStream())
            val key = Base64.getEncoder().encodeToString(Random.nextBytes(16))
            output.write(
                ("GET /ptc HTTP/1.1\r\nHost: $address\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Key: $key\r\n" +
                    "Sec-WebSocket-Version: 13\r\nOrigin: $PTC_ORIGIN\r\n\r\n").toByteArray(Charsets.US_ASCII),
            )
            output.flush()
            if (!readUpgrade(input)) return UNREACHABLE
            while (times.size < PTC_ROUNDS && System.nanoTime() < deadline) {
                // One masked binary frame carrying 'p', as a browser sends it.
                val mask = Random.nextBytes(4)
                val sent = System.nanoTime()
                output.write(byteArrayOf(0x82.toByte(), 0x81.toByte(), mask[0], mask[1], mask[2], mask[3],
                    (112 xor mask[0].toInt()).toByte()))
                output.flush()
                if (!readPong(input)) break
                times += (System.nanoTime() - sent) / 1_000_000L
            }
            runCatching {
                val mask = Random.nextBytes(4)
                output.write(byteArrayOf(0x88.toByte(), 0x80.toByte(), mask[0], mask[1], mask[2], mask[3]))
                output.flush()
            }
        } catch (_: Exception) {
            // A broken socket keeps whatever round trips it already measured.
        } finally {
            unregister(stop)
            runCatching { socket.close() }
        }
        return times.minOrNull()?.toInt()?.coerceAtLeast(1) ?: UNREACHABLE
    }

    /** The server's answer to the upgrade: true for `101 Switching Protocols`. */
    private fun readUpgrade(input: InputStream): Boolean {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte < 0 || head.length > 4096) return false
            head.append(byte.toChar())
        }
        return head.startsWith("HTTP/1.1 101") || head.startsWith("HTTP/1.0 101")
    }

    /** Reads frames until the server's one-byte 'p' (true) or a close (false). */
    private fun readPong(input: InputStream): Boolean {
        while (true) {
            val first = input.read()
            val second = input.read()
            if (first < 0 || second < 0) return false
            var length = (second and 0x7F).toLong()
            if (length == 126L) length = ((input.read() shl 8) or input.read()).toLong()
            else if (length == 127L) { length = 0; repeat(8) { length = (length shl 8) or input.read().toLong() } }
            val mask = if (second and 0x80 != 0) ByteArray(4) { input.read().toByte() } else null
            if (length > 64) return false
            val payload = ByteArray(length.toInt()) { input.read().toByte() }
            if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (first and 0x0F) {
                0x8 -> return false
                0x2, 0x1 -> if (payload.size == 1 && payload[0] == 112.toByte()) return true
            }
        }
    }

    /** The pre-2026-10-09 probe: a TCP dial of the address's own port (custom arenas only). */
    private fun tcpProbe(arena: Arena): Int {
        val socket = Socket()
        val stop: () -> Unit = { runCatching { socket.close() } }
        if (!register(stop)) {
            runCatching { socket.close() }
            return UNREACHABLE
        }
        val started = System.nanoTime()
        return try {
            socket.use {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(arena.address, arena.port), PING_TIMEOUT_MS)
            }
            ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            UNREACHABLE
        } finally {
            unregister(stop)
        }
    }

    /**
     * Pings every arena, reporting each result the moment it lands.
     *
     * In list order, so the arenas the player is already looking at fill in
     * first. Cancelling the caller's scope cancels the sweep.
     */
    suspend fun pingAll(arenas: List<Arena>, onResult: (String, Int) -> Unit) = coroutineScope {
        val gate = Semaphore(PING_CONCURRENCY)
        // One ping per machine; every arena on it gets the same number.
        arenas.groupBy { if (it.id < 0) it.endpoint else it.address }.values.map { group ->
            async {
                gate.withPermit {
                    val value = ping(group.first())
                    group.forEach { onResult(it.endpoint, value) }
                }
            }
        }.forEach { it.await() }
    }

    /**
     * Decodes the directory.
     *
     * The first character is a format marker; every pair after it is one byte,
     * written as two nibbles that have each been shifted a further seven places
     * through the alphabet. Records are 28 bytes in the current format and 11
     * in the older one, and the file says which by its length alone.
     */
    fun parse(payload: ByteArray): List<Arena> {
        var end = payload.size
        while (end > 0 && payload[end - 1].toInt().toChar().isWhitespace()) end--
        require(end >= 3) { "The arena directory came back empty." }

        val nibbles = end - 1
        require(nibbles % 2 == 0) { "The arena directory is truncated." }
        val decoded = ByteArray(nibbles / 2)
        var shift = 0
        for (index in 0 until nibbles) {
            var nibble = ((payload[index + 1].toInt() and 0xFF) - 'a'.code - shift) % 26
            if (nibble < 0) nibble += 26
            require(nibble <= 15) { "The arena directory could not be read." }
            if (index % 2 == 0) decoded[index / 2] = (nibble shl 4).toByte()
            else decoded[index / 2] = (decoded[index / 2].toInt() or nibble).toByte()
            shift = (shift + 7) % 26
        }

        val modern = decoded.isNotEmpty() && decoded.size % 28 == 0
        val recordSize = when {
            modern -> 28
            decoded.isNotEmpty() && decoded.size % 11 == 0 -> 11
            else -> throw IllegalStateException("The arena directory is in an unknown format.")
        }

        val arenas = ArrayList<Arena>(decoded.size / recordSize)
        var offset = 0
        while (offset + recordSize <= decoded.size) {
            val record = decoded.copyOfRange(offset, offset + recordSize)
            fun byte(at: Int) = record[at].toInt() and 0xFF
            val ipAt = if (modern) 1 else 0
            val port: Int
            val players: Int
            val cluster: Int
            val id: Int
            if (modern) {
                port = (byte(21) shl 8) or byte(22)
                players = (byte(23) shl 8) or byte(24)
                cluster = byte(25)
                id = (byte(26) shl 8) or byte(27)
            } else {
                port = (byte(4) shl 16) or (byte(5) shl 8) or byte(6)
                players = (byte(7) shl 16) or (byte(8) shl 8) or byte(9)
                cluster = byte(10)
                id = offset / recordSize + 1
            }
            val quads = (0 until 4).map { byte(ipAt + it) }
            if (port in 1..65535 && quads.any { it != 0 }) {
                arenas += Arena(
                    address = quads.joinToString("."),
                    port = port,
                    players = players.coerceIn(0, 65535),
                    id = id,
                    cluster = cluster,
                )
            }
            offset += recordSize
        }
        check(arenas.isNotEmpty()) { "The directory listed no usable arenas." }
        return arenas
    }

    /** True if the typed text names this arena in any way it can be named. */
    fun matches(arena: Arena, query: String): Boolean {
        if (query.isBlank()) return true
        val term = query.trim().lowercase()
        return arena.endpoint.contains(term) ||
            "server ${arena.id}".contains(term) ||
            "cluster ${arena.cluster}".contains(term) ||
            arena.id.toString() == term
    }
}
