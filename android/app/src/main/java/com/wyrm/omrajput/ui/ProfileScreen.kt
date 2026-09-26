package com.wyrm.omrajput.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A player, shaped exactly as the backend returns one.
 *
 * Mirrors PUBLIC_PLAYER in the arena backend's db.mjs, so the screen and the
 * server never disagree about what a profile is.
 */
data class WyrmProfile(
    val id: String = "",
    val displayName: String = "",
    val ingameName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarKey: String = "mono-ink",
    val avatarUrl: String = "",
    val highestScore: Long = 0,
    val kills: Long = 0,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val memberSince: String = "",
) {
    val isEmpty: Boolean get() = id.isEmpty()
}

/**
 * Spec page 06 — Profile.
 *
 * A Notion property table, not four huge numbers. Back is ‹ Social (or Play).
 * Edit is a text control, not a hamburger. Nested Edit / Backup / Followers
 * stay on their existing screens until those pages are named.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: WyrmProfile,
    scoreRank: Int?,
    killRank: Int?,
    teamLabel: String,
    voiceVerified: Boolean,
    backLabel: String = "Social",
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenTeam: () -> Unit,
    onVerifyVoice: () -> Unit,
    onOpenBackup: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    offline: Boolean,
    updatedAt: Long,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val handle = if (profile.username.isBlank()) "no username yet" else "@${profile.username}"
    val since = formatSince(profile.memberSince)
    val props = listOf(
        Prop("Rank by score", rankLine(scoreRank, profile.highestScore), onClick = null),
        Prop("Rank by kills", rankLine(killRank, profile.kills), onClick = null),
        Prop("Followers", grouped(profile.followerCount), onOpenFollowers),
        Prop("Following", grouped(profile.followingCount), onOpenFollowing),
        Prop("Team", teamLabel, onOpenTeam),
        Prop("Voice", if (voiceVerified) "Verified" else "Not verified", if (voiceVerified) null else onVerifyVoice),
        Prop("In the arena since", since.ifBlank { "—" }, onClick = null),
    )

    Box(modifier = Modifier.fillMaxSize().background(Wyrm.Paper)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Wyrm.Paper.copy(alpha = 0.94f))
                    .padding(top = insetTop)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
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
                        text = "···",
                        fontFamily = Wyrm.Body,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp,
                        color = Wyrm.Ink,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(onClick = { menuOpen = true })
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Wyrm.Rule),
            )

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = insetBottom + 20.dp),
            ) {
                if (offline && updatedAt > 0L) {
                    Text("Offline · updated ${profileCacheAge(updatedAt)} ago", fontFamily = Wyrm.Body, fontSize = 11.sp,
                        color = Wyrm.Quiet, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
                }
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WyrmAvatar(
                        url = profile.avatarUrl,
                        avatarKey = profile.avatarKey,
                        initial = profile.displayName.trim().ifEmpty { profile.ingameName },
                        size = 64.dp,
                        corner = 18.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName.ifEmpty { "Unnamed" },
                            fontFamily = Wyrm.Body,
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp,
                            letterSpacing = (-0.4).sp,
                            color = Wyrm.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = handle,
                            fontFamily = Wyrm.Body,
                            fontSize = 14.sp,
                            color = Wyrm.Quiet,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                PropCard(props)

                SectionLabel("Bio")
                BioCard(
                    if (profile.bio.isBlank()) "Nothing yet. Add a line about how you play."
                    else profile.bio,
                )
            }
            }
        }

        if (menuOpen) {
            ProfileMenuSheet(
                handle = "${profile.displayName.ifBlank { "Wyrm" }} · $handle",
                onEdit = {
                    menuOpen = false
                    onEdit()
                },
                onBackup = {
                    menuOpen = false
                    onOpenBackup()
                },
                onSignOut = {
                    menuOpen = false
                    onSignOut()
                },
                onDelete = {
                    menuOpen = false
                    confirmingDelete = true
                },
                onCancel = { menuOpen = false },
            )
        }

        if (confirmingDelete) {
            DeleteConfirm(
                onCancel = { confirmingDelete = false },
                onConfirm = {
                    confirmingDelete = false
                    onDeleteAccount()
                },
            )
        }
    }
}

private fun profileCacheAge(savedAt: Long): String {
    val minutes = ((System.currentTimeMillis() - savedAt).coerceAtLeast(0L) / 60_000L)
    return if (minutes < 1) "just now" else if (minutes < 60) "$minutes min" else if (minutes < 1440) "${minutes / 60} hr" else "${minutes / 1440} d"
}

private data class Prop(
    val label: String,
    val value: String,
    val onClick: (() -> Unit)?,
)

@Composable
private fun PropCard(props: List<Prop>) {
    val shape = wyrmRounded(14.dp)
    val nums = TextStyle(fontFeatureSettings = "tnum")
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
    ) {
        props.forEachIndexed { index, prop ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
            }
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val click = prop.onClick
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .then(if (click != null) Modifier.scale(pressScale(pressed)) else Modifier)
                    .then(
                        if (click != null) {
                            Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = click,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = prop.label,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.width(124.dp),
                )
                Text(
                    text = prop.value,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = Wyrm.Ink,
                    style = nums,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BioCard(bio: String) {
    val shape = wyrmRounded(14.dp)
    Text(
        text = bio,
        fontFamily = Wyrm.Body,
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
        color = Wyrm.Ink,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(14.dp),
    )
}

@Composable
private fun ProfileMenuSheet(
    handle: String,
    onEdit: () -> Unit,
    onBackup: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheet = wyrmRounded(15.dp)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x520F0F0F))
                .clickable(onClick = onCancel),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 9.dp, end = 9.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sheet)
                    .background(Color(0xF8FCFBFA)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = handle,
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 12.dp, bottom = 11.dp),
                )
                MenuAction("Edit profile", Wyrm.Ink, onEdit)
                MenuAction("Backup and restore", Wyrm.Ink, onBackup)
                MenuAction("Sign out", Wyrm.Ink, onSignOut)
                MenuAction("Delete account", Wyrm.Badge, onDelete)
            }
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(sheet)
                    .background(Color(0xF8FCFBFA))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = Wyrm.Ink,
                )
            }
        }
    }
}

@Composable
private fun MenuAction(label: String, colour: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = Wyrm.Body, fontSize = 17.sp, color = colour)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.92.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun DeleteConfirm(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val shape = wyrmRounded(14.dp)
        Column(
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .clip(shape)
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(20.dp),
        ) {
            Text(
                text = "Delete account?",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Wyrm.Badge,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your profile disappears and you are signed out. " +
                    "Anywhere you already appear — leaderboards, chat, follows — " +
                    "will show you as deleted. Signing in again with the same " +
                    "Google account starts a fresh account, not this one.",
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Wyrm.Quiet,
            )
            Spacer(Modifier.height(18.dp))
            Row {
                OutlineKeep(modifier = Modifier.weight(1f), onClick = onCancel)
                Spacer(Modifier.width(10.dp))
                FillDelete(modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun OutlineKeep(modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(42.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(10.dp))
            .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Keep it",
            fontFamily = Wyrm.Body,
            fontSize = 15.sp,
            color = Wyrm.Mute,
        )
    }
}

@Composable
private fun FillDelete(modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(42.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(10.dp))
            .background(Wyrm.Badge)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Delete",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Wyrm.contentOn(Wyrm.Badge),
        )
    }
}

private fun rankLine(rank: Int?, value: Long): String {
    val score = grouped(value)
    return if (rank != null && rank > 0) "${ordinal(rank)} · $score" else score
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
    NumberFormat.getIntegerInstance(Locale.US).format(value)

private val sinceFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

private fun formatSince(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().format(sinceFormat)
}.getOrDefault("")
