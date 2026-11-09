package com.wyrm.omrajput.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wyrm.omrajput.R
import com.wyrm.omrajput.WyrmActivity
import com.wyrm.omrajput.WyrmMessagingService
import org.json.JSONArray
import org.json.JSONObject

/*
 * Events (OM, 2026-10-30): the event servers' tournaments, as Wyrm's backend
 * serves them (`/v1/events`, `/v1/events/:id`, backend/src/events.mjs). The
 * backend keeps the organisers' feed in memory only and adds the Wyrm players
 * of each event, the arena's live board and, signed in, friends.
 *
 * The app picks each event's look from the feed's own type words, so a new
 * wording ("Last Duo Standing (Kickout)") still lands on the right card; a
 * type Wyrm has no card for keeps its own name on the universal card.
 */

enum class EventKind { LAST_ONE_STANDING, TEAM_LAST_STANDING, LAST_DUO_STANDING, PAINTBALL, BATTLEDOME, OTHER }

fun eventKindOf(type: String?): EventKind {
    val t = type.orEmpty().uppercase()
    return when {
        "DUO" in t -> EventKind.LAST_DUO_STANDING
        "PAINT" in t -> EventKind.PAINTBALL
        "TEAM" in t -> EventKind.TEAM_LAST_STANDING
        "BATTLEDOME" in t || "DOME" in t -> EventKind.BATTLEDOME
        "STANDING" in t || "KICKOUT" in t -> EventKind.LAST_ONE_STANDING
        else -> EventKind.OTHER
    }
}

/** The type in words: our name for the five we know, the feed's own otherwise. */
fun eventKindName(kind: EventKind, type: String?): String = when (kind) {
    EventKind.LAST_ONE_STANDING -> "Last One Standing"
    EventKind.TEAM_LAST_STANDING -> "Last Team Standing"
    EventKind.LAST_DUO_STANDING -> "Last Duo Standing"
    EventKind.PAINTBALL -> "Paintball"
    EventKind.BATTLEDOME -> "Battledome"
    EventKind.OTHER -> type?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", true) }
        ?.lowercase()?.split(Regex("(?<=[\\s(\\-/])|(?=[\\s(\\-/])"))
        ?.joinToString("") { part -> part.replaceFirstChar { it.titlecase() } }
        ?: "Community Event"
}

fun eventKindBlurb(kind: EventKind): String = when (kind) {
    EventKind.LAST_ONE_STANDING -> "Every snake for itself. Players are knocked out until one is left."
    EventKind.TEAM_LAST_STANDING -> "Play with your team. The last team with a snake alive wins."
    EventKind.LAST_DUO_STANDING -> "Play in pairs. The last duo with a snake alive wins."
    EventKind.PAINTBALL -> "A paint fight in the arena. The organisers' message has the rules."
    EventKind.BATTLEDOME -> "A closed dome that shrinks. Stay inside and outlast everyone."
    EventKind.OTHER -> "A community event on this server. The organisers' message has the details."
}

data class EventRegistration(
    /** true = sign up on Discord first, false = no sign-up, null = the feed does not say. */
    val discord: Boolean?,
    /** "channels" (sign up in their channel) or "verified" (verified players are in), or null. */
    val mode: String?,
    val autoAccept: Boolean?,
    val url: String?,
)

data class WyrmEvent(
    val id: String,
    val eventNumber: Int?,
    /** "upcoming", "live" or "finished". */
    val status: String,
    val type: String?,
    val kind: EventKind,
    val serverCode: String,
    val flag: String,
    val region: String,
    /** "ip:port", never with http. */
    val address: String,
    val startsAt: String?,
    val startedAt: String?,
    val endedAt: String?,
    val durationSeconds: Long?,
    val players: Int?,
    val prizeUsd: Double?,
    val roundMinutes: Int?,
    val teamNames: Boolean?,
    val reconstructed: Boolean,
    val registration: EventRegistration,
    val title: String?,
    val prizeText: String?,
    val markdown: String?,
    val rules: List<String>,
    /** Wyrm players in it; null before it starts. */
    val wyrmPlayers: Int?,
) {
    val name: String get() = eventKindName(kind, type)
    /** The organisers' server code without its port suffix ("AE_443" -> "AE"). */
    val serverLabel: String get() = serverCode.substringBefore('_')
    val startsAtMs: Long get() = parseInstant(startsAt)
    val startedAtMs: Long get() = parseInstant(startedAt)
    val endedAtMs: Long get() = parseInstant(endedAt)
    val needsSignUp: Boolean get() = registration.discord == true
}

