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
import androidx.compose.material3.Text
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
)

enum class WyrmThemeId(
    val storedName: String,
    val displayName: String,
    val description: String,
    val palette: WyrmPalette,
) {
    PAPER(
        "paper", "Paper", "The original warm Wyrm canvas",
        WyrmPalette(
            Color(0xFFF7F6F3), Color(0xFFFFFFFF), Color(0xFF37352F), Color(0xFFFFFFFF),
            Color(0xFF787774), Color(0xFF6B6660), Color(0x1437352F), Color(0x1237352F),
            Color(0xFF448361), Color(0xFF2F6FDE), Color(0xFF7C776F), Color(0xF0FCFBFA),
            Color(0xFFC6C1B8), Color(0xFFC4554D), Color(0xFFF0EEE9), Color(0xFFEFEDE8),
            Color(0xFFFAF9F7), false,
        ),
    ),
    GRAPHITE(
        "graphite", "Graphite", "Charcoal paper, warm readable type",
        WyrmPalette(
            Color(0xFF171816), Color(0xFF222321), Color(0xFFF2EFE8), Color(0xFF171816),
            Color(0xFFB9B5AD), Color(0xFFD1CDC4), Color(0x24F2EFE8), Color(0x1CF2EFE8),
            Color(0xFF72B68F), Color(0xFF8CB4FF), Color(0xFFA7A39B), Color(0xF01D1E1C),
            Color(0xFF77746E), Color(0xFFE17B72), Color(0xFF2B2C29), Color(0xFF30312E),
            Color(0xFF282926), true,
        ),
    ),
    BLUSH(
        "blush", "Blush", "Soft rose paper with berry ink",
        WyrmPalette(
            Color(0xFFFFF4F5), Color(0xFFFFFBFB), Color(0xFF4C3036), Color(0xFFFFFBFB),
            Color(0xFF88676D), Color(0xFF73555B), Color(0x184C3036), Color(0x124C3036),
            Color(0xFF3F8062), Color(0xFF9D4561), Color(0xFF8A7074), Color(0xF0FFF9FA),
            Color(0xFFCDB7BA), Color(0xFFC44F5F), Color(0xFFF7E7E9), Color(0xFFF3E1E4),
            Color(0xFFFFF8F8), false,
        ),
    ),
    SUN(
        "sun", "Sun", "Cream and ochre without harsh glare",
        WyrmPalette(
            Color(0xFFFFF8DE), Color(0xFFFFFCF0), Color(0xFF463B22), Color(0xFFFFFCF0),
            Color(0xFF7D6F51), Color(0xFF695C40), Color(0x18463B22), Color(0x12463B22),
            Color(0xFF487A59), Color(0xFF9A681D), Color(0xFF82765E), Color(0xF0FFFAE9),
            Color(0xFFC9BB96), Color(0xFFB94F47), Color(0xFFF4EAC8), Color(0xFFF1E5BD),
            Color(0xFFFFFBEA), false,
        ),
    ),
    SLATE(
        "slate", "Slate", "Cool grey field with crisp graphite",
        WyrmPalette(
            Color(0xFFF0F2F3), Color(0xFFF9FAFA), Color(0xFF30383C), Color(0xFFF9FAFA),
            Color(0xFF646E74), Color(0xFF566168), Color(0x1830383C), Color(0x1230383C),
            Color(0xFF3F7D61), Color(0xFF356C8E), Color(0xFF727C81), Color(0xF0F5F7F7),
            Color(0xFFB8C0C3), Color(0xFFB65252), Color(0xFFE5E9EA), Color(0xFFE2E7E8),
            Color(0xFFF5F7F7), false,
        ),
    ),
    LILAC(
        "lilac", "Lilac", "Pale violet paper with aubergine ink",
        WyrmPalette(
            Color(0xFFF7F2FC), Color(0xFFFCFAFF), Color(0xFF403348), Color(0xFFFCFAFF),
            Color(0xFF796B81), Color(0xFF65566E), Color(0x18403348), Color(0x12403348),
            Color(0xFF487D61), Color(0xFF7655A3), Color(0xFF7C7182), Color(0xF0FAF7FD),
            Color(0xFFC3B7CA), Color(0xFFC15362), Color(0xFFEDE4F2), Color(0xFFE9E0EF),
            Color(0xFFFAF7FD), false,
        ),
    ),
    FOREST(
        "forest", "Forest", "Muted sage with deep evergreen ink",
        WyrmPalette(
            Color(0xFFF1F5ED), Color(0xFFFAFCF8), Color(0xFF2F3F34), Color(0xFFFAFCF8),
            Color(0xFF647168), Color(0xFF536158), Color(0x182F3F34), Color(0x122F3F34),
            Color(0xFF3D7B56), Color(0xFF356F5C), Color(0xFF707C73), Color(0xF0F7FAF5),
            Color(0xFFB6C2B7), Color(0xFFB95450), Color(0xFFE4EBDD), Color(0xFFE1E9DA),
            Color(0xFFF7FAF5), false,
        ),
    ),
    MIDNIGHT(
        "midnight", "Midnight", "Blue-black paper with cool moonlit type",
        WyrmPalette(
            Color(0xFF101820), Color(0xFF19242D), Color(0xFFEAF1F4), Color(0xFF101820),
            Color(0xFFAAB8C0), Color(0xFFC5D0D5), Color(0x24EAF1F4), Color(0x1CEAF1F4),
            Color(0xFF6DB68F), Color(0xFF83B8E8), Color(0xFF9DABB3), Color(0xF0141E27),
            Color(0xFF6B7B85), Color(0xFFE07972), Color(0xFF22303A), Color(0xFF283741),
            Color(0xFF1E2A34), true,
        ),
    );

    companion object {
        fun fromStored(value: String?): WyrmThemeId =
            entries.firstOrNull { it.storedName == value } ?: PAPER
    }
}

