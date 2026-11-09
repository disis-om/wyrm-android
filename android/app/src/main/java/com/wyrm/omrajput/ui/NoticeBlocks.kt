package com.wyrm.omrajput.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.wyrm.omrajput.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.BuildConfig
import com.wyrm.omrajput.data.NoticeBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/*
 * Notice blocks (OM, 2026-10-30): the Observatory's builder lays a notice out
 * as blocks, each with its own size, width, alignment and colour, and the
 * phone draws exactly that. Widths are a share of the message's width, so a
 * picture set to 60% is 60% on every phone, upright or sideways. iOS twin:
 * WyrmNoticeBlocks.swift; the Observatory preview follows the same numbers
 * (Wyrm Android ARCHITECTURE "Notice blocks").
 */

/** Text sizes in dp: s / m / l, and headings 1 / 2 / 3. */
internal fun noticeTextSize(size: String) = when (size) { "s" -> 13f; "l" -> 18f; else -> 15f }
internal fun noticeHeadingSize(level: Int) = when (level) { 1 -> 26f; 3 -> 17f; else -> 21f }

internal fun noticeColour(name: String): Color = when (name) {
    "quiet" -> Wyrm.Quiet
    "link" -> Wyrm.Link
    "live" -> Wyrm.Live
    "red" -> Color(0xFFE5484D)
    "gold" -> Color(0xFFC28A2C)
    else -> Wyrm.Ink
}

private fun textAlign(align: String) = when (align) { "center" -> TextAlign.Center; "right" -> TextAlign.End; else -> TextAlign.Start }
private fun boxAlign(align: String) = when (align) { "left" -> Alignment.CenterStart; "right" -> Alignment.CenterEnd; else -> Alignment.Center }

/** A bare address that is a picture: an image file, or an Observatory upload. */
internal fun isPictureAddress(text: String): Boolean {
    val t = text.trim()
    if (!(t.startsWith("https://") || t.startsWith("http://")) || t.any { it.isWhitespace() }) return false
    val path = t.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") ||
        path.endsWith(".gif") || "/v1/notice/images/" in path
}

/** A Markdown picture's layout from its title: `![alt](url "w=60 a=center")`. */
internal data class NoticeImageLayout(val width: Float, val align: String)

internal fun noticeImageLayout(title: String?): NoticeImageLayout {
    var width = 100f
    var align = "center"
    title.orEmpty().split(' ', ';', ',').forEach { part ->
        val (key, value) = part.split('=', limit = 2).let { if (it.size == 2) it[0].trim() to it[1].trim() else return@forEach }
        when (key) {
            "w" -> value.removeSuffix("%").toFloatOrNull()?.let { width = it.coerceIn(10f, 100f) }
            "a" -> if (value in setOf("left", "center", "right")) align = value
        }
    }
    return NoticeImageLayout(width, align)
}

/**
 * Pictures for notices: any public link or the Observatory's uploads (a
 * relative `/v1/...` address is the API's). Kept in memory (32 MB), decoded
 * at most 2048 px on the long side so they stay sharp in the popup.
 */
internal object NoticeImages {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val failed = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun absolute(url: String): String = when {
        url.startsWith("/") -> BuildConfig.WYRM_API_URL.trimEnd('/') + url
        else -> url
    }

    fun peek(url: String): ImageBitmap? = cache.get(absolute(url))

    suspend fun load(raw: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val url = absolute(raw.trim())
        cache.get(url)?.let { return@withContext it }
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return@withContext null
        failed[url]?.let { if (System.currentTimeMillis() - it < 60_000) return@withContext null }
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "image/*")
            }
            try {
                if (connection.responseCode !in 200..299) null
                else {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?.asImageBitmap()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull().also { image ->
            if (image != null) cache.put(url, image) else failed[url] = System.currentTimeMillis()
        }
    }
}

