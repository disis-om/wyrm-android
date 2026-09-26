package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.NotificationKind
import com.wyrm.omrajput.data.WyrmNotification
import com.wyrm.omrajput.data.eventWhen
import com.wyrm.omrajput.data.hasStarted
import kotlinx.coroutines.delay

/**
 * The panel behind the bell on Home.
 *
 * One list, newest first, and every entry reads at a glance because the kind
 * decides the shape rather than a caption: an event carries a schedule and a
 * server, a chat message carries whoever sent it, an update carries what
 * changed. Tapping a card marks it read; the kind-specific action underneath
 * — Join, View, whatever it is — does the rest.
 */
@Composable
fun NotificationsScreen(
    notifications: List<WyrmNotification>,
    insetTop: Dp,
    insetBottom: Dp,
    showRootTabs: Boolean = true,
    onMarkAllRead: () -> Unit,
    onOpenNotification: (String) -> Unit,
    onSetNotificationRead: (String, Boolean) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onJoinEvent: (String) -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenBackup: () -> Unit,
    onTabSocial: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTabPlay: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTabSkin: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTabSettings: (androidx.compose.ui.geometry.Rect) -> Unit,
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
    onRefresh: (done: () -> Unit) -> Unit = { it() },
) {
    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(pulling) { if (pulling) onRefresh { pulling = false } }
    val listState = rememberLazyListState()
    var selectedNotification by remember { mutableStateOf<WyrmNotification?>(null) }
    LaunchedEffect(highlightId, notifications) {
        val index = notifications.indexOfFirst { it.id == highlightId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            delay(1200)
            onHighlightConsumed()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Wyrm.Paper)) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Wyrm iOS › Alerts: the header scrolls with the inbox; no rule under it.
        val clearance = LocalRootTabClearance.current
        IosRefreshable(pulling, { pulling = true }, Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = insetTop),
                ) {
                    item {
                        IosScreenHeader(kicker = "Inbox", title = "Alerts") {
                            Text(
                                text = "Read all",
                                fontFamily = Wyrm.Body,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Wyrm.Link,
                                modifier = Modifier.clickable(onClick = onMarkAllRead),
                            )
                        }
                    }
                    if (notifications.isEmpty()) {
                        item {
                            IosPaperCard { IosEmptyPanel(title = "All caught up", note = "Nothing new right now.") }
                        }
                    }
                    itemsIndexed(notifications, key = { _, item -> item.id }) { _, notification ->
                        Box(Modifier.padding(horizontal = 16.dp).animateItem()) {
                        NotificationCard(
                            notification = notification,
                            highlighted = notification.id == highlightId,
                            onTap = { onOpenNotification(notification.id) },
                            onLongPress = { selectedNotification = notification },
                            onJoinEvent = onJoinEvent,
                            onOpenUpdate = onOpenUpdate,
                            onOpenLeaderboard = onOpenLeaderboard,
                            onOpenBackup = onOpenBackup,
                        )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    item { Spacer(Modifier.height(clearance)) }
                }
        }

        if (showRootTabs) {
            RootTabs(
                selected = RootTab.NOTIFICATIONS,
                insetBottom = insetBottom,
                unreadNotifications = notifications.count { !it.read },
                onNotifications = {},
                onSocial = onTabSocial,
                onPlay = onTabPlay,
                onSkin = onTabSkin,
                onSettings = onTabSettings,
            )
        }
    }

        selectedNotification?.let { notification ->
            IosActionSheet(
                title = notification.title,
                actions = listOf(
                    IosSheetAction(if (notification.read) "Mark unread" else "Mark as read") {
                        onSetNotificationRead(notification.id, !notification.read)
                    },
                    IosSheetAction("Delete notification", destructive = true) { onDeleteNotification(notification.id) },
                ),
                insetBottom = insetBottom,
                onDismiss = { selectedNotification = null },
            )
        }
    }
}

/** A quiet paper card; unread state is visible without washing out its text. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NotificationCardShell(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    highlighted: Boolean,
    unread: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(17.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) Wyrm.Hover else Wyrm.Card.copy(alpha = 0.92f))
            .border(1.dp, Wyrm.Rule, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(16.dp),
        content = content,
    )
}

/**
 * Wyrm iOS's alert card: unread dot, the kind in tracked caps, the date and a
 * ⋯ menu; the title; the body rendered as full Markdown; any detail rows. The
 * same card for every kind — tapping still opens what the alert is about.
 */
@Composable
private fun NotificationCard(
    notification: WyrmNotification,
    highlighted: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onJoinEvent: (String) -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    NotificationCardShell(
        onClick = onTap,
        onLongClick = onLongPress,
        highlighted = highlighted,
        unread = !notification.read,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(WyrmCapsule).background(if (notification.read) Color.Transparent else Wyrm.Live))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notification.kind.name.replace('_', ' '),
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.sp,
                    color = Wyrm.Live,
                )
                Spacer(Modifier.weight(1f))
                Text(notification.createdAt.take(10), fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = Wyrm.Quiet)
                Box(
                    Modifier
                        .size(28.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLongPress),
                    contentAlignment = Alignment.Center,
                ) { IosIcon(IosGlyph.ELLIPSIS, Wyrm.Quiet, size = 18.dp) }
            }
            Text(notification.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Wyrm.Ink)
            if (notification.body.isNotBlank()) {
                WyrmMarkdown(source = notification.body, baseSize = 12.5.sp)
            }
            alertMeta(notification).forEach { (key, value) ->
                Row {
                    Text(key, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet)
                    Spacer(Modifier.weight(1f))
                    Text(value, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Wyrm.Ink)
                }
            }
        }
    }
}

