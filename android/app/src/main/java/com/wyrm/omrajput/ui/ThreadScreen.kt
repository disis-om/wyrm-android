package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.ApiPlayer
import com.wyrm.omrajput.data.ChatMessage
import kotlinx.coroutines.delay

/**
 * One direct thread.
 *
 * The only place in the app that uses bubbles, because a private thread is the
 * one screen where who said what is the whole content — and with two people in
 * the room, a side is cheaper to read than a name on every line.
 */
@Composable
fun ThreadScreen(
    player: ApiPlayer,
    messages: List<ChatMessage>,
    meId: String,
    draft: String,
    sending: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onInvite: () -> Unit,
    onVoiceInvite: () -> Unit,
    onJoinInvite: (String) -> Unit,
) {
    val bottom = keyboardRoom(insetBottom)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Wyrm.Paper.copy(alpha = 0.94f))
                .padding(top = insetTop)
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹",
                    fontFamily = Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(10.dp))
                WyrmAvatar(
                    url = player.avatarUrl,
                    avatarKey = player.avatarKey,
                    initial = player.displayName,
                    size = 32.dp,
                    corner = 10.dp,
                    modifier = Modifier.clickable { onOpenPlayer(player.id) },
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (player.isDeleted) "Deleted player" else player.displayName,
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Wyrm.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (player.handle.isNotEmpty()) {
                        Text(
                            text = player.handle,
                            fontFamily = Wyrm.Body,
                            fontSize = 12.sp,
                            color = Wyrm.Quiet,
                        )
                    }
                }
                Text(
                    text = "Profile",
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .clickable { onOpenPlayer(player.id) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
            if (player.canMessage) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Arena invite",
                        fontFamily = Wyrm.Body,
                        fontSize = 13.sp,
                        color = Wyrm.Link,
                        modifier = Modifier.clickable(onClick = onInvite).padding(vertical = 4.dp),
                    )
                    Text(
                        text = "Voice invite",
                        fontFamily = Wyrm.Body,
                        fontSize = 13.sp,
                        color = Wyrm.Link,
                        modifier = Modifier.clickable(onClick = onVoiceInvite).padding(vertical = 4.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        if (error.isNotEmpty()) {
            Text(
                text = error,
                fontFamily = Wyrm.Body,
                fontSize = 12.sp,
                color = Wyrm.Badge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (player.canMessage) {
                            "Nothing here yet."
                        } else {
                            "You both have to follow each other before this thread opens."
                        },
                        fontFamily = Wyrm.Body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Wyrm.Quiet,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.height(18.dp)) }
                    items(messages.asReversed(), key = { it.id }) { message ->
                        Bubble(
                            message = message,
                            mine = message.authorId == meId,
                            onJoinInvite = onJoinInvite,
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        ThreadComposer(
            draft = draft,
            sending = sending,
            enabled = player.canMessage,
            placeholder = if (player.canMessage) "Message" else "Not open yet",
            insetBottom = bottom,
            onDraftChange = onDraftChange,
            onSend = onSend,
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage, mine: Boolean, onJoinInvite: (String) -> Unit) {
    val invite = remember(message.body) { parseInvite(message.body) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (invite != null) {
            InviteBubble(
                address = invite.first,
                note = invite.second,
                mine = mine,
                onJoin = onJoinInvite,
            )
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (mine) 14.dp else 5.dp,
                            bottomEnd = if (mine) 5.dp else 14.dp,
                        ),
                    )
                    .background(if (mine) Wyrm.Ink else Wyrm.Card)
                    .then(
                        if (mine) Modifier
                        else Modifier.border(
                            1.dp,
                            Wyrm.Rule,
                            RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = 5.dp,
                                bottomEnd = 14.dp,
                            ),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = message.body,
                    fontFamily = Wyrm.Body,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = if (mine) Wyrm.OnInk else Wyrm.Ink,
                )
                Text(
                    text = clockOf(message.createdAt),
                    fontFamily = Wyrm.Body,
                    fontSize = 11.sp,
                    color = if (mine) Wyrm.OnInk.copy(alpha = 0.65f) else Wyrm.Quiet,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * A server invite, drawn instead of the plain bubble it is wire-compatible
 * with.
 *
 * There is no message "type" column on the backend, so an invite is a normal
 * direct message whose body happens to start with [INVITE_PREFIX] — a client
 * that does not know this yet would just show the raw line, which is exactly
 * the fallback a wire format should degrade to.
 */
@Composable
private fun InviteBubble(address: String, note: String, mine: Boolean, onJoin: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(address) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(wyrmRounded(14.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
            .padding(13.dp, 14.dp),
    ) {
        Text(
            text = "ARENA INVITE",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.5.sp,
            letterSpacing = 0.92.sp,
            color = Wyrm.Quiet,
        )
        Text(
            text = note.ifBlank { "Arena invite" },
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Wyrm.Ink,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = address,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 12.5.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                PaperPrimaryButton(label = "Join", onClick = { onJoin(address) })
            }
            Box(Modifier.weight(1f)) {
                PaperOutlineButton(
                    label = if (copied) "Copied" else "Copy",
                    onClick = {
                        clipboard.setText(AnnotatedString(address))
                        copied = true
                    },
                )
            }
        }
    }
}

@Composable
private fun ThreadComposer(
    draft: String,
    sending: Boolean,
    enabled: Boolean,
    placeholder: String,
    insetBottom: Dp,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = enabled && draft.isNotBlank() && !sending
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Wyrm.Paper.copy(alpha = 0.94f))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = insetBottom.coerceAtLeast(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .clip(wyrmRounded(11.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(11.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = draft,
                onValueChange = { if (enabled) onDraftChange(it.take(280)) },
                enabled = enabled && !sending,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = Wyrm.Ink,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Wyrm.Ink),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(text = placeholder, fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.TabIdle)
                    }
                    inner()
                },
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(wyrmRounded(11.dp))
                .background(if (canSend) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "→", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Wyrm.OnInk)
        }
    }
}

private const val INVITE_PREFIX = "##invite:"

/**
 * Builds the wire format for a server invite: a normal direct-message body.
 *
 * One line, address then note, space-separated rather than on a line of its
 * own — the backend's [filterBody] strips every control character out of a
 * message before it is stored, newlines included, so a real newline never
 * survives the trip and cannot be the separator.
 */
fun encodeInvite(address: String, note: String): String = "$INVITE_PREFIX$address $note"

/** Whether a message body is a server invite, and if so, its address and note. */
fun parseInvite(body: String): Pair<String, String>? {
    if (!body.startsWith(INVITE_PREFIX)) return null
    val rest = body.removePrefix(INVITE_PREFIX)
    val space = rest.indexOf(' ')
    val address = if (space == -1) rest else rest.substring(0, space)
    if (address.isEmpty()) return null
    val note = if (space == -1) "" else rest.substring(space + 1).trim()
    return address to note
}
