package com.wyrm.omrajput.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyrm.omrajput.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/*
 * Arena drop reports (OM, 2026-09-29), the same report shape as Wyrm iOS.
 *
 * The engine (`platform/android_home.c`, `android_home_arena_drop`) notices a
 * live snake being hung up on and hands up a snapshot: close code and reason,
 * life, score, ping, fps, how quiet the arena was, how often we dialled. Here
 * that gets the phone's side added: which network, whether it switched during
 * the match, and two quick probes (never to the arena: arenas penalise an IP
 * that connects too often). Then a card asks the player, or with "Always send"
 * the report goes on its own.
 *
 * No location, no Wi-Fi signal strength, no speed test.
 */

data class DropRecord(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    /** The drop keys of the report context; device keys are added on send. */
    val facts: Map<String, String>,
    /** The focused log, captured when the drop happened. */
    val logs: String,
    val hint: String,
    val sentence: String,
    /** "Arena 1234", or "Arena ip:port" for an arena the directory does not know. */
    val arenaLabel: String,
    val lifeSec: String,
)

object DropWatch {
    private const val PREFS = "wyrm_drop"
    private const val KEY_AUTO = "auto_send"
    private const val KEY_ASKED_AT = "asked_at"
    private const val KEY_AUTO_SENT = "auto_sent"
    private const val PROMPT_GAP_MS = 10 * 60_000L
    private const val AUTO_PER_HOUR = 6
    private const val PROBE_TIMEOUT_MS = 1500
    private const val LOG_LIMIT = 60_000

    /** The drop the card is asking about. A newer drop replaces it. */
    var prompt by mutableStateOf<DropRecord?>(null)
        private set
    var autoSend by mutableStateOf(false)
        private set
    var toast by mutableStateOf("")

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** The card that came up last, so a card taken down and put back is not held off. */
    private var askedId = ""

    /* The default network, watched while Wyrm is in front. Only the times it
       changed are kept; a drop counts the ones inside its own match. */
    private val lock = Any()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null
    private val changes = ArrayDeque<Long>()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun install(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        autoSend = prefs(app).getBoolean(KEY_AUTO, false)
    }

    fun applyAutoSend(on: Boolean) {
        autoSend = on
        appContext?.let { prefs(it).edit().putBoolean(KEY_AUTO, on).apply() }
    }