/** The detail rows iOS lists under an alert, from whatever the alert carries. */
private fun alertMeta(n: WyrmNotification): List<Pair<String, String>> = buildList {
    n.actorName?.takeIf { it.isNotBlank() }?.let { add("From" to it) }
    n.eventOrganizer?.takeIf { it.isNotBlank() }?.let { add("Organizer" to it) }
    n.startsAt?.takeIf { it.isNotBlank() }?.let { add("Starts" to it.replace('T', ' ').take(16)) }
    n.serverAddress?.takeIf { it.isNotBlank() }?.let { add("Server" to it) }
    n.version?.takeIf { it.isNotBlank() }?.let { add("Version" to it) }
    n.value?.let { add("Value" to it.toString()) }
}.sortedBy { it.first }

/** The two account-local actions revealed by holding any card. */
@Composable
private fun NotificationActionsPopup(
    notification: WyrmNotification,
    insetBottom: Dp,
    onDismiss: () -> Unit,
    onSetRead: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val dismissInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Wyrm.Gutter)
                .padding(bottom = insetBottom + Wyrm.Gap)
                .fillMaxWidth()
                .clip(wyrmRounded(15.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(15.dp))
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = "ALERT",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.92.sp,
                color = Wyrm.Quiet,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = notification.title,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Wyrm.Ink,
                maxLines = 2,
            )
            Spacer(Modifier.height(16.dp))
            WyrmRule()
            NotificationPopupAction(
                label = if (notification.read) "Mark as unread" else "Mark as read",
                color = Wyrm.Ink,
            ) { onSetRead(!notification.read) }
            WyrmRule()
            NotificationPopupAction("Delete notification", Wyrm.Blood, onDelete)
        }
    }
}

@Composable
private fun NotificationPopupAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

/** The small-caps tag every card opens with, plus the unread dot and the clock. */
@Composable
private fun NotificationHeader(tag: String, tagColor: Color, timestamp: String, unread: Boolean) {
    // Wyrm iOS: unread dot, the kind in tracked caps, the date on the right.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(WyrmCapsule).background(if (unread) Wyrm.Live else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Text(
            text = tag.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            color = Wyrm.Live,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = timestamp,
            fontFamily = Wyrm.Body,
            fontSize = 10.5.sp,
            color = Wyrm.Quiet,
        )
    }
}

@Composable
private fun UnreadDot() {
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(wyrmRounded(999.dp))
            .background(Wyrm.Live),
    )
}

@Composable
private fun FeatureCardBody(n: WyrmNotification) {
    NotificationHeader("New Feature", Wyrm.Live, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Wyrm.Ink,
    )
    Spacer(Modifier.height(8.dp))
    WyrmMarkdown(n.body, baseSize = 14.sp)
}

@Composable
private fun EventCardBody(n: WyrmNotification, onJoin: (String) -> Unit) {
    var clock by remember(n.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(n.id) {
        while (true) {
            delay(60_000)
            clock = System.currentTimeMillis()
        }
    }
    val started = n.hasStarted(java.time.Instant.ofEpochMilli(clock))
    NotificationHeader("Battledome Event", Wyrm.Live, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = Wyrm.Ink,
    )
    if (n.body.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        ExpandableBlock { WyrmMarkdown(n.body, baseSize = 14.sp) }
    }
    Spacer(Modifier.height(12.dp))
    WyrmRule()
    Spacer(Modifier.height(12.dp))
    eventWhen(n.startsAt).takeIf { it.isNotEmpty() }?.let { InfoRow("When", it) }
    n.eventOrganizer?.let { InfoRow("By", it) }
    n.serverAddress?.let { InfoRow("Server", it) }
    Spacer(Modifier.height(14.dp))

    InviteActions(
        id = n.id,
        address = n.serverAddress,
        joinLabel = if (started) "Join Now" else eventCountdown(n.startsAt, clock),
        joinFilled = started,
        onJoin = onJoin,
    )
}

@Composable
private fun UpdateCardBody(n: WyrmNotification, onOpenUpdate: () -> Unit) {
    NotificationHeader("Update Available", Wyrm.Live, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Wyrm.Ink,
    )
    n.version?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(6.dp))
        WyrmLabel("Version $it")
    }
    if (n.body.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        ExpandableBlock { WyrmMarkdown(n.body, baseSize = 14.sp) }
    }
    Spacer(Modifier.height(12.dp))
    NotificationOutlineAction(label = "View updates", onClick = onOpenUpdate)
}

