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
import com.wyrm.omrajput.data.BackupState
import com.wyrm.omrajput.data.UpdateState

enum class BackupAction { CREATE, CHECK, RESTORE, CHOOSE_FOLDER }

/**
 * Spec page 16 — Settings › Backup & version.
 *
 * Real backup actions and the signed updater. No invented daily/keep-versions
 * rows — those are not in the engine.
 */
@Composable
fun BackupScreen(
    state: BackupState,
    activeAction: BackupAction?,
    update: UpdateState = UpdateState(),
    installedVersion: String = "",
    settingsVersion: String = "",
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String = "Settings",
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onCheck: () -> Unit,
    onRestore: () -> Unit,
    onChooseFolder: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    betaUpdates: Boolean = false,
    onBetaUpdates: (Boolean) -> Unit = {},
    onInstall: () -> Unit = {},
    onOpenInstalledNotes: () -> Unit = {},
    onOpenAvailableNotes: () -> Unit = {},
    onResetAll: (() -> Unit)? = null,
) {
    val waiting = state.busy || activeAction != null
    val creating = state.saving || activeAction == BackupAction.CREATE
    val checking = state.status == 4 || activeAction == BackupAction.CHECK
    val restoring = state.restoring || activeAction == BackupAction.RESTORE
    val openingFolder = activeAction == BackupAction.CHOOSE_FOLDER
    // The item-by-item restore report belongs to its modal result card. Keeping
    // it underneath made one completed action look like two separate reports.
    val restoreResult = state.restored || state.partial ||
        (state.failed && state.title.startsWith("Restore", ignoreCase = true))
    val headline = when {
        restoreResult -> "Backup restore finished"
        state.title.isNotBlank() -> state.title
        else -> "No backup on this phone yet"
    }
    val updateLabel = when {
        update.busy -> update.title.ifBlank { "Checking…" }
        update.available -> "Update available"
        update.failed -> "Check failed"
        update.title.isNotBlank() -> update.title
        else -> "Up to date"
    }
    var confirmingReset by remember { mutableStateOf(false) }

    SettingsDrillScaffold(
        title = "Backup",
        parent = backLabel,
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp, 16.dp, 14.dp, 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (state.failed) Wyrm.Badge else Wyrm.Live),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        text = "LAST BACKUP",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.8.sp,
                        color = if (state.failed) Wyrm.Badge else Wyrm.Live,
                    )
                }
                Text(
                    text = headline,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.padding(top = 9.dp),
                )
                Text(
                    text = if (state.detail.isNotBlank() && !restoreResult) {
                        state.detail
                    } else {
                        "Skins, controls, settings and team keys."
                    },
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (state.busy) {
                    Spacer(Modifier.height(12.dp))
                    BackupLoadingState(
                        title = state.title.ifEmpty { "Working on your backup" },
                        fraction = state.count / 100f,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (state.selectingFolder) {
                        Box(modifier = Modifier.weight(1f)) {
                            PaperPrimaryButton(
                                label = if (openingFolder) "Opening folder…" else "OK",
                                enabled = !openingFolder,
                                onClick = onChooseFolder,
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            PaperPrimaryButton(
                                label = if (creating) "Backing up…" else "Back up now",
                                enabled = !waiting || creating,
                                onClick = onCreate,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            PaperOutlineButton(
                                label = if (checking) "Loading…" else "Restore",
                                enabled = !waiting || checking || restoring,
                                onClick = if (state.found) onRestore else onCheck,
                            )
                        }
                    }
                }
                if (!state.selectingFolder && state.found && !waiting) {
                    Text(
                        text = "View previous backups",
                        fontFamily = Wyrm.Body,
                        fontSize = 14.sp,
                        color = Wyrm.Link,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable(onClick = onCheck),
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
            SettingsBoolRow(
                title = "Beta updates",
                detail = "Try new builds before everyone else. They can have rough edges.",
                on = betaUpdates,
                first = false,
                onToggle = onBetaUpdates,
            )
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
            "Before an update, Wyrm creates a dated backup in the folder you choose. " +
                "Team credentials are included only as Android-Keystore ciphertext.",
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
