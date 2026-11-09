package com.wyrm.omrajput.ui

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.ApiPlayer
import com.wyrm.omrajput.data.ChatMessage
import com.wyrm.omrajput.data.GifItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/*
 * Chat, the next level (OM, 2026-10-30): Discord's layout in Wyrm's look, for
 * global chat and direct messages. Squircle faces (the small copy), the name
 * and "Today at 5:32 PM" on one line, a run of one person's messages joined,
 * Discord's reply line, reactions under a message, hold for the reaction bar
 * and actions, swipe left to reply, "who is typing", @mentions highlighted,
 * GIFs and stickers from KLIPY beside Send, search, and pins (pink) that only
 * the DEV accounts set. Data: backend chat-plus.mjs; prototype
 * tools/chat-prototype/index.html. iOS twin: WyrmDiscordChat.swift.
 */

internal val ChatPink = Color(0xFFE5487A)
private val MentionInk = Color(0xFFB07B10)
private val MentionWash = Color(0x24F2B544)
private val MentionBar = Color(0xFFF2B544)
internal val QUICK_REACTIONS = listOf("❤️", "😂", "👍", "🔥", "😮")

/** Emoji for the picker, by group. */
internal val CHAT_EMOJI: List<Pair<String, List<String>>> = listOf(
    "Smileys" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 🤗 🤭 🤫 🤔 😐 😑 😶 🙄 😏 😬 😌 😔 😪 😴 😷 🤒 🥵 🥶 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 😮 😲 😳 🥺 😦 😨 😰 😢 😭 😱 😖 😣 😞 😓 😩 😫 😤 😡 😠 🤬 💀 👻 👽 🤖 💩".split(" "),
    "People" to "👋 🤚 ✋ 🖖 👌 🤌 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🙏 💪 🫡 🫶 👀 🧠".split(" "),
    "Nature" to "🐍 🐉 🦖 🐢 🐸 🐊 🦎 🐙 🦈 🐬 🐳 🐝 🦋 🐞 🌸 🌹 🌻 🌲 🍀 🍁 🌈 ⚡ 🔥 💧 ❄️ 🌙 ⭐ ☀️".split(" "),
    "Food" to "🍎 🍓 🍉 🍌 🍕 🍔 🍟 🌮 🍜 🍣 🍩 🍪 🎂 🍫 🍿 🧃 ☕ 🧋".split(" "),
    "Activity" to "🎮 🕹️ 🏆 🥇 🥈 🥉 🎯 🎲 🎉 🎊 🎁 ⚽ 🏀 🏏 🎸 🎧".split(" "),
    "Symbols" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💯 💢 💥 💫 ✨ ✅ ❌ ❓ ❗ ⚠️ 🔔 🎵 💬 👑 💎".split(" "),
)

private val GIF_LINK = Regex("^https://[a-z0-9.-]*klipy\\.com/\\S+$")
private val MENTION = Regex("(^|[^A-Za-z0-9_])@([A-Za-z0-9_]{3,20})")
internal fun isChatGif(body: String) = GIF_LINK.matches(body.trim())

/** Discord's typing line: one, two, three names, then "Several people". */
internal fun typingLine(names: List<String>): AnnotatedString = buildAnnotatedString {
    fun bold(text: String) = withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Wyrm.Ink)) { append(text) }
    when (names.size) {
        0 -> Unit
        1 -> { bold(names[0]); append(" is typing…") }
        2 -> { bold(names[0]); append(" and "); bold(names[1]); append(" are typing…") }
        3 -> { bold(names[0]); append(", "); bold(names[1]); append(" and "); bold(names[2]); append(" are typing…") }
        else -> { bold("Several people"); append(" are typing…") }
    }
}

private fun instantOf(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

/** "Today at 5:32 PM", "Yesterday at 9:10 AM", else "12/10/2026 9:10 AM". */
internal fun discordTime(raw: String): String {
    val at = instantOf(raw)?.atZone(ZoneId.systemDefault()) ?: return ""
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at)
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (at.toLocalDate()) {
        today -> "Today at $clock"
        today.minusDays(1) -> "Yesterday at $clock"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(at) + " " + clock
    }
}

private fun shortClock(raw: String): String =
    instantOf(raw)?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(it.atZone(ZoneId.systemDefault())) }.orEmpty()

/** One person's run: same author, under seven minutes apart, not a reply. */
private fun joinsRun(message: ChatMessage, previous: ChatMessage?): Boolean {
    if (previous == null || previous.authorId != message.authorId || message.replyTo != null) return false
    val a = instantOf(previous.createdAt) ?: return false
    val b = instantOf(message.createdAt) ?: return false
    return b.epochSecond - a.epochSecond < 7 * 60
}

/** Words with @mentions as pills: yours gold, others blue. */
private fun chatText(body: String, meUsername: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (match in MENTION.findAll(body)) {
        val start = match.groups[2]!!.range.first - 1
        append(body.substring(at, start))
        val name = match.groupValues[2]
        val mine = meUsername.isNotBlank() && name.equals(meUsername, ignoreCase = true)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (mine) MentionInk else Wyrm.Link,
            background = if (mine) Color(0x40F2B544) else Wyrm.Link.copy(alpha = 0.14f))) { append("@$name") }
        at = match.range.last + 1
    }
    append(body.substring(at))
}

/** What a chat screen is given; one class so the global chat and a thread share it. */
internal class ChatActions(
    val onSend: () -> Unit,
    val onSendGif: (String) -> Unit,
    val onDraftChange: (String) -> Unit,
    val onTyping: () -> Unit,
    val onBack: () -> Unit,
    val onAuthor: (String) -> Unit,
    val onReply: (ChatMessage) -> Unit,
    val onEdit: (ChatMessage) -> Unit,
    val onCancelCompose: () -> Unit,
    val onDelete: (ChatMessage) -> Unit,
    val onReport: (ChatMessage) -> Unit,
    val onReact: (ChatMessage, String) -> Unit,
    val onPin: (ChatMessage, Boolean) -> Unit,
    val search: suspend (String) -> List<ChatMessage>,
    val gifs: suspend (Boolean, String) -> List<GifItem>,
    val pins: suspend () -> Pair<List<ChatMessage>, Boolean>,
    /** Who reacted (hold a reaction, OM 2026-10-31). */
    val who: suspend (ChatMessage) -> List<Pair<String, List<com.wyrm.omrajput.data.ReactionPerson>>> = { emptyList() },
    /** @ suggestions from every player (username to name), OM 2026-11-07. */
    val mentionSearch: suspend (String) -> List<Pair<String, String>> = { emptyList() },
    /** A link's embed, read by the server (OM 2026-11-07). */
    val linkPreview: suspend (String) -> com.wyrm.omrajput.data.LinkPreview? = { null },
)

