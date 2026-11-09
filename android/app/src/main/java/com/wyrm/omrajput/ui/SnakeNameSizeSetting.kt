package com.wyrm.omrajput.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wyrm.omrajput.data.Setting

/** Real bead/eye textures and the arena's baked wyrmplayer label at a fixed head reference. */
@Composable
internal fun SnakeNameSizeSetting(setting: Setting, first: Boolean, onChange: (Setting, List<Float>) -> Unit) {
    Column(Modifier.settingAnchor(setting.id)) {
        SnakeBodyPreview(mode = 0, spine = false, spineWidth = 0f, shadow = false,
            skin = SkinState(), hideCosmetics = true, nameScale = setting.number.coerceIn(0.5f, 2.5f))
        SettingsSliderRow(title = "Player names", valueText = "${(setting.number * 100).toInt()}%",
            detail = "Name size relative to the snake", value = setting.number,
            range = 0.5f..2.5f, steps = 0, first = first,
            onChange = { onChange(setting, listOf(it)) })
    }
}
