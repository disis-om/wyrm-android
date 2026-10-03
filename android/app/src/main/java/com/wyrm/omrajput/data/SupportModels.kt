package com.wyrm.omrajput.data

import org.json.JSONArray
import org.json.JSONObject

// Help & feedback and badge models, shared with Wyrm Desktop (see SupportCenter.kt).

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
