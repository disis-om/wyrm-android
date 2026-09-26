package com.wyrm.omrajput.data

import android.content.Context

/** The last five arenas entered from Play, newest first — Wyrm iOS's "Recently joined". */
object RecentArenas {
    private const val PREFS_NAME = "wyrm_recent_arenas"
    private const val KEY = "endpoints"

    fun load(context: Context): List<String> = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY, "").orEmpty().lineSequence().map(String::trim).filter { it.isNotEmpty() }.toList()

    fun push(context: Context, endpoint: String): List<String> {
        val next = (listOf(endpoint) + load(context).filterNot { it == endpoint }).take(5)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY, next.joinToString("\n")).apply()
        return next
    }
}

/** Portable, player-owned arena shortcuts. No account or team state lives here. */
object SavedArenas {
    const val PREFS_NAME = "wyrm_saved_arenas"
    const val KEY_ENDPOINTS = "endpoints"
    const val MAX_SAVED = 24

    fun load(context: Context): List<String> = clean(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINTS, "")
            .orEmpty()
            .lineSequence()
            .toList()
    )

    fun save(context: Context, endpoint: String): List<String> {
        if (!ArenaDirectory.isValidEndpoint(endpoint)) return load(context)
        val next = clean(listOf(endpoint) + load(context))
        write(context, next)
        return next
    }

    fun remove(context: Context, endpoint: String): List<String> {
        val next = load(context).filterNot { it == endpoint }
        write(context, next)
        return next
    }

    /** Used by restore after the archive has validated each endpoint. */
    fun replace(context: Context, endpoints: List<String>): List<String> {
        val next = clean(endpoints)
        write(context, next)
        return next
    }

    private fun clean(endpoints: List<String>): List<String> = endpoints
        .asSequence()
        .map(String::trim)
        .filter(ArenaDirectory::isValidEndpoint)
        .distinct()
        .take(MAX_SAVED)
        .toList()

    private fun write(context: Context, endpoints: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENDPOINTS, endpoints.joinToString("\n"))
            .apply()
    }
}