/** Someone with Wyrm open right now (global chat's "{n} active", 2026-11-07). */
internal data class ChatPerson(val id: String, val name: String, val username: String, val arena: String)

private enum class ChatSheet { NONE, HOLD, REACT, PICKER, SEARCH, PINS, WHO, ACTIVE }

/** Rank of a mention match: 0 starts the username, 1 starts the name, 2 inside either, -1 none. */
private fun mentionRank(query: String, username: String, name: String): Int = when {
    query.isEmpty() -> 0
    username.startsWith(query, ignoreCase = true) -> 0
    name.startsWith(query, ignoreCase = true) -> 1
    username.contains(query, ignoreCase = true) || name.contains(query, ignoreCase = true) -> 2
    else -> -1
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun WyrmChatScreen(
    title: String,
    subtitle: String,
    direct: Boolean,
    peer: ApiPlayer?,
    messages: List<ChatMessage>,
    meId: String,
    meUsername: String,
    meName: String,
    meAvatarUrl: String,
    meAvatarKey: String,
    draft: String,
    sending: Boolean,
    error: String,
    typing: List<String>,
    replying: ChatMessage?,
    editing: ChatMessage?,
    canPin: Boolean,
    mentionables: List<Pair<String, String>>,
    insetTop: Dp,
    insetBottom: Dp,
    actions: ChatActions,
    header: (@Composable () -> Unit)? = null,
    /** Global chat: who has Wyrm open now; the subtitle opens the list. */
    activePeople: List<ChatPerson> = emptyList(),
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var sheet by remember { mutableStateOf(ChatSheet.NONE) }
    var held by remember { mutableStateOf<ChatMessage?>(null) }
    // A sheet coming up puts the keyboard away, as Discord does (OM, 2026-10-31).
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(sheet) {
        if (sheet != ChatSheet.NONE) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    var pickerTab by remember { mutableStateOf(1) }
    var whoEmoji by remember { mutableStateOf("") }
    var whoAt by remember { mutableStateOf(0L) }
    val clipboard = LocalClipboardManager.current
    val latestTyping by rememberUpdatedState(actions.onTyping)

    // The author of a line: a global message carries it; a direct one is you or them.
    fun nameOf(m: ChatMessage) = when {
        // The account's name (OM, 2026-10-31); the in-game name is only for the arena's own chat.
        !direct -> m.authorName.ifBlank { m.authorUsername }.ifBlank { "Player" }
        m.authorId == meId -> meName
        else -> peer?.displayName ?: "Player"
    }
    fun avatarOf(m: ChatMessage): Pair<String, String> = when {
        !direct -> m.authorAvatarUrl to m.authorAvatarKey
        m.authorId == meId -> meAvatarUrl to meAvatarKey
        else -> (peer?.avatarUrl.orEmpty()) to (peer?.avatarKey ?: "mono-ink")
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLinkPreview provides actions.linkPreview) {
    Box(Modifier.fillMaxSize().background(wyrmScopedPalette.paper)) {
        Column(Modifier.fillMaxSize().padding(top = insetTop)) {
            // Header: back, the room, search, and pins (global only).
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderButton(LucideR.drawable.lucide_ic_chevron_left, "Back", wyrmScopedPalette.ink, actions.onBack)
                Box(Modifier.weight(1f).padding(start = 4.dp)) {
                    if (header != null) header() else Column {
                        Text(title, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = wyrmScopedPalette.ink, maxLines = 1)
                        if (subtitle.isNotBlank()) Row(
                            Modifier.then(if (!direct) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sheet = ChatSheet.ACTIVE } else Modifier),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(wyrmScopedPalette.live))
                            Spacer(Modifier.width(5.dp))
                            Text(subtitle, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = wyrmScopedPalette.quiet, maxLines = 1)
                        }
                    }
                }
                HeaderButton(LucideR.drawable.lucide_ic_search, "Search", wyrmScopedPalette.ink) { sheet = ChatSheet.SEARCH }
                if (!direct) HeaderButton(LucideR.drawable.lucide_ic_pin, "Pinned messages", ChatPink) { sheet = ChatSheet.PINS }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
            Column(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ChatList(messages, meId, meUsername, direct, peer, ::nameOf, ::avatarOf,
                        // A hold on a reaction is "who reacted", not the message's hold.
                        onHold = { if (System.currentTimeMillis() - whoAt > 800) { held = it; sheet = ChatSheet.HOLD } },
                        onReply = actions.onReply,
                        onReact = actions.onReact,
                        onAddReaction = { held = it; sheet = ChatSheet.REACT },
                        onAuthor = actions.onAuthor,
                        onWho = { m, emoji -> whoAt = System.currentTimeMillis(); held = m; whoEmoji = emoji; sheet = ChatSheet.WHO })
                    // The @ list over the bottom of the chat (OM, 2026-11-07): every
                    // player, not only the room's; the room's and the online ones
                    // come first while nothing is typed, then the list narrows and
                    // sorts as each letter is typed (start of the username, start
                    // of the name, then anywhere in either).
                    val query = Regex("@([A-Za-z0-9_]{0,20})$").find(draft)?.groupValues?.get(1)
                    var remote by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                    LaunchedEffect(query, direct) {
                        if (direct || query == null) { remote = emptyList(); return@LaunchedEffect }
                        delay(180)
                        remote = runCatching { actions.mentionSearch(query) }.getOrDefault(emptyList())
                    }
                    val matches = if (direct || query == null) emptyList()
                        else (mentionables + activePeople.filter { it.username.isNotBlank() && it.id != meId }.map { it.username to it.name } + remote)
                            .distinctBy { it.first.lowercase() }
                            .filter { (username, _) -> !username.equals(meUsername, ignoreCase = true) }
                            .map { it to mentionRank(query, it.first, it.second) }
                            .filter { it.second >= 0 }
                            .sortedWith(compareBy<Pair<Pair<String, String>, Int>> { it.second }.thenBy { if (query.isEmpty()) 0 else it.first.first.length })
                            .map { it.first }
                            .take(8)
                    if (matches.isNotEmpty()) MentionList(matches, Modifier.align(Alignment.BottomCenter)) { username ->
                        actions.onDraftChange(draft.replace(Regex("@([A-Za-z0-9_]{0,20})$"), "@$username "))
                    }
                }
                // Who is typing, Discord's way.
                Row(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (typing.isNotEmpty()) {
                        TypingDots()
                        Spacer(Modifier.width(6.dp))
                        Text(typingLine(typing), fontFamily = Wyrm.Body, fontSize = 12.sp, color = wyrmScopedPalette.mute, maxLines = 1)
                    }
                }
                (editing ?: replying)?.let { target ->
                    Row(Modifier.fillMaxWidth().background(wyrmScopedPalette.well).padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (editing != null) "Editing message" else "Replying to ", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.mute)
                        if (editing == null) Text(nameOf(target), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, color = wyrmScopedPalette.ink, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(26.dp).clip(CircleShape).background(wyrmScopedPalette.card).clickable(onClick = actions.onCancelCompose), contentAlignment = Alignment.Center) {
                            Icon(painterResource(LucideR.drawable.lucide_ic_x), "Cancel", Modifier.size(13.dp), tint = wyrmScopedPalette.mute)
                        }
                    }
                }
                if (error.isNotBlank()) Text(error, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = wyrmScopedPalette.badge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Composer(
                    draft = draft, sending = sending,
                    placeholder = if (direct) "Message @${peer?.username ?: peer?.displayName ?: ""}" else "Message global chat",
                    bottomInset = if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) 0.dp else insetBottom,
                    onChange = { actions.onDraftChange(it); if (it.isNotBlank()) latestTyping() },
                    onSend = actions.onSend,
                    onEmoji = { pickerTab = 0; sheet = ChatSheet.PICKER },
                    onGif = { pickerTab = 1; sheet = ChatSheet.PICKER },
                )
            }
        }

        // Sheets over everything.
        SheetHost(open = sheet != ChatSheet.NONE, insetBottom = insetBottom, tall = sheet == ChatSheet.PICKER || sheet == ChatSheet.REACT || sheet == ChatSheet.SEARCH || sheet == ChatSheet.PINS || sheet == ChatSheet.WHO || sheet == ChatSheet.ACTIVE,
            onDismiss = { sheet = ChatSheet.NONE; held = null }) {
            when (sheet) {
                ChatSheet.HOLD -> held?.let { m ->
                    HoldSheet(m, mine = m.authorId == meId, direct = direct, canPin = canPin && !direct, name = nameOf(m),
                        onQuick = { emoji -> sheet = ChatSheet.NONE; actions.onReact(m, emoji) },
                        onMore = { sheet = ChatSheet.REACT },
                        onAction = { action ->
                            sheet = ChatSheet.NONE
                            when (action) {
                                "reply" -> actions.onReply(m)
                                "edit" -> actions.onEdit(m)
                                "copy" -> clipboard.setText(AnnotatedString(m.body))
                                "mention" -> actions.onDraftChange(draft + (if (draft.isEmpty() || draft.endsWith(" ")) "" else " ") + "@${m.authorUsername} ")
                                "pin" -> actions.onPin(m, m.pinned.isBlank())
                                "delete" -> actions.onDelete(m)
                                "report" -> actions.onReport(m)
                            }
                        })
                }
                ChatSheet.REACT -> EmojiGrid(header = "React with an emoji") { emoji ->
                    held?.let { actions.onReact(it, emoji) }
                    sheet = ChatSheet.NONE
                }
                ChatSheet.PICKER -> PickerSheet(pickerTab, onTab = { pickerTab = it }, gifs = actions.gifs,
                    onEmoji = { actions.onDraftChange(draft + it) },
                    onGif = { url -> sheet = ChatSheet.NONE; actions.onSendGif(url) })
                ChatSheet.SEARCH -> SearchSheet(direct, actions.search, meUsername, ::nameOf)
                ChatSheet.PINS -> PinsSheet(actions.pins, meUsername, ::nameOf, onUnpin = { actions.onPin(it, false) })
                ChatSheet.WHO -> held?.let { m ->
                    WhoReactedSheet(m, whoEmoji, actions.who, onPlayer = { id -> sheet = ChatSheet.NONE; actions.onAuthor(id) })
                }
                ChatSheet.ACTIVE -> ActiveSheet(subtitle, activePeople) { id -> sheet = ChatSheet.NONE; actions.onAuthor(id) }
                ChatSheet.NONE -> Unit
            }
        }
    }
    }
}

