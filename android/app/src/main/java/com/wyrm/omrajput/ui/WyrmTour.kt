package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The app tour (OM, 2026-10-05): "Welcome to Wyrm", then a spotlight on each
 * main thing, the way apps introduce themselves after an install or an update.
 *
 * Standards it follows: one element lit at a time with the rest dimmed, a
 * short title and one or two compact sentences, "n of N" progress, Back /
 * Next, Skip always visible, shown once (an install, or the first update that
 * carries it), and replayable from Settings › Help & feedback.
 *
 * The tour walks the real app, not pictures of it: each step names a place
 * (Home, the Controls workspace on one of its tabs, the Settings tab) that
 * `WyrmOverlay.applyTourPlace` opens, and an anchor id that the real control
 * carries (`Modifier.tourAnchor`). The anchor reports its bounds and scrolls
 * itself into view when its step comes up. Taps on the dimmed app are held
 * while the tour runs, so it can never be pulled somewhere it did not expect.
 *
 * Wyrm iOS has the same steps, words and order: `WyrmTour.swift`.
 */

internal enum class TourPlace { HOME, CONTROLS, BUTTONS, ARENA_UI, SETTINGS }

internal data class TourStep(
    val place: TourPlace,
    /** The control lit by this step; null for the welcome and the last card. */
    val anchor: String?,
    val title: String,
    val body: String,
)

internal object WyrmTour {
    /** Raise to show the tour once more to everyone (a new tour). */
    const val VERSION = 1
    private const val PREFS = "wyrm_tour"
    private const val SEEN = "seen_version"

    val steps = listOf(
        TourStep(TourPlace.HOME, null, "Welcome to Wyrm",
            "A quick look at where everything is. It takes about a minute."),
        TourStep(TourPlace.HOME, "home.team", "Team mode",
            "Connect with your team here. It links you through NTL: teammates on your minimap, plus a team roster and team chat in the arena."),
        TourStep(TourPlace.HOME, "home.near", "Near Original",
            "Play like the original slither.io: its minimap, leaderboard, joystick, boost and arrow. Turn it off for Wyrm's own."),
        TourStep(TourPlace.HOME, "home.controls", "Controls",
            "How you play: steering, on-screen buttons and where everything sits in the arena."),
        TourStep(TourPlace.CONTROLS, "controls.preview", "Steering",
            "Choose Arrow or Joystick, how you boost, sizes, the arrow's look and movement, and the zoom bar. The preview shows it live."),
        TourStep(TourPlace.BUTTONS, "controls.tabs", "On-screen buttons",
            "Pick which buttons appear in the arena, like zoom, auto restart and chat, and how each one fires."),
        TourStep(TourPlace.ARENA_UI, "controls.tabs", "Arena UI",
            "Set the size of the minimap, the leaderboard and the stats text."),
        TourStep(TourPlace.ARENA_UI, "controls.arrange", "Arrange the layout",
            "Opens the arena editor, sideways like a match. Drag the joystick, boost, buttons, minimap, leaderboard, stats, team roster and chat where you want them, then Save."),
        TourStep(TourPlace.SETTINGS, "settings.arena", "Arena",
            "Display: scores, names, minimap and text sizes. Controls: steering, boost and the zoom bar. On-screen buttons: which ones show and how they fire."),
        TourStep(TourPlace.SETTINGS, "settings.help", "Playing help",
            "Modes: Normal or Assist, helper lines and arena colours. Bot: when it circles and how wide it swings."),
        TourStep(TourPlace.SETTINGS, "settings.performance", "Performance",
            "Auto: full speed while the phone is cool, slower when it warms up or Battery Saver is on. Balanced: a steady 60 FPS, cooler and easier on the battery. Performance: the highest frame rate and least delay; the phone runs warmer."),
        TourStep(TourPlace.SETTINGS, "settings.account", "Account",
            "Profile: name, username, photo and bio. Notifications: choose what reaches you. Privacy: who can reach you and what is stored."),
        TourStep(TourPlace.SETTINGS, "settings.support", "Help & feedback",
            "Report a problem, suggest an idea and read Wyrm's replies. You can replay this tour here too."),
        TourStep(TourPlace.HOME, null, "You're all set",
            "Jump in and play. You can replay this tour any time from Settings › Help & feedback."),
    )

    /** -1 while the tour is not running. */
    var step by mutableIntStateOf(-1)
        private set

    val active: Boolean get() = step >= 0
    val current: TourStep? get() = steps.getOrNull(step)
    /** The anchor that should scroll into view now. */
    val target: String? get() = current?.anchor

