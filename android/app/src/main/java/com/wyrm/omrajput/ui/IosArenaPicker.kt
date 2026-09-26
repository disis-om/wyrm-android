package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Arena
import com.wyrm.omrajput.data.ArenaDirectory

/*
 * Wyrm iOS's server picker (WyrmArenaPicker in WyrmDesignMain.swift).
 *
 * A full page: LIVE DIRECTORY over "Pick a server", a + for a custom
 * address, Close. Below the search, the arenas you joined last, then the live
 * directory by latency — ten at first, the rest behind "See all" — and the
 * saved custom addresses in a fold. A row is the arena code, its address and
 * its latency, coloured from the fastest (green) to the slowest (red).
 */

/** Wyrm iOS's four-digit arena code. */
fun Arena.iosCode(): String = "%04d".format(id % 10_000)

private fun Arena.iosTitle(): String = if (id < 0) "Custom arena" else "Arena ${iosCode()}"

/** A saved or typed address as an arena row; a missing port is Slither's 444. */
fun customArena(raw: String): Arena? {
    val trimmed = raw.trim()
    val withPort = if (trimmed.contains(':')) trimmed else "$trimmed:444"
    if (!ArenaDirectory.isValidEndpoint(withPort)) return null
    val address = withPort.substringBeforeLast(':')
    val port = withPort.substringAfterLast(':').toIntOrNull() ?: return null
    return Arena(address = address, port = port, players = 0, id = -1, cluster = 0)
}

