package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Hotkey
import com.wyrm.omrajput.data.Setting

/* These are the actions that make sense as named touch buttons. Native keeps
 * the same allowlist so an old backup cannot bring a retired button back. */
private val OnScreenButtonOrder = listOf(1, 2, 3, 4, 6, 7, 8, 9)

@Composable
fun OnScreenButtonsContent(
    buttons: List<Hotkey>,
    settings: List<Setting>,
    onChange: (Setting, List<Float>) -> Unit,
    onButtonChange: (Hotkey) -> Unit,
    onEditLayout: () -> Unit,
    onResetLayout: () -> Unit,
) {
    val byAction = buttons.associateBy { it.action }
    val allowed = OnScreenButtonOrder.mapNotNull(byAction::get)
    val visible = allowed.filter { it.visible }
    val size = settings.firstOrNull { it.id == "keys.key_scale" }?.number ?: 1f
    val opacity = settings.firstOrNull { it.id == "keys.opacity" }?.number ?: 1f
    val appearance = settings.filter { it.id == "keys.key_scale" || it.id == "keys.opacity" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Wyrm.Gutter),
    ) {
        OnScreenButtonsPreview(buttons = visible, scale = size, opacity = opacity)
        Spacer(Modifier.height(Wyrm.Gap))

        WyrmLabel("appearance")
        appearance.forEach { setting ->
            WyrmRule()
            SettingRow(setting = setting, onChange = { onChange(setting, it) })
        }
        WyrmRule()

        Spacer(Modifier.height(Wyrm.Gap))
        WyrmLabel("actions")
        allowed.forEach { button ->
            WyrmRule()
            OnScreenButtonRow(button = button, onChange = onButtonChange)
        }
        WyrmRule()

        Spacer(Modifier.height(Wyrm.GapLarge))
        WyrmPrimaryAction(
            label = "Adjust button layout",
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = visible.isNotEmpty(),
            onClick = onEditLayout,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (visible.isNotEmpty()) {
                "Opens sideways so you can place every enabled button exactly where your thumb expects it during a match."
            } else {
                "Turn on at least one button above before arranging the layout."
            },
            fontFamily = Wyrm.Body,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = Wyrm.Faint,
        )
        Spacer(Modifier.height(14.dp))
        OutlineAction(label = "Reset button positions", onClick = onResetLayout)
        Spacer(Modifier.height(Wyrm.GapLarge))
    }
}

@Composable
internal fun OnScreenButtonsPreview(buttons: List<Hotkey>, scale: Float, opacity: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .clip(wyrmRounded(10.dp))
            .background(Wyrm.Well)
            .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp)),
    ) {
        Text(
            text = "PREVIEW",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(12.dp),
        )
        buttons.forEach { button ->
            PreviewPositioned(position = Offset(button.x, button.y)) {
                PaperKey(
                    label = button.name.uppercase(),
                    opacity = opacity,
                    scale = scale * 0.48f,
                )
            }
        }
    }
}

@Composable
private fun OnScreenButtonRow(button: Hotkey, onChange: (Hotkey) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = button.name,
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Wyrm.White,
            modifier = Modifier.weight(1f),
        )
        if (button.fixedMode) {
            LockedTapMode()
        } else {
            ButtonModeSelector(
                selected = button.mode.coerceIn(0, 1),
                onSelect = { onChange(button.copy(mode = it)) },
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.clickable { onChange(button.copy(visible = !button.visible)) }) {
            WyrmSwitch(checked = button.visible)
        }
    }
}

@Composable
private fun LockedTapMode() {
    Box(
        modifier = Modifier
            .width(112.dp)
            .height(34.dp)
            .clip(wyrmRounded(Wyrm.Pill))
            .background(glassFill())
            .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "TAP",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = Wyrm.Faint,
        )
    }
}

@Composable
private fun ButtonModeSelector(selected: Int, onSelect: (Int) -> Unit) {
    WyrmCompactSegmentedSelector(
        options = listOf("TOGGLE", "HOLD"),
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.width(112.dp),
    )
}
