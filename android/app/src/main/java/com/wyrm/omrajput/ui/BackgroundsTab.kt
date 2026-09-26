package com.wyrm.omrajput.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The arena's floor.
 *
 * One entry per background, in the same order the engine holds them, because
 * the index is what is stored and what crosses the bridge. Kept as a plain list
 * here rather than read from native: these are packaged assets, so Compose can
 * open the same files the renderer does and show the real tile.
 */
data class ArenaBackground(val label: String, val asset: String?)

val ARENA_BACKGROUNDS = listOf(
    ArenaBackground("Wyrm", "textures/background_4k.png"),
    ArenaBackground("None", null),
    ArenaBackground("Classic", "textures/backgrounds/bgee_classic.png"),
    ArenaBackground("Slither", "textures/backgrounds/bgee2.png"),
    ArenaBackground("Asanoha", "textures/backgrounds/bg_asanoha.png"),
    ArenaBackground("Seigaiha", "textures/backgrounds/bg_seigaiha.png"),
    ArenaBackground("Grey grid", "textures/backgrounds/bg_graygrid.png"),
    ArenaBackground("Rizz", "textures/backgrounds/bg_rizz.png"),
    ArenaBackground("Stars", "textures/backgrounds/bg_usastar.png"),
    ArenaBackground("Circuits", "textures/backgrounds/bg_circuits.png"),
    ArenaBackground("Circuits II", "textures/backgrounds/bg_circuits2.png"),
    ArenaBackground("Hex ice", "textures/backgrounds/bg_hexice.png"),
    ArenaBackground("Hex", "textures/backgrounds/bg_hexB.png"),
    ArenaBackground("Hearts", "textures/backgrounds/bg_hearts.png"),
    ArenaBackground("Leaves", "textures/backgrounds/bg_leaves.png"),
    ArenaBackground("Paint", "textures/backgrounds/bg_paint.png"),
    ArenaBackground("Snakey", "textures/backgrounds/bg_snakey.png"),
    ArenaBackground("Stained glass", "textures/backgrounds/bg_stainedglass.png"),
    ArenaBackground("Kitties", "textures/backgrounds/bg_kitties.png"),
    ArenaBackground("Blue cube", "textures/backgrounds/bg_bluecube.png"),
    ArenaBackground("Purple cube", "textures/backgrounds/bg_purplecube.png"),
    ArenaBackground("Red cube", "textures/backgrounds/bg_redcube.png"),
)

@Composable
fun BackgroundsTab(selected: Int, onPick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = Wyrm.Gutter, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(ARENA_BACKGROUNDS.size) { index ->
            BackgroundCell(
                background = ARENA_BACKGROUNDS[index],
                chosen = index == selected,
                onClick = { onPick(index) },
            )
        }
    }
}

@Composable
private fun BackgroundCell(
    background: ArenaBackground,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(wyrmRounded(Wyrm.Corner))
                .background(Wyrm.Black)
                .border(
                    if (chosen) 2.dp else 1.dp,
                    if (chosen) Wyrm.White else Wyrm.Line,
                    wyrmRounded(Wyrm.Corner),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val tile = rememberAssetTile(background.asset)
            if (tile != null) {
                // Cropped rather than fitted: a tile shown whole tells you less
                // about a floor than a piece of it at the size you will see it.
                Image(
                    bitmap = tile,
                    contentDescription = background.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (background.asset == null) {
                Text(
                    "NONE",
                    fontFamily = Wyrm.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = Wyrm.Faint,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = background.label,
            fontFamily = Wyrm.Body,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp,
            color = if (chosen) Wyrm.White else Wyrm.Faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Decodes a packaged tile, downsampled.
 *
 * These are full-size arena floors — several are close to a megapixel — and a
 * grid of twenty at full resolution is both slow and pointless at this size, so
 * they are sampled down on the way in.
 */
@Composable
internal fun rememberAssetTile(asset: String?): ImageBitmap? {
    if (asset == null) return null
    val context = LocalContext.current
    var bitmap by remember(asset) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(asset) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(asset).use { stream ->
                    BitmapFactory.decodeStream(
                        stream, null,
                        BitmapFactory.Options().apply { inSampleSize = 4 },
                    )?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}
