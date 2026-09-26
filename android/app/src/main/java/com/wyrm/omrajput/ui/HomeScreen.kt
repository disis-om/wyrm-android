package com.wyrm.omrajput.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.R
import java.text.NumberFormat
import java.util.Locale

data class HomeSection(
    val title: String,
    val detail: String,
    val icon: Int,
    val enabled: Boolean = true,
    val accent: Color? = null,
    val onOpen: (Rect) -> Unit,
)

/**
 * Spec page 01 — Play.
 *
 * Four-tab paper root: nearest arena, loadout, rooms & team. Enter lobby still
 * opens the landscape lobby; the match is not dialled from here.
 */
@Composable
fun HomeScreen(
    nickname: String,
    nicknameEditable: Boolean,
    displayName: String,
    username: String,
    avatarUrl: String,
    avatarKey: String,
    arenaTitle: String,
    arenaPing: Int,
    arenaPlayers: Int,
    arenaOnline: Boolean,
    arenaReady: Boolean,
    bestScore: Long,
    kills: Long,
    foodLabel: String,
    foodStyle: Int,
    controlsLabel: String,
    publicRoomCode: String,
    pickServerLabel: String,
    teamName: String,
    teamOnline: Int,
    teamConfigured: Boolean,
    unreadNotifications: Int,
    arenaEndpoint: String = "",
    arenaLive: Boolean = false,
    voiceRoomsLive: Int = 0,
    voiceRoomName: String = "",
    insetTop: Dp,
    insetBottom: Dp,
    showRootTabs: Boolean = true,
    onAppear: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onNicknameDone: () -> Unit,
    onEnterArena: () -> Unit,
    onPickServer: (Rect) -> Unit,
    onOpenProfile: (Rect) -> Unit,
    onOpenFood: (Rect) -> Unit,
    onOpenControls: (Rect) -> Unit,
    onOpenMode: (Rect) -> Unit,
    onOpenTeam: (Rect) -> Unit,
    onOpenVoice: (Rect) -> Unit,
    onTabNotifications: (Rect) -> Unit,
    onTabSocial: (Rect) -> Unit,
    onTabSkin: (Rect) -> Unit,
    onTabSettings: (Rect) -> Unit,
) {
    LaunchedEffect(Unit) { onAppear() }
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    var editingNickname by remember { mutableStateOf(false) }
    var nicknameEditorBounds by remember { mutableStateOf(Rect.Zero) }

    fun finishNickname() {
        if (!editingNickname) return
        editingNickname = false
        focusManager.clearFocus()
        onNicknameDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper)
            .pointerInput(editingNickname, nicknameEditorBounds) {
                if (!editingNickname) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    if (!nicknameEditorBounds.contains(down.position)) finishNickname()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = insetTop),
        ) {
            Header(
                nickname = nickname,
                displayName = displayName,
                username = username,
                avatarUrl = avatarUrl,
                avatarKey = avatarKey,
                nicknameEditable = nicknameEditable,
                editing = editingNickname,
                onNicknameChange = onNicknameChange,
                onStartEditing = { editingNickname = true },
                onFinishRename = ::finishNickname,
                onEditorBounds = { nicknameEditorBounds = it },
                onOpenProfile = onOpenProfile,
            )
            IosPaperCard {
                IosArenaCard(
                    title = arenaTitle,
                    live = arenaLive,
                    players = arenaPlayers,
                    endpoint = arenaEndpoint,
                    ready = arenaReady,
                    onEnter = onEnterArena,
                    onPickServer = onPickServer,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IosMetric("BEST SCORE", grouped(bestScore))
                    Box(Modifier.width(1.dp).height(51.dp).background(Wyrm.Rule))
                    IosMetric("TOTAL KILLS", grouped(kills))
                }
            }
            IosSectionLabel("Loadout")
            IosPaperCard {
                IosLoadoutRow("Food", foodLabel, first = true, leading = { IosFoodWell() }) { onOpenFood(Rect.Zero) }
                IosLoadoutRow("Controls", controlsLabel, leading = { IosLoadoutIcon(IosGlyph.GAMECONTROLLER) }) { onOpenControls(Rect.Zero) }
                IosLoadoutRow("Mode", "", leading = { IosLoadoutIcon(IosGlyph.SCOPE) }) { onOpenMode(Rect.Zero) }
            }
            IosSectionLabel("Rooms & team")
            IosPaperCard {
                IosListRow(
                    title = "Voice rooms",
                    detail = voiceRoomName.ifBlank { "Own and community rooms" },
                    value = "$voiceRoomsLive live",
                    glyph = IosGlyph.MIC,
                    tint = Wyrm.Live,
                ) { onOpenVoice(Rect.Zero) }
                IosListRow(
                    title = "Team mode",
                    detail = "Original engine team layer",
                    value = "Open",
                    glyph = IosGlyph.PERSON_3,
                    tint = Wyrm.Live,
                ) { onOpenTeam(Rect.Zero) }
            }
            Spacer(Modifier.height(LocalRootTabClearance.current))
        }
        if (showRootTabs) {
            RootTabs(
                selected = RootTab.PLAY,
                insetBottom = insetBottom,
                unreadNotifications = unreadNotifications,
                onNotifications = onTabNotifications,
                onPlay = {},
                onSocial = onTabSocial,
                onSkin = onTabSkin,
                onSettings = onTabSettings,
            )
        }
    }
}

