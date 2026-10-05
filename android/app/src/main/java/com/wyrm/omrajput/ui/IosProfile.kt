package com.wyrm.omrajput.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.Badge
import com.wyrm.omrajput.data.BadgeStore
import com.wyrm.omrajput.data.TRAILS_ENABLED
import com.wyrm.omrajput.data.TRAIL_BADGE_IDS
import com.wyrm.omrajput.data.Trail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

/*
 * The profile (OM, 2026-09-29), as Wyrm iOS lays it out (`WyrmProfile.swift`):
 * the way people already read a social profile, in Wyrm's own paper look.
 *
 * - A header with the squircle avatar beside Trails, Followers and Following.
 * - Name, "Follows you", bio, then the arena numbers as chips (Best, Kills).
 * - Edit profile + Share on your own profile; Follow + Message on another's.
 * - Ten badges (`backend/src/badges.mjs`), each with its progress ring.
 * - The player's trails as a three-column grid, paged as it scrolls.
 * - Tapping the avatar grows it into the middle of the screen. On your own
 *   profile a pill rises from the bottom and opens into Change photo, Remove
 *   this photo and Edit profile.
 *
 * Every part paints from the last visit at once (SocialCache) and refreshes in
 * place; placeholders have the final sizes, so nothing jumps when data lands.
 */

private val profileAvatarSize = 86.dp

