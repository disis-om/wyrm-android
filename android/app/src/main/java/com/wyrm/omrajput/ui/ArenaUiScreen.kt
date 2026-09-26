package com.wyrm.omrajput.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting

enum class ControlsWorkspaceTab(val label: String) {
    CONTROLS("Controls"),
    BUTTONS("On-screen buttons"),
    ARENA_UI("Arena UI"),
}

@Composable
internal fun ControlsWorkspaceTabs(
    selected: ControlsWorkspaceTab,
    onSelect: (ControlsWorkspaceTab) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        PaperSegmented(
            options = ControlsWorkspaceTab.entries.map { it.label },
            selected = selected.ordinal,
            onSelect = { onSelect(ControlsWorkspaceTab.entries[it]) },
        )
    }
}

/** Screen-space arena furniture only; controls and buttons keep their own tabs. */
@Composable
fun ArenaUiScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
    onEditLayout: () -> Unit,
    onResetLayout: () -> Unit,
    workspaceTab: ControlsWorkspaceTab,
    onWorkspaceTab: (ControlsWorkspaceTab) -> Unit,
    contentOnly: Boolean = false,
) {
    val appearance = listOf(
        "general.minimap_size",
        "general.lb_font",
        "general.stats_font",
    ).mapNotNull { settings.named(it) }

    SettingsDrillScaffold(
        title = "Controls",
        parent = backLabel,
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
        sectionTabs = {
            ControlsWorkspaceTabs(selected = workspaceTab, onSelect = onWorkspaceTab)
        },
        contentOnly = contentOnly,
    ) {
        SettingsSectionLabel("Arena HUD", top = 18.dp)
        SettingsCard {
            Text(
                text = "Move every screen-space element without changing the arena beneath it.",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Medium,
                fontSize = 15.5.sp,
                lineHeight = 21.sp,
                color = Wyrm.Ink,
                modifier = Modifier.padding(14.dp),
            )
        }

        SettingsSectionLabel("Size and type")
        SettingsCard {
            appearance.forEachIndexed { index, setting ->
                SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
            }
        }
        SettingsCaption("These are the same saved values shown in Settings › Display. Changes stay synchronized.")

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
            PaperPrimaryButton(label = "Arrange arena UI", onClick = onEditLayout)
            Spacer(Modifier.height(9.dp))
            PaperOutlineButton(label = "Reset arena positions", onClick = onResetLayout)
        }
        SettingsCaption("Leaderboard, stats, minimap, team roster and chat can each be placed independently in landscape.")
    }
}