@Composable
private fun InviteCardBody(n: WyrmNotification, onJoin: (String) -> Unit) {
    NotificationHeader("Server Invite", Wyrm.Live, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        WyrmAvatar(
            url = "",
            avatarKey = "",
            initial = n.actorName ?: "?",
            size = 30.dp,
            corner = 11.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = n.actorName ?: "Someone",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.Ink,
        )
    }
    if (n.body.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        WyrmMarkdown(n.body, baseSize = 14.sp)
    }
    n.serverAddress?.let {
        Spacer(Modifier.height(12.dp))
        InfoRow("Server", it)
    }
    Spacer(Modifier.height(14.dp))
    InviteActions(n.id, n.serverAddress, onJoin = onJoin)
}

@Composable
private fun FollowCardBody(n: WyrmNotification) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WyrmAvatar(
            url = "",
            avatarKey = "",
            initial = n.actorName ?: "?",
            size = 34.dp,
            corner = 12.dp,
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Wyrm.Ink)) {
                    append(n.actorName ?: "Someone")
                }
                append("  " + n.body)
            },
            fontFamily = Wyrm.Body,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Wyrm.Ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (!n.read) {
                UnreadDot()
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = n.timestamp,
                fontFamily = Wyrm.Body,
                fontSize = 10.sp,
                color = Wyrm.Quiet,
            )
        }
    }
}

@Composable
private fun AchievementCardBody(n: WyrmNotification) {
    NotificationHeader("Milestone", Wyrm.Live, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Wyrm.Ink,
    )
    Spacer(Modifier.height(6.dp))
    WyrmMarkdown(n.body, baseSize = 14.sp)
}

@Composable
private fun NoticeCardBody(n: WyrmNotification) {
    NotificationHeader("Notice", Wyrm.Blood, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Wyrm.Ink,
    )
    if (n.body.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        WyrmMarkdown(n.body, baseSize = 14.sp)
    }
}

@Composable
private fun BroadcastCardBody(n: WyrmNotification) {
    NotificationHeader("Announcement", Wyrm.Quiet, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Wyrm.Ink,
    )
    if (n.body.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        WyrmMarkdown(n.body, baseSize = 14.sp)
    }
}

@Composable
private fun RankCardBody(n: WyrmNotification, onOpenLeaderboard: () -> Unit) {
    NotificationHeader("Leaderboard", Wyrm.Live, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Wyrm.Ink,
    )
    Spacer(Modifier.height(6.dp))
    WyrmMarkdown(n.body, baseSize = 14.sp)
    Spacer(Modifier.height(12.dp))
    NotificationOutlineAction(label = "View leaderboard", onClick = onOpenLeaderboard)
}

@Composable
private fun BackupCardBody(n: WyrmNotification, onOpenBackup: () -> Unit) {
    NotificationHeader("Backup", Wyrm.Quiet, n.timestamp, !n.read)
    Spacer(Modifier.height(8.dp))
    Text(
        text = n.title,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Wyrm.Ink,
    )
    Spacer(Modifier.height(6.dp))
    WyrmMarkdown(n.body, baseSize = 14.sp)
    Spacer(Modifier.height(12.dp))
    NotificationOutlineAction(label = "View backup", onClick = onOpenBackup)
}

/** Secondary card action with paper contrast, never the legacy glass treatment. */
@Composable
private fun NotificationOutlineAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .scale(pressScale(pressed))
            .clip(shape)
            .background(if (enabled) Wyrm.Paper else Wyrm.Well)
            .border(1.dp, Wyrm.Ink.copy(alpha = if (enabled) 0.24f else 0.08f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (enabled) Wyrm.Ink else Wyrm.Quiet.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun InviteActions(
    id: String,
    address: String?,
    joinLabel: String = "Join Now",
    joinFilled: Boolean = true,
    onJoin: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NotificationOutlineAction(
            label = if (copied) "Copied" else "Copy IP",
            modifier = Modifier.weight(1f),
            enabled = !address.isNullOrBlank(),
        ) {
            address?.let {
                clipboard.setText(AnnotatedString(it))
                copied = true
            }
        }
        if (joinFilled) {
            WyrmPrimaryAction(
                label = joinLabel,
                modifier = Modifier.weight(1.2f).height(50.dp),
            ) { address?.let(onJoin) }
        } else {
            NotificationOutlineAction(
                label = joinLabel,
                modifier = Modifier.weight(1.2f),
                enabled = !address.isNullOrBlank(),
            ) { address?.let(onJoin) }
        }
    }
}

private fun eventCountdown(startsAt: String?, nowMs: Long): String {
    val instant = startsAt ?: return "Join Now"
    return runCatching {
    val target = java.time.Instant.parse(instant).toEpochMilli()
    val minutes = ((target - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L
    when {
        minutes >= 24 * 60 -> "Starts in ${minutes / (24 * 60)}d"
        minutes >= 60 -> "Starts in ${minutes / 60}h ${minutes % 60}m"
        else -> "Starts in ${minutes}m"
    }
    }.getOrDefault("Join Now")
}

/** A label/value pair, the same shape an event's rundown always takes. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.width(78.dp),
        )
        Text(
            text = value,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = Wyrm.Ink,
        )
    }
}
