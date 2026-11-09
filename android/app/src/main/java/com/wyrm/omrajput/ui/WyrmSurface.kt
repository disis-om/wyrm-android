package com.wyrm.omrajput.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import com.wyrm.omrajput.i18n.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.R

data class WyrmPalette(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val onInk: Color,
    val quiet: Color,
    val mute: Color,
    val rule: Color,
    val rowRule: Color,
    val live: Color,
    val link: Color,
    val tabIdle: Color,
    val tabBar: Color,
    val chevron: Color,
    val badge: Color,
    val well: Color,
    val track: Color,
    val hover: Color,
    val dark: Boolean,
    val hero: WyrmHero = WyrmHero(paper, ink, quiet, mute, rule, well, paper, paper),
    val hero2: WyrmHero = WyrmHero(paper, ink, quiet, mute, rule, well, paper, paper),
)

/* WyrmThemeId, the 22 themes and their stored colours live in WyrmThemeTables.kt. */

private fun Color.mixKeepAlpha(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * t,
        green = green + (target.green - green) * t,
        blue = blue + (target.blue - blue) * t,
        alpha = alpha,
    )
}

private fun Color.chroma(intensity: Float): Color {
    val m = intensity * 2f
    val l = red * 0.2126f + green * 0.7152f + blue * 0.0722f
    fun f(v: Float) = (l + (v - l) * m).coerceIn(0f, 1f)
    return Color(f(red), f(green), f(blue), alpha)
}

