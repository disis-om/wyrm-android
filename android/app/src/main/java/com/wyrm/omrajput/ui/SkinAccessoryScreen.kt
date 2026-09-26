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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spec page 25 — Skin › Accessory. */
@Composable
fun SkinAccessoryScreen(
    worn: Int,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
    onWear: () -> Unit,
) {
    val atlas by rememberSkinAtlas()
    SkinDrillScaffold(
        title = "Accessory",
        insetTop = insetTop,
        insetBottom = insetBottom,
        onBack = onBack,
        trailing = "None",
        trailingEnabled = worn >= 0,
        onTrailing = { onPick(-1) },
        onWear = onWear,
    ) {
        SkinAccessoryContent(worn = worn, onPick = onPick, atlas = atlas)
    }
}

@Composable
internal fun SkinAccessoryContent(
    worn: Int,
    onPick: (Int) -> Unit,
    atlas: androidx.compose.ui.graphics.ImageBitmap? = rememberSkinAtlas().value,
) {
        Spacer(Modifier.height(16.dp))
        WornCard(atlas = atlas, worn = worn)
        SkinSectionLabel("Headwear · $ACCESSORY_COUNT")
        SkinCard {
            Column(Modifier.padding(14.dp)) {
                val columns = 4
                val rows = (ACCESSORY_COUNT + columns - 1) / columns
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (column in 0 until columns) {
                            val id = row * columns + column
                            if (id < ACCESSORY_COUNT) {
                                AccessoryCell(
                                    atlas = atlas,
                                    id = id,
                                    selected = worn == id,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(
                                            end = if (column < columns - 1) 10.dp else 0.dp,
                                            bottom = if (row < rows - 1) 10.dp else 0.dp,
                                        ),
                                    onPick = { onPick(id) },
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
private fun WornCard(atlas: androidx.compose.ui.graphics.ImageBitmap?, worn: Int) {
    val shape = wyrmRounded(14.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(wyrmRounded(12.dp))
                .background(Wyrm.Ink),
            contentAlignment = Alignment.Center,
        ) {
            if (worn >= 0 && atlas != null) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    drawAccessory(atlas, worn)
                }
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "WORN",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.92.sp,
                color = Wyrm.Quiet,
            )
            Text(
                text = if (worn < 0) "Nothing yet" else "Accessory ${worn + 1}",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                color = Wyrm.Ink,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "Accessories sit on the head and never affect play.",
                fontFamily = Wyrm.Body,
                fontSize = 12.5.sp,
                color = Wyrm.Quiet,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun AccessoryCell(
    atlas: androidx.compose.ui.graphics.ImageBitmap?,
    id: Int,
    selected: Boolean,
    modifier: Modifier,
    onPick: () -> Unit,
) {
    val shape = wyrmRounded(12.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Wyrm.Ink)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Wyrm.Ink else Wyrm.Rule,
                shape,
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (atlas != null) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                drawAccessory(atlas, id)
            }
        }
    }
}
