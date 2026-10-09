package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.ArenaDirectory
import com.wyrm.omrajput.data.FriendActivity
import com.wyrm.omrajput.data.FriendPresence
import kotlinx.coroutines.delay

/**
 * A friend's activity in one line (OM, 2026-10-09): "Playing in Arena 4817"
 * with the arena's flag, how long, and Join; "Active now" while Wyrm is open;
 * "Played in Arena 4817 · 2h ago" after. Nothing for a friend who shares
 * nothing. Same words as Wyrm iOS's `WyrmFriendActivityLine`.
 */
@Composable
fun FriendActivityLine(
    activity: FriendActivity?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.5.sp,
    onJoin: ((String) -> Unit)? = null,
) {
    activity ?: return
    // "12m" keeps counting between fetches.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000L)
            value = System.currentTimeMillis()
        }
    }
    val arena = activity.arena
    when {
        activity.playing && arena != null -> {
            LaunchedEffect(arena) { runCatching { ArenaDirectory.loadCountries(listOf(arena)) } }
            Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Playing in ${FriendPresence.arenaName(arena)}",
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = fontSize,
                    color = Wyrm.Live, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                ArenaCountryBadge(ArenaDirectory.countryOf(arena))
                activity.sinceNow(now)?.let {
                    Text(shortSpan(it), fontFamily = Wyrm.Body, fontSize = fontSize, color = Wyrm.Quiet, maxLines = 1)
                }
                if (onJoin != null) {
                    Text(
                        "Join",
                        fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = fontSize,
                        color = Wyrm.OnInk,
                        modifier = Modifier
                            .clip(wyrmRounded(Wyrm.Pill))
                            .background(Wyrm.Ink)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onJoin(arena) }
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        activity.online -> Text(
            "Active now", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = fontSize,
            color = Wyrm.Live, maxLines = 1, modifier = modifier,
        )
        activity.lastArena != null && activity.lastPlayedAtMs != null -> Text(
            "Played in ${FriendPresence.arenaName(activity.lastArena)} · ${shortSpan(now - activity.lastPlayedAtMs)} ago",
            fontFamily = Wyrm.Body, fontSize = fontSize, color = Wyrm.Quiet, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = modifier,
        )
    }
}

/** 45s, 12m, 3h 5m, 2d. */
internal fun shortSpan(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        minutes < 1 -> "${seconds.coerceAtLeast(1)}s"
        hours < 1 -> "${minutes}m"
        hours < 24 -> if (minutes % 60 == 0L) "${hours}h" else "${hours}h ${minutes % 60}m"
        else -> "${hours / 24}d"
    }
}
