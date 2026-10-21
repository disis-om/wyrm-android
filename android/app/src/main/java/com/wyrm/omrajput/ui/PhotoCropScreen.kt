package com.wyrm.omrajput.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a finished crop is worth sending: square, Discord-sized (OM, 2026-10-14). */
private const val OUTPUT_PIXELS = 256
private const val OUTPUT_QUALITY = 85

/**
 * Choosing which square of a photograph becomes a face, like every other app
 * (OM, 2026-10-14): the whole photo is on screen, the avatar's squircle sits
 * in the middle with the rest dimmed, and the photo moves under it in any
 * direction and zooms with a pinch. The window is always full: zoom starts
 * where the photo just covers it and the photo cannot leave it.
 *
 * Before, the photo view itself was a centre-cropped square, so only that
 * centre could ever be moved into the window. Now the photo is drawn whole
 * with the same transform that renders the upload, so what is framed is
 * exactly what is sent.
 */
@Composable
fun PhotoCropScreen(
    photo: Bitmap,
    busy: Boolean,
    error: String,
    insetTop: Dp,
    insetBottom: Dp,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    var scale by remember(photo) { mutableFloatStateOf(1f) }
    var offset by remember(photo) { mutableStateOf(Offset.Zero) }
    var area by remember(photo) { mutableStateOf(Size.Zero) }
    val image = remember(photo) { photo.asImageBitmap() }
    val dim = Wyrm.Paper.copy(alpha = 0.72f)
    val edge = Wyrm.Ink

    // The window: a square in the middle of the stage with a margin around it.
    val window = if (area.width > 0f) min(area.width, area.height) * 0.86f else 0f
    // How far the picture has to be enlarged to cover the window at all: where
    // zoom starts and the floor it can never go below.
    val cover = if (window > 0f) max(window / photo.width, window / photo.height) else 1f

    fun clamp(candidate: Offset, zoom: Float): Offset {
        val drawn = cover * zoom
        val slackX = max(0f, (photo.width * drawn - window) / 2f)
        val slackY = max(0f, (photo.height * drawn - window) / 2f)
        return Offset(candidate.x.coerceIn(-slackX, slackX), candidate.y.coerceIn(-slackY, slackY))
    }

    Column(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = insetTop).height(52.dp).padding(horizontal = 14.dp),
        ) {
            Text(
                text = "Cancel", fontFamily = Wyrm.Body, fontSize = 16.sp, color = Wyrm.Link,
                modifier = Modifier.align(Alignment.CenterStart).clickable(enabled = !busy, onClick = onCancel)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            Text(
                text = "Crop photo", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                color = Wyrm.Ink, modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Column(Modifier.weight(1f).padding(bottom = insetBottom)) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .background(Wyrm.Track)
                    .onSizeChanged { area = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(photo) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 6f)
                            offset = clamp(offset + pan, next)
                            scale = next
                        }
                    },
            ) {
                if (window <= 0f) return@Canvas
                val drawn = cover * scale
                // The whole photo, centred on the window, then moved and zoomed.
                withTransform({
                    translate(size.width / 2f + offset.x, size.height / 2f + offset.y)
                    scale(drawn, drawn, Offset.Zero)
                }) {
                    drawImage(
                        image,
                        dstOffset = IntOffset(-photo.width / 2, -photo.height / 2),
                        dstSize = IntSize(photo.width, photo.height),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                // Everything outside the window is dimmed; the window has the avatar's corners.
                val left = (size.width - window) / 2f
                val top = (size.height - window) / 2f
                val corner = CornerRadius(window * 0.30f)
                val hole = RoundRect(left, top, left + window, top + window, corner)
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    addRoundRect(hole)
                }
                drawPath(shade, dim)
                drawPath(Path().apply { addRoundRect(hole) }, edge.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx()))
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (error.isNotEmpty()) error else "Drag the photo to any side and pinch to zoom. " +
                    "What is inside the frame is what everyone sees.",
                fontFamily = Wyrm.Body, fontSize = 12.sp, lineHeight = 18.sp,
                color = if (error.isNotEmpty()) Wyrm.Badge else Wyrm.Quiet,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    PaperOutlineButton(label = "Cancel", enabled = !busy, onClick = onCancel)
                }
                Box(Modifier.weight(2f)) {
                    PaperPrimaryButton(
                        label = if (busy) "Uploading…" else "Use photo",
                        enabled = !busy && window > 0f,
                        onClick = { onConfirm(renderCrop(photo, window, cover * scale, offset)) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * Redraws the framed square at upload size.
 *
 * Same transform as the preview, in the same order: scale about the centre of
 * the picture, then shift, measured up from the on-screen window to the output
 * square by a single ratio.
 */
private fun renderCrop(photo: Bitmap, window: Float, drawn: Float, offset: Offset): Bitmap {
    val output = Bitmap.createBitmap(OUTPUT_PIXELS, OUTPUT_PIXELS, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(android.graphics.Color.WHITE)
    val ratio = OUTPUT_PIXELS / window
    canvas.save()
    canvas.translate(OUTPUT_PIXELS / 2f + offset.x * ratio, OUTPUT_PIXELS / 2f + offset.y * ratio)
    canvas.scale(drawn * ratio, drawn * ratio)
    canvas.drawBitmap(photo, -photo.width / 2f, -photo.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
    return output
}

/** JPEG at 256 px: a face is a photograph, and this is what the server keeps anyway. */
fun Bitmap.toUploadBytes(): ByteArray =
    java.io.ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, OUTPUT_QUALITY, it) }
        .toByteArray()
