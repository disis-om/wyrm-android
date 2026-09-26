package com.wyrm.omrajput.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Arena
import com.wyrm.omrajput.data.ArenaDirectory

/** How the arenas are ordered. Ping first, because that is what a run feels like. */
enum class ArenaSort(val label: String) {
    PING("Ping"),
    PLAYERS("Players"),
    CLUSTER("Cluster"),
}

/** Everything the picker needs to draw itself, owned by the overlay. */
data class ArenaListState(
    val arenas: List<Arena> = emptyList(),
    val pings: Map<String, Int> = emptyMap(),
    val loading: Boolean = false,
    val error: String = "",
    val selected: String = "",
    /**
     * Arenas that refused a join, mapped to the elapsed-realtime instant their
     * mark lapses. Written by the engine through
     * `WyrmActivity.setArenaRefusedFromNative`, because nothing measurable from
     * this screen can tell a refusing arena from a healthy one: the ping is a
     * TCP connect to the play port, and a server that hangs up on every join
     * still answers it in twenty milliseconds.
     */
    val refused: Map<String, Long> = emptyMap(),
)

/**
 * Choosing where to play, portrait.
 *
 * The official directory is a few hundred machines that differ only in numbers,
 * so the screen is built to compare numbers: one line per arena carrying its
 * round trip, its load, its cluster and its id, ordered by whichever of those
 * the player says matters. The round trip is measured here rather than
 * reported — every arena is dialled on the port the game actually uses — which
 * is the difference between a list and a decision.
 */
