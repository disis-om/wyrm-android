package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

/**
 * Wyrm extras (OM, 2026-11-07): `textures/wyrm_extras.png` + `.json`
 * (Wyrm Android tools/wyrm-extras/make.py), the same atlas the engine draws
 * from (game/wyrm_extras.c):
 *  - AIR's special-skin art, cut from AIR's own baked sheet;
 *  - NTL's minimap icons, numbered as NTL's `di` (0..741, 996..1000);
 *  - Wyrm's own minimap icons for Wyrm friends.
 */
object WyrmExtras {
    /** [image] is a square cell with the piece in its middle; [ow] x [oh] its own size. */
    class Art(val image: ImageBitmap, val pivotX: Float, val pivotY: Float, val ow: Float, val oh: Float)

    const val NTL_DEFAULT = 1000
    const val WYRM_DEFAULT = 0
    val wyrmNames: List<String> get() = names

    @Volatile private var sheet: Bitmap? = null
    @Volatile private var json: JSONObject? = null
    private var names: List<String> = emptyList()
    private val cache = HashMap<String, ImageBitmap>()
    private val arts = HashMap<String, Art>()

    @Synchronized
    fun load(context: Context): Boolean {
        if (sheet != null) return true
        return runCatching {
            val bitmap = context.assets.open("textures/wyrm_extras.png").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            } ?: return false
            val data = JSONObject(context.assets.open("textures/wyrm_extras.json").use { it.readBytes().decodeToString() })
            val wyrm = data.getJSONArray("wyrm")
            names = List(wyrm.length()) { wyrm.getJSONArray(it).getString(0) }
            json = data
            sheet = bitmap
            AirArt.image = { name -> art(name)?.let { AirArtImage(it.image, it.pivotX, it.pivotY, it.ow) } }
            true
        }.getOrDefault(false)
    }

    private fun cut(key: String, x: Int, y: Int, w: Int, h: Int): ImageBitmap? {
        cache[key]?.let { return it }
        val source = sheet ?: return null
        if (w <= 0 || h <= 0 || x + w > source.width || y + h > source.height) return null
        return Bitmap.createBitmap(source, x, y, w, h).asImageBitmap().also { cache[key] = it }
    }

    @Synchronized
    fun art(name: String): Art? {
        arts[name]?.let { return it }
        val r = json?.optJSONObject("air")?.optJSONArray(name) ?: return null
        val image = cut("air:$name", r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3)) ?: return null
        return Art(image, r.getDouble(4).toFloat(), r.getDouble(5).toFloat(),
            r.optDouble(6, r.getInt(2).toDouble()).toFloat(), r.optDouble(7, r.getInt(3).toDouble()).toFloat())
            .also { arts[name] = it }
    }

    @Synchronized
    fun wyrmIcon(index: Int): ImageBitmap? {
        val list = json?.optJSONArray("wyrm") ?: return null
        if (index !in 0 until list.length()) return null
        val r = list.getJSONArray(index)
        return cut("wyrm:$index", r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4))
    }

    val wyrmCount: Int get() = json?.optJSONArray("wyrm")?.length() ?: 0
    val ntlCount: Int get() = json?.optJSONArray("ntl")?.length() ?: 0

    /** NTL's icon for `di`: 0..741 or 996..1000 (1000 = its plain dot). */
    @Synchronized
    fun ntlIcon(di: Int): ImageBitmap? {
        val data = json ?: return null
        val r = when {
            di in 0 until ntlCount -> data.getJSONArray("ntl").getJSONArray(di)
            di in 996..1000 -> data.optJSONObject("ntlSpecial")?.optJSONArray(di.toString())
            else -> null
        } ?: return null
        return cut("ntl:$di", r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3))
    }

    /** The NTL icons a player can pick, in NTL's order, default dot first. */
    val ntlChoices: List<Int> get() = listOf(NTL_DEFAULT) + (0 until ntlCount)
}
