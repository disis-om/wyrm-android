package com.wyrm.omrajput.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Arena
import com.wyrm.omrajput.data.ArenaDirectory
import com.wyrm.omrajput.data.TeamMember

private val CardShape = wyrmRounded(14.dp)
private val FieldShape = wyrmRounded(12.dp)
private val CtaShape = wyrmRounded(11.dp)
private val nums = TextStyle(fontFeatureSettings = "tnum")
private const val NEAREST_ARENA_COUNT = 8

/** Spec 27 — Play › Pick a server. One search field for the directory and custom IPs. */
@Composable
fun ArenaSheetScreen(
    state: ArenaListState,
    saved: List<String>,
    teamMembers: List<TeamMember> = emptyList(),
    insetBottom: Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onSave: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val refusedNow = remember(state.refused) {
        val now = SystemClock.elapsedRealtime()
        state.refused.filterValues { it > now }.keys
    }
    val parsed = remember(query) { ArenaDirectory.extractEndpoint(query) }
    val sortedMatches = remember(state.arenas, state.pings, query, parsed) {
        state.arenas
            .filter { arena ->
                if (parsed != null) arena.endpoint.equals(parsed, ignoreCase = true)
                else ArenaDirectory.matches(arena, query)
            }
            .sortedWith(
                compareBy<Arena> { sheetPingRank(state.pings[it.endpoint]) }
                    .thenByDescending { it.players }
                    .thenBy { it.id },
            )
    }
    val visibleArenas = remember(sortedMatches, query, showAll) {
        if (query.isBlank() && !showAll) sortedMatches.take(NEAREST_ARENA_COUNT)
        else sortedMatches
    }
    val customCandidate = parsed?.takeIf { endpoint ->
        state.arenas.none { it.endpoint.equals(endpoint, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 9.dp)
                .size(width = 38.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Wyrm.Chevron),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = "Cancel",
                fontFamily = Wyrm.Body,
                fontSize = 16.sp,
                color = Wyrm.Link,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            Text(
                text = "Pick a server",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Wyrm.Ink,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = if (state.loading) "…" else "Refresh",
                fontFamily = Wyrm.Body,
                fontSize = 15.sp,
                color = if (state.loading) Wyrm.TabIdle else Wyrm.Link,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(enabled = !state.loading, onClick = onRefresh)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Wyrm.Rule),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = insetBottom + 24.dp),
        ) {
            item {
                Text(
                    text = "FIND AN ARENA",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.92.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                )
                Spacer(Modifier.height(8.dp))
                SearchField(value = query, onValueChange = { query = it })
                Text(
                    text = "Search by server, cluster or address. A pasted ip:port or invite can also become a custom arena.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 9.dp),
                )
            }

            if (saved.isNotEmpty()) {
                item {
                    SectionLabel("RECENTLY JOINED", top = 18.dp)
                    PaperGroup {
                        saved.forEachIndexed { index, endpoint ->
                            RecentRow(
                                endpoint = endpoint,
                                first = index == 0,
                                current = endpoint == state.selected,
                                known = state.arenas.firstOrNull { it.endpoint == endpoint },
                                onClick = {
                                    onSave(endpoint)
                                    onSelect(endpoint)
                                },
                                onRemove = { onRemoveSaved(endpoint) },
                            )
                        }
                    }
                }
            }

            if (customCandidate != null) {
                item {
                    CustomArenaPrompt(
                        endpoint = customCandidate,
                        onUse = {
                            onSave(customCandidate)
                            onSelect(customCandidate)
                        },
                    )
                }
            }

            item {
                SectionLabel(
                    text = when {
                        query.isNotBlank() -> "SEARCH RESULTS · ${visibleArenas.size}"
                        showAll -> "ALL ARENAS · ${sortedMatches.size}"
                        else -> "SERVERS NEAR YOU"
                    },
                    top = 22.dp,
                )
            }

            when {
                state.error.isNotEmpty() && state.arenas.isEmpty() ->
                    item { SheetNotice(state.error) }

                state.loading && state.arenas.isEmpty() ->
                    item { SheetNotice("Reading the arena directory…") }

                visibleArenas.isEmpty() && query.isNotBlank() && customCandidate == null ->
                    item {
                        SheetNotice(
                            "No listed arena matches “${query.trim()}”. Paste a complete ip:port to use a custom arena.",
                        )
                    }

                visibleArenas.isEmpty() && customCandidate != null -> Unit

                visibleArenas.isEmpty() ->
                    item { SheetNotice("No arenas in the live directory yet.") }

                else -> {
                    itemsIndexed(
                        items = visibleArenas,
                        key = { _, arena -> arena.endpoint },
                    ) { index, arena ->
                        ArenaListRow(
                            arena = arena,
                            ping = state.pings[arena.endpoint],
                            first = index == 0,
                            last = index == visibleArenas.lastIndex,
                            current = arena.endpoint == state.selected,
                            refused = refusedNow.contains(arena.endpoint),
                            teamHere = teamMembers.count { member ->
                                member.playing && member.arena.equals(arena.endpoint, ignoreCase = true)
                            },
                            onClick = {
                                onSave(arena.endpoint)
                                onSelect(arena.endpoint)
                            },
                        )
                    }
                    if (query.isBlank() && sortedMatches.size > NEAREST_ARENA_COUNT) {
                        item {
                            ShowAllButton(
                                showingAll = showAll,
                                total = sortedMatches.size,
                                onClick = { showAll = !showAll },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = FieldShape
    val ring = if (focused || value.isNotBlank()) Wyrm.Link else Wyrm.Rule
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.5.dp, ring, shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                letterSpacing = 0.3.sp,
                color = Wyrm.Ink,
            ),
            cursorBrush = SolidColor(Wyrm.Link),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            decorationBox = { input ->
                if (value.isEmpty()) {
                    Text(
                        text = "Server, cluster or ip:port",
                        fontFamily = Wyrm.Body,
                        fontSize = 16.sp,
                        color = Wyrm.Quiet,
                    )
                }
                input()
            },
        )
    }
}

@Composable
private fun CustomArenaPrompt(
    endpoint: String,
    onUse: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 18.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Wyrm.Link),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "CUSTOM ARENA?",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.88.sp,
                color = Wyrm.Link,
            )
        }
        Text(
            text = endpoint,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Wyrm.Ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "This address is not in the live directory. If it is a private or custom server, you can still select it.",
            fontFamily = Wyrm.Body,
            fontSize = 13.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(top = 3.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(44.dp)
                .scale(pressScale(pressed))
                .clip(CtaShape)
                .background(Wyrm.Ink)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onUse,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Use custom arena",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Wyrm.OnInk,
            )
        }
    }
}

