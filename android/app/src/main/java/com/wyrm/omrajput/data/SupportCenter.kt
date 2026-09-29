package com.wyrm.omrajput.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyrm.omrajput.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.system.exitProcess

/*
 * Help & feedback, badges and the crash watch (OM, 2026-09-29), as Wyrm iOS
 * does them (`WyrmSupport.swift`, `WyrmProfile.swift`).
 *
 * - A crash is kept on the phone and nothing leaves on its own: the next
 *   launch says Wyrm closed unexpectedly and asks. "Always send" skips the
 *   question from then on.
 * - Java crashes are written by the uncaught-exception handler; native
 *   crashes (the C engine) and "Wyrm isn't responding" come from Android's own
 *   record of how the last run ended (ApplicationExitInfo, Android 11+).
 * - Reports carry the app and Android version, the phone, the screen and the
 *   last lines of Wyrm's own log, with tokens, passwords, keys and emails
 *   redacted. Backend: `backend/src/support.mjs`.
 */

/* ------------------------------------------------------------------ models */

data class Badge(
    val id: String,
    val title: String,
    val detail: String,
    val earned: Boolean,
    val progress: Int,
    val goal: Int,
) {
    val fraction: Float get() = if (goal > 0) (progress.toFloat() / goal).coerceIn(0f, 1f) else 0f
}

data class BadgeBook(val badges: List<Badge>, val trailCount: Int, val beads: Int)

data class SupportReport(
    val id: String,
    val kind: String,
    val status: String,
    val message: String,
    val reply: String,
    val createdAt: String,
    val updatedAt: String,
)

internal fun JSONObject.toBadgeBook(): BadgeBook {
    val rows = optJSONArray("badges")
    return BadgeBook(
        badges = (0 until (rows?.length() ?: 0)).map { index ->
            val row = rows!!.getJSONObject(index)
            Badge(
                id = row.optString("id"),
                title = row.optString("title"),
                detail = row.optString("detail"),
                earned = row.optBoolean("earned"),
                progress = row.optInt("progress"),
                goal = row.optInt("goal"),
            )
        },
        trailCount = optInt("trailCount"),
        beads = optInt("beads"),
    )
}

internal fun BadgeBook.toJson(): JSONObject = JSONObject()
    .put("trailCount", trailCount)
    .put("beads", beads)
    .put("badges", JSONArray().apply {
        badges.forEach {
            put(JSONObject().put("id", it.id).put("title", it.title).put("detail", it.detail)
                .put("earned", it.earned).put("progress", it.progress).put("goal", it.goal))
        }
    })

internal fun JSONObject.toSupportReport() = SupportReport(
    id = optString("id"),
    kind = optString("kind"),
    status = optString("status"),
    message = optString("message"),
    reply = optString("reply").takeIf { it != "null" }.orEmpty(),
    createdAt = optString("createdAt"),
    updatedAt = optString("updatedAt"),
)

internal fun SupportReport.toJson(): JSONObject = JSONObject()
    .put("id", id).put("kind", kind).put("status", status).put("message", message)
    .put("reply", reply).put("createdAt", createdAt).put("updatedAt", updatedAt)

enum class SupportKind(val key: String, val title: String, val pageTitle: String, val question: String, val placeholder: String) {
    BUG("bug", "Problem", "Report a problem", "What went wrong?",
        "What you did, what you expected, and what happened instead."),
    SUGGESTION("suggestion", "Idea", "Suggest an idea", "What would make Wyrm better?",
        "A feature, a skin, a mode, a small thing that bugs you. Every idea is read."),
    HELP("help", "Help", "Ask for help", "What do you need help with?",
        "Ask anything about Wyrm: your account, skins, arenas, backups."),
    OTHER("other", "Other", "Something else", "What's on your mind?", "Tell us anything.");

    /** Problems and help questions are hard to answer without the device. */
    val attachesByDefault: Boolean get() = this == BUG || this == HELP

    companion object {
        fun of(key: String): SupportKind = values().firstOrNull { it.key == key } ?: BUG
        fun title(key: String): String = when (key) {
            "crash" -> "Crash"
            "drop" -> "Arena drop"
            else -> values().firstOrNull { it.key == key }?.title ?: "Report"
        }
    }
}

/* --------------------------------------------------------------- redaction */

