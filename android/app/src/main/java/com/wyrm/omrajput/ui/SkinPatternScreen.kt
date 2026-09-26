package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_SKIN_CODE = 256

/** Skin › Pattern. Custom code builder, paper. */
@Composable
fun SkinPatternScreen(
    tables: SkinTables,
    state: SkinState,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onCodeChange: (String, IntArray) -> Unit,
    onWear: () -> Unit,
    embedded: Boolean = false,
) {
    val atlas by rememberSkinAtlas()
    val clipboard = LocalClipboardManager.current

    fun append(group: Int, argb: Int) {
        if (state.code.length >= MAX_SKIN_CODE) return
        val character = tables.codeChars.getOrNull(group)?.toInt()?.toChar() ?: return
        val colours = state.coloursFor(state.code.length) + argb
        onCodeChange(state.code + character, colours)
    }

    var mixing by remember { mutableStateOf(false) }
    val content: @Composable ColumnScope.() -> Unit = {
        SkinSectionLabel("Your pattern", top = 18.dp)
        SkinCard {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "${state.code.length} / $MAX_SKIN_CODE",
                    fontFamily = Wyrm.Body,
                    fontSize = 12.sp,
                    color = Wyrm.Quiet,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val fieldShape = wyrmRounded(10.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(fieldShape)
                        .background(Wyrm.Well)
                        .border(1.dp, Wyrm.Rule, fieldShape)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = state.code,
                        onValueChange = { typed ->
                            val cleaned = typed.lowercase()
                                .filter { tables.groupOf(it) >= 0 }
                                .take(MAX_SKIN_CODE)
                            if (cleaned != state.code) {
                                val kept = cleaned.commonPrefixWith(state.code).length
                                onCodeChange(
                                    cleaned,
                                    IntArray(cleaned.length) {
                                        if (it < kept) state.colourAt(it) else 0
                                    },
                                )
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                            color = Wyrm.Ink,
                        ),
                        cursorBrush = SolidColor(Wyrm.Ink),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (state.code.isEmpty()) {
                                Text(
                                    text = "Type a code, paste one, or tap colours",
                                    fontFamily = Wyrm.Body,
                                    fontSize = 13.sp,
                                    color = Wyrm.TabIdle,
                                )
                            }
                            inner()
                        },
                    )
                }
                val sheet = atlas
                if (state.code.isNotEmpty() && sheet != null) {
                    Spacer(Modifier.height(10.dp))
                    val groups = state.code.map { tables.groupOf(it) }
                    val scroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .horizontalScroll(scroll),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width((groups.size * 10).dp)
                                .fillMaxHeight(),
                        ) {
                            val bead = size.height
                            val step = 10.dp.toPx()
                            groups.forEachIndexed { index, group ->
                                val built = state.colourAt(index)
                                if (built != 0) {
                                    drawCircle(
                                        Color(built),
                                        bead / 2f,
                                        Offset(index * step + bead / 2f, bead / 2f),
                                    )
                                } else if (group >= 0) {
                                    drawBead(sheet, group, index * step, 0f, bead)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperAction("Undo", enabled = state.code.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        val shorter = state.code.length - 1
                        onCodeChange(state.code.dropLast(1), state.coloursFor(shorter))
                    }
                    PaperAction("Copy", enabled = state.code.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        clipboard.setText(AnnotatedString(state.code))
                    }
                    PaperAction("Clear", enabled = state.code.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        onCodeChange("", IntArray(0))
                    }
                }
            }
        }

        SkinSectionLabel(if (mixing) "Mix a colour" else "Original beads")
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaperAction("Beads", enabled = true, modifier = Modifier.weight(1f)) { mixing = false }
            PaperAction("Colour picker", enabled = true, modifier = Modifier.weight(1f)) { mixing = true }
        }
        Spacer(Modifier.height(10.dp))
        if (mixing) {
            SkinCard {
                Column(Modifier.padding(14.dp)) {
                    ColourStudio(
                        tables = tables,
                        enabled = state.code.length < MAX_SKIN_CODE,
                        paper = true,
                        onAdd = ::append,
                    )
                }
            }
        } else {
            SkinCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val groups = tables.palette.indices.filter { it !in SkinTables.DEAD_GROUPS }
                    groups.chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { group ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable { append(group, 0) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val sheet = atlas
                                    if (sheet != null) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            drawBead(sheet, group, 0f, 0f, size.minDimension)
                                        }
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(tables.colourOf(group) ?: Wyrm.Well),
                                        )
                                    }
                                }
                            }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (embedded) {
        Column(content = content)
    } else {
        SkinDrillScaffold(
            title = "Pattern",
            insetTop = insetTop,
            insetBottom = insetBottom,
            onBack = onBack,
            onWear = onWear,
            content = content,
        )
    }
}

@Composable
private fun PaperAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(wyrmRounded(10.dp))
            .border(1.dp, Wyrm.Rule, wyrmRounded(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Wyrm.Body,
            fontSize = 14.sp,
            color = if (enabled) Wyrm.Ink else Wyrm.TabIdle,
        )
    }
}
