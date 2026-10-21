package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.wyrm.omrajput.data.ShedBeads
import com.wyrm.omrajput.data.ShedGenerator
import com.wyrm.omrajput.data.ShedRecipe
import com.wyrm.omrajput.data.ShedSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Shed (OM, 2026-10-15): Home › Shed, the skin generator page, ported from
 * the HTML prototype (Desktop/thinking-orbs/generator.html) one to one.
 *   rest: one W breathes in 3D in the middle; a floating Liquid Glass
 *         composer at the bottom holds the recipe (a sentence of tappable
 *         words) and Shed.
 *   Shed: the composer slides away, everything else dissolves into blur, the
 *         same W grows to the middle and its own dots become the snake's face
 *         (no cut) while the steps are read out; then it dissolves, the batch
 *         scrolls under the composer card by card (each snake drawn head first
 *         as it scrolls in) and the composer slides back up.
 *   A tapped card opens underneath with its code and Copy code.
 * The theme is the app's (Settings); nothing here is synced (OM): the recipe
 * and the skins already shown live in prefs "wyrm_shed" (WIPE on log out).
 */

private val ShedEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private const val STAGE_W = 380f
private const val STAGE_H = 296f
private const val COUNT_MIN = 1
private const val COUNT_MAX = 40

/** The steps read out under the W, one every 1.4 s over its whole journey. */
private val SHED_STEPS = listOf(
    0.0 to "Reading your recipe",
    1.4 to "Choosing colours that belong together",
    2.8 to "Weaving the bands",
    4.2 to "Fitting every arena · ${ShedGenerator.MAX_RUNS} runs",
    5.6 to "Letting go of look-alikes",
)

private val MOOD_TEXT = mapOf(
    "cute" to "Soft colours, candy pairs",
    "danger" to "Dark body, one glowing colour",
    "random" to "Anything goes, still matching",
    "pattern" to "Clean repeats you can spot",
    "unique" to "Rare mixes, stars, odd rhythms",
)
private val STYLE_TEXT = mapOf(
    "stripes" to "Equal bands, one after another",
    "thickThin" to "A wide band, a thin line",
    "ramp" to "Colour to colour and back",
    "rainbow" to "Colours in rainbow order",
    "pulse" to "A long body with a spark",
    "band" to "A body with a ringed band",
    "duo" to "Two colours, side by side",
    "fib" to "Bands grow 2, 3, 5, 8, 13",
    "weave" to "Two bands knitted with a line",
    "chaos" to "Random bands, no repeats",
    "starry" to "Plain bands with star beads",
)
private val SIZE_TEXT = mapOf("short" to "Narrow bands", "medium" to "Balanced bands", "long" to "Wide bands")

/* ------------------------------------------------------------- storage */

/** The recipe and the skins already shown, on this phone only (OM: not synced). */
private object ShedStore {
    const val FILE = "wyrm_shed"
    private const val HISTORY_MAX = 3000

    fun recipe(context: Context): ShedRecipe {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = ShedRecipe()
        return ShedRecipe(
            count = p.getInt("count", d.count).coerceIn(COUNT_MIN, COUNT_MAX),
            mood = p.getString("mood", d.mood)?.takeIf { it in ShedGenerator.MOODS } ?: d.mood,
            tone = p.getString("tone", d.tone)?.takeIf { it in setOf("auto", "light", "dark", "mix") } ?: d.tone,
            theme = p.getString("theme", "")?.takeIf { it.isEmpty() || it in ShedGenerator.THEMES } ?: "",
            colours = p.getString("colours", "").orEmpty().filter { ShedBeads.isOfficial(it) }.toList().distinct(),
            style = p.getString("style", d.style)?.takeIf { it == "auto" || it in ShedGenerator.STYLES } ?: d.style,
            size = p.getString("size", d.size)?.takeIf { it in ShedGenerator.SIZES } ?: d.size,
            symmetry = p.getString("symmetry", d.symmetry)?.takeIf { it in setOf("auto", "on", "off") } ?: d.symmetry,
            wildness = p.getFloat("wildness", d.wildness.toFloat()).toDouble().coerceIn(0.0, 1.0),
            seed = 1 + (Math.random() * 999999).toInt(),
        )
    }

    fun save(context: Context, r: ShedRecipe) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("count", r.count).putString("mood", r.mood).putString("tone", r.tone)
            .putString("theme", r.theme).putString("colours", r.colours.joinToString(""))
            .putString("style", r.style).putString("size", r.size).putString("symmetry", r.symmetry)
            .putFloat("wildness", r.wildness.toFloat()).apply()
    }

    fun history(context: Context): List<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("history", "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun saveHistory(context: Context, list: List<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("history", list.takeLast(HISTORY_MAX).joinToString("\n")).apply()
    }
}

private enum class ShedPhase { REST, RUN, LEAVING, RESULTS }
private enum class ShedSheet { COUNT, MOOD, TONE, SOURCE, STYLE, SIZE, TUNE }

/** Animate the content only: a blur layer must never wrap the glass surface. */
private class ShedMorphPart {
    val opacity = Animatable(1f)
    val blur = Animatable(0f)
    val scale = Animatable(1f)