@Composable
private fun Header(
    nickname: String,
    displayName: String,
    username: String,
    avatarUrl: String,
    avatarKey: String,
    nicknameEditable: Boolean,
    editing: Boolean,
    onNicknameChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onFinishRename: () -> Unit,
    onEditorBounds: (Rect) -> Unit,
    onOpenProfile: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val name = displayName.ifBlank { "Wyrm" }
    val handle = username.trim().removePrefix("@")
    val initial = name.filter { it.isLetter() }.take(2).uppercase().ifEmpty { "W" }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var editorFocused by remember { mutableStateOf(false) }
    var keyboardSeen by remember { mutableStateOf(false) }

    LaunchedEffect(editing) {
        if (editing) {
            editorFocused = false
            keyboardSeen = false
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(editing, imeVisible) {
        if (!editing) return@LaunchedEffect
        if (imeVisible) keyboardSeen = true
        else if (keyboardSeen) onFinishRename()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(wyrmRounded(10.dp))
                .clickable(enabled = nicknameEditable && !editing, onClick = onStartEditing)
                .padding(end = 12.dp),
        ) {
            Text(
                text = "Wyrm",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                letterSpacing = 1.sp,
                color = Wyrm.Quiet,
            )
            if (editing) {
                BasicTextField(
                    value = nickname,
                    onValueChange = { typed ->
                        onNicknameChange(typed.filterNot { it.isISOControl() }.take(24))
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 29.sp,
                        letterSpacing = (-0.5).sp,
                        color = Wyrm.Ink,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onFinishRename() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) editorFocused = true
                            else if (editing && editorFocused) onFinishRename()
                        }
                        .onGloballyPositioned { onEditorBounds(it.boundsInRoot()) },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (nickname.isBlank()) {
                                Text(
                                    "Wyrm Player",
                                    fontFamily = Wyrm.Body,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 29.sp,
                                    letterSpacing = (-0.5).sp,
                                    color = Wyrm.Faint,
                                )
                            }
                            inner()
                        }
                    },
                )
            } else {
                Text(
                    text = nickname.ifBlank { "Wyrm Player" },
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 29.sp,
                    letterSpacing = (-0.5).sp,
                    color = if (nickname.isBlank()) Wyrm.Faint else Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Tap to rename in-game name",
                fontFamily = Wyrm.Body,
                fontSize = 9.5.sp,
                color = Wyrm.Quiet.copy(alpha = 0.55f),
            )
        }
        Row(
            modifier = Modifier
                .scale(pressScale(pressed))
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .clip(wyrmRounded(12.dp))
                .clickable(interactionSource = interaction, indication = null) {
                    onOpenProfile(bounds)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = name,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (handle.isNotBlank()) {
                    Text(
                        text = "@$handle",
                        fontFamily = Wyrm.Body,
                        fontSize = 10.5.sp,
                        color = Wyrm.Quiet,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            WyrmAvatar(
                url = avatarUrl,
                avatarKey = avatarKey,
                initial = initial,
                size = 36.dp,
                corner = 10.8.dp,
            )
        }
    }
}

@Composable
private fun ArenaCard(
    title: String,
    ping: Int,
    players: Int,
    online: Boolean,
    ready: Boolean,
    bestScore: Long,
    kills: Long,
    onEnter: () -> Unit,
    onPickServer: (Rect) -> Unit,
) {
    val card = wyrmRounded(16.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(card)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, card),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(wyrmRounded(99.dp))
                        .background(if (online) Wyrm.Live else Wyrm.Quiet),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (ready) "Nearest arena" else "Select an arena",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp,
                    color = if (online) Wyrm.Live else Wyrm.Quiet,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = title,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    letterSpacing = (-0.3).sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ping > 0) {
                    Text(
                        text = "$ping ms",
                        fontFamily = Wyrm.Body,
                        fontSize = 13.sp,
                        color = Wyrm.Mute,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                text = if (players > 0) {
                    "${grouped(players)} snakes in the arena"
                } else if (ready) {
                    "Waiting on the directory"
                } else {
                    "Pick a server to enter"
                },
                fontFamily = Wyrm.Body,
                fontSize = 13.5.sp,
                color = Wyrm.Mute,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(4.dp)
                    .clip(wyrmRounded(99.dp))
                    .background(Wyrm.Track),
            ) {
                val load = (players / 500f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (load == 0f) 0.02f else load)
                        .height(4.dp)
                        .background(Wyrm.Ink),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                EnterButton(
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onEnter,
                )
                Spacer(Modifier.width(9.dp))
                GlobeButton(onClick = onPickServer)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.dp, color = Color.Transparent, shape = RoundedCornerShape(0))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Wyrm.Rule),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            StatCell("Best score", grouped(bestScore), Modifier.weight(1f))
            Box(
                Modifier
                    .width(1.dp)
                    .height(52.dp)
                    .background(Wyrm.Rule),
            )
            StatCell("Total kills", grouped(kills), Modifier.weight(1f))
        }
    }
}

