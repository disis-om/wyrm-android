package com.wyrm.omrajput.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyrm.omrajput.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The developer's notice (OM, 2026-10-25; backend notices.mjs): one popup in
 * the middle of the screen when Wyrm starts. iOS twin: WyrmNotice.swift.
 *
 * Asked once per start (`GET /v1/notice`, no account needed). Per notice id
 * this phone keeps how many starts showed it and whether the player chose
 * "Don't show again": the first start shows OK only, every later start shows
 * "Don't show again" too, and once that is ticked the notice never shows
 * again. A new notice has a new id, so it starts over. Prefs `wyrm_notice`,
 * phone only (KEEP: a log out does not bring a dismissed notice back).
 */
/**
 * One block of the Observatory's builder (OM, 2026-10-30; backend notices.mjs
 * NOTICE_BLOCK). Sizes, widths and colours mean the same on Android, iOS and
 * in the Observatory's preview. An unknown block type is skipped.
 */
sealed class NoticeBlock {
    data class Heading(val text: String, val size: Int, val align: String, val color: String) : NoticeBlock()
    data class Paragraph(val text: String, val size: String, val align: String, val color: String) : NoticeBlock()
    data class Picture(val url: String, val alt: String, val width: Float, val align: String, val radius: Float) : NoticeBlock()
    data class Button(val label: String, val url: String, val style: String, val width: Float, val align: String) : NoticeBlock()
    object Divider : NoticeBlock()
    data class Space(val height: Float) : NoticeBlock()
    data class Items(val items: List<String>, val ordered: Boolean, val color: String) : NoticeBlock()
    data class Quote(val text: String, val color: String) : NoticeBlock()

    companion object {
        fun from(json: JSONObject): NoticeBlock? = when (json.optString("t")) {
            "heading" -> Heading(json.optString("text"), json.optInt("size", 2).coerceIn(1, 3), json.optString("align", "left"), json.optString("color", "ink"))
            "text" -> Paragraph(json.optString("text"), json.optString("size", "m"), json.optString("align", "left"), json.optString("color", "ink"))
            "image" -> json.optString("url").takeIf { it.isNotBlank() }?.let {
                Picture(it, json.optString("alt"), json.optDouble("width", 100.0).toFloat(), json.optString("align", "center"), json.optDouble("radius", 12.0).toFloat())
            }
            "button" -> Button(json.optString("label"), json.optString("url"), json.optString("style", "filled"),
                json.optDouble("width", 100.0).toFloat(), json.optString("align", "center"))
            "divider" -> Divider
            "space" -> Space(json.optDouble("height", 16.0).toFloat())
            "list" -> Items(json.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
                json.optBoolean("ordered"), json.optString("color", "ink"))
            "quote" -> Quote(json.optString("text"), json.optString("color", "ink"))
            else -> null
        }
    }
}

data class AppNotice(
    val id: String,
    val kind: String,
    val label: String,
    val title: String,
    val markdown: String,
    /** The builder's layout; empty = the Markdown message. */
    val blocks: List<NoticeBlock> = emptyList(),
) {
    /** The small line above the heading. */
    val kicker: String
        get() = when (kind) {
            "developer" -> "FROM THE DEVELOPER"
            "message" -> "MESSAGE"
            "update" -> "UPDATE"
            "custom" -> label.uppercase().ifBlank { "NOTICE" }
            else -> "NOTICE"
        }
}

object NoticeCenter {
    const val PREFS = "wyrm_notice"
    // The single-notice keys of 2026-10-25, still read for the notice they named.
    private const val KEY_ID = "id"
    private const val KEY_SHOWN = "shown"
    private const val KEY_NEVER = "never"

    /** The notice on screen now, or null. */
    var shown by mutableStateOf<AppNotice?>(null)
        private set
    /** From the second start: "Don't show again" is offered. */
    var offerNeverAgain by mutableStateOf(false)
        private set
    /** "2 of 3" when several notices are live (OM, 2026-10-30), else blank. */
    var position by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var asked = false
    /* Several notices can be live at once (OM, 2026-10-30): the ones this
       start still has to show, in the server's order. */
    private var queue: List<AppNotice> = emptyList()
    private var total = 0
    private var index = 0

    private fun shownKey(id: String) = "n.$id.shown"
    private fun neverKey(id: String) = "n.$id.never"

    private fun shownCount(prefs: android.content.SharedPreferences, id: String): Int =
        if (prefs.contains(shownKey(id))) prefs.getInt(shownKey(id), 0)
        else if (prefs.getString(KEY_ID, null) == id) prefs.getInt(KEY_SHOWN, 0) else 0

    private fun never(prefs: android.content.SharedPreferences, id: String): Boolean =
        if (prefs.contains(neverKey(id))) prefs.getBoolean(neverKey(id), false)
        else prefs.getString(KEY_ID, null) == id && prefs.getBoolean(KEY_NEVER, false)

    /** Once per start. */
    fun check(context: Context) {
        if (asked) return
        asked = true
        val app = context.applicationContext
        scope.launch {
            val notices = withContext(Dispatchers.IO) { fetch() }
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            queue = notices.filterNot { never(prefs, it.id) }
            total = queue.size
            index = 0
            next(prefs)
        }
    }

    private fun next(prefs: android.content.SharedPreferences) {
        val notice = queue.getOrNull(index)
        if (notice == null) { shown = null; position = ""; return }
        offerNeverAgain = shownCount(prefs, notice.id) >= 1
        position = if (total > 1) "${index + 1} of $total" else ""
        shown = notice
    }

    /** OK: counts this start; with [never] ticked the notice is done for good. Then the next one. */
    fun close(context: Context, never: Boolean) {
        val notice = shown ?: return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(shownKey(notice.id), shownCount(prefs, notice.id) + 1)
            .putBoolean(neverKey(notice.id), never && offerNeverAgain)
            .apply()
        shown = null
        index += 1
        scope.launch { kotlinx.coroutines.delay(260); next(prefs) }
    }

    private fun JSONObject.toNotice(): AppNotice? {
        val id = optString("id")
        return if (id.isBlank()) null else AppNotice(
            id = id,
            kind = optString("kind", "notice"),
            label = optString("label"),
            title = optString("title"),
            markdown = optString("markdown"),
            blocks = optJSONArray("blocks")?.let { list ->
                (0 until list.length()).mapNotNull { index -> list.optJSONObject(index)?.let(NoticeBlock::from) }
            }.orEmpty(),
        )
    }

    private fun fetch(): List<AppNotice> = try {
        val base = BuildConfig.WYRM_API_URL.trimEnd('/')
        val connection = URL("$base/v1/notice").openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != 200) emptyList()
            else {
                val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val all = body.optJSONArray("notices")
                if (all != null) (0 until all.length()).mapNotNull { all.optJSONObject(it)?.toNotice() }
                else listOfNotNull(body.optJSONObject("notice")?.toNotice())
            }
        } finally {
            connection.disconnect()
        }
    } catch (failure: Exception) {
        Log.w("Wyrm", "notice not read: ${failure.message}")
        emptyList()
    }
}
