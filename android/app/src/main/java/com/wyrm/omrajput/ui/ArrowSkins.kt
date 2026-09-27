package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wyrm.omrajput.data.Setting

/**
 * The image arrows, identical to Wyrm iOS (`WyrmArrowImages`): same names, same
 * order, same atlas (`textures/arrow_skins.png`, 5 x 4 cells of 256 px). Append
 * only: a saved choice is an index into this list and into the atlas the
 * engine draws from.
 */
internal object ArrowImages {
    val names = listOf(
        "Blue 3D", "Blue 3D II", "Red 3D", "Yellow 3D", "Red arrow",
        "Wing", "Arrow I", "Arrow II", "Arrow III", "Neon Ice",
        "Neon Magenta", "Volt", "Inferno", "Aqua", "Heat",
        "Jade", "Chrome", "Twin Volt", "Sunset", "Streak",
    )
    private const val COLUMNS = 5
    private const val CELL = 256
    private var atlas: Bitmap? = null
    private val cache = HashMap<Int, ImageBitmap>()

    fun image(context: Context, index: Int): ImageBitmap? {
        if (index !in names.indices) return null
        cache[index]?.let { return it }
        val sheet = atlas ?: runCatching {
            context.assets.open("textures/arrow_skins.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.also { atlas = it } ?: return null
        val x = (index % COLUMNS) * CELL
        val y = (index / COLUMNS) * CELL
        if (x + CELL > sheet.width || y + CELL > sheet.height) return null
        val piece = Bitmap.createBitmap(sheet, x, y, CELL, CELL).asImageBitmap()
        cache[index] = piece
        return piece
    }
}

/**
 * Which arrow the arena draws and how bright, kept on the device like iOS keeps
 * it (UserDefaults there) and handed to the engine through
 * `nativeSetArrowSkin`. The drawn styles stay the engine's own `arrow.style`.
 */
object ArrowSkinStore {
    private const val PREFS = "wyrm_arrows"
    private const val KEY_SKIN = "skin"
    private const val KEY_BRIGHTNESS = "brightness"

    /** -1: one of the engine's drawn styles; 0…19: an image arrow. */
    var skin by mutableIntStateOf(-1)
        private set
    /** 0.2…1.0, multiplied into the arrow's colour (images and drawn alike). */
    var brightness by mutableFloatStateOf(1f)
        private set

    private var prefs: android.content.SharedPreferences? = null
    private var sink: ((Int, Float) -> Unit)? = null

    /** Called once by the activity; publishes the saved choice to the engine. */
    @JvmStatic
    fun attach(context: Context, publish: (Int, Float) -> Unit) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        val saved = store.getInt(KEY_SKIN, -1)
        skin = if (saved in ArrowImages.names.indices) saved else -1
        brightness = store.getFloat(KEY_BRIGHTNESS, 1f).coerceIn(0.2f, 1f)
        sink = publish
        publish(skin, brightness)
    }

    fun select(image: Int) {
        skin = if (image in ArrowImages.names.indices) image else -1
        prefs?.edit()?.putInt(KEY_SKIN, skin)?.apply()
        sink?.invoke(skin, brightness)
    }

    fun updateBrightness(value: Float) {
        brightness = value.coerceIn(0.2f, 1f)
        prefs?.edit()?.putFloat(KEY_BRIGHTNESS, brightness)?.apply()
        sink?.invoke(skin, brightness)
    }
}

private fun brightnessFilter(b: Float): ColorFilter? =
    if (b >= 0.999f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(b, b, b, 1f) })

/** The arrow as the arena draws it, for previews and picker tiles. */
@Composable
internal fun ArrowGlyph(
    codeStyle: Int,
    imageSkin: Int,
    colour: Color,
    brightness: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image = remember(imageSkin) { ArrowImages.image(context, imageSkin) }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            colorFilter = brightnessFilter(brightness),
            modifier = modifier,
        )
    } else {
        Canvas(modifier = modifier) {
            val length = minOf(size.width, size.height * 1.4f) * 0.62f
            drawArrowShape(
                style = codeStyle,
                length = length,
                width = length * 30f / 52f,
                fill = Color(
                    red = (colour.red * brightness).coerceIn(0f, 1f),
                    green = (colour.green * brightness).coerceIn(0f, 1f),
                    blue = (colour.blue * brightness).coerceIn(0f, 1f),
                ),
                outline = Color(0xFF040609),
                alpha = 1f,
                outlineWidth = 1.6.dp.toPx(),
            )
        }
    }
}