private fun contrastRatio(background: Color, foreground: Color): Float {
    val lighter = maxOf(background.luminance(), foreground.luminance())
    val darker = minOf(background.luminance(), foreground.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Walk the colour toward black or white until it clears [minimum] on every background. */
private fun Color.ensureAgainst(backgrounds: List<Color>, minimum: Float): Color {
    fun worst(c: Color) = backgrounds.minOf { contrastRatio(it, c) }
    if (worst(this) >= minimum) return this
    val black = Color.Black
    val white = Color.White
    val target = if (worst(black) >= worst(white)) black else white
    var best = this
    var bestW = worst(this)
    var t = 0.04f
    while (t <= 1.001f) {
        val mixed = mixKeepAlpha(target, t)
        val w = worst(mixed)
        if (w >= minimum) return mixed
        if (w > bestW) {
            best = mixed
            bestW = w
        }
        t += 0.04f
    }
    return best
}

/** Move a surface toward paper until ink on it clears 4.5:1. */
private fun Color.separateToward(paper: Color, ink: Color): Color {
    if (contrastRatio(this, ink) >= 4.5f) return this
    var best = this
    var bestR = contrastRatio(this, ink)
    var t = 0.04f
    while (t <= 1.001f) {
        val mixed = mixKeepAlpha(paper, t)
        val r = contrastRatio(mixed, ink)
        if (r >= 4.5f) return mixed
        if (r > bestR) {
            best = mixed
            bestR = r
        }
        t += 0.04f
    }
    return best
}

private fun WyrmHero.retinted(bg: Color, hi: Color, lo: Color, seedOn: Color): WyrmHero {
    val on = seedOn.ensureAgainst(listOf(bg), 6.5f)
    val well = bg.mixKeepAlpha(on, 0.14f).copy(alpha = 1f).separateToward(bg, on)
    val quiet = on.mixKeepAlpha(bg, 0.28f).ensureAgainst(listOf(bg, well), 4.5f)
    val mute = on.mixKeepAlpha(bg, 0.14f).ensureAgainst(listOf(bg, well), 4.5f)
    return WyrmHero(
        bg = bg,
        on = on,
        quiet = quiet,
        mute = mute,
        rule = on.copy(alpha = 0.22f),
        well = well,
        hi = hi,
        lo = lo,
    )
}

/** Card-local palette. Slot 2 is hero2; anything else is hero. Tab roles stay. */
internal fun WyrmPalette.heroView(slot: Int): WyrmPalette {
    val h = if (slot == 2) hero2 else hero
    return copy(
        paper = h.bg,
        card = h.bg,
        ink = h.on,
        onInk = h.bg,
        quiet = h.quiet,
        mute = h.mute,
        rule = h.rule,
        rowRule = h.rule,
        live = h.on,
        link = h.on,
        chevron = h.quiet,
        well = h.well,
        track = h.rule,
        dark = h.bg.luminance() < 0.45f,
    )
}

private fun WyrmPalette.resecured(): WyrmPalette {
    val ink2 = ink.ensureAgainst(listOf(paper), 7.5f)
    val card2 = card.separateToward(paper, ink2)
    val well2 = well.separateToward(paper, ink2)
    val body = listOf(paper, card2)
    return copy(
        ink = ink2,
        card = card2,
        well = well2,
        onInk = onInk.ensureAgainst(listOf(ink2), 4.5f),
        quiet = quiet.ensureAgainst(body + well2, 4.5f),
        mute = mute.ensureAgainst(body + well2, 4.5f),
        live = live.ensureAgainst(body + well2, 4.5f),
        badge = badge.ensureAgainst(body + well2, 4.5f),
        link = link.ensureAgainst(body + well2, 4.5f),
        tabIdle = tabIdle.ensureAgainst(listOf(tabBar, paper), 4.5f),
        chevron = chevron.ensureAgainst(body + well2, 4.5f),
        dark = paper.luminance() < 0.45f,
    )
}

internal fun WyrmPalette.withIntensity(intensity: Float): WyrmPalette {
    val safe = intensity.coerceIn(0f, 1f)
    // Even at 50%, secure small-text roles: the prototype's decorative
    // chevron/link seeds can be below the app's 4.5:1 reading floor.
    val shifted = if (safe < 0.5f) {
        val t = 1f - safe * 2f
        copy(
            card = card.mixKeepAlpha(paper, t),
            well = well.mixKeepAlpha(paper, t),
            track = track.mixKeepAlpha(paper, t),
            hover = hover.mixKeepAlpha(paper, t),
            live = live.mixKeepAlpha(paper, t),
            badge = badge.mixKeepAlpha(paper, t),
            tabBar = tabBar.mixKeepAlpha(paper, t),
            rule = rule.copy(alpha = rule.alpha * (1f - t)),
            rowRule = rowRule.copy(alpha = rowRule.alpha * (1f - t)),
            hero = hero.retinted(
                hero.bg.mixKeepAlpha(paper, t),
                hero.hi.mixKeepAlpha(paper, t),
                hero.lo.mixKeepAlpha(paper, t),
                hero.on,
            ),
            hero2 = hero2.retinted(
                hero2.bg.mixKeepAlpha(paper, t),
                hero2.hi.mixKeepAlpha(paper, t),
                hero2.lo.mixKeepAlpha(paper, t),
                hero2.on,
            ),
        )
    } else {
        fun Color.lift() = chroma(safe)
        copy(
            paper = paper.lift(),
            card = card.lift(),
            ink = ink.lift(),
            onInk = onInk.lift(),
            quiet = quiet.lift(),
            mute = mute.lift(),
            rule = rule.lift(),
            rowRule = rowRule.lift(),
            live = live.lift(),
            link = link.lift(),
            tabIdle = tabIdle.lift(),
            tabBar = tabBar.lift(),
            chevron = chevron.lift(),
            badge = badge.lift(),
            well = well.lift(),
            track = track.lift(),
            hover = hover.lift(),
            hero = hero.retinted(hero.bg.lift(), hero.hi.lift(), hero.lo.lift(), hero.on.lift()),
            hero2 = hero2.retinted(hero2.bg.lift(), hero2.hi.lift(), hero2.lo.lift(), hero2.on.lift()),
        )
    }
    return shifted.resecured()
}

/**
 * Wyrm's surface language: black, light, and glass.
 *
 * The same look the on-screen controls already wear — a translucent body, a
 * highlight along the top as though the piece were lit from above, and a bright
 * hairline where the edge catches. It is built out of light rather than colour,
 * so a panel reads the same over Home's black as it does over a live arena.
 *
 * The greys are deliberately higher than they were. Wyrm's black is very dark,
 * and text set at old-dark-grey against it fell below the point where a phone
 * outdoors can resolve it — the app read as dim rather than as quiet.
 */
object Wyrm {
    val Black: Color get() = palette.paper
    val Carbon: Color get() = palette.card
    val Raised: Color get() = palette.well
    val Line: Color get() = palette.rule

    val White: Color get() = palette.ink
    val SoftWhite: Color get() = palette.mute
    val Grey: Color get() = palette.quiet
    val Faint: Color get() = palette.quiet

    private var themeState by mutableStateOf(WyrmThemeId.SAND)
    private var modeState by mutableStateOf(WyrmThemeId.SAND.defaultMode)
    private var systemDarkState by mutableStateOf(false)
    private var intensityState by mutableFloatStateOf(0.5f)
    private var paletteState by mutableStateOf(
        themeState.side(modeState, systemDarkState).withIntensity(intensityState),
    )
    val theme: WyrmThemeId get() = themeState
    val themeMode: WyrmThemeMode get() = modeState
    val systemDark: Boolean get() = systemDarkState
    val themeIntensity: Float get() = intensityState
    val currentPalette: WyrmPalette get() = paletteState
    private val palette: WyrmPalette get() = paletteState

    /* Compose paper colours are state so every existing semantic token updates
       together. WyrmOverlay also publishes these roles to the native arena's
       UI-only palette; gameplay pixels and state do not read them. */
    val Paper: Color get() = palette.paper
    val Card: Color get() = palette.card
    val Ink: Color get() = palette.ink
    val OnInk: Color get() = palette.onInk
    val Quiet: Color get() = palette.quiet
    val Mute: Color get() = palette.mute
    val Rule: Color get() = palette.rule
    val RowRule: Color get() = palette.rowRule
    val Live: Color get() = palette.live
    val Link: Color get() = palette.link
    val TabIdle: Color get() = palette.tabIdle
    val TabBar: Color get() = palette.tabBar
    val Chevron: Color get() = palette.chevron
    val Badge: Color get() = palette.badge
    val Well: Color get() = palette.well
    val Track: Color get() = palette.track
    val Hover: Color get() = palette.hover

    /**
     * Ink on a selected pill or tab (OM, 2026-09-28). Graphite and Midnight
     * seat the selection on a light thumb, where their light ink vanished, so
     * there it is the dark paper mixed towards black. As `selectedPillInk` on iOS.
     */
    val PillInk: Color get() =
        if (palette.dark) androidx.compose.ui.graphics.lerp(palette.paper, Color(0xFF111111), 0.55f) else palette.ink
    /** The light thumb under [PillInk] in the dark themes. */
    val PillThumb: Color get() = palette.ink.copy(alpha = 0.9f)

    /**
     * The tab bar's resting pill (OM, 2026-09-28): a light, see-through
     * capsule in every theme. It is drawn over the tab's icon and label, which
     * keep the theme's ink, so an opaque light pill hid them in the dark themes.
     */
    val TabThumb: Color get() = palette.ink.copy(alpha = if (palette.dark) 0.14f else 0.07f)

    fun contentOn(background: Color): Color {
        val luminance = background.luminance()
        val darkContrast = (luminance + 0.05f) / 0.05f
        val lightContrast = 1.05f / (luminance + 0.05f)
        return if (darkContrast >= lightContrast) Color.Black else Color.White
    }

    fun applyTheme(
        theme: WyrmThemeId,
        intensity: Float = intensityState,
        mode: WyrmThemeMode = modeState,
    ) {
        themeState = theme
        modeState = mode
        intensityState = intensity.coerceIn(0f, 1f)
        republish()
    }

    /** Phone light/dark. Republishes only while the saved mode is Auto. */
    fun setSystemDark(dark: Boolean) {
        if (systemDarkState == dark) return
        systemDarkState = dark
        if (modeState == WyrmThemeMode.AUTO) republish()
    }

    private fun republish() {
        paletteState = themeState.side(modeState, systemDarkState).withIntensity(intensityState)
    }

    /** The engine-boot green. Used for the mark. Live UI uses [Live]. */
    val Green = Color(0xFF3FEE96)

    /** Death, and nothing else. The only red anywhere in the app. */
    val Blood = Color(0xFFFF4D4D)

    val Gutter = 22.dp
    val Gap = 14.dp
    val GapLarge = 30.dp

    /*
     * Corners, on one scale.
     *
     * iOS rounds by the size of the thing: a field is softer than a hairline
     * rule, a sheet is softer than a field, and anything you press with a thumb
     * is a pill. Naming them keeps a screen from inventing its own radius.
     */
    val CornerSmall = 12.dp
    val Corner = 18.dp
    val CornerLarge = 26.dp
    val Pill = 999.dp

    /*
     * Both faces are variable fonts. Manrope's default instance is ExtraLight
     * (200), and only Normal and Bold were declared, so body text drew thin and
     * faded next to Wyrm iOS and every SemiBold label jumped to Bold (OM,
     * 2026-09-29). Every weight is now its own instance with its axis set
     * explicitly, a touch heavier than its name: Android rasterises Manrope
     * lighter than iOS does, and this is what matches the iPhone side by side.
     */
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun manrope(weight: FontWeight, axis: Int) =
        Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun bodoni(weight: FontWeight, axis: Int) =
        Font(R.font.bodoni_moda, weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))

    val Display = FontFamily(
        bodoni(FontWeight.Normal, 440),
        bodoni(FontWeight.Medium, 540),
        bodoni(FontWeight.SemiBold, 640),
        bodoni(FontWeight.Bold, 740),
    )
    val Body = FontFamily(
        manrope(FontWeight.Light, 360),
        manrope(FontWeight.Normal, 460),
        manrope(FontWeight.Medium, 560),
        manrope(FontWeight.SemiBold, 650),
        manrope(FontWeight.Bold, 740),
        manrope(FontWeight.ExtraBold, 800),
    )
}

/* ------------------------------------------------------------------- glass */

/**
 * The frosted body every raised surface shares.
 *
 * Lit from the top and falling away, which is the whole trick: a flat
 * translucent fill looks like a sticker, a graded one looks like a pane.
 * [lift] carries how far the surface is meant to stand off the background, so
 * the same recipe draws a whole sheet and a single chip.
 */
fun glassFill(lift: Float = 1f) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.13f * lift),
    0.55f to Color.White.copy(alpha = 0.07f * lift),
    1f to Color.White.copy(alpha = 0.035f * lift),
)

