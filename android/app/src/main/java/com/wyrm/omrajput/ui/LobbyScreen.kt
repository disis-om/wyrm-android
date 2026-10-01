package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun LobbyScreen(
    address: String,
    serverId: Int,
    cluster: Int,
    nickname: String,
    entering: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onNicknameChange: (String) -> Unit,
    onPlayAi: () -> Unit,
    onPlay: () -> Unit,
    onHome: () -> Unit,
    /** "Share run": only while the last finished run can still be shared. */
    canShareRun: Boolean = false,
    onShareRun: () -> Unit = {},
    /** The last run since the app started (OM, 2026-10-02); null shows nothing. */
    lastRun: LastRun? = null,
) {
    var quickSettings by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = quickSettings,
        transitionSpec = {
            if (targetState) {
                (fadeIn() + slideInHorizontally { it / 7 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 12 })
            } else {
                (fadeIn() + slideInHorizontally { -it / 9 }) togetherWith
                    (fadeOut() + slideOutHorizontally { it / 7 })
            }
        },
        label = "lobby-route",
    ) { showingQuickSettings ->
        if (showingQuickSettings) {
            LobbyQuickSettings(
                insetTop = insetTop,
                insetBottom = insetBottom,
                onBack = { quickSettings = false },
            )
        } else {
            LobbyReadyRoom(
                address = address,
                serverId = serverId,
                cluster = cluster,
                nickname = nickname,
                entering = entering,
                insetTop = insetTop,
                insetBottom = insetBottom,
                onNicknameChange = onNicknameChange,
                onPlayAi = onPlayAi,
                onPlay = onPlay,
                onHome = onHome,
                onQuickSettings = { quickSettings = true },
                canShareRun = canShareRun,
                onShareRun = onShareRun,
                lastRun = lastRun,
            )
        }
    }
}

@Composable
private fun LobbyReadyRoom(
    address: String,
    serverId: Int,
    cluster: Int,
    nickname: String,
    entering: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onNicknameChange: (String) -> Unit,
    onPlayAi: () -> Unit,
    onPlay: () -> Unit,
    onHome: () -> Unit,
    onQuickSettings: () -> Unit,
    canShareRun: Boolean,
    onShareRun: () -> Unit,
    lastRun: LastRun?,
) {
    // Playing upright (Settings › Controls › Play orientation): the same room, stacked.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight > maxWidth) {
            LobbyReadyRoomUpright(
                address, serverId, cluster, nickname, entering, insetTop, insetBottom,
                onNicknameChange, onPlayAi, onPlay, onHome, canShareRun, onShareRun, lastRun,
            )
        } else {
    Box(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        if (lastRun == null) {
            WyrmMark(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 38.dp, top = insetTop + 5.dp),
                size = 110.dp,
                ink = Wyrm.Ink.copy(alpha = 0.045f),
                unfilled = androidx.compose.ui.graphics.Color.Transparent,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insetTop, bottom = insetBottom)
                .padding(horizontal = 40.dp, vertical = 24.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    WyrmLabel("Ready room", color = Wyrm.Quiet)
                    Text(
                        "Enter the arena",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 29.sp,
                        letterSpacing = (-0.6).sp,
                        color = Wyrm.Ink,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (lastRun != null) LobbyLastRunCard(lastRun, compact = true)
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
            Spacer(Modifier.weight(0.65f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.25f)
                        .clip(wyrmRounded(16.dp))
                        .background(Wyrm.Card)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(16.dp))
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                ) {
                    WyrmLabel("Selected arena", color = Wyrm.Quiet)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        address.ifBlank { "No arena selected" },
                        fontFamily = Wyrm.Display,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        color = if (address.isBlank()) Wyrm.Quiet else Wyrm.Ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ArenaIdentity(
                            label = "Server code",
                            value = when {
                                address.isBlank() -> "—"
                                serverId == -2 -> "…"
                                serverId >= 0 -> serverId.toString()
                                else -> "CUSTOM"
                            },
                        )
                        if (cluster >= 0) {
                            ArenaIdentity("Cluster", cluster.toString())
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.92f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    WyrmLabel("Playing as", color = Wyrm.Quiet)
                    Spacer(Modifier.height(7.dp))
                    LobbyName(
                        nickname = nickname,
                        enabled = !entering,
                        onNicknameChange = onNicknameChange,
                        onDone = onPlay,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Quick settings is gone from the lobby (OM); Home takes its place.
                LobbyPaperButton(
                    label = "Home",
                    icon = R.drawable.ic_wyrm_home,
                    modifier = Modifier.width(128.dp),
                    enabled = !entering,
                    onClick = onHome,
                )
                Spacer(Modifier.weight(1f))
                // The last run, as a trail (OM, 2026-09-30).
                if (canShareRun) {
                    LobbyShareRunButton(
                        modifier = Modifier.width(138.dp),
                        enabled = !entering,
                        onClick = onShareRun,
                    )
                }
                LobbyPaperButton(
                    label = "Play with AI",
                    icon = R.drawable.ic_play_assist,
                    modifier = Modifier.width(142.dp),
                    // A blank name is allowed: the arena shows no name (OM).
                    enabled = !entering,
                    onClick = onPlayAi,
                )
                LobbyPlayButton(
                    entering = entering,
                    enabled = address.isNotBlank() && !entering,
                    modifier = Modifier.width(180.dp),
                    onClick = onPlay,
                )
            }
        }
    }
        }
    }
}