    suspend fun dissolve() = coroutineScope {
        launch { opacity.animateTo(0f, tween(240)) }
        launch { blur.animateTo(20f, tween(240)) }
        launch { scale.animateTo(0.94f, tween(240)) }
    }

    suspend fun materialize(delayMs: Int) {
        opacity.snapTo(0f)
        blur.snapTo(20f)
        scale.snapTo(1.06f)
        delay(delayMs.toLong())
        coroutineScope {
            launch { opacity.animateTo(1f, tween(550, easing = ShedEase)) }
            launch { blur.animateTo(0f, tween(550, easing = ShedEase)) }
            launch { scale.animateTo(1f, tween(550, easing = ShedEase)) }
        }
    }
}

private fun Modifier.shedMorph(part: ShedMorphPart): Modifier =
    graphicsLayer {
        alpha = part.opacity.value
        scaleX = part.scale.value
        scaleY = part.scale.value
    }.blur(part.blur.value.dp)

/* ------------------------------------------------------------- the page */

@Composable
internal fun ShedScreen(
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val scope = rememberCoroutineScope()
    val textures by rememberSkinTextures()
    val clipboard = LocalClipboardManager.current
    val backdrop = rememberLayerBackdrop()

    var recipe by remember { mutableStateOf(ShedStore.recipe(context)) }
    fun setRecipe(r: ShedRecipe) { recipe = r; ShedStore.save(context, r) }
    var phase by remember { mutableStateOf(ShedPhase.REST) }
    val skins = remember { mutableStateListOf<ShedSkin>() }
    val shown = remember { mutableStateListOf<String>() } // cards that have already drawn in
    var openKey by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<ShedSheet?>(null) }
    var displayedSheet by remember { mutableStateOf<ShedSheet?>(null) }
    var morphing by remember { mutableStateOf(false) }
    val morphParts = remember { List(4) { ShedMorphPart() } }
    var busy by remember { mutableStateOf(false) }
    var composerAway by remember { mutableStateOf(false) }
    var sheds by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // the W's clock, its run and where it stands
    var clock by remember { mutableDoubleStateOf(0.0) }
    var runStart by remember { mutableStateOf<Double?>(null) }
    var stageOn by remember { mutableStateOf(true) }
    val stageY = remember { Animatable(0f) }
    val stageScale = remember { Animatable(0.68f) }
    val stageAlpha = remember { Animatable(0f) }
    var rootW by remember { mutableFloatStateOf(0f) }
    var rootH by remember { mutableFloatStateOf(0f) }
    var rootTop by remember { mutableFloatStateOf(0f) }
    var restY by remember { mutableFloatStateOf(-1f) }
    var restScale by remember { mutableFloatStateOf(0.68f) }
    var composerH by remember { mutableFloatStateOf(0f) }
    var measuredComposerH by remember { mutableFloatStateOf(0f) }

    fun closeSheet() { focus.clearFocus(); sheet = null }
    fun openSheet(value: ShedSheet) {
        if (!morphing && !busy && !composerAway && sheet == null) sheet = value
    }

    // Sequential, like morph() in the prototype: dissolve, swap, resize and
    // materialise. A close during the reveal cancels it and reverses smoothly.
    LaunchedEffect(sheet) {
        if (sheet == displayedSheet && !morphing) return@LaunchedEffect
        morphing = true
        coroutineScope { morphParts.forEach { launch { it.dissolve() } } }
        displayedSheet = sheet
        coroutineScope {
            morphParts.forEachIndexed { index, part ->
                launch { part.materialize(index * 40) }
            }
        }
        morphing = false
    }
    // Opening choices must not push the feed or the resting W upward. Publish
    // the new collapsed height only after the reverse morph has finished.
    LaunchedEffect(measuredComposerH, sheet, displayedSheet, morphing) {
        if (sheet == null && displayedSheet == null && !morphing) composerH = measuredComposerH
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { ShedW.prepare() }
        ready = true
    }
    LaunchedEffect(stageOn) {
        if (!stageOn) return@LaunchedEffect
        val t0 = withFrameNanos { it }
        val base = clock
        while (true) withFrameNanos { clock = base + (it - t0) / 1e9 }
    }
    // resting: the W sits in its slot; it fades in the first time
    LaunchedEffect(ready, restY, restScale, phase) {
        if (ready && phase == ShedPhase.REST && restY >= 0f) {
            stageY.snapTo(restY)
            stageScale.snapTo(restScale)
            if (stageAlpha.value < 1f) stageAlpha.animateTo(1f, tween(900, easing = ShedEase))
        }
    }
    androidx.activity.compose.BackHandler(enabled = sheet != null || displayedSheet != null || morphing) { closeSheet() }

    val veiled = phase == ShedPhase.RUN || phase == ShedPhase.LEAVING
    val veil by animateFloatAsState(if (veiled) 1f else 0f, tween(600, easing = ShedEase), label = "shed-veil")
    val away by animateFloatAsState(if (composerAway) 1f else 0f, tween(if (composerAway) 600 else 650, easing = ShedEase), label = "shed-composer")

    fun shed() {
        if (busy || !ready) return
        busy = true
        scope.launch {
            val history = ShedStore.history(context)
            val (batch, _) = withContext(Dispatchers.Default) { ShedGenerator.generate(recipe, history.toHashSet()) }
            ShedStore.saveHistory(context, history + batch.map { it.key })
            val centreY = rootH * 0.43f
            val centreScale = min(1f, rootW * 0.9f / with(density) { STAGE_W.dp.toPx() })
            val fromRest = phase == ShedPhase.REST
            composerAway = true
            phase = ShedPhase.RUN
            if (!fromRest) {
                // after a batch the W was away: it materialises in the middle
                stageY.snapTo(centreY)
                stageScale.snapTo(centreScale)
                stageAlpha.snapTo(0f)
                stageOn = true
                launch { stageAlpha.animateTo(1f, tween(700)) }
            }
            launch { stageY.animateTo(centreY, tween(1100, easing = ShedEase)) }
            launch { stageScale.animateTo(centreScale, tween(1100, easing = ShedEase)) }
            runStart = clock
            delay((ShedW.RUN * 1000).toLong())
            // the W (back as a W) and the last line dissolve
            phase = ShedPhase.LEAVING
            stageAlpha.animateTo(0f, tween(700))
            stageOn = false
            runStart = null
            skins.clear(); skins.addAll(batch)
            shown.clear()
            openKey = null
            listState.scrollToItem(0)
            phase = ShedPhase.RESULTS
            launch { delay(450); composerAway = false }
            setRecipe(recipe.copy(seed = (Integer.toUnsignedLong(ShedGenerator.mixSeed(recipe.seed, 77)) % 999999).toInt() + 1))
            sheds++
            busy = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Wyrm.Paper)
            .onGloballyPositioned { rootTop = it.positionInRoot().y; rootW = it.size.width.toFloat(); rootH = it.size.height.toFloat() },
    ) {
        // ---------------------------------------------------------- the page (what the glass sees)
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(Wyrm.Paper)
                .graphicsLayer { alpha = 1f - 0.75f * veil; scaleX = 1f - 0.04f * veil; scaleY = 1f - 0.04f * veil }
                .blur((16 * veil).dp)
                .padding(top = insetTop),
        ) {
            ShedHeader(onBack = onBack, sub = if (phase == ShedPhase.RESULTS || skins.isNotEmpty()) "${skins.size} skins · best first" else null)
            if (skins.isEmpty()) {
                // rest: the slot the W breathes in
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.padding(bottom = with(density) { composerH.toDp() } + 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(260.dp, 203.dp)
                                .onGloballyPositioned {
                                    restY = it.positionInRoot().y - rootTop + it.size.height / 2f
                                    restScale = it.size.width / with(density) { STAGE_W.dp.toPx() }
                                },
                        )
                        Text("What should your Wyrm shed into?", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Wyrm.Ink, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text("Write your recipe below, then shed.", fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = with(density) { composerH.toDp() } + 40.dp + insetBottom),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(skins, key = { _, s -> s.key }) { index, skin ->
                        ShedCard(
                            skin = skin,
                            index = index,
                            textures = textures,
                            open = openKey == skin.key,
                            alreadyShown = skin.key in shown,
                            delayMs = if (index < 5 && skin.key !in shown) 180 + index * 140 else 0,
                            onShown = { if (skin.key !in shown) shown.add(skin.key) },
                            onToggle = { openKey = if (openKey == skin.key) null else skin.key },
                            onCopy = { clipboard.setText(AnnotatedString(skin.code)) },
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------- the one W
        if (ready && stageOn) {
            val dark = Wyrm.currentPalette.dark
            val ink = Wyrm.Ink
            Canvas(
                Modifier
                    .size(STAGE_W.dp, STAGE_H.dp)
                    .graphicsLayer {
                        translationX = rootW / 2f - STAGE_W.dp.toPx() / 2f
                        translationY = stageY.value - STAGE_H.dp.toPx() / 2f
                        scaleX = stageScale.value
                        scaleY = stageScale.value
                        alpha = stageAlpha.value
                    }
                    .blur((20 * (1f - stageAlpha.value)).dp),
            ) {
                val run = runStart?.let { clock - it }
                // full 3D at rest; it straightens out as the W grows to the middle
                val depth = if (run == null) 1.0 else Math.pow(maxOf(0.0, 1 - run / 0.9), 2.0)
                val dots = ShedW.frame(STAGE_W, STAGE_H, clock, run, sheds % 2, depth)
                paintShedW(dots, ink, dark, density.density)
            }
        }

        // ---------------------------------------------------------- the steps under the W
        if (phase == ShedPhase.RUN) {
            val t = runStart?.let { clock - it } ?: 0.0
            val step = SHED_STEPS.indexOfLast { t >= it.first }.coerceAtLeast(0)
            val top = stageY.value + STAGE_H * density.density * stageScale.value / 2f + with(density) { 6.dp.toPx() }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(700, easing = ShedEase)) + scaleIn(tween(700, easing = ShedEase), initialScale = 1.06f)) togetherWith
                        (fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 0.94f))
                },
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, top.roundToInt()) },
                label = "shed-steps",
            ) { k ->
                Text(
                    SHED_STEPS[k].second,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Wyrm.Mute, textAlign = TextAlign.Center,
                )
            }
        }

        // Transparent outside catcher, above the W and below the composer.
        if (sheet != null || displayedSheet != null || morphing) {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::closeSheet,
            ))
        }

        // ---------------------------------------------------------- the floating composer
        // Slide/fade on an outer wrapper only. The rounded glass has no blur
        // layer around it, and its backdrop records opaque themed paper.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp + insetBottom)
                .widthIn(max = 416.dp)
                .fillMaxWidth()
                .offset { IntOffset(0, (away * (measuredComposerH + with(density) { 40.dp.toPx() })).roundToInt()) }
                .graphicsLayer { alpha = 1f - away },
        ) {
            Column(
                Modifier
                .fillMaxWidth()
                .onSizeChanged { measuredComposerH = it.height.toFloat() }
                .clip(wyrmRounded(30.dp))
                .liquidGlass(
                    backdrop = backdrop,
                    shape = wyrmRounded(30.dp),
                    tint = Wyrm.Card.copy(alpha = 0.55f),
                    blur = 6.dp,
                    refraction = 10.dp,
                    depth = 16.dp,
                    shadow = null,
                    fallback = Wyrm.Card,
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .animateContentSize(tween(600, easing = ShedEase), alignment = Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            ) {
                val showing = displayedSheet
                if (showing != null) {
                    ShedComposerChoices(
                        sheet = showing,
                        recipe = recipe,
                        textures = textures,
                        parts = morphParts,
                        maxBodyHeight = min(with(density) { rootH.toDp().value } * 0.56f, 520f).dp,
                        maxHeight = ((if (rootH > 0f) with(density) { rootH.toDp() } else screenHeight) - insetTop - insetBottom - 50.dp).coerceAtLeast(100.dp),
                        onRecipe = { if (sheet == showing) setRecipe(it) },
                        onClose = ::closeSheet,
                    )
                } else {
                Box(Modifier.shedMorph(morphParts[0])) { ShedSentence(recipe, textures, ::openSheet) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.shedMorph(morphParts[1]), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Wyrm.Ink.copy(alpha = 0.07f))
                            .clickable { openSheet(ShedSheet.TUNE) },
                        contentAlignment = Alignment.Center,
                    ) { SlidersIcon(Wyrm.Ink) }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.height(48.dp).clip(WyrmCapsule).background(Wyrm.Ink)
                            .clickable(enabled = !busy && !morphing && sheet == null && !composerAway) { shed() }
                            .padding(start = 24.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (sheds > 0) "Shed again" else "Shed", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Wyrm.OnInk)
                        Spacer(Modifier.width(8.dp))
                        ArrowIcon(Wyrm.OnInk)
                    }
                }
                }
            }
            if (composerAway) Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) {})
        }
    }
}

