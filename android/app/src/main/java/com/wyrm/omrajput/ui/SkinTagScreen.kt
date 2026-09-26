package com.wyrm.omrajput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import kotlin.math.roundToInt

/** Spec page 26 — Skin › Tag. */
@Composable
fun SkinTagScreen(
    settings: List<Setting>,
    tagCode: String,
    tagFetchState: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onSettingChange: (Setting, List<Float>) -> Unit,
    onTagCodeChange: (String) -> Unit,
    onFetchTag: (String) -> Unit,
) {
    val atlas by rememberTagAtlas()
    val chosen = settings.firstOrNull { it.id == "tags.index" }
    val selected = chosen?.number?.roundToInt() ?: -1
    val chain = settings.firstOrNull { it.id == "tags.chain" }
    val swing = settings.firstOrNull { it.id == "tags.swing" }
    val scale = settings.firstOrNull { it.id == "tags.scale" }
    val shrink = settings.firstOrNull { it.id == "tags.small" }
    val teamOnly = settings.firstOrNull { it.id == "tags.team_only" }
    val hideAll = settings.firstOrNull { it.id == "tags.hidden" }

    SkinDrillScaffold(
        title = "Tag",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
        trailing = "None",
        trailingEnabled = selected >= 0,
        onTrailing = { chosen?.let { onSettingChange(it, listOf(-1f)) } },
        wear = false,
    ) {
        SkinSectionLabel("Pick one", top = 18.dp)
        SkinCard {
            Column(Modifier.padding(14.dp)) {
                val columns = 4
                val cells = TAG_ART.size + 1
                val rows = (cells + columns - 1) / columns
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (column in 0 until columns) {
                            val cell = row * columns + column
                            if (cell >= cells) {
                                Spacer(Modifier.weight(1f))
                                continue
                            }
                            val index = cell - 1
                            TagPickCell(
                                atlas = atlas,
                                index = index,
                                selected = index == selected,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        end = if (column < columns - 1) 10.dp else 0.dp,
                                        bottom = if (row < rows - 1) 10.dp else 0.dp,
                                    ),
                                onPick = { chosen?.let { onSettingChange(it, listOf(index.toFloat())) } },
                            )
                        }
                    }
                }
            }
        }

        SkinSectionLabel("How it moves")
        SkinCard {
            if (chain != null) {
                SliderRow(chain, "How far it trails behind the head.", first = true, onSettingChange)
            }
            if (swing != null) {
                SliderRow(swing, "", first = chain == null, onSettingChange)
            }
            if (scale != null) {
                SliderRow(scale, "", first = chain == null && swing == null, onSettingChange)
            }
        }

        SkinSectionLabel("Everyone else's tags")
        SkinCard {
            if (shrink != null) {
                SwitchRow(
                    title = "Shrink large tags",
                    detail = "Keeps the arena readable.",
                    on = shrink.enabled,
                    first = true,
                    onToggle = { onSettingChange(shrink, listOf(if (it) 1f else 0f)) },
                )
            }
            if (teamOnly != null) {
                SwitchRow(
                    title = "Team tags only",
                    detail = "Hides tags outside your team.",
                    on = teamOnly.enabled,
                    first = shrink == null,
                    onToggle = { onSettingChange(teamOnly, listOf(if (it) 1f else 0f)) },
                )
            }
            if (hideAll != null) {
                SwitchRow(
                    title = "Hide all tags",
                    detail = "Including your own.",
                    on = hideAll.enabled,
                    first = shrink == null && teamOnly == null,
                    onToggle = { onSettingChange(hideAll, listOf(if (it) 1f else 0f)) },
                )
            }
        }

        SkinSectionLabel("Private tag")
        SkinCard {
            Text(
                text = "If you have been given a tag id and password, put them here as “id password”.",
                fontFamily = Wyrm.Body,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Wyrm.Quiet,
                modifier = Modifier.padding(14.dp),
            )
            val fieldShape = wyrmRounded(10.dp)
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(fieldShape)
                    .background(Wyrm.Card)
                    .border(1.dp, Wyrm.Rule, fieldShape)
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = tagCode,
                    onValueChange = onTagCodeChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Wyrm.Ink,
                    ),
                    cursorBrush = SolidColor(Wyrm.Ink),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (tagCode.isEmpty()) {
                            Text(
                                text = "id password",
                                fontFamily = Wyrm.Body,
                                fontSize = 14.sp,
                                color = Wyrm.TabIdle,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(wyrmRounded(11.dp))
                    .background(Wyrm.Ink)
                    .clickable { onFetchTag(tagCode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tagFetchState.ifEmpty { "Fetch tag" },
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Wyrm.OnInk,
                )
            }
        }
    }
}

@Composable
internal fun TagPickCell(
    atlas: androidx.compose.ui.graphics.ImageBitmap?,
    index: Int,
    selected: Boolean,
    modifier: Modifier,
    onPick: () -> Unit,
) {
    val shape = wyrmRounded(12.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (index < 0) Wyrm.Paper else Wyrm.Ink)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Wyrm.Ink else Wyrm.Rule,
                shape,
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (index < 0) {
            Text(
                text = "None",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Wyrm.Ink,
            )
        } else if (atlas != null && index < TAG_ART.size) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                drawTag(atlas, TAG_ART[index])
            }
        }
    }
}
