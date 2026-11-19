package com.wyrm.omrajput.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.i18n.Text
import com.wyrm.omrajput.data.Setting

@Composable
fun SettingsAiTrainerScreen(
    settings: List<Setting>,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onChange: (Setting, List<Float>) -> Unit,
) {
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val enabled = settings.named("general.ai_coach")
    SettingsDrillScaffold(
        title = "AI Trainer",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        Spacer(Modifier.height(18.dp))
        SettingsCard {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 15.dp)) {
                Text(
                    text = "Treinador IA",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = palette.ink,
                )
                Text(
                    text = "Analisa cobras próximas e assume a direção somente quando detecta risco de colisão. Seus controles manuais e o bot tradicional permanecem independentes.",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.5.sp,
                    color = palette.quiet,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        SettingsSectionLabel("Funcionamento")
        SettingsCard {
            if (enabled != null) SettingTypedRow(
                setting = enabled,
                first = true,
                onChange = onChange,
            )
        }
        SettingsCaption("A primeira versão faz desvio preventivo de cobras próximas. Novas funções serão adicionadas nesta aba.")
    }
}
