package com.wyrm.omrajput.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyrm.omrajput.data.WyrmSound

/** Settings › Music and sounds (OM, 2026-11-08): AIR's own, as in its settings. */
@Composable
internal fun SoundSettingsSection() {
    SettingsSectionLabel("Music and sounds", top = 0.dp)
    SettingsCard {
        SettingsBoolRow(
            title = "Music",
            detail = "Slither's music: the menu tune everywhere outside a match, game songs from Play on.",
            on = WyrmSound.musicOn, first = true, onToggle = WyrmSound::applyMusic,
        )
        SettingsBoolRow(
            title = "New music",
            detail = "Off plays slither's old game tune instead of the eight new songs.",
            on = WyrmSound.newMusic, first = false, onToggle = WyrmSound::applyNewMusic,
        )
        SettingsBoolRow(
            title = "Sounds",
            detail = "Eating, boosting, and snakes dying on your screen (zoom out and you hear more of them).",
            on = WyrmSound.soundsOn, first = false, onToggle = WyrmSound::applySounds,
        )
        SettingsBoolRow(
            title = "New sounds",
            detail = "Off plays slither's old eating sounds.",
            on = WyrmSound.newSounds, first = false, onToggle = WyrmSound::applyNewSounds,
        )
    }
    Spacer(Modifier.height(22.dp))
}
