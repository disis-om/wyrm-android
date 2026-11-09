package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.ArenaDirectory
import com.wyrm.omrajput.data.EventDetail
import com.wyrm.omrajput.data.EventKind
import com.wyrm.omrajput.data.EventPlayer
import com.wyrm.omrajput.data.EventsPage
import com.wyrm.omrajput.data.WyrmEvent
import com.wyrm.omrajput.data.eventKindBlurb
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * Events in Wyrm (OM, 2026-10-30), the screens of tools/events-prototype:
 * Home's event card, the Events page (Upcoming / Live / Past), an event's
 * page with its Event pass, and the join sheet. Data: backend /v1/events.
 */

/* ---------------------------------------------------------------- time */

@Composable
internal fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs); now = System.currentTimeMillis() } }
    return now
}

private val ZONE: ZoneId get() = ZoneId.systemDefault()
private fun clockText(ms: Long): String = if (ms <= 0) "—" else
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(ZONE))
private fun dayText(ms: Long): String {
    if (ms <= 0) return "—"
    val day = Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate()
    val today = LocalDate.now(ZONE)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(day)
    }
}
internal fun countdownText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "%02d:%02d".format(m, x)
}
private fun minutesText(seconds: Long?): String = when {
    seconds == null -> "—"
    seconds < 60 -> "$seconds s"
    else -> "${(seconds + 30) / 60} min"
}
private fun dollars(usd: Double?): String? = usd?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) "$" + "%,d".format(it.toLong()) else "$" + "%,.2f".format(it) }
private fun grouped(n: Long?): String = n?.let { "%,d".format(it) } ?: "—"

/** Discord's timestamps (<t:1793359800:t>) in the reader's own time, as Discord shows them. */
internal fun discordTimes(text: String, now: Long = System.currentTimeMillis()): String =
    Regex("<t:(\\d+)(?::([tTdDfFR]))?>").replace(text) { m ->
        val ms = m.groupValues[1].toLong() * 1000
        val at = Instant.ofEpochMilli(ms).atZone(ZONE)
        when (m.groupValues[2]) {
            "R" -> { val min = (ms - now) / 60_000; val a = kotlin.math.abs(min)
                val t = if (a < 60) "$a min" else if (a < 2880) "${(a + 30) / 60} h" else "${(a + 720) / 1440} days"
                if (min >= 0) "in $t" else "$t ago" }
            "t" -> clockText(ms)
            "T" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).format(at)
            "d" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(at)
            "D" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).format(at)
            "F" -> DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, ", Locale.getDefault()).format(at) + clockText(ms)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).format(at) + ", " + clockText(ms)
        }
    }

/* ---------------------------------------------------------------- small pieces */

