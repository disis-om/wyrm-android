package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
import com.wyrm.omrajput.R
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.VoiceParticipant
import com.wyrm.omrajput.data.VoiceRoom
import com.wyrm.omrajput.data.relativeTime
import com.wyrm.omrajput.voice.VoiceCallState
import com.wyrm.omrajput.voice.VoiceConnectionStage
import kotlinx.coroutines.delay
import java.time.Instant

enum class VoicePage { DIRECTORY, VERIFY_EMAIL, VERIFY_CODE, VERIFIED, CREATE_ROOM, ROOM, CALL }
enum class VoiceOperationKind { CREATE_ROOM, JOIN_ROOM, ACCEPT_INVITE }

data class VoiceOperationState(
    val id: String = "",
    val kind: VoiceOperationKind = VoiceOperationKind.CREATE_ROOM,
    val stage: Int = 0,
    val running: Boolean = false,
    val failure: String = "",
) {
    val stages: List<String> get() = when (kind) {
        // Three plain steps (OM, 2026-09-30: the seven technical ones were too long).
        VoiceOperationKind.CREATE_ROOM -> listOf("Creating your room", "Securing your room", "Your room is ready")
        VoiceOperationKind.JOIN_ROOM, VoiceOperationKind.ACCEPT_INVITE -> listOf("Joining room", "Connecting audio", "You're in")
    }
}

data class VoiceScreenState(
    val page: VoicePage = VoicePage.DIRECTORY,
    val verified: Boolean = false,
    val loading: Boolean = false,
    val rooms: List<VoiceRoom> = emptyList(),
    val selectedRoom: VoiceRoom? = null,
    val email: String = "",
    val challengeId: String = "",
    val resendAt: String = "",
    val roomPassword: String = "",
    val pendingInviteId: String = "",
    val error: String = "",
    val offline: Boolean = false,
    val updatedAt: Long = 0L,
    val operation: VoiceOperationState = VoiceOperationState(),
)