/** Wyrm iOS Play's arena block: live dot, arena code, players, endpoint, load bar, Enter lobby and the globe. */
@Composable
private fun IosArenaCard(
    title: String,
    live: Boolean,
    players: Int,
    endpoint: String,
    ready: Boolean,
    onEnter: () -> Unit,
    onPickServer: (Rect) -> Unit,
) {
    Column(Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(WyrmCapsule).background(if (live) Wyrm.Live else Wyrm.Quiet))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (live) "LIVE ARENA" else "ARENA DIRECTORY",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                letterSpacing = 0.8.sp,
                color = if (live) Wyrm.Live else Wyrm.Quiet,
            )
        }
        Row(Modifier.padding(top = 11.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = Wyrm.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (ready) {
                Text("$players players", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet)
            }
        }
        Text(
            text = endpoint.ifBlank { "Choose a live arena or enter an address." },
            fontFamily = Wyrm.Body,
            fontSize = 12.5.sp,
            color = Wyrm.Mute,
            modifier = Modifier.padding(top = 3.dp),
        )
        Box(
            Modifier
                .padding(top = 13.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(WyrmCapsule)
                .background(Wyrm.Track),
        ) {
            val load = (players / 2000f).coerceIn(0f, 1f)
            if (load > 0f) Box(Modifier.fillMaxWidth(load).height(4.dp).clip(WyrmCapsule).background(Wyrm.Ink))
        }
        Row(Modifier.padding(top = 16.dp)) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .scale(pressScale(pressed, ready))
                    .clip(wyrmRounded(11.dp))
                    .background(if (ready) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
                    .clickable(interactionSource = interaction, indication = null, enabled = ready, onClick = onEnter),
                contentAlignment = Alignment.Center,
            ) {
                Text("Enter lobby", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.OnInk)
            }
            Spacer(Modifier.width(9.dp))
            var bounds by remember { mutableStateOf(Rect.Zero) }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .clip(wyrmRounded(11.dp))
                    .border(1.dp, Wyrm.Rule, wyrmRounded(11.dp))
                    .clickable { onPickServer(bounds) },
                contentAlignment = Alignment.Center,
            ) {
                IosIcon(IosGlyph.GLOBE, Wyrm.Mute, size = 19.dp, semibold = true)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.77.sp,
            color = Wyrm.Quiet,
        )
        Text(
            text = value,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = Wyrm.Ink,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EnterButton(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(46.dp)
            .scale(pressScale(pressed, enabled))
            .clip(wyrmRounded(11.dp))
            .background(if (enabled) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Enter lobby",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5.sp,
            color = Wyrm.OnInk,
        )
    }
}

@Composable
private fun GlobeButton(onClick: (Rect) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val shape = wyrmRounded(11.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(pressScale(pressed))
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(shape)
            .border(1.dp, Wyrm.Rule, shape)
            .clickable(interactionSource = interaction, indication = null) { onClick(bounds) },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_play_globe),
            contentDescription = "Pick a server",
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(Wyrm.Mute),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        letterSpacing = 0.92.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
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
private fun LoadoutRow(
    title: String,
    value: String,
    first: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onOpen: (Rect) -> Unit,
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
                .height(52.dp)
                .scale(pressScale(pressed))
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .clickable(interactionSource = interaction, indication = null) { onOpen(bounds) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            fontFamily = Wyrm.Body,
            fontSize = 15.5.sp,
            color = Wyrm.Ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                color = Wyrm.Quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
        }
        Chevron()
        }
    }
}

