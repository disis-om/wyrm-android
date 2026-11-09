package com.wyrm.omrajput.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.wyrm.omrajput.i18n.Text
import com.wyrm.omrajput.i18n.WyrmLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Language (OM, 2026-10-31): Settings › Language, and the one-time
 * "Choose your language" sheet after an update (after What's new). Auto
 * follows the phone; a pick shows at once, so the sheet itself turns into
 * the language as the player taps. The tables are i18n/WyrmLanguage.kt.
 */

/** Auto first, then every language in its own words, with a tick on the chosen one. */
@Composable
private fun LanguageRows(chosen: String, onPick: (String) -> Unit) {
    val phone = WyrmLanguage.phoneLanguage()
    LanguageRow(
        title = "Auto",
        detail = "Same as your phone · ${WyrmLanguage.nameOf(phone)}",
        selected = chosen == WyrmLanguage.AUTO,
        icon = LucideR.drawable.lucide_ic_smartphone,
        onClick = { onPick(WyrmLanguage.AUTO) },
    )
    LanguageRow(
        title = "English",
        detail = null,
        selected = chosen == "en",
        icon = null,
        onClick = { onPick("en") },
    )
    WyrmLanguage.choices.forEach { choice ->
        LanguageRow(
            title = choice.name,
            detail = null,
            selected = chosen == choice.code,
            icon = null,
            onClick = { onPick(choice.code) },
        )
    }
}

@Composable
private fun LanguageRow(title: String, detail: String?, selected: Boolean, icon: Int?, onClick: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val shape = wyrmRounded(14.dp)
    val tint by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "language-row")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(shape)
            .background(if (selected) wyrmScopedPalette.ink else wyrmScopedPalette.card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) wyrmScopedPalette.ink else wyrmScopedPalette.rule, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(34.dp).clip(wyrmRounded(10.dp)).background(wyrmScopedPalette.well), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, Modifier.size(18.dp), tint = wyrmScopedPalette.ink)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            // A language's own name is never translated: it is how its speakers find it.
            androidx.compose.material3.Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = wyrmScopedPalette.ink)
            if (detail != null) {
                Text(detail, fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = wyrmScopedPalette.quiet, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Box(
            Modifier.size(24.dp).graphicsLayer { alpha = 0.25f + 0.75f * tint }.clip(CircleShape)
                .background(if (selected) wyrmScopedPalette.ink else Color.Transparent)
                .border(if (selected) 0.dp else 1.5.dp, wyrmScopedPalette.rule, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(painterResource(LucideR.drawable.lucide_ic_check), null, Modifier.size(14.dp), tint = wyrmScopedPalette.onInk)
        }
    }
}

/** Settings › Language. */
@Composable
internal fun LanguageScreen(insetTop: Dp, insetBottom: Dp, onBack: () -> Unit) {
    SettingsDrillScaffold(title = "Language", insetTop = insetTop, insetBottom = insetBottom, onBack = onBack) {
        SettingsCaption("Auto uses your phone's language. Pick one to keep Wyrm in it.")
        LanguageRows(WyrmLanguage.chosen) { WyrmLanguage.choose(it) }
        SettingsCaption("Names, chat messages and the arena stay as players wrote them.")
        Spacer(Modifier.height(insetBottom))
    }
}

/**
 * The one-time sheet after an update (OM): rises over Home after What's new,
 * says the choice can be changed later in Settings, and is never shown again
 * on this phone (WyrmLanguage.promptShown, written before it closes).
 */
@Composable
internal fun LanguagePrompt(insetTop: Dp, insetBottom: Dp, onDone: () -> Unit) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var arrived by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { arrived = true }
    fun close() {
        if (!arrived) return
        arrived = false
        scope.launch { delay(330); onDone() }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = arrived, enter = fadeIn(tween(240)), exit = fadeOut(tween(220)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0xCC1E1C1A))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { })
        }
        AnimatedVisibility(
            visible = arrived,
            enter = slideInVertically(tween(380)) { it } + fadeIn(tween(140)),
            exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(top = insetTop + 40.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 420.dp).fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(wyrmScopedPalette.paper),
            ) {
                Box(Modifier.padding(top = 9.dp).align(Alignment.CenterHorizontally).size(width = 38.dp, height = 4.dp)
                    .clip(wyrmRounded(99.dp)).background(wyrmScopedPalette.chevron))
                Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).clip(wyrmRounded(18.dp)).background(wyrmScopedPalette.ink), contentAlignment = Alignment.Center) {
                        Icon(painterResource(LucideR.drawable.lucide_ic_languages), null, Modifier.size(28.dp), tint = wyrmScopedPalette.onInk)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Choose your language", fontFamily = Wyrm.Display, fontSize = 27.sp, color = wyrmScopedPalette.ink, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Wyrm now speaks your language. Auto follows your phone.", fontFamily = Wyrm.Body, fontSize = 13.5.sp,
                        lineHeight = 19.sp, color = wyrmScopedPalette.quiet, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    LanguageRows(WyrmLanguage.chosen) { WyrmLanguage.choose(it) }
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = insetBottom + 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("You can change this later in Settings › Language.", fontFamily = Wyrm.Body, fontSize = 12.5.sp,
                        color = wyrmScopedPalette.quiet, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    PaperPrimaryButton("Continue") { close() }
                }
            }
        }
    }
}
