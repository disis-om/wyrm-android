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

@Composable
fun LobbyScreen(
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
) {
    Box(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        WyrmMark(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 38.dp, top = insetTop + 5.dp),
            size = 110.dp,
            ink = Wyrm.Ink.copy(alpha = 0.045f),
            unfilled = androidx.compose.ui.graphics.Color.Transparent,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insetTop, bottom = insetBottom)
                .padding(horizontal = 40.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                LobbyPaperButton(
                    label = "Quick settings",
                    icon = R.drawable.ic_wyrm_tune,
                    modifier = Modifier.width(176.dp),
                    enabled = !entering,
                    onClick = onQuickSettings,
                )
                Spacer(Modifier.weight(1f))
                LobbyPaperButton(
                    label = "Home",
                    icon = R.drawable.ic_wyrm_home,
                    modifier = Modifier.width(112.dp),
                    enabled = !entering,
                    onClick = onHome,
                )
                LobbyPaperButton(
                    label = "Play with AI",
                    icon = R.drawable.ic_play_assist,
                    modifier = Modifier.width(142.dp),
                    enabled = nickname.isNotBlank() && !entering,
                    onClick = onPlayAi,
                )
                LobbyPlayButton(
                    entering = entering,
                    enabled = address.isNotBlank() && nickname.isNotBlank() && !entering,
                    modifier = Modifier.width(180.dp),
                    onClick = onPlay,
                )
            }
        }
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
                fontSize = 48.sp,
                lineHeight = 52.sp,
                color = if (enabled) Wyrm.Ink else Wyrm.Quiet,
            ),
            cursorBrush = SolidColor(Wyrm.Link),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (nickname.isNotBlank()) onDone()
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
                            fontSize = 48.sp,
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
