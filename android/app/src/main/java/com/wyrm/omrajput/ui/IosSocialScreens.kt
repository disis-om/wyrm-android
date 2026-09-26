package com.wyrm.omrajput.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.wyrm.omrajput.data.ApiPlayer
import com.wyrm.omrajput.data.ChatMessage
import com.wyrm.omrajput.data.Conversation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/*
 * Social, as Wyrm iOS draws it (WyrmDesignDetails.swift, WyrmSocialExtras.swift,
 * WyrmChatUI.swift): Leaderboard, Messages, a direct thread, Global chat,
 * Connections, Profile and Edit profile. Every page sits in the same chrome —
 * "‹ Back", a centred title, an optional action — and pulls to refresh.
 */

private fun groupedNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun initialsOf(name: String): String {
    val letters = name.split(" ").filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
    return letters.ifEmpty { "W" }
}

/** Global chat's refusals, worded as on iOS. */
fun iosChatError(code: String): String = when (code) {
    "PROFILE_INCOMPLETE" -> "Choose a username or arena name before chatting."
    "MESSAGE_RATE_LIMITED" -> "Slow down a little — try again in a moment."
    "INVALID_MESSAGE" -> "That message could not be sent."
    else -> code
}

/* ---------------------------------------------------------------- chrome */

@Composable
fun IosPageChrome(
    title: String,
    insetTop: Dp,
    onBack: () -> Unit,
    actionTitle: String = "",
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Wyrm.Paper).padding(top = insetTop)) {
        Box(Modifier.fillMaxWidth().height(52.dp).background(Wyrm.Paper).padding(horizontal = 18.dp)) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                IosIcon(IosGlyph.CHEVRON_LEFT, Wyrm.Link, size = 15.dp, weight = 2.6f)
                Text("Back", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Wyrm.Link)
            }
            Text(
                title,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Wyrm.Ink,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 80.dp),
            )
            if (actionTitle.isNotEmpty()) {
                Text(
                    actionTitle,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAction?.invoke() },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        content()
    }
}

/** SwiftUI `.refreshable`: pull, a spinner at the top, release to refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosRefreshable(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            val fraction = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = fraction
                        translationY = 14.dp.toPx() * fraction
                    },
            ) { if (fraction > 0.02f) IosSpinner(size = 22.dp) }
        },
    ) { content() }
}

/* ----------------------------------------------------------- leaderboard */

@Composable
fun IosLeaderboardScreen(
    score: List<ApiPlayer>,
    kills: List<ApiPlayer>,
    sort: Int,
    refreshing: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onSort: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
) {
    val rows = if (sort == 0) score else kills
    IosPageChrome("Leaderboard", insetTop, onBack) {
        IosRefreshable(refreshing, onRefresh, Modifier.weight(1f)) {
            // Lazy: only the rows on screen are built, so switching boards is instant.
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "sort") {
                    LiquidSegmented(listOf("Score", "Kills"), sort, onSort, Modifier.padding(16.dp).fillMaxWidth())
                }
                if (rows.isEmpty()) {
                    item(key = "empty") { IosPaperCard { IosEmptyPanel("No ranked players yet", "Finished runs will appear here.") } }
                }
                itemsIndexed(rows, key = { _, player -> "$sort-${player.id}" }) { index, player ->
                    Box(Modifier.padding(horizontal = 16.dp).cardSegment(first = index == 0, last = index == rows.lastIndex)) {
                        PlayerRankRow(index + 1, player, if (sort == 0) player.highestScore else player.kills) { onOpenPlayer(player.id) }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp + insetBottom)) }
            }
        }
    }
}

