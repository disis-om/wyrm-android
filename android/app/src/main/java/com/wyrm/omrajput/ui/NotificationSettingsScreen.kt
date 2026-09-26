package com.wyrm.omrajput.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NotificationChoice(
    val kind: String,
    val title: String,
    val detail: String,
)

private data class NotificationGroup(
    val title: String,
    val rows: List<NotificationChoice>,
)

private val notificationGroups = listOf(
    NotificationGroup(
        "People",
        listOf(
            NotificationChoice("invite", "Arena invites", "Someone sends you a server and key."),
            NotificationChoice("dm", "Direct messages", "New thread or reply."),
            NotificationChoice("voice_invite", "Voice invitations", "Private invitations to verified voice rooms."),
            NotificationChoice("follow", "New followers", "When another player starts following you."),
        ),
    ),
    NotificationGroup(
        "Wyrm",
        listOf(
            NotificationChoice("notice", "Notices", "Maintenance, downtime and important alerts."),
            NotificationChoice("broadcast", "Broadcasts", "General announcements sent to everyone."),
            NotificationChoice("event", "Battledome events", "Scheduled events, start times and arena addresses."),
            NotificationChoice("update", "Updates", "New versions and their changelogs."),
            NotificationChoice("feature", "New features", "What has been added or changed inside Wyrm."),
        ),
    ),
    NotificationGroup(
        "You",
        listOf(
            NotificationChoice("achievement", "Achievements", "Personal bests and milestones after a run."),
            NotificationChoice("rank", "Rank changes", "Leaderboard movement after a finished run."),
            NotificationChoice("backup", "Backup receipts", "Local backup and restore results from this device."),
        ),
    ),
)

/** Spec page 14 — grouped by who is pinging you. Real kinds only. */
@Composable
fun NotificationSettingsScreen(
    masterEnabled: Boolean,
    enabledKinds: Set<String>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onKindChanged: (String, Boolean) -> Unit,
) {
    SettingsDrillScaffold(
        title = "Notifications",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsSectionLabel("Android", top = 16.dp)
        SettingsCard {
            SettingsBoolRow(
                title = "All notifications",
                detail = if (masterEnabled) {
                    "Allowed by Android. Tap to manage the master permission."
                } else {
                    "Off in Android. Tap here, then allow Wyrm notifications."
                },
                on = masterEnabled,
                first = true,
                onToggle = { onOpenSystemSettings() },
            )
        }

        notificationGroups.forEach { group ->
            SettingsSectionLabel(group.title)
            SettingsCard {
                group.rows.forEachIndexed { index, choice ->
                    NotificationPaperRow(
                        choice = choice,
                        enabled = masterEnabled,
                        checked = masterEnabled && choice.kind in enabledKinds,
                        first = index == 0,
                        onClick = { onKindChanged(choice.kind, choice.kind !in enabledKinds) },
                    )
                }
            }
        }
        SettingsCaption("Nothing here fires while you are inside an arena.")
    }
}

@Composable
private fun NotificationPaperRow(
    choice: NotificationChoice,
    enabled: Boolean,
    checked: Boolean,
    first: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.alpha(if (enabled) 1f else 0.46f)) {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = choice.title, fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                if (choice.detail.isNotBlank()) {
                    Text(
                        text = choice.detail,
                        fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            InkSwitch(on = checked, onToggle = { if (enabled) onClick() })
        }
    }
}