/* ------------------------------------------------------------- header */

@Composable
private fun ShedHeader(onBack: () -> Unit, sub: String?) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Box(
            Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape).background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { ChevronIcon(Wyrm.Ink) }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WYRM", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.6.sp, color = Wyrm.Quiet)
            Text("Shed", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.6).sp, color = Wyrm.Ink)
        }
        if (sub != null) Text(
            sub, modifier = Modifier.align(Alignment.BottomEnd),
            fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Wyrm.Quiet,
        )
    }
}

/* ------------------------------------------------------------- the recipe sentence */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShedSentence(recipe: ShedRecipe, textures: SkinTextures?, onOpen: (ShedSheet) -> Unit) {
    val sourceLabel: String
    val sourceBeads: List<Char>
    when {
        recipe.colours.isNotEmpty() -> { sourceLabel = "my ${recipe.colours.size}"; sourceBeads = recipe.colours }
        recipe.theme.isNotEmpty() -> { val t = ShedGenerator.THEMES.getValue(recipe.theme); sourceLabel = t.label.lowercase(); sourceBeads = t.chars.toList() }
        else -> { sourceLabel = "any colour"; sourceBeads = emptyList() }
    }
    FlowRow(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ShedWord(recipe.count.toString(), emptyList(), textures) { onOpen(ShedSheet.COUNT) }
        SentenceText(" ", Wyrm.Quiet, FontWeight.SemiBold)
        ShedWord(ShedGenerator.MOODS.getValue(recipe.mood).label.lowercase(), emptyList(), textures) { onOpen(ShedSheet.MOOD) }
        PlainWords(" skins in ")
        ShedWord(recipe.tone.let { if (it == "mix") "mixed" else it }, emptyList(), textures) { onOpen(ShedSheet.TONE) }
        PlainWords(" tones,")
        ShedWord(sourceLabel, sourceBeads, textures) { onOpen(ShedSheet.SOURCE) }
        SentenceText(", ", Wyrm.Quiet, FontWeight.SemiBold)
        ShedWord(if (recipe.style == "auto") "any" else ShedGenerator.STYLES.getValue(recipe.style).lowercase(), emptyList(), textures) { onOpen(ShedSheet.STYLE) }
        PlainWords(" style,")
        ShedWord(recipe.size, emptyList(), textures) { onOpen(ShedSheet.SIZE) }
        SentenceText(" bands.", Wyrm.Quiet, FontWeight.SemiBold)
    }
}

