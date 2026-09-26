package com.wyrm.omrajput.data

/**
 * One setting, exactly as the engine described it.
 *
 * Nothing here is hard-coded on this side: the label, the range, the options
 * and the current value all arrive from the engine's own table, so a setting
 * the app does not know about still renders correctly and a setting that is
 * removed simply stops appearing.
 */
data class Setting(
    val id: String,
    val group: String,
    val type: SettingType,
    val label: String,
    val hint: String,
    val raw: String,
    val minimum: Float,
    val maximum: Float,
    val options: List<String>,
) {
    val number: Float get() = raw.toFloatOrNull() ?: 0f
    val enabled: Boolean get() = number != 0f
    val index: Int get() = number.toInt()

    /** Colour channels, 0..1, always four long so callers need no branches. */
    val channels: List<Float>
        get() {
            val parsed = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
            return List(4) { parsed.getOrElse(it) { if (it == 3) 1f else 0f } }
        }
}

enum class SettingType { BOOL, INT, FLOAT, ENUM, COLOR3, COLOR4;

    companion object {
        fun of(name: String): SettingType = when (name) {
            "bool" -> BOOL
            "int" -> INT
            "enum" -> ENUM
            "color3" -> COLOR3
            "color4" -> COLOR4
            else -> FLOAT
        }
    }
}

/** One engine action exposed as a named button over the arena. */
data class Hotkey(
    val action: Int,
    val name: String,
    val key: Int,
    val keyName: String,
    val mode: Int,
    /** Restart, quit and zoom cannot change how they fire. */
    val fixedMode: Boolean,
    val visible: Boolean,
    val x: Float,
    val y: Float,
)

data class KeyOption(val key: Int, val name: String)

/** What the updater is doing, as the engine last reported it. */
data class UpdateState(
    val status: Int = 0,
    val progress: Int = 0,
    val title: String = "",
    val detail: String = "",
    val version: String = "",
) {
    val busy: Boolean get() = status == 1 || status == 4 || status == 5 || status == 7
    val available: Boolean get() = status == 3 || status == 6
    val failed: Boolean get() = status == 8
}

/** What the backup worker last found or changed. */
data class BackupState(
    val status: Int = 0,
    val count: Int = 0,
    val title: String = "",
    val detail: String = "",
) {
    val busy: Boolean get() = status in setOf(2, 4, 7, 12)
    val selectingFolder: Boolean get() = status == 1
    val saving: Boolean get() = status == 2
    val saved: Boolean get() = status == 3
    val found: Boolean get() = status == 5 || status == 10
    val restoreOffer: Boolean get() = status == 10
    val restoring: Boolean get() = status == 7
    val restored: Boolean get() = status == 8
    val partial: Boolean get() = status == 13
    val failed: Boolean get() = status == 9
}

/**
 * Parsers for the engine's snapshots.
 *
 * The format is deliberately plain — tab-separated fields, one record a line —
 * because the alternative was writing a JSON encoder in C for data that never
 * leaves the process.
 */
object SettingsCodec {
    fun settings(snapshot: String): List<Setting> = snapshot.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 9) return@mapNotNull null
            Setting(
                id = parts[0],
                group = parts[1],
                type = SettingType.of(parts[2]),
                label = parts[3],
                hint = parts[4],
                raw = parts[5],
                minimum = parts[6].toFloatOrNull() ?: 0f,
                maximum = parts[7].toFloatOrNull() ?: 1f,
                options = parts[8].split('|').filter { it.isNotEmpty() },
            )
        }
        .toList()

    fun hotkeys(snapshot: String): List<Hotkey> = snapshot.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 9) return@mapNotNull null
            Hotkey(
                action = parts[0].toIntOrNull() ?: return@mapNotNull null,
                name = parts[1],
                key = parts[2].toIntOrNull() ?: 0,
                keyName = parts[3],
                mode = parts[4].toIntOrNull() ?: 0,
                fixedMode = parts[5] == "1",
                visible = parts[6] == "1",
                x = parts[7].toFloatOrNull() ?: 0.5f,
                y = parts[8].toFloatOrNull() ?: 0.5f,
            )
        }
        .toList()

    fun keyOptions(snapshot: String): List<KeyOption> = snapshot.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            KeyOption(parts[0].toIntOrNull() ?: return@mapNotNull null, parts[1])
        }
        .toList()

    fun update(snapshot: String): UpdateState {
        val parts = snapshot.split('\t', limit = 9)
        if (parts.size < 5) return UpdateState()
        return UpdateState(
            status = parts[0].toIntOrNull() ?: 0,
            progress = parts[1].toIntOrNull() ?: 0,
            title = parts[2],
            detail = parts[3],
            version = parts[4],
        )
    }

    fun backup(snapshot: String): BackupState {
        val parts = snapshot.split('\t', limit = 9)
        if (parts.size < 9) return BackupState()
        return BackupState(
            status = parts[5].toIntOrNull() ?: 0,
            count = parts[6].toIntOrNull() ?: 0,
            title = parts[7],
            detail = parts[8],
        )
    }
}