/** The lit edge. Brightest along the top, where light would land. */
fun glassEdge(lift: Float = 1f) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.38f * lift),
    1f to Color.White.copy(alpha = 0.10f * lift),
)

/** The body of something pressed *into* the surface rather than raised out of it. */
fun wellFill() = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.22f),
    1f to Color.White.copy(alpha = 0.035f),
)

/** A well's edge is lit along the bottom — the opposite of a raised pane. */
fun wellEdge() = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.09f),
    1f to Color.White.copy(alpha = 0.24f),
)

/**
 * A pane of glass, with something on it.
 *
 * Everything raised in the app is one of these: rows, cards, sheets, the bar a
 * screen hangs its title from. It is deliberately not a Material Card — no
 * elevation shadow, no tonal fill — because the depth here comes from the
 * gradient and the edge, and a shadow underneath would fight the arena when
 * the same panel is drawn over a live game.
 */
@Composable
fun WyrmGlass(
    modifier: Modifier = Modifier,
    corner: Dp = Wyrm.Corner,
    lift: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = wyrmRounded(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Wyrm.Black.copy(alpha = 0.34f * lift))
            .background(glassFill(lift))
            .border(1.dp, glassEdge(lift), shape),
        content = content,
    )
}

/**
 * The chip that is either on or off: a tab, a filter, a text size.
 *
 * On is solid white with black type — the same weight as the primary action,
 * because a chosen segment is a statement about what you are looking at. Off is
 * glass, so a row of them reads as one control rather than as four buttons.
 */
