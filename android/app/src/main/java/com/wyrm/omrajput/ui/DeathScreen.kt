package com.wyrm.omrajput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the run was worth. */
data class DeathStats(
    val score: Int = 0,
    val kills: Int = 0,
    val playTimeSeconds: Double = 0.0,
)

/**
 * The death card, landscape, over a still-running arena.
 *
 * Dying does not end anything, and it does not interrupt anything either: the
 * engine keeps the player in the arena watching until the arena itself is done
 * with them, and only then asks for this. By the time it arrives the snake has
 * respawned under the bot, so the game is moving behind it. Only the card is
 * opaque — the rest is a scrim thin enough to watch the arena through, because
 * the whole point is that the world did not stop for you.
 *
 * It arrives in four beats, and the gaps between them are the effect:
 *
 *   the scrim closes;
 *   the card drops in from slightly too large and settles;
 *   the words land on it, oversized and off square, and knock it down a little;
 *   the numbers come up one after another, and then the way out.
 *
 * Landing the words with the card makes them a title. Landing them a beat late,
 * on something already sitting still, makes them a stamp.
 */
@Composable
fun DeathScreen(
    stats: DeathStats,
    autoRespawn: Boolean,
    onAutoRespawn: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onHome: () -> Unit,
    onLobby: () -> Unit,
) {
    val scrim = remember { Animatable(0f) }
    val card = remember { Animatable(0f) }
    val stamp = remember { Animatable(0f) }
    val impact = remember { Animatable(0f) }
    val tail = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(durationMillis = 260)) }
        launch { card.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing)) }
        launch {
            delay(150)
            // Springy on purpose: it carries a little past one, which reads as
            // the stamp bouncing off what it hit.
            stamp.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
            )
        }
        launch {
            // The blow itself: the card takes it at the moment of contact and
            // shakes it off. Without this the words hit nothing.
            delay(215)
            impact.snapTo(1f)
            impact.animateTo(
                0f,
                spring(dampingRatio = 0.30f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        launch {
            delay(330)
            tail.animateTo(1f, tween(durationMillis = 520, easing = LinearOutSlowInEasing))
        }
    }

    // Each of the three numbers, then the buttons, gets its own slice of `tail`
    // so they come up one after another rather than together.
    fun staggered(index: Int, count: Int = 4): Float {
        val span = 1f / (count + 1)
        val start = index * span
        return ((tail.value - start) / (1f - start)).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Solid, not a scrim. Nothing respawns behind this any more, so
            // there is no live arena to keep visible — only the frozen last
            // frame of a match that is over, which is worse than black.
            .background(Color.Black.copy(alpha = scrim.value)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = card.value
                    // Falls towards the player rather than growing into place,
                    // then takes the hit from the words.
                    val s = 1f + (1f - card.value) * 0.12f - impact.value * 0.022f
                    scaleX = s
                    scaleY = s
                    translationY = impact.value * 7.dp.toPx()
                }
                .widthIn(max = 460.dp)
                .clip(wyrmRounded(Wyrm.CornerLarge))
                .background(Wyrm.Black.copy(alpha = 0.80f))
                .background(glassFill(1.3f))
                .border(1.dp, glassEdge(1.3f), wyrmRounded(Wyrm.CornerLarge))
                .padding(horizontal = 34.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // The bruise the stamp leaves: a red bloom that opens with the
                // blow and is gone before it is noticed as a shape.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .drawBehind {
                            val glow = impact.value
                            if (glow <= 0.01f) return@drawBehind
                            val radius = size.minDimension * (0.5f + (1f - glow) * 1.1f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Wyrm.Blood.copy(alpha = 0.42f * glow),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = radius,
                                ),
                                radius = radius,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        },
                )
                Text(
                    text = "YOU DIED",
                    fontFamily = Wyrm.Display,
                    fontSize = 44.sp,
                    letterSpacing = 4.sp,
                    color = Wyrm.Blood,
                    modifier = Modifier.graphicsLayer {
                        // More than twice the size on the way down, and a few
                        // degrees off square until it hits.
                        val s = 1f + (1f - stamp.value) * 1.4f
                        scaleX = s
                        scaleY = s
                        rotationZ = -6f * (1f - stamp.value)
                        alpha = (stamp.value * 2.8f).coerceAtMost(1f)
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            WyrmRule()
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Score", stats.score.toString(), staggered(0))
                Stat("Kills", stats.kills.toString(), staggered(1))
                Stat("Time", formatPlayTime(stats.playTimeSeconds), staggered(2))
            }

            Spacer(Modifier.height(20.dp))

            // The one control that makes this card stop appearing. Turning it
            // on plays again straight away, because a card whose purpose is to
            // not be shown has no business staying up once it is switched off.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val t = staggered(3)
                        alpha = t
                        translationY = (1f - t) * 12.dp.toPx()
                    }
                    .clip(wyrmRounded(Wyrm.Pill))
                    .clickable { onAutoRespawn(!autoRespawn) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 24.dp)
                        .clip(wyrmRounded(Wyrm.Pill))
                        .background(
                            if (autoRespawn) Wyrm.White.copy(alpha = 0.88f)
                            else Wyrm.White.copy(alpha = 0.12f),
                        )
                        .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill)),
                    contentAlignment =
                        if (autoRespawn) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(18.dp)
                            .clip(wyrmRounded(Wyrm.Pill))
                            .background(if (autoRespawn) Wyrm.Black else Wyrm.SoftWhite),
                    )
                }
                Text(
                    text = "  Auto respawn — skip this screen",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.sp,
                    color = if (autoRespawn) Wyrm.SoftWhite else Wyrm.Grey,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val t = staggered(3)
                        alpha = t
                        translationY = (1f - t) * 12.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GhostAction(
                    label = "Home",
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    onClick = onHome,
                )
                // Changing arena, or the name on the snake, is a lobby decision
                // rather than a Home one — and going through Home to reach it
                // means a rotation each way for nothing.
                GhostAction(
                    label = "Lobby",
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    onClick = onLobby,
                )
                WyrmPrimaryAction(
                    label = "Play",
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp),
                    onClick = onPlay,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, arrival: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            alpha = arrival
            translationY = (1f - arrival) * 14.dp.toPx()
        },
    ) {
        Text(
            text = value,
            fontFamily = Wyrm.Display,
            fontSize = 30.sp,
            color = Wyrm.White,
        )
        Spacer(Modifier.height(3.dp))
        WyrmLabel(label)
    }
}

/** The quiet way out, so only one action on the card is loud. */
@Composable
private fun GhostAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(wyrmRounded(Wyrm.Pill))
            .background(glassFill())
            .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 2.2.sp,
            color = Wyrm.SoftWhite,
        )
    }
}

/** Seconds as m:ss, or h:mm:ss once a run has gone on long enough to deserve it. */
internal fun formatPlayTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remainder = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%d:%02d".format(minutes, remainder)
    }
}
