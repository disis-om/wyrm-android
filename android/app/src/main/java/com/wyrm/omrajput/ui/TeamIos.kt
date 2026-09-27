package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.ChatMessage
import com.wyrm.omrajput.data.TeamMember
import com.wyrm.omrajput.data.TeamMessage
import com.wyrm.omrajput.data.TeamProfile
import com.wyrm.omrajput.data.TeamState

/*
 * Team mode as Wyrm iOS draws it (`WyrmTeamDetail`): a status card, the live
 * roster with FPS, ping and leaderboard place, one "Open team chat" action, and
 * every team setting behind the gear at the top right. Team chat is the same
 * transcript and Liquid Glass composer as Global chat.
 */

private val tnum = TextStyle(fontFeatureSettings = "tnum")

@Composable
internal fun IosTeamOverview(
    enabled: Boolean,
    state: TeamState,
    teams: List<TeamProfile>,
    activeTeam: Int,
    myArena: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onSelectTeam: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpenChat: () -> Unit,
    onForget: () -> Unit,
    onJoinArena: (String) -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var pickingTeam by remember { mutableStateOf(false) }
    val paused = com.wyrm.omrajput.data.TeamService.NTL_SERVICES_DISABLED
    val running = enabled && teams.isNotEmpty() && !paused
    val current = teams.getOrNull(activeTeam)
    Box(Modifier.fillMaxSize()) {
        IosPageChrome("Team mode", insetTop, onBack) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (paused) {
                    IosSectionLabel("Paused")
                    IosPaperCard {
                        IosListRow(
                            title = "Team mode is paused",
                            detail = "NTL services are switched off in this build: nothing is sent to or received from NTL while arena drops are being fixed. Your saved teams stay on this phone.",
                            showsChevron = false,
                        )
                    }
                }
                IosSectionLabel("Team mode")
                IosPaperCard {
                    IosListRow(
                        title = if (running) current?.name?.ifBlank { "Team" } ?: "Team" else "No team connected",
                        detail = statusDetail(running, teams.isNotEmpty(), state),
                        value = when {
                            !running -> ""
                            state.connected -> "Live"
                            state.status.isNotBlank() && state.status != "Connected" -> "Offline"
                            else -> "Joining"
                        },
                        showsChevron = false,
                    )
                }
                if (running && state.members.isNotEmpty()) {
                    IosSectionLabel("Live roster")
                    IosPaperCard {
                        state.members.forEachIndexed { index, member ->
                            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 35.dp).height(1.dp).background(Wyrm.RowRule))
                            RosterRow(member, sameArena = member.arena.equals(myArena, ignoreCase = true)) {
                                if (member.playing) onJoinArena(member.arena)
                            }
                        }
                    }
                }
                if (!paused) Column(Modifier.padding(16.dp)) {
                    when {
                        running -> IosPrimaryAction("Open team chat", IosGlyph.BUBBLES, onClick = onOpenChat)
                        teams.isEmpty() -> IosPrimaryAction("Add a team", IosGlyph.PERSON_3, onClick = onAdd)
                        else -> IosPrimaryAction("Pick a saved team", IosGlyph.PERSON_3) { pickingTeam = true }
                    }
                }
                Spacer(Modifier.height(24.dp + insetBottom.coerceAtLeast(8.dp)))
            }
        }
        // Team settings live behind the gear, top right, as on iOS.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = insetTop + 8.dp, end = 14.dp)
                .size(36.dp)
                .clip(CircleShape)
                .liquidGlass(backdrop = LocalPageBackdrop.current, shape = CircleShape, tint = Wyrm.Card.copy(alpha = 0.5f))
                .border(1.dp, Wyrm.Rule, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settingsOpen = true },
            contentAlignment = Alignment.Center,
        ) { IosIcon(IosGlyph.GEAR, Wyrm.Ink, size = 18.dp) }

        if (settingsOpen) {
            val actions = buildList {
                teams.forEachIndexed { index, team ->
                    val mark = if (running && index == activeTeam) "✓  " else ""
                    add(IosSheetAction(mark + team.name.ifBlank { "Team ${index + 1}" }) {
                        onSelectTeam(index)
                        if (!enabled) onEnabled(true)
                    })
                }
                add(IosSheetAction("Add another team") { onAdd() })
                if (running) {
                    add(IosSheetAction("Disconnect team", destructive = true) { onEnabled(false) })
                    add(IosSheetAction("Remove this team", destructive = true) { onForget() })
                }
            }
            IosActionSheet(title = "Team settings", actions = actions, insetBottom = insetBottom) { settingsOpen = false }
        }
        if (pickingTeam) {
            IosActionSheet(
                title = "Saved teams",
                actions = teams.mapIndexed { index, team ->
                    IosSheetAction(team.name.ifBlank { "Team ${index + 1}" }) {
                        onSelectTeam(index)
                        onEnabled(true)
                    }
                },
                insetBottom = insetBottom,
            ) { pickingTeam = false }
        }
    }
}

private fun statusDetail(running: Boolean, hasTeams: Boolean, state: TeamState): String = when {
    !running -> if (hasTeams) "Pick a saved team from the gear." else "Use the Auth and Team ID from your NTL Team."
    state.connected -> "${state.members.size} members · NTL compatible"
    state.status.isNotBlank() && state.status != "Connected" -> state.status
    else -> "Joining your team"
}

/** Name, key owner and arena on the left; FPS, ping and leaderboard place on the right. */
@Composable
private fun RosterRow(member: TeamMember, sameArena: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (sameArena) Wyrm.Live else Wyrm.Quiet.copy(alpha = 0.3f)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Wyrm.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    member.owner.takeIf { it.isNotBlank() }?.let { "Key $it" },
                    if (member.playing) member.arena else "In menu",
                ).joinToString(" · "),
                fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = Wyrm.Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            member.fps?.let { Stat("$it fps") }
            member.ping?.let { Stat("$it ms") }
            if (member.rank > 0) Stat("LB #${member.rank}", strong = true)
        }
        if (member.playing) IosIcon(IosGlyph.ARROW_UP_RIGHT, Wyrm.Quiet, size = 13.dp)
    }
}

@Composable
private fun Stat(text: String, strong: Boolean = false) {
    Text(
        text,
        fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, style = tnum,
        color = if (strong) Wyrm.OnInk else Wyrm.Ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (strong) Wyrm.Ink else Wyrm.Well)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/** Team chat: Global chat's transcript and Liquid Glass composer, fixed to the bottom. */
@Composable
internal fun IosTeamChat(
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
    val items = remember(messages) {
        messages.mapIndexed { index, message ->
            ChatMessage(
                id = "team-$index-${message.from.hashCode()}-${message.text.hashCode()}",
                body = message.text,
                createdAt = "",
                authorId = message.from,
                authorName = message.from,
                authorUsername = "",
                authorAvatarUrl = "",
                authorAvatarKey = "",
            )
        }
    }
    IosPageChrome("Team chat", insetTop, onBack) {
        IosChatPage(
            messages = items,
            meId = myName,
            showsAuthors = true,
            emptyTitle = "No Team messages yet",
            emptyNote = "Messages from your connected NTL Team appear here.",
            error = "",
            draft = draft,
            placeholder = "Message the team",
            limit = 280,
            sending = sending,
            insetBottom = insetBottom,
            onDraftChange = onDraftChange,
            onSend = onSend,
        )
    }
}
