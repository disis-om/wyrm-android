package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.R
import com.wyrm.omrajput.data.ApiPlayer
import com.wyrm.omrajput.data.ChatMessage
import com.wyrm.omrajput.data.Conversation

/** The two halves of chat: everyone, and the people you follow. */
enum class ChatTab(val label: String) { GLOBAL("Global"), DIRECT("Direct") }

/**
 * Spec page 04 — Social › Messages.
 *
 * Direct is the page: arena invites as cards, then threads. Global stays on
 * this shell until spec 05 is named; it is a paper ledger, not the 05 bubbles.
 * Back is ‹ Social (or Play). No tab bar.
 */
@Composable
fun ChatScreen(
    tab: ChatTab,
    unreadDirect: Long,
    messages: List<ChatMessage>,
    conversations: List<Conversation>,
    following: List<ApiPlayer>,
    meId: String,
    draft: String,
    sending: Boolean,
    error: String,
    backLabel: String = "Social",
    insetTop: Dp,
    insetBottom: Dp,
    onTabChange: (ChatTab) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenThread: (ApiPlayer) -> Unit,
    onJoinInvite: (String) -> Unit,
    onReport: (ChatMessage) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val invites = remember(conversations) {
        conversations.mapNotNull { row ->
            val invite = parseInvite(row.lastMessage) ?: return@mapNotNull null
            Triple(row, invite.first, invite.second)
        }
    }
    val threads = remember(conversations) {
        conversations.filter { parseInvite(it.lastMessage) == null }
    }
    val pickable = remember(following, conversations) {
        val spoken = conversations.map { it.player.id }.toSet()
        following.filter { it.id !in spoken && !it.isDeleted }
    }

    Box(modifier = Modifier.fillMaxSize().background(Wyrm.Paper)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = keyboardRoom(insetBottom)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Wyrm.Paper.copy(alpha = 0.94f))
                    .padding(top = insetTop)
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "‹ $backLabel",
                        fontFamily = Wyrm.Body,
                        fontSize = 16.sp,
                        color = Wyrm.Link,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clickable(onClick = onBack)
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "Messages",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Wyrm.Ink,
                    )
                }
                ChatSegmented(tab = tab, unreadDirect = unreadDirect, onTabChange = onTabChange)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Wyrm.Rule),
            )

            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Blood,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            when (tab) {
                ChatTab.DIRECT -> {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (invites.isNotEmpty()) {
                            item(key = "invites-label") {
                                SectionLabel("Arena invites", top = 18.dp)
                            }
                            itemsIndexed(invites, key = { _, row -> "invite-${row.first.player.id}" }) { _, row ->
                                InviteCard(
                                    conversation = row.first,
                                    address = row.second,
                                    onJoin = { onJoinInvite(row.second) },
                                    onReply = { onOpenThread(row.first.player) },
                                )
                            }
                        }
                        item(key = "threads-label") {
                            SectionLabel("Threads", top = if (invites.isEmpty()) 18.dp else 24.dp)
                        }
                        if (threads.isEmpty()) {
                            item(key = "threads-empty") {
                                EmptyCard("People you message will land here.")
                            }
                        } else {
                            item(key = "threads") {
                                ThreadCard {
                                    threads.forEachIndexed { index, thread ->
                                        ThreadRow(
                                            thread = thread,
                                            first = index == 0,
                                            onOpen = { onOpenThread(thread.player) },
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    NewMessageBar(onOpen = { picking = true })
                }

                ChatTab.GLOBAL -> {
                    Box(modifier = Modifier.weight(1f)) {
                        if (messages.isEmpty()) {
                            Notice("Nobody has said anything yet.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                reverseLayout = true,
                            ) {
                                items(messages.asReversed(), key = { it.id }) { message ->
                                    GlobalRow(
                                        message = message,
                                        mine = message.authorId == meId,
                                        onOpenPlayer = onOpenPlayer,
                                        onReport = onReport,
                                    )
                                }
                            }
                        }
                    }
                    PaperComposer(
                        draft = draft,
                        sending = sending,
                        placeholder = "Say something to everyone",
                        onDraftChange = onDraftChange,
                        onSend = onSend,
                    )
                }
            }
        }

        if (picking) {
            PickerSheet(
                people = pickable,
                insetTop = insetTop,
                onDismiss = { picking = false },
                onPick = { person ->
                    picking = false
                    onOpenThread(person)
                },
            )
        }
    }
}

@Composable
private fun ChatSegmented(
    tab: ChatTab,
    unreadDirect: Long,
    onTabChange: (ChatTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(wyrmRounded(10.dp))
            .background(Wyrm.Track)
            .padding(3.dp),
    ) {
        ChatTab.entries.forEach { option ->
            val active = option == tab
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .scale(pressScale(pressed))
                    .clip(wyrmRounded(8.dp))
                    .background(if (active) Wyrm.Card else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                    ) { onTabChange(option) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.label,
                        fontFamily = Wyrm.Body,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.5.sp,
                        color = if (active) Wyrm.Ink else Wyrm.Mute,
                    )
                    if (option == ChatTab.DIRECT && unreadDirect > 0 && !active) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(wyrmRounded(99.dp))
                                .background(Wyrm.Badge)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = unreadBadge(unreadDirect),
                                fontFamily = Wyrm.Body,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Wyrm.contentOn(Wyrm.Badge),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, top: Dp) {
    Text(
        text = text.uppercase(),
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.92.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = 8.dp),
    )
}

@Composable
private fun InviteCard(
    conversation: Conversation,
    address: String,
    onJoin: () -> Unit,
    onReply: () -> Unit,
) {
    val player = conversation.player
    val name = if (player.isDeleted) "Deleted player" else player.displayName.ifEmpty { "Unnamed" }
    val whenLabel = clockOf(conversation.lastAt)
    val shape = wyrmRounded(14.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.5.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WyrmAvatar(
                url = player.avatarUrl,
                avatarKey = player.avatarKey,
                initial = player.displayName,
                size = 30.dp,
                corner = 9.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = address,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (whenLabel.isNotEmpty()) {
                Text(
                    text = whenLabel,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.sp,
                    color = Wyrm.Quiet,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            PaperFillButton(
                label = "Join their arena",
                modifier = Modifier.weight(1f),
                onClick = onJoin,
            )
            Spacer(Modifier.width(8.dp))
            PaperOutlineButton(label = "Reply", onClick = onReply)
        }
    }
}

@Composable
private fun ThreadCard(content: @Composable () -> Unit) {
    val shape = wyrmRounded(14.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
    ) {
        content()
    }
}

@Composable
private fun ThreadRow(thread: Conversation, first: Boolean, onOpen: () -> Unit) {
    val player = thread.player
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val preview = when {
        thread.lastMessage.isNotEmpty() -> thread.lastMessage
        player.canMessage -> "No messages yet"
        else -> "Waiting for them to follow you back"
    }
    Column {
        if (!first) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Wyrm.RowRule),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .scale(pressScale(pressed))
                .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WyrmAvatar(
                url = player.avatarUrl,
                avatarKey = player.avatarKey,
                initial = player.displayName,
                size = 34.dp,
                corner = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (player.isDeleted) "Deleted player" else player.displayName,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = if (player.isDeleted) Wyrm.Quiet else Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preview,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (thread.unread > 0) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .height(20.dp)
                        .clip(wyrmRounded(99.dp))
                        .background(Wyrm.Badge)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (thread.unread > 99) "99+" else "${thread.unread}",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = Wyrm.contentOn(Wyrm.Badge),
                    )
                }
            }
            Text(
                text = "›",
                fontFamily = Wyrm.Body,
                fontSize = 17.sp,
                color = Wyrm.Chevron,
            )
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    val shape = wyrmRounded(14.dp)
    Text(
        text = message,
        fontFamily = Wyrm.Body,
        fontSize = 14.sp,
        color = Wyrm.Quiet,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .fillMaxWidth(),
    )
}

@Composable
private fun NewMessageBar(onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val plusInteraction = remember { MutableInteractionSource() }
    val plusPressed by plusInteraction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Wyrm.Paper.copy(alpha = 0.94f))
            .padding(top = 1.dp)
            .background(Wyrm.Rule)
            .padding(top = 1.dp)
            .background(Wyrm.Paper.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .scale(pressScale(pressed))
                .clip(wyrmRounded(11.dp))
                .background(Wyrm.Well)
                .border(1.dp, Wyrm.Rule, wyrmRounded(11.dp))
                .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Message someone new",
                fontFamily = Wyrm.Body,
                fontSize = 15.sp,
                color = Wyrm.TabIdle,
            )
        }
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .scale(pressScale(plusPressed))
                .clip(wyrmRounded(11.dp))
                .background(Wyrm.Ink)
                .clickable(interactionSource = plusInteraction, indication = null, onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                colorFilter = ColorFilter.tint(Wyrm.OnInk),
            )
        }
    }
}