/** Plain words of the sentence, each its own piece so the line wraps between words. */
@Composable
private fun PlainWords(text: String) {
    for (w in text.split(' ').filter { it.isNotEmpty() }) SentenceText("$w ", Wyrm.Quiet, FontWeight.SemiBold)
}

@Composable
private fun SentenceText(text: String, color: Color, weight: FontWeight) {
    Text(text, fontFamily = Wyrm.Body, fontWeight = weight, fontSize = 19.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp, color = color)
}

/** A tappable word: bold, with a dashed ink underline (and its beads for colours). */
@Composable
private fun ShedWord(label: String, beads: List<Char>, textures: SkinTextures?, onTap: () -> Unit) {
    val line = Wyrm.Ink.copy(alpha = 0.35f)
    Row(
        Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .drawBehind {
                drawLine(
                    line, Offset(0f, size.height - 3.dp.toPx()), Offset(size.width, size.height - 3.dp.toPx()),
                    strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (beads.isNotEmpty()) {
            Row(Modifier.padding(end = 4.dp)) {
                beads.take(5).forEachIndexed { i, ch -> BeadDot(ch, textures, 17.dp, Modifier.offset(x = (-5 * i).dp)) }
            }
        }
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp, color = Wyrm.Ink)
    }
}

/** One official bead, drawn with the game's own art. */
@Composable
private fun BeadDot(ch: Char, textures: SkinTextures?, size: Dp, modifier: Modifier = Modifier) {
    val image = textures?.beads?.get(ShedBeads[ch].id)
    Canvas(modifier.size(size)) { if (image != null) drawImageInto(image, 0f, 0f, this.size.width, this.size.height) }
}

/* ------------------------------------------------------------- cards */

@Composable
private fun ShedCard(
    skin: ShedSkin,
    index: Int,
    textures: SkinTextures?,
    open: Boolean,
    alreadyShown: Boolean,
    delayMs: Int,
    onShown: () -> Unit,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val appear = remember { Animatable(if (alreadyShown) 1f else 0f) }
    val grow = remember { Animatable(if (alreadyShown) 1f else 0f) }
    LaunchedEffect(skin.key) {
        if (alreadyShown) return@LaunchedEffect
        delay(delayMs.toLong())
        onShown()
        launch { appear.animateTo(1f, tween(800, easing = ShedEase)) }
        delay(120)
        grow.animateTo(1f, tween(1100, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
    }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1400); copied = false } }

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 26.dp.toPx()
                val s = 0.97f + 0.03f * appear.value
                scaleX = s; scaleY = s
            }
            .blur((10 * (1f - appear.value)).dp)
            .clip(wyrmRounded(24.dp))
            .background(Wyrm.Card)
            .then(if (open) Modifier.border(2.dp, Wyrm.Ink, wyrmRounded(24.dp)) else Modifier),
    ) {
        Column(
            Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    modifier = Modifier.widthIn(min = 34.dp),
                    fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-1).sp,
                    color = if (open) Wyrm.Ink else Wyrm.Quiet,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${ShedGenerator.MOODS.getValue(skin.mood).label} ${ShedGenerator.STYLES.getValue(skin.style).lowercase()}",
                        fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Wyrm.Ink,
                    )
                    Text(
                        "${skin.colours.size} colours · ${skin.code.length} beads a pattern",
                        fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Quiet,
                    )
                }
                Row { skin.colours.forEachIndexed { i, ch -> BeadDot(ch, textures, 16.dp, Modifier.offset(x = (-5 * i).dp)) } }
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val bead = (maxWidth - 28.dp) / (1f + 127f * (8f / 48f))
                Canvas(
                    Modifier.fillMaxWidth().height(bead * 2.16f + 24.dp + 6.dp)
                        .clip(wyrmRounded(14.dp)).background(Wyrm.Paper),
                ) {
                    val t = textures ?: return@Canvas
                    drawShedSnake(t, skin.code, grow.value, density)
                }
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(500, easing = ShedEase)) + fadeIn(tween(400, delayMillis = 100)),
            exit = shrinkVertically(tween(400, easing = ShedEase)) + fadeOut(tween(200)),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                Text(
                    skin.code,
                    modifier = Modifier.fillMaxWidth().clip(wyrmRounded(14.dp)).background(Wyrm.Well).padding(horizontal = 12.dp, vertical = 10.dp),
                    fontFamily = Wyrm.Body, fontSize = 12.5.sp, lineHeight = 19.sp, color = Wyrm.Mute,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.height(32.dp).clip(WyrmCapsule).background(Wyrm.Well).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${skin.runs} / ${ShedGenerator.MAX_RUNS} runs · fits", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Wyrm.Mute)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.height(38.dp).clip(WyrmCapsule).background(Wyrm.Ink)
                            .clickable { onCopy(); copied = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CopyIcon(Wyrm.OnInk)
                        Spacer(Modifier.width(6.dp))
                        Text(if (copied) "Copied" else "Copy code", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Wyrm.OnInk)
                    }
                }
            }
        }
    }
}

