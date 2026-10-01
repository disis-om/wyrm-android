package com.wyrm.omrajput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

/*
 * The "⋯" menu (OM, 2026-10-01), in the app's theme rather than the system's:
 * a sheet that rises from the bottom of the screen (not from inside the card
 * that opened it), a grab handle, one row per action with its Lucide icon, the
 * destructive one in red, and Cancel apart. Opened over the whole window, so a
 * card in a list can never clip or misplace it.
 */

internal data class WyrmMenuItem(
    val title: String,
    val icon: Int,
    val detail: String = "",
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** The whole window, top-left: the sheet lays itself out inside. */
private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

@Composable
internal fun WyrmMenuSheet(
    title: String,
    items: List<WyrmMenuItem>,
    insetBottom: Dp,
    subtitle: String = "",
    onDismiss: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { appear.animateTo(1f, iosSpring(0.38f, 0.86f)) }
    fun close(then: () -> Unit = {}) {
        scope.launch {
            appear.animateTo(0f, iosSpring(0.26f, 1f))
            onDismiss()
            then()
        }
    }
    Popup(popupPositionProvider = FullWindow, onDismissRequest = { close() }, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f * appear.value))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = insetBottom + 10.dp)
                    .graphicsLayer {
                        translationY = (1f - appear.value) * size.height * 1.1f
                        alpha = 0.4f + 0.6f * appear.value
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(wyrmRounded(26.dp))
                        .background(Wyrm.Card)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(26.dp))
                        .padding(bottom = 6.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 9.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(38.dp).height(5.dp).clip(WyrmCapsule).background(Wyrm.Rule))
                    }
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
                        Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Wyrm.Ink)
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, fontFamily = Wyrm.Body, fontSize = 13.sp, lineHeight = 18.sp, color = Wyrm.Quiet,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    items.forEachIndexed { index, item ->
                        if (index > 0) Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(Wyrm.RowRule))
                        MenuRow(item) { close(item.onClick) }
                    }
                }
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .scale(pressScale(pressed))
                        .clip(wyrmRounded(20.dp))
                        .background(Wyrm.Card)
                        .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp))
                        .clickable(interactionSource = interaction, indication = null) { close() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Wyrm.Ink)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: WyrmMenuItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (item.destructive) Wyrm.Badge else Wyrm.Ink
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .scale(pressScale(pressed))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(wyrmRounded(12.dp)).background(tint.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(item.icon), null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = tint)
            if (item.detail.isNotBlank()) {
                Text(item.detail, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Quiet, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}
