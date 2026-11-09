package com.wyrm.omrajput.i18n

import android.content.Context
import android.content.SharedPreferences
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Wyrm in the player's language (OM, 2026-10-31).
 *
 * The words are the app's own English; each language is one table in the
 * assets, `i18n/<code>.json` = {"english": "translated"}, made by
 * `tools/i18n/extract.py` + `translate.py` (the same files go to iOS). Every
 * `Text` in the app is this package's [Text], which asks [tr]: an exact line,
 * else a template ("Tag {0} is yours"), else the English as it is. Nothing is
 * fetched: no API, no network.
 *
 * The choice is "auto" (the phone's language) or a code, in prefs
 * `wyrm_language` (AccountSync SYNC: it follows the account on this
 * platform). Whether the one-time "Choose your language" prompt was shown is
 * `wyrm_language_prompt` (KEEP: never again on this phone, logged out or not).
 */
object WyrmLanguage {
    const val AUTO = "auto"
    private const val PREFS = "wyrm_language"
    private const val KEY = "language"
    private const val PROMPT_PREFS = "wyrm_language_prompt"
    private const val PROMPT_KEY = "shown"

    data class Choice(val code: String, val name: String)

    /** Every language Wyrm has a table for, in the order the list shows them. */
    var choices by mutableStateOf<List<Choice>>(emptyList())
        private set

    /** What the player chose: [AUTO] or a code. */
    var chosen by mutableStateOf(AUTO)
        private set

    /** The language shown now ("en" for English). */
    var active by mutableStateOf("en")
        private set

    /** Bumped when a table lands, so every Text reads its words again. */
    private var revision by mutableIntStateOf(0)

    @Volatile private var table: Map<String, String> = emptyMap()
    @Volatile private var templates: List<Pair<Regex, String>> = emptyList()
    private val cache = LruCache<String, String>(1024)
    private var app: Context? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** At start, before the first screen: the choice, then its table on a worker. */
    fun init(context: Context) {
        if (app != null) return
        val ctx = context.applicationContext
        app = ctx
        choices = runCatching {
            val array = JSONArray(ctx.assets.open("i18n/languages.json").bufferedReader().use { it.readText() })
            (0 until array.length()).map { array.getJSONObject(it).let { o -> Choice(o.getString("code"), o.getString("name")) } }
        }.getOrDefault(emptyList())
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        chosen = prefs.getString(KEY, AUTO) ?: AUTO
        // An account restore writes the prefs: the shown language follows it.
        listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == null || key == KEY) {
                val next = p.getString(KEY, AUTO) ?: AUTO
                if (next != chosen) { chosen = next; load() }
            }
        }.also { prefs.registerOnSharedPreferenceChangeListener(it) }
        load()
    }

    /** The player picked [code] ([AUTO] or one of [choices]). */
    fun choose(code: String) {
        val ctx = app ?: return
        chosen = code
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
        load()
    }

    /** The language "Auto" means on this phone, as a code ("en" when Wyrm has none for it). */
    fun phoneLanguage(): String {
        val locale = Locale.getDefault()
        val have = choices.map { it.code }.toSet()
        val lang = when (locale.language) {
            "in" -> "id"
            "iw" -> "he"
            "tl" -> "fil"
            else -> locale.language
        }
        val code = when (lang) {
            "pt" -> "pt-BR"
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-Hant" else "zh-Hans"
            else -> lang
        }
        return if (code in have) code else "en"
    }

    /** The name of a code in its own language ("English" for en). */
    fun nameOf(code: String): String = if (code == "en") "English" else choices.firstOrNull { it.code == code }?.name ?: code

    /** Right-to-left scripts: Arabic, Persian, Urdu. */
    val rightToLeft: Boolean get() = active in setOf("ar", "fa", "ur")

    // ---------------------------------------------------------------- prompt

    /** Whether the one-time "Choose your language" prompt is still owed on this phone. */
    fun promptOwed(context: Context): Boolean =
        !context.getSharedPreferences(PROMPT_PREFS, Context.MODE_PRIVATE).getBoolean(PROMPT_KEY, false)

    /** Shown once: never again on this phone (commit, so a crash right after cannot bring it back). */
    fun promptShown(context: Context) {
        context.getSharedPreferences(PROMPT_PREFS, Context.MODE_PRIVATE).edit().putBoolean(PROMPT_KEY, true).commit()
    }

    // ---------------------------------------------------------------- words

    /** [text] in the shown language: an exact line, a template, or the English. */
    fun tr(text: String): String {
        val seen = revision // read in composition: a new table redraws the text
        if (seen < 0 || active == "en" || text.isEmpty()) return text
        cache.get(text)?.let { return it }
        val out = table[text] ?: fromTemplate(text) ?: text
        cache.put(text, out)
        return out
    }

    private fun fromTemplate(text: String): String? {
        for ((pattern, translated) in templates) {
            val match = pattern.matchEntire(text) ?: continue
            var out = translated
            match.groupValues.drop(1).forEachIndexed { i, value -> out = out.replace("{$i}", value) }
            return out
        }
        return null
    }

    private fun load() {
        val ctx = app ?: return
        val code = if (chosen == AUTO) phoneLanguage() else chosen.takeIf { c -> choices.any { it.code == c } } ?: "en"
        if (code == "en") {
            table = emptyMap(); templates = emptyList(); cache.evictAll()
            active = "en"; revision++
            return
        }
        Thread {
            val loaded = runCatching {
                val json = JSONObject(ctx.assets.open("i18n/$code.json").bufferedReader().use { it.readText() })
                val exact = HashMap<String, String>(json.length() * 2)
                val shapes = ArrayList<Pair<Regex, String>>()
                json.keys().forEach { english ->
                    val translated = json.getString(english)
                    exact[english] = translated
                    if (HOLE.containsMatchIn(english)) shapes += patternOf(english) to translated
                }
                // Longest first, so "Tag {0} is yours. It is…" wins over a shorter shape.
                exact to shapes.sortedByDescending { it.first.pattern.length }
            }.getOrNull()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (loaded != null) {
                    table = loaded.first; templates = loaded.second; active = code
                } else {
                    table = emptyMap(); templates = emptyList(); active = "en"
                }
                cache.evictAll()
                revision++
            }
        }.apply { name = "wyrm-language"; isDaemon = true }.start()
    }

    private val HOLE = Regex("""\{\d+\}""")

    /** "Tag {0} is yours" -> ^Tag (.+?) is yours$ with the rest matched literally. */
    private fun patternOf(english: String): Regex {
        val out = StringBuilder("^")
        var last = 0
        for (m in HOLE.findAll(english)) {
            out.append(Regex.escape(english.substring(last, m.range.first)))
            out.append("(.+?)")
            last = m.range.last + 1
        }
        out.append(Regex.escape(english.substring(last))).append("$")
        return Regex(out.toString(), RegexOption.DOT_MATCHES_ALL)
    }
}

/** Short form for code outside a Text (a toast, a title string). */
fun tr(text: String): String = WyrmLanguage.tr(text)
