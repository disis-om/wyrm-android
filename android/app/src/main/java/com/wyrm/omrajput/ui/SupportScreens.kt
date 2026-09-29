package com.wyrm.omrajput.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.CrashRecord
import com.wyrm.omrajput.data.CrashWatch
import com.wyrm.omrajput.data.SupportKind
import com.wyrm.omrajput.data.SupportReport
import com.wyrm.omrajput.data.SupportStore
import com.wyrm.omrajput.data.WyrmRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The screens of Help & feedback and the crash prompt (OM, 2026-09-29), as
 * Wyrm iOS draws them (`WyrmSupportViews.swift`). The engine behind them is
 * `data/SupportCenter.kt`: CrashWatch, SupportStore.
 */

private fun confirmHaptic(view: android.view.View, ok: Boolean) {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    view.performHapticFeedback(constant)
}

private val SupportKind.icon: Int
    get() = when (this) {
        SupportKind.BUG -> LucideR.drawable.lucide_ic_message_circle_warning
        SupportKind.SUGGESTION -> LucideR.drawable.lucide_ic_lightbulb
        SupportKind.HELP -> LucideR.drawable.lucide_ic_circle_question_mark
        SupportKind.OTHER -> LucideR.drawable.lucide_ic_message_circle
    }

// ------------------------------------------------------------ crash prompt

/**
 * Holds the crash prompt on screen through its "Thank you", then lets it go.
 * With "Always send" on, the report goes at launch and only a short note shows.
 */
