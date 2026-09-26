package com.wyrm.omrajput.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.R

data class SocialRecent(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val avatarKey: String,
    val whenLabel: String = "",
)

/**
 * Spec page 02 — Social.
 *
 * One index for people. Nested destinations still open existing screens
 * until those pages are redesigned.
 */
@Composable
fun SocialScreen(
    killRank: Int?,
    messageDetail: String,
    unreadMessages: Long,
    voiceDetail: String,
    followerCount: Long,
    followingCount: Long,
    profileHandle: String,
    profileInitials: String,
    recents: List<SocialRecent>,
    unreadNotifications: Int,
    insetTop: Dp,
    insetBottom: Dp,
    showRootTabs: Boolean = true,
    onAppear: () -> Unit,
    onOpenLeaderboard: (Rect) -> Unit,
    onOpenMessages: (Rect) -> Unit,
    onOpenVoice: (Rect) -> Unit,
    onOpenFollowers: (Rect) -> Unit,
    onOpenProfile: (Rect) -> Unit,
    onOpenPlayer: (String, Rect) -> Unit,
    liveRooms: Int = 0,
    /** Pull to refresh: everything Social shows, and every page behind it. */
    onRefresh: (done: () -> Unit) -> Unit = { onAppear(); it() },
    onOpenGlobalChat: (Rect) -> Unit = onOpenMessages,
    onTabNotifications: (Rect) -> Unit,
    onTabPlay: (Rect) -> Unit,
    onTabSkin: (Rect) -> Unit,
    onTabSettings: (Rect) -> Unit,
) {
    LaunchedEffect(Unit) { onAppear() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        // Wyrm iOS pulls Social to refresh leaderboards, chats, rooms and alerts.
        var pulling by remember { mutableStateOf(false) }
        LaunchedEffect(pulling) {
            if (pulling) onRefresh { pulling = false }
        }
        IosRefreshable(pulling, { pulling = true }, Modifier.weight(1f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = insetTop),
        ) {
            // Wyrm iOS › Social: one card of destinations, then the circle.
            IosScreenHeader(kicker = "Arena", title = "Social")
            IosPaperCard {
                IosListRow(
                    title = "Leaderboard",
                    detail = if (killRank != null) "You are $killRank by kills" else "Score and kills",
                    glyph = IosGlyph.TROPHY,
                ) { onOpenLeaderboard(Rect.Zero) }
                IosListRow(
                    title = "Messages",
                    detail = if (unreadMessages == 0L) "No unread messages" else "$unreadMessages unread",
                    glyph = IosGlyph.MESSAGE,
                    tint = Wyrm.Link,
                ) { onOpenMessages(Rect.Zero) }
                IosListRow(
                    title = "Global chat",
                    detail = "Everyone in Wyrm · last 24 hours",
                    glyph = IosGlyph.BUBBLES,
                    tint = Wyrm.Link,
                ) { onOpenGlobalChat(Rect.Zero) }
                IosListRow(
                    title = "Voice rooms",
                    detail = "$liveRooms live",
                    glyph = IosGlyph.MIC,
                    tint = Wyrm.Live,
                ) { onOpenVoice(Rect.Zero) }
                IosListRow(
                    title = "Connections",
                    detail = "$followerCount followers · $followingCount following",
                    glyph = IosGlyph.PERSON_2,
                ) { onOpenFollowers(Rect.Zero) }
                IosListRow(
                    title = "Your profile",
                    detail = profileHandle,
                    glyph = IosGlyph.PERSON_CIRCLE,
                ) { onOpenProfile(Rect.Zero) }
            }
            IosSectionLabel("Recently played with")
            IosPaperCard {
                if (recents.isEmpty()) {
                    IosEmptyPanel(
                        title = "Your arena circle starts here",
                        note = "Players from real conversations and follows appear here.",
                    )
                } else {
                    recents.forEachIndexed { index, person ->
                        RecentRow(person = person, first = index == 0, onOpen = onOpenPlayer)
                    }
                }
            }
            Spacer(Modifier.height(LocalRootTabClearance.current))
        }
        }
        if (showRootTabs) {
            RootTabs(
                selected = RootTab.SOCIAL,
                insetBottom = insetBottom,
                unreadNotifications = unreadNotifications,
                onNotifications = onTabNotifications,
                onPlay = onTabPlay,
                onSocial = {},
                onSkin = onTabSkin,
                onSettings = onTabSettings,
            )
        }
    }
}

@Composable
private fun IndexCard(content: @Composable () -> Unit) {
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
private fun IndexRow(
    title: String,
    detail: String,
    onOpen: (Rect) -> Unit,
    first: Boolean = false,
    wellColor: Color,
    wellIcon: Int? = null,
    wellTint: Color = Wyrm.Mute,
    wellContent: (@Composable () -> Unit)? = null,
    badge: Long = 0,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
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
                .height(58.dp)
                .scale(pressScale(pressed))
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .clickable(interactionSource = interaction, indication = null) { onOpen(bounds) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(wyrmRounded(8.dp))
                    .background(wellColor),
                contentAlignment = Alignment.Center,
            ) {
                if (wellContent != null) {
                    wellContent()
                } else if (wellIcon != null) {
                    Image(
                        painter = painterResource(wellIcon),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        colorFilter = ColorFilter.tint(wellTint),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.5.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(end = 6.dp)
                        .clip(wyrmRounded(99.dp))
                        .background(Wyrm.Badge)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 99) "99+" else "$badge",
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
private fun RecentRow(person: SocialRecent, first: Boolean, onOpen: (String, Rect) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val initial = person.name.filter { it.isLetter() }.take(2).uppercase().ifEmpty { "W" }
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
                .height(58.dp)
                .scale(pressScale(pressed))
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .clickable(interactionSource = interaction, indication = null) {
                    onOpen(person.id, bounds)
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WyrmAvatar(
                url = person.avatarUrl,
                avatarKey = person.avatarKey,
                initial = initial,
                size = 32.dp,
                corner = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = person.handle,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (person.whenLabel.isNotBlank()) {
                Text(
                    text = person.whenLabel,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Quiet,
                )
            }
        }
    }
}

private fun ordinal(n: Int): String {
    val mod100 = n % 100
    val suffix = when {
        mod100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

private fun grouped(value: Long): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(value)
