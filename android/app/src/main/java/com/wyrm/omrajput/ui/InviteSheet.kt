package com.wyrm.omrajput.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Arena
import com.wyrm.omrajput.data.ArenaDirectory

/**
 * Bringing a follower into your own match.
 *
 * Drawn over whatever screen asked for it, the same way [UpdatePrompt] is —
 * an invite is not a place in the app either, just a card that appears and a
 * decision that closes it. The server list is the same directory the Arena
 * picker searches, because that is what "which server" already means here.
 */
@Composable
fun InviteSheet(
    recipientName: String,
    state: ArenaListState,
    query: String,
    note: String,
    selected: String,
    sending: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onQueryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val springing by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f),
        label = "entrance",
    )
    val entrance = springing.coerceIn(0f, 1f)

    val visible = remember(state.arenas, query) {
        state.arenas.filter { ArenaDirectory.matches(it, query) }.take(40)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f * entrance))
            .padding(top = insetTop, bottom = insetBottom)
            .padding(horizontal = Wyrm.Gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .scale(0.94f + 0.06f * springing)
                .alpha(entrance)
                .clip(wyrmRounded(Wyrm.CornerLarge))
                .background(Wyrm.Black.copy(alpha = 0.86f))
                .background(glassFill(1.4f))
                .border(1.dp, glassEdge(1.4f), wyrmRounded(Wyrm.CornerLarge))
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    WyrmLabel("Invite")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Bring $recipientName in",
                        fontFamily = Wyrm.Display,
                        fontSize = 24.sp,
                        color = Wyrm.White,
                    )
                }
                Text(
                    text = "✕",
                    fontSize = 15.sp,
                    color = Wyrm.Grey,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            WyrmSearchField(
                value = query,
                placeholder = "Search a server",
                onValueChange = onQueryChange,
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    state.loading && state.arenas.isEmpty() -> ListNotice("Reading the arena directory…")
                    visible.isEmpty() -> ListNotice("No server matches “$query”.")
                    else -> visible.forEach { arena ->
                        ServerRow(
                            arena = arena,
                            selected = arena.endpoint == selected,
                            onClick = { onSelect(arena.endpoint) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            WyrmLabel("Message (optional)")
            Spacer(Modifier.height(8.dp))
            WyrmWell(modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = note,
                    onValueChange = { onNoteChange(it.take(140)) },
                    textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.White),
                    cursorBrush = SolidColor(Wyrm.Green),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (note.isEmpty()) {
                            Text(
                                text = "$recipientName has invited you to join them on this server",
                                fontFamily = Wyrm.Body,
                                fontSize = 13.sp,
                                color = Wyrm.Faint,
                            )
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            WyrmPrimaryAction(
                label = if (sending) "Sending…" else "Send Invite",
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selected.isNotEmpty() && !sending,
                onClick = onSend,
            )
        }
    }
}

@Composable
private fun ServerRow(arena: Arena, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(wyrmRounded(Wyrm.CornerSmall))
            .then(if (selected) Modifier.background(glassFill(1.6f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(wyrmRounded(999.dp))
                .background(if (selected) Wyrm.Green else Wyrm.Line)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = arena.endpoint,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Wyrm.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${arena.players} playing",
            fontFamily = Wyrm.Body,
            fontSize = 10.sp,
            color = Wyrm.Faint,
        )
    }
}

@Composable
private fun ListNotice(message: String) {
    Text(
        text = message,
        fontFamily = Wyrm.Body,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = Wyrm.Faint,
        modifier = Modifier.padding(vertical = 18.dp),
    )
}
