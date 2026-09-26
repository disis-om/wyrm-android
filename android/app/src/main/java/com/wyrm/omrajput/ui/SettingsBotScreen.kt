package com.wyrm.omrajput.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting

/**
 * Spec page 13 — Settings › Bot.
 *
 * The bot is an on-screen button. This page is circle score and radius — the
 * two engine fields. Laser moved to Assist. No invented boost/restart toggles.
 */
@Composable
fun SettingsBotScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
) {
    val circle = settings.named("general.bot_circle")
    val radius = settings.named("general.bot_radius")
    val rows = listOfNotNull(circle, radius)

    SettingsDrillScaffold(
        title = "Bot",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsCard {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 15.dp)) {
                Text(
                    text = "Let the bot play",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Wyrm.Ink,
                )
                Text(
                    text = "Turn it on with the Bot button in a match. It hunts food, then circles once it is big.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        SettingsSectionLabel("Basic · behaviour")
        SettingsCard {
            rows.forEachIndexed { index, setting ->
                SettingTypedRow(setting = setting, first = index == 0, onChange = onChange)
            }
        }
        SettingsCaption("Laser and helper drawing moved to Assist, where you can actually see them.")
    }
}
