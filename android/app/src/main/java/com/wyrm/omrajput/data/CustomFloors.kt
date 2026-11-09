package com.wyrm.omrajput.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import android.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Your own arena floors (OM, 2026-11-08): photos from the gallery, shrunk on
 * the phone so the engine can draw them (long side 2048 px, JPEG), kept in
 * the app's own files and listed under Skin › Arena background. Any number;
 * a long press opens a list to delete some. The engine draws the chosen one
 * as background index [INDEX] (game/wyrm_extras.c `wyrm_custom_floor_set`).
 *
 * Phone only: the photos never leave the phone and are not in the account
 * (prefs `wyrm_custom_floors`, AccountSync WIPE: logging out forgets them,
 * and the next start deletes the files). The shared document never carries
 * index 200, so another phone keeps its own floor.
 */
object CustomFloors {
    const val INDEX = 200
    const val MAX_SIDE = 2048
    private const val PREFS = "wyrm_custom_floors"

    data class Floor(val id: String, val path: String, val width: Int, val height: Int)

    val floors = mutableStateListOf<Floor>()
    val chosen = mutableStateOf<String?>(null)
    private var engine: ((String, Int, Int) -> Unit)? = null

    private fun dir(context: Context) = File(context.filesDir, "custom_floors").apply { mkdirs() }

    @JvmStatic
    fun attach(context: Context, publish: (String, Int, Int) -> Unit) {
        engine = publish
        reload(context)
    }

    fun reload(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = runCatching { JSONArray(prefs.getString("floors", "[]")) }.getOrDefault(JSONArray())
        val loaded = (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            Floor(o.optString("id"), o.optString("path"), o.optInt("w"), o.optInt("h"))
                .takeIf { it.id.isNotBlank() && File(it.path).isFile && it.width > 0 && it.height > 0 }
        }
        floors.clear(); floors.addAll(loaded)
        // Files nobody lists any more (a log out cleared the list) go.
        val keep = loaded.map { File(it.path).name }.toSet()
        dir(context).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        chosen.value = prefs.getString("chosen", null)?.takeIf { id -> loaded.any { it.id == id } }
        publish()
    }

    private fun save(context: Context) {
        val list = JSONArray()
        floors.forEach { list.put(JSONObject().put("id", it.id).put("path", it.path).put("w", it.width).put("h", it.height)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("floors", list.toString()).putString("chosen", chosen.value).apply()
    }

    private fun publish() {
        val floor = floors.firstOrNull { it.id == chosen.value }
        engine?.invoke(floor?.path.orEmpty(), floor?.width ?: 0, floor?.height ?: 0)
    }

    /** The photo shrunk and saved; null when it could not be read. Off the main thread. */
    fun add(context: Context, uri: Uri): Floor? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: return null
        // Turned the way the camera held it.
        val turn = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (turn != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(turn) }
            val next = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (next !== bitmap) bitmap.recycle()
            bitmap = next
        }
        val id = UUID.randomUUID().toString()
        val file = File(dir(context), "$id.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val floor = Floor(id, file.absolutePath, bitmap.width, bitmap.height)
        bitmap.recycle()
        floor
    }.getOrNull()

    /** On the main thread, after [add]. */
    fun keep(context: Context, floor: Floor) {
        floors.add(floor)
        save(context)
    }

    fun choose(context: Context, id: String) {
        chosen.value = id
        save(context)
        publish()
    }

    /** Deletes these photos; true when the worn one was among them. */
    fun delete(context: Context, ids: Set<String>): Boolean {
        val gone = floors.filter { it.id in ids }
        gone.forEach { File(it.path).delete() }
        floors.removeAll(gone)
        val wornGone = chosen.value in ids
        if (wornGone) chosen.value = null
        save(context)
        publish()
        return wornGone
    }

    fun thumbnail(floor: Floor, side: Int = 256): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(floor.width, floor.height) / side) }
        BitmapFactory.decodeFile(floor.path, o)
    }.getOrNull()
}