@Composable
internal fun CrashPromptHost(repository: WyrmRepository, insetBottom: Dp, insetTop: Dp) {
    var shown by remember { mutableStateOf<CrashRecord?>(null) }
    var last by remember { mutableStateOf<CrashRecord?>(null) }
    val prompt = CrashWatch.prompt
    LaunchedEffect(Unit) {
        CrashWatch.launchCheck(repository)
        shown = CrashWatch.prompt
    }
    LaunchedEffect(prompt) {
        if (prompt != null) {
            shown = prompt
        } else if (shown != null) {
            // Sent: leave the thanks up for a moment. Dismissed: already gone.
            delay(1_600)
            if (CrashWatch.prompt == null) shown = null
        }
    }
    shown?.let { last = it }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = shown != null, enter = fadeIn(tween(1)), exit = fadeOut(tween(250))) {
            last?.let { record ->
                CrashPromptOverlay(
                    record = record,
                    repository = repository,
                    insetBottom = insetBottom,
                    onClosed = { shown = null },
                )
            }
        }
        val toast = CrashWatch.toast
        LaunchedEffect(toast) {
            if (toast.isNotEmpty()) {
                delay(2_400)
                CrashWatch.toast = ""
            }
        }
        AnimatedVisibility(
            visible = toast.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = insetTop + 18.dp),
        ) {
            Text(
                toast,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Wyrm.OnInk,
                modifier = Modifier
                    .clip(WyrmCapsule)
                    .background(Wyrm.Ink)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

private enum class CrashPhase { ASKING, SENDING, SENT, FAILED }

/**
 * Raised on the launch after a crash: a paper card from the bottom, one clear
 * ask, the note optional, what is sent one tap away. Nothing is sent until the
 * player says so (or has chosen "Always send").
 */
@Composable
private fun CrashPromptOverlay(
    record: CrashRecord,
    repository: WyrmRepository,
    insetBottom: Dp,
    onClosed: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val view = LocalView.current
    var note by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(CrashPhase.ASKING) }
    var appeared by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (appeared) iosSpring<Float>(0.5f, 0.86f) else tween<Float>(220),
        label = "crash-prompt",
        finishedListener = { value -> if (value == 0f && !appeared) { CrashWatch.dismissPrompt(); onClosed() } },
    )
    LaunchedEffect(Unit) {
        appeared = true
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    val density = LocalDensity.current
    val busy = phase == CrashPhase.SENDING

    fun send() {
        focus.clearFocus()
        phase = CrashPhase.SENDING
        // `send` clears the prompt itself; the card stays up to say thanks.
        CrashWatch.send(repository, record, note) { ok ->
            confirmHaptic(view, ok)
            phase = if (ok) CrashPhase.SENT else CrashPhase.FAILED
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Wyrm.Ink.copy(alpha = 0.34f * appear))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.clearFocus() },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp + insetBottom)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = with(density) { 520.dp.toPx() } * (1f - appear) }
                .shadow(30.dp, wyrmRounded(28.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(wyrmRounded(28.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) {
            if (phase == CrashPhase.SENT) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SupportCheckBadge(64.dp, 26.dp)
                    Text("Thank you", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Wyrm.Ink)
                    Text(
                        "The report is with the developer. You just made Wyrm a little better.",
                        fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Mute, textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(50.dp).clip(wyrmRounded(15.dp)).background(Wyrm.Badge.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(LucideR.drawable.lucide_ic_bandage), null, tint = Wyrm.Badge, modifier = Modifier.size(21.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("CRASH DETECTED", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        letterSpacing = 1.1.sp, color = Wyrm.Badge)
                    Text("Wyrm ${record.appVersion} · build ${record.build}", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet)
                }
            }
            Text("Wyrm closed unexpectedly", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = Wyrm.Ink, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Send the crash report to the developer and we'll see exactly what went wrong. You're helping make Wyrm better for every player.",
                fontFamily = Wyrm.Body, fontSize = 13.5.sp, lineHeight = 19.5.sp, color = Wyrm.Mute,
                modifier = Modifier.padding(top = 6.dp),
            )
            SupportLineField(
                value = note,
                onValue = { note = it.take(1000) },
                placeholder = "What were you doing? (optional)",
                enabled = !busy,
                modifier = Modifier.padding(top = 16.dp),
            )
            val chevron by animateFloatAsState(if (showDetails) 180f else 0f, label = "included-chevron")
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(38.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showDetails = !showDetails },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("What's included", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Wyrm.Link)
                Icon(painterResource(LucideR.drawable.lucide_ic_chevron_down), null, tint = Wyrm.Link,
                    modifier = Modifier.size(13.dp).rotate(chevron))
            }
            AnimatedVisibility(visible = showDetails, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SupportIncludedLine(LucideR.drawable.lucide_ic_smartphone, "Your phone model, Android and Wyrm version")
                    SupportIncludedLine(LucideR.drawable.lucide_ic_code, "Where in Wyrm's code it stopped")
                    SupportIncludedLine(LucideR.drawable.lucide_ic_file_text, "The last few minutes of Wyrm's own log")
                    SupportIncludedLine(LucideR.drawable.lucide_ic_lock, "Never your password, keys, Team ID or messages", tint = Wyrm.Live)
                }
            }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Always send crash reports", fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                    Text("Skip this question next time", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 1.dp))
                }
                Spacer(Modifier.width(12.dp))
                InkSwitch(on = CrashWatch.autoSend, onToggle = { CrashWatch.applyAutoSend(it) })
            }
            if (phase == CrashPhase.FAILED) {
                Text(
                    "Couldn't send it. Try again, or send it later from Settings › Help & feedback.",
                    fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Badge, modifier = Modifier.padding(top = 4.dp),
                )
            }
            SupportCapsuleButton(
                label = when (phase) {
                    CrashPhase.SENDING -> "Sending…"
                    CrashPhase.FAILED -> "Try again"
                    else -> "Send report"
                },
                busy = busy,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
                onClick = ::send,
            )
            Text(
                "Not now",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Wyrm.Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .clickable(enabled = !busy, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        focus.clearFocus()
                        appeared = false
                    }
                    .padding(vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun SupportIncludedLine(icon: Int, text: String, tint: Color = Wyrm.Quiet) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(18.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(12.dp))
        }
        Text(text, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Mute)
    }
}

@Composable
private fun SupportCheckBadge(size: Dp, icon: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Wyrm.Live.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(LucideR.drawable.lucide_ic_check), null, tint = Wyrm.Live, modifier = Modifier.size(icon))
    }
}

/** The solid ink capsule of the crash card, with its spinner. */
@Composable
private fun SupportCapsuleButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(pressScale(pressed, enabled))
            .clip(WyrmCapsule)
            .background(Wyrm.Ink)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            IosSpinner(size = 18.dp, colour = Wyrm.OnInk)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = Wyrm.OnInk)
    }
}

/** One line of text on a well, with its placeholder. */
@Composable
private fun SupportLineField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Ink),
        cursorBrush = SolidColor(Wyrm.Link),
        modifier = modifier.fillMaxWidth().height(46.dp).clip(wyrmRounded(13.dp)).background(Wyrm.Well),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Quiet)
                inner()
            }
        },
    )
}

// --------------------------------------------------------- Help & feedback

private data class SupportFaq(val question: String, val answer: String)

