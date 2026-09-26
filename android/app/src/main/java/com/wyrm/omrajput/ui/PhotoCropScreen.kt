package com.wyrm.omrajput.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** What a finished crop is worth sending: square, and no larger than this. */
private const val OUTPUT_PIXELS = 512
private const val OUTPUT_QUALITY = 90

/**
 * Choosing which square of a photograph becomes a face.
 *
 * Drag to move, pinch to zoom, and the square never leaves the picture — the
 * offsets are clamped rather than sprung back, so it is impossible to commit a
 * crop with an empty edge in it. What is committed is exactly what is framed:
 * the same arithmetic that positions the preview positions the canvas that
 * renders it, so nothing shifts between what was chosen and what is uploaded.
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
    var viewport by remember(photo) { mutableFloatStateOf(0f) }

    // How far the picture has to be enlarged to cover the square at all, which
    // is where zoom starts and the floor it can never go below.
    val cover = if (viewport > 0f) {
        max(viewport / photo.width, viewport / photo.height)
    } else {
        1f
    }

    fun clamp(candidate: Offset, zoom: Float): Offset {
        val drawn = cover * zoom
        val slackX = max(0f, (photo.width * drawn - viewport) / 2f)
        val slackY = max(0f, (photo.height * drawn - viewport) / 2f)
        return Offset(
            candidate.x.coerceIn(-slackX, slackX),
            candidate.y.coerceIn(-slackY, slackY),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wyrm.Paper),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = insetTop)
                .height(52.dp)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = "Cancel",
                fontFamily = Wyrm.Body,
                fontSize = 16.sp,
                color = Wyrm.Link,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(enabled = !busy, onClick = onCancel)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            Text(
                text = "Crop photo",
                fontFamily = Wyrm.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Wyrm.Ink,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = insetBottom),
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(1f)
                    .clip(wyrmRounded(16.dp))
                    .background(Wyrm.Track)
                    .clipToBounds()
                    .onSizeChanged { viewport = min(it.width, it.height).toFloat() }
                    .pointerInput(photo) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 6f)
                            offset = clamp(offset + pan, next)
                            scale = next
                        }
                    },
            ) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (error.isNotEmpty()) error else "Drag to move, pinch to zoom. " +
                    "The square is what everyone sees.",
                fontFamily = Wyrm.Body,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = if (error.isNotEmpty()) Wyrm.Badge else Wyrm.Quiet,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    PaperOutlineButton(label = "Cancel", enabled = !busy, onClick = onCancel)
                }
                Box(Modifier.weight(2f)) {
                    PaperPrimaryButton(
                        label = if (busy) "Uploading…" else "Use photo",
                        enabled = !busy && viewport > 0f,
                        onClick = { onConfirm(renderCrop(photo, viewport, cover * scale, offset)) },
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
 * Same transform as the preview, in the same order — scale about the centre of
 * the picture, then shift — measured up from the on-screen square to the output
 * square by a single ratio.
 */
private fun renderCrop(photo: Bitmap, viewport: Float, drawn: Float, offset: Offset): Bitmap {
    val output = Bitmap.createBitmap(OUTPUT_PIXELS, OUTPUT_PIXELS, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val ratio = OUTPUT_PIXELS / viewport
    canvas.save()
    canvas.translate(OUTPUT_PIXELS / 2f + offset.x * ratio, OUTPUT_PIXELS / 2f + offset.y * ratio)
    canvas.scale(drawn * ratio, drawn * ratio)
    canvas.drawBitmap(photo, -photo.width / 2f, -photo.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
    return output
}

/** JPEG, because a face is a photograph and PNG would send four times the bytes. */
fun Bitmap.toUploadBytes(): ByteArray =
    java.io.ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, OUTPUT_QUALITY, it) }
        .toByteArray()