/** "{n} active" (OM, 2026-11-07): who has Wyrm open right now; a tap opens the profile. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ActiveSheet(title: String, people: List<ChatPerson>, onPlayer: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    Text(title.replaceFirstChar { it.uppercase() }, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = wyrmScopedPalette.ink,
        modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 2.dp))
    Text("On Wyrm right now. Players who hide their activity are counted, not listed.", fontFamily = Wyrm.Body, fontSize = 12.5.sp,
        color = wyrmScopedPalette.quiet, modifier = Modifier.padding(horizontal = 18.dp))
    LazyColumn(Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
        if (people.isEmpty()) item {
            Text("Nobody to show yet.", fontFamily = Wyrm.Body, fontSize = 13.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.padding(18.dp))
        }
        items(people, key = { it.id }) { person ->
            Row(Modifier.fillMaxWidth().clickable { onPlayer(person.id) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(wyrmScopedPalette.live))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name.ifBlank { "Player" }, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = wyrmScopedPalette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (person.username.isNotBlank()) Text("@" + person.username, fontFamily = Wyrm.Body, fontSize = 12.5.sp,
                        color = wyrmScopedPalette.quiet, maxLines = 1)
                }
                if (person.arena.isNotBlank()) Text("In an arena", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    color = wyrmScopedPalette.live)
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), label, Modifier.size(20.dp), tint = tint)
    }
}

/* ---------------------------------------------------------------- the list */