@Composable
fun ArenaPickerScreen(
    state: ArenaListState,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ArenaSort.PING) }

    // The busiest arena sets the scale every load bar is drawn against, so it
    // is worked out once for the list rather than once per row.
    val busiest = remember(state.arenas) { state.arenas.maxOfOrNull { it.players } ?: 1 }

    // Marks are held as the instant they lapse, so this drops the expired ones
    // rather than showing a refusal from ten minutes ago. It re-reads the clock
    // whenever a new refusal lands, which is the only moment the set can grow.
    val refusedNow = remember(state.refused) {
        val now = SystemClock.elapsedRealtime()
        state.refused.filterValues { it > now }.keys
    }

    val visible = remember(state.arenas, state.pings, query, sort) {
        state.arenas
            .filter { ArenaDirectory.matches(it, query) }
            .sortedWith(
                when (sort) {
                    // An unmeasured or unreachable arena sorts last rather than
                    // first, which is what a missing number should mean here.
                    ArenaSort.PING -> compareBy { pingRank(state.pings[it.endpoint]) }
                    ArenaSort.PLAYERS -> compareByDescending { it.players }
                    ArenaSort.CLUSTER -> compareBy<Arena> { it.cluster }.thenByDescending { it.players }
                }
            )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WyrmBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insetTop, bottom = insetBottom),
        ) {
            Header(
                loading = state.loading,
                onBack = onBack,
                onRefresh = onRefresh,
            )

            Column(modifier = Modifier.padding(horizontal = Wyrm.Gutter)) {
                Summary(state = state, showing = visible.size)
                Spacer(Modifier.height(14.dp))
                WyrmSearchField(
                    value = query,
                    placeholder = "Search address, server or cluster",
                    onValueChange = { query = it },
                )
                Spacer(Modifier.height(12.dp))
                SortBar(sort = sort, onSortChange = { sort = it })
                Spacer(Modifier.height(6.dp))
            }

            when {
                state.error.isNotEmpty() && state.arenas.isEmpty() ->
                    Notice(state.error, Wyrm.Blood)

                state.loading && state.arenas.isEmpty() ->
                    Notice("Reading the arena directory…", Wyrm.Faint)

                visible.isEmpty() ->
                    Notice("No arena matches “$query”.", Wyrm.Faint)

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.endpoint }) { arena ->
                        WyrmRule()
                        ArenaRow(
                            arena = arena,
                            ping = state.pings[arena.endpoint],
                            current = arena.endpoint == state.selected,
                            refused = refusedNow.contains(arena.endpoint),
                            busiest = busiest,
                            onClick = { onSelect(arena.endpoint) },
                        )
                    }
                    item {
                        WyrmRule()
                        Manual(current = state.selected, onSelect = onSelect)
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun Header(loading: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Wyrm.Gutter)
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "←",
            fontFamily = Wyrm.Body,
            fontSize = 20.sp,
            color = Wyrm.SoftWhite,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "SELECT ARENA",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 3.sp,
            color = Wyrm.White,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (loading) "MEASURING…" else "REFRESH",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = if (loading) Wyrm.Faint else Wyrm.SoftWhite,
            modifier = Modifier
                .clip(wyrmRounded(999.dp))
                .border(1.dp, Wyrm.Line, wyrmRounded(999.dp))
                .clickable(enabled = !loading, onClick = onRefresh)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

/** What the whole directory adds up to, in one line of plain numbers. */
@Composable
internal fun Summary(state: ArenaListState, showing: Int) {
    val players = state.arenas.sumOf { it.players }
    val best = state.pings.values.filter { it > 0 }.minOrNull()
    val measured = state.pings.size

    Text(
        text = buildString {
            append("$showing of ${state.arenas.size} arenas")
            if (players > 0) append(" · ${grouped(players)} playing")
            if (best != null) append(" · best $best ms")
            if (state.arenas.isNotEmpty() && measured < state.arenas.size) {
                append(" · pinging ${measured}/${state.arenas.size}")
            }
        },
        fontFamily = Wyrm.Body,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        color = Wyrm.Faint,
    )
}

@Composable
internal fun SortBar(sort: ArenaSort, onSortChange: (ArenaSort) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WyrmLabel("Sort")
        Spacer(Modifier.width(2.dp))
        ArenaSort.entries.forEach { option ->
            val active = option == sort
            Text(
                text = option.label.uppercase(),
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = if (active) Wyrm.Black else Wyrm.SoftWhite,
                modifier = Modifier
                    .clip(wyrmRounded(999.dp))
                    .background(if (active) Wyrm.White else Color.Transparent)
                    .border(1.dp, if (active) Wyrm.White else Wyrm.Line, wyrmRounded(999.dp))
                    .clickable { onSortChange(option) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * One arena.
 *
 * The address is the identity, the round trip is the verdict, and the load bar
 * underneath is the only picture on the screen — a full bar means a busy arena,
 * which is a reason to pick it or avoid it depending on the player.
 */
@Composable
internal fun ArenaRow(
    arena: Arena,
    ping: Int?,
    current: Boolean,
    refused: Boolean,
    busiest: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (current) Modifier.background(glassFill(1.6f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = Wyrm.Gutter, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(pingColour(ping))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = arena.endpoint,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Wyrm.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (current) {
                Text(
                    text = "CURRENT",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = Wyrm.Green,
                )
                Spacer(Modifier.width(10.dp))
            }
            // Deliberately not disabled. The engine still dials a refusing
            // arena if it is chosen — both original clients do — because a
            // refusal is a coin toss rather than a verdict. This says so.
            if (refused) {
                Text(
                    text = "REFUSED",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = Wyrm.Faint,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = when {
                    ping == null -> "· · ·"
                    ping == ArenaDirectory.UNREACHABLE -> "—"
                    else -> "$ping ms"
                },
                fontFamily = Wyrm.Display,
                fontSize = 17.sp,
                color = if (ping == null) Wyrm.Faint else pingColour(ping),
            )
        }

        Spacer(Modifier.height(7.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SERVER ${arena.id}  ·  CLUSTER ${arena.cluster}  ·  " +
                    "${arena.players} PLAYING",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
                color = Wyrm.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            LoadBar(fraction = if (busiest > 0) arena.players / busiest.toFloat() else 0f)
        }
    }
}

/** How full this arena is next to the busiest one in the directory. */
@Composable
private fun LoadBar(fraction: Float) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(3.dp)
            .clip(wyrmRounded(999.dp))
            .background(Wyrm.Line),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(wyrmRounded(999.dp))
                .background(Wyrm.SoftWhite)
        )
    }
}

/**
 * For an arena the directory does not list.
 *
 * Private servers and anything Wyrm hosts itself never appear upstream, so the
 * address stays typeable. It is at the bottom because almost nobody needs it.
 */
@Composable
private fun Manual(current: String, onSelect: (String) -> Unit) {
    var typed by remember(current) { mutableStateOf(current) }
    val valid = ArenaDirectory.isValidEndpoint(typed)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Wyrm.Gutter)
            .padding(top = 24.dp),
    ) {
        WyrmLabel("Or enter an address")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WyrmWell(modifier = Modifier.weight(1f).height(50.dp)) {
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it.filter { c -> c.isDigit() || c == '.' || c == ':' }.take(21) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 14.sp, color = Wyrm.White),
                    cursorBrush = SolidColor(Wyrm.Green),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    decorationBox = { inner ->
                        if (typed.isEmpty()) {
                            Text(
                                text = "0.0.0.0:444",
                                fontFamily = Wyrm.Body,
                                fontSize = 14.sp,
                                color = Wyrm.Faint,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            WyrmPrimaryAction(
                label = "Use",
                modifier = Modifier.width(96.dp).height(50.dp),
                enabled = valid && typed != current,
                onClick = { onSelect(typed) },
            )
        }
    }
}

@Composable
private fun Notice(message: String, colour: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Wyrm.Gutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontFamily = Wyrm.Body,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colour,
        )
    }
}

/** Unmeasured sorts after everything measured; unreachable sorts after that. */
private fun pingRank(ping: Int?): Int = when {
    ping == null -> 100_000
    ping == ArenaDirectory.UNREACHABLE -> 200_000
    else -> ping
}

private fun pingColour(ping: Int?): Color = when {
    ping == null -> Wyrm.Line
    ping == ArenaDirectory.UNREACHABLE -> Wyrm.Blood
    ping < 90 -> Wyrm.Green
    ping < 180 -> Wyrm.White
    else -> Wyrm.Grey
}

private fun grouped(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
