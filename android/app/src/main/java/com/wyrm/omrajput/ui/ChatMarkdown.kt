package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.LinkPreview
import com.wyrm.omrajput.i18n.Text

/*
 * Discord markdown for global chat and direct messages (OM, 2026-11-07:
 * "every kind of markdown, links embedded, spaces and Enter like Discord").
 * iOS twin: WyrmChatMarkdown.swift (same rules).
 *
 * Blocks: ``` code ```, "# ", "## ", "### " headings, "-# " small text,
 * "> " quotes, "- " / "* " bullets ("1. " stays as typed).
 * Inline: `code`, [text](https://link), bare https:// links, ***bold
 * italic***, **bold**, __underline__, ~~strike~~, ||spoiler||, *italic*,
 * _italic_, @mentions. Line breaks and spaces are kept as typed.
 */

private val INLINE = Regex(
    "(`+)([^`\\n]+?)\\1" +                                            // 1,2 code
        "|\\[([^\\]\\n]{1,200})\\]\\((https?://[^)\\s]{1,2000})\\)" +  // 3,4 masked link
        "|(https?://[^\\s<]+[^\\s<.,:;\"')\\]!?*_~|])" +               // 5 link
        "|\\*\\*\\*(.+?)\\*\\*\\*" +                                     // 6 bold italic
        "|\\*\\*(.+?)\\*\\*" +                                           // 7 bold
        "|__(.+?)__" +                                                   // 8 underline
        "|~~(.+?)~~" +                                                   // 9 strike
        "|\\|\\|(.+?)\\|\\|" +                                           // 10 spoiler
        "|\\*([^*\\s](?:[^*]*?[^*\\s])?)\\*" +                           // 11 italic
        "|(?<![A-Za-z0-9])_([^_\\s](?:[^_]*?[^_\\s])?)_(?![A-Za-z0-9])" + // 12 italic
        "|(?<![A-Za-z0-9_])@([A-Za-z0-9_]{3,20})",                      // 13 mention
)
private val LINK = Regex("https?://[^\\s<]+[^\\s<.,:;\"')\\]!?*_~|]")
private val MentionGold = Color(0xFFB07B10)

/** A bare "www." address, which Discord also embeds (as https://). */
private val WWW = Regex("(?<![A-Za-z0-9/@.])www\\.[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+[^\\s<]*[^\\s<.,:;\"')\\]!?*_~|]")

/**
 * Every link in a message, in order, each once (OM, 2026-11-07: a link in the
 * middle of a message, or in a second message, had no embed). Not GIF links,
 * not inside code; Discord's own limit of 5 embeds a message.
 */
internal fun chatLinks(body: String): List<String> {
    val noCode = body.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("`[^`\\n]+`"), " ")
    val found = LINK.findAll(noCode).map { it.range.first to it.value } +
        WWW.findAll(noCode).map { it.range.first to "https://" + it.value }
    return found.sortedBy { it.first }.map { it.second }
        .filter { !isChatGif(it) }.distinct().take(5).toList()
}

internal fun firstChatLink(body: String): String? = chatLinks(body).firstOrNull()

internal fun chatMarkdown(body: String, meUsername: String, palette: WyrmPalette): AnnotatedString = buildAnnotatedString {
    val lines = body.split('\n')
    var inCode = false
    lines.forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        if (raw.trimStart().startsWith("```")) {
            // A fence line: opens or closes a code block (its language word is dropped).
            inCode = !inCode
            val rest = raw.trimStart().removePrefix("```").let { if (inCode) "" else it }
            if (rest.isNotBlank()) appendInline(this, rest, meUsername, palette)
            return@forEachIndexed
        }
        if (inCode) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, background = palette.well, color = palette.ink)) { append(raw) }
            return@forEachIndexed
        }
        when {
            raw.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)) { appendInline(this, raw.substring(4), meUsername, palette) }
            raw.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)) { appendInline(this, raw.substring(3), meUsername, palette) }
            raw.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)) { appendInline(this, raw.substring(2), meUsername, palette) }
            raw.startsWith("-# ") -> withStyle(SpanStyle(fontSize = 12.sp, color = palette.quiet)) { appendInline(this, raw.substring(3), meUsername, palette) }
            raw.startsWith("> ") || raw == ">" -> {
                withStyle(SpanStyle(color = palette.rule, fontWeight = FontWeight.Black)) { append("▎ ") }
                withStyle(SpanStyle(color = palette.mute)) { appendInline(this, raw.removePrefix(">").removePrefix(" "), meUsername, palette) }
            }
            raw.startsWith("- ") || raw.startsWith("* ") -> {
                append("  •  ")
                appendInline(this, raw.substring(2), meUsername, palette)
            }
            else -> appendInline(this, raw, meUsername, palette)
        }
    }
}