data class EventPlayer(
    val playerId: String,
    val name: String,
    val avatarUrl: String,
    val avatarKey: String,
    val bestScore: Long?,
    val kills: Long?,
    val runs: Int,
    val playingNow: Boolean,
)

data class EventLiveBoard(
    val players: Int?,
    val leaderboard: List<Pair<String, Long>>,
    val minimap: ArenaMinimap?,
)

data class EventDetail(
    val event: WyrmEvent,
    val live: EventLiveBoard?,
    val wyrmCount: Int,
    val wyrm: List<EventPlayer>,
    val friends: List<EventPlayer>,
)

data class EventsPage(
    val upcoming: List<WyrmEvent> = emptyList(),
    val live: List<WyrmEvent> = emptyList(),
    val finished: List<WyrmEvent> = emptyList(),
    val nextCursor: Int? = null,
    val totalFinished: Int = 0,
    val stale: Boolean = false,
) {
    /** The one event Home shows: live first, then the next to start. */
    val featured: WyrmEvent? get() = live.firstOrNull() ?: upcoming.firstOrNull()
}

private fun parseInstant(iso: String?): Long =
    iso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

private fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
private fun JSONObject.intOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.longOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
private fun JSONObject.boolOrNull(name: String): Boolean? = if (has(name) && !isNull(name)) optBoolean(name) else null

internal fun JSONObject.toWyrmEvent(): WyrmEvent {
    val server = optJSONObject("server") ?: JSONObject()
    val prize = optJSONObject("prize")
    val reg = optJSONObject("registration") ?: JSONObject()
    val description = optJSONObject("description")
    val raw = optJSONObject("raw")
    val type = str("type")
    val rules = description?.optJSONArray("rules")
    return WyrmEvent(
        id = optString("id"),
        eventNumber = intOrNull("eventNumber"),
        status = optString("status", "finished"),
        type = type,
        kind = eventKindOf(type),
        serverCode = server.str("code").orEmpty(),
        flag = server.str("flag").orEmpty(),
        region = server.str("region").orEmpty(),
        address = server.str("address").orEmpty(),
        startsAt = str("startsAt"),
        startedAt = str("startedAt"),
        endedAt = str("endedAt"),
        durationSeconds = longOrNull("durationSeconds"),
        players = intOrNull("players"),
        // The server sends the prize in dollars too (OM: show it in dollars).
        prizeUsd = raw?.takeIf { it.has("prize_usd") && !it.isNull("prize_usd") }?.optDouble("prize_usd")
            ?: prize?.takeIf { it.has("usd") && !it.isNull("usd") }?.optDouble("usd")
            ?: prize?.optDouble("amount")?.takeIf { prize.optString("currency") == "USD" },
        roundMinutes = intOrNull("roundMinutes") ?: raw?.intOrNull("round_min"),
        teamNames = boolOrNull("teamNames"),
        reconstructed = optBoolean("reconstructed"),
        registration = EventRegistration(
            discord = reg.boolOrNull("discord"),
            mode = reg.str("mode"),
            autoAccept = reg.boolOrNull("autoAccept"),
            url = reg.str("url"),
        ),
        title = description?.str("title"),
        prizeText = description?.str("prizeText"),
        markdown = description?.str("markdown"),
        rules = (0 until (rules?.length() ?: 0)).mapNotNull { rules?.optString(it)?.takeIf(String::isNotBlank) },
        wyrmPlayers = intOrNull("wyrmPlayers"),
    )
}

private fun JSONArray?.events(): List<WyrmEvent> =
    (0 until (this?.length() ?: 0)).mapNotNull { index -> this?.optJSONObject(index)?.toWyrmEvent()?.takeIf { it.id.isNotBlank() && it.address.isNotBlank() } }

