package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The exact textures the engine draws with, cut for Compose — Wyrm iOS's
 * `WyrmSkinTextureLibrary`. The Skin page draws its own preview from these;
 * no engine surface is shown.
 */
class SkinTextures(
    val beads: Map<Int, ImageBitmap>,
    /** AIR Build-a-Slither cells: nsk 0 / nsk 1 beads and `ksmc_t`. */
    val airBeads: Map<Int, ImageBitmap>,
    val airShadow: ImageBitmap?,
    val airWheel: ImageBitmap?,
    val accessories: Map<Int, ImageBitmap>,
    val accessoryThumbnails: Map<Int, ImageBitmap>,
    val tags: Map<Int, ImageBitmap>,
    val tagThumbnails: Map<Int, ImageBitmap>,
    val backgrounds: Map<Int, ImageBitmap>,
    /** Wyrm's own beads (`WyrmBeads`), cut from their atlas quarters. */
    val wyrmBeads: Map<Int, ImageBitmap> = emptyMap(),
    /** Wyrm looks (`WyrmLook`): the 8 x 8 cells of wyrm_accessories.png. */
    val looks: Map<Int, ImageBitmap> = emptyMap(),
    /** Each look cell cut to its art and turned to face up, for the picker tiles. */
    val lookThumbnails: Map<Int, ImageBitmap> = emptyMap(),
) {
    companion object {
        private val lock = Mutex()
        @Volatile private var loaded: SkinTextures? = null

        suspend fun load(context: Context): SkinTextures? = lock.withLock {
            loaded ?: withContext(Dispatchers.IO) { build(context) }?.also { loaded = it }
        }

        fun cached(): SkinTextures? = loaded

        private fun decode(context: Context, path: String, maxPixel: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPixel) sample *= 2
            context.assets.open(path).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        }.getOrNull()

        /** iOS `crop`: fractions of the sheet, floored origin, rounded size, clipped. */
        /**
         * A look cell cut to its visible art and turned a quarter left, so what
         * faces the snake's front faces up: glasses sit level, ears stand on top.
         */
        private fun lookThumbnail(cell: Bitmap): Bitmap? {
            val w = cell.width
            val h = cell.height
            val pixels = IntArray(w * h)
            cell.getPixels(pixels, 0, w, 0, 0, w, h)
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            for (y in 0 until h) for (x in 0 until w) {
                if ((pixels[y * w + x] ushr 24) > 16) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) return null
            val pad = 3
            val left = max(0, minX - pad)
            val top = max(0, minY - pad)
            val cut = Bitmap.createBitmap(cell, left, top, min(w, maxX + pad + 1) - left, min(h, maxY + pad + 1) - top)
            val turn = android.graphics.Matrix().apply { postRotate(-90f) }
            return Bitmap.createBitmap(cut, 0, 0, cut.width, cut.height, turn, true)
        }

        private fun crop(image: Bitmap, x: Double, y: Double, width: Double, height: Double): Bitmap? {
            val left = floor(x * image.width).toInt()
            val top = floor(y * image.height).toInt()
            val w = max(1, (width * image.width).roundToInt())
            val h = max(1, (height * image.height).roundToInt())
            val right = minOf(image.width, left + w)
            val bottom = minOf(image.height, top + h)
            if (left !in 0 until image.width || top !in 0 until image.height || right <= left || bottom <= top) return null
            return Bitmap.createBitmap(image, left, top, right - left, bottom - top)
        }

        /** Picker cells drop the atlas' soft game shadow, as on iOS. */
        private fun removingSoftShadow(image: Bitmap): Bitmap {
            val out = image.copy(Bitmap.Config.ARGB_8888, true)
            val pixels = IntArray(out.width * out.height)
            out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            for (i in pixels.indices) {
                val alpha = (pixels[i] ushr 24) and 0xFF
                pixels[i] = when {
                    alpha < 76 -> 0
                    alpha < 176 -> (((alpha - 76) * 255 / 100).coerceIn(0, 255) shl 24) or (pixels[i] and 0x00FFFFFF)
                    else -> pixels[i]
                }
            }
            out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            return out
        }

        private fun build(context: Context): SkinTextures? {
            // Half the 8K sheet: every bead is still 224 px, more than it is ever drawn.
            val atlas = decode(context, "textures/tex_atlas_8k.png", 2016) ?: return null
            val tagAtlas = decode(context, "textures/wyrm_tags.png", 4096) ?: return null
            val beads = (0 until 42).mapNotNull { id ->
                crop(atlas, (id % 7) / 7.0, (id / 7) / 9.0, 1.0 / 7, 1.0 / 9)?.let { id to it.asImageBitmap() }
            }.toMap()
            val airBeads = (0 until 2).mapNotNull { kind ->
                crop(atlas, (2 + kind) / 7.0, 6.0 / 9, 1.0 / 7, 1.0 / 9)?.let { kind to it.asImageBitmap() }
            }.toMap()
            val airShadow = crop(atlas, 4.0 / 7, 6.0 / 9, 102.0 / 64 / 7, 102.0 / 64 / 9)?.asImageBitmap()
            val wyrmBeads = (0 until WyrmBeads.COUNT).mapNotNull { kind ->
                val r = WyrmBeads.uv(kind)
                crop(atlas, r[0], r[1], r[2], r[3])?.let { kind to it.asImageBitmap() }
            }.toMap()
            val airWheel = decode(context, "textures/air_colour_wheel.png", 768)?.asImageBitmap()
            val accessories = mutableMapOf<Int, ImageBitmap>()
            val accessoryThumbs = mutableMapOf<Int, ImageBitmap>()
            for (id in 0 until 32) {
                val cell = crop(atlas, 5.0 / 7 + (id % 8) / 28.0, 8.0 / 9 + (id / 8) / 36.0, 1.0 / 28, 1.0 / 36) ?: continue
                accessories[id] = cell.asImageBitmap()
                accessoryThumbs[id] = removingSoftShadow(cell).asImageBitmap()
            }
            val tags = mutableMapOf<Int, ImageBitmap>()
            val tagThumbs = mutableMapOf<Int, ImageBitmap>()
            for (tag in SkinCatalog.tags) {
                val cell = crop(
                    tagAtlas, tag.minU.toDouble(), tag.minV.toDouble(),
                    (tag.maxU - tag.minU).toDouble(), (tag.maxV - tag.minV).toDouble(),
                ) ?: continue
                tags[tag.id] = cell.asImageBitmap()
                tagThumbs[tag.id] = removingSoftShadow(cell).asImageBitmap()
            }
            val backgrounds = SkinCatalog.backgrounds.mapNotNull { background ->
                val path = background.asset ?: return@mapNotNull null
                decode(context, path, 520)?.let { background.id to it.asImageBitmap() }
            }.toMap()
            val lookSheet = decode(context, "textures/wyrm_accessories.png", 2048)
            val lookCells = if (lookSheet == null) emptyMap() else (0 until 40).mapNotNull { cell ->
                crop(lookSheet, (cell % 8) / 8.0, (cell / 8) / 8.0, 1.0 / 8, 1.0 / 8)?.let { cell to it }
            }.toMap()
            val looks = lookCells.mapValues { it.value.asImageBitmap() }
            val lookThumbs = lookCells.mapNotNull { (cell, bitmap) -> lookThumbnail(bitmap)?.let { cell to it.asImageBitmap() } }.toMap()
            return SkinTextures(beads, airBeads, airShadow, airWheel, accessories, accessoryThumbs, tags, tagThumbs, backgrounds, wyrmBeads, looks, lookThumbs)
        }
    }
}

@Composable
fun rememberSkinTextures(): State<SkinTextures?> {
    val context = LocalContext.current
    return produceState(initialValue = SkinTextures.cached(), context) {
        if (value == null) value = SkinTextures.load(context)
    }
}
