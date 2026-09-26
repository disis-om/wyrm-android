package com.wyrm.omrajput.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

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

    /** Enough at once to sweep a few hundred arenas quickly, few enough not to
     * look like a port scan to the phone's own network stack. */
    private const val PING_CONCURRENCY = 24
    private const val PING_TIMEOUT_MS = 1500

    /* A round trip uses the same port as the arena WebSocket. Closing the
     * coroutine does not interrupt Socket.connect on Dispatchers.IO, so every
     * socket is registered before it connects and closed by hand.
     *
     * Probes exist only while the arena picker is open: not in the lobby, not
     * in the background, never while Play owns the arena. Closing the picker
     * or pressing Play closes every probe still in flight. */
    private val probeLock = Any()
    private val activeProbes = mutableSetOf<Socket>()
    private var playOwnsArenaPort = false
    private var pickerOpen = false

    /* Measured on 2026-09-25: thirty bare TCP connects to one arena, one every
     * two seconds, and that arena reset every connection from the same public
     * IP for about a minute afterwards — the WebSocket never upgraded, so every
     * Play in that minute failed. A lobby that pinged its arena every two
     * seconds walked straight into that. So a round trip is remembered for a minute and an arena is never re-dialled
     * inside it, however often the picker is opened or refreshed. */
    private const val PROBE_REUSE_MS = 60_000L
    private val measured = HashMap<String, Pair<Long, Int>>()

    internal fun forgetMeasurements() {
        synchronized(probeLock) { measured.clear() }
    }

    private fun closeActiveProbes() {
        val probes = synchronized(probeLock) { activeProbes.toList() }
        probes.forEach { probe -> runCatching { probe.close() } }
    }

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
     * Round trip to the arena itself, in milliseconds.
     *
     * A TCP connection to the port the arena plays on, which is the same path
     * the game takes, so the number means what a player thinks it means. ICMP
     * would be cheaper and would measure something the game never uses.
     */
    suspend fun ping(arena: Arena): Int = withContext(Dispatchers.IO) {
        val socket = Socket()
        var reused: Int? = null
        val registered = synchronized(probeLock) {
            val last = measured[arena.endpoint]
            val now = System.nanoTime() / 1_000_000L
            if (last != null && now - last.first < PROBE_REUSE_MS) {
                reused = last.second
                false
            } else if (playOwnsArenaPort || !pickerOpen) false else {
                activeProbes.add(socket)
                true
            }
        }
        if (!registered) {
            runCatching { socket.close() }
            return@withContext reused ?: UNREACHABLE
        }
        val started = System.nanoTime()
        try {
            socket.use {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(arena.address, arena.port), PING_TIMEOUT_MS)
            }
            ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            UNREACHABLE
        }.also { value ->
            /* A probe closed by Play or by the picker closing measured nothing;
               only a completed dial counts against the arena. */
            synchronized(probeLock) {
                activeProbes.remove(socket)
                if (!playOwnsArenaPort && pickerOpen)
                    measured[arena.endpoint] = System.nanoTime() / 1_000_000L to value
            }
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
        arenas.map { arena ->
            async { gate.withPermit { onResult(arena.endpoint, ping(arena)) } }
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