/**
 * The Skin tab's preview, exactly (SkinPreview / drawSkinRows / drawSkinHead):
 * 128 beads a row, two rows, 8/48 of a bead apart, the head row turned half a
 * revolution, bead k (k = 0 the head) = code[k % length], drawn tail first so
 * nearer-the-head beads sit on top; eyes 12/29 of a bead, pupil 7/29, 2 units
 * forward. [grow] 0..1 lays the snake in from the head; the bead being laid
 * swells into place and the eyes come once the head bead is full size.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShedSnake(t: SkinTextures, code: String, grow: Float, px: Float) {
    val row = 128
    val w = size.width; val h = size.height
    val nativeSpan = 1f + (row - 1) * (8f / 48f)
    val scale = min((w - 28 * px) / nativeSpan, (h - 24 * px) / 2.16f)
    val step = 8f * (scale / 48f)
    val gap = scale * 0.16f
    val bodyWidth = scale + step * (row - 1)
    val x = w * 0.5f - bodyWidth * 0.5f
    val centreY = h * 0.48f
    val headY = centreY - scale * 0.5f - gap * 0.5f
    val tailY = centreY + scale * 0.5f + gap * 0.5f
    val total = row * 2
    val ids = code.map { ShedBeads[it].id }
    val done = grow >= 1f
    val laid = if (done) total.toFloat() else grow * total
    for (segment in 0 until total) {
        val codeIndex = total - 1 - segment
        if (codeIndex >= laid) continue
        val local = segment % row
        val headRow = segment >= row
        val slot = if (headRow) local else row - 1 - local
        val image = t.beads[ids[codeIndex % ids.size]] ?: continue
        val k = if (done) 1f else min(1f, (laid - codeIndex) / 6f)
        val size = scale * (0.35f + 0.65f * k)
        val cx = x + scale * 0.5f + slot * step
        val cy = if (headRow) headY else tailY
        if (headRow) rotate(180f, pivot = Offset(cx, cy)) { drawImageInto(image, cx - size / 2, cy - size / 2, size, size) }
        else drawImageInto(image, cx - size / 2, cy - size / 2, size, size)
    }
    if (laid < 6f) return
    val eye = t.beads[40] ?: return
    val hx = x + scale * 0.5f + step * (row - 1)
    val unit = scale / 29f
    val iris = 12 * unit
    val pupil = 7 * unit
    val cx = hx + 6 * unit
    for (side in 0 until 2) {
        val ey = if (side == 0) -6 * unit - 0.5f * px else 6 * unit
        drawImageInto(eye, cx - iris / 2, headY + ey - iris / 2, iris, iris, Color.White)
        val py = if (side == 0) -6 * unit else 6 * unit
        drawImageInto(eye, cx + 0.5f * px + 2 * unit - pupil / 2, headY + py - pupil / 2, pupil, pupil, Color.Black)
    }
}

/* ------------------------------------------------------------- sheets */