@Composable
internal fun PaperComposer(
    draft: String,
    sending: Boolean,
    placeholder: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Wyrm.Paper.copy(alpha = 0.94f))
            .padding(top = 1.dp)
            .background(Wyrm.Rule)
            .padding(top = 1.dp)
            .background(Wyrm.Paper.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .clip(wyrmRounded(11.dp))
                .background(Wyrm.Well)
                .border(1.dp, Wyrm.Rule, wyrmRounded(11.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(280)) },
                enabled = !sending,
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink),
                cursorBrush = SolidColor(Wyrm.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!sending && draft.isNotBlank()) onSend()
                }),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = Wyrm.Body,
                            fontSize = 15.sp,
                            color = Wyrm.TabIdle,
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(9.dp))
        val canSend = draft.isNotBlank() && !sending
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            modifier = Modifier
                .size(42.dp)
                .scale(pressScale(pressed, canSend))
                .clip(wyrmRounded(11.dp))
                .background(if (canSend) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = canSend,
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                colorFilter = ColorFilter.tint(Wyrm.OnInk),
            )
        }
    }
}

@Composable
private fun GlobalRow(
    message: ChatMessage,
    mine: Boolean,
    onOpenPlayer: (String) -> Unit,
    onReport: (ChatMessage) -> Unit,
) {
    var showReport by remember(message.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        WyrmAvatar(
            url = message.authorAvatarUrl,
            avatarKey = message.authorAvatarKey,
            initial = message.authorName,
            size = 28.dp,
            corner = 9.dp,
            modifier = Modifier.clickable(enabled = !message.authorDeleted) {
                onOpenPlayer(message.authorId)
            },
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (message.authorDeleted) "Deleted player" else message.authorName,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = if (message.authorDeleted) Wyrm.Quiet else Wyrm.Quiet,
                    modifier = Modifier.clickable(enabled = !message.authorDeleted) {
                        onOpenPlayer(message.authorId)
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = clockOf(message.createdAt),
                    fontFamily = Wyrm.Body,
                    fontSize = 11.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.clickable { showReport = !showReport },
                )
            }
            val bubble = RoundedCornerShape(14.dp, 14.dp, 14.dp, 5.dp)
            Text(
                text = message.body,
                fontFamily = Wyrm.Body,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                color = if (mine) Wyrm.OnInk else Wyrm.Ink,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clip(bubble)
                    .background(if (mine) Wyrm.Ink else Wyrm.Card)
                    .then(if (mine) Modifier else Modifier.border(1.dp, Wyrm.Rule, bubble))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
            if (showReport && !mine) {
                Text(
                    text = "Report this message",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Wyrm.Blood,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable {
                            showReport = false
                            onReport(message)
                        },
                )
            }
        }
    }
}

