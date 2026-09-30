package com.wyrm.omrajput.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.UpdateState

enum class BackupAction { CREATE, CHECK, RESTORE, CHOOSE_FOLDER }

/**
 * Settings › Updates & version. Until 2026-10-01 this page also held manual
 * `.wyrm` backups; settings now live in the account (data/AccountSync.kt), so
 * only the signed updater, the beta switch and the version remain.
 */
@Composable
fun BackupScreen(
    update: UpdateState = UpdateState(),
    installedVersion: String = "",
    settingsVersion: String = "",
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String = "Settings",
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    betaUpdates: Boolean = false,
    onBetaUpdates: (Boolean) -> Unit = {},
    onInstall: () -> Unit = {},
    onOpenInstalledNotes: () -> Unit = {},
    onOpenAvailableNotes: () -> Unit = {},
    onResetAll: (() -> Unit)? = null,
) {
    val updateLabel = when {
        update.busy -> update.title.ifBlank { "Checking…" }
        update.available -> "Update available"
        update.failed -> "Check failed"
        update.title.isNotBlank() -> update.title
        else -> "Up to date"
    }
    var confirmingReset by remember { mutableStateOf(false) }

    SettingsDrillScaffold(
        title = "Updates",
        parent = backLabel,
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        // Clear of the header rule, as on iOS.
        Spacer(Modifier.height(18.dp))
        // An update that is ready, or on its way in, gets its own card and a
        // real button rather than only a row label.
        val updating = update.busy && update.status != 1
        if (update.available || updating) {
            SettingsSectionLabel("Update")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (update.version.isNotBlank()) "Wyrm ${update.version} is ready" else "A new Wyrm is ready",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = Wyrm.Ink,
                    )
                    if (update.detail.isNotBlank()) {
                        Text(
                            text = update.detail,
                            fontFamily = Wyrm.Body,
                            fontSize = 13.sp,
                            color = Wyrm.Quiet,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    PaperPrimaryButton(
                        label = if (updating) update.title.ifBlank { "Updating…" } else "Update now",
                        enabled = !updating,
                        onClick = onInstall,
                    )
                }
            }
        }

        SettingsSectionLabel("Version")
        SettingsCard {
            SettingsValueRow("Wyrm", installedVersion.ifBlank { "—" }, first = true)
            SettingsLinkRow(
                title = "Check for updates",
                value = updateLabel,
                first = false,
                onClick = if (update.available) onInstall else onCheckUpdate,
            )
            // Also where the beta prompt's "turn them off" shortcut lands.
            Box(Modifier.settingAnchor("app.beta-updates")) {
                SettingsBoolRow(
                    title = "Beta updates",
                    detail = "Get early builds before everyone else. They can have rough edges or bugs; turn this off to get stable updates only.",
                    on = betaUpdates,
                    first = false,
                    onToggle = onBetaUpdates,
                )
            }
            if (settingsVersion.isNotBlank()) {
                SettingsValueRow("Settings format", "v$settingsVersion", first = false)
            }
            SettingsLinkRow(
                title = "What's in this build",
                value = "",
                first = false,
                onClick = onOpenInstalledNotes,
            )
            if (update.available && update.version.isNotBlank() && update.version != installedVersion) {
                SettingsLinkRow(
                    title = "What's new in ${update.version}",
                    value = "",
                    first = false,
                    onClick = onOpenAvailableNotes,
                )
            }
        }

        if (onResetAll != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(22.dp))
            SettingsCard {
                SettingsActionRow(
                    title = if (confirmingReset) "Tap again to reset everything" else "Reset everything to defaults",
                    first = true,
                    danger = true,
                    onClick = {
                        if (confirmingReset) {
                            confirmingReset = false
                            onResetAll()
                        } else {
                            confirmingReset = true
                        }
                    },
                )
            }
        }
        SettingsCaption(
            "Your settings, skin and layouts are saved to your Wyrm account, so an update or a new phone " +
                "never loses them. Log in and they come back.",
        )
    }
}

@Composable
private fun BackupLoadingState(title: String, fraction: Float) {
    val transition = rememberInfiniteTransition(label = "backup-mark")
    val markFill by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "backup-mark-fill",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(wyrmRounded(14.dp))
            .background(Wyrm.Well)
            .padding(16.dp),
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WyrmMark(
                    modifier = Modifier.size(38.dp),
                    size = 32.dp,
                    fill = markFill,
                    ink = Wyrm.Ink,
                    unfilled = Wyrm.Track,
                )
                Column {
                    Text(
                        text = "BACKUP IN PROGRESS",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                        color = Wyrm.Quiet,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = title,
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Wyrm.Ink,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            RestoreProgress(fraction)
    }
}

@Composable
private fun RestoreProgress(fraction: Float) {
    val progress by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360),
        label = "backup-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(wyrmRounded(99.dp))
            .background(Wyrm.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(5.dp)
                .clip(wyrmRounded(99.dp))
                .background(Wyrm.Ink),
        )
    }
}