@Composable
private fun ShedComposerChoices(
    sheet: ShedSheet,
    recipe: ShedRecipe,
    textures: SkinTextures?,
    parts: List<ShedMorphPart>,
    maxBodyHeight: Dp,
    maxHeight: Dp,
    onRecipe: (ShedRecipe) -> Unit,
    onClose: () -> Unit,
) {
    val (title, sub) = when (sheet) {
        ShedSheet.COUNT -> "How many skins" to "From $COUNT_MIN to $COUNT_MAX. Every one is checked before you see it."
        ShedSheet.MOOD -> "Mood" to "Sets the colours, the style and how they repeat."
        ShedSheet.TONE -> "Tones" to "Light or dark beads. A theme or your own beads come first."
        ShedSheet.STYLE -> "Style" to "How the colours are laid along the body."
        ShedSheet.SIZE -> "Band width" to "How wide each colour band is."
        ShedSheet.SOURCE -> "Colours" to if (recipe.colours.isNotEmpty()) "${recipe.colours.size} picked · only these will be used" else "Any colour, a theme, or tap beads to use only yours."
        ShedSheet.TUNE -> "Fine tune" to "Small changes to how the skins come out."
    }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 2.dp)) {
        Text(title, modifier = Modifier.shedMorph(parts[0]), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.4).sp, color = Wyrm.Ink)
        Spacer(Modifier.height(4.dp))
        Text(sub, modifier = Modifier.shedMorph(parts[1]), fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Quiet)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.weight(1f, fill = false).heightIn(max = maxBodyHeight)
                .shedMorph(parts[2]).verticalScroll(rememberScrollState()).padding(bottom = 4.dp),
        ) {
            ShedSheetBody(sheet, recipe, textures, onRecipe, onClose)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().shedMorph(parts[3]).height(52.dp).clip(WyrmCapsule).background(Wyrm.Ink).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("Done", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Wyrm.OnInk) }
    }
}

private class Opt(val key: String, val label: String, val sub: String)

