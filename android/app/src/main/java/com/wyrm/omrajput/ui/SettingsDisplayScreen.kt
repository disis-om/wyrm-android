package com.wyrm.omrajput.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val basic = BASIC_IDS.mapNotNull { settings.named(it) }
    val advancedRows = settings.filter { setting ->
        (setting.group == "general" || setting.group.startsWith("general.")) &&
            setting.group != "general.bot" &&
            setting.id !in BASIC_IDS &&
            setting.id != "general.vsync" && // Settings › Performance owns it now
            setting.label.isNotBlank()
    }
    // Opened by settings search when the row it points at is inside.
    var advanced by remember { mutableStateOf(SettingsFocus.wants(advancedRows.map { it.id })) }
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
        // Look ahead (OM, 2026-10-05): moved off Controls. Same switch, both modes.
        SettingsSectionLabel("Camera")
        SettingsCard {
            Box(Modifier.settingAnchor("app.look-ahead")) { SettingsBoolRow(
                title = "Look ahead",
                detail = "Like slither: the view moves ahead of your snake, toward where it is going, and a little further while boosting.",
                on = PlayFeelStore.lookAhead,
                first = true,
                onToggle = { PlayFeelStore.applyLookAhead(it) },
            ) }
        }
        if (advancedRows.isNotEmpty()) {
            AdvancedFold(label = "Advanced", open = advanced, onToggle = { advanced = !advanced })
            if (advanced) {
                SettingsCard {
                    advancedRows.forEachIndexed { index, setting ->
                        SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
                    }
                }
                SettingsCaption("Zoom step, cursor size and after-death delay live here — the things nobody touches twice. Frame rate and vsync are in Settings › Performance.")
            }
        }
    }
}