/** The top of a room with nothing older (OM, 2026-10-31): Discord's "start of the channel". */
@Composable
private fun ChatStart(direct: Boolean) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)) {
        Box(Modifier.size(52.dp).clip(wyrmRounded(16.dp)).background(wyrmScopedPalette.well), contentAlignment = Alignment.Center) {
            Icon(painterResource(LucideR.drawable.lucide_ic_message_circle), null, Modifier.size(24.dp), tint = wyrmScopedPalette.ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (direct) "This is the start of your chat" else "This is the start of Global chat",
            fontFamily = Wyrm.Display, fontSize = 22.sp, color = wyrmScopedPalette.ink)
        Spacer(Modifier.height(4.dp))
        Text(if (direct) "You've seen every message. Older ones are gone after 7 days."
            else "You're all caught up. Messages older than 7 days are cleared.",
            fontFamily = Wyrm.Body, fontSize = 13.sp, lineHeight = 18.sp, color = wyrmScopedPalette.quiet)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
    }
}

@Composable
private fun ChatList(
    messages: List<ChatMessage>, meId: String, meUsername: String, direct: Boolean, peer: ApiPlayer?,
    nameOf: (ChatMessage) -> String, avatarOf: (ChatMessage) -> Pair<String, String>,
    onHold: (ChatMessage) -> Unit, onReply: (ChatMessage) -> Unit,
    onReact: (ChatMessage, String) -> Unit, onAddReaction: (ChatMessage) -> Unit, onAuthor: (String) -> Unit,
    onWho: (ChatMessage, String) -> Unit = { _, _ -> },
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val listState = rememberLazyListState()
    var firstLoad by remember { mutableStateOf(true) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val paging = LocalChatPaging.current
    /* Older pages (OM, 2026-11-07: one swipe up loaded page after page, each
       jumping further up). The list used to start with a "top" item (the
       spinner) above the first message. LazyColumn keeps the first visible
       item's key in place when items are added before it (Compose "Lists":
       item keys), and at the top that item was the spinner, so after a page
       arrived the view stayed on the new oldest messages, near the top again,
       and asked for the next page: a cascade. Now the top item exists only
       when there is nothing older (the start card) or nothing at all; the
       spinner is drawn over the list. While pages remain, the first visible
       item is always a message, so the line being read stays exactly where
       it was with no manual scrolling, and a page is asked for only once the
       reader is near the top again. */
    val showTop = messages.isEmpty() || (paging != null && !paging.hasOlder)
    val topCount = if (showTop) 1 else 0
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (firstLoad) { listState.scrollToItem(messages.size + topCount); firstLoad = false }
        else if (listState.firstVisibleItemIndex >= messages.size - 12) listState.animateScrollToItem(messages.size + topCount)
    }
    LaunchedEffect(imeBottom > 0) { if (imeBottom > 0 && messages.isNotEmpty()) listState.animateScrollToItem(messages.size + topCount) }
    val latestPaging by rememberUpdatedState(paging)
    val latestMessages by rememberUpdatedState(messages)
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.firstVisibleItemIndex, latestPaging?.loading == true, latestMessages.firstOrNull()?.id) }
            .collect { (first, loading, _) ->
                val pages = latestPaging ?: return@collect
                if (!firstLoad && !loading && first <= 2 && pages.hasOlder && latestMessages.isNotEmpty() &&
                    listState.layoutInfo.totalItemsCount > 0) {
                    pages.onLoadOlder()
                }
            }
    }
    val lastMine = if (direct) messages.lastOrNull { it.authorId == meId } else null
    Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp)) {
        if (showTop) item(key = "top") {
            if (messages.isEmpty()) Text(if (direct) "Say hello when you are ready." else "Quiet right now. Messages stay here for 7 days.",
                fontFamily = Wyrm.Body, fontSize = 13.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.fillMaxWidth().padding(28.dp))
            else ChatStart(direct)
        }
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            val previous = messages.getOrNull(index - 1)
            val run = joinsRun(message, previous)
            val mentioned = !direct && message.authorId != meId && (meId in message.mentions ||
                (meUsername.isNotBlank() && message.body.contains("@$meUsername", ignoreCase = true)))
            val (url, key) = avatarOf(message)
            ChatLine(message, run, mentioned, meUsername, nameOf(message), url, key,
                onHold = { onHold(message) }, swipe = !direct, onReply = { onReply(message) },
                onReact = { onReact(message, it) }, onAddReaction = { onAddReaction(message) },
                onWho = { onWho(message, it) },
                onAuthor = { onAuthor(message.authorId) })
            if (message === lastMine) Text(if (message.readAt.isNotBlank()) "Seen" else "Sent", fontFamily = Wyrm.Body,
                fontSize = 11.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        item(key = "bottom") { Spacer(Modifier.height(2.dp)) }
    }
    if (paging?.loading == true) Row(
        Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(CircleShape).background(wyrmScopedPalette.card)
            .border(1.dp, wyrmScopedPalette.rule, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IosSpinner(size = 14.dp, colour = wyrmScopedPalette.quiet)
        Spacer(Modifier.width(8.dp))
        Text("Loading messages", fontFamily = Wyrm.Body, fontSize = 12.sp, color = wyrmScopedPalette.mute)
    }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatLine(
    message: ChatMessage, run: Boolean, mentioned: Boolean, meUsername: String, name: String,
    avatarUrl: String, avatarKey: String,
    onHold: () -> Unit, onReply: () -> Unit, onReact: (String) -> Unit, onAddReaction: () -> Unit, onAuthor: () -> Unit,
    // Direct messages have no replies on the server, so no swipe there.
    swipe: Boolean = true,
    /** Hold a reaction: who reacted with it. */
    onWho: (String) -> Unit = {},
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    Box(
        Modifier.fillMaxWidth()
            .background(if (mentioned) MentionWash else Color.Transparent)
            .drawBehind { if (mentioned) drawRect(MentionBar, Offset.Zero, androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
            .pointerInput(message.id, swipe) {
                if (!swipe) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val reply = drag.value <= -threshold
                        scope.launch { drag.animateTo(0f, tween(180)) }
                        if (reply) onReply()
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, tween(180)) } },
                ) { change, amount ->
                    val next = (drag.value + amount).coerceIn(-threshold * 1.3f, 0f)
                    if (next != drag.value) { change.consume(); scope.launch { drag.snapTo(next) } }
                }
            }
            .pointerInput(message.id) { detectTapGestures(onLongPress = { onHold() }) },
    ) {
        // The reply arrow that comes in from the right while you swipe.
        val shown = (-drag.value / threshold).coerceIn(0f, 1f)
        if (shown > 0f) Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(30.dp).clip(CircleShape).background(wyrmScopedPalette.well),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(LucideR.drawable.lucide_ic_reply), null, Modifier.size(15.dp), tint = wyrmScopedPalette.mute.copy(alpha = shown))
        }
        Column(Modifier.offset { IntOffset(drag.value.roundToInt(), 0) }.padding(start = 14.dp, end = 14.dp, top = if (run) 2.dp else 12.dp, bottom = 2.dp)) {
            message.replyTo?.let { reply ->
                // Discord's reply line: a curve into a small quote above the name.
                Row(Modifier.padding(start = 20.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(26.dp).height(10.dp).offset(y = 5.dp).drawBehind {
                        val stroke = 2.dp.toPx()
                        drawLine(wyrmScopedPalette.rule, Offset(stroke / 2, size.height), Offset(stroke / 2, stroke), stroke)
                        drawLine(wyrmScopedPalette.rule, Offset(0f, stroke / 2), Offset(size.width - 4.dp.toPx(), stroke / 2), stroke)
                    })
                    Text("@" + reply.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = wyrmScopedPalette.mute, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                    Text(if (isChatGif(reply.body)) "GIF" else reply.body, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.quiet,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row {
                if (run) {
                    Box(Modifier.width(40.dp))
                } else {
                    Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAuthor)) {
                        WyrmAvatar(url = avatarUrl, avatarKey = avatarKey, initial = name.take(1).uppercase(), size = 40.dp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (!run) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = wyrmScopedPalette.ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAuthor))
                        if (message.badge.isNotBlank()) {
                            Spacer(Modifier.width(5.dp))
                            Text(message.badge, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(wyrmScopedPalette.live).padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        if (message.pinned.isNotBlank()) {
                            Spacer(Modifier.width(5.dp))
                            Icon(painterResource(LucideR.drawable.lucide_ic_pin), "Pinned", Modifier.size(12.dp), tint = ChatPink)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(discordTime(message.createdAt), fontFamily = Wyrm.Body, fontSize = 11.sp, color = wyrmScopedPalette.quiet, maxLines = 1)
                    }
                    if (isChatGif(message.body)) {
                        RemoteAnimatedImage(message.body.trim(), Modifier.padding(top = 4.dp).widthIn(max = 230.dp).heightIn(max = 230.dp).clip(RoundedCornerShape(10.dp)))
                    } else {
                        // Discord markdown, line breaks and links (OM, 2026-11-07; ChatMarkdown.kt).
                        val shown = remember(message.body, message.editedAt, meUsername, wyrmScopedPalette) {
                            buildAnnotatedString {
                                append(chatMarkdown(message.body, meUsername, wyrmScopedPalette))
                                if (message.editedAt.isNotBlank()) withStyle(SpanStyle(fontSize = 10.5.sp, color = wyrmScopedPalette.quiet)) { append("  (edited)") }
                            }
                        }
                        androidx.compose.material3.Text(
                            shown,
                            fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 21.sp, color = wyrmScopedPalette.ink,
                        )
                        // Every link's embed, as Discord (OM, 2026-11-07).
                        chatLinks(message.body).forEach { ChatLinkEmbed(it) }
                    }
                    if (message.reactions.isNotEmpty()) FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.reactions.forEach { reaction ->
                            Row(Modifier.height(26.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (reaction.mine) wyrmScopedPalette.link.copy(alpha = 0.14f) else wyrmScopedPalette.well)
                                .border(1.dp, if (reaction.mine) wyrmScopedPalette.link else Color.Transparent, RoundedCornerShape(8.dp))
                                // Tap: add or take back yours. Hold: who reacted (Discord).
                                .combinedClickable(onClick = { onReact(reaction.emoji) }, onLongClick = { onWho(reaction.emoji) })
                                .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(reaction.emoji, fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(reaction.count.toString(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                                    color = if (reaction.mine) wyrmScopedPalette.link else wyrmScopedPalette.mute)
                            }
                        }
                        Box(Modifier.height(26.dp).clip(RoundedCornerShape(8.dp)).background(wyrmScopedPalette.well).clickable(onClick = onAddReaction)
                            .padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                            Icon(painterResource(LucideR.drawable.lucide_ic_smile_plus), "Add a reaction", Modifier.size(15.dp), tint = wyrmScopedPalette.quiet)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val phase by rememberInfiniteTransition(label = "typing").animateFloat(0f, 3f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val lit = ((phase - i + 3) % 3) < 1f
            Box(Modifier.size(5.dp).offset(y = if (lit) (-2).dp else 0.dp).clip(CircleShape).background(wyrmScopedPalette.mute.copy(alpha = if (lit) 1f else 0.3f)))
        }
    }
}

@Composable
private fun MentionList(matches: List<Pair<String, String>>, modifier: Modifier, onPick: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(wyrmScopedPalette.card)
        .border(1.dp, wyrmScopedPalette.rule, RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState())) {
        Text("MEMBERS", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 1.sp, color = wyrmScopedPalette.quiet,
            modifier = Modifier.padding(start = 12.dp, top = 9.dp, bottom = 4.dp))
        matches.forEach { (username, name) ->
            Row(Modifier.fillMaxWidth().clickable { onPick(username) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = wyrmScopedPalette.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text("@$username", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.quiet, maxLines = 1)
            }
        }
    }
}

/* ---------------------------------------------------------------- composer */

@Composable
private fun Composer(
    draft: String, sending: Boolean, placeholder: String, bottomInset: Dp,
    onChange: (String) -> Unit, onSend: () -> Unit, onEmoji: () -> Unit, onGif: () -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    /* The field keeps its own selection (OM, 2026-11-07): with text selected,
       Discord's format bar comes up over it. Enter makes a new line; Send sends. */
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draft)) }
    if (field.text != draft) field = androidx.compose.ui.text.input.TextFieldValue(draft, androidx.compose.ui.text.TextRange(draft.length))
    fun set(next: androidx.compose.ui.text.input.TextFieldValue) {
        val clipped = if (next.text.length > 1000) next.copy(text = next.text.take(1000)) else next
        field = clipped
        if (clipped.text != draft) onChange(clipped.text)
    }
    Column(Modifier.fillMaxWidth().background(wyrmScopedPalette.paper)) {
    if (!field.selection.collapsed) ChatFormatBar(field, ::set)
    // Plain and quiet (OM, 2026-10-30): one field, emoji and GIF inside it, Send beside it.
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 8.dp + bottomInset),
        verticalAlignment = Alignment.Bottom) {
        Row(Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(21.dp)).background(wyrmScopedPalette.well)
            .border(1.dp, wyrmScopedPalette.rule, RoundedCornerShape(21.dp)).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (draft.isEmpty()) Text(placeholder, fontFamily = Wyrm.Body, fontSize = 15.sp, color = wyrmScopedPalette.mute, maxLines = 1)
                BasicTextField(value = field, onValueChange = ::set,
                    textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = wyrmScopedPalette.ink), cursorBrush = SolidColor(wyrmScopedPalette.ink),
                    maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onEmoji), contentAlignment = Alignment.Center) {
                Icon(painterResource(LucideR.drawable.lucide_ic_smile), "Emoji", Modifier.size(20.dp), tint = wyrmScopedPalette.mute)
            }
            Box(Modifier.padding(start = 2.dp, end = 2.dp).clip(RoundedCornerShape(6.dp)).border(1.6.dp, wyrmScopedPalette.mute, RoundedCornerShape(6.dp))
                .clickable(onClick = onGif).padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text("GIF", fontFamily = Wyrm.Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, color = wyrmScopedPalette.mute)
            }
        }
        Spacer(Modifier.width(8.dp))
        val ready = draft.isNotBlank() && !sending
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (ready) wyrmScopedPalette.ink else wyrmScopedPalette.well).clickable(enabled = ready, onClick = onSend),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(LucideR.drawable.lucide_ic_send_horizontal), "Send", Modifier.size(18.dp), tint = if (ready) wyrmScopedPalette.onInk else wyrmScopedPalette.mute)
        }
    }
    }
}