@Composable
private fun OptGrid(options: List<Opt>, selected: (String) -> Boolean, extra: @Composable (Opt) -> Unit = {}, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { o ->
                    val on = selected(o.key)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(wyrmRounded(18.dp))
                            .background(if (on) Wyrm.Card else Wyrm.Well)
                            .then(if (on) Modifier.border(2.dp, Wyrm.Ink, wyrmRounded(18.dp)) else Modifier)
                            .clickable { onPick(o.key) }
                            .padding(12.dp),
                    ) {
                        Text(o.label, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Wyrm.Ink)
                        if (o.sub.isNotEmpty()) Text(o.sub, fontFamily = Wyrm.Body, fontSize = 12.sp, lineHeight = 16.sp, color = Wyrm.Quiet)
                        extra(o)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text.uppercase(), fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.6.sp, color = Wyrm.Quiet)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ShedSheetBody(
    sheet: ShedSheet,
    recipe: ShedRecipe,
    textures: SkinTextures?,
    onRecipe: (ShedRecipe) -> Unit,
    onClose: () -> Unit,
) {
    when (sheet) {
        ShedSheet.COUNT -> {
            Text(
                "${recipe.count} ${if (recipe.count == 1) "skin" else "skins"}",
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-1.5).sp, color = Wyrm.Ink,
            )
            Spacer(Modifier.height(14.dp))
            LiquidSlider(
                value = recipe.count.toFloat(),
                onValueChange = { onRecipe(recipe.copy(count = it.roundToInt().coerceIn(COUNT_MIN, COUNT_MAX))) },
                valueRange = COUNT_MIN.toFloat()..COUNT_MAX.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("$COUNT_MIN", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Wyrm.Quiet)
                Spacer(Modifier.weight(1f))
                Text("$COUNT_MAX", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Wyrm.Quiet)
            }
        }
        ShedSheet.MOOD -> {
            OptGrid(ShedGenerator.MOODS.map { (k, m) -> Opt(k, m.label, MOOD_TEXT[k].orEmpty()) }, { it == recipe.mood }) { onRecipe(recipe.copy(mood = it)); onClose() }
        }
        ShedSheet.TONE -> {
            OptGrid(
                listOf(Opt("auto", "Auto", "The mood decides"), Opt("light", "Light", "Bright, airy beads"), Opt("dark", "Dark", "Deep, heavy beads"), Opt("mix", "Mixed", "Light and dark together")),
                { it == recipe.tone },
            ) { onRecipe(recipe.copy(tone = it)); onClose() }
        }
        ShedSheet.STYLE -> {
            OptGrid(
                listOf(Opt("auto", "Any", "The mood picks one")) + ShedGenerator.STYLES.map { (k, v) -> Opt(k, v, STYLE_TEXT[k].orEmpty()) },
                { it == recipe.style },
            ) { onRecipe(recipe.copy(style = it)); onClose() }
        }
        ShedSheet.SIZE -> {
            OptGrid(
                ShedGenerator.SIZES.map { (k, v) -> Opt(k, k.replaceFirstChar { it.uppercase() }, "${v[0]}–${v[1]} beads · ${SIZE_TEXT[k]}") },
                { it == recipe.size },
            ) { onRecipe(recipe.copy(size = it)); onClose() }
        }
        ShedSheet.SOURCE -> {
            OptGrid(listOf(Opt("any", "Any colour", "Matched for you")), { recipe.theme.isEmpty() && recipe.colours.isEmpty() }) {
                onRecipe(recipe.copy(theme = "", colours = emptyList())); onClose()
            }
            GroupTitle("Themes")
            OptGrid(
                ShedGenerator.THEMES.map { (k, t) -> Opt(k, t.label, "") },
                { recipe.colours.isEmpty() && it == recipe.theme },
                extra = { o ->
                    Row(Modifier.padding(top = 4.dp)) {
                        ShedGenerator.THEMES.getValue(o.key).chars.forEachIndexed { i, ch -> BeadDot(ch, textures, 16.dp, Modifier.offset(x = (-4 * i).dp)) }
                    }
                },
            ) { onRecipe(recipe.copy(theme = it, colours = emptyList())); onClose() }
            for ((title, test) in listOf<Pair<String, (Double) -> Boolean>>(
                "Light beads" to { it >= 0.62 }, "Mid beads" to { it >= 0.42 && it < 0.62 }, "Dark beads" to { it < 0.42 },
            )) {
                GroupTitle(title)
                val beads = ShedBeads.all.filter { test(it.L) }.sortedByDescending { it.L }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    beads.chunked(8).forEach { rowBeads ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowBeads.forEach { b ->
                                val on = b.ch in recipe.colours
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f)
                                        .then(if (on) Modifier.border(2.5.dp, Wyrm.Ink, CircleShape) else Modifier)
                                        .padding(3.dp)
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            val mine = if (on) recipe.colours - b.ch else recipe.colours + b.ch
                                            onRecipe(recipe.copy(colours = mine, theme = ""))
                                        },
                                ) {
                                    BeadDot(b.ch, textures, 40.dp, Modifier.fillMaxSize())
                                    Text(
                                        b.ch.toString(),
                                        modifier = Modifier.align(Alignment.BottomEnd).clip(wyrmRounded(6.dp)).background(Wyrm.Card).padding(horizontal = 3.dp),
                                        fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = Wyrm.Ink,
                                    )
                                }
                            }
                            repeat(8 - rowBeads.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        ShedSheet.TUNE -> {
            TuneRow("Symmetry") {
                PaperSegmented(
                    listOf("Auto", "On", "Off"),
                    listOf("auto", "on", "off").indexOf(recipe.symmetry).coerceAtLeast(0),
                    { onRecipe(recipe.copy(symmetry = listOf("auto", "on", "off")[it])) },
                    Modifier.width(190.dp),
                )
            }
            TuneRow("Wildness") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiquidSlider(
                        value = (recipe.wildness * 100).toFloat(),
                        onValueChange = { onRecipe(recipe.copy(wildness = (it.roundToInt() / 100.0).coerceIn(0.0, 1.0))) },
                        valueRange = 0f..100f,
                        modifier = Modifier.width(140.dp),
                    )
                    Text("${(recipe.wildness * 100).roundToInt()}", modifier = Modifier.width(34.dp), textAlign = TextAlign.End,
                        fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Wyrm.Quiet)
                }
            }
            TuneRow("Lucky number", last = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var text by remember(recipe.seed) { mutableStateOf(recipe.seed.toString()) }
                    BasicTextField(
                        value = text,
                        onValueChange = { v ->
                            text = v.filter { it.isDigit() }.take(9)
                            text.toIntOrNull()?.let { onRecipe(recipe.copy(seed = it)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Wyrm.Ink, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Wyrm.Ink),
                        modifier = Modifier.width(110.dp).height(36.dp).clip(wyrmRounded(12.dp)).background(Wyrm.Well).padding(top = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Wyrm.Ink.copy(alpha = 0.07f))
                            .clickable { onRecipe(recipe.copy(seed = 1 + (Math.random() * 999999).toInt())) },
                        contentAlignment = Alignment.Center,
                    ) { DiceIcon(Wyrm.Ink) }
                }
            }
        }
    }
}