/**
 * The Ready Room held upright (OM, 2026-10-01; redesigned 2026-10-02 for the
 * thumb zone). A top bar (round Home on the left, "READY ROOM" in the middle,
 * a faint W on the right), then the title, the arena card and the name card,
 * which scroll if the screen is short. The actions sit in a dock at the bottom
 * where the thumb is: Play with AI and Share run side by side, and PLAY full
 * width and tallest under them. Nothing important sits in the top corners.
 */
@Composable
private fun LobbyReadyRoomUpright(
    address: String,
    serverId: Int,
    cluster: Int,
    nickname: String,
    entering: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onNicknameChange: (String) -> Unit,
    onPlayAi: () -> Unit,
    onPlay: () -> Unit,
    onHome: () -> Unit,
    canShareRun: Boolean,
    onShareRun: () -> Unit,
    lastRun: LastRun?,
) {
    Column(Modifier.fillMaxSize().background(Wyrm.Paper).padding(top = insetTop)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .height(48.dp),
        ) {
            LobbyRoundHome(
                enabled = !entering,
                onClick = onHome,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            WyrmLabel("Ready room", modifier = Modifier.align(Alignment.Center), color = Wyrm.Quiet)
            // The real curvy Wyrm mark, in full ink (OM: the mark, never a letter).
            WyrmMark(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                size = 30.dp,
                ink = Wyrm.Ink,
                unfilled = Color.Transparent,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Enter the arena",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = (-0.6).sp,
                color = Wyrm.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Check your arena and name, then play.",
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                color = Wyrm.Quiet,
            )
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(wyrmRounded(18.dp))
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(18.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                WyrmLabel("Selected arena", color = Wyrm.Quiet)
                Spacer(Modifier.height(7.dp))
                Text(
                    address.ifBlank { "No arena selected" },
                    fontFamily = Wyrm.Display,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    color = if (address.isBlank()) Wyrm.Quiet else Wyrm.Ink,
                    maxLines = 1,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ArenaIdentity(
                        label = "Server code",
                        value = when {
                            address.isBlank() -> "—"
                            serverId == -2 -> "…"
                            serverId >= 0 -> serverId.toString()
                            else -> "CUSTOM"
                        },
                    )
                    if (cluster >= 0) {
                        ArenaIdentity("Cluster", cluster.toString())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(wyrmRounded(18.dp))
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(18.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                WyrmLabel("Playing as", color = Wyrm.Quiet)
                Spacer(Modifier.height(6.dp))
                LobbyName(
                    nickname = nickname,
                    enabled = !entering,
                    onNicknameChange = onNicknameChange,
                    onDone = onPlay,
                    fontSize = 36.sp,
                )
            }
            if (lastRun != null) {
                Spacer(Modifier.height(12.dp))
                LobbyLastRunCard(lastRun)
            }
            Spacer(Modifier.height(20.dp))
        }

        // The dock: the thumb's reach, PLAY lowest and largest.
        val dock = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(dock)
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, dock)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + insetBottom),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LobbyPaperButton(
                    label = "Play with AI",
                    icon = R.drawable.ic_play_assist,
                    modifier = Modifier.weight(1f),
                    enabled = !entering,
                    onClick = onPlayAi,
                )
                if (canShareRun) {
                    LobbyShareRunButton(
                        modifier = Modifier.weight(1f),
                        enabled = !entering,
                        onClick = onShareRun,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LobbyPlayButton(
                entering = entering,
                enabled = address.isNotBlank() && !entering,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPlay,
            )
        }
    }
}

/** The upright Ready Room's Home: a round 44 dp card button with the house. */
@Composable
private fun LobbyRoundHome(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.94f else 1f,
        spring(dampingRatio = 0.58f, stiffness = 820f),
        label = "lobby-home-press",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_wyrm_home),
            contentDescription = "Home",
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(if (enabled) Wyrm.Ink else Wyrm.Quiet),
        )
    }
}