private fun profileNumber(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun profileInitials(name: String): String {
    val letters = name.split(" ").filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
    return letters.ifEmpty { "W" }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private val Badge.icon: Int
    get() = when (id) {
        "first-blood" -> LucideR.drawable.lucide_ic_droplet
        "hunter" -> LucideR.drawable.lucide_ic_crosshair
        "slayer" -> LucideR.drawable.lucide_ic_zap
        "big-snake" -> LucideR.drawable.lucide_ic_trending_up
        "giant" -> LucideR.drawable.lucide_ic_flame
        "legend" -> LucideR.drawable.lucide_ic_crown
        "trailblazer" -> LucideR.drawable.lucide_ic_sparkles
        "crowd-favourite" -> LucideR.drawable.lucide_ic_hexagon
        "social" -> LucideR.drawable.lucide_ic_users
        "veteran" -> LucideR.drawable.lucide_ic_calendar
        else -> LucideR.drawable.lucide_ic_star
    }

@Composable
fun IosProfileScreen(
    own: Boolean,
    playerId: String,
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
    canMessage: Boolean,
    refreshing: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onFollowers: () -> Unit,
    onFollowing: () -> Unit,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit,
    onOpenTrail: (String) -> Unit,
    onNewTrail: () -> Unit,
    onShare: () -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    /** Another player's record has arrived (Follow and Message wait for it). */
    ready: Boolean = true,
    followBusy: Boolean = false,
    photoBusy: Boolean = false,
    photoError: String = "",
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val initials = profileInitials(displayName)
    val grid = TrailsStore.authors[playerId]
    val book = BadgeStore.books[playerId]
    val trailCount = when {
        book != null -> profileNumber(book.trailCount.toLong())
        grid != null && grid.loaded && grid.reachedEnd -> "${grid.trails.size}"
        else -> "–"
    }

    LaunchedEffect(playerId) {
        if (playerId.isNotBlank()) {
            if (TRAILS_ENABLED) TrailsStore.loadAuthor(playerId)
            BadgeStore.load(playerId)
        }
    }

    var container by remember { mutableStateOf(Rect.Zero) }
    var avatarBounds by remember { mutableStateOf(Rect.Zero) }
    var avatarMounted by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    val expand = remember { Animatable(0f) }
    var badgeShown by remember { mutableStateOf<Badge?>(null) }

    fun expandAvatar() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        avatarMounted = true
        scope.launch {
            expand.snapTo(0f)
            launch { expand.animateTo(1f, iosSpring(0.46f, 0.82f)) }
            if (own) {
                delay(140)
                optionsOpen = true
            }
        }
    }

    fun collapseAvatar(then: (() -> Unit)? = null) {
        optionsOpen = false
        scope.launch {
            expand.animateTo(0f, iosSpring(0.42f, 0.86f))
            avatarMounted = false
            then?.invoke()
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { container = it.boundsInRoot() }) {
        val blurRadius = if (avatarMounted) (18f * expand.value).dp else 0.dp
        Box(Modifier.fillMaxSize().blur(blurRadius)) {
            IosPageChrome(
                title = handle.ifBlank { "Profile" },
                insetTop = insetTop,
                onBack = onBack,
                actionTitle = if (own) "Edit" else "",
                onAction = if (own) onEdit else null,
            ) {
                IosRefreshable(
                    refreshing = refreshing,
                    onRefresh = {
                        onRefresh()
                        if (playerId.isNotBlank()) {
                            if (TRAILS_ENABLED) TrailsStore.loadAuthor(playerId)
                            BadgeStore.load(playerId)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "header") {
                            ProfileHeader(
                                own = own,
                                displayName = displayName,
                                bio = bio,
                                avatarUrl = avatarUrl,
                                avatarKey = avatarKey,
                                initials = initials,
                                avatarHidden = avatarMounted,
                                trailCount = trailCount,
                                followers = followers,
                                following = following,
                                score = score,
                                kills = kills,
                                beads = if (TRAILS_ENABLED) book?.beads ?: 0 else 0,
                                isFollowing = isFollowing,
                                followsYou = followsYou,
                                canMessage = canMessage,
                                ready = ready,
                                followBusy = followBusy,
                                onAvatar = ::expandAvatar,
                                onAvatarBounds = { avatarBounds = it },
                                onEdit = onEdit,
                                onShare = onShare,
                                onFollowers = onFollowers,
                                onFollowing = onFollowing,
                                onToggleFollow = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleFollow()
                                },
                                onMessage = onMessage,
                            )
                        }
                        // Badges are off the profile (OM, 2026-10-05: "badges hata de", a new
                        // concept comes later). The strip and its sheet are kept below, unused.
                        if (TRAILS_ENABLED) item(key = "grid-header") { ProfileGridHeader() }
                        if (TRAILS_ENABLED) when {
                            grid != null && grid.loaded && grid.trails.isNotEmpty() -> {
                                val rows = grid.trails.chunked(3)
                                items(rows, key = { row -> "row-" + row.first().id }) { row ->
                                    ProfileGridRow(row, onOpenTrail)
                                    val lastId = grid.trails.last().id
                                    if (row.any { it.id == lastId }) {
                                        LaunchedEffect(lastId) { TrailsStore.loadMoreAuthor(playerId, lastId) }
                                    }
                                }
                                if (grid.loading && grid.cursor != null) {
                                    item(key = "grid-more") {
                                        Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                                            IosSpinner(size = 20.dp)
                                        }
                                    }
                                }
                            }
                            grid != null && grid.loaded -> item(key = "grid-empty") {
                                ProfileGridEmpty(own = own, failed = grid.failed, name = displayName, onNewTrail = onNewTrail)
                            }
                            else -> items(3, key = { "grid-placeholder-$it" }) { row ->
                                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(3) { column ->
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .background(Wyrm.Well.copy(alpha = if ((row * 3 + column) % 2 == 0) 1f else 0.7f)),
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "end") { Spacer(Modifier.height(36.dp + insetBottom)) }
                    }
                }
            }
        }

        if (avatarMounted) {
            ProfileAvatarOverlay(
                own = own,
                progress = expand.value,
                optionsOpen = optionsOpen,
                start = if (avatarBounds == Rect.Zero) null else avatarBounds.center - container.topLeft,
                displayName = displayName,
                handle = handle,
                avatarUrl = avatarUrl,
                avatarKey = avatarKey,
                initials = initials,
                photoBusy = photoBusy,
                photoError = photoError,
                insetBottom = insetBottom,
                onCollapse = { collapseAvatar() },
                onChangePhoto = onChangePhoto,
                onRemovePhoto = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemovePhoto()
                },
                onEdit = { collapseAvatar(onEdit) },
            )
        }

        AnimatedVisibility(visible = badgeShown != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Wyrm.Ink.copy(alpha = 0.3f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { badgeShown = null },
            )
        }
        var lastBadge by remember { mutableStateOf<Badge?>(null) }
        badgeShown?.let { lastBadge = it }
        AnimatedVisibility(
            visible = badgeShown != null,
            enter = slideInVertically(iosSpring(0.4f, 0.84f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            lastBadge?.let { badge ->
                ProfileBadgeSheet(badge, insetBottom) { badgeShown = null }
            }
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun ProfileHeader(
    own: Boolean,
    displayName: String,
    bio: String,
    avatarUrl: String,
    avatarKey: String,
    initials: String,
    avatarHidden: Boolean,
    trailCount: String,
    followers: Long,
    following: Long,
    score: Long,
    kills: Long,
    beads: Int,
    isFollowing: Boolean,
    followsYou: Boolean,
    canMessage: Boolean,
    ready: Boolean,
    followBusy: Boolean,
    onAvatar: () -> Unit,
    onAvatarBounds: (Rect) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFollowers: () -> Unit,
    onFollowing: () -> Unit,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            WyrmAvatar(
                avatarUrl,
                avatarKey,
                initials,
                profileAvatarSize,
                Modifier
                    .onGloballyPositioned { onAvatarBounds(it.boundsInRoot()) }
                    .alpha(if (avatarHidden) 0f else 1f)
                    .scale(pressScale(pressed))
                    .clickable(interactionSource = interaction, indication = null, onClick = onAvatar),
            )
            Row(Modifier.weight(1f)) {
                if (TRAILS_ENABLED) ProfileStat(trailCount, "Trails", null)
                ProfileStat(profileNumber(followers), "Followers", onFollowers)
                ProfileStat(profileNumber(following), "Following", onFollowing)
            }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(displayName, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Wyrm.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (!own && followsYou) {
                Text(
                    if (isFollowing) "Friends" else "Follows you",
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = Wyrm.Live,
                    modifier = Modifier.clip(WyrmCapsule).background(Wyrm.Live.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (bio.isNotBlank()) {
            Text(bio, fontFamily = Wyrm.Body, fontSize = 13.5.sp, lineHeight = 19.sp, color = Wyrm.Mute, modifier = Modifier.padding(top = 4.dp))
        } else if (own) {
            Text(
                "Add a line about how you play",
                fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Wyrm.Link,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit),
            )
        }
        // Best / Kills / Beads chips are off the profile (OM, 2026-10-05).
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (own) {
                ProfileButton("Edit profile", filled = false, onClick = onEdit)
                ProfileButton("Share profile", filled = false, onClick = onShare)
            } else {
                ProfileButton(
                    when {
                        followBusy -> "…"
                        isFollowing -> "Following"
                        followsYou -> "Follow back"
                        else -> "Follow"
                    },
                    filled = !isFollowing,
                    enabled = ready && !followBusy,
                    onClick = onToggleFollow,
                )
                ProfileButton("Message", filled = false, enabled = ready && canMessage, onClick = onMessage)
            }
        }
        if (!own && ready && !canMessage) {
            Text("You can message each other once you both follow.", fontFamily = Wyrm.Body, fontSize = 11.sp,
                color = Wyrm.Quiet, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RowScope.ProfileStat(value: String, label: String, onClick: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        Modifier
            .weight(1f)
            .scale(pressScale(pressed, onClick != null))
            .clickable(interactionSource = interaction, indication = null, enabled = onClick != null) { onClick?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Wyrm.Ink, maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"))
        Text(label, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet, maxLines = 1)
    }
}

@Composable
private fun ProfileChip(icon: Int, label: String, value: String) {
    Row(
        Modifier.height(28.dp).clip(WyrmCapsule).background(Wyrm.Well).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(painterResource(icon), null, tint = Wyrm.Quiet, modifier = Modifier.size(11.dp))
        Text(label, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet)
        Text(value, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Wyrm.Ink)
    }
}

@Composable
private fun RowScope.ProfileButton(title: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .height(38.dp)
            .scale(pressScale(pressed, enabled))
            .alpha(if (enabled) 1f else 0.45f)
            .clip(wyrmRounded(11.dp))
            .background(if (filled) Wyrm.Ink else Wyrm.Well)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (filled) Wyrm.OnInk else Wyrm.Ink)
    }
}

// ------------------------------------------------------------------ badges

@Composable
private fun ProfileBadgeStrip(badges: List<Badge>?, onOpen: (Badge) -> Unit) {
    val list = badges.orEmpty().withIndex()
        .sortedWith(compareBy<IndexedValue<Badge>>({ !it.value.earned }, { it.index }))
        .map { it.value }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text("BADGES", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp,
                letterSpacing = 0.9.sp, color = Wyrm.Quiet, modifier = Modifier.weight(1f))
            if (badges != null) {
                Text("${badges.count { it.earned }} of ${badges.size}", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp, color = Wyrm.Quiet)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (list.isEmpty()) {
                repeat(6) {
                    Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.padding(3.5.dp).size(58.dp).clip(CircleShape).background(Wyrm.Well))
                        Box(Modifier.width(48.dp).height(9.dp).clip(wyrmRounded(4.dp)).background(Wyrm.Well))
                    }
                }
            } else {
                list.forEach { badge ->
                    val interaction = remember(badge.id) { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Box(
                        Modifier
                            .scale(pressScale(pressed))
                            .clickable(interactionSource = interaction, indication = null) { onOpen(badge) },
                    ) { ProfileBadgeMedal(badge, 58.dp) }
                }
            }
        }
    }
}

@Composable
private fun ProfileBadgeMedal(badge: Badge, size: Dp, showsTitle: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        val ink = Wyrm.Ink
        val well = Wyrm.Well
        val live = Wyrm.Live
        Box(Modifier.padding(size * 0.06f).size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size.minDimension
                drawCircle(if (badge.earned) ink else well, radius = s / 2f)
                if (badge.earned) {
                    val w = kotlin.math.max(2.dp.toPx(), s * 0.04f)
                    drawCircle(live, radius = s / 2f + s * 0.06f - w / 2f, style = Stroke(w))
                } else if (badge.fraction > 0f) {
                    val w = kotlin.math.max(2.5.dp.toPx(), s * 0.05f)
                    val inset = s * 0.04f + w / 2f
                    drawArc(
                        live, startAngle = -90f, sweepAngle = 360f * badge.fraction, useCenter = false,
                        topLeft = Offset(inset, inset), size = Size(s - inset * 2f, s - inset * 2f),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
            }
            Icon(painterResource(badge.icon), null, tint = if (badge.earned) Wyrm.OnInk else Wyrm.Quiet,
                modifier = Modifier.size(size * 0.34f))
        }
        if (showsTitle) {
            Text(badge.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp,
                color = if (badge.earned) Wyrm.Ink else Wyrm.Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.width(size + 14.dp))
        }
    }
}

@Composable
private fun ProfileBadgeSheet(badge: Badge, insetBottom: Dp, onDone: () -> Unit) {
    val shape = wyrmRounded(28.dp)
    Column(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp + insetBottom)
            .fillMaxWidth()
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileBadgeMedal(badge, 92.dp, showsTitle = false)
        Text(badge.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Wyrm.Ink)
        Text(badge.detail, fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.Mute, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp))
        if (badge.earned) {
            Text("Earned", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Wyrm.Live,
                modifier = Modifier.clip(WyrmCapsule).background(Wyrm.Live.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp))
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp).clip(WyrmCapsule).background(Wyrm.Well)) {
                    Box(Modifier.width(kotlin.comparisons.maxOf(8.dp, maxWidth * badge.fraction)).height(8.dp).clip(WyrmCapsule).background(Wyrm.Live))
                }
                Text("${profileNumber(badge.progress.toLong())} of ${profileNumber(badge.goal.toLong())}", fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Wyrm.Quiet)
            }
        }
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) { PaperPrimaryButton("Done", onClick = onDone) }
    }
}

// ------------------------------------------------------------- trails grid

@Composable
private fun ProfileGridHeader() {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(painterResource(LucideR.drawable.lucide_ic_grid_3x3), null, tint = Wyrm.Ink, modifier = Modifier.size(14.dp))
                Text("Trails", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Wyrm.Ink)
            }
            Box(Modifier.align(Alignment.BottomCenter).width(90.dp).height(2.dp).background(Wyrm.Ink))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
    }
}