fun Modifier.wyrmSegment(active: Boolean, corner: Dp = Wyrm.Pill): Modifier {
    val shape = wyrmRounded(corner)
    return this
        .clip(shape)
        .background(if (active) SolidColor(Wyrm.White) else glassFill())
        .border(1.dp, if (active) SolidColor(Wyrm.White) else glassEdge(), shape)
}

/**
 * The spring everything presses with.
 *
 * Short, slightly under-damped, and the same everywhere, so the app has one
 * sense of weight rather than a different one per screen.
 */
@Composable
fun pressScale(pressed: Boolean, enabled: Boolean = true): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 820f),
        label = "press",
    )
    return scale
}

@Composable
fun WyrmTheme(content: @Composable () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val scheme = if (Wyrm.currentPalette.dark) {
        darkColorScheme(
            primary = wyrmScopedPalette.ink,
            onPrimary = wyrmScopedPalette.onInk,
            secondary = wyrmScopedPalette.live,
            background = wyrmScopedPalette.paper,
            onBackground = wyrmScopedPalette.ink,
            surface = wyrmScopedPalette.card,
            onSurface = wyrmScopedPalette.ink,
            outline = wyrmScopedPalette.rule,
        )
    } else {
        lightColorScheme(
            primary = wyrmScopedPalette.ink,
            onPrimary = wyrmScopedPalette.onInk,
            secondary = wyrmScopedPalette.live,
            background = wyrmScopedPalette.paper,
            onBackground = wyrmScopedPalette.ink,
            surface = wyrmScopedPalette.card,
            onSurface = wyrmScopedPalette.ink,
            outline = wyrmScopedPalette.rule,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            displayLarge = TextStyle(fontFamily = Wyrm.Display, fontSize = 54.sp, lineHeight = 54.sp),
            headlineLarge = TextStyle(fontFamily = Wyrm.Display, fontSize = 34.sp),
            headlineMedium = TextStyle(fontFamily = Wyrm.Display, fontSize = 26.sp),
            titleLarge = TextStyle(fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            bodyLarge = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp),
            bodyMedium = TextStyle(fontFamily = Wyrm.Body, fontSize = 13.sp),
            labelLarge = TextStyle(fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp),
        ),
        content = content,
    )
}