@Composable
fun VoiceChatScreen(
    state: VoiceScreenState,
    call: VoiceCallState,
    backLabel: String = "Social",
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRoom: (VoiceRoom) -> Unit,
    onStartVerification: (String) -> Unit,
    onConfirmVerification: (String) -> Unit,
    onResendVerification: () -> Unit,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String) -> Unit,
    onToggleMineGate: (VoiceRoom) -> Unit = {},
    onRevealPassword: () -> Unit,
    onRegeneratePassword: () -> Unit,
    onToggleGate: () -> Unit,
    onDeleteRoom: () -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
    onUnban: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onLeaveCall: () -> Unit,
    onVolume: (Float) -> Unit,
    onRoute: (String) -> Unit,
    onCancelOperation: () -> Unit,
    onRetryOperation: () -> Unit,
    onVerify: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        when (state.page) {
            VoicePage.DIRECTORY -> IosVoiceDirectory(
                verified = state.verified,
                rooms = state.rooms,
                refreshing = state.loading,
                insetTop = insetTop,
                insetBottom = insetBottom,
                onBack = onBack,
                onNew = onOpenCreate,
                onVerify = onVerify,
                onRefresh = onRefresh,
                onOpenRoom = onOpenRoom,
            )
            VoicePage.CREATE_ROOM -> PaperVoiceCreate(
                state = state,
                insetTop = insetTop,
                insetBottom = insetBottom,
                onCancel = onBack,
                onCreate = onCreateRoom,
            )
            else -> {
            Column(Modifier.fillMaxSize().background(Wyrm.Paper)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = insetTop)
                        .height(52.dp)
                        .padding(horizontal = 14.dp),
                ) {
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onBack),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        IosIcon(IosGlyph.CHEVRON_LEFT, Wyrm.Link, size = 15.dp, weight = 2.6f)
                        Text("Back", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Wyrm.Link)
                    }
                    Text(
                        text = when (state.page) {
                            VoicePage.VERIFY_EMAIL, VoicePage.VERIFY_CODE, VoicePage.VERIFIED -> "Voice verification"
                            VoicePage.ROOM -> "Voice room"
                            VoicePage.CALL -> "Voice"
                            else -> "Voice"
                        },
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Wyrm.Ink,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
                Box(Modifier.weight(1f).padding(bottom = insetBottom)) {
                val push52 = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.roundToPx() }
                val pop38 = with(androidx.compose.ui.platform.LocalDensity.current) { 38.dp.roundToPx() }
                AnimatedContent(
                    targetState = state.page,
                    transitionSpec = {
                        val verifying = setOf(VoicePage.VERIFY_EMAIL, VoicePage.VERIFY_CODE, VoicePage.VERIFIED)
                        if (targetState == VoicePage.CALL || initialState == VoicePage.CALL) {
                            fadeIn(tween(100)).togetherWith(fadeOut(tween(70)))
                        } else if (targetState in verifying && initialState in verifying) {
                            // Wyrm iOS: .wyrmCinematicPush on spring(0.5, 0.84).
                            (fadeIn(iosSpring(0.5f, 0.84f)) + slideInHorizontally(iosSpring(0.5f, 0.84f)) { push52 })
                                .togetherWith(fadeOut(iosSpring(0.5f, 0.84f)) + slideOutHorizontally(iosSpring(0.5f, 0.84f)) { pop38 })
                        } else {
                            (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn())
                                .togetherWith(slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it } + fadeOut())
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "voice-page",
                ) { page ->
                    when (page) {
                        VoicePage.DIRECTORY, VoicePage.CREATE_ROOM -> Unit
                        VoicePage.VERIFY_EMAIL -> VerifyEmail(state, onStartVerification)
                        VoicePage.VERIFY_CODE -> VerifyCode(state, onConfirmVerification, onResendVerification)
                        VoicePage.VERIFIED -> VerifiedVoice(onReturn = onBack)
                        VoicePage.ROOM -> RoomDetails(
                            state, onJoinRoom, onRevealPassword, onRegeneratePassword,
                            onToggleGate, onDeleteRoom, onUnban, onOpenProfile,
                        )
                        VoicePage.CALL -> CallRoom(
                            call, state.selectedRoom?.mine == true, onToggleMute, onToggleDeafen,
                            onLeaveCall, onVolume, onRoute, onKick, onBan, onOpenProfile,
                        )
                    }
                }
                }
            }
            }
        }
        AnimatedVisibility(
            visible = state.operation.running || state.operation.failure.isNotEmpty(),
            enter = fadeIn(tween(180)), exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize().zIndex(50f),
        ) {
            VoiceJoinSheet(
                title = if (state.operation.kind == VoiceOperationKind.CREATE_ROOM) "New voice room" else state.selectedRoom?.name ?: "Voice room",
                stages = state.operation.stages,
                stage = state.operation.stage,
                failure = state.operation.failure,
                bottom = insetBottom,
                onCancel = onCancelOperation,
                onRetry = onRetryOperation,
            )
        }
    }
}