    /** The lit steps (everything but the welcome and the last card). */
    val spotlightCount: Int get() = steps.size - 2

    fun pending(context: Context): Boolean = prefs(context).getInt(SEEN, 0) < VERSION

    fun start() {
        TourAnchors.bounds.clear()
        step = 0
    }

    fun next(context: Context) {
        if (step >= steps.lastIndex) finish(context) else step += 1
    }

    fun back() {
        if (step > 0) step -= 1
    }

    /** Finished or skipped: seen on this phone for this tour version. */
    fun finish(context: Context) {
        prefs(context).edit().putInt(SEEN, VERSION).apply()
        step = -1
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Where each tour anchor is on screen, in the overlay's root coordinates. */
internal object TourAnchors {
    val bounds = mutableStateMapOf<String, Rect>()
}

/**
 * Marks a control the tour can light. It reports its bounds and, when its step
 * comes up, scrolls itself into view inside whatever scrolls around it.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tourAnchor(id: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val wanted = WyrmTour.active && WyrmTour.target == id
    LaunchedEffect(wanted) {
        if (!wanted) return@LaunchedEffect
        // Let a page that just slid in settle before scrolling it.
        delay(380)
        runCatching { requester.bringIntoView() }
    }
    DisposableEffect(id) { onDispose { TourAnchors.bounds.remove(id) } }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { TourAnchors.bounds[id] = it.boundsInRoot() }
}

/**
 * The tour over everything: the dim with its lit window, and the card. Taps
 * on the dim are held; only the card's buttons answer.
 */
@Composable
internal fun WyrmTourOverlay(
    insetTop: Dp,
    insetBottom: Dp,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = WyrmTour.current ?: return
    val index = WyrmTour.step
    val density = LocalDensity.current
    val anchor = step.anchor?.let { TourAnchors.bounds[it] }
    val pad = with(density) { 8.dp.toPx() }
    // The window glides from step to step; with nothing to light it closes to the middle.
    val hasHole = anchor != null && anchor.width > 1f && anchor.height > 1f
    val glide = tween<Float>(320, easing = FastOutSlowInEasing)
    val left by animateFloatAsState(if (hasHole) anchor!!.left - pad else 0f, glide, label = "tour-left")
    val top by animateFloatAsState(if (hasHole) anchor!!.top - pad else 0f, glide, label = "tour-top")
    val right by animateFloatAsState(if (hasHole) anchor!!.right + pad else 0f, glide, label = "tour-right")
    val bottom by animateFloatAsState(if (hasHole) anchor!!.bottom + pad else 0f, glide, label = "tour-bottom")
    val holeAlpha by animateFloatAsState(if (hasHole) 1f else 0f, tween(220), label = "tour-hole")
    val pulse = rememberInfiniteTransition(label = "tour-pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "tour-ring")
    val dim = Color.Black.copy(alpha = if (step.anchor == null) 0.55f else 0.62f)

    Box(
        Modifier
            .fillMaxSize()
            // Hold every touch on the app beneath while the tour runs.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(dim)
            if (holeAlpha > 0.01f && right > left && bottom > top) {
                val corner = CornerRadius(16.dp.toPx())
                val origin = Offset(left, top)
                val box = Size(right - left, bottom - top)
                drawRoundRect(Color.Black.copy(alpha = holeAlpha), origin, box, corner, blendMode = BlendMode.Clear)
                drawRoundRect(Color.White.copy(alpha = 0.9f * holeAlpha), origin, box, corner, style = Stroke(2.dp.toPx()))
                // A soft ring breathing out of the window: "this one".
                val grow = 10.dp.toPx() * ring
                drawRoundRect(
                    Color.White.copy(alpha = 0.45f * (1f - ring) * holeAlpha),
                    Offset(left - grow, top - grow), Size(box.width + grow * 2, box.height + grow * 2),
                    CornerRadius(16.dp.toPx() + grow), style = Stroke(2.dp.toPx()),
                )
            }
        }
        val hole = if (hasHole) Rect(left, top, right, bottom) else null
        TourCardPlacement(hole = hole, insetTop = insetTop, insetBottom = insetBottom) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 10 }) togetherWith fadeOut(tween(140))
                },
                label = "tour-card",
            ) { shown ->
                val shownStep = WyrmTour.steps.getOrNull(shown) ?: return@AnimatedContent
                when (shown) {
                    0 -> TourWelcomeCard(shownStep, onStart = onNext, onSkip = onSkip)
                    WyrmTour.steps.lastIndex -> TourDoneCard(shownStep, onDone = onNext, onBack = onBack)
                    else -> TourStepCard(shownStep, number = shown, total = WyrmTour.spotlightCount,
                        onNext = onNext, onBack = onBack, onSkip = onSkip)
                }
            }
        }
    }
}