    /** On while the activity is resumed, off when it pauses. Never throws. */
    @JvmStatic
    fun watchNetwork(context: Context, on: Boolean) {
        val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        synchronized(lock) {
            if (on && callback == null) {
                val watch = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        synchronized(lock) {
                            val previous = lastNetwork
                            if (previous != null && previous != network) {
                                changes.addLast(SystemClock.elapsedRealtime())
                                while (changes.size > 40) changes.removeFirst()
                            }
                            lastNetwork = network
                        }
                    }
                }
                try {
                    manager.registerDefaultNetworkCallback(watch)
                    callback = watch
                } catch (failure: SecurityException) {
                    Log.w("Wyrm", "network watch unavailable: ${failure.message}")
                } catch (failure: RuntimeException) {
                    Log.w("Wyrm", "network watch unavailable: ${failure.message}")
                }
            } else if (!on) {
                val watch = callback ?: return
                callback = null
                // The first network after coming back is where we are, not a change.
                lastNetwork = null
                runCatching { manager.unregisterNetworkCallback(watch) }
            }
        }
    }

    private fun changesSince(since: Long): Int = synchronized(lock) { changes.count { it >= since } }

    /* ------------------------------------------------------------ the drop */

    /**
     * The engine's snapshot, UI thread. [arena] is the directory record for the
     * endpoint when the picker knows it.
     */
    fun onNativeDrop(packed: String, arena: Arena?, repository: WyrmRepository) {
        val context = appContext ?: return
        val native = parse(packed)
        val detectedAt = SystemClock.elapsedRealtime()
        val droppedAt = java.time.Instant.now().toString()
        val lifeSec = native["lifeSec"].orEmpty()
        val lifeMs = ((lifeSec.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        // Taken now, as things stood when the arena hung up.
        val network = networkFacts(context)
        scope.launch {
            try {
                val (internetMs, apiMs, logs) = coroutineProbe()
                // Counted after the probes: a switch that caused the drop often
                // lands a moment after the socket died.
                val switched = changesSince(detectedAt - lifeMs - 1_000)
                val facts = LinkedHashMap<String, String>()
                for (key in NATIVE_KEYS) facts[key] = native[key].orEmpty()
                facts["arenaId"] = arena?.id?.toString().orEmpty()
                facts["arenaCluster"] = arena?.cluster?.toString().orEmpty()
                facts["arenaPlayers"] = arena?.players?.toString().orEmpty()
                facts.putAll(network)
                facts["netChanges"] = switched.toString()
                facts["internetMs"] = internetMs
                facts["apiMs"] = apiMs
                val (hint, sentence) = hintFor(
                    internetMs = internetMs,
                    netChanges = switched,
                    connectsLastMin = native["connectsLastMin"]?.toIntOrNull() ?: 0,
                    lifeSec = lifeSec.toDoubleOrNull() ?: 0.0,
                )
                facts["hint"] = hint
                facts["droppedAt"] = droppedAt
                val label = "Arena ${arena?.id?.toString() ?: native["arena"].orEmpty()}".trim()
                val record = DropRecord(
                    facts = facts,
                    logs = logs,
                    hint = hint,
                    sentence = sentence,
                    arenaLabel = label,
                    lifeSec = lifeSec.ifBlank { "0" },
                )
                Log.i("Wyrm", "arena drop: hint $hint, net ${facts["netType"]}, internet $internetMs, api $apiMs")
                if (autoSend) {
                    if (takeAutoSlot(context)) {
                        send(repository, record, "") { ok -> if (ok) toast = "Drop report sent. Thank you." }
                    } else {
                        Log.i("Wyrm", "arena drop: auto-send limit reached, not sent")
                    }
                } else {
                    prompt = record
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                Log.w("Wyrm", "arena drop not recorded: ${failure.message}")
            }
        }
    }

    private suspend fun coroutineProbe(): Triple<String, String, String> {
        val apiHost = runCatching { Uri.parse(BuildConfig.WYRM_API_URL).host }.getOrNull()
            ?: "wyrm-api.77-245-76-86.sslip.io"
        val apiPort = runCatching { Uri.parse(BuildConfig.WYRM_API_URL).port }.getOrNull()?.takeIf { it > 0 } ?: 443
        val internet = scope.async(Dispatchers.IO) { probe("1.1.1.1", 443) }
        val api = scope.async(Dispatchers.IO) { probe(apiHost, apiPort) }
        // The log is read now, before anything later can push the drop out of it.
        val log = scope.async(Dispatchers.IO) { SupportContext.recentLog(limit = LOG_LIMIT) }
        // A blocking connect cannot be interrupted; waiting on it can.
        val internetMs = withTimeoutOrNull(PROBE_TIMEOUT_MS + 1_500L) { internet.await() } ?: "fail"
        val apiMs = withTimeoutOrNull(PROBE_TIMEOUT_MS + 1_500L) { api.await() } ?: "fail"
        val logs = withTimeoutOrNull(5_000L) { log.await() }.orEmpty()
        return Triple(internetMs, apiMs, logs)
    }

    /** TCP connect time in ms, or "fail". Background thread. */
    private fun probe(host: String, port: Int): String {
        val socket = Socket()
        return try {
            val address = InetAddress.getByName(host)
            val started = SystemClock.elapsedRealtime()
            socket.connect(InetSocketAddress(address, port), PROBE_TIMEOUT_MS)
            (SystemClock.elapsedRealtime() - started).toString()
        } catch (_: Exception) {
            "fail"
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun networkFacts(context: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = manager?.let { m -> runCatching { m.getNetworkCapabilities(m.activeNetwork) }.getOrNull() }
        if (manager == null || caps == null) {
            out["netType"] = "none"
            out["netValidated"] = "no"
            out["netMetered"] = ""
            out["netDownKbps"] = ""
            out["netUpKbps"] = ""
            return out
        }
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        // "Both available": on Wi-Fi with mobile data up alongside it.
        @Suppress("DEPRECATION")
        val otherCellular = !cellular && runCatching {
            manager.allNetworks.any { network ->
                manager.getNetworkCapabilities(network)?.let {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }
        }.getOrDefault(false)
        val base = when {
            wifi && (cellular || otherCellular) -> "wifi+cellular"
            wifi -> "wifi"
            cellular -> "cellular"
            ethernet -> "ethernet"
            vpn -> "vpn"
            else -> "other"
        }
        out["netType"] = if (vpn && base != "vpn") "$base+vpn" else base
        out["netValidated"] = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "yes" else "no"
        out["netMetered"] = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "no" else "yes"
        out["netDownKbps"] = caps.linkDownstreamBandwidthKbps.toString()
        out["netUpKbps"] = caps.linkUpstreamBandwidthKbps.toString()
        return out
    }

    /** The first reason that fits, and what the card says about it. Same order and copy as iOS. */
    internal fun hintFor(internetMs: String, netChanges: Int, connectsLastMin: Int, lifeSec: Double): Pair<String, String> = when {
        internetMs == "fail" -> "no_internet" to
            "Your internet dropped. Check Wi-Fi or mobile data and pick the arena again."
        netChanges > 0 -> "network_switch" to
            "Your connection switched during the match (Wi-Fi and mobile data). Stay on one network while playing."
        connectsLastMin >= 20 -> "ip_penalty" to
            "You joined many times in a minute, so the arena is resting you. Wait a minute and try once."
        lifeSec < 15 -> "same_wifi" to
            "Another slither app on the same Wi-Fi (on a PC or another phone) can make the arena drop you. Close it, or switch to mobile data."
        else -> "arena_closed" to
            "The arena closed the connection. Sending the report helps us find out why."
    }

    private val NATIVE_KEYS = listOf(
        "dropReason", "deathPacket", "dialToSpawnMs", "closeCode", "closeReason", "errorText", "lifeSec", "score", "length", "kills",
        "pingMs", "lagging", "fps", "lastPacketAgoMs", "connectsLastMin", "persona", "protocol", "arena",
    )

    /** `key=value` lines from the engine. */
    internal fun parse(packed: String): Map<String, String> = packed.lineSequence()
        .mapNotNull { line ->
            val split = line.indexOf('=')
            if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 1).trim().take(300)
        }
        .toMap()

    /* ------------------------------------------------------------ the card */

    /** At most one card per ten minutes; the one already up may always stay. */
    fun canAsk(record: DropRecord): Boolean {
        if (record.id == askedId) return true
        val context = appContext ?: return true
        val last = prefs(context).getLong(KEY_ASKED_AT, 0L)
        val now = System.currentTimeMillis()
        return last <= 0L || now < last || now - last >= PROMPT_GAP_MS
    }

    fun markAsked(record: DropRecord) {
        if (record.id == askedId) return
        askedId = record.id
        appContext?.let { prefs(it).edit().putLong(KEY_ASKED_AT, System.currentTimeMillis()).apply() }
    }

    fun dismissPrompt() { prompt = null }

    /** Six silent reports an hour, counted on this phone. */
    private fun takeAutoSlot(context: Context): Boolean {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val recent = prefs.getString(KEY_AUTO_SENT, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }
            .filter { it in (now - 3_600_000L)..now }
        if (recent.size >= AUTO_PER_HOUR) return false
        prefs.edit().putString(KEY_AUTO_SENT, (recent + now).joinToString(",")).apply()
        return true
    }

    fun send(repository: WyrmRepository, record: DropRecord, note: String, done: (Boolean) -> Unit) {
        val context = appContext ?: return done(false)
        scope.launch {
            val ok = try {
                val facts = SupportContext.current(context, "Arena") + record.facts
                repository.submitSupport("drop", note.trim(), facts, logs = record.logs)
                if (prompt?.id == record.id) prompt = null
                true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                Log.w("Wyrm", "drop report not sent: ${failure.message}")
                false
            }
            done(ok)
        }
    }
}