object SupportRedact {
    private val rules = listOf(
        Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}") to "[token]",
        Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+") to "Bearer [token]",
        Regex("(?i)\\b(password|passwd|pwd|secret|auth[_-]?key|api[_-]?key|access[_-]?token|token|team[_-]?id|key)([\"'\\s]*[:=]\\s*[\"']?)[^\\s\"',&;]+") to "$1$2[hidden]",
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----") to "[private key]",
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "[email]",
    )

    /** For logs and stacks. A player's own message is sent as they wrote it. */
    fun clean(text: String): String = rules.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
}

/* ------------------------------------------------------------ device facts */

object SupportContext {
    fun current(context: Context, screen: String): Map<String, String> = mapOf(
        "platform" to "android",
        "appVersion" to BuildConfig.VERSION_NAME,
        "build" to BuildConfig.VERSION_CODE.toString(),
        "osVersion" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "device" to "${Build.MANUFACTURER} ${Build.MODEL}".take(120),
        "screen" to screen.take(80),
        "locale" to Locale.getDefault().toLanguageTag(),
    )

    fun minimal(): Map<String, String> = mapOf(
        "platform" to "android",
        "appVersion" to BuildConfig.VERSION_NAME,
        "build" to BuildConfig.VERSION_CODE.toString(),
    )

    /**
     * Wyrm's own log tags: the engine (SDL_Log is `SDL/APP`), the SDL shell,
     * and the Java/Kotlin side. `AndroidRuntime` carries a Java crash's stack.
     */
    private val OWN_TAGS = listOf("SDL/APP", "SDL", "Wyrm", "WyrmMessaging", "WyrmUpdater", "WyrmBackup", "AndroidRuntime")

    private fun logcat(vararg args: String): String = runCatching {
        val process = ProcessBuilder(listOf("logcat", "-d", "-v", "time") + args)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.destroy()
        text
    }.getOrDefault("")

    /**
     * The newest lines of this app's own log. Android lets an app read only
     * its own lines, and the buffer still holds the last run's for a while.
     *
     * Wyrm's own tags come first, so system noise cannot push them out, then
     * the general tail in whatever room is left. The whole stays under [limit]
     * (the backend keeps the last 120 KB, so going over would cut the focused
     * block, not the tail).
     */
    suspend fun recentLog(maxLines: Int = 600, limit: Int = 100_000): String = withContext(Dispatchers.IO) {
        runCatching {
            val focusedHead = "--- Wyrm's own log ---\n"
            val tailHead = "\n--- Everything (newest lines) ---\n"
            val focused = SupportRedact.clean(
                logcat("-t", "2000", "-s", *OWN_TAGS.map { "$it:V" }.toTypedArray()),
            ).takeLast(limit * 3 / 5)
            val room = (limit - focusedHead.length - focused.length - tailHead.length).coerceAtLeast(0)
            val tail = SupportRedact.clean(logcat("-t", maxLines.toString())).takeLast(room)
            (focusedHead + focused + tailHead + tail).take(limit)
        }.getOrDefault("")
    }
}

/* ------------------------------------------------------------- crash watch */

data class CrashRecord(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    /** "java", "native", "anr". */
    val cause: String,
    val title: String,
    val stack: String,
    val appVersion: String,
    val build: Int,
    val sent: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("at", at).put("cause", cause).put("title", title)
        .put("stack", stack).put("appVersion", appVersion).put("build", build).put("sent", sent)

    companion object {
        fun from(json: JSONObject) = CrashRecord(
            id = json.optString("id"), at = json.optLong("at"), cause = json.optString("cause"),
            title = json.optString("title"), stack = json.optString("stack"), appVersion = json.optString("appVersion"),
            build = json.optInt("build"), sent = json.optBoolean("sent"),
        )
    }
}

object CrashWatch {
    private const val PREFS = "wyrm_crash"
    private const val KEY_LAST = "last"
    private const val KEY_EXIT_SEEN = "exit_seen"
    private const val KEY_AUTO = "auto_send"

    /** The crash the launch prompt is asking about. */
    var prompt by mutableStateOf<CrashRecord?>(null)
        private set
    /** The newest crash, kept so it can still be sent from Settings. */
    var last by mutableStateOf<CrashRecord?>(null)
        private set
    var toast by mutableStateOf("")
    var autoSend by mutableStateOf(false)
        private set
    /** Where the player is, for the report. */
    @Volatile var screen: String = "Launch"

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun javaFile(context: Context) = File(context.filesDir, "wyrm-crash/java.txt")

