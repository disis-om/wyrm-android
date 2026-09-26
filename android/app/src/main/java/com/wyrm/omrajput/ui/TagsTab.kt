package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.Setting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Tags, and the handful of numbers that decide how one hangs.
 *
 * The settings are here rather than in the settings tree, and they open in
 * place rather than on a screen of their own, for one reason: the preview is a
 * few centimetres above them and it is running the real rope. Lengthen the
 * chain and you watch the chain lengthen. Put the same sliders three screens
 * away and they are being adjusted blind.
 */
@Composable
fun TagsTab(
    settings: List<Setting>,
    onChange: (Setting, List<Float>) -> Unit,
    onFetch: (String) -> Unit,
    fetchState: String,
    code: String,
    onCodeChange: (String) -> Unit,
) {
    val atlas by rememberTagAtlas()
    val chosen = settings.firstOrNull { it.id == "tags.index" }
    val selected = chosen?.number?.roundToInt() ?: -1
    var tuning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Wyrm.Gutter),
    ) {
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            WyrmLabel(if (selected < 0) "No tag" else "Tag ${selected + 1}")
            Spacer(Modifier.weight(1f))
            GearButton(open = tuning) { tuning = !tuning }
        }

        // The settings drop in underneath the gear rather than replacing the
        // grid, so the tag being tuned is still on screen while it is tuned.
        AnimatedVisibility(
            visible = tuning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TAG_SETTINGS.forEach { id ->
                    settings.firstOrNull { it.id == id }?.let { setting ->
                        WyrmRule()
                        SettingRow(setting) { onChange(setting, it) }
                    }
                }
                WyrmRule()
            }
        }

        Spacer(Modifier.height(14.dp))

        // "None" first, because taking a tag off should be as easy as putting
        // one on and should not mean hunting for an empty square at the end.
        val columns = 4
        val cells = TAG_ART.size + 1
        val rows = (cells + columns - 1) / columns
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (column in 0 until columns) {
                    val cell = row * columns + column
                    if (cell >= cells) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val index = cell - 1
                    TagCell(
                        atlas = atlas,
                        index = index,
                        selected = index == selected,
                        modifier = Modifier.weight(1f),
                        onPick = {
                            chosen?.let { onChange(it, listOf(index.toFloat())) }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        WyrmRule()
        Spacer(Modifier.height(16.dp))

        WyrmLabel("Private tag")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "If you have been given a tag id and password, put them in " +
                "here as \"id password\" — the mod's own \"!tag id password\" " +
                "works too. They are checked by the tag service, not by Wyrm. " +
                "It only answers whether the tag is yours; the artwork for a " +
                "private one stays on its side, so a tag Wyrm does not carry " +
                "will not appear in the grid above.",
            fontFamily = Wyrm.Body,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Wyrm.Grey,
        )
        Spacer(Modifier.height(10.dp))
        WyrmSearchField(
            value = code,
            placeholder = "id password",
            onValueChange = onCodeChange,
        )
        Spacer(Modifier.height(10.dp))
        WyrmPill(
            label = if (fetchState.isEmpty()) "Fetch tag" else fetchState,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onFetch(code) },
        )

        Spacer(Modifier.height(34.dp))
    }
}

/**
 * The tag settings, in the order they are shown.
 *
 * Named rather than filtered by group so that the tag itself — which is the
 * grid below, not a slider — cannot appear among them.
 */
private val TAG_SETTINGS = listOf(
    "tags.chain", "tags.swing", "tags.scale",
    "tags.small", "tags.hidden", "tags.team_only",
)

@Composable
private fun GearButton(open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(wyrmRounded(Wyrm.Pill))
            .background(glassFill(if (open) 2.0f else 1f))
            .border(1.dp, glassEdge(), wyrmRounded(Wyrm.Pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⚙",
            fontFamily = Wyrm.Body,
            fontSize = 14.sp,
            color = if (open) Wyrm.White else Wyrm.SoftWhite,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (open) "Done" else "Settings",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (open) Wyrm.White else Wyrm.SoftWhite,
        )
    }
}

/** One square of the picker: the tag's artwork, cut out of the sheet. */
@Composable
private fun TagCell(
    atlas: ImageBitmap?,
    index: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(wyrmRounded(Wyrm.CornerSmall))
            .background(glassFill(if (selected) 2.0f else 1f))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) SolidColor(Wyrm.White) else glassEdge(),
                wyrmRounded(Wyrm.CornerSmall),
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (index < 0) {
            Text(
                text = "NONE",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = if (selected) Wyrm.White else Wyrm.Grey,
            )
        } else if (atlas != null && index < TAG_ART.size) {
            val art = TAG_ART[index]
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                drawTag(atlas, art)
            }
        }
    }
}

/** Draws one tag scaled to fit the square it was given, keeping its shape. */
internal fun DrawScope.drawTag(atlas: ImageBitmap, art: TagArt) {
    val scale = minOf(size.width / art.w, size.height / art.h)
    val width = art.w * scale
    val height = art.h * scale
    drawImage(
        image = atlas,
        srcOffset = IntOffset(art.x, art.y),
        srcSize = IntSize(art.w, art.h),
        dstOffset = IntOffset(
            ((size.width - width) / 2f).roundToInt(),
            ((size.height - height) / 2f).roundToInt(),
        ),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
    )
}

/**
 * The tag sheet, loaded once.
 *
 * Loaded at full size on purpose. Sampling it down would halve the memory and
 * break every coordinate in [TAG_ART], which is written in the sheet's own
 * pixels — and a picker that cuts each tag out of the wrong place is worse than
 * one that costs a few megabytes while it is open.
 */
@Composable
internal fun rememberTagAtlas(): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { loadTagAtlas(context) }
    }
}

private fun loadTagAtlas(context: Context): ImageBitmap? = try {
    context.assets.open(TAG_ATLAS_ASSET).use { stream ->
        BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            },
        )?.asImageBitmap()
    }
} catch (ignored: Throwable) {
    null
}
