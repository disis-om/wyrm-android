package com.wyrm.omrajput.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wyrm.omrajput.data.Setting

private val BASIC_IDS = setOf(
    "general.snake_scores",
    "general.show_own_name",
    "general.minimap_size",
    "general.ui_font",
)

/**
 * Spec page 09 — Settings › Display.
 *
 * Basic is scores, own name, minimap, interface text. Everything else in
 * general (except bot / laser) sits under Advanced. Bot lives on its own page.
 */
@Composable
fun SettingsDisplayScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    val basic = BASIC_IDS.mapNotNull { settings.named(it) }
    val advancedRows = settings.filter { setting ->
        (setting.group == "general" || setting.group.startsWith("general.")) &&
            setting.group != "general.bot" &&
            setting.id !in BASIC_IDS &&
            setting.label.isNotBlank()
    }
    SettingsDrillScaffold(
        title = "Display",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsSectionLabel("Basic", top = 18.dp)
        SettingsCard {
            basic.forEachIndexed { index, setting ->
                SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
            }
        }
        if (advancedRows.isNotEmpty()) {
            AdvancedFold(label = "Advanced", open = advanced, onToggle = { advanced = !advanced })
            if (advanced) {
                SettingsCard {
                    advancedRows.forEachIndexed { index, setting ->
                        SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
                    }
                }
                SettingsCaption("VSync, zoom step, cursor size and after-death delay live here — the things nobody touches twice.")
            }
        }
    }
}