/* ---------------------------------------------------------------- sheets */

@Composable
private fun SheetHost(open: Boolean, insetBottom: Dp, tall: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    AnimatedVisibility(open, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(open, enter = slideInVertically(tween(260)) { it }, exit = slideOutVertically(tween(220)) { it }) {
            Column(Modifier.fillMaxWidth().then(if (tall) Modifier.fillMaxHeight(0.72f) else Modifier)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(wyrmScopedPalette.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(bottom = insetBottom + 10.dp)) {
                Box(Modifier.padding(top = 8.dp, bottom = 10.dp).size(38.dp, 5.dp).clip(CircleShape).background(wyrmScopedPalette.rule).align(Alignment.CenterHorizontally))
                content()
            }
        }
    }
}

@Composable
private fun HoldSheet(
    message: ChatMessage, mine: Boolean, direct: Boolean, canPin: Boolean, name: String,
    onQuick: (String) -> Unit, onMore: () -> Unit, onAction: (String) -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        QUICK_REACTIONS.forEach { emoji ->
            val on = message.reactions.any { it.emoji == emoji && it.mine }
            Box(Modifier.size(50.dp).clip(CircleShape).background(if (on) wyrmScopedPalette.link.copy(alpha = 0.14f) else wyrmScopedPalette.well)
                .border(if (on) 2.dp else 0.dp, if (on) wyrmScopedPalette.link else Color.Transparent, CircleShape)
                .clickable { onQuick(emoji) }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
        }
        Box(Modifier.size(50.dp).clip(CircleShape).background(wyrmScopedPalette.well).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(painterResource(LucideR.drawable.lucide_ic_smile_plus), "More reactions", Modifier.size(22.dp), tint = wyrmScopedPalette.mute)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = wyrmScopedPalette.ink)) { append(name) }
        append(" · "); append(if (isChatGif(message.body)) "GIF" else message.body.take(90))
    }, fontFamily = Wyrm.Body, fontSize = 13.sp, color = wyrmScopedPalette.mute, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(wyrmScopedPalette.well).padding(12.dp))
    if (!direct) SheetAction(LucideR.drawable.lucide_ic_reply, "Reply") { onAction("reply") }
    if (mine && !isChatGif(message.body)) SheetAction(LucideR.drawable.lucide_ic_pencil, "Edit message") { onAction("edit") }
    if (!isChatGif(message.body)) SheetAction(LucideR.drawable.lucide_ic_copy, "Copy text") { onAction("copy") }
    if (!direct && !mine && message.authorUsername.isNotBlank()) SheetAction(LucideR.drawable.lucide_ic_at_sign, "Mention") { onAction("mention") }
    if (canPin) SheetAction(if (message.pinned.isBlank()) LucideR.drawable.lucide_ic_pin else LucideR.drawable.lucide_ic_pin_off,
        if (message.pinned.isBlank()) "Pin message" else "Unpin message", tint = ChatPink) { onAction("pin") }
    if (mine) SheetAction(LucideR.drawable.lucide_ic_trash_2, "Delete message", tint = wyrmScopedPalette.badge) { onAction("delete") }
    else if (!direct) SheetAction(LucideR.drawable.lucide_ic_flag, "Report", tint = wyrmScopedPalette.badge) { onAction("report") }
}