internal fun JSONObject.toEventsPage(): EventsPage = EventsPage(
    upcoming = optJSONArray("upcoming").events(),
    live = optJSONArray("live").events(),
    finished = optJSONArray("finished").events(),
    nextCursor = if (isNull("nextCursor")) null else optInt("nextCursor"),
    totalFinished = optInt("totalFinished"),
    stale = optBoolean("stale"),
)

private fun JSONObject.toEventPlayer(api: String): EventPlayer {
    val url = optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" }
    return EventPlayer(
        playerId = optString("playerId"),
        name = optString("name").ifBlank { optString("username") },
        avatarUrl = when {
            url == null -> ""
            url.startsWith("http") -> url
            else -> api + url
        },
        avatarKey = optString("avatarKey", "mono-ink"),
        bestScore = longOrNull("bestScore"),
        kills = longOrNull("kills"),
        runs = optInt("runs"),
        playingNow = optBoolean("playingNow"),
    )
}

internal fun JSONObject.toEventDetail(api: String): EventDetail {
    val wyrm = optJSONObject("wyrm")
    val players = wyrm?.optJSONArray("players")
    val friends = optJSONArray("friends")
    val live = optJSONObject("live")
    return EventDetail(
        event = getJSONObject("event").toWyrmEvent(),
        live = live?.let { board ->
            val rows = board.optJSONArray("leaderboard")
            EventLiveBoard(
                players = board.intOrNull("players"),
                leaderboard = (0 until (rows?.length() ?: 0)).mapNotNull { i ->
                    rows?.optJSONObject(i)?.let { it.optString("name") to it.optLong("score") }
                },
                minimap = board.str("minimap")?.let(ArenaMinimap::parse),
            )
        },
        wyrmCount = wyrm?.optInt("count") ?: 0,
        wyrm = (0 until (players?.length() ?: 0)).mapNotNull { players?.optJSONObject(it)?.toEventPlayer(api) },
        friends = (0 until (friends?.length() ?: 0)).mapNotNull { friends?.optJSONObject(it)?.toEventPlayer(api) },
    )
}

/* ---------------------------------------------------------------- arena monitor */

/**
 * The monitor's minimap (backend arena-status.mjs `mapSvgDataUrl`): an SVG of
 * `size` x `size` cells, each lit cell a small square in one of three tones.
 * Read straight into cells so the app draws it itself, no SVG library.
 */
class ArenaMinimap(val size: Int, val cells: List<IntArray>, val bitmap: android.graphics.Bitmap? = null) {
    companion object {
        private val VIEW = Regex("viewBox=\"0 0 (\\d+) (\\d+)\"")
        private val PATH = Regex("<path d=\"([^\"]*)\"")
        private val CELL = Regex("M([\\d.]+) ([\\d.]+)h")
        fun parse(dataUrl: String): ArenaMinimap? = runCatching {
            // Most arenas now send a PNG (white dots on clear, 2026-10-31): drawn as it is.
            if (dataUrl.startsWith("data:image/") && !dataUrl.startsWith("data:image/svg")) {
                val bytes = android.util.Base64.decode(dataUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                val image = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                return ArenaMinimap(image.width, emptyList(), image)
            }
            val svg = if (dataUrl.startsWith("data:")) {
                String(android.util.Base64.decode(dataUrl.substringAfter("base64,"), android.util.Base64.DEFAULT), Charsets.UTF_8)
            } else dataUrl
            val size = VIEW.find(svg)?.groupValues?.get(1)?.toInt() ?: return null
            val tones = PATH.findAll(svg).take(3).map { path ->
                CELL.findAll(path.groupValues[1]).map { m ->
                    val x = m.groupValues[1].toFloat().toInt()
                    val y = m.groupValues[2].toFloat().toInt()
                    y * size + x
                }.toList().toIntArray()
            }.toList()
            ArenaMinimap(size, tones)
        }.getOrNull()
    }
}

/** What the monitor knows about one arena right now. */
data class ArenaLive(
    val players: Int?,
    val country: String,
    val top: List<Pair<String, Long>>,
    val minimap: ArenaMinimap?,
    val updated: String,
    /** "ip:port" (IPv4 when it has one), the arena code (null for a custom
     *  arena) and whether NTL lists it as custom (2026-11-07: the picker shows
     *  the arenas NTL knows that slither's directory does not). */
    val endpoint: String = "",
    val code: Int? = null,
    val custom: Boolean = false,
)

internal fun JSONObject.toArenaMonitor(): Map<String, ArenaLive> {
    val servers = optJSONArray("servers") ?: return emptyMap()
    val out = HashMap<String, ArenaLive>()
    for (i in 0 until servers.length()) {
        val s = servers.optJSONObject(i) ?: continue
        val rows = s.optJSONArray("leaderboard")
        val live = ArenaLive(
            players = s.intOrNull("playerCount"),
            country = s.optString("country"),
            top = (0 until (rows?.length() ?: 0)).mapNotNull { r -> rows?.optJSONObject(r)?.let { it.optString("name") to it.optLong("score") } }.take(10),
            minimap = s.optJSONObject("preview")?.str("dataUrl")?.let(ArenaMinimap::parse),
            updated = s.optString("updatedLabel"),
            endpoint = s.str("ipv4Address") ?: s.str("address").orEmpty(),
            code = s.intOrNull("id"),
            custom = s.optBoolean("custom", false),
        )
        s.str("ipv4Address")?.let { out[it] = live }
        s.str("address")?.let { out[it] = live }
    }
    return out
}

/* ---------------------------------------------------------------- reminders */

/**
 * "Remind me" on an upcoming event (OM, 2026-10-30): the phone itself posts
 * an alert 30 and 10 minutes before the start. Nothing goes to the server.
 * Kept on this phone only (prefs `wyrm_event_reminders`, wiped on log out;
 * AccountSync WIPE): which events, not a setting.
 */
object EventReminders {
    private const val PREFS = "wyrm_event_reminders"
    private val LEADS_MIN = intArrayOf(30, 10)