@Composable
private fun PaperGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(CardShape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, CardShape),
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String, top: Dp) {
    Text(
        text = text,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.92.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = 8.dp),
    )
}

@Composable
private fun ArenaListRow(
    arena: Arena,
    ping: Int?,
    first: Boolean,
    last: Boolean,
    current: Boolean,
    refused: Boolean,
    teamHere: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) 14.dp else 0.dp,
        topEnd = if (first) 14.dp else 0.dp,
        bottomStart = if (last) 14.dp else 0.dp,
        bottomEnd = if (last) 14.dp else 0.dp,
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape),
    ) {
        NearbyRow(
            arena = arena,
            ping = ping,
            current = current,
            refused = refused,
            teamHere = teamHere,
            onClick = onClick,
        )
    }
}

@Composable
private fun NearbyRow(
    arena: Arena,
    ping: Int?,
    current: Boolean,
    refused: Boolean,
    teamHere: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .scale(pressScale(pressed))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = arena.paperTitle(),
                    fontFamily = Wyrm.Body,
                    fontSize = 15.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = arena.endpoint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Wyrm.Quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        ping == null -> "· · ·"
                        ping == ArenaDirectory.UNREACHABLE -> "—"
                        else -> "$ping ms"
                    },
                    fontFamily = Wyrm.Body,
                    fontSize = 14.sp,
                    color = Wyrm.Ink,
                    style = nums,
                )
                Text(
                    text = when {
                        current -> "Current"
                        refused -> "Refused recently"
                        teamHere > 0 -> "$teamHere team · ${arena.players} snakes"
                        else -> "${arena.players} snakes"
                    },
                    fontFamily = Wyrm.Body,
                    fontSize = 11.5.sp,
                    color = if (current) Wyrm.Live else Wyrm.Quiet,
                    style = nums,
                )
            }
            Text(
                text = "›",
                fontFamily = Wyrm.Body,
                fontSize = 17.sp,
                color = Wyrm.Chevron,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ShowAllButton(showingAll: Boolean, total: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(46.dp)
            .scale(pressScale(pressed))
            .clip(CtaShape)
            .border(1.dp, Wyrm.Rule, CtaShape)
            .background(Wyrm.Card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (showingAll) "Show nearest only" else "Show all $total arenas",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Wyrm.Link,
        )
    }
}

@Composable
private fun RecentRow(
    endpoint: String,
    first: Boolean,
    current: Boolean,
    known: Arena?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
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
                .height(54.dp)
                .scale(pressScale(pressed))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = endpoint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.5.sp,
                    color = Wyrm.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        current -> "Current"
                        known != null -> known.paperTitle()
                        else -> "Saved on this phone"
                    },
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = if (current) Wyrm.Live else Wyrm.Quiet,
                    modifier = Modifier.padding(top = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Remove",
                fontFamily = Wyrm.Body,
                fontSize = 13.sp,
                color = Wyrm.Quiet,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun SheetNotice(message: String) {
    Text(
        text = message,
        fontFamily = Wyrm.Body,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Wyrm.Quiet,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
    )
}

private fun Arena.paperTitle(): String =
    if (id >= 0) "Cluster $cluster · server $id" else endpoint

private fun sheetPingRank(ping: Int?): Int = when {
    ping == null -> 100_000
    ping == ArenaDirectory.UNREACHABLE -> 200_000
    else -> ping
}
