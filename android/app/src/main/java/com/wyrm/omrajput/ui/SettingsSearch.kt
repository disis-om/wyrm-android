package com.wyrm.omrajput.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * Settings search, as Wyrm iOS has it (`WyrmSettingsSearch.swift`).
 *
 * Every setting is indexed by its label, hint and page. A result is the real
 * control — switch, slider, segmented pill, colour — working right there in
 * the hub. The arrow on its card opens the page it lives on, scrolls to it,
 * opens whatever fold or mode tab holds it and blinks it twice.
 *
 * Adding a setting to a page? Give its row `settingAnchor(id)` (engine rows get
 * it through `SettingTypedRow`) and add an entry in `WyrmOverlay.settingsIndex`.
 */

/** The setting a result asked a page to reveal, and the query itself. */
internal object SettingsFocus {
    var query by mutableStateOf("")
    /** Engine setting id, `hotkey.<action>`, or an `app.` id for app settings. */
    var target by mutableStateOf<String?>(null)
        private set
    /** Bumped each time a target is asked for, so a repeat still blinks. */
    var pulse by mutableIntStateOf(0)
        private set

    fun reveal(id: String) {
        target = id
        pulse += 1
    }

    fun finish(id: String) {
        if (target == id) target = null
    }

    /** True when the target is one of these ids: pages open the fold or pick the tab holding it. */
    fun wants(ids: Collection<String>): Boolean = target?.let { it in ids } ?: false
    fun wants(id: String): Boolean = target == id
}

/**
 * Makes a row findable by settings search: once the page has slid in, the row
 * scrolls into view and blinks twice when it is the one a result opened.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.settingAnchor(id: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val lit = remember { Animatable(0f) }
    val pulse = SettingsFocus.pulse
    LaunchedEffect(pulse, SettingsFocus.target) {
        if (SettingsFocus.target != id) return@LaunchedEffect
        delay(420)
        runCatching { requester.bringIntoView() }
        delay(260)
        repeat(2) {
            lit.animateTo(1f, tween(220))
            delay(80)
            lit.animateTo(0f, tween(220))
            delay(260)
        }
        SettingsFocus.finish(id)
    }
    val link = Wyrm.Link
    this
        .bringIntoViewRequester(requester)
        .drawWithContent {
            drawContent()
            val a = lit.value
            if (a > 0f) {
                val inset = 2.dp.toPx()
                val corner = CornerRadius(12.dp.toPx())
                val box = Size(size.width - inset * 2, size.height - inset * 2)
                drawRoundRect(link.copy(alpha = 0.16f * a), Offset(inset, inset), box, corner)
                drawRoundRect(link.copy(alpha = 0.7f * a), Offset(inset, inset), box, corner, style = Stroke(1.5.dp.toPx()))
            }
        }
}

/** One searchable setting. [open] is null for a setting on the hub itself. */
class SettingsSearchEntry(
    val id: String,
    val title: String,
    val detail: String,
    val page: String,
    val keywords: String = "",
    val open: (() -> Unit)?,
    val control: @Composable () -> Unit,
) {
    fun matches(words: List<String>): Boolean {
        val haystack = "$title $detail $page $keywords".lowercase()
        return words.all { it in haystack }
    }
}

/** The search field at the top of the hub. */
@Composable
internal fun SettingsSearchField(query: String, onQuery: (String) -> Unit, placeholder: String = "Search settings") {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val shape = wyrmRounded(13.dp)
    val style = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Ink)
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, if (focused) Wyrm.Ink.copy(alpha = 0.35f) else Wyrm.Rule, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IosIcon(IosGlyph.MAGNIFIER, Wyrm.Quiet, size = 16.dp, semibold = true)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, style = style.copy(color = Wyrm.Quiet), maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Wyrm.Link),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (query.isNotEmpty()) {
            IosIcon(
                IosGlyph.XMARK_CIRCLE,
                Wyrm.Chevron,
                size = 18.dp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onQuery("") },
            )
        }
    }
}

/** Results: each card is the page it belongs to, the live control, and an arrow to its place. */
@Composable
internal fun SettingsSearchResults(query: String, entries: List<SettingsSearchEntry>, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val first = query.lowercase()
    val ranked = entries.filter { it.matches(words) }.sortedWith(
        compareByDescending<SettingsSearchEntry> { it.title.lowercase().startsWith(first) }.thenBy { it.title },
    )
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (ranked.isEmpty()) "Nothing matches “$query”" else "${ranked.size} result${if (ranked.size == 1) "" else "s"}",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            letterSpacing = 0.6.sp,
            color = Wyrm.Quiet,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        ranked.take(40).forEach { entry ->
            val shape = wyrmRounded(14.dp)
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, shape),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.page.uppercase(),
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.1.sp,
                        color = Wyrm.Quiet,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Wyrm.Well)
                            .border(1.dp, Wyrm.Rule, CircleShape)
                            .clickable {
                                focus.clearFocus()
                                SettingsFocus.reveal(entry.id)
                                entry.open?.invoke() ?: onClear()
                            },
                        contentAlignment = Alignment.Center,
                    ) { IosIcon(IosGlyph.ARROW_UP_RIGHT, Wyrm.Ink, size = 12.dp, semibold = true) }
                }
                entry.control()
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
