package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.wyrm.omrajput.data.TeamMessage

/**
 * Team chat, over a live match.
 *
 * A panel rather than a screen: the arena is still there behind it, still
 * being played — by the bot, which took over the moment this opened. That is
 * also why this can be a Compose layer at all. Everywhere else in the app,
 * Compose over gameplay would swallow the touches that steer the snake; here
 * there is nothing left to swallow, because the player has already handed the
 * snake over to read this.
 *
 * It holds every message since the app was opened, and none from before it.
 * The team service has no history to hand back, and inventing one would be
 * pretending.
 */
@Composable
fun ArenaChatScreen(
    messages: List<TeamMessage>,
    draft: String,
    sending: Boolean,
    handoverSeconds: Int,
    insetTop: Dp,
    insetBottom: Dp,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onHandoverChange: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Dark enough to read against, clear enough to keep an eye on the
            // arena — you are still in it.
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(430.dp)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = insetTop + 12.dp,
                    bottom = keyboardRoom(insetBottom + 12.dp),
                )
                .clip(wyrmRounded(Wyrm.CornerLarge))
                .background(Color.Black.copy(alpha = 0.62f))
                .background(glassFill(1.4f))
                .border(1.dp, glassEdge(1.4f), wyrmRounded(Wyrm.CornerLarge)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TEAM CHAT",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = Wyrm.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "CLOSE",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = Wyrm.SoftWhite,
                    modifier = Modifier
                        .clip(wyrmRounded(Wyrm.Pill))
                        .background(glassFill())
                        .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 15.dp, vertical = 9.dp),
                )
            }

            /*
             * There used to be a line here saying the bot was playing, and a
             * three/five/ten-second handover picker beside it. Both are gone
             * with the thing they described: chat does not take the bot any
             * more, so nothing is being handed back and there is nothing to
             * count. The player turned the bot on themselves and can turn it
             * off from the button beside chat whenever they like.
             */

            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nothing said since you opened Wyrm.",
                            fontFamily = Wyrm.Body,
                            fontSize = 12.sp,
                            color = Wyrm.Faint,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        items(messages.asReversed()) { message ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = message.from,
                                    fontFamily = Wyrm.Body,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (message.from == "TEAM") Wyrm.Green else Wyrm.Grey,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = message.text,
                                    fontFamily = Wyrm.Body,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = Wyrm.SoftWhite,
                                )
                            }
                        }
                    }
                }
            }

            Composer(
                draft = draft,
                sending = sending,
                placeholder = "Message the team",
                onDraftChange = onDraftChange,
                onSend = onSend,
            )
        }
    }
}