@Composable
internal fun EventFlag(country: String, width: Dp = 18.dp, height: Dp = 13.dp) {
    if (country.length != 2) return
    val url = ArenaDirectory.flagUrl(country)
    var image by remember(url) { mutableStateOf<ImageBitmap?>(AvatarImages.peek(url)) }
    LaunchedEffect(url) { if (image == null) image = AvatarImages.load(url) }
    val shape = RoundedCornerShape(3.dp)
    Box(Modifier.size(width, height).clip(shape).background(Color.White.copy(alpha = .25f)).border(.5.dp, Color.Black.copy(alpha = .14f), shape)) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun GlassPill(modifier: Modifier = Modifier, solid: Color? = null, content: @Composable () -> Unit) {
    val palette = LocalWyrmPalette.current
    Row(
        modifier.clip(CircleShape).background(palette?.well ?: solid ?: Color.White.copy(alpha = .2f))
            .border(1.dp, palette?.rule ?: (if (solid == null) Color.White.copy(alpha = .24f) else Color.Transparent), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun PillText(text: String, color: Color? = null) =
    Text(text, color = LocalWyrmPalette.current?.ink ?: color ?: Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)

@Composable
private fun StatusPill(event: WyrmEvent) {
    // Only this pill ticks; the card and its picture stay still.
    val now = if (event.status == "upcoming") rememberNow() else 0L
    when (event.status) {
        "live" -> GlassPill(solid = Color(0xFFE5484D)) { Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White)); PillText("LIVE") }
        "upcoming" -> GlassPill { PillText("IN " + countdownText(event.startsAtMs - now)) }
        else -> GlassPill { PillText("Ended " + dayText(event.startedAtMs)) }
    }
}

private fun registrationLine(event: WyrmEvent): String? = when {
    event.registration.discord == false -> "No sign-up"
    event.registration.mode == "verified" -> "Verified Discord"
    event.registration.discord == true || event.registration.url != null -> "Register on Discord"
    else -> null
}

/* ---------------------------------------------------------------- Home */

/** Home's event card: the live event, else the next one. */
@Composable
internal fun HomeEventCard(event: WyrmEvent, reminded: Boolean, onOpen: () -> Unit, onJoin: () -> Unit, onRemind: () -> Unit) {
    WyrmHeroTheme(1) {
 val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val shape = wyrmRounded(26.dp)
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).clickable(onClick = onOpen),
    ) {
        EventArt(event.kind, event.id, big = false, modifier = Modifier.matchParentSize())
        Column(Modifier.padding(16.dp).heightIn(min = 196.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(event)
                Spacer(Modifier.weight(1f))
                GlassPill { EventFlag(event.flag); PillText(event.serverLabel) }
            }
            Column(Modifier.clip(wyrmRounded(12.dp)).background(wyrmScopedPalette.paper).padding(8.dp)) {
                Text(if (event.status == "live") "EVENT ON NOW" else "NEXT EVENT", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 1.sp)
                Text(event.name, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 26.sp)
                val sub = buildString {
                    append(if (event.status == "live") "Started " + clockText(event.startedAtMs.takeIf { it > 0 } ?: event.startsAtMs)
                    else dayText(event.startsAtMs) + " · " + clockText(event.startsAtMs))
                    dollars(event.prizeUsd)?.let { append(" · ").append(it) }
                }
                Text(sub, color = wyrmScopedPalette.mute, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            registrationLine(event)?.let { line -> Row { GlassPill { PillText(line) } } }
            Spacer(Modifier.weight(1f, fill = false))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(46.dp).clip(CircleShape).background(wyrmScopedPalette.ink).clickable(onClick = onJoin), contentAlignment = Alignment.Center) {
                    Text(if (event.status == "live") "Join now" else "Join at the start", color = wyrmScopedPalette.onInk, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                if (event.status == "upcoming") BellButton(reminded, onRemind)
            }
        }
    }

    }
}

@Composable
private fun BellButton(on: Boolean, onClick: () -> Unit, size: Dp = 46.dp) {
    val scoped = LocalWyrmPalette.current
    val ink = scoped?.ink ?: Color.White
    val onInk = scoped?.onInk ?: Color(0xFF1D1C19)
    Box(
        Modifier.size(size).clip(CircleShape).background(if (on) ink else (scoped?.well ?: Color.White.copy(alpha = .2f)))
            .border(1.dp, scoped?.rule ?: Color.White.copy(alpha = .25f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { IosIcon(IosGlyph.BELL, if (on) onInk else ink, size = 20.dp) }
}

/** Home when nothing is on: the last event, and a way to all of them. */
@Composable
internal fun HomeLastEventRow(last: WyrmEvent, onOpenEvents: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    IosPaperCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenEvents).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(wyrmRounded(14.dp))) { EventArt(last.kind, last.id, false, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("EVENTS", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 1.sp)
                Text("No event right now", color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Last: ${last.name}" + (last.players?.let { " · $it players" } ?: "") + " · " + dayText(last.startedAtMs),
                    color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IosIcon(IosGlyph.CHEVRON_RIGHT, wyrmScopedPalette.chevron, size = 16.dp)
        }
    }
}

/* ---------------------------------------------------------------- Events page */

@Composable
internal fun EventsScreen(
    page: EventsPage,
    loading: Boolean,
    loadingMore: Boolean,
    remindedIds: Set<String>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onOpen: (WyrmEvent) -> Unit,
    onJoin: (WyrmEvent) -> Unit,
    onRemind: (WyrmEvent) -> Unit,
    onLoadMore: () -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var tab by remember { mutableStateOf(if (page.live.isNotEmpty()) 1 else if (page.upcoming.isNotEmpty()) 0 else 2) }
    SettingsDrillScaffold(title = "Events", insetTop = insetTop, insetBottom = insetBottom, onBack = onBack, parent = "Play") {
        Text("Community tournaments on slither servers. Times are in your time zone.", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body,
            fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        PaperSegmented(
            options = listOf("Upcoming ${page.upcoming.size}", "Live ${page.live.size}", "Past ${page.totalFinished}"),
            selected = tab, onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        )
        val list = when (tab) { 0 -> page.upcoming; 1 -> page.live; else -> page.finished }
        when {
            loading && list.isEmpty() -> Text("Loading…", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, modifier = Modifier.padding(24.dp))
            list.isEmpty() -> EmptyEvents(tab)
            tab < 2 -> Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { e -> HomeEventCard(e, e.id in remindedIds, { onOpen(e) }, { onJoin(e) }, { onRemind(e) }) }
            }
            else -> {
                list.groupBy { dayText(it.startedAtMs.takeIf { ms -> ms > 0 } ?: it.startsAtMs) }.forEach { (day, rows) ->
                    IosSectionLabel(day)
                    IosPaperCard { rows.forEachIndexed { i, e -> PastRow(e, first = i == 0) { onOpen(e) } } }
                }
                if (page.nextCursor != null) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.clip(CircleShape).background(wyrmScopedPalette.card).border(1.dp, wyrmScopedPalette.rule, CircleShape)
                            .clickable(enabled = !loadingMore, onClick = onLoadMore).padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Text(if (loadingMore) "Loading…" else "Show older events", color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp + insetBottom))
    }
}

@Composable
private fun EmptyEvents(tab: Int) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (tab == 0) "Nothing planned yet" else if (tab == 1) "No event is on" else "No past events", color = wyrmScopedPalette.ink,
            fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(if (tab == 0) "When the organisers plan an event it shows here." else if (tab == 1) "A live event shows here with its board and who from Wyrm is playing." else "",
            color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 14.sp)
    }
}

@Composable
private fun PastRow(e: WyrmEvent, first: Boolean, onClick: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(wyrmRounded(14.dp))) { EventArt(e.kind, e.id, false, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EventFlag(e.flag, 16.dp, 11.dp)
                Text(listOfNotNull(e.serverLabel, clockText(e.startedAtMs), e.durationSeconds?.let(::minutesText)).joinToString(" · "),
                    color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.5.sp)
            }
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                e.players?.let { Chip("$it players") }
                dollars(e.prizeUsd)?.let { Chip(it, Color(0xFFC28A2C)) }
                e.wyrmPlayers?.takeIf { it > 0 }?.let { Chip("$it on Wyrm", wyrmScopedPalette.live) }
            }
        }
        IosIcon(IosGlyph.CHEVRON_RIGHT, wyrmScopedPalette.chevron, size = 16.dp)
    }
}

