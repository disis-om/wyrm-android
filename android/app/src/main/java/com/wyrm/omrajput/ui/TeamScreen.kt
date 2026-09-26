package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.TeamMember
import com.wyrm.omrajput.data.TeamMessage
import com.wyrm.omrajput.data.TeamProfile
import com.wyrm.omrajput.data.TeamState
import java.text.NumberFormat
import java.util.Locale

enum class TeamTab(val label: String) { TEAM("Team"), CHAT("Chat") }

private val nums = TextStyle(fontFeatureSettings = "tnum")

/**
 * Spec pages 17–19 — Team mode roster, chat, and connect.
 *
 * Roster is one compact Join on the live rows, not a stacked button under each
 * name. Chat is its own page with ‹ Team. Connect is the empty / add form.
 * No invented ping — the service does not send one.
 */
@Composable
fun TeamScreen(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    state: TeamState,
    tab: TeamTab,
    messages: List<TeamMessage>,
    myArena: String,
    myName: String,
    teams: List<TeamProfile>,
    activeTeam: Int,
    adding: Boolean,
    draft: String,
    sending: Boolean,
    backLabel: String = "Play",
    insetTop: Dp,
    insetBottom: Dp,
    onTabChange: (TeamTab) -> Unit,
    onBack: () -> Unit,
    onSelectTeam: (Int) -> Unit,
    onAddTeam: (String, String, String) -> Unit,
    onStartAdding: (Boolean) -> Unit,
    onForget: () -> Unit,
    onJoinArena: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val bottom = keyboardRoom(insetBottom)
    when {
        enabled && (teams.isEmpty() || adding) -> TeamConnectPage(
            status = state.status,
            canCancel = teams.isNotEmpty(),
            insetTop = insetTop,
            insetBottom = bottom,
            onCancel = { if (teams.isNotEmpty()) onStartAdding(false) else onBack() },
            onSave = onAddTeam,
        )
        enabled && tab == TeamTab.CHAT -> TeamChatPage(
            teamName = teams.getOrNull(activeTeam)?.name.orEmpty().ifBlank { "Team" },
            members = state.members,
            messages = messages,
            myName = myName,
            draft = draft,
            sending = sending,
            insetTop = insetTop,
            insetBottom = bottom,
            onBack = { onTabChange(TeamTab.TEAM) },
            onDraftChange = onDraftChange,
            onSend = onSend,
        )
        else -> TeamRosterPage(
            enabled = enabled,
            onEnabled = onEnabled,
            state = state,
            teams = teams,
            activeTeam = activeTeam,
            myArena = myArena,
            backLabel = backLabel,
            insetTop = insetTop,
            insetBottom = insetBottom,
            onBack = onBack,
            onSelectTeam = onSelectTeam,
            onAdd = { onStartAdding(true) },
            onOpenChat = { onTabChange(TeamTab.CHAT) },
            onForget = onForget,
            onJoinArena = onJoinArena,
        )
    }
}

@Composable
private fun TeamRosterPage(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    state: TeamState,
    teams: List<TeamProfile>,
    activeTeam: Int,
    myArena: String,
    backLabel: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onSelectTeam: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpenChat: () -> Unit,
    onForget: () -> Unit,
    onJoinArena: (String) -> Unit,
) {
    val playing = state.members.filter { it.playing }
    val idle = state.members.filter { !it.playing }
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
                    text = "Team mode",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    InkSwitch(on = enabled, onToggle = onEnabled)
                }
            }
            if (enabled && teams.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    teams.forEachIndexed { index, profile ->
                        TeamPill(
                            label = profile.name.ifBlank { "Team" },
                            selected = index == activeTeam,
                            onClick = { onSelectTeam(index) },
                        )
                    }
                    TeamPill(label = "＋ Add team", selected = false, onClick = onAdd)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (state.connected) Wyrm.Live else Wyrm.Chevron),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.connected) "Connected" else state.status.ifBlank { "Connecting" },
                            fontFamily = Wyrm.Body,
                            fontSize = 12.5.sp,
                            color = if (state.connected) Wyrm.Live else if (state.status.isNotBlank()) Wyrm.Badge else Wyrm.Quiet,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Wyrm.Rule),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!enabled) {
                SettingsCaption("Team Mode is off. No roster, no team chat, and nothing is being asked of the team service.")
            } else if (state.members.isEmpty()) {
                SettingsCaption(
                    if (state.connected) "Nobody from the team is online."
                    else "Positions and scores refresh every four seconds — that is as fast as the team service allows.",
                )
            } else {
                if (playing.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "IN AN ARENA · ${playing.size}",
                            fontFamily = Wyrm.Body,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.92.sp,
                            color = Wyrm.Quiet,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "refreshes every 4s",
                            fontFamily = Wyrm.Body,
                            fontSize = 12.sp,
                            color = Wyrm.Quiet,
                        )
                    }
                    SettingsCard {
                        playing.forEachIndexed { index, member ->
                            MemberLine(
                                member = member,
                                sameArena = member.arena.equals(myArena, ignoreCase = true),
                                first = index == 0,
                                onJoin = { onJoinArena(member.arena) },
                            )
                        }
                    }
                }
                if (idle.isNotEmpty()) {
                    SettingsSectionLabel("In the menu · ${idle.size}")
                    SettingsCard {
                        idle.forEachIndexed { index, member ->
                            MemberLine(
                                member = member,
                                sameArena = false,
                                first = index == 0,
                                onJoin = {},
                            )
                        }
                    }
                }
            }
            if (enabled && teams.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
                    PaperOutlineButton(label = "Open team chat", onClick = onOpenChat)
                    Spacer(Modifier.height(9.dp))
                    PaperDangerButton(label = "Remove this team", onClick = onForget)
                }
                SettingsCaption("Positions and scores refresh every four seconds — that is as fast as the team service allows.")
            }
            Spacer(Modifier.height(24.dp + insetBottom.coerceAtLeast(8.dp)))
        }
    }
}