@Composable
private fun VerifiedVoice(onReturn: () -> Unit) {
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
    Column(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = appear.value
            val scale = 0.9f + 0.1f * appear.value
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        IosIcon(IosGlyph.CHECKMARK_SEAL, Wyrm.Live, size = 70.dp)
        Text("Voice profile verified", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Wyrm.Ink)
        Text("You can now enter and create player voice rooms.", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet)
        Box(Modifier.padding(top = 12.dp).widthIn(max = 300.dp)) { IosPrimaryAction("Return to rooms", onClick = onReturn) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaperVoiceDirectory(
    state: VoiceScreenState,
    backLabel: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpenRoom: (VoiceRoom) -> Unit,
    onToggleGate: (VoiceRoom) -> Unit,
    onRefresh: () -> Unit,
) {
    var idleOpen by remember { mutableStateOf(false) }
    val mine = state.rooms.firstOrNull { it.mine }
    val publicRooms = state.rooms.filter { it.managedPublic }
    val live = state.rooms.filter { it.active && !it.mine && !it.managedPublic }
    val idle = state.rooms.filter { !it.active && !it.mine && !it.managedPublic }
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
                    text = "Voice rooms",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    text = if (state.verified) "New" else "Verify",
                    fontFamily = Wyrm.Body,
                    fontSize = 15.5.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(onClick = onNew)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.offline && state.updatedAt > 0L) {
                SettingsCaption("Offline · updated ${cacheAge(state.updatedAt)} ago · voice entry disabled")
            }
            if (state.error.isNotBlank()) {
                Text(
                    text = state.error,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Badge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                )
            }
            if (publicRooms.isNotEmpty()) {
                SettingsSectionLabel("Public rooms · direct entry", top = 18.dp)
                SettingsCard {
                    publicRooms.forEachIndexed { index, room ->
                        PaperRoomRow(room = room, first = index == 0, onOpen = { onOpenRoom(room) })
                    }
                }
                SettingsCaption("No room password. Login, microphone permission, capacity and moderation still apply.")
            }
            mine?.let { room ->
                Spacer(Modifier.height(16.dp))
                SettingsCard {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VoiceInitials(room.name, live = room.active)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (room.active) "Your room is open" else "Your room",
                                    fontFamily = Wyrm.Body,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Wyrm.Ink,
                                )
                                Text(
                                    text = if (state.verified) {
                                        "Voice identity verified · ${room.activeCount} of 10 inside"
                                    } else {
                                        "${room.activeCount} of 10 inside"
                                    },
                                    fontFamily = Wyrm.Body,
                                    fontSize = 12.5.sp,
                                    color = Wyrm.Quiet,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                PaperPrimaryButton(label = "Manage room", onClick = { onOpenRoom(room) })
                            }
                            Box(Modifier.weight(1f)) {
                                PaperOutlineButton(
                                    label = if (room.gate == "open") "Close gate" else "Open gate",
                                    onClick = { onToggleGate(room) },
                                )
                            }
                        }
                    }
                }
            }
            if (live.isNotEmpty()) {
                SettingsSectionLabel("Live now · ${live.size}", top = if (mine == null) 18.dp else 22.dp)
                SettingsCard {
                    live.forEachIndexed { index, room ->
                        PaperRoomRow(room = room, first = index == 0, onOpen = { onOpenRoom(room) })
                    }
                }
            } else if (mine == null && idle.isEmpty() && !state.loading) {
                SettingsCaption(
                    if (state.verified) "No rooms yet. New makes one."
                    else "Verify once to create and enter voice rooms.",
                )
            }
            if (idle.isNotEmpty()) {
                AdvancedFold(
                    label = "Idle rooms · ${idle.size}",
                    open = idleOpen,
                    onToggle = { idleOpen = !idleOpen },
                )
                if (idleOpen) {
                    SettingsCard {
                        idle.forEachIndexed { index, room ->
                            PaperRoomRow(room = room, first = index == 0, onOpen = { onOpenRoom(room) })
                        }
                    }
                }
                SettingsCaption("Empty rooms are folded away instead of filling the page with 0 / 10.")
            }
            if (state.loading && state.rooms.isEmpty()) {
                SettingsCaption("Loading rooms…")
            }
            Spacer(Modifier.height(24.dp + insetBottom.coerceAtLeast(8.dp)))
        }
        }
    }
}

@Composable
private fun PaperRoomRow(room: VoiceRoom, first: Boolean, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .scale(pressScale(pressed))
                .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (room.managedPublic) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WyrmMark(
                        size = 22.dp,
                        ink = Wyrm.Mute.copy(alpha = 0.42f),
                        unfilled = Color.Transparent,
                    )
                }
            } else {
                VoiceInitials(room.creator.displayName.ifBlank { room.name }, live = room.active)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = room.name,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (room.managedPublic) "Public · direct entry" else "by ${room.creator.displayName} · ${if (room.gate == "open") "open" else "key only"}",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (room.active) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Wyrm.Live),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = "${room.activeCount} / ${room.capacity}",
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                color = Wyrm.Mute,
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "›", fontFamily = Wyrm.Body, fontSize = 17.sp, color = Wyrm.Chevron)
        }
    }
}