/** One slice of a WyrmPaperCard, so a lazy list can still read as one card. */
private fun Modifier.cardSegment(first: Boolean, last: Boolean): Modifier = this.drawWithContent {
    val r = 17.dp.toPx()
    val stroke = 1.dp.toPx()
    val top = if (first) r else 0f
    val bottom = if (last) r else 0f
    val shape = androidx.compose.ui.geometry.RoundRect(
        0f, 0f, size.width, size.height,
        topLeftCornerRadius = CornerRadius(top), topRightCornerRadius = CornerRadius(top),
        bottomRightCornerRadius = CornerRadius(bottom), bottomLeftCornerRadius = CornerRadius(bottom),
    )
    val path = Path().apply { addRoundRect(shape) }
    drawPath(path, Wyrm.Card.copy(alpha = 0.92f))
    clipPath(path) { this@drawWithContent.drawContent() }
    // The rule: both sides always, the top on the first slice, the bottom on the last.
    val half = stroke / 2f
    val outline = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                half, if (first) half else -stroke, size.width - half, if (last) size.height - half else size.height + stroke,
                topLeftCornerRadius = CornerRadius(top), topRightCornerRadius = CornerRadius(top),
                bottomRightCornerRadius = CornerRadius(bottom), bottomLeftCornerRadius = CornerRadius(bottom),
            ),
        )
    }
    clipRect(0f, 0f, size.width, size.height) {
        drawPath(outline, Wyrm.Rule, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    }
}

@Composable
private fun PlayerRankRow(rank: Int, player: ApiPlayer, value: Long, onClick: () -> Unit) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$rank", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Wyrm.Quiet,
                textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
            WyrmAvatar(player.avatarUrl, player.avatarKey, initialsOf(player.displayName), 35.dp)
            PersonText(player.displayName, player.handle, Modifier.weight(1f))
            Text(groupedNumber(value), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Wyrm.Ink)
        }
        RowRule(58.dp, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun PersonText(name: String, handle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Wyrm.Ink, maxLines = 1)
        if (handle.isNotEmpty()) Text(handle, fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = Wyrm.Quiet, maxLines = 1)
    }
}

@Composable
private fun RowRule(start: Dp, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = start).fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
}

/* -------------------------------------------------------------- messages */

