package com.wyrm.omrajput.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * The notification choices owned by this device.
 *
 * Android owns the master permission; Wyrm owns the finer question of which
 * kinds the player wants. Keeping the two separate means switching all alerts
 * off in Android does not erase a carefully chosen set of categories when the
 * player later turns them back on.
 */
object NotificationPreferences {
    private const val PREFS = "wyrm_notification_preferences"

    val knownKinds: Set<String> = linkedSetOf(
        "dm",
        "invite",
        "voice_invite",
        "notice",
        "broadcast",
        "event",
        "update",
        "feature",
        "follow",
        "achievement",
        "rank",
        "backup",
    )

    /** A new kind starts enabled so an older client preference file cannot hide it forever. */
    @JvmStatic
    fun isKindEnabled(context: Context, kind: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(kind, true)

    fun enabledKinds(context: Context): Set<String> =
        knownKinds.filterTo(linkedSetOf()) { isKindEnabled(context, it) }

    fun setKindEnabled(context: Context, kind: String, enabled: Boolean) {
        if (kind !in knownKinds) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(kind, enabled)
            .apply()
    }

    /** The value shown by the master switch, including Android 13's runtime permission. */
    fun systemEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Android does not let an app revoke its own notification permission.
     * The master row therefore opens the one system surface that can truly
     * switch every channel on or off, and the screen re-reads it on resume.
     */
    fun openSystemSettings(context: Context) {
        val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }.getOrElse {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** The server spelling used by FCM and the typed notifications feed. */
fun NotificationKind.preferenceKey(): String = when (this) {
    NotificationKind.NOTICE -> "notice"
    NotificationKind.BROADCAST -> "broadcast"
    NotificationKind.EVENT -> "event"
    NotificationKind.UPDATE -> "update"
    NotificationKind.FEATURE -> "feature"
    NotificationKind.INVITE -> "invite"
    NotificationKind.VOICE_INVITE -> "voice_invite"
    NotificationKind.FOLLOW -> "follow"
    NotificationKind.ACHIEVEMENT -> "achievement"
    NotificationKind.RANK -> "rank"
    NotificationKind.BACKUP -> "backup"
}