@Composable
private fun RoomRow(code: String, onCopy: () -> Unit, onOpen: (Rect) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Your public room",
                fontFamily = Wyrm.Body,
                fontSize = 15.5.sp,
                color = Wyrm.Ink,
            )
            Text(
                text = code.ifBlank { "None yet" },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                color = Wyrm.Quiet,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (code.isNotBlank()) {
            Text(
                text = "Copy",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Wyrm.Link,
                modifier = Modifier.clickable(onClick = onCopy),
            )
        }
    }
}

@Composable
private fun TeamRow(
    name: String,
    online: Int,
    configured: Boolean,
    onOpen: (Rect) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val tag = name.filter { it.isLetterOrDigit() }.take(3).uppercase().ifEmpty { "TM" }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Wyrm.RowRule),
        )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(pressScale(pressed))
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clickable(interactionSource = interaction, indication = null) { onOpen(bounds) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(wyrmRounded(8.dp))
                .background(Color(0xFFE6EFE8)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tag,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                color = Wyrm.Live,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Team mode",
            fontFamily = Wyrm.Body,
            fontSize = 15.5.sp,
            color = Wyrm.Ink,
            modifier = Modifier.weight(1f),
        )
        if (configured) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(wyrmRounded(99.dp))
                        .background(Wyrm.Live),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$online online",
                    fontFamily = Wyrm.Body,
                    fontSize = 14.sp,
                    color = Wyrm.Live,
                )
            }
        } else {
            Text(
                text = "Connect a team",
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                color = Wyrm.Quiet,
            )
        }
        Spacer(Modifier.width(6.dp))
        Chevron()
    }
    }
}

@Composable
private fun FoodWell(style: Int) {
    Canvas(
        modifier = Modifier
            .size(26.dp)
            .clip(wyrmRounded(8.dp))
            .background(Wyrm.Well),
    ) {
        val radius = if (style == 0) size.minDimension * 0.13f else size.minDimension * 0.115f
        drawCircle(Wyrm.Live, radius, Offset(size.width * 0.32f, size.height * 0.38f))
        drawCircle(Wyrm.Link, radius * 0.82f, Offset(size.width * 0.68f, size.height * 0.34f))
        drawCircle(Color(0xFFD38B5D), radius * 1.08f, Offset(size.width * 0.55f, size.height * 0.69f))
    }
}

@Composable
private fun WellIcon(icon: Int) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(wyrmRounded(8.dp))
            .background(Wyrm.Well),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(Wyrm.Mute),
        )
    }
}

@Composable
private fun Chevron() {
    Text(
        text = "›",
        fontFamily = Wyrm.Body,
        fontSize = 17.sp,
        color = Wyrm.Chevron,
    )
}

enum class RootTab { NOTIFICATIONS, SOCIAL, PLAY, SKIN, SETTINGS }

/**
 * Wyrm iOS's root tab bar, glyph for glyph: Alerts, Social, Play, Skin,
 * Settings over a floating capsule of glass. The chosen tab is semibold ink,
 * the rest take the theme's tab colour, and every glyph carries a faint paper
 * halo because clear glass shows whatever happens to scroll beneath it.
 */
@Composable
fun FloatingRootTabs(
    selected: RootTab,
    unreadNotifications: Int,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onExpand: () -> Unit = {},
) {
    val order = RootTab.entries
    val tabs = order.map { tab ->
        LiquidTab { chosen -> FloatingTabLabel(tab, chosen, if (tab == RootTab.NOTIFICATIONS) unreadNotifications else 0) }
    }
    LiquidTabBar(
        tabs = tabs,
        selected = order.indexOf(selected),
        onSelect = { onSelect(order[it]) },
        modifier = modifier,
        collapsed = collapsed,
        onExpand = onExpand,
    )
}