    /** Once, first thing in the activity: read what the last run left, then arm. */
    @JvmStatic
    fun install(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val prefs = prefs(app)
        autoSend = prefs.getBoolean(KEY_AUTO, false)
        last = prefs.getString(KEY_LAST, null)?.let { runCatching { CrashRecord.from(JSONObject(it)) }.getOrNull() }
        runCatching { collectLeftovers(app) }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val file = javaFile(app)
                file.parentFile?.mkdirs()
                file.writeText(
                    "${error.javaClass.name}: ${error.message.orEmpty()}\n" +
                        "thread: ${thread.name}\nbuild: ${BuildConfig.VERSION_CODE}\n" +
                        Log.getStackTraceString(error),
                )
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else { Process.killProcess(Process.myPid()); exitProcess(10) }
        }
    }

    fun applyAutoSend(on: Boolean) {
        autoSend = on
        appContext?.let { prefs(it).edit().putBoolean(KEY_AUTO, on).apply() }
    }

    private fun collectLeftovers(context: Context) {
        val prefs = prefs(context)
        var record: CrashRecord? = null
        val file = javaFile(context)
        if (file.exists()) {
            val text = runCatching { file.readText() }.getOrDefault("")
            file.delete()
            if (text.isNotBlank()) {
                val build = Regex("build: (\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: BuildConfig.VERSION_CODE
                record = CrashRecord(cause = "java", title = text.lineSequence().first().take(160), stack = text,
                    appVersion = BuildConfig.VERSION_NAME, build = build, at = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis())
            }
        }
        // Android's own record of how the last runs ended: native crashes and
        // freezes the Java handler never sees.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val exits = runCatching { manager?.getHistoricalProcessExitReasons(context.packageName, 0, 8) }.getOrNull().orEmpty()
            val newest = exits.maxOfOrNull { it.timestamp } ?: 0L
            val seen = prefs.getLong(KEY_EXIT_SEEN, -1L)
            // The first launch with the watch only records where history starts:
            // an old crash from before this build is not asked about.
            if (seen >= 0 && record == null) {
                val crash = exits
                    .filter { it.timestamp > seen }
                    .filter { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_ANR || it.reason == ApplicationExitInfo.REASON_CRASH }
                    .maxByOrNull { it.timestamp }
                if (crash != null) record = recordFrom(crash)
            }
            if (newest > 0) prefs.edit().putLong(KEY_EXIT_SEEN, maxOf(newest, seen)).apply()
            else if (seen < 0) prefs.edit().putLong(KEY_EXIT_SEEN, 0L).apply()
        }
        if (record != null) {
            keep(record)
            prompt = record
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun recordFrom(info: ApplicationExitInfo): CrashRecord {
        val cause = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
            ApplicationExitInfo.REASON_ANR -> "anr"
            else -> "java"
        }
        val trace = if (info.reason == ApplicationExitInfo.REASON_ANR) {
            runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty().take(60_000)
        } else ""
        val title = when (cause) {
            "native" -> "Native crash${info.description?.let { ": $it" }.orEmpty()}"
            "anr" -> "Wyrm stopped responding${info.description?.let { ": $it" }.orEmpty()}"
            else -> info.description ?: "Crash"
        }
        val stack = buildString {
            append("reason: ").append(cause).append('\n')
            append("status: ").append(info.status).append('\n')
            append("importance: ").append(info.importance).append('\n')
            info.description?.let { append("description: ").append(it).append('\n') }
            append("pss: ").append(info.pss).append(" rss: ").append(info.rss).append('\n')
            if (trace.isNotBlank()) append('\n').append(trace)
        }
        return CrashRecord(cause = cause, title = title.take(160), stack = stack, at = info.timestamp,
            appVersion = BuildConfig.VERSION_NAME, build = BuildConfig.VERSION_CODE)
    }

    private fun keep(record: CrashRecord) {
        last = record
        appContext?.let { prefs(it).edit().putString(KEY_LAST, record.toJson().toString()).apply() }
    }

    /** With "Always send" on, the report goes and the prompt never shows. */
    fun launchCheck(repository: WyrmRepository) {
        val record = prompt ?: return
        if (!autoSend) return
        prompt = null
        send(repository, record, "") { ok -> if (ok) toast = "Crash report sent. Thank you." }
    }

    fun dismissPrompt() { prompt = null }

    fun send(repository: WyrmRepository, record: CrashRecord, note: String, done: (Boolean) -> Unit) {
        val context = appContext ?: return done(false)
        scope.launch {
            val ok = try {
                val facts = SupportContext.current(context, screen) + mapOf(
                    "crashCause" to record.cause,
                    "crashedBuild" to record.build.toString(),
                    "crashedAt" to java.time.Instant.ofEpochMilli(record.at).toString(),
                )
                val logs = SupportContext.recentLog()
                repository.submitSupport("crash", note.trim(), facts, stack = record.stack.ifBlank { record.title }, logs = logs)
                keep(record.copy(sent = true))
                if (prompt?.id == record.id) prompt = null
                true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                Log.w("Wyrm", "crash report not sent: ${failure.message}")
                false
            }
            done(ok)
        }
    }
}

