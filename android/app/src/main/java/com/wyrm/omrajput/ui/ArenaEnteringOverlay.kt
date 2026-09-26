package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A short honest transition while the native arena connection takes over. */
@Composable
fun ArenaEnteringOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BootingMark()
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Entering arena…",
                fontFamily = Wyrm.Body,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = Wyrm.Grey,
            )
        }
    }
}