    fun isOn(context: Context, id: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(id)

    fun toggle(context: Context, event: WyrmEvent): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(event.id)) {
            cancel(context, event.id)
            prefs.edit().remove(event.id).apply()
            false
        } else {
            schedule(context, event)
            prefs.edit().putLong(event.id, event.startsAtMs).apply()
            true
        }
    }

    private fun intent(context: Context, event: WyrmEvent?, id: String, lead: Int): PendingIntent {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("lead", lead)
            event?.let {
                putExtra("name", it.name)
                putExtra("server", it.region.ifBlank { it.serverLabel })
                putExtra("prize", it.prizeUsd?.takeIf { p -> p > 0 }?.let { p -> "$" + if (p % 1.0 == 0.0) p.toLong().toString() else p.toString() } ?: "")
            }
        }
        return PendingIntent.getBroadcast(context, (id.hashCode() * 31 + lead), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun schedule(context: Context, event: WyrmEvent) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        for (lead in LEADS_MIN) {
            val at = event.startsAtMs - lead * 60_000L
            if (event.startsAtMs <= 0 || at <= now) continue
            // Inexact (no exact-alarm permission); Android may move it a little.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context, event, event.id, lead))
        }
    }

    private fun cancel(context: Context, id: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        for (lead in LEADS_MIN) alarms.cancel(intent(context, null, id, lead))
    }

    /** Log out: every reminder goes with the account. */
    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.all.keys.forEach { cancel(context, it) }
        prefs.edit().clear().apply()
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val lead = intent.getIntExtra("lead", 10)
        val name = intent.getStringExtra("name") ?: "Event"
        val server = intent.getStringExtra("server").orEmpty()
        val prize = intent.getStringExtra("prize").orEmpty()
        WyrmMessagingService.ensureChannels(context)
        val open = Intent(context, WyrmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("wyrm.kind", "wyrm_event")
            putExtra("wyrm.id", id)
        }
        val pending = PendingIntent.getActivity(context, id.hashCode() * 31 + lead, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = buildString {
            append(name).append(" starts in ").append(lead).append(" minutes")
            if (server.isNotBlank()) append(" on the ").append(server).append(" server")
            if (prize.isNotBlank()) append(" · ").append(prize).append(" prize")
            append(". Tap to join.")
        }
        val notification = NotificationCompat.Builder(context, WyrmMessagingService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(id.hashCode() * 31 + lead, notification)
        if (lead == 10) context.getSharedPreferences("wyrm_event_reminders", Context.MODE_PRIVATE).edit().remove(id).apply()
    }
}