private val supportFaqs = listOf(
    SupportFaq(
        "My snake spawned and then dropped out of the arena",
        "Another slither app on the same Wi-Fi (on a PC or another phone) can make the arena drop you. Close it, or switch to mobile data, and pick the arena again. Joining the same arena many times a minute also gets a short timeout; wait a minute and try once.",
    ),
    SupportFaq(
        "How do I change my username or photo?",
        "Open your profile and tap Edit profile. You can rename twice a month. Tap your photo on your profile to change or remove it.",
    ),
    SupportFaq(
        "My skin or beads look different in the arena",
        "Other players see the arena's own colours, not Wyrm beads; your Wyrm beads and looks are drawn on your own snake. If your snake draws blank, update Wyrm and choose the skin again.",
    ),
    SupportFaq(
        "How do I keep my settings when I reinstall?",
        "Settings › Backup & version › Create backup saves skins, controls and settings to your backup folder. Restore from it on the new install.",
    ),
    SupportFaq(
        "I'm not getting notifications",
        "Check Settings › Notifications, and that Wyrm is allowed in Android's settings. Likes and replies on your trails arrive as notifications and wait in Alerts.",
    ),
    SupportFaq(
        "How do I get Wyrm updates?",
        "Wyrm tells you when a new build is out. Turn on Beta updates in Backup & version to get early builds.",
    ),
    SupportFaq(
        "How do I delete my account?",
        "Profile › Edit profile › Delete account. Your profile, trails and messages are removed from Wyrm's server.",
    ),
)

private fun supportWhen(millis: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))

/** "n new replies" for the Settings hub and the reports row. */
internal fun supportRepliesLabel(): String {
    val unseen = SupportStore.unseenReplies
    return when {
        unseen <= 0 -> ""
        unseen == 1 -> "1 new reply"
        else -> "$unseen new replies"
    }
}

@Composable
fun HelpCenterScreen(
    insetTop: Dp,
    insetBottom: Dp,
    repository: WyrmRepository,
    onBack: () -> Unit,
    onCompose: (SupportKind) -> Unit,
    onReports: () -> Unit,
) {
    LaunchedEffect(Unit) { SupportStore.refresh() }
    var expanded by remember { mutableStateOf(-1) }
    var sendingLast by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf("") }
    SettingsDrillScaffold(
        title = "Help & feedback",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("How can we help?", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Wyrm.Ink)
            Text("Wyrm is made by one person, and every report is read.", fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SupportTile(SupportKind.BUG) { onCompose(SupportKind.BUG) }
            SupportTile(SupportKind.SUGGESTION) { onCompose(SupportKind.SUGGESTION) }
            SupportTile(SupportKind.HELP) { onCompose(SupportKind.HELP) }
        }

        SettingsSectionLabel("Your reports")
        SettingsCard {
            val summary = supportRepliesLabel().ifEmpty {
                when {
                    !SupportStore.loaded -> ""
                    SupportStore.reports.isEmpty() -> "None yet"
                    else -> "${SupportStore.reports.size}"
                }
            }
            SettingsValueRow(title = "Your reports", value = summary, first = true, onOpen = { onReports() })
        }

        SettingsSectionLabel("Crash reports")
        SettingsCard {
            Box(Modifier.settingAnchor("app.crash.auto")) {
                SettingsBoolRow(
                    title = "Always send crash reports",
                    detail = "If Wyrm closes unexpectedly, the report goes without asking.",
                    on = CrashWatch.autoSend,
                    first = true,
                    onToggle = { CrashWatch.applyAutoSend(it) },
                )
            }
            CrashWatch.last?.let { last ->
                SettingsHairline()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Last crash", fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                        Text(
                            "${supportWhen(last.at)} · ${if (last.sent) "Sent. Thank you" else "Not sent"}",
                            fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    if (!last.sent) {
                        Spacer(Modifier.width(12.dp))
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        Box(
                            Modifier
                                .width(74.dp)
                                .height(34.dp)
                                .scale(pressScale(pressed, !sendingLast))
                                .clip(WyrmCapsule)
                                .background(Wyrm.Ink)
                                .clickable(interactionSource = interaction, indication = null, enabled = !sendingLast) {
                                    sendingLast = true
                                    lastError = ""
                                    CrashWatch.send(repository, last, "") { ok ->
                                        if (!ok) lastError = "Couldn't send it. Check your connection and try again."
                                        sendingLast = false
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sendingLast) IosSpinner(size = 16.dp, colour = Wyrm.OnInk)
                            else Text("Send", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Wyrm.OnInk)
                        }
                    }
                }
            }
        }
        if (lastError.isNotEmpty()) {
            Text(lastError, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Badge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }
        SettingsCaption(
            "A crash report holds your phone model, Android and Wyrm version, where in Wyrm it stopped and the last few minutes of Wyrm's log. Never your password, keys, Team ID or messages.",
        )

        SettingsSectionLabel("Common questions")
        SettingsCard {
            supportFaqs.forEachIndexed { index, item ->
                if (index > 0) SettingsHairline()
                val open = expanded == index
                val turn by animateFloatAsState(if (open) 45f else 0f, label = "faq-plus")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            expanded = if (open) -1 else index
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(item.question, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp,
                        color = Wyrm.Ink, modifier = Modifier.weight(1f))
                    Icon(painterResource(LucideR.drawable.lucide_ic_plus), null, tint = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 3.dp).size(14.dp).rotate(turn))
                }
                AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Text(item.answer, fontFamily = Wyrm.Body, fontSize = 13.sp, lineHeight = 19.sp, color = Wyrm.Mute,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
                }
            }
        }

        SettingsSectionLabel("Something else")
        SettingsCard {
            SettingsValueRow(title = "Write to Wyrm", value = "", first = true, onOpen = { onCompose(SupportKind.OTHER) })
        }
        SettingsCaption("Replies from Wyrm arrive in Alerts and under Your reports.")
    }
}

