package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.ApiPlayer

/**
 * Somebody else's profile.
 *
 * The same ledger as your own, minus the things only you may change, plus the
 * two things you can do about them: follow, and open a thread. A closed
 * account still opens — the record stands even when the person has gone — but
 * nothing on it can be acted on.
 */
@Composable
fun PlayerScreen(
    player: ApiPlayer,
    busy: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = insetTop)
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                Text(
                    text = "‹ Back",
                    fontFamily = Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
                Text(
                    text = "Profile",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = insetBottom + 20.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WyrmAvatar(
                    url = player.avatarUrl,
                    avatarKey = player.avatarKey,
                    initial = player.displayName,
                    size = 66.dp,
                    corner = 19.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (player.isDeleted) "Deleted player" else player.displayName,
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = if (player.isDeleted) Wyrm.Quiet else Wyrm.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (player.handle.isNotEmpty()) {
                        Text(
                            text = player.handle,
                            fontFamily = Wyrm.Body,
                            fontSize = 14.5.sp,
                            color = Wyrm.Quiet,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (player.followsYou && !player.isDeleted) {
                        Text(
                            text = "Follows you",
                            fontFamily = Wyrm.Body,
                            fontSize = 12.5.sp,
                            color = Wyrm.Live,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (player.bio.isNotBlank()) {
                Text(
                    text = "BIO",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.92.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
                )
                Text(
                    text = player.bio,
                    fontFamily = Wyrm.Body,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(wyrmRounded(14.dp))
                        .background(Wyrm.Card)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
                        .padding(14.dp),
                )
            }
            if (!player.isDeleted) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (player.isFollowing) {
                            PaperOutlineButton(if (busy) "…" else "Following", enabled = !busy, onClick = onToggleFollow)
                        } else {
                            PaperPrimaryButton(
                                if (busy) "…" else if (player.followsYou) "Follow back" else "Follow",
                                enabled = !busy,
                                onClick = onToggleFollow,
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        PaperOutlineButton(
                            if (player.canMessage) "Message" else "Message locked",
                            enabled = player.canMessage,
                            onClick = { if (player.canMessage) onMessage() },
                        )
                    }
                }
                if (!player.canMessage) {
                    Text(
                        text = "A direct thread opens once you follow each other.",
                        fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
            }
            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Badge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
            }
            Text(
                text = "RECORD",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.92.sp,
                color = Wyrm.Quiet,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(wyrmRounded(14.dp))
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp)),
            ) {
                PaperRecord("Highest score", player.highestScore.toString(), first = true)
                PaperRecord("Total kills", player.kills.toString())
                PaperRecord("Followers", player.followerCount.toString(), onClick = onOpenFollowers)
                PaperRecord("Following", player.followingCount.toString(), onClick = onOpenFollowing)
            }
        }
    }
}

@Composable
private fun PaperRecord(label: String, value: String, first: Boolean = false, onClick: (() -> Unit)? = null) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, fontFamily = Wyrm.Body, fontSize = 13.5.sp, color = Wyrm.Quiet, modifier = Modifier.width(132.dp))
            Text(text = value, fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Text(text = "›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = Wyrm.Chevron)
            }
        }
    }
}

/**
 * A screen that is still fetching what it is about.
 *
 * Worth having as its own thing: the alternative was routing away while the
 * request was in flight, which is what made tapping a leaderboard row look
 * like it did nothing at all.
 */
@Composable
fun LoadingScreen(title: String, insetTop: Dp, insetBottom: Dp, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = insetTop)
                .height(52.dp)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = "‹ Back",
                fontFamily = Wyrm.Body,
                fontSize = 16.sp,
                color = Wyrm.Link,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            Text(
                text = title,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Wyrm.Ink,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Text(
            text = "Loading…",
            fontFamily = Wyrm.Body,
            fontSize = 14.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(20.dp),
        )
    }
}

/**
 * Spec page 23 — Social › Followers.
 *
 * One page, two segments. Follow/Following is the real [ApiPlayer.isFollowing]
 * flag, not a mock.
 */
@Composable
fun ConnectionsScreen(
    kind: String,
    followerCount: Long,
    followingCount: Long,
    players: List<ApiPlayer>,
    loading: Boolean,
    backLabel: String = "Social",
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onKindChange: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onToggleFollow: (ApiPlayer) -> Unit,
) {
    val followers = kind != "following"
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
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(40.dp)) {
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
                    text = "People",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            PaperSegmented(
                options = listOf("Followers · $followerCount", "Following · $followingCount"),
                selected = if (followers) 0 else 1,
                onSelect = { onKindChange(if (it == 0) "followers" else "following") },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        when {
            loading && players.isEmpty() -> Note("Loading…")
            players.isEmpty() -> Note("Nobody here yet.")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    SettingsCard {
                        players.forEachIndexed { index, player ->
                            ConnectionRow(
                                player = player,
                                first = index == 0,
                                onOpen = { onOpenPlayer(player.id) },
                                onToggleFollow = { onToggleFollow(player) },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp + insetBottom.coerceAtLeast(8.dp))) }
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    player: ApiPlayer,
    first: Boolean,
    onOpen: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val best = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(player.highestScore)
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clickable(onClick = onOpen)
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
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = if (player.isDeleted) "Deleted player" else player.displayName,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = if (player.isDeleted) Wyrm.Quiet else Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        player.handle.takeIf { it.isNotBlank() },
                        "$best best",
                    ).joinToString(" · "),
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!player.isDeleted) {
                FollowChip(following = player.isFollowing, onClick = onToggleFollow)
            }
        }
    }
}

@Composable
private fun FollowChip(following: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(wyrmRounded(9.dp))
            .then(
                if (following) Modifier.border(1.dp, Wyrm.Rule, wyrmRounded(9.dp))
                else Modifier.background(Wyrm.Ink)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (following) 13.dp else 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (following) "Following" else "Follow",
            fontFamily = Wyrm.Body,
            fontWeight = if (following) FontWeight.Normal else FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (following) Wyrm.Mute else Wyrm.OnInk,
        )
    }
}

@Composable
private fun Note(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = Wyrm.Gutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontFamily = Wyrm.Body,
            fontSize = 13.sp,
            color = Wyrm.Faint,
        )
    }
}

@Composable
private fun RecordCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 18.dp, horizontal = 4.dp),
    ) {
        Text(
            text = value,
            fontFamily = Wyrm.Display,
            fontSize = 30.sp,
            color = Wyrm.White,
        )
        Spacer(Modifier.height(4.dp))
        WyrmLabel(label, color = if (onClick != null) Wyrm.Grey else Wyrm.Faint)
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Wyrm.Line)
    )
}