@Composable
fun IosArenaPicker(
    state: ArenaListState,
    saved: List<String>,
    recent: List<String>,
    selection: String,
    insetTop: Dp,
    insetBottom: Dp,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onSaveCustom: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var customAddress by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf(false) }
    val customLatencies = remember { mutableStateMapOf<String, Int>() }

    // Custom addresses are not in the directory sweep, so measure the ones on
    // screen here, once each, the way iOS measures a saved arena.
    LaunchedEffect(saved, selection) {
        (saved + selection).distinct().mapNotNull(::customArena)
            .filter { custom -> state.arenas.none { it.endpoint == custom.endpoint } && custom.endpoint !in customLatencies }
            .forEach { custom -> customLatencies[custom.endpoint] = ArenaDirectory.ping(custom) }
    }

    fun matches(arena: Arena) = search.isEmpty() ||
        arena.endpoint.contains(search) || arena.iosTitle().contains(search, ignoreCase = true)

    fun latency(arena: Arena): Int? = if (arena.id < 0) customLatencies[arena.endpoint] else state.pings[arena.endpoint]

    val live = state.arenas
    val filtered = live.filter(::matches).sortedWith(
        compareBy<Arena> { a -> state.pings[a.endpoint]?.takeIf { it > 0 } ?: Int.MAX_VALUE }.thenBy { it.iosCode() },
    )
    val recentRows = recent.mapNotNull { endpoint ->
        live.firstOrNull { it.endpoint == endpoint } ?: if (endpoint in saved) customArena(endpoint) else null
    }.filter(::matches)
    val ranked = filtered.filter { it.endpoint !in recent }
    val positive = state.pings.values.filter { it > 0 }
    val low = positive.minOrNull()
    val high = positive.maxOrNull()

    fun latencyText(arena: Arena): String {
        val value = latency(arena) ?: return "—"
        return if (value > 0) "${value}ms" else "Unavailable"
    }

    fun latencyColour(arena: Arena): Color {
        val value = latency(arena) ?: return Wyrm.Quiet
        if (value <= 0) return Color(0.75f, 0.25f, 0.22f)
        val lo = low ?: value
        val hi = high ?: value
        val ratio = if (hi == lo) 0f else ((value - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
        return Color(0.18f + 0.68f * ratio, 0.68f - 0.45f * ratio, 0.27f - 0.08f * ratio)
    }

    fun saveCustom() {
        val arena = customArena(customAddress)
        if (arena == null) {
            addressError = true
            return
        }
        addressError = false
        onSaveCustom(arena.endpoint)
        showSaved = true
        showAdd = false
        customAddress = ""
    }

    @Composable
    fun ArenaRow(arena: Arena) {
        val chosen = selection == arena.endpoint
        val shape = wyrmRounded(15.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Wyrm.Card.copy(alpha = 0.9f))
                .border(if (chosen) 2.dp else 1.dp, if (chosen) Wyrm.Ink else Wyrm.Rule, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onSelect(arena.endpoint)
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(arena.iosTitle(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
                Text(arena.endpoint, fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = Wyrm.Quiet)
            }
            Text(latencyText(arena), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = latencyColour(arena))
        }
    }

    @Composable
    fun SectionLabel(text: String) {
        Text(
            text = text,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 3.dp),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper)
            .padding(top = insetTop),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("LIVE DIRECTORY", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = Wyrm.Live)
                Text("Pick a server", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = Wyrm.Ink)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Wyrm.Card)
                    .clickable { showAdd = !showAdd },
                contentAlignment = Alignment.Center,
            ) { IosIcon(IosGlyph.PLUS, Wyrm.Ink, size = 19.dp, semibold = true) }
            Spacer(Modifier.width(8.dp))
            Text(
                "Close",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Wyrm.Link,
                modifier = Modifier.clickable(onClick = onClose).padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
        // Search.
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(wyrmRounded(13.dp))
                .background(Wyrm.Card.copy(alpha = 0.82f))
                .border(1.dp, Wyrm.Rule, wyrmRounded(13.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IosIcon(IosGlyph.MAGNIFIER, Wyrm.Ink, size = 16.dp, semibold = true)
            Spacer(Modifier.width(8.dp))
            PickerField(search, { search = it }, "Arena code or IP", KeyboardType.Uri, Modifier.weight(1f))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + insetBottom),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (showAdd) {
                item(key = "add") {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(wyrmRounded(14.dp))
                                .background(Wyrm.Card)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PickerField(customAddress, { customAddress = it }, "IPv4 address:port", KeyboardType.Uri, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Save",
                                fontFamily = Wyrm.Body,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Wyrm.Link,
                                modifier = Modifier.clickable { saveCustom() },
                            )
                        }
                        if (addressError) {
                            Text(
                                "Enter a valid IPv4 address and port (1–65535).",
                                fontFamily = Wyrm.Body,
                                fontSize = 11.sp,
                                color = Color(0xFFFF3B30),
                            )
                        }
                    }
                }
            }
            if (recentRows.isNotEmpty()) {
                item(key = "recent-label") { SectionLabel("RECENTLY JOINED") }
                items(recentRows, key = { "recent-" + it.endpoint }) { ArenaRow(it) }
            }
            item(key = "arenas-label") { SectionLabel(if (search.isEmpty()) "ARENAS" else "SEARCH RESULTS") }
            val shown = if (showAll || search.isNotEmpty()) ranked else ranked.take(10)
            items(shown, key = { it.endpoint }) { ArenaRow(it) }
            if (search.isEmpty() && ranked.size > 10) {
                item(key = "see-all") {
                    Text(
                        text = if (showAll) "Show less" else "See all",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Wyrm.Ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAll = !showAll }
                            .padding(14.dp),
                    )
                }
            }
            if (saved.isNotEmpty()) {
                item(key = "saved") {
                    val chevron by animateFloatAsState(if (showSaved) 90f else 0f, iosSpring(0.35f, 0.86f), label = "saved-chevron")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(wyrmRounded(15.dp))
                            .background(Wyrm.Card.copy(alpha = 0.9f))
                            .padding(14.dp)
                            .animateContentSize(iosSpring(0.4f, 0.86f)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showSaved = !showSaved },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) { SectionLabel("SAVED ARENAS · ${saved.size}") }
                            IosIcon(IosGlyph.CHEVRON_RIGHT, Wyrm.Ink, size = 14.dp, semibold = true, modifier = Modifier.rotate(chevron))
                        }
                        AnimatedVisibility(showSaved, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(Modifier.padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                saved.mapNotNull(::customArena).forEach { ArenaRow(it) }
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty() && recentRows.isEmpty() && saved.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (state.loading) "Loading the live directory…" else "No active arenas right now. Try refreshing or add a custom IP.",
                        fontFamily = Wyrm.Body,
                        fontSize = 12.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Ink)
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Wyrm.Link),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Wyrm.Quiet.copy(alpha = 0.7f)))
                inner()
            }
        },
    )
}