@Composable
private fun FloatingTabLabel(tab: RootTab, chosen: Boolean, badge: Int) {
    val colour by animateColorAsState(
        targetValue = if (chosen) Wyrm.Ink else Wyrm.TabIdle,
        animationSpec = tween(160),
        label = "tab colour",
    )
    val (glyph, label) = when (tab) {
        RootTab.NOTIFICATIONS -> IosGlyph.BELL_BADGE to "Alerts"
        RootTab.SOCIAL -> IosGlyph.PERSON_2 to "Social"
        RootTab.PLAY -> IosGlyph.PLAY_CIRCLE to "Play"
        RootTab.SKIN -> IosGlyph.HEXAGON_GRID to "Skin"
        RootTab.SETTINGS -> IosGlyph.SLIDERS to "Settings"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IosIcon(
                glyph = glyph,
                tint = colour,
                size = if (tab == RootTab.PLAY) 25.dp else 22.dp,
                semibold = chosen,
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 11.dp, y = (-7).dp)
                        .height(14.dp)
                        .widthIn(min = 16.dp)
                        .clip(WyrmCapsule)
                        .background(Wyrm.Live)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = minOf(badge, 99).toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        color = Wyrm.OnInk,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 8.5.sp,
            color = colour,
            maxLines = 1,
        )
    }
}

@Composable
fun RootTabs(
    selected: RootTab,
    insetBottom: Dp,
    unreadNotifications: Int,
    onNotifications: (Rect) -> Unit,
    onPlay: (Rect) -> Unit,
    onSocial: (Rect) -> Unit,
    onSkin: (Rect) -> Unit,
    onSettings: (Rect) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Wyrm.TabBar)
            .padding(top = 9.dp, bottom = insetBottom.coerceAtLeast(8.dp) + 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TabItem("Notifications", R.drawable.ic_tab_notifications, selected == RootTab.NOTIFICATIONS,
            badge = unreadNotifications, onClick = onNotifications)
        TabItem("Social", R.drawable.ic_tab_social, selected == RootTab.SOCIAL, onClick = onSocial)
        TabItem("Play", R.drawable.ic_tab_play_outline, selected == RootTab.PLAY,
            prominent = true, onClick = onPlay)
        TabItem("Skin", R.drawable.ic_tab_skin, selected == RootTab.SKIN, onClick = onSkin)
        TabItem("Settings", R.drawable.ic_tab_settings, selected == RootTab.SETTINGS, onClick = onSettings)
    }
}

@Composable
private fun RowScope.TabItem(
    label: String,
    icon: Int,
    selected: Boolean,
    prominent: Boolean = false,
    badge: Int = 0,
    onClick: (Rect) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val color by animateColorAsState(
        targetValue = if (selected) Wyrm.Ink else Wyrm.TabIdle,
        animationSpec = tween(200),
        label = "$label colour",
    )
    val markerWidth by animateDpAsState(
        targetValue = if (selected) 14.dp else 0.dp,
        animationSpec = tween(220),
        label = "$label marker",
    )
    val activeScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(220),
        label = "$label active",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .scale(activeScale * pressScale(pressed))
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !selected,
            ) { onClick(bounds) }
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(if (prominent) 25.dp else 22.dp),
                colorFilter = ColorFilter.tint(color),
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-4).dp)
                        .then(
                            if (badge == 1) Modifier.size(8.dp)
                            else Modifier.height(17.dp).widthIn(min = 17.dp)
                        )
                        .clip(wyrmRounded(9.dp))
                        .background(Wyrm.Live)
                        .then(if (badge > 1) Modifier.padding(horizontal = 4.dp) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (badge > 1) {
                        Text(
                            text = badge.toString(),
                            fontFamily = Wyrm.Body,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = Wyrm.contentOn(Wyrm.Live),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (label == "Notifications") 8.8.sp else 10.5.sp,
            color = color,
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .width(markerWidth)
                .height(2.dp)
                .clip(wyrmRounded(1.dp))
                .background(if (selected) Wyrm.Ink else Color.Transparent),
        )
    }
}

@Composable
fun BootingMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "boot")
    val fill by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fill",
    )
    WyrmMark(
        modifier = modifier,
        size = 96.dp,
        fill = fill,
        ink = Wyrm.Ink,
        unfilled = Wyrm.Line,
    )
}

/** First frame: paper field, ink W with a diagonal shine on the letter only. */
@Composable
fun PaperLaunchScreen() {
    val transition = rememberInfiniteTransition(label = "launch")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "launch-shimmer",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
        contentAlignment = Alignment.Center,
    ) {
        WyrmMarkShimmer(size = 96.dp, shimmer = shimmer)
    }
}

private fun grouped(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun grouped(value: Int): String = grouped(value.toLong())