@Composable
private fun SheetAction(icon: Int, label: String, tint: Color = Wyrm.Ink, onClick: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(19.dp), tint = if (tint == wyrmScopedPalette.ink) wyrmScopedPalette.mute else tint)
        Spacer(Modifier.width(14.dp))
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = tint)
    }
}

@Composable
private fun EmojiGrid(header: String?, onPick: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(Modifier.fillMaxSize()) {
        if (header != null) Text(header, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = wyrmScopedPalette.ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            CHAT_EMOJI.forEach { (group, list) ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(8) }, key = "h-$group") {
                    Text(group.uppercase(), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 1.sp,
                        color = wyrmScopedPalette.quiet, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp))
                }
                items(list, key = { "$group-$it" }) { emoji ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { onPick(emoji) }, contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 25.sp)
                    }
                }
            }
        }
    }
}

/** The picker's category row: label to search term ("" = trending). Same list as iOS. */
private val GIF_CATEGORIES = listOf(
    "Trending" to "", "Happy" to "happy", "LOL" to "lol", "Love" to "love", "Sad" to "sad", "Wow" to "wow",
    "Angry" to "angry", "Dance" to "dance", "GG" to "gg", "Hype" to "hype", "Thanks" to "thank you",
    "Hi" to "hello", "Bye" to "bye", "Yes" to "yes", "No" to "no", "Cute" to "cute", "Snake" to "snake",
)

