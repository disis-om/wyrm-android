package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The game's own sprite sheet, so the editor shows real beads.
 *
 * A colour group is not a colour — it is a shaded, highlighted bead sitting in
 * the arena's texture atlas, and a flat circle of its average RGB reads as a
 * different thing entirely. The atlas is laid out as a fixed 7x9 grid (see
 * calc_cg_uvs in app/src/game/game_data.c), so cell positions are arithmetic
 * and need no bridge from the engine.
 *
 * The sheet is 3136x4032; it is decoded once at a quarter of that, which is
 * still 112 pixels per bead — more than any of them are ever drawn at — for
 * about 3 MB, and every bead is then drawn as a source rectangle out of that one
 * image rather than as seventy-odd separate bitmaps.
 */
object SkinAtlas {
    const val COLUMNS = 7
    const val ROWS = 9
    // Accessory cells occupy only 1/28 x 1/36 of the sheet. Quarter-size
    // decoding left roughly 28 source pixels and made them look washed/soft
    // when expanded in the picker; half-size keeps a crisp 56 px source while
    // still avoiding the full 8K allocation.
    private const val SAMPLE = 2

    fun load(context: Context): ImageBitmap? = try {
        context.assets.open("textures/tex_atlas_8k.png").use { stream ->
            val options = BitmapFactory.Options().apply {
                inSampleSize = SAMPLE
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    } catch (ignored: Throwable) {
        null
    }
}

/** The atlas, decoded off the main thread; null until it is ready. */
@Composable
fun rememberSkinAtlas(): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { SkinAtlas.load(context) }
    }
}

const val ACCESSORY_COUNT = 32

/**
 * Accessories live in the same atlas as the beads, from (5/7, 8/9),
 * eight across and four down, each cell 1/28 by 1/36 of the sheet.
 */
fun DrawScope.drawAccessory(atlas: ImageBitmap, id: Int) {
    if (id !in 0 until ACCESSORY_COUNT) return
    val col = id % 8
    val row = id / 8
    val uvX = 5f / 7f + col * (1f / 28f)
    val uvY = 8f / 9f + row * (1f / 36f)
    val uvW = 1f / 28f
    val uvH = 1f / 36f
    val srcX = (uvX * atlas.width).toInt()
    val srcY = (uvY * atlas.height).toInt()
    val srcW = (uvW * atlas.width).toInt().coerceAtLeast(1)
    val srcH = (uvH * atlas.height).toInt().coerceAtLeast(1)
    val scale = minOf(size.width / srcW, size.height / srcH)
    val width = srcW * scale
    val height = srcH * scale
    drawImage(
        image = atlas,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(
            ((size.width - width) / 2f).toInt(),
            ((size.height - height) / 2f).toInt(),
        ),
        dstSize = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
    )
}

/** Draws one colour group's bead, exactly as the arena draws it. */
fun DrawScope.drawBead(
    atlas: ImageBitmap,
    group: Int,
    left: Float,
    top: Float,
    size: Float,
) {
    val cellWidth = atlas.width / SkinAtlas.COLUMNS
    val cellHeight = atlas.height / SkinAtlas.ROWS
    val column = group % SkinAtlas.COLUMNS
    val row = group / SkinAtlas.COLUMNS
    if (row >= SkinAtlas.ROWS) return

    drawImage(
        image = atlas,
        srcOffset = IntOffset(column * cellWidth, row * cellHeight),
        srcSize = IntSize(cellWidth, cellHeight),
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(size.toInt(), size.toInt()),
    )
}