@Composable
private fun MemberLine(
    member: TeamMember,
    sameArena: Boolean,
    first: Boolean,
    onJoin: () -> Unit,
) {
    val score = NumberFormat.getIntegerInstance(Locale.US).format(member.score)
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (member.playing) 64.dp else 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (member.playing) Wyrm.Live else Wyrm.Chevron),
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = member.name,
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        !member.playing -> "Idle in the lobby"
                        sameArena -> "In your arena"
                        else -> member.arena
                    },
                    fontFamily = if (member.playing && !sameArena) FontFamily.Monospace else Wyrm.Body,
                    fontSize = 12.sp,
                    color = if (sameArena) Wyrm.Live else Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (member.bot) {
                    Text(
                        text = "Botting",
                        fontFamily = Wyrm.Body,
                        fontSize = 11.5.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                text = score,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (member.playing) Wyrm.Ink else Wyrm.Quiet,
                style = nums,
            )
            if (member.playing && !sameArena) {
                Spacer(Modifier.width(10.dp))
                JoinChip(onClick = onJoin)
            }
        }
    }
}

@Composable
private fun JoinChip(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .height(32.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(9.dp))
            .background(Wyrm.Ink)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Join",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Wyrm.OnInk,
        )
    }
}

@Composable
private fun TeamPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        selected -> Wyrm.Ink
        else -> Color.Transparent
    }
    val stroke = when {
        selected -> Color.Transparent
        else -> Wyrm.Rule
    }
    Text(
        text = label,
        fontFamily = Wyrm.Body,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 13.sp,
        color = if (selected) Wyrm.OnInk else Wyrm.Mute,
        modifier = Modifier
            .scale(pressScale(pressed))
            .clip(wyrmRounded(99.dp))
            .background(fill)
            .border(1.dp, stroke, wyrmRounded(99.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun PaperDangerButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .scale(pressScale(pressed))
            .clip(wyrmRounded(12.dp))
            .border(1.dp, Wyrm.Rule, wyrmRounded(12.dp))
            .background(if (pressed) Wyrm.Badge.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontSize = 15.sp,
            color = Wyrm.Badge,
        )
    }
}

@Composable
private fun TeamChatPage(
    teamName: String,
    members: List<TeamMember>,
    messages: List<TeamMessage>,
    myName: String,
    draft: String,
    sending: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val playing = members.count { it.playing }
    val idle = members.size - playing
    val faces = members.take(3)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Text(
                    text = "‹ Team",
                    fontFamily = Wyrm.Body,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
                Text(
                    text = "$teamName · team chat",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 72.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(
                        24.dp + 17.dp * (faces.size - 1).coerceAtLeast(0),
                    ),
                ) {
                    faces.forEachIndexed { index, member ->
                        InitialsWell(
                            name = member.name,
                            playing = member.playing,
                            modifier = Modifier
                                .padding(start = 17.dp * index)
                                .zIndex(index.toFloat()),
                        )
                    }
                }
                if (members.isNotEmpty()) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = when {
                            playing > 0 && idle > 0 -> "$playing in an arena · $idle idle"
                            playing > 0 -> "$playing in an arena"
                            else -> "$idle idle"
                        },
                        fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp,
                        color = Wyrm.Quiet,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Wyrm.Rule),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No team messages yet.",
                        fontFamily = Wyrm.Body,
                        fontSize = 14.sp,
                        color = Wyrm.Quiet,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.height(18.dp)) }
                    items(messages.asReversed()) { message ->
                        ChatBubble(message = message, mine = isMine(message, myName))
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        PaperComposer(
            draft = draft,
            sending = sending,
            placeholder = "Message the team",
            insetBottom = insetBottom,
            onDraftChange = onDraftChange,
            onSend = onSend,
        )
    }
}

private fun isMine(message: TeamMessage, myName: String): Boolean {
    if (message.from.equals("TEAM", ignoreCase = true)) return false
    val mine = myName.trim()
    return mine.isNotBlank() && message.from.equals(mine, ignoreCase = true)
}

