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
            return SkinTextures(beads, airBeads, airShadow, airWheel, accessories, accessoryThumbs, tags, tagThumbs, backgrounds)
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