private fun appendInline(out: AnnotatedString.Builder, text: String, meUsername: String, palette: WyrmPalette) {
    var at = 0
    for (match in INLINE.findAll(text)) {
        if (match.range.first < at) continue
        out.append(text.substring(at, match.range.first))
        val g = match.groups
        when {
            g[2] != null -> out.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, background = palette.well)) { append(g[2]!!.value) }
            g[3] != null -> link(out, g[4]!!.value, palette) { appendInline(out, g[3]!!.value, meUsername, palette) }
            g[5] != null -> link(out, g[5]!!.value, palette) { out.append(g[5]!!.value) }
            g[6] != null -> out.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)) { appendInline(out, g[6]!!.value, meUsername, palette) }
            g[7] != null -> out.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { appendInline(out, g[7]!!.value, meUsername, palette) }
            g[8] != null -> out.withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { appendInline(out, g[8]!!.value, meUsername, palette) }
            g[9] != null -> out.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(out, g[9]!!.value, meUsername, palette) }
            g[10] != null -> out.withStyle(SpanStyle(background = palette.ink.copy(alpha = 0.82f), color = palette.paper)) { append(g[10]!!.value) }
            g[11] != null -> out.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(out, g[11]!!.value, meUsername, palette) }
            g[12] != null -> out.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(out, g[12]!!.value, meUsername, palette) }
            g[13] != null -> {
                val name = g[13]!!.value
                val mine = meUsername.isNotBlank() && name.equals(meUsername, ignoreCase = true)
                out.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (mine) MentionGold else palette.link,
                    background = if (mine) Color(0x40F2B544) else palette.link.copy(alpha = 0.14f))) { append("@$name") }
            }
        }
        at = match.range.last + 1
    }
    out.append(text.substring(at))
}

private inline fun link(out: AnnotatedString.Builder, url: String, palette: WyrmPalette, body: () -> Unit) {
    out.withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = palette.link, textDecoration = TextDecoration.Underline)))) { body() }
}

/* ------------------------------------------------------------ link embeds */

/** The server's link previews (repository.linkPreview), provided by WyrmChatScreen. */
internal val LocalLinkPreview = compositionLocalOf<(suspend (String) -> LinkPreview?)?> { null }

/** One answer per link: a found preview is kept; a link with none (or a
 *  failed ask) is asked again after a minute, so a slow site still embeds. */
private object LinkPreviewCache {
    val values = mutableStateMapOf<String, LinkPreview?>()
    val askedAt = HashMap<String, Long>()
    fun shouldAsk(url: String): Boolean {
        if (values[url] != null) return false
        val now = System.currentTimeMillis()
        val last = askedAt[url]
        if (last != null && now - last < 60_000) return false
        askedAt[url] = now
        return true
    }
}

