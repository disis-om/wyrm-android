package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Hotkey
import com.wyrm.omrajput.data.Setting

/** Same allowlist as the dark keys page and the engine. */
private val OnScreenButtonOrder = listOf(1, 2, 3, 4, 6, 7, 8, 9)

/**
 * Spec page 11 — Settings › On-screen buttons.
 *
 * One row per real hotkey. Mode is a proper two-way control beside the
 * visibility switch, so firing behaviour cannot be mistaken for a caption.
 */
@Composable
fun SettingsButtonsScreen(
    buttons: List<Hotkey>,
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
    onButtonChange: (Hotkey) -> Unit,
    onEditLayout: () -> Unit,
    onResetLayout: () -> Unit,
    backLabel: String = "Settings",
    workspaceTab: ControlsWorkspaceTab? = null,
    onWorkspaceTab: (ControlsWorkspaceTab) -> Unit = {},
    contentOnly: Boolean = false,
) {
    val byAction = buttons.associateBy { it.action }
    val allowed = OnScreenButtonOrder.mapNotNull(byAction::get)
    val visibleCount = allowed.count { it.visible }
    val size = settings.named("keys.key_scale")
    val opacity = settings.named("keys.opacity")

    SettingsDrillScaffold(
        title = "On-screen buttons",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
        parent = backLabel,
        sectionTabs = workspaceTab?.let { selected ->
            { ControlsWorkspaceTabs(selected = selected, onSelect = onWorkspaceTab) }
        },
        contentOnly = contentOnly,
    ) {
        SettingsSectionLabel("Live preview", top = 18.dp)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OnScreenButtonsPreview(
                buttons = allowed.filter { it.visible },
                scale = size?.number ?: 1f,
                opacity = opacity?.number ?: 1f,
            )
        }

        SettingsSectionLabel("Buttons · $visibleCount of ${allowed.size} on", top = 18.dp)
        SettingsCard {
            allowed.forEachIndexed { index, button ->
                if (index > 0) SettingsHairline()
                ButtonLine(button = button, onChange = onButtonChange)
            }
        }

        SettingsSectionLabel("Appearance")
        SettingsCard {
            var first = true
            size?.let {
                SettingTypedRow(it, first = first, onChange = onChange)
                first = false
            }
            opacity?.let { SettingTypedRow(it, first = first, onChange = onChange) }
        }

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
            PaperPrimaryButton(
                label = "Arrange the layout",
                enabled = visibleCount > 0,
                onClick = onEditLayout,
            )
            Spacer(Modifier.height(9.dp))
            PaperOutlineButton(label = "Reset positions", onClick = onResetLayout)
        }
        SettingsCaption(
            if (visibleCount > 0) {
                "The preview uses each button's real position, size and opacity. Toggle and Hold choose how a press behaves; the switch controls whether it appears."
            } else {
                "Turn on at least one button above before arranging the layout."
            },
        )
    }
}

@Composable
private fun ButtonLine(button: Hotkey, onChange: (Hotkey) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = button.name,
            fontFamily = Wyrm.Body,
            fontSize = 15.5.sp,
            color = Wyrm.Ink,
            modifier = Modifier.weight(1f),
        )
        if (button.fixedMode) {
            FixedTapBadge()
        } else {
            PaperSegmented(
                options = listOf("Toggle", "Hold"),
                selected = button.mode.coerceIn(0, 1),
                onSelect = { onChange(button.copy(mode = it)) },
                modifier = Modifier.width(116.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        InkSwitch(on = button.visible, onToggle = { onChange(button.copy(visible = it)) })
    }
}

@Composable
private fun FixedTapBadge() {
    Box(
        modifier = Modifier
            .width(116.dp)
            .height(38.dp)
            .background(Wyrm.Track, wyrmRounded(10.dp))
            .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Tap",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Wyrm.Quiet,
        )
    }
}