@Composable
private fun RowScope.SupportTile(kind: SupportKind, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(16.dp)
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 108.dp)
            .scale(pressScale(pressed))
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(38.dp).clip(wyrmRounded(11.dp)).background(Wyrm.Well), contentAlignment = Alignment.Center) {
            Icon(painterResource(kind.icon), null, tint = Wyrm.Ink, modifier = Modifier.size(18.dp))
        }
        Text(kind.pageTitle, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = Wyrm.Ink, maxLines = 2)
    }
}

// ---------------------------------------------------------- write a report

@Composable
fun SupportComposeScreen(
    initialKind: SupportKind,
    signedIn: Boolean,
    handle: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onReports: () -> Unit,
) {
    val kinds = remember { listOf(SupportKind.BUG, SupportKind.SUGGESTION, SupportKind.HELP, SupportKind.OTHER) }
    val focus = LocalFocusManager.current
    val view = LocalView.current
    var kind by remember { mutableStateOf(initialKind) }
    var message by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(initialKind.attachesByDefault) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val trimmed = message.trim()
    val canSend = trimmed.length >= 3 && !sending

    fun send() {
        if (!canSend) return
        focus.clearFocus()
        sending = true
        error = ""
        SupportStore.send(kind, trimmed, attach, "Help & feedback") { failure ->
            sending = false
            if (failure != null) {
                error = failure
                confirmHaptic(view, false)
            } else {
                confirmHaptic(view, true)
                sent = true
            }
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        SettingsDrillScaffold(
            title = if (sent) "Sent" else kind.pageTitle,
            parent = "Help",
            insetTop = insetTop,
            insetBottom = insetBottom,
            onBack = onBack,
            trailing = if (sent) null else "Send",
            trailingEnabled = canSend,
            onTrailing = if (sent) null else ({ send() }),
        ) {
            if (sent) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(60.dp))
                    SupportCheckBadge(76.dp, 30.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Thank you", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Wyrm.Ink)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (kind == SupportKind.SUGGESTION) {
                            "Your idea is with the developer. The best ones end up in Wyrm."
                        } else {
                            "Your report is with the developer. If we reply, it arrives in Alerts and under Your reports."
                        },
                        fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Mute, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(30.dp))
                    PaperPrimaryButton("Done", onClick = onBack)
                    Text(
                        "See your reports",
                        fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Wyrm.Link,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onReports)
                            .padding(12.dp),
                    )
                }
                return@SettingsDrillScaffold
            }
            PaperSegmented(
                options = kinds.map { it.title },
                selected = kinds.indexOf(kind).coerceAtLeast(0),
                onSelect = { index ->
                    kind = kinds[index]
                    attach = kind.attachesByDefault
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth(),
            )
            Text(kind.question, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Wyrm.Ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp))
            val shape = wyrmRounded(14.dp)
            BasicTextField(
                value = message,
                onValueChange = { message = it.take(4000) },
                enabled = !sending,
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 21.sp, color = Wyrm.Ink),
                cursorBrush = SolidColor(Wyrm.Link),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .clip(shape)
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, shape)
                    .padding(horizontal = 15.dp, vertical = 18.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (message.isEmpty()) {
                            Text(kind.placeholder, fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 21.sp,
                                color = Wyrm.Quiet.copy(alpha = 0.8f))
                        }
                        inner()
                    }
                },
            )
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                if (error.isNotEmpty()) {
                    Text(error, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Badge, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text("${message.length}/4000", fontFamily = Wyrm.Body, fontSize = 11.sp,
                    color = if (message.length > 3800) Wyrm.Badge else Wyrm.Quiet)
            }
            SettingsSectionLabel("Include")
            SettingsCard {
                SettingsBoolRow(
                    title = "Device info and recent log",
                    detail = "Phone model, Android and Wyrm version, the last few minutes of Wyrm's log. Makes problems much easier to fix.",
                    on = attach,
                    first = true,
                    onToggle = { attach = it },
                )
            }
            SettingsCaption(
                if (!signedIn) {
                    "You're not signed in, so we can't reply in the app."
                } else {
                    "Sent as ${handle.ifBlank { "you" }}. If we reply, you'll find it in Alerts and under Your reports. Never your password, keys or messages."
                },
            )
            if (sending) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IosSpinner(size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Sending…", fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
                }
            }
        }
    }
}

