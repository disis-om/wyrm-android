package com.wyrm.omrajput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private val MARKDOWN_EXTENSIONS: List<Extension> = listOf(
    TablesExtension.create(),
    StrikethroughExtension.create(),
    TaskListItemsExtension.create(),
    AutolinkExtension.create(),
)

private val MARKDOWN_PARSER: Parser = Parser.builder()
    .extensions(MARKDOWN_EXTENSIONS)
    .build()

/**
 * CommonMark parsed correctly and drawn in Wyrm's own type and spacing.
 *
 * Parsing is cached by source because a feed re-composes while it scrolls. The
 * parser owns syntax; this file owns appearance, so no library theme can leak
 * into the app's established surface language.
 */
@Composable
fun WyrmMarkdown(
    source: String,
    modifier: Modifier = Modifier,
    baseSize: TextUnit = 13.sp,
    onMediaOpen: ((ReleaseMedia) -> Unit)? = null,
) {
    val document = remember(source) { MARKDOWN_PARSER.parse(source) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        document.children().forEach { MarkdownBlock(it, baseSize, 0, onMediaOpen) }
    }
}

@Composable
private fun MarkdownBlock(
    node: Node,
    baseSize: TextUnit,
    depth: Int,
    onMediaOpen: ((ReleaseMedia) -> Unit)?,
) {
    when (node) {
        is Paragraph -> {
            val media = paragraphMedia(node)
            if (media != null && onMediaOpen != null) {
                ReleaseMediaCard(media = media, onOpen = { onMediaOpen(media) })
            } else {
                MarkdownText(node, baseSize, Wyrm.Ink)
            }
        }
        is Heading -> {
            val size = when (node.level) { 1 -> 26.sp; 2 -> 20.sp; else -> 14.sp }
            Text(
                text = inline(node),
                fontFamily = if (node.level <= 2) Wyrm.Display else Wyrm.Body,
                fontWeight = if (node.level >= 3) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = size,
                letterSpacing = if (node.level >= 3) 0.4.sp else TextUnit.Unspecified,
                color = Wyrm.Ink,
                modifier = Modifier.padding(top = if (node.level == 1) 6.dp else 2.dp),
            )
        }
        is ThematicBreak -> WyrmRule(modifier = Modifier.padding(vertical = 6.dp))
        is BlockQuote -> Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(2.dp).align(Alignment.Top).background(Wyrm.Link))
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) { node.children().forEach { MarkdownBlock(it, baseSize, depth + 1, onMediaOpen) } }
        }
        is BulletList -> MarkdownList(node, baseSize, depth, null, onMediaOpen)
        is OrderedList -> MarkdownList(node, baseSize, depth, node.startNumber, onMediaOpen)
        is FencedCodeBlock -> CodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> CodeBlock(node.literal, "")
        is TableBlock -> MarkdownTable(node, baseSize)
        is HtmlBlock -> {
            val media = htmlMedia(node.literal)
            if (media != null && onMediaOpen != null) {
                ReleaseMediaCard(media = media, onOpen = { onMediaOpen(media) })
            } else {
                LiteralHtml(node.literal, baseSize)
            }
        }
        else -> node.children().forEach { MarkdownBlock(it, baseSize, depth, onMediaOpen) }
    }
}

@Composable
private fun MarkdownText(node: Node, size: TextUnit, color: Color) {
    Text(
        text = inline(node),
        fontFamily = Wyrm.Body,
        fontSize = size,
        lineHeight = size * 1.46f,
        color = color,
    )
}

@Composable
private fun MarkdownList(
    list: Node,
    baseSize: TextUnit,
    depth: Int,
    orderedStart: Int?,
    onMediaOpen: ((ReleaseMedia) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            val task = item.descendants().filterIsInstance<TaskListItemMarker>().firstOrNull()
            Row(
                modifier = Modifier.padding(start = (depth * 14).dp),
                verticalAlignment = Alignment.Top,
            ) {
                when {
                    task != null -> TaskMarker(task.isChecked)
                    orderedStart != null -> Text(
                        text = "${orderedStart + index}.",
                        fontFamily = Wyrm.Body,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        color = Wyrm.Quiet,
                        modifier = Modifier.width(22.dp),
                    )
                    else -> Text(
                        text = when (depth % 3) { 0 -> "—"; 1 -> "·"; else -> "▪" },
                        fontFamily = Wyrm.Body,
                        fontSize = baseSize,
                        color = Wyrm.Quiet,
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.children().forEach { MarkdownBlock(it, baseSize, depth + 1, onMediaOpen) }
                }
            }
        }
    }
}

@Composable
private fun TaskMarker(checked: Boolean) {
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .width(13.dp)
            .clip(wyrmRounded(4.dp))
            .background(if (checked) Wyrm.Live else Wyrm.Well),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (checked) "✓" else " ",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = Wyrm.Black,
        )
    }
}