/** Emoji, GIFs and Stickers behind the GIF button; GIFs and stickers from KLIPY. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PickerSheet(tab: Int, onTab: (Int) -> Unit, gifs: suspend (Boolean, String) -> List<GifItem>,
                        onEmoji: (String) -> Unit, onGif: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var query by remember(tab) { mutableStateOf("") }
    var items by remember(tab) { mutableStateOf<List<GifItem>?>(null) }
    LaunchedEffect(tab, query) {
        if (tab == 0) return@LaunchedEffect
        if (query.isNotEmpty()) delay(350)
        items = null
        items = runCatching { gifs(tab == 2, query.trim()) }.getOrDefault(emptyList())
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Emoji", "GIFs", "Stickers").forEachIndexed { index, label ->
            Box(Modifier.weight(1f).height(34.dp).clip(CircleShape).background(if (index == tab) wyrmScopedPalette.ink else wyrmScopedPalette.well).clickable { onTab(index) },
                contentAlignment = Alignment.Center) {
                Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, color = if (index == tab) wyrmScopedPalette.onInk else wyrmScopedPalette.mute)
            }
        }
    }
    if (tab == 0) { EmojiGrid(header = null, onPick = onEmoji); return }
    Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).background(wyrmScopedPalette.well)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(LucideR.drawable.lucide_ic_search), null, Modifier.size(16.dp), tint = wyrmScopedPalette.quiet)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(if (tab == 1) "Search GIFs" else "Search stickers", fontFamily = Wyrm.Body, fontSize = 14.sp, color = wyrmScopedPalette.quiet)
            BasicTextField(query, { query = it.take(60) }, singleLine = true, cursorBrush = SolidColor(wyrmScopedPalette.ink),
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 14.sp, color = wyrmScopedPalette.ink), modifier = Modifier.fillMaxWidth())
        }
    }
    // Categories, as Discord's picker (OM, 2026-10-31): each one is a search.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GIF_CATEGORIES.forEach { (label, term) ->
            val on = query.trim().equals(term, ignoreCase = true)
            Box(Modifier.height(30.dp).clip(CircleShape).background(if (on) wyrmScopedPalette.ink else wyrmScopedPalette.well)
                .clickable { query = term }.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (on) wyrmScopedPalette.onInk else wyrmScopedPalette.mute)
            }
        }
    }
    val list = items
    when {
        list == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { IosSpinner(size = 18.dp, colour = wyrmScopedPalette.quiet) }
        list.isEmpty() -> Text(if (query.isBlank()) "GIFs are not available right now." else "Nothing found.", fontFamily = Wyrm.Body,
            fontSize = 13.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.fillMaxWidth().padding(28.dp))
        else -> LazyVerticalGrid(GridCells.Fixed(if (tab == 2) 4 else 2), Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id + it.url }) { gif ->
                Box(Modifier.aspectRatio(if (tab == 2) 1f else (gif.width.toFloat() / gif.height.coerceAtLeast(1)).coerceIn(0.6f, 2f))
                    .clip(RoundedCornerShape(10.dp)).background(wyrmScopedPalette.well).clickable { onGif(gif.url) }) {
                    RemoteAnimatedImage(gif.preview, Modifier.fillMaxSize(), crop = tab == 1)
                }
            }
        }
    }
    Text("Powered by KLIPY", fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = wyrmScopedPalette.quiet,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SearchSheet(direct: Boolean, search: suspend (String) -> List<ChatMessage>, meUsername: String, nameOf: (ChatMessage) -> String) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChatMessage>?>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        results = null
        results = runCatching { search(query.trim()) }.getOrDefault(emptyList())
    }
    Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(wyrmScopedPalette.well)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(LucideR.drawable.lucide_ic_search), null, Modifier.size(17.dp), tint = wyrmScopedPalette.quiet)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(if (direct) "Search this chat" else "Search global chat", fontFamily = Wyrm.Body, fontSize = 15.sp, color = wyrmScopedPalette.quiet)
            BasicTextField(query, { query = it.take(60) }, singleLine = true, cursorBrush = SolidColor(wyrmScopedPalette.ink),
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = wyrmScopedPalette.ink), modifier = Modifier.fillMaxWidth())
        }
    }
    val list = results
    when {
        list == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { IosSpinner(size = 18.dp, colour = wyrmScopedPalette.quiet) }
        list.isEmpty() -> Text(if (query.trim().length < 2) "Type at least two letters." else "No messages found.", fontFamily = Wyrm.Body,
            fontSize = 13.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        else -> LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { message -> CompactLine(message, nameOf(message), meUsername) }
        }
    }
}

/** Who reacted (OM, 2026-10-31): one tab per emoji with its count, then the players. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.WhoReactedSheet(
    message: ChatMessage, first: String,
    load: suspend (ChatMessage) -> List<Pair<String, List<com.wyrm.omrajput.data.ReactionPerson>>>,
    onPlayer: (String) -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var groups by remember(message.id) { mutableStateOf<List<Pair<String, List<com.wyrm.omrajput.data.ReactionPerson>>>?>(null) }
    var tab by remember(message.id) { mutableStateOf(first) }
    LaunchedEffect(message.id) { groups = runCatching { load(message) }.getOrDefault(emptyList()) }
    Text("Reactions", fontFamily = Wyrm.Display, fontSize = 21.sp, color = wyrmScopedPalette.ink, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))
    val list = groups
    if (list == null) {
        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { IosSpinner(size = 18.dp, colour = wyrmScopedPalette.quiet) }
        return
    }
    if (list.isEmpty()) {
        Text("No reactions any more.", fontFamily = Wyrm.Body, fontSize = 13.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.padding(18.dp))
        return
    }
    val shown = list.firstOrNull { it.first == tab } ?: list.first()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { (emoji, people) ->
            val on = emoji == shown.first
            Row(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) wyrmScopedPalette.ink else wyrmScopedPalette.well)
                .clickable { tab = emoji }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(people.size.toString(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (on) wyrmScopedPalette.onInk else wyrmScopedPalette.mute)
            }
        }
    }
    LazyColumn(Modifier.weight(1f)) {
        items(shown.second, key = { it.id }) { person ->
            Row(Modifier.fillMaxWidth().clickable { onPlayer(person.id) }.padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                WyrmAvatar(person.avatarUrl, person.avatarKey, person.name.take(1).uppercase(), 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = wyrmScopedPalette.ink, maxLines = 1)
                    if (person.username.isNotBlank()) Text("@" + person.username, fontFamily = Wyrm.Body, fontSize = 12.sp, color = wyrmScopedPalette.quiet, maxLines = 1)
                }
                Text(shown.first, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PinsSheet(load: suspend () -> Pair<List<ChatMessage>, Boolean>, meUsername: String, nameOf: (ChatMessage) -> String,
                      onUnpin: (ChatMessage) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var state by remember { mutableStateOf<Pair<List<ChatMessage>, Boolean>?>(null) }
    LaunchedEffect(Unit) { state = runCatching { load() }.getOrDefault(emptyList<ChatMessage>() to false) }
    Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(LucideR.drawable.lucide_ic_pin), null, Modifier.size(17.dp), tint = ChatPink)
        Spacer(Modifier.width(8.dp))
        Text("Pinned messages", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = wyrmScopedPalette.ink)
    }
    val current = state
    when {
        current == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { IosSpinner(size = 18.dp, colour = wyrmScopedPalette.quiet) }
        current.first.isEmpty() -> Text("Nothing pinned yet.", fontFamily = Wyrm.Body, fontSize = 13.sp, color = wyrmScopedPalette.quiet,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        else -> LazyColumn(Modifier.weight(1f)) {
            items(current.first, key = { it.id }) { message ->
                Box {
                    CompactLine(message, nameOf(message), meUsername)
                    if (current.second) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(30.dp).clip(CircleShape).background(wyrmScopedPalette.well)
                        .clickable { onUnpin(message); state = current.first.filterNot { it.id == message.id } to current.second },
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(LucideR.drawable.lucide_ic_pin_off), "Unpin", Modifier.size(14.dp), tint = ChatPink)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactLine(message: ChatMessage, name: String, meUsername: String) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        WyrmAvatar(url = message.authorAvatarUrl, avatarKey = message.authorAvatarKey, initial = name.take(1).uppercase(), size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = wyrmScopedPalette.ink, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(discordTime(message.createdAt), fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = wyrmScopedPalette.quiet, maxLines = 1)
            }
            if (isChatGif(message.body)) RemoteAnimatedImage(message.body.trim(), Modifier.padding(top = 3.dp).widthIn(max = 160.dp).heightIn(max = 120.dp).clip(RoundedCornerShape(8.dp)))
            else Text(chatText(message.body, meUsername), fontFamily = Wyrm.Body, fontSize = 14.sp, color = wyrmScopedPalette.ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---------------------------------------------------------------- GIFs */