/** One picture: [width] percent of the message's width, placed left, centre or right. */
@Composable
internal fun NoticeImage(url: String, alt: String, width: Float, align: String, radius: Float = 12f) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    var image by remember(url) { mutableStateOf(NoticeImages.peek(url)) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (image == null) {
            image = NoticeImages.load(url)
            failed = image == null
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(radius.coerceIn(0f, 32f).dp)
        Box(Modifier.width(maxWidth * (width.coerceIn(10f, 100f) / 100f)).align(boxAlign(align))) {
            val shown = image
            when {
                shown != null -> Image(
                    bitmap = shown,
                    contentDescription = alt.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(shown.width.toFloat() / shown.height.coerceAtLeast(1)).clip(shape),
                )
                failed -> Text(alt.ifBlank { "The picture could not load." }, fontFamily = Wyrm.Body, fontSize = 12.sp,
                    color = wyrmScopedPalette.quiet, modifier = Modifier.fillMaxWidth().clip(shape).background(wyrmScopedPalette.well).padding(12.dp))
                else -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(wyrmScopedPalette.well))
            }
        }
    }
}

/** The builder's blocks, top to bottom, as the Observatory's preview shows them. */
@Composable
internal fun NoticeBlocksView(blocks: List<NoticeBlock>, modifier: Modifier = Modifier) {
    val wyrmScopedPalette = LocalWyrmPalette.current ?: Wyrm.currentPalette

    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is NoticeBlock.Heading -> Text(
                    markdownInline(block.text), fontFamily = Wyrm.Display, fontWeight = FontWeight.Bold,
                    fontSize = noticeHeadingSize(block.size).sp, lineHeight = (noticeHeadingSize(block.size) * 1.2f).sp,
                    color = noticeColour(block.color), textAlign = textAlign(block.align), modifier = Modifier.fillMaxWidth(),
                )
                is NoticeBlock.Paragraph -> Text(
                    markdownInline(block.text), fontFamily = Wyrm.Body,
                    fontSize = noticeTextSize(block.size).sp, lineHeight = (noticeTextSize(block.size) * 1.46f).sp,
                    color = noticeColour(block.color), textAlign = textAlign(block.align), modifier = Modifier.fillMaxWidth(),
                )
                is NoticeBlock.Picture -> NoticeImage(block.url, block.alt, block.width, block.align, block.radius)
                is NoticeBlock.Button -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val filled = block.style != "outline"
                    Box(
                        Modifier.width(maxWidth * (block.width.coerceIn(10f, 100f) / 100f)).align(boxAlign(block.align))
                            .height(46.dp).clip(WyrmCapsule)
                            .background(if (filled) wyrmScopedPalette.ink else Color.Transparent)
                            .border(1.2.dp, if (filled) Color.Transparent else wyrmScopedPalette.ink, WyrmCapsule)
                            .clickable { runCatching { uri.openUri(NoticeImages.absolute(block.url)) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(block.label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = if (filled) wyrmScopedPalette.onInk else wyrmScopedPalette.ink, maxLines = 1)
                    }
                }
                NoticeBlock.Divider -> Box(Modifier.fillMaxWidth().height(1.dp).background(wyrmScopedPalette.rule))
                is NoticeBlock.Space -> Spacer(Modifier.height(block.height.coerceIn(4f, 96f).dp))
                is NoticeBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(if (block.ordered) "${index + 1}." else "—", fontFamily = Wyrm.Body, fontSize = 15.sp,
                                color = wyrmScopedPalette.quiet, modifier = Modifier.width(24.dp))
                            Text(markdownInline(item), fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 21.sp,
                                color = noticeColour(block.color), modifier = Modifier.weight(1f))
                        }
                    }
                }
                is NoticeBlock.Quote -> Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(WyrmCapsule).background(wyrmScopedPalette.link))
                    Spacer(Modifier.width(12.dp))
                    Text(markdownInline(block.text), fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 21.sp,
                        color = noticeColour(block.color), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