@Composable
private fun CodeBlock(source: String, language: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (language.isNotBlank()) WyrmLabel(language.substringBefore(' '))
        WyrmWell(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = source.trimEnd(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Wyrm.Ink,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}

@Composable
private fun MarkdownTable(table: TableBlock, baseSize: TextUnit) {
    val rows = table.children()
        .filter { it is TableHead || it is TableBody }
        .flatMap { section ->
            section.children().filterIsInstance<TableRow>().map { row ->
                MarkdownTableRow(
                    cells = row.children().filterIsInstance<TableCell>().toList(),
                    header = section is TableHead,
                )
            }
        }
        .toList()
    val columnCount = rows.maxOfOrNull { it.cells.size } ?: return
    if (columnCount == 0) return

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minimumWidth = 128.dp * columnCount
        val tableWidth = if (minimumWidth > maxWidth) minimumWidth else maxWidth
        val shape = wyrmRounded(14.dp)
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .width(tableWidth)
                    .clip(shape)
                    .background(Wyrm.Carbon.copy(alpha = 0.88f))
                    .border(1.dp, Wyrm.Line, shape),
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    val header = row.header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(
                                when {
                                    header -> Wyrm.Well
                                    rowIndex % 2 == 0 -> Color.White.copy(alpha = 0.025f)
                                    else -> Color.Transparent
                                }
                            ),
                    ) {
                        repeat(columnCount) { columnIndex ->
                            val cell = row.cells.getOrNull(columnIndex)
                            Text(
                                text = cell?.let(::inline) ?: AnnotatedString(""),
                                fontFamily = Wyrm.Body,
                                fontWeight = if (header || cell?.isHeader == true) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                fontSize = if (header) 12.sp else baseSize,
                                letterSpacing = if (header) 0.35.sp else TextUnit.Unspecified,
                                color = if (header) Wyrm.Ink else Wyrm.Mute,
                                textAlign = when (cell?.alignment) {
                                    TableCell.Alignment.CENTER -> TextAlign.Center
                                    TableCell.Alignment.RIGHT -> TextAlign.End
                                    else -> TextAlign.Start
                                },
                                lineHeight = if (header) 17.sp else baseSize * 1.4f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                            )
                            if (columnIndex < columnCount - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                        .background(Wyrm.Line)
                                )
                            }
                        }
                    }
                    if (rowIndex < rows.lastIndex) WyrmRule()
                }
            }
        }
    }
}

private data class MarkdownTableRow(
    val cells: List<TableCell>,
    val header: Boolean,
)

@Composable
private fun LiteralHtml(literal: String, baseSize: TextUnit) {
    Text(
        text = literal,
        fontFamily = Wyrm.Body,
        fontSize = baseSize,
        lineHeight = baseSize * 1.46f,
        color = Wyrm.Ink,
    )
}

private fun inline(parent: Node): AnnotatedString = buildAnnotatedString {
    fun appendNode(node: Node) {
        when (node) {
            is Text -> append(node.literal)
            is SoftLineBreak -> append('\n')
            is HardLineBreak -> append('\n')
            is Code -> {
                pushStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Wyrm.Ink,
                    background = Wyrm.Well,
                ))
                append(node.literal)
                pop()
            }
            is Emphasis -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                node.children().forEach(::appendNode)
                pop()
            }
            is StrongEmphasis -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Wyrm.Ink))
                node.children().forEach(::appendNode)
                pop()
            }
            is Strikethrough -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Wyrm.Quiet))
                node.children().forEach(::appendNode)
                pop()
            }
            is Link -> {
                pushLink(LinkAnnotation.Url(node.destination))
                pushStyle(SpanStyle(color = Wyrm.Link, textDecoration = TextDecoration.Underline))
                node.children().forEach(::appendNode)
                pop()
                pop()
            }
            is Image -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Wyrm.Quiet))
                node.children().forEach(::appendNode)
                pop()
            }
            is HtmlInline -> append(node.literal)
            is TaskListItemMarker -> Unit
            else -> node.children().forEach(::appendNode)
        }
    }
    parent.children().forEach(::appendNode)
}

private fun Node.children(): Sequence<Node> = sequence {
    var node = firstChild
    while (node != null) {
        yield(node)
        node = node.next
    }
}

private fun Node.descendants(): Sequence<Node> = sequence {
    children().forEach { child ->
        yield(child)
        yieldAll(child.descendants())
    }
}

/** A media paragraph stays a media block instead of collapsing to alt text. */
private fun paragraphMedia(paragraph: Paragraph): ReleaseMedia? {
    val children = paragraph.children().toList()
    if (children.size != 1) return null
    return when (val child = children.single()) {
        is Image -> ReleaseMedia(
            url = child.destination,
            kind = ReleaseMediaKind.IMAGE,
            description = plainText(child).ifBlank { "Release image" },
        ).takeIf { trustedReleaseMediaUrl(it.url) }
        is Link -> ReleaseMedia(
            url = child.destination,
            kind = ReleaseMediaKind.VIDEO,
            description = plainText(child).ifBlank { "Release video" },
        ).takeIf { trustedReleaseMediaUrl(it.url) && videoReleaseUrl(it.url) }
        else -> null
    }
}

/** Git-hosted uploaded videos may be emitted as a small HTML video element. */
private fun htmlMedia(source: String): ReleaseMedia? {
    val match = Regex("""<video[^>]+src=[\"']([^\"']+)[\"'][^>]*>""", RegexOption.IGNORE_CASE)
        .find(source) ?: return null
    val url = match.groupValues[1]
    return ReleaseMedia(url, ReleaseMediaKind.VIDEO, "Release video")
        .takeIf { trustedReleaseMediaUrl(url) }
}

private fun plainText(parent: Node): String = buildString {
    parent.descendants().filterIsInstance<Text>().forEach { append(it.literal) }
}