/**
 * A GIF or animated WebP from KLIPY, playing (Android 9+ through ImageDecoder;
 * older phones show its first frame). Bytes kept in memory (24 MB) so a
 * scrolled-back GIF does not download again.
 */
private object ChatGifBytes {
    val cache = object : android.util.LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    suspend fun load(url: String): ByteArray? = cache.get(url) ?: withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 20_000 }
            try {
                if (connection.responseCode !in 200..299 || connection.contentLengthLong > 8L * 1024 * 1024) null
                else connection.inputStream.use { it.readBytes() }
            } finally { connection.disconnect() }
        }.getOrNull()?.also { cache.put(url, it) }
    }
}

@Composable
internal fun RemoteAnimatedImage(url: String, modifier: Modifier = Modifier, crop: Boolean = false) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var bytes by remember(url) { mutableStateOf(ChatGifBytes.cache.get(url)) }
    LaunchedEffect(url) { if (bytes == null) bytes = ChatGifBytes.load(url) }
    val data = bytes
    if (data == null) {
        Box(modifier.background(wyrmScopedPalette.well).size(160.dp, 110.dp))
        return
    }
    AndroidView(
        factory = { context -> ImageView(context).apply { adjustViewBounds = true } },
        update = { view ->
            if (view.tag == url) return@AndroidView
            view.tag = url
            view.scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (Build.VERSION.SDK_INT >= 28) {
                runCatching {
                    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(data)))
                    view.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                }.onFailure { BitmapFactory.decodeByteArray(data, 0, data.size)?.let(view::setImageBitmap) }
            } else {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.let(view::setImageBitmap)
            }
        },
        modifier = modifier,
    )
}

/** A direct thread's header: their face (online dot), name, and what they are doing. */
@Composable
internal fun ThreadChatHeader(peer: ApiPlayer?, activity: com.wyrm.omrajput.data.FriendActivity?, onOpen: () -> Unit, onJoin: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Row(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically) {
        WyrmAvatar(url = peer?.avatarUrl.orEmpty(), avatarKey = peer?.avatarKey ?: "mono-ink",
            initial = (peer?.displayName ?: "?").take(1).uppercase(), size = 34.dp, online = activity?.online == true)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(peer?.displayName ?: "Message", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = wyrmScopedPalette.ink, maxLines = 1)
            if (activity != null && (activity.online || activity.lastArena != null)) FriendActivityLine(activity, fontSize = 11.5.sp, onJoin = onJoin)
            else if (!peer?.username.isNullOrBlank()) Text("@" + peer?.username, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = wyrmScopedPalette.quiet, maxLines = 1)
        }
    }
}
