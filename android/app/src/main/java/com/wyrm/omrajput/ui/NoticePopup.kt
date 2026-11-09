package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.data.NoticeCenter

/**
 * The developer's notice (OM, 2026-10-25; data/Notices.kt): a card in the
 * middle of the screen, two thirds of its height, in the app's theme, the
 * message in full Markdown and scrolling when it is long. OK closes it; from
 * the second start "Don't show again" is offered above OK.
 */
@Composable
internal fun NoticeHost() {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val notice = NoticeCenter.shown
    val context = LocalContext.current
    var never by remember(notice?.id) { mutableStateOf(false) }
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
    ) {
        val shown = notice ?: return@AnimatedVisibility
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            val cardHeight = maxHeight * (2f / 3f)
            val cardWidth = if (maxWidth * 0.88f > 520.dp) 520.dp else maxWidth * 0.88f
            run {
                Column(
                    Modifier.width(cardWidth).height(cardHeight).clip(wyrmRounded(26.dp)).background(wyrmScopedPalette.card)
                        .border(1.dp, wyrmScopedPalette.rule, wyrmRounded(26.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shown.kicker, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            letterSpacing = 1.4.sp, color = wyrmScopedPalette.mute, modifier = Modifier.weight(1f))
                        if (NoticeCenter.position.isNotBlank()) Text(NoticeCenter.position, fontFamily = Wyrm.Body,
                            fontSize = 11.sp, color = wyrmScopedPalette.quiet)
                    }
                    if (shown.title.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        // The heading is Markdown too (OM, 2026-10-30); plain words keep the old look.
                        if (shown.title.any { it in "*_`[]#!~<>" }) {
                            WyrmMarkdown(shown.title, baseSize = 21.sp, images = true)
                        } else {
                            Text(shown.title, fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                                lineHeight = 28.sp, color = wyrmScopedPalette.ink)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            // The builder's layout when the notice has one (OM, 2026-10-30), else its Markdown;
                            // pictures show in both, at the width the Observatory set.
                            if (shown.blocks.isNotEmpty()) NoticeBlocksView(shown.blocks)
                            else if (shown.markdown.isNotBlank()) WyrmMarkdown(shown.markdown, baseSize = 14.sp, images = true)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (NoticeCenter.offerNeverAgain) {
                        Row(
                            Modifier.fillMaxWidth().clip(wyrmRounded(12.dp)).clickable { never = !never }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(wyrmRounded(6.dp))
                                    .background(if (never) wyrmScopedPalette.ink else Color.Transparent)
                                    .border(1.5.dp, if (never) wyrmScopedPalette.ink else wyrmScopedPalette.quiet, wyrmRounded(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (never) Icon(painterResource(LucideR.drawable.lucide_ic_check), null,
                                    tint = wyrmScopedPalette.onInk, modifier = Modifier.size(14.dp))
                            }
                            Text("Don't show again", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = wyrmScopedPalette.ink)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    PaperPrimaryButton("OK") { NoticeCenter.close(context, never) }
                }
            }
        }
    }
}