@Composable
private fun Chip(text: String, tint: Color? = null) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Text(text, color = tint ?: wyrmScopedPalette.mute, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background((tint ?: wyrmScopedPalette.ink).copy(alpha = if (tint == null) .06f else .15f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/* ---------------------------------------------------------------- event page */

@Composable
internal fun EventDetailScreen(
    event: WyrmEvent,
    detail: EventDetail?,
    reminded: Boolean,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onRemind: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val now = rememberNow()
    val e = detail?.event ?: event
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize().background(wyrmScopedPalette.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(340.dp + insetTop)) {
                EventArt(e.kind, e.id, big = true, modifier = Modifier.matchParentSize())
                Column(Modifier.fillMaxSize().padding(top = insetTop + 10.dp, start = 18.dp, end = 18.dp, bottom = 44.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundGlass(onBack) { IosIcon(IosGlyph.CHEVRON_LEFT, Color.White, size = 20.dp) }
                        Spacer(Modifier.weight(1f))
                        if (e.status == "upcoming") BellButton(reminded, onRemind, 40.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (e.status == "live") GlassPill(solid = Color(0xFFE5484D)) { PillText("LIVE") }
                        GlassPill { EventFlag(e.flag); PillText(e.region.ifBlank { e.serverLabel }) }
                        e.eventNumber?.let { GlassPill { PillText("#$it") } }
                    }
                    val title = e.title?.takeIf { it.length < 34 }
                    Text(title ?: e.name, color = Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, modifier = Modifier.padding(top = 10.dp))
                    Text((if (title != null) e.name + " · " else "") + eventKindBlurb(e.kind), color = Color.White.copy(alpha = .88f), fontFamily = Wyrm.Body, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            // The timer card overlaps the art a little.
            Card(Modifier.offset(y = (-26).dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label(when (e.status) { "upcoming" -> "Starts in"; "live" -> "On for"; else -> "Played" })
                        Text(when (e.status) {
                            "upcoming" -> countdownText(e.startsAtMs - now)
                            "live" -> countdownText(now - (e.startedAtMs.takeIf { it > 0 } ?: e.startsAtMs))
                            else -> dayText(e.startedAtMs)
                        }, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (e.status == "finished") clockText(e.startedAtMs) + " – " + clockText(e.endedAtMs) else clockText(e.startsAtMs),
                            color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (e.status == "finished") minutesText(e.durationSeconds) else dayText(e.startsAtMs), color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.5.sp)
                    }
                }
            }
            Column(Modifier.offset(y = (-12).dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (e.status != "upcoming" && !detail?.friends.isNullOrEmpty()) FriendsBar(detail!!.friends, e.status == "live")
                detail?.live?.let { LiveBoard(it) }
                if (e.status != "finished") RegistrationCard(e) { url -> runCatching { uri.openUri(url) } }
                WyrmPlayers(e, detail)
                if (e.rules.isNotEmpty()) {
                    IosSectionLabel("Rules")
                    IosPaperCard { e.rules.forEachIndexed { i, rule ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            IosIcon(IosGlyph.CHECKMARK, wyrmScopedPalette.live, size = 16.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(rule.replace("**", ""), color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontSize = 13.5.sp)
                        }
                    } }
                }
                e.markdown?.let { md ->
                    IosSectionLabel("From the organisers")
                    IosPaperCard { WyrmMarkdown(discordTimes(md, now), Modifier.padding(16.dp), baseSize = 14.sp) }
                }
                if (e.status == "finished") RegistrationCard(e) { url -> runCatching { uri.openUri(url) } }
                EventPass(e, now, onCopy)
                EventPeople(onCopy)
                Spacer(Modifier.height(110.dp + insetBottom))
            }
        }
        if (e.status != "finished") {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, wyrmScopedPalette.paper)))
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp + insetBottom), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(58.dp).clip(CircleShape).background(wyrmScopedPalette.ink).clickable(onClick = onJoin), contentAlignment = Alignment.Center) {
                    Text(if (e.status == "live") "Join now" else "Join at the start", color = wyrmScopedPalette.onInk, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun RoundGlass(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)).border(1.dp, Color.White.copy(alpha = .25f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier) { IosPaperCard(content) }
}

@Composable
private fun Label(text: String) = Text(text.uppercase(), color = Wyrm.Quiet, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = .9.sp)

@Composable
private fun FriendsBar(friends: List<EventPlayer>, live: Boolean) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Spacer(Modifier.height(4.dp))
    IosPaperCard {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            friends.take(2).forEach { p -> WyrmAvatar(p.avatarUrl, p.avatarKey, p.name.take(1), 30.dp); Spacer(Modifier.width(4.dp)) }
            Spacer(Modifier.width(8.dp))
            val names = friends.take(2).joinToString(" and ") { it.name }
            Text("$names ${if (live) "are playing" else "played"} this event", color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiveBoard(board: com.wyrm.omrajput.data.EventLiveBoard) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    IosSectionLabel("Live in the arena")
    IosPaperCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(92.dp)) { drawArenaMinimap(board.minimap) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(grouped(board.players?.toLong()), color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Text("snakes in the arena now", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 13.sp)
            }
        }
        board.leaderboard.forEachIndexed { i, (name, score) -> BoardRow(i + 1, name, score) }
    }
}

@Composable
private fun BoardRow(rank: Int, name: String, score: Long) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.width(24.dp))
        Text(name.ifBlank { "—" }, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(grouped(score), color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun RegistrationCard(e: WyrmEvent, onOpenUrl: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val r = e.registration
    val (title, lead, steps) = when {
        r.discord == false -> Triple("No sign-up needed", "Join the server at the start time with your usual in-game name.", emptyList())
        r.mode == "verified" -> Triple("Verified Discord players are in", "The organisers enrol players who are verified in their Discord automatically.",
            listOf("Join the organisers' Discord and verify once.", "Keep the same in-game name you verified with.", "Join this server when the event starts."))
        else -> Triple("Register on Discord first", "The organisers' bot runs sign-ups. Players who are not registered cannot take part.",
            listOf("Open the registration channel in their Discord.", "Sign up with your real in-game name. Their bot sends you a link.", "Join this server at the start. Do not change network or name."))
    }
    IosSectionLabel("Registration")
    IosPaperCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(lead, color = wyrmScopedPalette.mute, fontFamily = Wyrm.Body, fontSize = 13.5.sp)
            steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(wyrmScopedPalette.well), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(s, color = wyrmScopedPalette.mute, fontFamily = Wyrm.Body, fontSize = 13.5.sp)
                }
            }
            r.url?.takeIf { r.discord != false }?.let { url ->
                Box(Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(Color(0xFF5865F2)).clickable { onOpenUrl(url) }, contentAlignment = Alignment.Center) {
                    Text("Open the registration channel", color = Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
            Text("Sign-ups are handled by the event organisers, not by Wyrm. For anything about registering, message Noah (noahvem) or Lucky (lucky_623) on Discord.",
                color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WyrmPlayers(e: WyrmEvent, detail: EventDetail?) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    IosSectionLabel("Wyrm players")
    IosPaperCard {
        val players = if (e.status == "upcoming") emptyList() else detail?.wyrm.orEmpty()
        when {
            detail == null && e.status != "upcoming" -> Text("Loading…", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, modifier = Modifier.padding(16.dp))
            players.isEmpty() -> Text(if (e.status == "upcoming") "Players from Wyrm show here once the event starts." else "Nobody from Wyrm played this one.",
                color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            else -> players.forEachIndexed { i, p ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (p.bestScore != null) "${i + 1}" else "·", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                    WyrmAvatar(p.avatarUrl, p.avatarKey, p.name.take(1), 30.dp, online = p.playingNow)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (p.runs > 0) "${p.runs} ${if (p.runs == 1) "life" else "lives"} · ${grouped(p.kills)} kills" else if (p.playingNow) "Playing now" else "Was in the arena",
                            color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.sp)
                    }
                    p.bestScore?.let { Text(grouped(it), color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                }
            }
        }
    }
}

/**
 * The Event pass (OM, 2026-10-30): what the feed says about the event, once,
 * in plain words, and only what applies (no team line for a solo event, no
 * Discord line without a sign-up, no tiers, no raw port or http).
 */
@Composable
private fun EventPass(e: WyrmEvent, now: Long, onCopy: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val look = eventLook(e.kind)
    IosSectionLabel("Event pass")
    val shape = wyrmRounded(24.dp)
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(wyrmScopedPalette.card).border(1.dp, wyrmScopedPalette.rule, shape)) {
        Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(look.bg)).padding(16.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("EVENT TYPE", color = Color.White.copy(alpha = .85f), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 1.sp)
                Text(e.name, color = Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            }
            e.eventNumber?.let { GlassPill { PillText("#$it") } }
        }
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(wyrmScopedPalette.well).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EventFlag(e.flag, 24.dp, 17.dp)
            Text("${e.serverLabel} server", color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(e.address, color = wyrmScopedPalette.mute, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(wyrmScopedPalette.card).clickable { onCopy(e.address) }, contentAlignment = Alignment.Center) {
                CopyIcon(wyrmScopedPalette.ink)
            }
        }
        val tiles = buildList<Triple<String, String, String>> {
            when (e.status) {
                "finished" -> add(Triple("Played", dayText(e.startedAtMs) + " · " + clockText(e.startedAtMs) + "–" + clockText(e.endedAtMs), minutesText(e.durationSeconds)))
                "live" -> add(Triple("Started", clockText(e.startedAtMs.takeIf { it > 0 } ?: e.startsAtMs), countdownText(now - (e.startedAtMs.takeIf { it > 0 } ?: e.startsAtMs)) + " ago"))
                else -> add(Triple("Starts", dayText(e.startsAtMs) + " · " + clockText(e.startsAtMs), "in " + countdownText(e.startsAtMs - now)))
            }
            dollars(e.prizeUsd)?.let { add(Triple("Prize", it, "for the winner")) }
            e.roundMinutes?.takeIf { it > 0 }?.let { add(Triple("First round", "$it min", "then players are knocked out")) }
            if (e.status == "finished") e.players?.let { add(Triple("Players", grouped(it.toLong()), "took part")) }
            if ((e.kind == EventKind.TEAM_LAST_STANDING || e.kind == EventKind.LAST_DUO_STANDING) && e.teamNames == true)
                add(Triple(if (e.kind == EventKind.LAST_DUO_STANDING) "Duos" else "Teams", "Named", "each team plays under its name"))
            if (e.needsSignUp) add(Triple("Discord sign-up", "Needed", when {
                e.registration.mode == "verified" -> "Verified players are in automatically"
                e.registration.autoAccept == true -> "Sign up in their channel, you are accepted at once"
                else -> "Sign up in their channel"
            }))
            if (e.reconstructed) add(Triple("Note", "Rebuilt from server logs", "some details may be missing"))
        }
        // The first tile and any sign-up or note take a whole row; the rest pair up.
        val wide = setOf("Starts", "Started", "Played", "Discord sign-up", "Note")
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val halves = tiles.filter { it.first !in wide }.toMutableList()
            tiles.forEach { t ->
                if (t.first in wide) PassTile(t, Modifier.fillMaxWidth())
                else if (halves.firstOrNull() == t) {
                    halves.removeAt(0)
                    val partner = halves.removeFirstOrNull()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PassTile(t, Modifier.weight(1f))
                        if (partner != null) PassTile(partner, Modifier.weight(1f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val atlas = rememberSkinAtlas().value
            val skin = look.skin + look.skin2
            Canvas(Modifier.width((skin.length * 11 + 6).dp).height(16.dp)) {
                if (atlas != null) skin.forEachIndexed { i, ch -> drawBead(atlas, beadGroup(ch), i * 11.dp.toPx(), 0f, 16.dp.toPx()) }
            }
            Spacer(Modifier.weight(1f))
            Text(listOfNotNull(e.region.takeIf { it.isNotBlank() }, e.eventNumber?.let { "event $it" }).joinToString(" · "), color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PassTile(t: Triple<String, String, String>, modifier: Modifier) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    Column(modifier.clip(RoundedCornerShape(16.dp)).background(wyrmScopedPalette.well).padding(horizontal = 12.dp, vertical = 11.dp)) {
        Label(t.first)
        Text(t.second, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
        if (t.third.isNotBlank()) Text(t.third, color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.5.sp)
    }
}

/* The people behind the events (OM, 2026-10-30), with their Discord names.
   Registration questions go to Noah and Lucky; Wyrm's own to OM. */
private data class EventPerson(val name: String, val role: String, val discord: String, val note: String)
private val EVENT_PEOPLE = listOf(
    EventPerson("Noah", "Organiser", "noahvem", "Runs the events. Ask about registration."),
    EventPerson("Lucky", "Manager", "lucky_623", "Manages the events. Ask about registration."),
    EventPerson("OM", "Wyrm developer", "disis_om", "Makes Wyrm. Ask about the app."),
)

@Composable
private fun EventPeople(onCopy: (String) -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    IosSectionLabel("Run by")
    IosPaperCard {
        EVENT_PEOPLE.forEachIndexed { i, person ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
            Row(Modifier.fillMaxWidth().clickable { onCopy(person.discord) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(wyrmRounded(12.dp)).background(Color(0xFF5865F2).copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Text(person.name.take(1), color = Color(0xFF5865F2), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Spacer(Modifier.width(6.dp))
                        Chip(person.role, if (person.role == "Wyrm developer") wyrmScopedPalette.live else Color(0xFF5865F2))
                    }
                    Text(person.note, color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.5.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Discord", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 10.5.sp)
                    Text(person.discord, color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Text("Tap a name to copy it. For registration, message Noah or Lucky.", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
    }
}

/* ---------------------------------------------------------------- joining */

/** Before joining: registration, then join now or at the start. */
@Composable
internal fun EventJoinSheet(event: WyrmEvent, insetBottom: Dp, onJoin: () -> Unit, onDismiss: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(8.dp).padding(bottom = insetBottom).fillMaxWidth().clip(wyrmRounded(34.dp)).background(wyrmScopedPalette.card)
            .clickable(enabled = false) {}.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val needs = event.needsSignUp
            Text(if (needs) "Are you registered?" else "Ready to join", color = wyrmScopedPalette.ink, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text(when {
                !needs -> "No sign-up is needed for this event."
                event.registration.mode == "verified" -> "This event takes players verified in the organisers' Discord. Unregistered players cannot take part."
                else -> "This event needs a sign-up in the organisers' Discord before it starts. Unregistered players cannot take part."
            }, color = wyrmScopedPalette.mute, fontFamily = Wyrm.Body, fontSize = 14.sp)
            event.rules.take(3).forEach { rule ->
                Row { IosIcon(IosGlyph.CHECKMARK, wyrmScopedPalette.live, size = 15.dp); Spacer(Modifier.width(8.dp)); Text(rule.replace("**", ""), color = wyrmScopedPalette.mute, fontFamily = Wyrm.Body, fontSize = 13.sp) }
            }
            if (needs) Text("Not sure? Message Noah (noahvem) or Lucky (lucky_623) on Discord.", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontSize = 12.5.sp)
            if (needs) event.registration.url?.let { url ->
                Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Color(0xFF5865F2)).clickable { runCatching { uri.openUri(url) } }, contentAlignment = Alignment.Center) {
                    Text("Register on Discord", color = Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(wyrmScopedPalette.ink).clickable(onClick = onJoin), contentAlignment = Alignment.Center) {
                Text(when { event.status == "live" -> "Join now"; needs -> "I'm registered, join at the start"; else -> "Join at the start" },
                    color = wyrmScopedPalette.onInk, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Box(Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Text("Not now", color = wyrmScopedPalette.quiet, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

/** Waiting for the start: a countdown, then the arena (same pause as Battledome's Play). */
@Composable
internal fun EventCountdownOverlay(event: WyrmEvent, onEnter: () -> Unit, onCancel: () -> Unit) {
    val now = rememberNow(250L)
    val left = event.startsAtMs - now
    LaunchedEffect(left <= 0) { if (left <= 0) onEnter() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F0C)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(countdownText(left), color = Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp)
            Text(event.name, color = Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("The event starts, then Wyrm enters ${event.address}", color = Color.White.copy(alpha = .7f), fontFamily = Wyrm.Body, fontSize = 14.sp)
            Box(Modifier.width(200.dp).height(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Text("Cancel", color = Color.White, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

/** Two overlapping sheets: copy. */
@Composable
private fun CopyIcon(tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val s = size.width
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx())
        drawRoundRect(tint, androidx.compose.ui.geometry.Offset(s * .36f, s * .36f), androidx.compose.ui.geometry.Size(s * .56f, s * .56f), androidx.compose.ui.geometry.CornerRadius(s * .1f), style = stroke)
        drawRoundRect(tint, androidx.compose.ui.geometry.Offset(s * .08f, s * .08f), androidx.compose.ui.geometry.Size(s * .56f, s * .56f), androidx.compose.ui.geometry.CornerRadius(s * .1f), style = stroke)
    }
}