@Composable
private fun ProfileGridRow(row: List<Trail>, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { index ->
            val trail = row.getOrNull(index)
            if (trail == null) {
                Spacer(Modifier.weight(1f))
            } else {
                val interaction = remember(trail.id) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier
                        .weight(1f)
                        .scale(pressScale(pressed))
                        .clickable(interactionSource = interaction, indication = null) { onOpen(trail.id) },
                ) {
                    ProfileTrailTile(trail)
                    // A video trail shows its length, Instagram-style (OM, 2026-10-05).
                    trail.video?.let { clip ->
                        Text("▶ " + clipTime(clip.durationMs), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

/**
 * One square in a profile's grid: the thumbnail filling the square, or the
 * words of a text trail on ink. Beads show when there are any.
 */
@Composable
private fun ProfileTrailTile(trail: Trail) {
    val address = trail.thumbUrl ?: trail.photo?.url
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .background(if (trail.photo == null) Wyrm.Ink else Wyrm.Well),
    ) {
        if (trail.photo != null && address != null) {
            var image by remember(address) { mutableStateOf(TrailImages.peek(address)) }
            LaunchedEffect(address) { if (image == null) image = TrailImages.load(address) }
            image?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        } else {
            Text(
                trail.caption,
                fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp,
                color = Wyrm.OnInk, maxLines = 5, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxSize().padding(9.dp),
            )
        }
        if (trail.likeCount > 0) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Wyrm.Live))
                Text(
                    "${trail.likeCount}",
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 6f)),
                )
            }
        }
    }
}

