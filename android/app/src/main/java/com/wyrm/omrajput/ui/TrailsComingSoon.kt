package com.wyrm.omrajput.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Trails while it is switched off (OM, 2026-10-02; TRAILS_ENABLED = false).
 * The Social card still looks the same and still opens a page: this one, which
 * says plainly that Trails is being finished and what it will bring. iOS twin:
 * `WyrmTrailsComingSoon` in WyrmTrails.swift (same copy).
 */
@Composable
internal fun TrailsComingSoonScreen(insetTop: Dp, insetBottom: Dp, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        Column(Modifier.fillMaxSize().padding(top = insetTop)) {
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                Row(
                    Modifier.align(Alignment.CenterStart)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IosIcon(IosGlyph.CHEVRON_LEFT, Wyrm.Link, size = 16.dp, semibold = true)
                    Text("Back", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Wyrm.Link)
                }
                Text("Trails", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center))
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = insetBottom + 28.dp),
            ) {
                Spacer(Modifier.height(6.dp))
                TrailsComingSoonHero()
                Spacer(Modifier.height(22.dp))
                WyrmLabel("In development", color = Wyrm.Quiet)
                Spacer(Modifier.height(6.dp))
                Text("Trails is still growing.", fontFamily = Wyrm.Display, fontSize = 34.sp, lineHeight = 38.sp, color = Wyrm.Ink)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Trails is where your best runs will live: the moment, the skin and the numbers, shared with " +
                        "everyone who plays Wyrm. We've switched it off for a little while so we can finish it " +
                        "properly instead of handing you something half-baked.",
                    fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 22.sp, color = Wyrm.Mute,
                )

                Spacer(Modifier.height(24.dp))
                WyrmLabel("Where it is", color = Wyrm.Quiet)
                Spacer(Modifier.height(10.dp))
                TrailsStages()

                Spacer(Modifier.height(26.dp))
                WyrmLabel("What's coming", color = Wyrm.Quiet)
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(wyrmRounded(18.dp))
                        .background(Wyrm.Card)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(18.dp)),
                ) {
                    TrailsComingRow(LucideR.drawable.lucide_ic_share_2, "Share a run from the lobby",
                        "Your score, kills and time, right on top of the moment you went down.")
                    TrailsComingRule()
                    TrailsComingRow(LucideR.drawable.lucide_ic_shirt, "Show off your skin",
                        "Anyone who likes it can try it on with one tap before they wear it.")
                    TrailsComingRule()
                    TrailsComingRow(LucideR.drawable.lucide_ic_sparkles, "Beads and replies",
                        "Drop a bead on a run you loved, and talk about it underneath.")
                    TrailsComingRule()
                    TrailsComingRow(LucideR.drawable.lucide_ic_grid_3x3, "Your trails on your profile",
                        "Every run you share, in one grid, so your profile tells your story.")
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(wyrmRounded(18.dp))
                        .background(Wyrm.Well)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(LucideR.drawable.lucide_ic_bell), contentDescription = null, tint = Wyrm.Ink,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("We'll tell you the moment it's ready.", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = Wyrm.Ink)
                        Text("Until then, go make a run worth sharing.", fontFamily = Wyrm.Body, fontSize = 13.sp,
                            color = Wyrm.Mute)
                    }
                }
            }
        }
    }
}

/** A snake of beads gliding along a dotted trail, forever. */
@Composable
private fun TrailsComingSoonHero() {
    val t by rememberInfiniteTransition(label = "trails-soon").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "trails-soon-t",
    )
    val well = Wyrm.Well
    val ink = Wyrm.Ink
    val quiet = Wyrm.Quiet
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(wyrmRounded(22.dp))
            .background(well),
    ) {
        val w = size.width
        val h = size.height
        fun at(p: Float): Offset {
            val x = -0.1f * w + p * 1.2f * w
            val y = h * 0.52f + sin(p * 2f * PI.toFloat() * 1.35f) * h * 0.22f
            return Offset(x, y)
        }
        // The trail: a dotted line the snake has drawn.
        val path = androidx.compose.ui.graphics.Path()
        val steps = 80
        for (i in 0..steps) {
            val p = at(i / steps.toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path, quiet.copy(alpha = 0.45f),
            style = Stroke(
                width = 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 9.dp.toPx())),
            ),
        )
        val bead = h * 0.07f
        val head = t * 1.0f
        for (i in 11 downTo 0) {
            val p = head - i * 0.022f
            if (p < -0.05f) continue
            val c = at(p)
            val alpha = 1f - i / 14f
            drawCircle(ink.copy(alpha = alpha), bead * (1f - i * 0.025f), c)
        }
        // Eyes on the head, looking where it goes.
        val c = at(head)
        val ahead = at(head + 0.01f)
        val dx = ahead.x - c.x
        val dy = ahead.y - c.y
        val len = kotlin.math.max(0.001f, kotlin.math.sqrt(dx * dx + dy * dy))
        val nx = dx / len
        val ny = dy / len
        for (side in listOf(-1f, 1f)) {
            val e = Offset(c.x + nx * bead * 0.35f - ny * side * bead * 0.42f, c.y + ny * bead * 0.35f + nx * side * bead * 0.42f)
            drawCircle(well, bead * 0.26f, e)
        }
        // A few soft food pellets ahead, the arena hint.
        for (k in 0 until 5) {
            val a = (k * 72f + 18f) * PI.toFloat() / 180f
            val f = Offset(w * (0.15f + 0.18f * k), h * (0.2f + 0.12f * cos(a)))
            drawCircle(quiet.copy(alpha = 0.22f), 3.dp.toPx(), f)
        }
    }
}

/** Sketched ✓ · Built ✓ · Polishing (now) · In your hands. */
@Composable
private fun TrailsStages() {
    val pulse by rememberInfiniteTransition(label = "trails-stage").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "trails-stage-pulse",
    )
    val stages = listOf("Sketched" to 2, "Built" to 2, "Polishing" to 1, "In your hands" to 0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((name, state) in stages) {
            val shape = wyrmRounded(Wyrm.Pill)
            Row(
                Modifier
                    .weight(if (name == "In your hands") 1.35f else 1f)
                    .clip(shape)
                    .background(if (state == 2) Wyrm.Ink else Wyrm.Card)
                    .border(1.dp, if (state == 1) Wyrm.Ink else Wyrm.Rule, shape)
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    2 -> Icon(painterResource(LucideR.drawable.lucide_ic_check), contentDescription = null,
                        tint = Wyrm.OnInk, modifier = Modifier.size(12.dp))
                    1 -> Box(Modifier.size(7.dp).clip(CircleShape).background(Wyrm.Ink.copy(alpha = pulse)))
                }
                if (state != 0) Spacer(Modifier.width(5.dp))
                Text(
                    name,
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = when (state) { 2 -> Wyrm.OnInk; 1 -> Wyrm.Ink; else -> Wyrm.Quiet },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TrailsComingRow(icon: Int, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(36.dp).clip(wyrmRounded(10.dp)).background(Wyrm.Well),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Wyrm.Ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Wyrm.Ink)
            Spacer(Modifier.height(2.dp))
            Text(detail, fontFamily = Wyrm.Body, fontSize = 13.sp, lineHeight = 18.sp, color = Wyrm.Mute)
        }
    }
}

@Composable
private fun TrailsComingRule() {
    Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
}