@Composable
private fun TuneRow(title: String, last: Boolean = false, content: @Composable () -> Unit) {
    val rule = Wyrm.Rule
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (last) Modifier else Modifier.drawBehind { drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
        content()
    }
}

/* ------------------------------------------------------------- Home's card */

/**
 * Home › Shed: a quiet explanation and a small ink capsule, without a W.
 */
@Composable
internal fun ShedHomeCard(onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Shed", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Wyrm.Ink)
            Spacer(Modifier.height(4.dp))
            Text("Pick a mood and shed fresh skins. Every one fits the arena.", fontFamily = Wyrm.Body, fontSize = 13.sp, lineHeight = 18.sp, color = Wyrm.Quiet)
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.clip(WyrmCapsule).background(Wyrm.Ink).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text("Shed →", fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Wyrm.OnInk)
        }
    }
}

/* ------------------------------------------------------------- icons */

@Composable
private fun ChevronIcon(color: Color, flip: Boolean = false) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.width / 24f
        val p = Path().apply {
            if (flip) { moveTo(9 * s, 5 * s); lineTo(16 * s, 12 * s); lineTo(9 * s, 19 * s) }
            else { moveTo(15 * s, 5 * s); lineTo(8 * s, 12 * s); lineTo(15 * s, 19 * s) }
        }
        drawPath(p, color, style = Stroke(2.2f * s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun ArrowIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.width / 24f
        val st = Stroke(2.4f * s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawLine(color, Offset(5 * s, 12 * s), Offset(17 * s, 12 * s), 2.4f * s, StrokeCap.Round)
        drawPath(Path().apply { moveTo(13 * s, 6 * s); lineTo(19 * s, 12 * s); lineTo(13 * s, 18 * s) }, color, style = st)
    }
}

@Composable
private fun SlidersIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.width / 24f
        val w = 2f * s
        drawLine(color, Offset(4 * s, 7 * s), Offset(13.8f * s, 7 * s), w, StrokeCap.Round)
        drawLine(color, Offset(18.2f * s, 7 * s), Offset(20 * s, 7 * s), w, StrokeCap.Round)
        drawLine(color, Offset(4 * s, 17 * s), Offset(7.8f * s, 17 * s), w, StrokeCap.Round)
        drawLine(color, Offset(12.2f * s, 17 * s), Offset(20 * s, 17 * s), w, StrokeCap.Round)
        drawCircle(color, 2.2f * s, Offset(16 * s, 7 * s), style = Stroke(w))
        drawCircle(color, 2.2f * s, Offset(10 * s, 17 * s), style = Stroke(w))
    }
}

@Composable
private fun CopyIcon(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s = size.width / 24f
        val st = Stroke(2f * s)
        drawRoundRect(color, Offset(8 * s, 8 * s), androidx.compose.ui.geometry.Size(12 * s, 12 * s), androidx.compose.ui.geometry.CornerRadius(3 * s), style = st)
        drawPath(Path().apply { moveTo(16 * s, 8 * s); lineTo(16 * s, 6 * s); quadraticTo(16 * s, 4 * s, 14 * s, 4 * s); lineTo(6 * s, 4 * s); quadraticTo(4 * s, 4 * s, 4 * s, 6 * s); lineTo(4 * s, 14 * s); quadraticTo(4 * s, 16 * s, 6 * s, 16 * s); lineTo(8 * s, 16 * s) }, color, style = st)
    }
}

@Composable
private fun DiceIcon(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val s = size.width / 24f
        drawRoundRect(color, Offset(4 * s, 4 * s), androidx.compose.ui.geometry.Size(16 * s, 16 * s), androidx.compose.ui.geometry.CornerRadius(4 * s), style = Stroke(2f * s))
        for ((dx, dy) in listOf(9f to 9f, 15f to 15f, 15f to 9f, 9f to 15f)) drawCircle(color, 1.4f * s, Offset(dx * s, dy * s))
    }
}
