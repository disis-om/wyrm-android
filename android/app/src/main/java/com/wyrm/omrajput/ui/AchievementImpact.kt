package com.wyrm.omrajput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.R
import com.wyrm.omrajput.data.WyrmNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** An earned milestone that remains over intact Home until its glass X is used. */
@Composable
fun AchievementImpact(
    achievement: WyrmNotification,
    onFinished: () -> Unit,
) {
    val arrival = remember(achievement.id) { Animatable(0f) }
    val lightning = remember(achievement.id) { Animatable(0f) }
    val departure = remember(achievement.id) { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val scope = rememberCoroutineScope()

    LaunchedEffect(achievement.id) {
        launch {
            arrival.animateTo(
                1f,
                spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium),
            )
        }
        delay(175)
        lightning.snapTo(1f)
        delay(70)
        lightning.snapTo(0.16f)
        delay(75)
        lightning.snapTo(1f)
        lightning.animateTo(0.30f, tween(720, easing = FastOutSlowInEasing))
    }

    val landed = arrival.value.coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.achievement_lightning),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .graphicsLayer {
                    alpha = lightning.value * (1f - departure.value)
                    val pulse = 0.90f + lightning.value * 0.12f
                    scaleX = pulse
                    scaleY = pulse
                },
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 388.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth()
                    .blur(((1f - landed) * 8f).dp)
                    .graphicsLayer {
                        alpha = (landed * 2.2f).coerceAtMost(1f) * (1f - departure.value)
                        val depthScale = 3.15f - landed * 2.15f
                        val leavingScale = 1f - departure.value * 0.08f
                        scaleX = depthScale * leavingScale
                        scaleY = depthScale * leavingScale
                        rotationX = (1f - landed) * 16f
                        rotationZ = (1f - landed) * -4f
                        translationY = departure.value * 30.dp.toPx()
                        cameraDistance = 12f * density
                    }
                    .clip(wyrmRounded(Wyrm.CornerLarge))
                    .background(Wyrm.Black.copy(alpha = 0.92f))
                    .background(glassFill(1.45f))
                    .border(1.dp, glassEdge(1.5f), wyrmRounded(Wyrm.CornerLarge))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WyrmLabel("Achievement unlocked", color = Wyrm.Green)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = achievement.title,
                    fontFamily = Wyrm.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    textAlign = TextAlign.Center,
                    color = Wyrm.White,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (achievement.body.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = achievement.body,
                        fontFamily = Wyrm.Body,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        color = Wyrm.SoftWhite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(42.dp)
                    .graphicsLayer {
                        alpha = landed * (1f - departure.value)
                        scaleX = landed
                        scaleY = landed
                    }
                    .clip(wyrmRounded(Wyrm.Pill))
                    .background(Wyrm.Black.copy(alpha = 0.94f))
                    .background(glassFill(1.6f))
                    .border(1.dp, glassEdge(1.6f), wyrmRounded(Wyrm.Pill))
                    .clickable(enabled = departure.value == 0f) {
                        scope.launch {
                            departure.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                            finish()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_x),
                    contentDescription = "Dismiss achievement",
                    tint = Wyrm.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
