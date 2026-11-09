package com.wyrm.omrajput.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text as RawText
import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.CustomArenas
import com.wyrm.omrajput.data.DeveloperMode
import com.wyrm.omrajput.data.WyrmDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Developer (OM, 2026-11-07: "iOS's developer option, same to same,
 * on Android"). iOS twin: the Developer card in WyrmSettingsPages.swift and
 * WyrmDeveloperDetail in WyrmDesignDetails.swift.
 *
 * Developer Mode on shows, under the switch, this phone's arena player ID
 * with Copy, then Wyrm logs (the page below). iOS's "Landscape rotation"
 * (Native / Internal) is iOS-only: it works around the iPhone keyboard, and
 * Android always turns natively.
 */
@Composable
internal fun DeveloperSettingsSection(onOpenLogs: (Rect) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DeveloperMode.reload(context) }
    Column(Modifier.settingAnchor("app.developer")) {
        SettingsSectionLabel("Developer", top = 0.dp)
        SettingsCard {
            SettingsBoolRow(
                title = "Developer Mode",
                detail = "Local diagnostics and export tools",
                on = DeveloperMode.on,
                first = true,
                onToggle = { DeveloperMode.set(context, it) },
            )
            if (DeveloperMode.on) {
                ArenaPlayerIdRow()
                SettingsValueRow(title = "Wyrm logs", value = "7 days", first = false, onOpen = onOpenLogs)
            }
        }
    }
}

/**
 * The 16-character id NTL calls the player ID: Wyrm sends it to every arena
 * it joins (CustomArenas, network/callback.c). This phone's own, kept through
 * log out. iOS twin: WyrmArenaPlayerIdRow.
 */
@Composable
internal fun ArenaPlayerIdRow() {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = remember { CustomArenas.id(context) }
    var copied by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        SettingsHairline()
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = "Arena player ID", fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = wyrmScopedPalette.ink)
            Text(
                text = "Sent to every arena you join. Community arenas know you by it.",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                color = wyrmScopedPalette.quiet,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    RawText(
                        text = id,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.6.sp,
                        color = wyrmScopedPalette.ink,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .scale(pressScale(pressed))
                        .clip(wyrmRounded(17.dp))
                        .background(wyrmScopedPalette.ink)
                        .clickable(interactionSource = interaction, indication = null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Arena player ID", id))
                            copied = true
                            scope.launch {
                                delay(1_800)
                                copied = false
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = wyrmScopedPalette.onInk,
                    )
                }
            }
        }
    }
}

/** Settings › Developer › Wyrm logs. iOS twin: WyrmDeveloperDetail. */
@Composable
internal fun DeveloperLogsScreen(
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var logOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val text = WyrmDiagnostics.text
    val lines = remember(text) {
        text.split('\n').filter { it.isNotEmpty() }.takeLast(WyrmDiagnostics.SHOWN_LINES)
    }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { WyrmDiagnostics.refresh(context) }
            busy = false
        }
    }
    LaunchedEffect(Unit) {
        WyrmDiagnostics.record("developer console opened")
        refresh()
    }

    SettingsDrillScaffold(
        title = "Developer Mode",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsSectionLabel("Diagnostics")
        SettingsCard {
            SettingsValueRow(title = "Retention", value = "7 days", first = true)
            SettingsValueRow(title = "Storage cap", value = "2 MB total", first = false)
            SettingsValueRow(title = "Current export", value = byteLabel(WyrmDiagnostics.byteCount), first = false)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f)) { PaperOutlineButton(label = "Refresh", enabled = !busy) { refresh() } }
            Box(Modifier.weight(1f)) {
                PaperOutlineButton(label = "Share logs", enabled = !busy) {
                    WyrmDiagnostics.record("share sheet requested")
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { WyrmDiagnostics.exportFile(context) }
                        if (file != null) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.trails", file,
                            )
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(send, "Share Wyrm logs")) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp)) {
            val clearInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(wyrmRounded(12.dp))
                    .border(1.dp, wyrmScopedPalette.badge.copy(alpha = 0.45f), wyrmRounded(12.dp))
                    .clickable(interactionSource = clearInteraction, indication = null) {
                        if (confirmClear) {
                            WyrmDiagnostics.clear(context)
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (confirmClear) "Tap again to clear logs" else "Clear stored logs",
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = wyrmScopedPalette.badge,
                )
            }
        }

        SettingsSectionLabel("App + engine log")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(wyrmRounded(15.dp))
                .background(wyrmScopedPalette.card)
                .border(1.dp, wyrmScopedPalette.rule, wyrmRounded(15.dp))
                .clickable { logOpen = !logOpen }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (logOpen) "Hide log" else "Show log",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = wyrmScopedPalette.ink,
                )
                Text(
                    text = if (lines.size >= WyrmDiagnostics.SHOWN_LINES) "Latest ${lines.size} lines" else "${lines.size} lines",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.sp,
                    color = wyrmScopedPalette.quiet,
                )
            }
            Text(
                text = "⌄",
                fontFamily = Wyrm.Body,
                fontSize = 18.sp,
                color = wyrmScopedPalette.ink,
                modifier = Modifier.rotate(if (logOpen) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = logOpen, enter = fadeIn(), exit = fadeOut()) {
            val list = rememberLazyListState()
            LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.size) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .height(440.dp)
                    .clip(wyrmRounded(15.dp))
                    .background(wyrmScopedPalette.card)
                    .border(1.dp, wyrmScopedPalette.rule, wyrmRounded(15.dp)),
            ) {
                SelectionContainer {
                    LazyColumn(state = list, modifier = Modifier.padding(12.dp)) {
                        itemsIndexed(lines) { _, line ->
                            RawText(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = wyrmScopedPalette.ink,
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                        item { Spacer(Modifier.height(44.dp)) }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(wyrmScopedPalette.ink)
                        .clickable { scope.launch { list.animateScrollToItem(lines.size) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "↓", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = wyrmScopedPalette.onInk)
                }
            }
        }
        Text(
            text = "Exports include app lifecycle, safe network status and engine events. Tokens, passwords and private message bodies are never written.",
            fontFamily = Wyrm.Body,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = wyrmScopedPalette.quiet,
            modifier = Modifier.padding(20.dp),
        )
    }
}

private fun byteLabel(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes bytes"
}
