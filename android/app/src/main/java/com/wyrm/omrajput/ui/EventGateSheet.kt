package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.WyrmNotification
import com.wyrm.omrajput.data.eventWhen

/** One deliberate pause before entering a server carrying an event notice. */
@Composable
fun EventGateSheet(
    event: WyrmNotification,
    insetTop: Dp,
    insetBottom: Dp,
    onOkay: () -> Unit,
    onJoinAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(top = insetTop, bottom = insetBottom)
            .padding(horizontal = Wyrm.Gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(wyrmRounded(Wyrm.CornerLarge))
                .background(Wyrm.Black.copy(alpha = 0.9f))
                .background(glassFill(1.5f))
                .border(1.dp, glassEdge(1.5f), wyrmRounded(Wyrm.CornerLarge))
                .padding(22.dp),
        ) {
            WyrmLabel("Battledome", color = Wyrm.Green)
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Hasn't started yet",
                fontFamily = Wyrm.Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 25.sp,
                color = Wyrm.White,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = "${event.title} starts",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Wyrm.SoftWhite,
            )
            eventWhen(event.startsAt).takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Grey)
            }
            Spacer(Modifier.height(20.dp))
            WyrmPrimaryAction(
                label = "OK",
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = onOkay,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlineAction("Cancel", Modifier.weight(1f), onClick = onDismiss)
                OutlineAction("Join anyway", Modifier.weight(1f), onClick = onJoinAnyway)
            }
        }
    }
}
