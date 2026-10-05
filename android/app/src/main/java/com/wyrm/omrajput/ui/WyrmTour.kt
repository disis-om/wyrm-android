package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
 * `WyrmOverlay.applyTourPlace` opens with the app's own page animations, and
 * an anchor id that the real control carries (`Modifier.tourAnchor`). The
 * anchor reports its bounds and scrolls itself well into view (clear of the
 * tab bar) when its step comes up.
 *
 * Smoothness (OM, 2026-10-06: the card flickered, jumping from top to bottom):
 * on Next the card fades away, the lit window glides from where it was to the
 * new control (it stays on the old one until the new one has been laid out),
 * and the new card only appears once that control has stopped moving, already
 * in its final place. The tour arrives and leaves cinematically: the dim
 * fades, the W draws itself, the card rises in; at the end everything eases
 * out together.
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
            "How you play. Three tabs inside: Controls, On-screen buttons and Arena UI."),
        TourStep(TourPlace.CONTROLS, "controls.tabs", "Controls",
            "Steer with the Arrow or the Joystick, choose how you boost, and set sizes, the arrow's look and movement, and the zoom bar."),
        TourStep(TourPlace.BUTTONS, "controls.tabs", "On-screen buttons",
            "Pick which buttons appear in the arena, like zoom, auto restart and chat, and how each one fires."),
        TourStep(TourPlace.ARENA_UI, "controls.tabs", "Arena UI",
            "Size the minimap, the leaderboard and the stats text. Arrange arena UI moves them, the team roster and chat anywhere."),
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

    /** Finishing: the tour is easing out; it ends when the overlay is gone. */
    var closing by mutableStateOf(false)
        private set

    val active: Boolean get() = step >= 0
    val current: TourStep? get() = steps.getOrNull(step)
    /** The anchor that should scroll into view now. */
    val target: String? get() = current?.anchor

    /** The lit steps (everything but the welcome and the last card). */
    val spotlightCount: Int get() = steps.size - 2

    fun pending(context: Context): Boolean = prefs(context).getInt(SEEN, 0) < VERSION

    fun start() {
        closing = false
        step = 0
    }

    fun next(context: Context) {
        if (closing) return
        if (step >= steps.lastIndex) finish(context) else step += 1
    }

    fun back() {
        if (closing) return
        if (step > 0) step -= 1
    }

    /** Finished or skipped: seen on this phone for this tour version; the overlay eases out, then [end]. */
    fun finish(context: Context) {
        prefs(context).edit().putInt(SEEN, VERSION).apply()
        if (active) closing = true
    }

    /** Called by the overlay once it has eased out. */
    fun end() {
        step = -1
        closing = false
    }

    /**
     * The window for step [index]: its own control once laid out; until then
     * the last lit control before it, so the window glides instead of blinking.
     */
    fun holeFor(index: Int): Rect? {
        val anchor = steps.getOrNull(index)?.anchor ?: return null
        TourAnchors.bounds[anchor]?.let { return it }
        for (i in index - 1 downTo 0) {
            val earlier = steps[i].anchor ?: continue
            TourAnchors.bounds[earlier]?.let { return it }
        }
        return null
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Where each tour anchor is on screen, in the overlay's root coordinates. */
internal object TourAnchors {
    val bounds = mutableStateMapOf<String, Rect>()
}

/**
 * Marks a control the tour can light. It reports its bounds and, when its step
 * comes up, scrolls itself well into view: clear of the top and of the tab bar
 * at the bottom, centred when the page can scroll that far, at the bottom when
 * it cannot.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tourAnchor(id: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val screen = LocalConfiguration.current.screenHeightDp.dp
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val wanted = WyrmTour.active && !WyrmTour.closing && WyrmTour.target == id
    LaunchedEffect(wanted) {
        if (!wanted) return@LaunchedEffect
        delay(160)
        // A rect reaching well above and below the control: bringing it into
        // view leaves the control near the middle of what is visible.
        val reach = with(density) { (screen * 0.3f).toPx() }
        runCatching {
            requester.bringIntoView(Rect(0f, -reach, size.width, size.height + reach))
        }
    }
    // While the tour runs a control that left keeps its last bounds, so the
    // window can glide from it; outside the tour it is forgotten.
    DisposableEffect(id) { onDispose { if (!WyrmTour.active) TourAnchors.bounds.remove(id) } }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned {
            size = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
            TourAnchors.bounds[id] = it.boundsInRoot()
        }
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
    val index = WyrmTour.step
    if (index < 0) return
    val density = LocalDensity.current

    // Cinematic arrival and leaving: the whole tour fades and settles in, and out.
    val presence = remember { Animatable(0f) }
    LaunchedEffect(WyrmTour.closing) {
        if (WyrmTour.closing) {
            presence.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
            WyrmTour.end()
        } else {
            presence.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    // The card on screen, and how far it has come in. A new step's card only
    // appears once its control has stopped moving (page changes and scrolls).
    var shownIndex by remember { mutableIntStateOf(index) }
    val cardIn = remember { Animatable(0f) }
    LaunchedEffect(index) {
        if (cardIn.value > 0f && shownIndex != index) cardIn.animateTo(0f, tween(150))
        val before = WyrmTour.steps.getOrNull(shownIndex)?.place
        val step = WyrmTour.steps[index]
        val minWait = when {
            index == 0 -> 420L
            before != step.place -> 720L
            else -> 260L
        }
        val started = System.currentTimeMillis()
        var last: Rect? = null
        var still = 0
        while (true) {
            val now = step.anchor?.let { TourAnchors.bounds[it] }
            val settled = step.anchor == null || (now != null && last != null &&
                abs(now.left - last.left) < 0.5f && abs(now.top - last.top) < 0.5f &&
                abs(now.width - last.width) < 0.5f && abs(now.height - last.height) < 0.5f)
            still = if (settled) still + 1 else 0
            last = now
            val waited = System.currentTimeMillis() - started
            if ((waited >= minWait && still >= 4) || waited > 2600) break
            delay(50)
        }
        shownIndex = index
        // Two frames for the new card to be measured and placed before it shows.
        withFrameNanos { }
        withFrameNanos { }
        cardIn.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }

    val pad = with(density) { 8.dp.toPx() }
    val glide = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Hold every touch on the app beneath while the tour runs.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val hole = WyrmTour.holeFor(index)
        val hasHole = hole != null && hole.width > 1f && hole.height > 1f
        // Nothing lit: the window closes to the middle of the screen.
        val left by animateFloatAsState(if (hasHole) hole!!.left - pad else width / 2f, glide, label = "tour-left")
        val top by animateFloatAsState(if (hasHole) hole!!.top - pad else height / 2f, glide, label = "tour-top")
        val right by animateFloatAsState(if (hasHole) hole!!.right + pad else width / 2f, glide, label = "tour-right")
        val bottom by animateFloatAsState(if (hasHole) hole!!.bottom + pad else height / 2f, glide, label = "tour-bottom")
        val ringAlpha by animateFloatAsState(if (hasHole) 1f else 0f, tween(260), label = "tour-ring-alpha")
        val dimLevel by animateFloatAsState(if (WyrmTour.steps[index].anchor == null) 0.58f else 0.64f, tween(300), label = "tour-dim")
        val pulse = rememberInfiniteTransition(label = "tour-pulse")
        val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "tour-ring")
        val p = presence.value

        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = dimLevel * p))
            if (right - left > 1f && bottom - top > 1f) {
                val corner = CornerRadius(16.dp.toPx())
                val origin = Offset(left, top)
                val box = Size(right - left, bottom - top)
                drawRoundRect(Color.Black, origin, box, corner, blendMode = BlendMode.Clear)
                val edge = ringAlpha * p
                if (edge > 0.01f) {
                    drawRoundRect(Color.White.copy(alpha = 0.9f * edge), origin, box, corner, style = Stroke(2.dp.toPx()))
                    // A soft ring breathing out of the window: "this one".
                    val grow = 10.dp.toPx() * ring
                    drawRoundRect(
                        Color.White.copy(alpha = 0.45f * (1f - ring) * edge),
                        Offset(left - grow, top - grow), Size(box.width + grow * 2, box.height + grow * 2),
                        CornerRadius(16.dp.toPx() + grow), style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }

        // The card: placed for the step it shows, never over its window.
        val shownHole = WyrmTour.holeFor(shownIndex)?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
            ?.takeIf { WyrmTour.steps.getOrNull(shownIndex)?.anchor != null }
        var cardHeight by remember { mutableIntStateOf(0) }
        val side = with(density) { 16.dp.toPx() }
        val cardWidth = min(width - side * 2, with(density) { 420.dp.toPx() })
        val topLimit = with(density) { (insetTop + 12.dp).toPx() }
        val bottomLimit = height - with(density) { (insetBottom + 12.dp).toPx() }
        val targetY = cardY(shownHole, cardHeight.toFloat(), height, topLimit, bottomLimit, with(density) { 14.dp.toPx() })
        val y = remember { Animatable(targetY) }
        LaunchedEffect(targetY) {
            // Hidden: jump; showing: glide (a control that still nudges a little).
            if (cardIn.value < 0.05f) y.snapTo(targetY) else y.animateTo(targetY, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
        }
        val c = cardIn.value
        val shownStep = WyrmTour.steps.getOrNull(shownIndex) ?: return@BoxWithConstraints
        val intro = shownIndex == 0
        Box(
            Modifier
                .offset { IntOffset(((width - cardWidth) / 2f).roundToInt(), y.value.roundToInt()) }
                .width(with(density) { cardWidth.toDp() })
                .onSizeChanged { cardHeight = it.height }
                .graphicsLayer {
                    alpha = c * p
                    // The welcome rises bigger and slower; every card settles from a little below.
                    val s = if (intro) 0.86f + 0.14f * c else 0.96f + 0.04f * c
                    scaleX = s * (0.94f + 0.06f * p)
                    scaleY = s * (0.94f + 0.06f * p)
                    translationY = (1f - c) * 18.dp.toPx() + (1f - p) * 14.dp.toPx()
                },
        ) {
            when (shownIndex) {
                0 -> TourWelcomeCard(shownStep, drawn = c, onStart = onNext, onSkip = onSkip)
                WyrmTour.steps.lastIndex -> TourDoneCard(shownStep, onDone = onNext, onBack = onBack)
                else -> TourStepCard(shownStep, number = shownIndex, total = WyrmTour.spotlightCount,
                    onNext = onNext, onBack = onBack, onSkip = onSkip)
            }
        }
    }
}

/** Under the window when it is in the top half, over it otherwise, centred when nothing is lit. */
private fun cardY(hole: Rect?, cardHeight: Float, height: Float, topLimit: Float, bottomLimit: Float, gap: Float): Float {
    val h = max(cardHeight, 1f)
    if (hole == null) return (height - h) / 2f
    val below = hole.bottom + gap
    val above = hole.top - gap - h
    val roomBelow = bottomLimit - below - h
    val roomAbove = above - topLimit
    val y = when {
        hole.center.y < height / 2f && roomBelow >= 0f -> below
        roomAbove >= 0f -> above
        roomBelow >= 0f -> below
        else -> bottomLimit - h
    }
    return y.coerceIn(topLimit, max(topLimit, bottomLimit - h))
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
private fun TourWelcomeCard(step: TourStep, drawn: Float, onStart: () -> Unit, onSkip: () -> Unit) {
    // The W draws itself as the welcome arrives.
    val fill by animateFloatAsState(if (drawn > 0.5f) 1f else 0f, tween(1100, easing = FastOutSlowInEasing), label = "tour-mark")
    TourCardSurface {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            WyrmMark(size = 64.dp, fill = fill)
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