/** Discord's embed under a message: a bar, the site, the title as a link, the text, the picture. */
@Composable
internal fun ChatLinkEmbed(url: String) {
    val load = LocalLinkPreview.current ?: return
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    LaunchedEffect(url) {
        if (LinkPreviewCache.shouldAsk(url)) {
            val got = runCatching { load(url) }.getOrNull()
            if (got != null) LinkPreviewCache.values[url] = got
        }
    }
    val uri = LocalUriHandler.current
    // No page details (a site that hides them from servers, or not loaded
    // yet): still a small card with the site and the link (OM, 2026-11-07:
    // every link gets its embed).
    val preview = LinkPreviewCache.values[url] ?: run {
        val host = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
        LinkPreview(url = url, title = url.removePrefix("https://").removePrefix("http://").take(120),
            description = "", image = "", siteName = host)
    }
    Row(
        Modifier.padding(top = 6.dp).widthIn(max = 360.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.well)
            .clickable { runCatching { uri.openUri(url) } },
    ) {
        Box(Modifier.width(4.dp).heightIn(min = 40.dp).background(palette.link))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            if (preview.siteName.isNotBlank()) Text(preview.siteName, fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = palette.quiet, maxLines = 1)
            if (preview.title.isNotBlank()) Text(preview.title, fontFamily = Wyrm.Body, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp,
                color = palette.link, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            if (preview.description.isNotBlank()) Text(preview.description, fontFamily = Wyrm.Body, fontSize = 13.sp, lineHeight = 18.sp,
                color = palette.mute, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (preview.image.isNotBlank()) RemoteAnimatedImage(preview.image,
                Modifier.padding(top = 9.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(6.dp)), crop = true)
        }
    }
}

/* ------------------------------------------------------------ composing */

private val FANCY_UPPER = 0x1D4D0 // MATHEMATICAL BOLD SCRIPT CAPITAL A
private val FANCY_LOWER = 0x1D4EA // MATHEMATICAL BOLD SCRIPT SMALL A
private val FANCY_DIGIT = 0x1D7CE // MATHEMATICAL BOLD DIGIT ZERO

/** 𝓕𝓪𝓷𝓬𝔂 letters: A-Z, a-z and 0-9 to bold script; everything else as it is. */
internal fun fancyText(text: String): String = buildString {
    for (c in text) when (c) {
        in 'A'..'Z' -> appendCodePoint(FANCY_UPPER + (c - 'A'))
        in 'a'..'z' -> appendCodePoint(FANCY_LOWER + (c - 'a'))
        in '0'..'9' -> appendCodePoint(FANCY_DIGIT + (c - '0'))
        else -> append(c)
    }
}

/** Wraps the selection in [marker]s (or takes them off when already there), keeping it selected. */
internal fun TextFieldValue.wrapSelection(marker: String, end: String = marker): TextFieldValue {
    val s = selection.min
    val e = selection.max
    val inner = text.substring(s, e)
    if (s >= marker.length && text.regionMatches(s - marker.length, marker, 0, marker.length) &&
        text.regionMatches(e, end, 0, end.length)) {
        val next = text.substring(0, s - marker.length) + inner + text.substring(e + end.length)
        return copy(text = next, selection = TextRange(s - marker.length, e - marker.length))
    }
    val next = text.substring(0, s) + marker + inner + end + text.substring(e)
    return copy(text = next, selection = TextRange(s + marker.length, e + marker.length))
}

internal fun TextFieldValue.fancySelection(): TextFieldValue {
    val s = selection.min
    val e = selection.max
    val made = fancyText(text.substring(s, e))
    return copy(text = text.substring(0, s) + made + text.substring(e), selection = TextRange(s, s + made.length))
}

/** [selection](https://) with the cursor where the address goes. */
internal fun TextFieldValue.linkSelection(): TextFieldValue {
    val s = selection.min
    val e = selection.max
    val inner = text.substring(s, e)
    val next = text.substring(0, s) + "[" + inner + "](https://)" + text.substring(e)
    val cursor = s + 1 + inner.length + 10
    return copy(text = next, selection = TextRange(cursor))
}

/** Discord's bar over the composer while text is selected. */
@Composable
internal fun ChatFormatBar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    @Composable
    fun Key(label: String, style: SpanStyle = SpanStyle(), apply: () -> TextFieldValue) {
        Box(Modifier.height(34.dp).clip(RoundedCornerShape(9.dp)).clickable { onChange(apply()) }.padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center) {
            Text(buildAnnotatedString { withStyle(style) { append(label) } }, fontFamily = Wyrm.Body, fontSize = 14.sp, color = palette.ink)
        }
    }
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(palette.card)
            .border(1.dp, palette.rule, RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Key("B", SpanStyle(fontWeight = FontWeight.Black)) { value.wrapSelection("**") }
        Key("I", SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)) { value.wrapSelection("*") }
        Key("U", SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) { value.wrapSelection("__") }
        Key("S", SpanStyle(textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Bold)) { value.wrapSelection("~~") }
        Key("</>", SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) { value.wrapSelection("`") }
        Key("||", SpanStyle(fontWeight = FontWeight.Black)) { value.wrapSelection("||") }
        Key("❝", SpanStyle(fontWeight = FontWeight.Black)) {
            val lineStart = value.text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(0)).let { if (it < 0 || value.selection.min == 0) 0 else it + 1 }
            value.copy(text = value.text.substring(0, lineStart) + "> " + value.text.substring(lineStart),
                selection = TextRange(value.selection.start + 2, value.selection.end + 2))
        }
        Key("Link", SpanStyle(color = palette.link, fontWeight = FontWeight.Bold)) { value.linkSelection() }
        Key(fancyText("Fancy")) { value.fancySelection() }
    }
}