/**
 * Near-black, never flat.
 *
 * One very slow, very dim pool of light drifts down the screen. It is the only
 * motion on an idle Home, and at this amplitude it registers as depth rather
 * than as an animation.
 */
@Composable
fun WyrmBackdrop(modifier: Modifier = Modifier, animated: Boolean = true) {
    val y = if (animated) {
        val drift = rememberInfiniteTransition(label = "backdrop")
        val position by drift.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "drift",
        )
        position
    } else {
        // A live call already has audio-driven state. Keeping the same light
        // pool still preserves the surface, without asking an older GPU to
        // redraw every translucent participant tile sixty times a second.
        0.38f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Wyrm.Black)
            .background(
                Brush.radialGradient(
                    // Brighter than it was: the glass above it has nothing to
                    // catch on a perfectly flat black, and the app read as dim
                    // rather than as dark.
                    colors = listOf(Color(0x26FFFFFF), Color.Transparent),
                    // Centre is in pixels; the pool tracks down the long edge of
                    // a portrait phone and back.
                    center = Offset(560f, y * 2400f),
                    radius = 1500f,
                )
            )
            .background(
                Brush.verticalGradient(
                    0f to Color(0x10FFFFFF),
                    0.5f to Color.Transparent,
                    1f to Color(0x40000000),
                )
            )
    )
}