@Composable
private fun PickerSheet(
    people: List<ApiPlayer>,
    insetTop: Dp,
    onDismiss: () -> Unit,
    onPick: (ApiPlayer) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(top = insetTop + 48.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shape = wyrmRounded(14.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Text(
                text = "Message someone new",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Wyrm.Ink,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
            if (people.isEmpty()) {
                Text(
                    text = "Follow someone from the leaderboard first.",
                    fontFamily = Wyrm.Body,
                    fontSize = 14.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                )
            } else {
                people.forEachIndexed { index, person ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
                    }
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .scale(pressScale(pressed))
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onPick(person) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WyrmAvatar(
                            url = person.avatarUrl,
                            avatarKey = person.avatarKey,
                            initial = person.displayName,
                            size = 34.dp,
                            corner = 10.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person.displayName,
                                fontFamily = Wyrm.Body,
                                fontSize = 15.sp,
                                color = Wyrm.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (person.handle.isNotEmpty()) {
                                Text(
                                    text = person.handle,
                                    fontFamily = Wyrm.Body,
                                    fontSize = 12.5.sp,
                                    color = Wyrm.Quiet,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperFillButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(38.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(10.dp))
            .background(Wyrm.Ink)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Wyrm.OnInk,
        )
    }
}

@Composable
private fun PaperOutlineButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .height(38.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(10.dp))
            .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontSize = 14.sp,
            color = Wyrm.Mute,
        )
    }
}

@Composable
private fun Notice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontFamily = Wyrm.Body,
            fontSize = 14.sp,
            color = Wyrm.Quiet,
        )
    }
}

/**
 * The one place a message is written on dark screens (thread, team, arena).
 *
 * Shared so those never drift apart — including the 280 character ceiling.
 */
@Composable
fun Composer(
    draft: String,
    sending: Boolean,
    placeholder: String,
    enabled: Boolean = true,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Wyrm.Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WyrmWell(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(280)) },
                enabled = enabled && !sending,
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.White),
                cursorBrush = SolidColor(Wyrm.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (enabled && !sending && draft.isNotBlank()) onSend()
                }),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = Wyrm.Body,
                            fontSize = 14.sp,
                            color = Wyrm.Faint,
                        )
                    }
                    inner()
                },
            )
        }
        AnimatedVisibility(
            visible = draft.isNotBlank(),
            enter = fadeIn() + scaleIn(initialScale = .65f),
            exit = fadeOut() + scaleOut(targetScale = .65f),
        ) {
            Box(
                Modifier.padding(start = 9.dp).size(48.dp).clip(CircleShape)
                    .background(if (enabled && !sending) Wyrm.White else Wyrm.Raised)
                    .clickable(enabled = enabled && !sending, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(LucideR.drawable.lucide_ic_send_horizontal), "Send",
                    Modifier.size(20.dp), tint = if (enabled && !sending) Wyrm.Black else Wyrm.Faint,
                )
            }
        }
    }
}

/**
 * The wall clock of a message, in the reader's own time zone.
 *
 * The server timestamps in UTC; printing that verbatim would put every message
 * five and a half hours in the past for the people this is being built for.
 */
fun clockOf(timestamp: String): String = runCatching {
    val local = java.time.Instant.parse(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    String.format("%02d:%02d", local.hour, local.minute)
}.getOrDefault("")

/** A compact count shared by Home, the Direct tab and each thread row. */
fun unreadBadge(count: Long): String = if (count > 4) "4+" else count.toString()
