package com.wyrm.omrajput.data

import android.content.Context

data class VoicePreferenceState(
    val muted: Boolean = true,
    val deafened: Boolean = false,
    val volume: Float = 1f,
    val audioRoute: String = "system",
    val notifications: Boolean = true,
)

/** Durable voice choices. Verification email and live-call credentials never enter this file. */
class VoicePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(playerId: String): VoicePreferenceState {
        val prefix = "$playerId."
        return VoicePreferenceState(
            muted = prefs.getBoolean(prefix + "muted", true),
            deafened = prefs.getBoolean(prefix + "deafened", false),
            volume = prefs.getFloat(prefix + "volume", 1f).coerceIn(0f, 1f),
            audioRoute = prefs.getString(prefix + "audioRoute", "system")
                ?.takeIf { it in ROUTES } ?: "system",
            notifications = prefs.getBoolean(prefix + "notifications", true),
        )
    }

    fun write(playerId: String, state: VoicePreferenceState) {
        val prefix = "$playerId."
        prefs.edit()
            .putBoolean(prefix + "muted", state.muted)
            .putBoolean(prefix + "deafened", state.deafened)
            .putFloat(prefix + "volume", state.volume.coerceIn(0f, 1f))
            .putString(prefix + "audioRoute", state.audioRoute.takeIf { it in ROUTES } ?: "system")
            .putBoolean(prefix + "notifications", state.notifications)
            .apply()
    }

    companion object {
        const val PREFS = "wyrm_voice_preferences"
        val ROUTES = setOf("system", "speaker", "earpiece", "bluetooth")
    }
}