/**
 * Puts the card where it never covers the lit window: under it when the window
 * is in the top half, over it otherwise, in the middle when nothing is lit.
 */
@Composable
private fun TourCardPlacement(hole: Rect?, insetTop: Dp, insetBottom: Dp, card: @Composable () -> Unit) {
    val density = LocalDensity.current
    Layout(content = { Box(Modifier.widthIn(max = 420.dp)) { card() } }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val side = 16.dp.roundToPx()
        val gap = 14.dp.roundToPx()
        val topLimit = with(density) { insetTop.roundToPx() } + 12.dp.roundToPx()
        val bottomLimit = constraints.maxHeight - with(density) { insetBottom.roundToPx() } - 12.dp.roundToPx()
        val width = min(constraints.maxWidth - side * 2, 420.dp.roundToPx()).coerceAtLeast(0)
        val placeable = measurables.first().measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = 0))
        val x = (constraints.maxWidth - placeable.width) / 2
        val y = if (hole == null) {
            (constraints.maxHeight - placeable.height) / 2
        } else {
            val below = hole.bottom.roundToInt() + gap
            val above = hole.top.roundToInt() - gap - placeable.height
            val roomBelow = bottomLimit - below - placeable.height
            val roomAbove = above - topLimit
            when {
                hole.center.y < constraints.maxHeight / 2f && roomBelow >= 0 -> below
                roomAbove >= 0 -> above
                roomBelow >= 0 -> below
                // A window taller than the screen allows: the card sits at the bottom over its edge.
                else -> bottomLimit - placeable.height
            }
        }
        val clamped = y.coerceIn(topLimit, max(topLimit, bottomLimit - placeable.height))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, clamped) }
    }
}

@Composable
private fun TourCardSurface(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, wyrmRounded(22.dp), clip = false)
            .clip(wyrmRounded(22.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(22.dp))
            // The card itself answers taps, so a tap on it never reaches the dim.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun TourWelcomeCard(step: TourStep, onStart: () -> Unit, onSkip: () -> Unit) {
    TourCardSurface {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            WyrmMark(size = 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(step.title, fontFamily = Wyrm.Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp,
                color = Wyrm.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(step.body, fontFamily = Wyrm.Body, fontSize = 14.5.sp, lineHeight = 20.sp, color = Wyrm.Mute,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            TourPrimaryButton("Show me around", Modifier.fillMaxWidth(), onStart)
            Spacer(Modifier.height(4.dp))
            TourTextButton("Skip for now", onSkip)
        }
    }
}

@Composable
private fun TourDoneCard(step: TourStep, onDone: () -> Unit, onBack: () -> Unit) {
    TourCardSurface {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            WyrmMark(size = 52.dp)
            Spacer(Modifier.height(12.dp))
            Text(step.title, fontFamily = Wyrm.Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
                color = Wyrm.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(step.body, fontFamily = Wyrm.Body, fontSize = 14.sp, lineHeight = 20.sp, color = Wyrm.Mute,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            TourPrimaryButton("Let's play", Modifier.fillMaxWidth(), onDone)
            Spacer(Modifier.height(4.dp))
            TourTextButton("Back", onBack)
        }
    }
}

@Composable
private fun TourStepCard(
    step: TourStep,
    number: Int,
    total: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    TourCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number of $total", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                letterSpacing = 0.4.sp, color = Wyrm.Quiet)
            Spacer(Modifier.width(10.dp))
            TourBeads(number, total, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            TourTextButton("Skip", onSkip)
        }
        Spacer(Modifier.height(8.dp))
        Text(step.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Wyrm.Ink)
        Spacer(Modifier.height(5.dp))
        Text(step.body, fontFamily = Wyrm.Body, fontSize = 14.sp, lineHeight = 19.5.sp, color = Wyrm.Mute)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (number > 1) TourTextButton("Back", onBack)
            Spacer(Modifier.weight(1f))
            TourPrimaryButton("Next", Modifier, onNext)
        }
    }
}

/** Progress as a row of beads, the lit ones in the theme's live colour. */
@Composable
private fun TourBeads(number: Int, total: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            Box(Modifier.size(if (i == number) 8.dp else 6.dp).clip(CircleShape)
                .background(if (i <= number) Wyrm.Live else Wyrm.Rule))
        }
    }
}

@Composable
private fun TourPrimaryButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(Wyrm.Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Wyrm.OnInk) }
}

@Composable
private fun TourTextButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Wyrm.Mute) }
}
