package com.wyrm.omrajput.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/*
 * A crash kept for its report, and Help & feedback's store. Moved out of
 * SupportCenter.kt unchanged (2026-10-12) so Wyrm Desktop compiles the same
 * code; SupportCenter.kt keeps the Android-only parts (SupportContext,
 * CrashWatch).
 */

/* ------------------------------------------------------------- crash record */

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
    /** A reply the player has not seen yet (the badge trail's last mark). */
    fun isUnseen(report: SupportReport): Boolean {
        if (report.reply.isBlank()) return false
        val seen = appContext?.getSharedPreferences(SEEN, Context.MODE_PRIVATE)?.getStringSet("seen", emptySet()).orEmpty()
        return stamp(report) !in seen
    }

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