@Composable
private fun ChatBubble(message: TeamMessage, mine: Boolean) {
    if (message.from.equals("TEAM", ignoreCase = true)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = message.text,
                fontFamily = Wyrm.Body,
                fontSize = 12.sp,
                color = Wyrm.Quiet,
                modifier = Modifier
                    .clip(wyrmRounded(99.dp))
                    .background(Wyrm.Hover)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            InitialsWell(name = message.from, playing = false)
            Spacer(Modifier.width(9.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (mine) 14.dp else 5.dp,
                        bottomEnd = if (mine) 5.dp else 14.dp,
                    ),
                )
                .background(if (mine) Wyrm.Ink else Wyrm.Card)
                .then(
                    if (mine) Modifier
                    else Modifier.border(
                        1.dp,
                        Wyrm.Rule,
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = 5.dp,
                            bottomEnd = 14.dp,
                        ),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (!mine) {
                Text(
                    text = message.from,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                text = message.text,
                fontFamily = Wyrm.Body,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = if (mine) Wyrm.OnInk else Wyrm.Ink,
            )
        }
    }
}

@Composable
private fun InitialsWell(name: String, playing: Boolean, modifier: Modifier = Modifier) {
    val initials = name.filter { it.isLetter() }.take(2).uppercase().ifBlank { "·" }
    val fill = if (playing) Color(0xFFE6EFE8) else Wyrm.Well
    val ink = if (playing) Wyrm.Live else Wyrm.Mute
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(wyrmRounded(8.dp))
            .background(fill)
            .border(2.dp, Wyrm.Paper, wyrmRounded(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = ink,
        )
    }
}

@Composable
private fun PaperComposer(
    draft: String,
    sending: Boolean,
    placeholder: String,
    insetBottom: Dp,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = draft.isNotBlank() && !sending
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Wyrm.Paper.copy(alpha = 0.94f))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = insetBottom.coerceAtLeast(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .clip(wyrmRounded(11.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(11.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(280)) },
                enabled = !sending,
                textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink),
                cursorBrush = SolidColor(Wyrm.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = Wyrm.Body,
                            fontSize = 15.sp,
                            color = Wyrm.TabIdle,
                        )
                    }
                    inner()
                },
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(wyrmRounded(11.dp))
                .background(if (canSend) Wyrm.Ink else Wyrm.Ink.copy(alpha = 0.35f))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "→",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Wyrm.OnInk,
            )
        }
    }
}

@Composable
private fun TeamConnectPage(
    status: String,
    canCancel: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onCancel: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var teamId by remember { mutableStateOf("") }
    var authKey by remember { mutableStateOf("") }
    val ready = teamId.trim().length >= 16 && authKey.trim().length >= 16
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
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
                    text = "Add a team",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Wyrm.Rule),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                Text(
                    text = "Paste what the team service gave you",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.4).sp,
                    color = Wyrm.Ink,
                )
                Text(
                    text = "Team mode runs on the NTL service. The ID and key are kept on this phone only — they are never sent to Wyrm.",
                    fontFamily = Wyrm.Body,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Wyrm.Mute,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            SettingsSectionLabel("Team")
            SettingsCard {
                ConnectField(
                    caption = "Name it — only you see this",
                    value = name,
                    onValueChange = { name = it.take(24) },
                    first = true,
                )
                ConnectField(
                    caption = "Team ID · at least 16 characters",
                    value = teamId,
                    onValueChange = { teamId = it.trim() },
                    first = false,
                    mono = true,
                )
                ConnectField(
                    caption = "Auth key · at least 16 characters",
                    value = authKey,
                    onValueChange = { authKey = it.trim() },
                    first = false,
                    mono = true,
                    secret = true,
                    valid = authKey.trim().length >= 16,
                )
            }
            if (status.isNotBlank()) {
                Text(
                    text = status,
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    color = Wyrm.Badge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
            }
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
                PaperPrimaryButton(
                    label = "Connect",
                    enabled = ready,
                    onClick = { onSave(name.trim(), teamId.trim(), authKey.trim()) },
                )
            }
            SettingsCaption("No team yet? A team ID comes from whoever runs your squad's NTL server.")
            if (canCancel) {
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp + insetBottom.coerceAtLeast(8.dp)))
        }
    }
}

@Composable
private fun ConnectField(
    caption: String,
    value: String,
    onValueChange: (String) -> Unit,
    first: Boolean,
    mono: Boolean = false,
    secret: Boolean = false,
    valid: Boolean = false,
) {
    Column {
        if (!first) SettingsHairline()
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(text = caption, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet)
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(wyrmRounded(10.dp))
                    .background(Wyrm.Card)
                    .border(
                        if (valid) 1.5.dp else 1.dp,
                        if (valid) Wyrm.Link else Wyrm.Rule,
                        wyrmRounded(10.dp),
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = if (mono) FontFamily.Monospace else Wyrm.Body,
                        fontSize = 15.sp,
                        color = Wyrm.Ink,
                    ),
                    cursorBrush = SolidColor(Wyrm.Ink),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (valid) {
                Text(
                    text = "Looks valid.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Live,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
