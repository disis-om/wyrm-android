package com.wyrm.omrajput.data

/**
 * What shape a notification's extra content takes.
 *
 * A chat message, a follower and a maintenance notice are all "one line and a
 * timestamp"; a battledome event carries a schedule and a server to join; an
 * update carries a version. The kind decides which of [WyrmNotification]'s
 * optional fields the card actually reads.
 */
enum class NotificationKind {
    NOTICE, BROADCAST, EVENT, UPDATE, FEATURE,
    INVITE, VOICE_INVITE, FOLLOW, ACHIEVEMENT, RANK, BACKUP,
}

data class BackupCategory(val name: String, val ok: Boolean)

/**
 * One entry in the panel.
 *
 * Operator-written [body] values are CommonMark plus the supported GFM
 * extensions; generated social bodies stay plain. Kind-specific fields are
 * null unless [kind] is the one that owns them.
 */
data class WyrmNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String = "",
    val timestamp: String,
    /** Raw UTC value, used when local and server rows are merged. */
    val createdAt: String,
    val read: Boolean = false,
    // INVITE and FOLLOW: who this is about.
    val actorId: String? = null,
    val actorName: String? = null,
    // VOICE_INVITE: the recipient-bound credential remains on the backend.
    val voiceRoomId: String? = null,
    val voiceInviteId: String? = null,
    val expiresAt: String? = null,
    // EVENT: the battledome's own rundown.
    val startsAt: String? = null,
    val eventOrganizer: String? = null,
    val serverAddress: String? = null,
    // UPDATE: which build it points at.
    val version: String? = null,
    // ACHIEVEMENT / RANK, and BACKUP's device-only detail.
    val value: Long? = null,
    val previous: Long? = null,
    val categories: List<BackupCategory> = emptyList(),
)

/**
 * One typed backend row, converted into the fields its card owns.
 */
fun ServerNotification.toWyrmNotification(): WyrmNotification = WyrmNotification(
    id = id,
    kind = kindOf(kind),
    title = title,
    body = body,
    timestamp = relativeTime(createdAt),
    createdAt = createdAt,
    read = read,
    actorId = meta["actorId"],
    actorName = meta["actorName"],
    voiceRoomId = meta["roomId"],
    voiceInviteId = meta["inviteId"],
    expiresAt = meta["expiresAt"],
    startsAt = meta["startsAt"],
    eventOrganizer = meta["organizer"],
    serverAddress = meta["address"],
    version = meta["version"],
    value = meta["value"]?.toLongOrNull() ?: meta["rank"]?.toLongOrNull(),
    previous = meta["previous"]?.toLongOrNull(),
)

/** An unknown future kind still gets the plain announcement card. */
private fun kindOf(raw: String): NotificationKind = when (raw) {
    "notice" -> NotificationKind.NOTICE
    "broadcast" -> NotificationKind.BROADCAST
    "event" -> NotificationKind.EVENT
    "update" -> NotificationKind.UPDATE
    "feature" -> NotificationKind.FEATURE
    "invite" -> NotificationKind.INVITE
    "voice_invite" -> NotificationKind.VOICE_INVITE
    "follow" -> NotificationKind.FOLLOW
    "achievement" -> NotificationKind.ACHIEVEMENT
    "rank" -> NotificationKind.RANK
    "backup" -> NotificationKind.BACKUP
    else -> NotificationKind.BROADCAST
}

/** "2m ago", "3h ago", "Yesterday", or a date — the same clock every timestamp in the app reads off. */
fun relativeTime(iso: String): String = runCatching {
    val then = java.time.Instant.parse(iso)
    val minutes = java.time.Duration.between(then, java.time.Instant.now()).toMinutes()
    when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 48 -> "Yesterday"
        else -> java.time.ZonedDateTime.ofInstant(then, java.time.ZoneId.systemDefault())
            .let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)}" }
    }
}.getOrDefault("")

/** One local-time label from the event's one authoritative UTC instant. */
fun eventWhen(startsAt: String?): String {
    val instant = startsAt ?: return ""
    return runCatching {
    val value = java.time.Instant.parse(instant)
        .atZone(java.time.ZoneId.systemDefault())
    val weekday = value.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    val month = value.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    val clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(value)
    "$weekday ${value.dayOfMonth} $month, $clock"
    }.getOrDefault("")
}

/** Invalid or absent times never block a public arena. */
fun WyrmNotification.hasStarted(now: java.time.Instant = java.time.Instant.now()): Boolean {
    val instant = startsAt ?: return true
    return runCatching { !java.time.Instant.parse(instant).isAfter(now) }.getOrDefault(true)
}