@Composable
private fun ArenaIdentity(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(wyrmRounded(8.dp))
            .background(Wyrm.Well)
            .border(1.dp, Wyrm.Rule, wyrmRounded(8.dp))
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            letterSpacing = 1.2.sp,
            color = Wyrm.Quiet,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            value,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Wyrm.Ink,
        )
    }
}

@Composable
private fun LobbyName(
    nickname: String,
    enabled: Boolean,
    onNicknameChange: (String) -> Unit,
    onDone: () -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit = 48.sp,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column {
        BasicTextField(
            value = nickname,
            onValueChange = { typed ->
                onNicknameChange(typed.filterNot { it.isISOControl() }.take(24))
            },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Wyrm.Display,
                fontSize = fontSize,
                lineHeight = fontSize * 1.08f,
                color = if (enabled) Wyrm.Ink else Wyrm.Quiet,
            ),
            cursorBrush = SolidColor(Wyrm.Link),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onDone()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (nickname.isEmpty()) {
                        Text(
                            "Wyrm Player",
                            fontFamily = Wyrm.Display,
                            fontSize = fontSize,
                            color = Wyrm.Quiet,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Wyrm.Ink.copy(alpha = 0.34f), Wyrm.Rule)
                    )
                )
        )
    }
}

@Composable
private fun LobbyPlayButton(
    entering: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.975f else 1f,
        spring(dampingRatio = 0.58f, stiffness = 820f),
        label = "lobby-play-press",
    )
    val shape = wyrmRounded(Wyrm.Pill)
    Row(
        modifier = modifier
            .height(62.dp)
            .scale(scale)
            .clip(shape)
            .background(if (enabled) Wyrm.Ink else Wyrm.Track)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entering) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = Wyrm.Quiet,
                strokeWidth = 1.6.dp,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_wyrm_play),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (entering) "ENTERING" else "PLAY",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 2.5.sp,
            color = if (enabled) Wyrm.White else Wyrm.Quiet,
        )
    }
}

/**
 * "Share run", made the one thing the eye goes to (OM, 2026-09-30). The lobby
 * is paper and ink, so this is its only colour: a warm gradient, a halo that
 * breathes out every 1.8 s and a light sweep across the face (contrast first,
 * motion second, as CTA guides say). Motion stops when the phone's
 * animations are off. Wyrm iOS: `WyrmShareRunButton` in `WyrmLobby.swift`.
 */
@Composable
private fun LobbyShareRunButton(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val still = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(11.dp)
    val loop = rememberInfiniteTransition(label = "share-run")
    val halo by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "halo")
    val sweep by loop.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(2600, delayMillis = 500, easing = FastOutSlowInEasing)), label = "sweep")
    val moving = enabled && !still
    Box(
        modifier = modifier
            .height(49.dp)
            .scale(pressScale(pressed, enabled))
            .drawBehind {
                if (!moving) return@drawBehind
                // The halo: a coral ring that grows out and fades.
                val spread = 9.dp.toPx() * halo
                drawRoundRect(
                    color = ShareRunCoral.copy(alpha = 0.55f * (1f - halo)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2, size.height + spread * 2),
                    cornerRadius = CornerRadius(11.dp.toPx() + spread),
                )
            }
            .clip(shape)
            .background(Brush.linearGradient(ShareRunWarm), alpha = if (enabled) 1f else 0.45f)
            .drawBehind {
                if (!moving) return@drawBehind
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.42f), Color.Transparent),
                        start = Offset(x - size.width * 0.22f, 0f),
                        end = Offset(x + size.width * 0.22f, size.height),
                    ),
                )
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(com.composables.icons.lucide.R.drawable.lucide_ic_share),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
            Spacer(Modifier.width(8.dp))
            Text("Share run", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.White)
        }
    }
}

/** Share run's colours: gold to coral to magenta, found nowhere else in the lobby. */
private val ShareRunWarm = listOf(Color(0xFFFFA91F), Color(0xFFFF5A5F), Color(0xFFD63AF9))
private val ShareRunCoral = Color(0xFFFF5A5F)

@Composable
private fun LobbyPaperButton(
    label: String,
    icon: Int,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(11.dp)
    Row(
        modifier = modifier
            .height(49.dp)
            .scale(pressScale(pressed, enabled))
            .clip(shape)
            .background(if (pressed) Wyrm.Well else Wyrm.Card)
            .border(1.dp, if (pressed) Wyrm.Ink.copy(alpha = 0.22f) else Wyrm.Rule, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            alpha = if (enabled) 1f else 0.42f,
            colorFilter = ColorFilter.tint(if (enabled) Wyrm.Ink else Wyrm.Quiet),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (enabled) Wyrm.Ink else Wyrm.Quiet,
        )
    }
}

