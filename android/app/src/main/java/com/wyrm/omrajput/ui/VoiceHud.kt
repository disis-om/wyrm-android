package com.wyrm.omrajput.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wyrm.omrajput.R
import com.wyrm.omrajput.voice.VoiceCallState

/** The only Compose surface left touchable while Vulkan owns the arena. */
@Composable
fun ArenaVoiceHud(
    state: VoiceCallState,
    onToggleMute: () -> Unit,
    onLeave: () -> Unit,
) {
    val microphonePaused = state.muted || state.interrupted
    WyrmGlass(Modifier.fillMaxSize(), corner = Wyrm.Pill, lift = 1.45f) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceHudButton(
                icon = if (microphonePaused) R.drawable.ic_voice_mic_off else R.drawable.ic_voice_mic,
                tint = if (microphonePaused) Wyrm.Blood else Wyrm.Green,
                onClick = onToggleMute,
            )
            VoiceHudButton(
                icon = R.drawable.ic_voice_call_end,
                tint = Wyrm.Blood,
                onClick = onLeave,
            )
        }
    }
}

@Composable
private fun VoiceHudButton(icon: Int, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}
