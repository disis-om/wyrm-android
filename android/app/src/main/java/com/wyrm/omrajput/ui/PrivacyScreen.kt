package com.wyrm.omrajput.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spec page 15 — Settings › Privacy, and the same document from sign-in.
 *
 * What is stored, said plainly. No invented DM/invite pickers. Delete account
 * lives on Profile, where the session already is.
 */
@Composable
fun PrivacyScreen(
    insetTop: Dp,
    insetBottom: Dp,
    backLabel: String = "Back",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val document by produceState(initialValue = emptyList<Block>(), context) {
        value = withContext(Dispatchers.IO) { parseMarkdown(readPolicy(context)) }
    }

    SettingsDrillScaffold(
        title = "Privacy",
        parent = backLabel,
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
    ) {
        SettingsSectionLabel("What Wyrm keeps", top = 18.dp)
        SettingsCard {
            SettingsValueRow("Stored on this phone", "Team ID, auth key, all settings", first = true)
            SettingsValueRow("Stored on the server", "Name, username, photo, bio, scores", first = false)
            SettingsValueRow("Chat retention", "Global 24 hours · direct until deleted", first = false)
            SettingsValueRow("Analytics", "Crash reports only", first = false)
        }
        SettingsSectionLabel("The policy")
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            document.forEach { block -> BlockView(block) }
        }
    }
}

@Composable
private fun BlockView(block: Block) {
    when (block) {
        is Block.Title -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = block.text,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = Wyrm.Ink,
            )
            Spacer(Modifier.height(6.dp))
        }

        is Block.Heading -> {
            Spacer(Modifier.height(26.dp))
            Text(
                text = block.text,
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = if (block.level == 2) 17.sp else 14.sp,
                letterSpacing = if (block.level == 2) 0.sp else 1.2.sp,
                color = if (block.level == 2) Wyrm.Ink else Wyrm.Quiet,
            )
            Spacer(Modifier.height(8.dp))
        }

        is Block.Paragraph -> {
            Text(
                text = inline(block.text),
                fontFamily = Wyrm.Body,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Wyrm.Mute,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        is Block.Bullet -> {
            Row(modifier = Modifier.padding(bottom = 10.dp)) {
                Text(
                    text = "—",
                    fontFamily = Wyrm.Body,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Wyrm.Quiet,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = inline(block.text),
                    fontFamily = Wyrm.Body,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Wyrm.Mute,
                )
            }
        }

        // A two-column table row, drawn as the pair it is rather than as a grid
        // — a phone is too narrow for columns, and the pairing is the content.
        is Block.Pair -> {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = inline(block.term),
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Wyrm.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = inline(block.detail),
                    fontFamily = Wyrm.Body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Wyrm.Mute,
                )
            }
        }

        Block.Rule -> {
            Spacer(Modifier.height(12.dp))
            WyrmRule()
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Bold spans and link text, which is all the inline Markdown this document uses. */
private fun inline(source: String) = buildAnnotatedString {
    var index = 0
    // Links are flattened to their label: the address is in the document for a
    // reader who wants it, and a tappable link inside a policy is one more way
    // to leave the screen you were asked to read.
    val text = Regex("""\[([^\]]+)]\(([^)]+)\)""").replace(source) { it.groupValues[1] }
    while (index < text.length) {
        val open = text.indexOf("**", index)
        if (open < 0) {
            append(text.substring(index))
            break
        }
        val close = text.indexOf("**", open + 2)
        if (close < 0) {
            append(text.substring(index))
            break
        }
        append(text.substring(index, open))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Wyrm.Ink)) {
            append(text.substring(open + 2, close))
        }
        index = close + 2
    }
}

private sealed interface Block {
    data class Title(val text: String) : Block
    data class Heading(val text: String, val level: Int) : Block
    data class Paragraph(val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Pair(val term: String, val detail: String) : Block
    data object Rule : Block
}

private fun readPolicy(context: Context): String =
    runCatching {
        context.assets.open("privacy.md").bufferedReader().use { it.readText() }
    }.getOrElse { "The privacy policy could not be opened on this device." }

/**
 * Enough Markdown for this one document.
 *
 * Headings, bullets, paragraphs, rules, and the two-column table. Not a general
 * parser and not trying to be — the document it renders lives in the same
 * repository, so the two can be kept in step by reading them.
 */
private fun parseMarkdown(source: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.setLength(0)
        }
    }

    source.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()

            line.startsWith("# ") -> { flush(); blocks += Block.Title(line.removePrefix("# ")) }
            line.startsWith("### ") -> { flush(); blocks += Block.Heading(line.removePrefix("### "), 3) }
            line.startsWith("## ") -> { flush(); blocks += Block.Heading(line.removePrefix("## "), 2) }
            line.startsWith("---") -> { flush(); blocks += Block.Rule }

            // The separator row of a table, which carries nothing.
            line.startsWith("|") && line.all { it == '|' || it == '-' || it == ' ' } -> flush()

            line.startsWith("|") -> {
                flush()
                val cells = line.trim('|').split('|').map { it.trim() }
                if (cells.size >= 2 && !cells[0].equals("What", ignoreCase = true)) {
                    blocks += Block.Pair(cells[0], cells[1])
                }
            }

            line.startsWith("- ") -> { flush(); blocks += Block.Bullet(line.removePrefix("- ")) }

            // A bullet that ran onto the next line stays part of its bullet.
            // Markdown says so by indenting it, and without this every wrapped
            // bullet became a stray paragraph of its own.
            raw.startsWith("  ") && paragraph.isEmpty() && blocks.lastOrNull() is Block.Bullet -> {
                val bullet = blocks.removeAt(blocks.lastIndex) as Block.Bullet
                blocks += Block.Bullet(bullet.text + " " + line)
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flush()
    return blocks
}
