package com.wyrm.omrajput.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrm.omrajput.data.CustomFloors
import com.wyrm.omrajput.i18n.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Add your photo" tile: opens the gallery, shrinks the photo and wears it. */
@Composable
internal fun AddFloorTile(onAdded: (CustomFloors.Floor) -> Unit) {
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; failed = false
        scope.launch {
            val floor = withContext(Dispatchers.IO) { CustomFloors.add(context, uri) }
            busy = false
            if (floor == null) failed = true else {
                CustomFloors.keep(context, floor)
                onAdded(floor)
            }
        }
    }
    val shape = wyrmRounded(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(palette.card.copy(alpha = 0.92f))
            .border(1.dp, palette.rule, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (!busy) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp).clip(wyrmRounded(13.dp)).background(palette.ink.copy(alpha = .06f)),
            contentAlignment = Alignment.Center) {
            Text(if (busy) "…" else "+", fontFamily = Wyrm.Body, fontSize = 30.sp, color = palette.ink)
        }
        Text(if (failed) "Could not read it" else "Your photo", fontFamily = Wyrm.Body, fontSize = 12.5.sp,
            color = if (failed) Color(0xFFE5484D) else palette.ink, maxLines = 1,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun floorThumb(floor: CustomFloors.Floor): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, floor.id) {
        value = withContext(Dispatchers.IO) { CustomFloors.thumbnail(floor)?.asImageBitmap() }
    }
    return image
}

/** One of your photos: tap wears it, a long press opens the delete list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CustomFloorTile(floor: CustomFloors.Floor, selected: Boolean, onPick: () -> Unit, onHold: () -> Unit) {
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val shape = wyrmRounded(16.dp)
    val image = floorThumb(floor)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(palette.card.copy(alpha = 0.92f))
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.ink else palette.rule, shape)
            .combinedClickable(remember { MutableInteractionSource() }, indication = null,
                onLongClick = onHold, onClick = onPick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp).clip(wyrmRounded(13.dp)).background(palette.ink)) {
            if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text("Your photo", fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = palette.ink, maxLines = 1,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

/** Long press: tick photos, Delete. */
@Composable
internal fun CustomFloorDeleteDialog(start: String, onDeleted: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val context = LocalContext.current
    val ticked = remember { mutableStateMapOf(start to true) }
    CentredDialog(onDismiss = onDismiss) {
        Column {
            Text("Your photos", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = palette.ink)
            Text("Tick the ones to delete.", fontFamily = Wyrm.Body, fontSize = 13.sp, color = palette.quiet)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Adaptive(92.dp), Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(CustomFloors.floors, key = { it.id }) { floor ->
                    val on = ticked[floor.id] == true
                    val image = floorThumb(floor)
                    Box(Modifier.padding(4.dp).fillMaxWidth().height(80.dp).clip(wyrmRounded(12.dp))
                        .background(palette.ink)
                        .border(if (on) 3.dp else 0.dp, if (on) Color(0xFFE5484D) else Color.Transparent, wyrmRounded(12.dp))
                        .clickable { ticked[floor.id] = !on }) {
                        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape)
                            .background(if (on) Color(0xFFE5484D) else Color.White.copy(alpha = .75f)),
                            contentAlignment = Alignment.Center) {
                            if (on) Text("✓", color = Color.White, fontSize = 12.sp, fontFamily = Wyrm.Body)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val count = ticked.count { it.value }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Cancel", fontFamily = Wyrm.Body, fontSize = 15.sp, color = palette.ink,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
                Text(if (count > 0) "Delete $count" else "Delete", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = if (count > 0) Color(0xFFE5484D) else palette.quiet,
                    modifier = Modifier.clickable(enabled = count > 0) {
                        onDeleted(CustomFloors.delete(context, ticked.filter { it.value }.keys))
                    }.padding(12.dp))
            }
        }
    }
}

/** Your photos and the Add tile, above the built-in floors (SkinScreenHooks.floorPhotos). */
@Composable
internal fun CustomFloorsSection(worn: Int, wear: (Int) -> Unit) {
    val context = LocalContext.current
    val photos = CustomFloors.floors
    val chosen = CustomFloors.chosen.value
    var deleting by remember { mutableStateOf<String?>(null) }
    fun pick(id: String) {
        CustomFloors.choose(context, id)
        wear(CustomFloors.INDEX)
    }
    TileGrid(minimum = 104.dp, count = photos.size + 1, aspect = null, fixedColumns = 3) { index ->
        if (index < photos.size) {
            val floor = photos[index]
            CustomFloorTile(floor, worn == CustomFloors.INDEX && chosen == floor.id,
                onPick = { pick(floor.id) }, onHold = { deleting = floor.id })
        } else {
            AddFloorTile { floor -> pick(floor.id) }
        }
    }
    Spacer(Modifier.height(10.dp))
    deleting?.let { start ->
        CustomFloorDeleteDialog(start, onDeleted = { wornGone ->
            deleting = null
            if (wornGone && worn == CustomFloors.INDEX) wear(0)
        }, onDismiss = { deleting = null })
    }
}

/** For WyrmActivity (Java): the hook value. */
internal fun customFloorsHook(): @Composable (Int, (Int) -> Unit) -> Unit = { worn, wear -> CustomFloorsSection(worn, wear) }