@Composable
private fun VoiceInitials(name: String, live: Boolean) {
    val initials = name.filter { it.isLetter() }.take(2).uppercase().ifBlank { name.take(1).uppercase() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(wyrmRounded(10.dp))
            .background(if (live) Color(0xFFE6EFE8) else Wyrm.Well),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (live) Wyrm.Live else Wyrm.Mute,
        )
    }
}

@Composable
private fun PaperVoiceCreate(
    state: VoiceScreenState,
    insetTop: Dp,
    insetBottom: Dp,
    onCancel: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val ready = name.trim().length >= 3
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
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                Text(
                    text = "Cancel",
                    fontFamily = Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
                Text(
                    text = "New room",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    text = "Create",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    color = if (ready) Wyrm.Link else Wyrm.TabIdle,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(enabled = ready, onClick = { onCreate(name.trim()) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionLabel("Name", top = 20.dp)
            SettingsCard {
                Column(Modifier.padding(12.dp, 12.dp, 14.dp, 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(wyrmRounded(10.dp))
                            .background(Wyrm.Card)
                            .border(1.5.dp, if (name.isNotBlank()) Wyrm.Link else Wyrm.Rule, wyrmRounded(10.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = Wyrm.Body,
                                fontSize = 15.sp,
                                color = Wyrm.Ink,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Wyrm.Ink),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (name.isEmpty()) {
                                    Text("Night squeeze", fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.TabIdle)
                                }
                                inner()
                            },
                        )
                    }
                    Text(
                        text = "It stays in the public directory while the gate is open.",
                        fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
            if (state.error.isNotBlank()) {
                Text(
                    text = state.error,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Badge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
            }
            SettingsCaption(
                "Gate and room key are set after the room exists. Followers-only is not a voice gate — rooms are open or key-only.",
            )
            Spacer(Modifier.height(24.dp + bottom.coerceAtLeast(8.dp)))
        }
    }
}

/* Wyrm iOS's voice verification (WyrmVoiceVerificationDetail): a live-tinted
 * disc with the step's glyph, a 24 bold title, one quiet line, the field and
 * the action — each step pushed in the cinematic way. */
@Composable
private fun VerifyStage(
    glyph: IosGlyph,
    title: String,
    note: String,
    error: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(92.dp).clip(CircleShape).background(Wyrm.Live.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            IosIcon(glyph, Wyrm.Live, size = 44.dp, weight = 1.2f)
        }
        Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Wyrm.Ink, textAlign = TextAlign.Center)
        Text(note, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 34.dp))
        content()
        if (error.isNotEmpty()) {
            Text(error, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Color(0xFFFF3B30), modifier = Modifier.padding(horizontal = 24.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VerifyField(value: String, onValue: (String) -> Unit, placeholder: String, keyboardType: KeyboardType, code: Boolean) {
    val style = androidx.compose.ui.text.TextStyle(
        fontFamily = Wyrm.Body,
        fontWeight = if (code) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (code) 24.sp else 15.sp,
        color = Wyrm.Ink,
        textAlign = if (code) TextAlign.Center else TextAlign.Start,
    )
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = style,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Wyrm.Link),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(if (code) 56.dp else 52.dp)
            .clip(wyrmRounded(14.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
            .padding(horizontal = 15.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = if (code) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Wyrm.Quiet.copy(alpha = 0.6f)))
                inner()
            }
        },
    )
}

@Composable
private fun VerifyEmail(state: VoiceScreenState, onSubmit: (String) -> Unit) {
    var email by remember(state.email) { mutableStateOf(state.email) }
    VerifyStage(
        glyph = IosGlyph.ENVELOPE_SHIELD,
        title = "Verify your email",
        note = "Wyrm sends one private code. Your email is protected by the voice control plane.",
        error = state.error,
    ) {
        VerifyField(email, { email = it.take(254).trim() }, "name@example.com", KeyboardType.Email, code = false)
        Box(Modifier.padding(horizontal = 24.dp)) {
            IosPrimaryAction(if (state.loading) "Sending…" else "Send code", enabled = !state.loading && email.contains('@')) { onSubmit(email.trim()) }
        }
    }
}

@Composable
private fun VerifyCode(state: VoiceScreenState, onConfirm: (String) -> Unit, onResend: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var remaining by remember(state.resendAt) { mutableIntStateOf(secondsUntil(state.resendAt)) }
    LaunchedEffect(state.resendAt) {
        while (remaining > 0) { delay(1_000); remaining = secondsUntil(state.resendAt) }
    }
    VerifyStage(
        glyph = IosGlyph.NUMBER_SQUARE,
        title = "Enter the six-digit code",
        note = "The code expires shortly. You can resend it without restarting this flow.",
        error = state.error,
    ) {
        VerifyField(code, { code = it.filter(Char::isDigit).take(6) }, "000000", KeyboardType.Number, code = true)
        Box(Modifier.padding(horizontal = 24.dp)) {
            IosPrimaryAction(if (state.loading) "Checking…" else "Verify", enabled = !state.loading && code.length == 6) { onConfirm(code) }
        }
        Text(
            "Resend code",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = if (remaining > 0) Wyrm.Quiet else Wyrm.Link,
            modifier = Modifier.clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { if (remaining == 0) onResend() },
        )
    }
}

/**
 * A room before joining (OM, 2026-09-30): the room's art, name and maker, a
 * live line, three facts, then one action. A private room asks for its code
 * in eight boxes and says how to get one; the owner manages the code, the
 * gate and bans below.
 */
@Composable
private fun RoomDetails(
    state: VoiceScreenState,
    onJoin: (String) -> Unit,
    onReveal: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleGate: () -> Unit,
    onDelete: () -> Unit,
    onUnban: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val room = state.selectedRoom ?: return
    var code by remember(room.id) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // A member's code can go stale when the owner changes it; the error brings the boxes back.
    val needsCode = !room.managedPublic && !room.mine && (!room.member || state.error.isNotBlank())
    val canJoin = !state.offline && (!needsCode || code.length == 8)
    val join = {
        if (canJoin) {
            // A room code is not an account password. Releasing focus first also
            // keeps an OEM password-save window from sitting over the call.
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            onJoin(code)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Spacer(Modifier.height(26.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VoiceRoomArt(room, 88.dp)
            Spacer(Modifier.height(14.dp))
            Text(room.name, fontFamily = Wyrm.Display, fontSize = 30.sp, lineHeight = 34.sp, color = Wyrm.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            if (room.managedPublic) {
                Text("Official Wyrm room", fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
            } else {
                Row(
                    Modifier.clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenProfile(room.creator.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    WyrmAvatar(room.creator.avatarUrl, room.creator.avatarKey, room.creator.displayName, 20.dp, corner = 10.dp)
                    Text("by ${room.creator.displayName}", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Wyrm.Mute)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(CircleShape).background(if (room.active) Wyrm.Live.copy(alpha = 0.13f) else Wyrm.Well)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                VoiceLiveBars(if (room.active) Wyrm.Live else Wyrm.Quiet, playing = room.active, height = 11.dp)
                Text(
                    when {
                        room.active -> "Live · ${room.activeCount} of ${room.capacity} inside" +
                            if (room.activeSince.isNotBlank()) " · since ${relativeTime(room.activeSince)}" else ""
                        room.gate != "open" && !room.managedPublic -> "Closed right now"
                        else -> "Quiet · be the first one in"
                    },
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                    color = if (room.active) Wyrm.Live else Wyrm.Mute,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        VoiceFacts(room)
        Spacer(Modifier.height(24.dp))
        if (needsCode) {
            Box(Modifier.padding(horizontal = 20.dp)) {
                VoiceRoomCodeField(
                    code = code,
                    onCode = { code = it },
                    creator = room.creator.displayName,
                    error = state.error,
                    onAskCreator = { onOpenProfile(room.creator.id) },
                    onDone = join,
                )
            }
            Spacer(Modifier.height(20.dp))
        } else if (state.error.isNotBlank()) {
            Text(state.error, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Blood, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
        }
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(56.dp).clip(CircleShape)
                .background(if (canJoin) Wyrm.Live else Wyrm.Well)
                .clickable(enabled = canJoin, interactionSource = remember { MutableInteractionSource() }, indication = null) { join() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(LucideR.drawable.lucide_ic_mic), null, Modifier.size(19.dp), tint = if (canJoin) Color.White else Wyrm.Quiet)
            Spacer(Modifier.width(9.dp))
            Text(
                when {
                    state.offline -> "Reconnect to join"
                    room.mine -> if (room.active) "Join your room" else "Start your room"
                    else -> "Join room"
                },
                fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (canJoin) Color.White else Wyrm.Quiet,
            )
        }
        Text(
            if (room.managedPublic) "You join muted. Tap the mic when you want to talk." else "You join muted. The creator can remove people.",
            fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        )
        if (room.mine) {
            VoiceSectionTitle("Manage your room")
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(wyrmRounded(20.dp)).background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Room code", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Wyrm.Quiet)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.roomPassword.ifBlank { "••••••••" }, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 3.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f))
                    if (state.roomPassword.isNotBlank()) {
                        Icon(
                            painterResource(LucideR.drawable.lucide_ic_copy), "Copy room code",
                            Modifier.size(38.dp).clip(CircleShape).clickable { clipboard.setText(AnnotatedString(state.roomPassword)) }.padding(9.dp),
                            tint = Wyrm.Ink,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    WyrmPill(if (state.roomPassword.isBlank()) "Show" else "New code", onClick = if (state.roomPassword.isBlank()) onReveal else onRegenerate)
                }
                Text("Share this code with friends. A new code keeps out anyone who had the old one.",
                    fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (room.gate == "open") "Open for entries" else "Closed", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = Wyrm.Ink)
                        Text(if (room.gate == "open") "People with the code can join." else "No one new can join until you open it.",
                            fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet)
                    }
                    WyrmPill(if (room.gate == "open") "Close" else "Open", onClick = onToggleGate)
                }
            }
            if (room.bans.isNotEmpty()) {
                VoiceSectionTitle("Removed for good", "${room.bans.size}")
                room.bans.forEach { ban ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ban.displayName, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f))
                        WyrmPill("Let back in", onClick = { onUnban(ban.playerId) })
                    }
                }
            }
            Text("Delete room", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Wyrm.Blood,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp).clickable(onClick = onDelete))
        }
        if (room.history.isNotEmpty()) {
            VoiceSectionTitle("Earlier calls")
            room.history.take(20).forEach { call ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(relativeTime(call.startedAt), fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Ink, modifier = Modifier.weight(1f))
                    Text("${call.peakParticipants} at most", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun cacheAge(savedAt: Long): String {
    val minutes = ((System.currentTimeMillis() - savedAt).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} hr"
        else -> "${minutes / (24 * 60)} d"
    }
}

/**
 * In the call (OM, 2026-09-30): the room and a live line on top, everyone as
 * a round avatar with a ring while they speak, and a dock of big round
 * controls at the bottom (mic, sound, speaker, more, leave). The owner taps a
 * person for Profile / Remove / Remove for good.
 */
@Composable
private fun CallRoom(
    call: VoiceCallState,
    owner: Boolean,
    onMute: () -> Unit,
    onDeafen: () -> Unit,
    onLeave: () -> Unit,
    onVolume: (Float) -> Unit,
    onRoute: (String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<VoiceParticipant?>(null) }
    val connected = call.stage == VoiceConnectionStage.CONNECTED
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    VoiceLiveBars(if (call.error.isBlank()) Wyrm.Live else Wyrm.Blood, playing = connected, height = 12.dp)
                    Text(
                        if (call.error.isNotBlank()) call.stage.label else if (connected) "LIVE" else call.stage.label.uppercase(),
                        fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp,
                        color = if (call.error.isBlank()) Wyrm.Live else Wyrm.Blood,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(call.roomName, fontFamily = Wyrm.Display, fontSize = 30.sp, lineHeight = 34.sp, color = Wyrm.Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${call.participants.size} of 10 in the room", fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                gridItems(call.participants, key = VoiceParticipant::id) { participant ->
                    val you = participant.playerId == call.playerId
                    VoiceParticipantTile(
                        participant = participant,
                        speaking = participant.playerId in call.speakingPlayerIds,
                        you = you,
                        onTap = { if (owner && !you) chosen = participant else onOpenProfile(participant.playerId) },
                    )
                }
            }
            AnimatedVisibility(more) {
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().clip(wyrmRounded(18.dp))
                        .background(Wyrm.Card).border(1.dp, Wyrm.Rule, wyrmRounded(18.dp)).padding(16.dp),
                ) {
                    Text("Call volume", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Wyrm.Quiet)
                    LiquidSlider(value = call.volume, onValueChange = onVolume)
                }
            }
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp).fillMaxWidth().clip(wyrmRounded(30.dp))
                    .background(Wyrm.Card).border(1.dp, Wyrm.Rule, wyrmRounded(30.dp)).padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val micOff = call.muted || call.interrupted
                VoiceDockButton(if (micOff) LucideR.drawable.lucide_ic_mic_off else LucideR.drawable.lucide_ic_mic,
                    if (micOff) "Unmute" else "Mute", on = !micOff, onClick = onMute)
                VoiceDockButton(if (call.deafened) LucideR.drawable.lucide_ic_headphone_off else LucideR.drawable.lucide_ic_headphones,
                    if (call.deafened) "Sound off" else "Sound", on = false, onClick = onDeafen)
                VoiceDockButton(if (call.audioRoute == "earpiece") LucideR.drawable.lucide_ic_ear else LucideR.drawable.lucide_ic_speaker,
                    if (call.audioRoute == "earpiece") "Earpiece" else "Speaker", on = false) {
                    onRoute(if (call.audioRoute == "earpiece") "speaker" else "earpiece")
                }
                VoiceDockButton(LucideR.drawable.lucide_ic_sliders_horizontal, "More", on = more) { more = !more }
                VoiceDockButton(LucideR.drawable.lucide_ic_phone_off, "Leave", on = false, danger = true, onClick = onLeave)
            }
        }
        // The owner's choices for one person.
        chosen?.let { person ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { chosen = null },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.padding(12.dp).fillMaxWidth().clip(wyrmRounded(24.dp)).background(Wyrm.Card)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(24.dp)).padding(vertical = 8.dp),
                ) {
                    Text(person.displayName, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    listOf(
                        Triple("View profile", Wyrm.Ink) { onOpenProfile(person.playerId) },
                        Triple("Remove from call", Wyrm.Ink) { onKick(person.playerId) },
                        Triple("Remove for good", Wyrm.Blood) { onBan(person.playerId) },
                    ).forEach { (label, colour, action) ->
                        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = colour,
                            modifier = Modifier.fillMaxWidth().clickable { chosen = null; action() }.padding(horizontal = 18.dp, vertical = 13.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Wyrm.Gutter).padding(top = Wyrm.GapLarge), content = content)
}

private fun secondsUntil(iso: String): Int = runCatching {
    ((Instant.parse(iso).toEpochMilli() - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
}.getOrDefault(0)