private fun styleName(setting: Setting?, index: Int): String {
    val option = setting?.options?.getOrNull(index)
    if (!option.isNullOrBlank()) return option.lowercase().replaceFirstChar { it.uppercase() }
    return arrowOptionLabel(index)
}

/** The Controls › arrow block: the chosen arrow with a way into the picker. */
@Composable
internal fun ArrowStyleRow(styleSetting: Setting?, colour: Color, first: Boolean, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val style = styleSetting?.index ?: 0
    val name = if (ArrowSkinStore.skin in ArrowImages.names.indices) ArrowImages.names[ArrowSkinStore.skin]
    else styleName(styleSetting, style)
    Column {
        if (!first) SettingsHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(pressScale(pressed))
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 56.dp)
                    .clip(wyrmRounded(10.dp))
                    .background(Wyrm.Well)
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArrowGlyph(style, ArrowSkinStore.skin, colour, ArrowSkinStore.brightness, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Arrow style", fontFamily = Wyrm.Body, fontSize = 15.5.sp, color = Wyrm.Ink)
                Text(name, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, maxLines = 1)
            }
            Text("›", fontFamily = Wyrm.Body, fontSize = 20.sp, color = Wyrm.Chevron)
        }
    }
}

/**
 * "Choose arrow": a live preview on top, the engine's drawn styles, then every
 * image arrow, each drawn exactly as it will look. Tap one to use it.
 */
@Composable
internal fun ArrowPickerSheet(
    styleSetting: Setting?,
    colour: Color,
    onPickStyle: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val style = styleSetting?.index ?: 0
        val skin = ArrowSkinStore.skin
        val brightness = ArrowSkinStore.brightness
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Wyrm.Paper)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp)) {
                Text(
                    "Choose arrow",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    "Done",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Link,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                LiveArrowPreview(style, skin, colour, brightness)
                SettingsSectionLabel("Wyrm arrows", top = 18.dp)
                ArrowGrid(count = ArrowOptions.size) { index ->
                    ArrowTile(
                        name = styleName(styleSetting, index),
                        selected = skin < 0 && style == index,
                        onClick = {
                            ArrowSkinStore.select(-1)
                            onPickStyle(index)
                        },
                    ) { ArrowGlyph(index, -1, colour, brightness, it) }
                }
                SettingsSectionLabel("Image arrows")
                ArrowGrid(count = ArrowImages.names.size) { index ->
                    ArrowTile(
                        name = ArrowImages.names[index],
                        selected = skin == index,
                        onClick = { ArrowSkinStore.select(index) },
                    ) { ArrowGlyph(0, index, colour, brightness, it) }
                }
                SettingsCaption("Size and brightness apply to every arrow; colour applies to the Wyrm arrows.")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The chosen arrow sweeping round as a finger would steer it. */
@Composable
private fun LiveArrowPreview(style: Int, skin: Int, colour: Color, brightness: Float) {
    val turn = rememberInfiniteTransition(label = "arrow preview")
    val angle by turn.animateFloat(
        initialValue = -28f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "arrow heading",
    )
    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .height(150.dp)
            .clip(wyrmRounded(14.dp))
            .background(Color(0xFF12161C))
            .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "LIVE PREVIEW",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            color = Color(0x99FFFFFF),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        ArrowGlyph(style, skin, colour, brightness, Modifier.size(120.dp).rotate(angle))
    }
}

@Composable
private fun ArrowGrid(count: Int, cell: @Composable (Int) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (0 until count).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { index -> Box(Modifier.weight(1f)) { cell(index) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ArrowTile(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    glyph: @Composable (Modifier) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = wyrmRounded(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale(pressed))
            .clip(shape)
            .background(Wyrm.Well)
            .border(if (selected) 2.dp else 1.dp, if (selected) Wyrm.Ink else Wyrm.Rule, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        glyph(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            fontFamily = Wyrm.Body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 11.sp,
            color = if (selected) Wyrm.Ink else Wyrm.Mute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 14.dp).padding(horizontal = 6.dp),
        )
    }
}