// ------------------------------------------------------------ your reports

@Composable
fun SupportReportsScreen(
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onNew: () -> Unit,
) {
    LaunchedEffect(Unit) { SupportStore.refresh() }
    // Opening the list is reading the replies in it.
    LaunchedEffect(SupportStore.loaded, SupportStore.loading, SupportStore.reports) {
        if (SupportStore.loaded && !SupportStore.loading) SupportStore.markRepliesSeen()
    }
    SettingsDrillScaffold(
        title = "Your reports",
        parent = "Help",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
        trailing = "New",
        onTrailing = onNew,
        refreshing = SupportStore.loading && SupportStore.loaded,
        onRefresh = { SupportStore.refresh() },
    ) {
        when {
            !SupportStore.loaded -> Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { SupportReportSkeleton() }
            }
            SupportStore.reports.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(LucideR.drawable.lucide_ic_inbox), null, tint = Wyrm.Quiet, modifier = Modifier.size(28.dp))
                Text(SupportStore.error.ifEmpty { "No reports yet" }, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp, color = Wyrm.Ink, textAlign = TextAlign.Center)
                Text("Problems, ideas and questions you send appear here, with Wyrm's replies.",
                    fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet, textAlign = TextAlign.Center)
            }
            else -> Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportStore.reports.forEach { report -> SupportReportCard(report) }
            }
        }
    }
}

@Composable
private fun SupportReportCard(report: SupportReport) {
    val shape = wyrmRounded(17.dp)
    val status = when {
        report.reply.isNotBlank() -> "Wyrm replied"
        report.status == "resolved" -> "Resolved"
        report.status == "read" -> "Seen"
        else -> "Sent"
    }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                SupportKind.title(report.kind).uppercase(),
                fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 1.sp, color = Wyrm.Ink,
                modifier = Modifier.clip(WyrmCapsule).background(Wyrm.Well).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Text(status, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                color = if (report.reply.isBlank()) Wyrm.Quiet else Wyrm.Live, modifier = Modifier.weight(1f))
            Text(trailTime(report.createdAt), fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet)
        }
        Text(report.message.ifBlank { "Crash report" }, fontFamily = Wyrm.Body, fontSize = 14.sp, lineHeight = 19.sp,
            color = Wyrm.Ink, maxLines = 5)
        if (report.reply.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(wyrmRounded(14.dp)).background(Wyrm.Live.copy(alpha = 0.09f)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WyrmBrandMark(size = 26.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Wyrm", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Wyrm.Ink)
                    Text(report.reply, fontFamily = Wyrm.Body, fontSize = 13.5.sp, lineHeight = 19.sp, color = Wyrm.Ink)
                }
            }
        }
    }
}

@Composable
private fun SupportReportSkeleton() {
    val pulse = rememberInfiniteTransition(label = "report-skeleton")
    val dim by pulse.animateFloat(1f, 0.55f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "report-skeleton-dim")
    val shape = wyrmRounded(17.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .graphicsLayer { alpha = dim }
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(90.dp).height(12.dp).clip(wyrmRounded(5.dp)).background(Wyrm.Well))
        Box(Modifier.fillMaxWidth().height(13.dp).clip(wyrmRounded(5.dp)).background(Wyrm.Well))
        Box(Modifier.width(190.dp).height(13.dp).clip(wyrmRounded(5.dp)).background(Wyrm.Well))
    }
}