private fun Color.withThemeIntensity(intensity: Float): Color {
    /* Fifty percent is the palette exactly as designed. Above that point only
       chroma grows; the softer half is handled by WyrmPalette so it can move
       the whole theme towards Paper without sacrificing text contrast. */
    val safeIntensity = intensity.coerceIn(0f, 1f)
    if (kotlin.math.abs(safeIntensity - 0.5f) < 0.0001f) return this
    val multiplier = safeIntensity * 2f
    val luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
    return Color(
        red = (luminance + (red - luminance) * multiplier).coerceIn(0f, 1f),
        green = (luminance + (green - luminance) * multiplier).coerceIn(0f, 1f),
        blue = (luminance + (blue - luminance) * multiplier).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

private fun Color.mixTowards(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * t,
        green = green + (target.green - green) * t,
        blue = blue + (target.blue - blue) * t,
        alpha = alpha + (target.alpha - alpha) * t,
    )
}

private fun contrastRatio(background: Color, foreground: Color): Float {
    val lighter = maxOf(background.luminance(), foreground.luminance())
    val darker = minOf(background.luminance(), foreground.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.withReadableContrast(foreground: Color, minimum: Float = 4.5f): Color {
    if (contrastRatio(this, foreground) >= minimum) return this
    val target = if (foreground.luminance() < 0.5f) Color.White else Color(0xFF111111)
    var low = 0f
    var high = 1f
    repeat(12) {
        val middle = (low + high) / 2f
        if (contrastRatio(mixTowards(target, middle), foreground) >= minimum) high = middle
        else low = middle
    }
    return mixTowards(target, high)
}

internal fun WyrmPalette.withIntensity(intensity: Float): WyrmPalette {
    val safeIntensity = intensity.coerceIn(0f, 1f)
    if (kotlin.math.abs(safeIntensity - 0.5f) < 0.0001f) return this

    if (safeIntensity < 0.5f) {
        val base = WyrmThemeId.PAPER.palette
        if (safeIntensity < 0.0001f) return base
        val amount = safeIntensity * 2f
        val rawPaper = base.paper.mixTowards(paper, amount)
        val rawCard = base.card.mixTowards(card, amount)
        val rawWell = base.well.mixTowards(well, amount)
        val surfaces = listOf(rawPaper, rawCard, rawWell)
        val baseScore = surfaces.minOf { contrastRatio(it, base.ink) }
        val themeScore = surfaces.minOf { contrastRatio(it, ink) }
        val fallbackInk = listOf(Color(0xFF111111), Color.White).maxBy { candidate ->
            surfaces.minOf { contrastRatio(it, candidate) }
        }
        val mixedInk = when {
            baseScore >= 4.5f && baseScore >= themeScore -> base.ink
            themeScore >= 4.5f -> ink
            else -> fallbackInk
        }
        val useThemeText = mixedInk == ink || mixedInk.luminance() > 0.5f
        val mixedOnInk = when (mixedInk) {
            base.ink -> base.onInk
            ink -> onInk
            else -> if (mixedInk.luminance() > 0.5f) Color(0xFF111111) else Color.White
        }
        val mixedPaper = rawPaper.withReadableContrast(mixedInk)
        val mixedCard = rawCard.withReadableContrast(mixedInk)
        val mixedWell = rawWell.withReadableContrast(mixedInk)
        return WyrmPalette(
            paper = mixedPaper,
            card = mixedCard,
            ink = mixedInk,
            onInk = mixedOnInk,
            quiet = if (useThemeText) quiet else base.quiet,
            mute = if (useThemeText) mute else base.mute,
            rule = base.rule.mixTowards(rule, amount),
            rowRule = base.rowRule.mixTowards(rowRule, amount),
            live = base.live.mixTowards(live, amount),
            link = base.link.mixTowards(link, amount),
            tabIdle = if (useThemeText) tabIdle else base.tabIdle,
            tabBar = base.tabBar.mixTowards(tabBar, amount),
            chevron = if (useThemeText) chevron else base.chevron,
            badge = base.badge.mixTowards(badge, amount),
            well = mixedWell,
            track = base.track.mixTowards(track, amount),
            hover = base.hover.mixTowards(hover, amount),
            dark = mixedPaper.luminance() < 0.45f,
        )
    }

    return copy(
        paper = paper.withThemeIntensity(safeIntensity),
        card = card.withThemeIntensity(safeIntensity),
        ink = ink.withThemeIntensity(safeIntensity),
        onInk = onInk.withThemeIntensity(safeIntensity),
        quiet = quiet.withThemeIntensity(safeIntensity),
        mute = mute.withThemeIntensity(safeIntensity),
        rule = rule.withThemeIntensity(safeIntensity),
        rowRule = rowRule.withThemeIntensity(safeIntensity),
        live = live.withThemeIntensity(safeIntensity),
        link = link.withThemeIntensity(safeIntensity),
        tabIdle = tabIdle.withThemeIntensity(safeIntensity),
        tabBar = tabBar.withThemeIntensity(safeIntensity),
        chevron = chevron.withThemeIntensity(safeIntensity),
        badge = badge.withThemeIntensity(safeIntensity),
        well = well.withThemeIntensity(safeIntensity),
        track = track.withThemeIntensity(safeIntensity),
        hover = hover.withThemeIntensity(safeIntensity),
    )
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
    val Black = Color(0xFF080808)
    val Carbon = Color(0xFF141414)
    val Raised = Color(0xFF1E1E1E)
    val Line = Color(0xFF343434)

    val White = Color(0xFFFAF9F6)
    val SoftWhite = Color(0xFFE6E4DE)
    val Grey = Color(0xFFAFAEA8)
    val Faint = Color(0xFF7E7D78)

    private var themeState by mutableStateOf(WyrmThemeId.PAPER)
    private var intensityState by mutableFloatStateOf(0.5f)
    private var paletteState by mutableStateOf(themeState.palette.withIntensity(intensityState))
    val theme: WyrmThemeId get() = themeState
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

    fun contentOn(background: Color): Color {
        val luminance = background.luminance()
        val darkContrast = (luminance + 0.05f) / 0.05f
        val lightContrast = 1.05f / (luminance + 0.05f)
        return if (darkContrast >= lightContrast) Color(0xFF111111) else Color.White
    }

    fun applyTheme(theme: WyrmThemeId, intensity: Float = intensityState) {
        themeState = theme
        intensityState = intensity.coerceIn(0f, 1f)
        paletteState = theme.palette.withIntensity(intensityState)
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

    val Display = FontFamily(Font(R.font.bodoni_moda, FontWeight.SemiBold))
    val Body = FontFamily(
        Font(R.font.manrope, FontWeight.Normal),
        Font(R.font.manrope, FontWeight.Bold),
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
    val scheme = if (Wyrm.theme.palette.dark) {
        darkColorScheme(
            primary = Wyrm.Ink,
            onPrimary = Wyrm.OnInk,
            secondary = Wyrm.Live,
            background = Wyrm.Paper,
            onBackground = Wyrm.Ink,
            surface = Wyrm.Card,
            onSurface = Wyrm.Ink,
            outline = Wyrm.Rule,
        )
    } else {
        lightColorScheme(
            primary = Wyrm.Ink,
            onPrimary = Wyrm.OnInk,
            secondary = Wyrm.Live,
            background = Wyrm.Paper,
            onBackground = Wyrm.Ink,
            surface = Wyrm.Card,
            onSurface = Wyrm.Ink,
            outline = Wyrm.Rule,
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