@Composable
fun LobbyQuickSettings(
    onBack: () -> Unit = {},
    insetTop: Dp = 0.dp,
    insetBottom: Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insetTop, bottom = insetBottom)
                .padding(horizontal = 34.dp, vertical = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LobbyPaperButton(
                    label = "Back",
                    icon = R.drawable.ic_wyrm_back,
                    modifier = Modifier.width(106.dp),
                    enabled = true,
                    onClick = onBack,
                )
                Spacer(Modifier.width(18.dp))
                Column {
                    WyrmLabel("Between rounds", color = Wyrm.Quiet)
                    Text(
                        "Quick settings",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 27.sp,
                        color = Wyrm.Ink,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(520.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(wyrmRounded(16.dp))
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(16.dp)),
            ) {
                Column(Modifier.padding(horizontal = 30.dp, vertical = 26.dp)) {
                    WyrmLabel("Quick settings", color = Wyrm.Quiet)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "This area of Wyrm is in development.",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        color = Wyrm.Ink,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "The controls you reach for between rounds will live here.",
                        fontFamily = Wyrm.Body,
                        fontSize = 11.sp,
                        color = Wyrm.Mute,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** "4:12", or "1:04:12" past an hour. */
internal fun lobbyRunTime(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The arena as the in-game minimap draws it (a round world on a square frame),
 * with a dot where the last run ended (OM, 2026-10-02). `x`/`y` are 0..1 of
 * the arena square; the dot breathes so it reads as "here".
 */
@Composable
internal fun LobbyRunMap(x: Float, y: Float, size: Dp, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "run-map").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "run-map-pulse",
    )
    val well = Wyrm.Well
    val rule = Wyrm.Rule
    val ink = Wyrm.Ink
    val dot = Wyrm.Blood
    val ring = Wyrm.Card
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(well, r, c)
        // Faint quarter lines, like the slither minimap's cross.
        drawLine(rule, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(rule, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        drawCircle(ink.copy(alpha = 0.07f), r * 0.5f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawCircle(ink.copy(alpha = 0.16f), r - 0.5.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        val at = Offset(
            c.x + (x.coerceIn(0f, 1f) - 0.5f) * 2f * r,
            c.y + (y.coerceIn(0f, 1f) - 0.5f) * 2f * r,
        )
        val dotR = (r * 0.085f).coerceAtLeast(3.dp.toPx())
        drawCircle(dot.copy(alpha = 0.35f * (1f - pulse)), dotR * (1f + pulse * 2.2f), at)
        drawCircle(ring, dotR + 1.5.dp.toPx(), at)
        drawCircle(dot, dotR, at)
    }
}

@Composable
private fun LobbyRunStat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(
            label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            letterSpacing = 1.3.sp,
            color = Wyrm.Quiet,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontFamily = if (big) Wyrm.Display else Wyrm.Body,
            fontWeight = if (big) FontWeight.Normal else FontWeight.Bold,
            fontSize = if (big) 26.sp else 17.sp,
            lineHeight = if (big) 28.sp else 20.sp,
            color = Wyrm.Ink,
            maxLines = 1,
        )
    }
}

/**
 * The last run (OM, 2026-10-02): where it ended on the arena, the score, the
 * kills and how long it lasted. Only after a run since the app started; with
 * no run there is nothing at all (no empty text). `compact` is the sideways
 * room's header chip.
 */
@Composable
internal fun LobbyLastRunCard(run: LastRun, modifier: Modifier = Modifier, compact: Boolean = false) {
    val hasMap = !run.mapX.isNaN() && !run.mapY.isNaN()
    val score = "%,d".format(run.score)
    if (compact) {
        Row(
            modifier = modifier
                .clip(wyrmRounded(16.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(16.dp))
                .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasMap) {
                LobbyRunMap(run.mapX, run.mapY, 48.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                WyrmLabel("Last run", color = Wyrm.Quiet)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                    LobbyRunStat("Score", score)
                    LobbyRunStat("Kills", run.kills.toString())
                    LobbyRunStat("Time", lobbyRunTime(run.seconds))
                }
            }
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(wyrmRounded(18.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasMap) {
            LobbyRunMap(run.mapX, run.mapY, 92.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            WyrmLabel("Last run", color = Wyrm.Quiet)
            Spacer(Modifier.height(8.dp))
            LobbyRunStat("Score", score, big = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                LobbyRunStat("Kills", run.kills.toString())
                LobbyRunStat("Time", lobbyRunTime(run.seconds))
            }
        }
    }
}