@Composable
fun IosMessagesScreen(
    conversations: List<Conversation>,
    candidates: List<ApiPlayer>,
    refreshing: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenThread: (ApiPlayer) -> Unit,
) {
    IosPageChrome("Messages", insetTop, onBack) {
        IosRefreshable(refreshing, onRefresh, Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                IosSectionLabel("Conversations")
                IosPaperCard {
                    if (conversations.isEmpty()) IosEmptyPanel("No messages yet", "Mutual follows can start a private conversation.")
                    conversations.forEach { row ->
                        IosListRow(
                            title = row.player.displayName,
                            detail = row.lastMessage.ifEmpty { "No messages yet" },
                            value = if (row.unread > 0) "${row.unread}" else "",
                            glyph = IosGlyph.PERSON_CIRCLE,
                            tint = Wyrm.Link,
                        ) { onOpenThread(row.player) }
                    }
                }
                if (candidates.isNotEmpty()) {
                    IosSectionLabel("People you can message")
                    IosPaperCard {
                        candidates.forEach { person ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenThread(person) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                WyrmAvatar(person.avatarUrl, person.avatarKey, initialsOf(person.displayName), 35.dp)
                                PersonText(person.displayName, person.handle, Modifier.weight(1f))
                                IosIcon(IosGlyph.MESSAGE, Wyrm.Link, size = 20.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp + insetBottom))
            }
        }
    }
}

/* ------------------------------------------------------------ chat pages */

@Composable
fun IosThreadScreen(
    title: String,
    messages: List<ChatMessage>,
    meId: String,
    draft: String,
    sending: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    IosPageChrome(title, insetTop, onBack) {
        IosChatPage(
            messages = messages,
            meId = meId,
            showsAuthors = false,
            emptyTitle = "Quiet so far",
            emptyNote = "Say hello when you are ready.",
            error = error,
            draft = draft,
            placeholder = "Message $title",
            limit = 1000,
            sending = sending,
            insetBottom = insetBottom,
            onDraftChange = onDraftChange,
            onSend = onSend,
        )
    }
}

@Composable
fun IosGlobalChatScreen(
    messages: List<ChatMessage>,
    meId: String,
    draft: String,
    sending: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onReport: (ChatMessage, String) -> Unit,
) {
    var reporting by remember { mutableStateOf<ChatMessage?>(null) }
    Box(Modifier.fillMaxSize()) {
        IosPageChrome("Global chat", insetTop, onBack) {
            IosChatPage(
                messages = messages,
                meId = meId,
                showsAuthors = true,
                emptyTitle = "Quiet right now",
                emptyNote = "Messages stay here for 24 hours. Say hello.",
                error = error,
                draft = draft,
                placeholder = "Message everyone",
                limit = 280,
                sending = sending,
                insetBottom = insetBottom,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onAuthor = onOpenAuthor,
                onReport = { reporting = it },
            )
        }
        reporting?.let { message ->
            IosActionSheet(
                title = "Report message",
                actions = listOf("Spam", "Harassment or abuse", "Hate or slurs", "Something else").map { reason ->
                    IosSheetAction(reason) { onReport(message, reason) }
                },
                insetBottom = insetBottom,
                onDismiss = { reporting = null },
            )
        }
    }
}

@Composable
private fun ColumnScope.IosChatPage(
    messages: List<ChatMessage>,
    meId: String,
    showsAuthors: Boolean,
    emptyTitle: String,
    emptyNote: String,
    error: String,
    draft: String,
    placeholder: String,
    limit: Int,
    sending: Boolean,
    insetBottom: Dp,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAuthor: ((String) -> Unit)? = null,
    onReport: ((ChatMessage) -> Unit)? = null,
) {
    val backdrop = rememberLayerBackdrop()
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(Modifier.weight(1f).fillMaxWidth().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().layerBackdrop(backdrop)) {
            IosChatTranscript(messages, meId, showsAuthors, emptyTitle, emptyNote, onAuthor, onReport)
        }
        AnimatedVisibility(error.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Text(
                error,
                fontFamily = Wyrm.Body,
                fontSize = 11.5.sp,
                color = Wyrm.Badge,
                modifier = Modifier.fillMaxWidth().background(Wyrm.Paper).padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        IosChatComposer(
            text = draft,
            onText = onDraftChange,
            placeholder = placeholder,
            limit = limit,
            sending = sending,
            bottomInset = if (imeUp) 0.dp else insetBottom,
            backdrop = backdrop,
            onSend = onSend,
        )
    }
}

/**
 * `WyrmChatTranscript`: consecutive messages from one author share a name
 * line and tighten into a run; the last of a run carries its time. New
 * bubbles spring in from their author's corner and the list follows them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosChatTranscript(
    messages: List<ChatMessage>,
    meId: String,
    showsAuthors: Boolean,
    emptyTitle: String,
    emptyNote: String,
    onAuthor: ((String) -> Unit)?,
    onReport: ((ChatMessage) -> Unit)?,
) {
    val listState = rememberLazyListState()
    val seen = remember { mutableSetOf<String>() }
    val firstLoad = remember { mutableStateOf(true) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (firstLoad.value) {
            listState.scrollToItem(messages.size)
            messages.forEach { seen += it.id }
            firstLoad.value = false
        } else {
            listState.animateScrollToItem(messages.size)
        }
    }
    LaunchedEffect(imeBottom > 0) { if (imeBottom > 0 && messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 10.dp)) {
        if (messages.isEmpty()) {
            item { Box(Modifier.padding(top = 18.dp)) { IosPaperCard { IosEmptyPanel(emptyTitle, emptyNote) } } }
        }
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            val previous = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)
            val fresh = message.id !in seen && !firstLoad.value
            ChatRow(
                message = message,
                mine = message.authorId == meId,
                startsGroup = previous?.authorId != message.authorId,
                endsGroup = next?.authorId != message.authorId,
                showsAuthors = showsAuthors,
                animateIn = fresh,
                onAuthor = onAuthor,
                onReport = onReport,
                modifier = Modifier.animateItem(),
            )
            LaunchedEffect(message.id) { seen += message.id }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    message: ChatMessage,
    mine: Boolean,
    startsGroup: Boolean,
    endsGroup: Boolean,
    showsAuthors: Boolean,
    animateIn: Boolean,
    onAuthor: ((String) -> Unit)?,
    onReport: ((ChatMessage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, iosSpring(0.42f, 0.78f)) }
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val shape = remember(mine, startsGroup, endsGroup) { BubbleShape(mine, !startsGroup, !endsGroup) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = if (startsGroup) 10.dp else 2.dp)
            .graphicsLayer {
                val p = appear.value
                val s = 0.86f + 0.14f * p
                scaleX = s
                scaleY = s
                alpha = p.coerceIn(0f, 1f)
                translationY = 12.dp.toPx() * (1f - p)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
            },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (showsAuthors && !mine && startsGroup) {
            Row(
                Modifier
                    .padding(start = 4.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAuthor?.invoke(message.authorId) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Wyrm.Ink.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                    Text(initialsOf(message.authorName), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Wyrm.OnInk)
                }
                Text(
                    if (message.authorUsername.isEmpty()) message.authorName else "${message.authorName} · @${message.authorUsername}",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    color = Wyrm.Quiet,
                )
            }
        }
        Box {
            Text(
                text = message.body,
                fontFamily = Wyrm.Body,
                fontSize = 14.5.sp,
                color = if (mine) Wyrm.OnInk else Wyrm.Ink,
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(shape)
                    .background(if (mine) Wyrm.Ink else Wyrm.Card)
                    .then(if (mine) Modifier else Modifier.border(1.dp, Wyrm.Rule, shape))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = wyrmRounded(13.dp),
                containerColor = Wyrm.Card,
            ) {
                DropdownMenuItem(
                    text = { Text("Copy", fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink) },
                    onClick = {
                        clipboard.setText(AnnotatedString(message.body))
                        menu = false
                    },
                )
                if (!mine && onReport != null) {
                    DropdownMenuItem(
                        text = { Text("Report", fontFamily = Wyrm.Body, fontSize = 15.sp, color = Color(0xFFFF3B30)) },
                        onClick = {
                            menu = false
                            onReport(message)
                        },
                    )
                }
            }
        }
        if (endsGroup) {
            shortTime(message.createdAt)?.let {
                Text(it, fontFamily = Wyrm.Body, fontSize = 9.5.sp, color = Wyrm.Quiet, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

private fun shortTime(raw: String): String? = runCatching {
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date.from(Instant.parse(raw)))
}.getOrNull()

/** `WyrmBubbleShape`: rounder outside a run, tight where one author's bubbles meet. */
private class BubbleShape(private val mine: Boolean, private val tightTop: Boolean, private val tightBottom: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val big = minOf(19 * density.density, size.height / 2f)
        val small = 6 * density.density
        val topLeading = if (!mine && tightTop) small else big
        val bottomLeading = if (!mine && tightBottom) small else if (!mine) small else big
        val topTrailing = if (mine && tightTop) small else big
        val bottomTrailing = if (mine && tightBottom) small else if (mine) small else big
        return Outline.Rounded(
            RoundRect(
                0f, 0f, size.width, size.height,
                topLeftCornerRadius = CornerRadius(topLeading),
                topRightCornerRadius = CornerRadius(topTrailing),
                bottomRightCornerRadius = CornerRadius(bottomTrailing),
                bottomLeftCornerRadius = CornerRadius(bottomLeading),
            ),
        )
    }
}

/**
 * `WyrmChatComposer`: a growing one-to-five-line field in Liquid Glass, and a
 * glass ink send button that swells out of it when there is something to
 * send. On send its arrow launches upward. A counter appears near the limit.
 */
@Composable
fun IosChatComposer(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    limit: Int,
    sending: Boolean,
    bottomInset: Dp,
    backdrop: com.kyant.backdrop.Backdrop,
    onSend: () -> Unit,
) {
    val trimmed = text.trim()
    val canSend = trimmed.isNotEmpty() && !sending && text.length <= limit
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val launch = remember { Animatable(0f) }
    fun send() {
        if (!canSend) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            launch.animateTo(1f, tween(220, easing = FastOutLinearInEasing))
            delay(60)
            launch.snapTo(0f)
        }
        onSend()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = wyrmRounded(0.dp),
                tint = Wyrm.Paper.copy(alpha = 0.35f),
                blur = 18.dp,
                refraction = 0.dp,
                depth = 0.dp,
                shadow = null,
                fallback = Wyrm.Paper,
            )
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp + bottomInset),
        horizontalAlignment = Alignment.End,
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).offset(y = (-8).dp).background(Wyrm.Rule))
        AnimatedVisibility(text.length > limit - 40, enter = fadeIn(), exit = fadeOut()) {
            Text(
                "${maxOf(0, limit - text.length)}",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                color = if (text.length > limit) Wyrm.Badge else Wyrm.Quiet,
                modifier = Modifier.padding(end = 64.dp, bottom = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val style = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink)
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = style,
                maxLines = 5,
                cursorBrush = SolidColor(Wyrm.Link),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = wyrmRounded(22.dp),
                        tint = Wyrm.Card.copy(alpha = 0.55f),
                        blur = 6.dp,
                        refraction = 10.dp,
                        depth = 16.dp,
                        shadow = null,
                        fallback = Wyrm.Card,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text(placeholder, style = style.copy(color = Wyrm.Quiet.copy(alpha = 0.8f)), maxLines = 1)
                        inner()
                    }
                },
            )
            AnimatedVisibility(
                visible = canSend || sending,
                enter = scaleIn(iosSpring(0.38f, 0.62f), initialScale = 0.3f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)) + fadeIn(iosSpring(0.38f, 0.62f)),
                exit = scaleOut(iosSpring(0.38f, 0.62f), targetScale = 0.3f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)) + fadeOut(iosSpring(0.38f, 0.62f)),
            ) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { val s = if (pressed) 0.975f else 1f; scaleX = s; scaleY = s }
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = CircleShape,
                            tint = Wyrm.Ink.copy(alpha = 0.92f),
                            blur = 4.dp,
                            refraction = 8.dp,
                            depth = 12.dp,
                            shadow = null,
                            fallback = Wyrm.Ink,
                        )
                        .clickable(interactionSource = interaction, indication = null, enabled = canSend) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sending) {
                        IosSpinner(size = 17.dp, colour = Wyrm.OnInk)
                    } else {
                        IosIcon(
                            IosGlyph.ARROW_UP,
                            Wyrm.OnInk,
                            size = 20.dp,
                            weight = 2.6f,
                            modifier = Modifier.graphicsLayer {
                                val p = launch.value
                                translationY = -30.dp.toPx() * p
                                alpha = 1f - p
                                val s = 1f - 0.4f * p
                                scaleX = s
                                scaleY = s
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------- action sheet */

class IosSheetAction(val title: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** SwiftUI `confirmationDialog`: a titled group of actions and a separate Cancel. */
@Composable
fun IosActionSheet(title: String, actions: List<IosSheetAction>, insetBottom: Dp, onDismiss: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { appear.animateTo(1f, iosSpring(0.4f, 0.9f)) }
    fun close(then: () -> Unit = {}) {
        scope.launch {
            appear.animateTo(0f, iosSpring(0.3f, 1f))
            onDismiss()
            then()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f }
            .background(Color.Black.copy(alpha = 0.22f * appear.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp)
                .padding(bottom = insetBottom + 8.dp)
                .graphicsLayer { translationY = size.height * (1f - appear.value) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(wyrmRounded(14.dp)).background(Wyrm.Card)) {
                Text(
                    title,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Wyrm.Quiet,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
                actions.forEach { action ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
                    Text(
                        action.title,
                        fontFamily = Wyrm.Body,
                        fontSize = 17.sp,
                        color = if (action.destructive) Color(0xFFFF3B30) else Wyrm.Link,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { close(action.onClick) }
                            .padding(vertical = 17.dp),
                    )
                }
            }
            Text(
                "Cancel",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = Wyrm.Link,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(wyrmRounded(14.dp))
                    .background(Wyrm.Card)
                    .clickable { close() }
                    .padding(vertical = 17.dp),
            )
        }
    }
}

/* ------------------------------------------------------------ connections */

@Composable
fun IosConnectionsScreen(
    followers: List<ApiPlayer>,
    following: List<ApiPlayer>,
    initialPage: Int,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
) {
    val pager = rememberPagerState(initialPage = initialPage) { 2 }
    val scope = rememberCoroutineScope()
    IosPageChrome("Connections", insetTop, onBack) {
        // The same Liquid Glass pill as every other switcher, riding the pager.
        LiquidSegmented(
            options = listOf("Followers", "Following"),
            counts = listOf("${followers.size}", "${following.size}"),
            selected = pager.currentPage,
            follow = pager.currentPage + pager.currentPageOffsetFraction,
            height = 38.dp,
            fontSize = 12.5.sp,
            onSelect = { index -> scope.launch { pager.animateScrollToPage(index, animationSpec = iosSpring(0.34f, 0.8f)) } },
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val rows = if (page == 0) followers else following
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                IosPaperCard {
                    if (rows.isEmpty()) {
                        IosEmptyPanel(if (page == 0) "No followers yet" else "Not following anyone yet", "Connections update from your Wyrm account.")
                    }
                    rows.forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenPlayer(person.id) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WyrmAvatar(person.avatarUrl, person.avatarKey, initialsOf(person.displayName), 36.dp)
                            PersonText(person.displayName, person.handle, Modifier.weight(1f))
                            IosIcon(IosGlyph.CHEVRON_RIGHT, Wyrm.Chevron, size = 12.dp, weight = 2.6f)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp + insetBottom))
            }
        }
    }
}

/* --------------------------------------------------------------- profile */

@Composable
fun IosProfileScreen(
    own: Boolean,
    displayName: String,
    handle: String,
    bio: String,
    avatarUrl: String,
    avatarKey: String,
    score: Long,
    kills: Long,
    followers: Long,
    following: Long,
    isFollowing: Boolean,
    followsYou: Boolean,
    refreshing: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onFollowers: () -> Unit,
    onFollowing: () -> Unit,
    onSignOut: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    IosPageChrome("Profile", insetTop, onBack, actionTitle = if (own) "Edit" else "", onAction = onEdit) {
        IosRefreshable(refreshing, onRefresh, Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WyrmAvatar(avatarUrl, avatarKey, initialsOf(displayName), 76.dp, Modifier.padding(top = 28.dp))
                Text(displayName, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = Wyrm.Ink,
                    modifier = Modifier.padding(top = 14.dp))
                Text(handle, fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
                Text(
                    bio.ifBlank { if (own) "Nothing yet. Add a line about how you play." else "Nothing here yet." },
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 34.dp).padding(top = 10.dp),
                )
                Row(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(wyrmRounded(15.dp))
                        .background(Wyrm.Card)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(15.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IosMetric("BEST", groupedNumber(score))
                    Box(Modifier.width(1.dp).height(48.dp).background(Wyrm.Rule))
                    IosMetric("KILLS", groupedNumber(kills))
                }
                IosPaperCard {
                    IosListRow("Followers", value = "$followers", onClick = onFollowers)
                    IosListRow("Following", value = "$following", onClick = onFollowing)
                    if (own) {
                        IosListRow("Sign out", destructive = true, showsChevron = false, onClick = onSignOut)
                    } else {
                        IosListRow(
                            if (isFollowing) "Unfollow" else "Follow",
                            value = if (followsYou) "Follows you" else "",
                            showsChevron = false,
                            onClick = onToggleFollow,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp + insetBottom))
            }
        }
    }
}

@Composable
fun IosEditProfileScreen(
    displayName: String,
    ingameName: String,
    username: String,
    bio: String,
    avatarUrl: String,
    avatarKey: String,
    renames: String,
    busy: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onSave: (displayName: String, ingameName: String, username: String, bio: String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var name by remember { mutableStateOf(displayName) }
    var arena by remember { mutableStateOf(ingameName) }
    var user by remember { mutableStateOf(username) }
    var about by remember { mutableStateOf(bio) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        IosPageChrome("Edit profile", insetTop, onBack, actionTitle = "Save", onAction = { onSave(name, arena, user, about) }) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WyrmAvatar(avatarUrl, avatarKey, initialsOf(name), 76.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(
                            if (avatarUrl.isNotEmpty()) "Change photo" else "Add photo",
                            fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Wyrm.Link,
                            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPickPhoto),
                        )
                        if (avatarUrl.isNotEmpty()) {
                            Text(
                                "Remove",
                                fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Wyrm.Badge,
                                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemovePhoto),
                            )
                        }
                    }
                    if (busy) IosSpinner(size = 20.dp)
                }
                EditField("Display name", name) { name = it }
                EditField("Arena name", arena) { arena = it }
                EditField("Username", user) { user = it }
                EditField("Bio", about) { about = it }
                if (renames.isNotEmpty()) {
                    Text(renames, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet, modifier = Modifier.fillMaxWidth())
                }
                if (error.isNotEmpty()) Text(error, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Color(0xFFFF3B30))
                val shape = wyrmRounded(13.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(shape)
                        .border(1.dp, Color(0xFFFF3B30).copy(alpha = 0.3f), shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Delete account", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFFFF3B30))
                }
                Spacer(Modifier.height(insetBottom))
            }
        }
        if (confirmDelete) {
            IosActionSheet(
                title = "Delete your Wyrm account? This cannot be undone.",
                actions = listOf(IosSheetAction("Delete account", destructive = true, onClick = onDeleteAccount)),
                insetBottom = insetBottom,
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label.uppercase(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 1.sp, color = Wyrm.Quiet)
        val style = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Wyrm.Link),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(wyrmRounded(13.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(13.dp))
                .padding(horizontal = 14.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(label, style = style.copy(color = Wyrm.Quiet.copy(alpha = 0.7f)))
                    inner()
                }
            },
        )
    }
}

@Suppress("unused")
private val keepBuild = Build.VERSION.SDK_INT


/* ------------------------------------------------------------ voice rooms */

/**
 * Wyrm iOS's Voice rooms page: a verify banner until the voice profile is
 * verified, the official Wyrm rooms, then player rooms. Android keeps its
 * "New" action because it can create rooms, which iOS cannot yet.
 */
@Composable
fun IosVoiceDirectory(
    verified: Boolean,
    rooms: List<com.wyrm.omrajput.data.VoiceRoom>,
    refreshing: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onVerify: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRoom: (com.wyrm.omrajput.data.VoiceRoom) -> Unit,
) {
    val official = rooms.filter { it.managedPublic }
    val personal = rooms.filter { !it.managedPublic }
    IosPageChrome("Voice rooms", insetTop, onBack, actionTitle = "New", onAction = onNew) {
        IosRefreshable(refreshing, onRefresh, Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (!verified) {
                    Row(
                        Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                            .fillMaxWidth()
                            .clip(wyrmRounded(16.dp))
                            .background(Wyrm.Card.copy(alpha = 0.9f))
                            .border(1.dp, Wyrm.Live.copy(alpha = 0.35f), wyrmRounded(16.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onVerify)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        IosIcon(IosGlyph.CHECKMARK_SHIELD, Wyrm.Live, size = 26.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Your voice profile is not verified", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Wyrm.Ink)
                            Text("Verify once to create and enter player rooms.", fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet)
                        }
                        Text("Verify", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Wyrm.Link)
                    }
                }
                IosSectionLabel("Official Wyrm rooms")
                IosPaperCard {
                    if (official.isEmpty()) IosEmptyPanel("Official rooms are quiet", "Wyrm-managed public rooms appear here first.")
                    official.forEach { room ->
                        Box {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 58.dp)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenRoom(room) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(38.dp).clip(wyrmRounded(9.dp)).background(Wyrm.Ink.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                                    Text("W", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Wyrm.Ink.copy(alpha = 0.38f))
                                }
                                PersonText(room.name, "Wyrm · direct entry", Modifier.weight(1f))
                                Text(
                                    if (room.active) "${room.activeCount}/${room.capacity} live" else "Public",
                                    fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                                    color = if (room.active) Wyrm.Live else Wyrm.Quiet,
                                )
                                IosIcon(IosGlyph.CHEVRON_RIGHT, Wyrm.Chevron, size = 12.dp, weight = 2.6f)
                            }
                            RowRule(64.dp, Modifier.align(Alignment.BottomStart))
                        }
                    }
                }
                IosSectionLabel("Player rooms · ${personal.size}")
                IosPaperCard {
                    if (personal.isEmpty()) IosEmptyPanel("No player rooms yet", "Your rooms and rooms from other players will appear here.")
                    personal.forEach { room ->
                        IosListRow(
                            title = room.name,
                            detail = if (room.mine) "Your room" else "by ${room.creator.displayName}",
                            value = if (room.active) "${room.activeCount} live" else room.gate.replaceFirstChar { it.uppercase() },
                            glyph = if (room.active) IosGlyph.WAVEFORM else IosGlyph.MIC,
                            tint = if (room.active) Wyrm.Live else Wyrm.Mute,
                        ) { onOpenRoom(room) }
                    }
                }
                Spacer(Modifier.height(24.dp + insetBottom))
            }
        }
    }
}