/* ------------------------------------------------------ Help & feedback */

object SupportStore {
    private const val SEEN = "wyrm_support_seen"
    var repository: WyrmRepository? = null
    var cache: SocialCache? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var reports by mutableStateOf<List<SupportReport>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    private var seenRevision by mutableStateOf(0)

    /**
     * The last real screen before Help & feedback, for a manual report.
     * The overlay writes it on every route change outside Help.
     */
    @Volatile var screenBefore: String = "Launch"

    fun attach(context: Context, repository: WyrmRepository, cache: SocialCache) {
        appContext = context.applicationContext
        this.repository = repository
        this.cache = cache
        // The cached reports at launch, so the Settings badge shows at once.
        if (repository.hasSession && reports.isEmpty()) {
            cache.supportReports()?.let { reports = it; loaded = true }
        }
    }

    private fun stamp(report: SupportReport) = "${report.id}|${report.updatedAt}"

    /** Replies the player has not opened yet. */
    val unseenReplies: Int
        get() {
            seenRevision
            val seen = appContext?.getSharedPreferences(SEEN, Context.MODE_PRIVATE)?.getStringSet("seen", emptySet()).orEmpty()
            return reports.count { it.reply.isNotBlank() && stamp(it) !in seen }
        }

    fun markRepliesSeen() {
        val stamps = reports.filter { it.reply.isNotBlank() }.map(::stamp).takeLast(200).toSet()
        appContext?.getSharedPreferences(SEEN, Context.MODE_PRIVATE)?.edit()?.putStringSet("seen", stamps)?.apply()
        seenRevision++
    }

    fun reset() {
        reports = emptyList()
        loaded = false
        error = ""
    }

    fun refresh() {
        val repo = repository ?: return
        if (!loaded && reports.isEmpty()) {
            cache?.supportReports()?.let { reports = it; loaded = true }
        }
        if (loading || !repo.hasSession) { loaded = true; return }
        loading = true
        scope.launch {
            try {
                val fresh = repo.mySupport()
                reports = fresh
                cache?.saveSupportReports(fresh)
                error = ""
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                if (reports.isEmpty()) error = readable(failure)
            } finally {
                loading = false
                loaded = true
            }
        }
    }

    /** A report written in Help & feedback; [done] gets null when it arrived, or why not. */
    fun send(kind: SupportKind, message: String, attach: Boolean, screen: String, done: (String?) -> Unit) {
        val repo = repository ?: return done("Something went wrong. Try again.")
        val context = appContext ?: return done("Something went wrong. Try again.")
        scope.launch {
            try {
                val facts = if (attach) SupportContext.current(context, screen) else SupportContext.minimal()
                val logs = if (attach) SupportContext.recentLog() else ""
                repo.submitSupport(kind.key, message, facts, logs = logs)
                done(null)
                refresh()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                done(readable(failure))
            }
        }
    }

    internal fun readable(failure: Throwable): String = when {
        failure is ApiException -> when (failure.message) {
            "HTTP_429" -> "You've sent a lot in a short time. Try again in a little while."
            "EMPTY_REPORT" -> "Write a few words first."
            "INVALID_REPORT", "HTTP_413" -> "That report is too long. Shorten it and try again."
            else -> "Something went wrong. Try again."
        }
        failure is java.io.IOException -> "Could not reach Wyrm. Check your connection and try again."
        else -> "Something went wrong. Try again."
    }
}

/* ------------------------------------------------------------------ badges */

object BadgeStore {
    var repository: WyrmRepository? = null
    var cache: SocialCache? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val books = mutableStateMapOf<String, BadgeBook>()
    private val loading = mutableSetOf<String>()

    fun load(id: String) {
        val repo = repository ?: return
        if (id.isBlank()) return
        if (books[id] == null) cache?.badges(id)?.let { books[id] = it }
        if (!loading.add(id)) return
        scope.launch {
            try {
                val fresh = repo.badges(id)
                if (books[id] != fresh) books[id] = fresh
                cache?.saveBadges(id, fresh)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
            } finally {
                loading.remove(id)
            }
        }
    }

    fun reset() {
        books.clear()
        loading.clear()
    }
}
