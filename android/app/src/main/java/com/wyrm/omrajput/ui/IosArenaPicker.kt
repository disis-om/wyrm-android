package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.wyrm.omrajput.i18n.Text
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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

/**
 * The arena machine's country (OM, 2026-10-09): a real flag picture with
 * rounded corners and a hairline edge, then its code ("IN"). Country from
 * NTL's service, flag from flagcdn at 160 px, cached on disk like a face
 * ([AvatarImages]). Nothing is drawn while the country is unknown; a quiet
 * plate stands in until the picture lands.
 */
@Composable
fun ArenaCountryBadge(country: String, modifier: Modifier = Modifier) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    if (country.length != 2) return
    val url = ArenaDirectory.flagUrl(country)
    var image by remember(url) { mutableStateOf(AvatarImages.peek(url)) }
    LaunchedEffect(url) { if (image == null) image = AvatarImages.load(url) }
    val shape = RoundedCornerShape(3.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 20.dp, height = 14.dp)
                .clip(shape)
                .background(wyrmScopedPalette.track)
                .border(0.5.dp, wyrmScopedPalette.ink, shape),
        ) {
            image?.let {
                Image(it, contentDescription = country, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(country, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
            letterSpacing = 0.6.sp, color = wyrmScopedPalette.quiet)
    }
}

/** A saved or typed address as an arena row; a missing port is Slither's 444. */
fun customArena(raw: String): Arena? {
    val trimmed = raw.trim()
    val withPort = ArenaDirectory.withDefaultPort(trimmed)
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
    /** The arena monitor (wyrmarenas) per "ip:port": minimap, top 10, players. */
    monitor: Map<String, com.wyrm.omrajput.data.ArenaLive> = emptyMap(),
    /** Picker style (OM, 2026-10-30): 0 Classic (the list), 1 Details (cards). */
    style: Int = 0,
    onStyle: (Int) -> Unit = {},
    /** Saved arenas removed together (OM, 2026-11-07: long press, select, delete). */
    onRemoveSaved: (List<String>) -> Unit = {},
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var search by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    /* Saved arenas picked by a long press; non-empty = selection mode. */
    var savedPicked by remember { mutableStateOf(setOf<String>()) }
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

    /* Every arena NTL lists (OM, 2026-11-07): the monitor (/v1/arena/status)
       is slither's arenas plus the ones NTL knows, custom arenas included.
       Those missing from slither's directory join the list in both styles,
       so search finds them and a tap plays them, saved or not. Not pinged
       here (custom servers have no /ptc); their players come from the feed. */
    val directoryEndpoints = remember(state.arenas) { state.arenas.mapTo(HashSet()) { it.endpoint.lowercase() } }
    val extra = remember(monitor, directoryEndpoints) {
        monitor.values.asSequence()
            .filter { it.endpoint.isNotEmpty() && it.endpoint.lowercase() !in directoryEndpoints }
            .distinctBy { it.endpoint.lowercase() }
            .mapNotNull { live -> customArena(live.endpoint)?.copy(players = live.players ?: 0, id = if (live.custom) -1 else (live.code ?: -1)) }
            .toList()
    }
    val live = state.arenas + extra
    val filtered = live.filter(::matches).sortedWith(
        compareBy<Arena> { a -> state.pings[a.endpoint]?.takeIf { it > 0 } ?: Int.MAX_VALUE }.thenBy { it.iosCode() },
    )
    // Phase 3 I (OM, 2026-10-01): Recent is ranked by ping too (unmeasured
    // last, joined order kept between equals). Uses only the pings the picker
    // already measured; no new probes.
    val recentRows = recent.mapNotNull { endpoint ->
        live.firstOrNull { it.endpoint == endpoint } ?: if (endpoint in saved) customArena(endpoint) else null
    }.filter(::matches).sortedWith(compareBy { a -> latency(a)?.takeIf { it > 0 } ?: Int.MAX_VALUE })
    // "Best for you": the live arena with the lowest measured ping (never a
    // custom address). Selecting it is a plain tap; nothing auto-joins.
    val best = if (search.isEmpty()) {
        live.filter { (state.pings[it.endpoint] ?: 0) > 0 }.minByOrNull { state.pings[it.endpoint] ?: Int.MAX_VALUE }
    } else null
    val ranked = filtered.filter { it.endpoint !in recent }
    val positive = state.pings.values.filter { it > 0 }
    val low = positive.minOrNull()
    val high = positive.maxOrNull()

    fun latencyText(arena: Arena): String {
        val value = latency(arena) ?: return if (arena.players > 0) "${arena.players} playing" else "—"
        return if (value > 0) "${value}ms" else "Unavailable"
    }

    fun latencyColour(arena: Arena): Color {
        val value = latency(arena) ?: return wyrmScopedPalette.quiet
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
    fun ArenaRow(arena: Arena, tag: String? = null) {
 val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette
        val chosen = selection == arena.endpoint
        val shape = wyrmRounded(15.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(wyrmScopedPalette.card.copy(alpha = 0.9f))
                .border(if (chosen) 2.dp else 1.dp, if (chosen) wyrmScopedPalette.ink else wyrmScopedPalette.rule, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onSelect(arena.endpoint)
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(arena.iosTitle(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = wyrmScopedPalette.ink)
                    if (arena.id >= 0) ArenaCountryBadge(ArenaDirectory.countryOf(arena.address), Modifier.padding(start = 8.dp))
                    if (tag != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tag,
                            fontFamily = Wyrm.Body,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = wyrmScopedPalette.live,
                            modifier = Modifier
                                .clip(WyrmCapsule)
                                .background(wyrmScopedPalette.live.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(arena.endpoint, fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = wyrmScopedPalette.quiet)
            }
            Text(latencyText(arena), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = latencyColour(arena))
        }
    }

    /* Details (OM, 2026-10-30): one card per arena with its live minimap, the
       top 10, its IPv4 address and players, from the arena monitor. Arenas
       come from slither's directory only; the monitor adds to them. */
    @Composable
    fun ArenaDetailCard(arena: Arena, tag: String? = null) {
 val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette
        val chosen = selection == arena.endpoint
        val live = monitor[arena.endpoint]
        val shape = wyrmRounded(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(wyrmScopedPalette.card.copy(alpha = 0.95f))
                .border(if (chosen) 2.dp else 1.dp, if (chosen) wyrmScopedPalette.live else wyrmScopedPalette.rule, shape),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(Modifier.size(104.dp)) { drawArenaMinimap(live?.minimap) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(arena.iosTitle(), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = wyrmScopedPalette.ink)
                        if (arena.id >= 0) ArenaCountryBadge(ArenaDirectory.countryOf(arena.address).ifBlank { live?.country.orEmpty() }, Modifier.padding(start = 8.dp))
                    }
                    if (tag != null) Text(tag, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = wyrmScopedPalette.live,
                        modifier = Modifier.clip(WyrmCapsule).background(wyrmScopedPalette.live.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp))
                    Text(arena.endpoint, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, color = wyrmScopedPalette.mute,
                        modifier = Modifier.clip(wyrmRounded(8.dp)).background(wyrmScopedPalette.well).padding(horizontal = 7.dp, vertical = 3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%,d".format(live?.players ?: arena.players), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = wyrmScopedPalette.ink)
                        Text(" playing", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.quiet)
                        Spacer(Modifier.weight(1f))
                        Text(latencyText(arena), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = latencyColour(arena))
                    }
                    Box(
                        Modifier.height(34.dp).clip(WyrmCapsule).background(if (chosen) wyrmScopedPalette.live else wyrmScopedPalette.ink)
                            .clickable { onSelect(arena.endpoint) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (chosen) "Selected" else "Choose", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (chosen) Color.White else wyrmScopedPalette.onInk) }
                }
            }
            val top = live?.top.orEmpty()
            Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
            if (top.isEmpty()) {
                Text("The live board is not watched for this arena yet.", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.padding(14.dp))
            } else Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("TOP 10", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = wyrmScopedPalette.quiet)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (column in 0..1) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in column * 5 until minOf(top.size, column * 5 + 5)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.width(18.dp))
                                Text(top[i].first.ifBlank { "—" }, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = wyrmScopedPalette.ink,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("%,d".format(top[i].second), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, color = wyrmScopedPalette.ink)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PickRow(arena: Arena, tag: String? = null) = if (style == 1) ArenaDetailCard(arena, tag) else ArenaRow(arena, tag)

    @Composable
    fun SectionLabel(text: String) {
        Text(
            text = text,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = wyrmScopedPalette.quiet,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 3.dp),
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wyrmScopedPalette.paper)
            .padding(top = insetTop),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("LIVE DIRECTORY", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = wyrmScopedPalette.live)
                Text("Pick a server", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = wyrmScopedPalette.ink)
            }
            // "Add IP" (OM, 2026-11-07: a bare + confused players): opens a popup.
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(WyrmCapsule)
                    .background(wyrmScopedPalette.card)
                    .border(1.dp, wyrmScopedPalette.rule, WyrmCapsule)
                    .clickable {
                        addressError = false
                        showAdd = true
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IosIcon(IosGlyph.PLUS, wyrmScopedPalette.ink, size = 15.dp, semibold = true)
                Spacer(Modifier.width(5.dp))
                Text("Add IP", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = wyrmScopedPalette.ink)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Close",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = wyrmScopedPalette.link,
                modifier = Modifier.clickable(onClick = onClose).padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
        PaperSegmented(
            options = listOf("Classic", "Details"),
            selected = style,
            onSelect = onStyle,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth(),
        )
        // Search.
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(wyrmRounded(13.dp))
                .background(wyrmScopedPalette.card.copy(alpha = 0.82f))
                .border(1.dp, wyrmScopedPalette.rule, wyrmRounded(13.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IosIcon(IosGlyph.MAGNIFIER, wyrmScopedPalette.ink, size = 16.dp, semibold = true)
            Spacer(Modifier.width(8.dp))
            PickerField(search, { search = it }, "Arena code or IP", KeyboardType.Uri, Modifier.weight(1f))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + insetBottom),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            best?.let { top ->
                item(key = "best-label") { SectionLabel("BEST FOR YOU") }
                item(key = "best-" + top.endpoint) { WyrmHeroTheme { PickRow(top, tag = "Lowest ping") } }
            }
            if (recentRows.isNotEmpty()) {
                item(key = "recent-label") { SectionLabel("RECENTLY JOINED") }
                items(recentRows, key = { "recent-" + it.endpoint }) { PickRow(it) }
            }
            item(key = "arenas-label") { SectionLabel(if (search.isEmpty()) "ARENAS" else "SEARCH RESULTS") }
            val shown = if (showAll || search.isNotEmpty()) ranked else ranked.take(10)
            items(shown, key = { it.endpoint }) { PickRow(it) }
            if (search.isEmpty() && ranked.size > 10) {
                item(key = "see-all") {
                    Text(
                        text = if (showAll) "Show less" else "See all",
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = wyrmScopedPalette.ink,
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
                            .background(wyrmScopedPalette.card.copy(alpha = 0.9f))
                            .padding(14.dp)
                            .animateContentSize(iosSpring(0.4f, 0.86f)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    if (savedPicked.isEmpty()) showSaved = !showSaved
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (savedPicked.isEmpty()) {
                                Box(Modifier.weight(1f)) { SectionLabel("SAVED ARENAS · ${saved.size}") }
                                IosIcon(IosGlyph.CHEVRON_RIGHT, wyrmScopedPalette.ink, size = 14.dp, semibold = true, modifier = Modifier.rotate(chevron))
                            } else {
                                // Selection (OM, 2026-11-07): long press picks, tap adds or drops.
                                Text("${savedPicked.size} selected", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = wyrmScopedPalette.ink, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                                Text(
                                    if (savedPicked.size == saved.size) "None" else "All",
                                    fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = wyrmScopedPalette.link,
                                    modifier = Modifier.clickable { savedPicked = if (savedPicked.size == saved.size) emptySet() else saved.toSet() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                                Text(
                                    "Cancel",
                                    fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = wyrmScopedPalette.link,
                                    modifier = Modifier.clickable { savedPicked = emptySet() }.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                                Text(
                                    "Delete",
                                    fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = wyrmScopedPalette.onInk,
                                    modifier = Modifier.clip(WyrmCapsule).background(wyrmScopedPalette.badge)
                                        .clickable {
                                            onRemoveSaved(savedPicked.toList())
                                            savedPicked = emptySet()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                        AnimatedVisibility(showSaved, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(Modifier.padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (savedPicked.isEmpty()) {
                                    Text("Touch and hold an arena to select it.", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = wyrmScopedPalette.quiet)
                                }
                                saved.mapNotNull(::customArena).forEach { arena ->
                                    SavedArenaRow(
                                        arena = arena,
                                        chosen = selection == arena.endpoint,
                                        picking = savedPicked.isNotEmpty(),
                                        picked = arena.endpoint in savedPicked,
                                        latency = latencyText(arena),
                                        latencyColour = latencyColour(arena),
                                        onTap = {
                                            if (savedPicked.isEmpty()) onSelect(arena.endpoint)
                                            else savedPicked = if (arena.endpoint in savedPicked) savedPicked - arena.endpoint else savedPicked + arena.endpoint
                                        },
                                        onHold = { savedPicked = savedPicked + arena.endpoint },
                                    )
                                }
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
                        color = wyrmScopedPalette.quiet,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
        }
        // Add IP (OM, 2026-11-07): a popup over the page, lifted above the keyboard.
        if (showAdd) {
            StudioDialog("Add an arena", onDismiss = { showAdd = false }) {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Text(
                    "Type the arena's IP and port, for example 141.164.36.225:446. It is saved under Saved arenas.",
                    fontFamily = Wyrm.Body, fontSize = 13.sp, color = wyrmScopedPalette.quiet,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(wyrmRounded(13.dp))
                        .background(wyrmScopedPalette.card)
                        .border(1.dp, if (addressError) wyrmScopedPalette.badge else wyrmScopedPalette.rule, wyrmRounded(13.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PickerField(customAddress, { customAddress = it; addressError = false }, "IP address:port", KeyboardType.Uri,
                        Modifier.weight(1f).focusRequester(focus), onDone = { saveCustom() })
                }
                if (addressError) {
                    Text("Enter an IPv4 address or [IPv6] address and a port (1–65535).",
                        fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = wyrmScopedPalette.badge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { PaperOutlineButton("Cancel") { showAdd = false } }
                    Box(Modifier.weight(1f)) { PaperPrimaryButton("Save", enabled = customAddress.isNotBlank()) { saveCustom() } }
                }
            }
        }
    }
}

/** A saved arena: tap plays (or picks while selecting), touch and hold selects. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SavedArenaRow(
    arena: Arena,
    chosen: Boolean,
    picking: Boolean,
    picked: Boolean,
    latency: String,
    latencyColour: Color,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val shape = wyrmRounded(15.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (picked) wyrmScopedPalette.ink.copy(alpha = 0.08f) else wyrmScopedPalette.card.copy(alpha = 0.9f))
            .border(if (chosen || picked) 2.dp else 1.dp, if (picked) wyrmScopedPalette.badge else if (chosen) wyrmScopedPalette.ink else wyrmScopedPalette.rule, shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onHold,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (picking) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (picked) wyrmScopedPalette.badge else Color.Transparent)
                    .border(1.5.dp, if (picked) wyrmScopedPalette.badge else wyrmScopedPalette.quiet, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (picked) Text("✓", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(arena.iosTitle(), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = wyrmScopedPalette.ink)
            Text(arena.endpoint, fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = wyrmScopedPalette.quiet)
        }
        Text(latency, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = latencyColour)
    }
}

@Composable
private fun PickerField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val style = TextStyle(fontFamily = Wyrm.Body, fontSize = 13.sp, color = wyrmScopedPalette.ink)
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(wyrmScopedPalette.link),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = wyrmScopedPalette.quiet))
                inner()
            }
        },
    )
}