@Composable
private fun ProfileGridEmpty(own: Boolean, failed: Boolean, name: String, onNewTrail: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painterResource(if (failed) LucideR.drawable.lucide_ic_wifi_off else LucideR.drawable.lucide_ic_grid_3x3),
            null, tint = Wyrm.Quiet, modifier = Modifier.size(26.dp),
        )
        Text(
            when {
                failed -> "Couldn't load trails"
                own -> "Share your first trail"
                else -> "No trails yet"
            },
            fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Wyrm.Ink,
        )
        Text(
            when {
                failed -> "Pull down to try again."
                own -> "A photo, a run, a few words. It shows up here and in Trails."
                else -> "When $name posts, it shows up here."
            },
            fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet, textAlign = TextAlign.Center,
        )
        if (own && !failed) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Text(
                "New trail",
                fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Wyrm.OnInk,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .scale(pressScale(pressed))
                    .clip(WyrmCapsule)
                    .background(Wyrm.Ink)
                    .clickable(interactionSource = interaction, indication = null, onClick = onNewTrail)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ avatar

@Composable
private fun ProfileAvatarOverlay(
    own: Boolean,
    progress: Float,
    optionsOpen: Boolean,
    start: Offset?,
    displayName: String,
    handle: String,
    avatarUrl: String,
    avatarKey: String,
    initials: String,
    photoBusy: Boolean,
    photoError: String,
    insetBottom: Dp,
    onCollapse: () -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEdit: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val big = min(maxWidth - 72.dp, 300.dp)
        val bigPx = with(density) { big.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centre = Offset(widthPx / 2f, heightPx * (if (own) 0.38f else 0.44f))
        val from = start ?: centre
        val at = Offset(lerp(from.x, centre.x, progress), lerp(from.y, centre.y, progress))
        val scale = lerp(with(density) { profileAvatarSize.toPx() } / bigPx, 1f, progress)
        // Below Android 12 there is no blur, so the paper veil does the work.
        val veil = if (Build.VERSION.SDK_INT >= 31) 0.45f else 0.9f

        Box(
            Modifier
                .fillMaxSize()
                .background(Wyrm.Paper.copy(alpha = veil * progress.coerceIn(0f, 1f)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCollapse),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (centre.y + bigPx / 2f + with(density) { 22.dp.toPx() }).roundToInt()) }
                .alpha(progress.coerceIn(0f, 1f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(displayName, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Wyrm.Ink)
            if (handle.isNotBlank()) Text(handle, fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
            if (own && photoError.isNotBlank()) {
                Text(photoError, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Badge, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 30.dp, end = 30.dp))
            }
        }

        Box(
            Modifier
                .offset { IntOffset((at.x - bigPx / 2f).roundToInt(), (at.y - bigPx / 2f).roundToInt()) }
                .size(big)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow((30f * progress.coerceIn(0f, 1f)).dp, wyrmRounded(big * 0.30f),
                    ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCollapse),
        ) {
            WyrmAvatar(avatarUrl, avatarKey, initials, big)
            if (own && photoBusy) {
                Box(
                    Modifier.fillMaxSize().clip(wyrmRounded(big * 0.30f)).background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) { IosSpinner(size = 30.dp, colour = Color.White) }
            }
        }

        if (own) {
            ProfilePhotoOptions(
                open = optionsOpen,
                progress = progress,
                maxWidth = maxWidth,
                hasPhoto = avatarUrl.isNotBlank(),
                busy = photoBusy,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = kotlin.comparisons.maxOf(40.dp, insetBottom + 12.dp)),
                onChangePhoto = onChangePhoto,
                onRemovePhoto = onRemovePhoto,
                onEdit = onEdit,
            )
        }
    }
}

/** A pill that opens into the photo options, as if the pill itself grew. */
@Composable
private fun ProfilePhotoOptions(
    open: Boolean,
    progress: Float,
    maxWidth: Dp,
    hasPhoto: Boolean,
    busy: Boolean,
    modifier: Modifier,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEdit: () -> Unit,
) {
    val rows = if (hasPhoto) 3 else 2
    val full = min(maxWidth - 32.dp, 480.dp)
    val width by animateDpAsState(if (open) full else 120.dp, iosSpring(0.5f, 0.8f), label = "photo-options-width")
    val height by animateDpAsState(if (open) (56 * rows).dp else 46.dp, iosSpring(0.5f, 0.8f), label = "photo-options-height")
    val shape = wyrmRounded(if (open) 24.dp else 23.dp)
    val shown = progress.coerceIn(0f, 1f)
    Column(
        modifier
            .graphicsLayer {
                alpha = shown
                val s = lerp(0.6f, 1f, shown)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .width(width)
            .height(height)
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
    ) {
        if (open) {
            ProfileOptionRow("Change photo", LucideR.drawable.lucide_ic_image, first = true, enabled = !busy, onClick = onChangePhoto)
            if (hasPhoto) {
                ProfileOptionRow("Remove this photo", LucideR.drawable.lucide_ic_trash_2, destructive = true, enabled = !busy, onClick = onRemovePhoto)
            }
            ProfileOptionRow("Edit profile", LucideR.drawable.lucide_ic_pencil, enabled = !busy, onClick = onEdit)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(34.dp).height(5.dp).clip(WyrmCapsule).background(Wyrm.Quiet.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun ProfileOptionRow(
    title: String,
    icon: Int,
    first: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (destructive) Wyrm.Badge else Wyrm.Ink
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
        Row(
            Modifier
                .fillMaxWidth()
                .height(55.dp)
                .scale(pressScale(pressed, enabled))
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = tint, maxLines = 1)
        }
    }
}
