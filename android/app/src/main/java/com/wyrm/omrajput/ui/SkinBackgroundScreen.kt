package com.wyrm.omrajput.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Skin › Arena background. No dedicated spec frame; paper drill-in from 07. */
@Composable
fun SkinBackgroundScreen(
    selected: Int,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
    onWear: () -> Unit,
) {
    SkinDrillScaffold(
        title = "Background",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
        onWear = onWear,
    ) {
        SkinBackgroundContent(selected = selected, onPick = onPick)
    }
}

@Composable
internal fun SkinBackgroundContent(selected: Int, onPick: (Int) -> Unit) {
        SkinSectionLabel("Arena floor", top = 18.dp)
        SkinCard {
            Column(Modifier.padding(14.dp)) {
                val columns = 3
                val rows = (ARENA_BACKGROUNDS.size + columns - 1) / columns
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (column in 0 until columns) {
                            val index = row * columns + column
                            if (index < ARENA_BACKGROUNDS.size) {
                                FloorCell(
                                    background = ARENA_BACKGROUNDS[index],
                                    chosen = index == selected,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(
                                            end = if (column < columns - 1) 10.dp else 0.dp,
                                            bottom = if (row < rows - 1) 12.dp else 0.dp,
                                        ),
                                    onClick = { onPick(index) },
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun FloorCell(
    background: ArenaBackground,
    chosen: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = wyrmRounded(12.dp)
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(Wyrm.Well)
                .border(
                    if (chosen) 2.dp else 1.dp,
                    if (chosen) Wyrm.Ink else Wyrm.Rule,
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val tile = rememberAssetTile(background.asset)
            if (tile != null) {
                Image(
                    bitmap = tile,
                    contentDescription = background.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (background.asset == null) {
                Text(
                    text = "None",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Wyrm.Quiet,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = background.label,
            fontFamily = Wyrm.Body,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp,
            color = if (chosen) Wyrm.Ink else Wyrm.Quiet,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