/** The hairline that does the work a card border would. */
@Composable
fun WyrmRule(modifier: Modifier = Modifier, color: Color = Wyrm.Line) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** Small caps, wide tracking. Names a thing without competing with it. */
@Composable
fun WyrmLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Wyrm.Grey,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = color,
    )
}

/**
 * The single loud control on a screen: a solid white block with black type.
 *
 * The one thing on a screen that is *not* glass. Everything else is something
 * you can see through; this is the thing you are meant to press, so it is
 * opaque and it is the brightest object in the frame. It shrinks a little under
 * a finger instead of rippling, which keeps the surface quiet.
 */
@Composable
fun WyrmPrimaryAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = pressScale(pressed, enabled)

    Box(
        modifier = modifier
            .scale(press)
            .clip(wyrmRounded(Wyrm.Pill))
            .background(if (enabled) Wyrm.White else Wyrm.Raised)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = if (enabled) Wyrm.Black else Wyrm.Faint,
                    strokeWidth = 1.7.dp,
                )
            }
            Text(
                text = label.uppercase(),
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 2.4.sp,
                color = if (enabled) Wyrm.Black else Wyrm.Faint,
            )
        }
    }
}

/** A glass pill for secondary state you can tap — the arena, a filter. */
@Composable
fun WyrmPill(
    label: String,
    modifier: Modifier = Modifier,
    leading: Color? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(Wyrm.Pill)

    Row(
        modifier = modifier
            .scale(pressScale(pressed))
            .clip(shape)
            .background(glassFill(if (pressed) 1.7f else 1f))
            .border(1.dp, glassEdge(if (pressed) 1.5f else 1f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(wyrmRounded(999.dp))
                    .background(leading)
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Wyrm.SoftWhite,
        )
    }
}

/**
 * One line of the Home index.
 *
 * Still a ledger rather than a grid — a serif ordinal, a name, and whatever
 * that section currently has to say — but each line is now its own pane of
 * glass instead of a stretch of hairline. It scales to seven sections without
 * turning into wallpaper, and it gives the panel that grows out of a row
 * something with the same corners to grow from.
 */
@Composable
fun WyrmIndexRow(
    ordinal: Int,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    onClick: (Rect) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The row hands over its own rectangle, because the screen it opens grows
    // out of it.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val lift = if (pressed && enabled) 2.1f else 1f
    val shape = wyrmRounded(Wyrm.Corner)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale(pressed, enabled))
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(shape)
            .background(Wyrm.Black.copy(alpha = if (enabled) 0.34f else 0.20f))
            .background(glassFill(if (enabled) lift else 0.45f))
            .border(1.dp, glassEdge(if (enabled) lift else 0.45f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onClick(bounds) }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ordinal.toString().padStart(2, '0'),
            fontFamily = Wyrm.Display,
            fontSize = 15.sp,
            color = if (enabled) Wyrm.Grey else Wyrm.Faint,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = title,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (enabled) Wyrm.White else Wyrm.Faint,
            modifier = Modifier.weight(1f),
        )
        if (accent != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(wyrmRounded(Wyrm.Pill))
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = detail,
            fontFamily = Wyrm.Body,
            fontSize = 12.sp,
            color = if (enabled) Wyrm.Grey else Wyrm.Faint,
        )
    }
}

/**
 * A recessed field, used only where the player types.
 *
 * The one surface that goes the other way: darker than its background rather
 * than lighter, with the lit edge along the *bottom*. Glass that has been
 * pressed in instead of raised, so a field never looks like a button.
 */
@Composable
fun WyrmWell(
    modifier: Modifier = Modifier,
    corner: Dp = Wyrm.CornerSmall,
    content: @Composable () -> Unit,
) {
    val shape = wyrmRounded(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.30f))
            .background(wellFill())
            .border(1.dp, wellEdge(), shape),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}
