package com.wyrm.omrajput.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyrm.omrajput.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings › Developer (OM, 2026-11-07: "same as iOS on Android"). iOS twin:
 * `@AppStorage("wyrm.ios.developer-mode")` in WyrmSettingsPages.swift.
 *
 * The switch is saved in `wyrm_developer` (`on`) and goes with the account,
 * this platform's document (AccountSync.FILES), as iOS's does.
 */
object DeveloperMode {
    const val PREFS = "wyrm_developer"
    private const val KEY_ON = "on"

    var on by mutableStateOf(false)
        private set

    /** Activity start, and again after a log in / log out rewrote the file. */
    fun reload(context: Context) {
        on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
    }

    fun set(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, value).apply()
    }
}

/**
 * Wyrm's own log kept on the phone, like iOS's WyrmDiagnostics: 7 days, 2 MB
 * at most, shown and shared from Settings › Developer › Wyrm logs.
 *
 * Android keeps an app's log lines only in a small ring buffer, so they are
 * copied into `files/wyrm-diagnostics/log.txt` each time the app goes to the
 * background and whenever the page is opened or refreshed: the lines since
 * the last copy (`logcat -v year -T`), redacted like a support report
 * ([SupportRedact]). Tokens, passwords and private messages are never written
 * by Wyrm's logging in the first place. Not account data: kept through log
 * out like the device's own files, gone when the app is deleted.
 */
object WyrmDiagnostics {
    const val RETENTION_DAYS = 7
    const val MAX_BYTES = 2 * 1024 * 1024
    const val SHOWN_LINES = 2000

    var text by mutableStateOf("Loading Wyrm diagnostics…")
        private set
    var byteCount by mutableIntStateOf(0)
        private set

    private val lock = Any()
    private val stamp = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}")

    private fun folder(c: Context) = File(c.filesDir, "wyrm-diagnostics").apply { mkdirs() }
    private fun logFile(c: Context) = File(folder(c), "log.txt")
    private fun markFile(c: Context) = File(folder(c), "last-copied.txt")

    fun record(message: String) {
        Log.i("Wyrm", "DIAGNOSTICS $message")
    }

    private fun logcat(args: List<String>): String = runCatching {
        val process = ProcessBuilder(listOf("logcat", "-d", "-v", "year") + args)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.destroy()
        out
    }.getOrDefault("")

    /** The lines since the last copy, appended; then old and excess lines go. Any thread. */
    fun capture(c: Context) = synchronized(lock) {
        runCatching {
            val mark = markFile(c)
            val since = if (mark.exists()) mark.readText().trim() else ""
            val raw = if (since.matches(stamp)) logcat(listOf("-T", since)) else logcat(listOf("-t", "4000"))
            val fresh = raw.lineSequence()
                .filter { line -> stamp.containsMatchIn(line) && (since.isEmpty() || line.take(23) > since) }
                .toList()
            if (fresh.isNotEmpty()) {
                logFile(c).appendText(SupportRedact.clean(fresh.joinToString("\n")) + "\n")
                mark.writeText(fresh.last().take(23))
            }
            trim(c)
        }
    }

    /** Drops lines older than [RETENTION_DAYS], then the oldest until under [MAX_BYTES]. */
    private fun trim(c: Context) {
        val file = logFile(c)
        if (!file.exists()) return
        val cutoff = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .format(Date(System.currentTimeMillis() - RETENTION_DAYS * 86_400_000L))
        var lines = file.readLines().filter { it.length < 23 || !stamp.containsMatchIn(it) || it.take(23) >= cutoff }
        var size = lines.sumOf { it.toByteArray().size + 1 }
        var drop = 0
        while (size > MAX_BYTES && drop < lines.size) {
            size -= lines[drop].toByteArray().size + 1
            drop++
        }
        if (drop > 0) lines = lines.drop(drop)
        file.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n")
    }

    /** Background thread: the activity went to the background. */
    @JvmStatic
    fun captureLater(c: Context) {
        val app = c.applicationContext
        Thread({ capture(app) }, "WyrmDiagnostics").start()
    }

    /** Any thread: copy the newest lines, then publish the stored log. */
    fun refresh(c: Context) {
        capture(c)
        val file = logFile(c)
        val all = if (file.exists()) file.readText() else ""
        byteCount = all.toByteArray().size
        text = all
    }

    fun clear(c: Context) = synchronized(lock) {
        logFile(c).delete()
        byteCount = 0
        text = ""
        record("stored logs cleared")
    }

    /** A copy for the share sheet, with a header like iOS's export. */
    fun exportFile(c: Context): File? = runCatching {
        refresh(c)
        val dir = File(c.cacheDir, "wyrm-logs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "Wyrm-diagnostics-${System.currentTimeMillis() / 1000}.txt"
        val out = File(dir, name)
        out.writeText(
            buildString {
                append("Wyrm diagnostics\n")
                append("platform: android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                append("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                append("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                append("retention: $RETENTION_DAYS days, 2 MB\n\n")
                append(text)
            },
        )
        out
    }.getOrNull()
}
