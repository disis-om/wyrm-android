package com.wyrm.omrajput.data

import android.content.Context
import com.wyrm.omrajput.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ReleaseNotes(
    val versionName: String,
    val title: String,
    val markdown: String,
)

/**
 * Exact-version release notes and their one local acknowledgement.
 *
 * The release host is deliberately an implementation detail. The player sees
 * Wyrm versions, never where they are published. A failed fetch is never
 * acknowledged: the next launch may try again instead of permanently losing
 * the notes because the phone happened to be offline once.
 */
class ReleaseNotesRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val repository = BuildConfig.WYRM_RELEASE_NOTES_REPO.also {
        require(REPOSITORY.matches(it)) { "Release notes source is invalid." }
    }

    fun needsAcknowledgement(versionCode: Int): Boolean =
        preferences.getInt(acknowledgedKey(), 0) < versionCode

    fun acknowledge(versionCode: Int) {
        if (versionCode <= 0) return
        val previous = preferences.getInt(acknowledgedKey(), 0)
        if (versionCode > previous) {
            preferences.edit().putInt(acknowledgedKey(), versionCode).apply()
        }
    }

    suspend fun fetch(versionName: String): ReleaseNotes = withContext(Dispatchers.IO) {
        require(VERSION.matches(versionName)) { "That version is not valid." }
        val cached = cached(versionName)
        try {
            val encodedTag = URLEncoder.encode("v$versionName", StandardCharsets.UTF_8.name())
            val url = URL(
                "https://api.github.com/repos/$repository/releases/tags/$encodedTag"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Wyrm-Android-Release-Notes/1.0")
                preferences.getString(cacheKey(versionName, "etag"), null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("If-None-Match", it) }
            }
            try {
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) {
                    return@withContext cached
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException(if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                        "Release notes are not available for Wyrm $versionName yet."
                    } else {
                        "Release notes could not be reached."
                    })
                }
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RESPONSE_BYTES) {
                            throw IllegalStateException("Release notes are unexpectedly large.")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
                if (json.optBoolean("draft", true) || json.optString("tag_name") != "v$versionName") {
                    throw IllegalStateException("Release notes do not match this Wyrm version.")
                }
                val markdown = json.optString("body").trim().ifEmpty {
                    "Performance and reliability improvements."
                }
                if (markdown.length > MAX_MARKDOWN_CHARS) {
                    throw IllegalStateException("Release notes are unexpectedly large.")
                }
                val result = ReleaseNotes(
                    versionName = versionName,
                    title = json.optString("name").trim().ifEmpty { "Wyrm $versionName" },
                    markdown = markdown,
                )
                preferences.edit()
                    .putString(cacheKey(versionName, "title"), result.title)
                    .putString(cacheKey(versionName, "body"), result.markdown)
                    .putString(cacheKey(versionName, "etag"), connection.getHeaderField("ETag"))
                    .apply()
                result
            } finally {
                connection.disconnect()
            }
        } catch (failure: Throwable) {
            cached ?: throw failure
        }
    }

    private fun cached(versionName: String): ReleaseNotes? {
        val body = preferences.getString(cacheKey(versionName, "body"), null)?.takeIf { it.isNotBlank() }
            ?: return null
        return ReleaseNotes(
            versionName = versionName,
            title = preferences.getString(cacheKey(versionName, "title"), null)
                ?.takeIf { it.isNotBlank() } ?: "Wyrm $versionName",
            markdown = body,
        )
    }

    private fun cacheKey(versionName: String, part: String) =
        "release_${repository.replace('/', '_')}_${versionName.replace('.', '_')}_$part"

    private fun acknowledgedKey() =
        "${KEY_ACKNOWLEDGED_VERSION}_${repository.replace('/', '_')}"

    private companion object {
        const val PREFS = "wyrm_release_notes"
        const val KEY_ACKNOWLEDGED_VERSION = "acknowledged_version_code"
        const val CONNECT_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_RESPONSE_BYTES = 192 * 1024
        const val MAX_MARKDOWN_CHARS = 128 * 1024
        val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
        val REPOSITORY = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    }
}
