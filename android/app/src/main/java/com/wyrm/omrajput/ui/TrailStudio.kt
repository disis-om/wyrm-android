package com.wyrm.omrajput.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wyrm.omrajput.R
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The Trails studio (OM, 2026-09-28), as Wyrm iOS draws it (`WyrmTrailStudio.swift`):
 *
 *   Photo   — live camera on top, recent photos below. A picked or taken photo
 *             opens in the editor: crop (Original, Free, 1:1, 4:5, 16:9), text,
 *             drawing.
 *   Text    — words only; posted as a text trail.
 *   Canvas  — a plain colour to write and draw on.
 *
 * The editor draws with the same code it exports with (`StudioInk`,
 * `StudioText.draw`, `StudioDraft.photoRect`), so the posted image is what the
 * player saw. Wyrm's own brush is Beads: a stroke laid down as a snake.
 */

// ------------------------------------------------------------------ model

internal object StudioPalette {
    val colours = listOf(
        0xFFFFFF, 0x111111, 0xF2B84B, 0xFF5E5B, 0xFF8FC0, 0xAA7DF0,
        0x3D5AFE, 0x00B8D9, 0x2FA45E, 0xB8F2E6, 0xF6E4C4, 0x1E2F5C,
    )
    fun argb(rgb: Int) = (0xFF shl 24) or rgb
    fun color(rgb: Int) = Color(argb(rgb))
    fun contrast(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        return if (0.299 * r + 0.587 * g + 0.114 * b > 150) 0x111111 else 0xFFFFFF
    }
}

internal enum class StudioBrush(val label: String) { PEN("Pen"), MARKER("Marker"), BEADS("Beads") }

internal class StudioStroke(val points: MutableList<Offset>, val rgb: Int, val width: Float, val brush: StudioBrush)

internal data class StudioText(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val rgb: Int,
    val serif: Boolean,
    val filled: Boolean,
    val center: Offset,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

internal enum class StudioAspect(val label: String) { ORIGINAL("Original"), FREE("Free"), SQUARE("1:1"), PORTRAIT("4:5"), WIDE("16:9") }

internal enum class StudioMode(val label: String) { PHOTO("Photo"), TEXT("Text"), CANVAS("Canvas") }

/** Typefaces and sizes shared by the editor and the export. */
internal class StudioType(context: Context, val density: Float) {
    private val sans = ResourcesCompat.getFont(context, R.font.manrope) ?: Typeface.DEFAULT
    private val serif = ResourcesCompat.getFont(context, R.font.bodoni_moda) ?: Typeface.SERIF
    fun face(serifFace: Boolean): Typeface {
        val base = if (serifFace) serif else sans
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, if (serifFace) 600 else 700, false)
        else Typeface.create(base, Typeface.BOLD)
    }
    val baseSize get() = 30f * density
    val maxWidth get() = (250f * density).roundToInt()
    val pad get() = 14f * density
}

internal object StudioTextPainter {
    private fun layout(item: StudioText, type: StudioType): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = type.face(item.serif)
            textSize = type.baseSize
            color = StudioPalette.argb(if (item.filled) StudioPalette.contrast(item.rgb) else item.rgb)
            if (!item.filled) setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(90, 0, 0, 0))
        }
        val widest = item.text.split('\n').maxOfOrNull { paint.measureText(it) } ?: 0f
        val width = min(type.maxWidth, ceil(widest).toInt().coerceAtLeast(1))
        return StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
    }

    /** The item's own size in canvas pixels, before its scale and rotation. */
    fun size(item: StudioText, type: StudioType): Pair<Float, Float> {
        val l = layout(item, type)
        val pad = if (item.filled) type.pad else 4f * type.density
        return (l.width + pad * 2) to (l.height + pad * 1.4f)
    }

    /** Paints the item centred on the canvas origin. */
    fun draw(canvas: android.graphics.Canvas, item: StudioText, type: StudioType) {
        val l = layout(item, type)
        val (w, h) = size(item, type)
        if (item.filled) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = StudioPalette.argb(item.rgb) }
            val r = min(16f * type.density, h / 2)
            canvas.drawRoundRect(RectF(-w / 2, -h / 2, w / 2, h / 2), r, r, fill)
        }
        canvas.save()
        canvas.translate(-l.width / 2f, -l.height / 2f)
        l.draw(canvas)
        canvas.restore()
    }
}

internal object StudioInk {
    fun draw(canvas: android.graphics.Canvas, strokes: List<StudioStroke>) { for (s in strokes) draw(canvas, s) }

    fun draw(canvas: android.graphics.Canvas, stroke: StudioStroke) {
        val points = stroke.points
        val first = points.firstOrNull() ?: return
        val colour = StudioPalette.argb(stroke.rgb)
        when (stroke.brush) {
            StudioBrush.PEN, StudioBrush.MARKER -> {
                val marker = stroke.brush == StudioBrush.MARKER
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = if (marker) stroke.width * 2.6f else stroke.width
                    color = colour
                    if (marker) { alpha = 115; xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
                }
                val path = Path().apply {
                    moveTo(first.x, first.y)
                    if (points.size == 1) lineTo(first.x + 0.1f, first.y)
                    for (i in 1 until points.size) {
                        val a = points[i - 1]; val b = points[i]
                        quadTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
                    }
                    if (points.size > 1) lineTo(points.last().x, points.last().y)
                }
                canvas.drawPath(path, paint)
            }
            StudioBrush.BEADS -> {
                // Wyrm's brush: beads laid along the path like a snake's body, lit from the top left, head last.
                val radius = stroke.width * 1.1f
                val spacing = radius * 1.35f
                val beads = mutableListOf(first)
                var carry = 0f
                for (i in 1 until points.size) {
                    val a = points[i - 1]; val b = points[i]
                    val length = hypot(b.x - a.x, b.y - a.y)
                    var travelled = spacing - carry
                    while (travelled <= length) {
                        val t = travelled / max(length, 0.0001f)
                        beads += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                        travelled += spacing
                    }
                    carry = length - (travelled - spacing)
                }
                val light = blend(colour, android.graphics.Color.WHITE, 0.6f)
                val dark = blend(colour, android.graphics.Color.BLACK, 0.35f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                beads.forEachIndexed { index, p ->
                    val r = if (index == beads.lastIndex) radius * 1.25f else radius
                    paint.shader = RadialGradient(p.x - r * 0.35f, p.y - r * 0.35f, r * 1.35f,
                        intArrayOf(light, colour, dark), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(p.x, p.y, r, paint)
                }
                paint.shader = null
                if (beads.size > 1) {
                    val head = beads.last(); val prev = beads[beads.size - 2]
                    val angle = atan2(head.y - prev.y, head.x - prev.x)
                    val r = radius * 1.25f
                    for (side in listOf(-1f, 1f)) {
                        val ex = head.x + cos(angle + side * 0.7f) * r * 0.5f
                        val ey = head.y + sin(angle + side * 0.7f) * r * 0.5f
                        paint.color = android.graphics.Color.WHITE; canvas.drawCircle(ex, ey, r * 0.28f, paint)
                        paint.color = android.graphics.Color.BLACK; canvas.drawCircle(ex, ey, r * 0.14f, paint)
                    }
                }
            }
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(
            ch((a shr 16) and 0xFF, (b shr 16) and 0xFF), ch((a shr 8) and 0xFF, (b shr 8) and 0xFF), ch(a and 0xFF, b and 0xFF))
    }
}

internal class StudioDraft {
    var mode by mutableStateOf(StudioMode.PHOTO)
    var image by mutableStateOf<Bitmap?>(null)
    var background by mutableStateOf(0x1E2F5C)
    var aspect by mutableStateOf(StudioAspect.ORIGINAL)
    var freeRatio by mutableFloatStateOf(0.8f)
    var photoScale by mutableFloatStateOf(1f)
    var photoOffset by mutableStateOf(Offset.Zero)
    val strokes = mutableStateListOf<StudioStroke>()
    val texts = mutableStateListOf<StudioText>()
    var caption by mutableStateOf("")
    /** Bumped by every stroke change, so the canvas redraws. */
    var ink by mutableStateOf(0)

    val ratio: Float
        get() = when (aspect) {
            StudioAspect.FREE -> freeRatio
            StudioAspect.SQUARE -> 1f
            StudioAspect.PORTRAIT -> 0.8f
            StudioAspect.WIDE -> 16f / 9f
            StudioAspect.ORIGINAL -> image?.takeIf { it.height > 0 }?.let { (it.width.toFloat() / it.height).coerceIn(0.8f, 1.91f) } ?: 0.8f
        }

    fun reset(next: StudioMode) {
        mode = next
        image = null
        aspect = if (next == StudioMode.CANVAS) StudioAspect.PORTRAIT else StudioAspect.ORIGINAL
        photoScale = 1f
        photoOffset = Offset.Zero
        strokes.clear()
        texts.clear()
        ink++
    }

    /** Where the photo sits in a canvas of [w] x [h]: filling it, then the player's zoom and pan. */
    fun photoRect(w: Float, h: Float): RectF {
        val bitmap = image ?: return RectF(0f, 0f, w, h)
        val fill = max(w / bitmap.width, h / bitmap.height)
        val pw = bitmap.width * fill * photoScale
        val ph = bitmap.height * fill * photoScale
        val left = (w - pw) / 2 + photoOffset.x
        val top = (h - ph) / 2 + photoOffset.y
        return RectF(left, top, left + pw, top + ph)
    }

    /** Keeps the photo covering the canvas after a pan or zoom. */
    fun clampOffset(w: Float, h: Float) {
        val r = photoRect(w, h)
        val maxX = max(0f, (r.width() - w) / 2)
        val maxY = max(0f, (r.height() - h) / 2)
        photoOffset = Offset(photoOffset.x.coerceIn(-maxX, maxX), photoOffset.y.coerceIn(-maxY, maxY))
    }

    /** Everything on a canvas of [w] x [h] pixels; the editor and the export both call this. */
    fun paint(canvas: android.graphics.Canvas, w: Float, h: Float, type: StudioType, live: StudioStroke? = null) {
        val bitmap = image
        if (bitmap != null) {
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(bitmap, null, photoRect(w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            canvas.drawColor(StudioPalette.argb(background))
        }
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        StudioInk.draw(canvas, strokes)
        live?.let { StudioInk.draw(canvas, it) }
        canvas.restoreToCount(layer)
        for (item in texts) {
            canvas.save()
            canvas.translate(item.center.x, item.center.y)
            canvas.rotate(item.rotation)
            canvas.scale(item.scale, item.scale)
            StudioTextPainter.draw(canvas, item, type)
            canvas.restore()
        }
    }

    /** The finished picture, 1440 px wide. */
    fun render(w: Float, h: Float, type: StudioType): Bitmap {
        val k = 1440f / max(w, 1f)
        val out = Bitmap.createBitmap(1440, (h * k).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.scale(k, k)
        paint(canvas, w, h, type)
        return out
    }
}

// ----------------------------------------------------------------- photos

private object StudioThumbs {
    val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
}

private fun recentPhotos(context: Context): List<Uri> = runCatching {
    val uris = mutableListOf<Uri>()
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && uris.size < 300) {
            uris += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(id))
        }
    }
    uris
}.getOrDefault(emptyList())

private fun thumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
    val key = ContentUris.parseId(uri)
    StudioThumbs.cache.get(key) ?: (if (Build.VERSION.SDK_INT >= 29) {
        context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) }
    })?.also { StudioThumbs.cache.put(key, it) }
}.getOrNull()

/** A picked photo at most 2400 px on its long side, upright, in software memory. */
private fun loadPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > 2400) {
                val s = 2400f / longest
                decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}.getOrNull()

private fun hasPhotoAccess(context: Context): Boolean {
    val granted = { p: String -> ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }
    return when {
        Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun ImageProxy.upright(front: Boolean): Bitmap {
    val raw = toBitmap()
    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
        if (front) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

// ----------------------------------------------------------------- studio

private enum class StudioStep { PICK, EDIT, CAPTION }

@Composable
fun TrailStudioScreen(insetTop: Dp, insetBottom: Dp, onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val draft = remember { StudioDraft() }
    val type = remember(density) { StudioType(context, density) }
    var step by remember { mutableStateOf(StudioStep.PICK) }
    var canvasPx by remember { mutableStateOf(0f to 0f) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var loadingPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { TrailsStore.resetPosting() }

    fun open(bitmap: Bitmap) {
        draft.reset(StudioMode.PHOTO)
        draft.image = bitmap
        step = StudioStep.EDIT
    }

    val pickAll = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            loadingPhoto = true
            scope.launch {
                withContext(Dispatchers.IO) { loadPhoto(context, uri) }?.let { open(it) }
                loadingPhoto = false
            }
        }
    }

    fun post(image: Bitmap?) {
        if (TrailsStore.posting.busy) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        TrailsStore.post(image, draft.caption)
        onClose()
    }

    fun back() {
        when (step) {
            StudioStep.PICK -> onClose()
            StudioStep.EDIT -> { step = StudioStep.PICK; if (draft.mode == StudioMode.PHOTO) draft.image = null }
            StudioStep.CAPTION -> step = if (draft.mode == StudioMode.CANVAS) StudioStep.PICK else StudioStep.EDIT
        }
    }

    fun forward() {
        when (step) {
            StudioStep.PICK -> when (draft.mode) {
                StudioMode.TEXT -> post(null)
                StudioMode.CANVAS -> { rendered = draft.render(canvasPx.first, canvasPx.second, type); step = StudioStep.CAPTION }
                StudioMode.PHOTO -> Unit
            }
            StudioStep.EDIT -> { rendered = draft.render(canvasPx.first, canvasPx.second, type); step = StudioStep.CAPTION }
            StudioStep.CAPTION -> post(rendered ?: draft.render(canvasPx.first, canvasPx.second, type))
        }
    }

    val ready = when (step) {
        StudioStep.PICK -> if (draft.mode == StudioMode.TEXT) draft.caption.isNotBlank() else draft.mode == StudioMode.CANVAS
        StudioStep.EDIT -> true
        StudioStep.CAPTION -> !TrailsStore.posting.busy
    }
    val actionLabel = if (step == StudioStep.CAPTION || (step == StudioStep.PICK && draft.mode == StudioMode.TEXT)) "Post" else "Next"
    val title = when (step) {
        StudioStep.PICK -> when (draft.mode) { StudioMode.TEXT -> "Text trail"; StudioMode.CANVAS -> "Canvas"; else -> "New trail" }
        StudioStep.EDIT -> "Edit"
        StudioStep.CAPTION -> "Caption"
    }

    val modeBar: @Composable () -> Unit = {
        Box(Modifier.padding(horizontal = 16.dp)) {
            PaperSegmented(
                options = StudioMode.entries.map { it.label },
                selected = draft.mode.ordinal,
                onSelect = { index ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    draft.reset(StudioMode.entries[index])
                    step = StudioStep.PICK
                },
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Wyrm.Paper).padding(top = insetTop, bottom = insetBottom).imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Wyrm.Well)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { back() },
                contentAlignment = Alignment.Center,
            ) {
                if (step == StudioStep.PICK) Text("✕", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
                else IosIcon(IosGlyph.CHEVRON_LEFT, Wyrm.Ink, size = 16.dp, semibold = true)
            }
            Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Wyrm.Ink,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).background(Wyrm.Ink.copy(alpha = if (ready) 1f else 0.3f))
                    .clickable(enabled = ready, interactionSource = remember { MutableInteractionSource() }, indication = null) { forward() }
                    .padding(horizontal = 16.dp).height(36.dp),
                contentAlignment = Alignment.Center,
            ) { Text(actionLabel, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Wyrm.OnInk) }
        }
        when {
            step == StudioStep.CAPTION -> CaptionStep(draft, rendered)
            step == StudioStep.EDIT || draft.mode == StudioMode.CANVAS ->
                StudioEditor(draft, type, showModes = step == StudioStep.PICK, modeBar = modeBar) { w, h -> canvasPx = w to h }
            draft.mode == StudioMode.TEXT -> TextComposer(draft, modeBar)
            else -> PhotoPicker(
                modeBar = modeBar,
                loading = loadingPhoto,
                onCaptured = { open(it) },
                onPick = { uri ->
                    loadingPhoto = true
                    scope.launch {
                        withContext(Dispatchers.IO) { loadPhoto(context, uri) }?.let { open(it) }
                        loadingPhoto = false
                    }
                },
                onAll = { pickAll.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
    }
}

@Composable
private fun PhotoPicker(
    modeBar: @Composable () -> Unit,
    loading: Boolean,
    onCaptured: (Bitmap) -> Unit,
    onPick: (Uri) -> Unit,
    onAll: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var cameraAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraAsked by remember { mutableStateOf(false) }
    var photosAllowed by remember { mutableStateOf(hasPhotoAccess(context)) }
    var photos by remember { mutableStateOf(emptyList<Uri>()) }
    var front by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraAllowed = it; cameraAsked = true }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        photosAllowed = hasPhotoAccess(context)
    }
    LaunchedEffect(Unit) {
        if (!cameraAllowed) askCamera.launch(Manifest.permission.CAMERA)
        if (!photosAllowed) askPhotos.launch(photoPermissions())
    }
    LaunchedEffect(photosAllowed) { if (photosAllowed) photos = withContext(Dispatchers.IO) { recentPhotos(context) } }

    DisposableEffect(cameraAllowed, front) {
        var provider: ProcessCameraProvider? = null
        if (cameraAllowed) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    provider = future.get().also { p ->
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        p.unbindAll()
                        p.bindToLifecycle(lifecycle, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, capture)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose { runCatching { provider?.unbindAll() } }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(0.8f).clip(wyrmRounded(22.dp)).background(Wyrm.Well)) {
            if (cameraAllowed) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(44.dp))
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(70.dp).clip(CircleShape).border(5.dp, Color.White, CircleShape).padding(9.dp)
                            .clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val shot = runCatching { image.upright(front) }.getOrNull()
                                        image.close()
                                        shot?.let(onCaptured)
                                    }
                                    override fun onError(exception: ImageCaptureException) {}
                                })
                            },
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { front = !front },
                        contentAlignment = Alignment.Center,
                    ) { Text("⟲", fontSize = 20.sp, color = Color.White) }
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Camera is off for Wyrm", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
                    Text(if (cameraAsked) "Allow it in Settings to take a photo here." else "Tap to allow the camera.",
                        fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Mute, textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { askCamera.launch(Manifest.permission.CAMERA) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        modeBar()
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (photosAllowed) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.padding(horizontal = 12.dp).clip(wyrmRounded(14.dp)),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item {
                        Box(Modifier.aspectRatio(1f).background(Wyrm.Well).clickable(onClick = onAll), contentAlignment = Alignment.Center) {
                            Text("All photos", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Wyrm.Mute)
                        }
                    }
                    items(photos, key = { it.toString() }) { uri ->
                        var thumb by remember(uri) { mutableStateOf(StudioThumbs.cache.get(ContentUris.parseId(uri))) }
                        LaunchedEffect(uri) { if (thumb == null) thumb = withContext(Dispatchers.IO) { thumbnail(context, uri) } }
                        Box(Modifier.aspectRatio(1f).background(Wyrm.Well).clickable { if (!loading) onPick(uri) }) {
                            thumb?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                }
            } else {
                Column(Modifier.align(Alignment.TopCenter).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your photos", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
                    Text("Allow Wyrm to show your photos here, or pick one from the library.", fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp, color = Wyrm.Mute, textAlign = TextAlign.Center)
                    PaperPrimaryButton(label = "Choose a photo", onClick = onAll)
                }
            }
            if (loading) {
                Box(Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Wyrm.Card), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Wyrm.Ink, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TextComposer(draft: StudioDraft, modeBar: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(top = 6.dp, bottom = 14.dp)) { modeBar() }
        Box(
            Modifier.weight(1f).padding(horizontal = 14.dp).fillMaxWidth().clip(wyrmRounded(22.dp)).background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(22.dp)).padding(18.dp),
        ) {
            if (draft.caption.isEmpty()) Text("Leave a thought…", fontFamily = Wyrm.Display, fontSize = 28.sp, color = Wyrm.Quiet)
            BasicTextField(
                value = draft.caption,
                onValueChange = { draft.caption = it.take(500) },
                textStyle = TextStyle(fontFamily = Wyrm.Display, fontSize = 28.sp, lineHeight = 36.sp, color = Wyrm.Ink),
                cursorBrush = SolidColor(Wyrm.Link),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text("${draft.caption.length}/500", fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

@Composable
private fun CaptionStep(draft: StudioDraft, rendered: Bitmap?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.padding(horizontal = 14.dp).padding(top = 8.dp).fillMaxWidth().clip(wyrmRounded(20.dp)).background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            rendered?.let {
                Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Fit,
                    modifier = Modifier.width(110.dp).clip(wyrmRounded(14.dp)))
            }
            Box(Modifier.weight(1f).height(160.dp)) {
                if (draft.caption.isEmpty()) Text("Write a caption…", fontFamily = Wyrm.Body, fontSize = 15.sp, color = Wyrm.Quiet)
                BasicTextField(
                    value = draft.caption,
                    onValueChange = { draft.caption = it.take(500) },
                    textStyle = TextStyle(fontFamily = Wyrm.Body, fontSize = 15.sp, lineHeight = 21.sp, color = Wyrm.Ink),
                    cursorBrush = SolidColor(Wyrm.Link),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text("${draft.caption.length}/500", fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
    }
}

// ----------------------------------------------------------------- editor

@Composable
private fun StudioEditor(
    draft: StudioDraft,
    type: StudioType,
    showModes: Boolean,
    modeBar: @Composable () -> Unit,
    onCanvas: (Float, Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var drawing by remember { mutableStateOf(false) }
    var brush by remember { mutableStateOf(StudioBrush.BEADS) }
    var inkRgb by remember { mutableStateOf(0xF2B84B) }
    var live by remember { mutableStateOf<StudioStroke?>(null) }
    var liveTick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<StudioText?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { (maxWidth - 24.dp).toPx() }
        val maxHeightPx = with(density) { (maxHeight - if (showModes) 230.dp else 180.dp).toPx() }.coerceAtLeast(200f)
        val heightPx = min(widthPx / draft.ratio, maxHeightPx)
        val w = heightPx * draft.ratio
        val h = heightPx
        LaunchedEffect(w, h) { onCanvas(w, h) }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showModes) modeBar()
            Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                Canvas(
                    Modifier.fillMaxSize().clip(wyrmRounded(18.dp)).border(1.dp, Wyrm.Rule, wyrmRounded(18.dp))
                        .pointerInput(drawing, brush, inkRgb) {
                            if (drawing) {
                                detectDragGestures(
                                    onDragStart = { p ->
                                        live = StudioStroke(mutableListOf(p), inkRgb, (if (brush == StudioBrush.BEADS) 7f else 5f) * density.density, brush)
                                        liveTick++
                                    },
                                    onDrag = { change, _ ->
                                        val p = Offset(change.position.x.coerceIn(0f, w), change.position.y.coerceIn(0f, h))
                                        live?.let { s -> if ((s.points.last() - p).getDistance() > 1.5f) { s.points += p; liveTick++ } }
                                    },
                                    onDragEnd = { live?.let { draft.strokes += it; draft.ink++ }; live = null },
                                    onDragCancel = { live = null },
                                )
                            } else {
                                // Pinch and pan: a text item under the first finger, otherwise the photo.
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val hit = draft.texts.indexOfLast { item ->
                                        val (tw, th) = StudioTextPainter.size(item, type)
                                        abs(down.position.x - item.center.x) < tw * item.scale / 2 + 12 &&
                                            abs(down.position.y - item.center.y) < th * item.scale / 2 + 12
                                    }
                                    var moved = 0f
                                    val start = System.currentTimeMillis()
                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        val turn = event.calculateRotation()
                                        moved += pan.getDistance()
                                        if (hit >= 0 && hit < draft.texts.size) {
                                            val item = draft.texts[hit]
                                            draft.texts[hit] = item.copy(
                                                center = Offset((item.center.x + pan.x).coerceIn(0f, w), (item.center.y + pan.y).coerceIn(0f, h)),
                                                scale = (item.scale * zoom).coerceIn(0.4f, 4f),
                                                rotation = item.rotation + turn,
                                            )
                                        } else if (draft.image != null) {
                                            draft.photoScale = (draft.photoScale * zoom).coerceIn(1f, 5f)
                                            draft.photoOffset += pan
                                            draft.clampOffset(w, h)
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } while (event.changes.any { it.pressed })
                                    if (hit >= 0 && moved < 12f && System.currentTimeMillis() - start < 300) {
                                        editing = draft.texts.getOrNull(hit)
                                    }
                                }
                            }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val redraw = liveTick + draft.ink + draft.texts.size
                    drawIntoCanvas { draft.paint(it.nativeCanvas, w, h, type, live) }
                }
                if (draft.aspect == StudioAspect.FREE && !drawing) FreeHandles(draft, w, h, widthPx, maxHeightPx)
            }
            StudioToolbar(
                draft = draft,
                drawing = drawing,
                brush = brush,
                inkRgb = inkRgb,
                onBrush = { brush = it },
                onInk = { inkRgb = it },
                onUndo = { if (draft.strokes.isNotEmpty()) { draft.strokes.removeAt(draft.strokes.lastIndex); draft.ink++ } },
                onText = {
                    drawing = false
                    val rgb = if (draft.image == null) StudioPalette.contrast(draft.background) else 0xFFFFFF
                    editing = StudioText(text = "", rgb = rgb, serif = false, filled = false, center = Offset(w / 2, h / 2))
                },
                onDraw = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); drawing = !drawing },
            )
        }
        editing?.let { current ->
            TextEditOverlay(
                item = current,
                onChange = { editing = it },
                onDelete = { draft.texts.removeAll { it.id == current.id }; editing = null },
                onDone = { done ->
                    val text = done.text.trim()
                    val index = draft.texts.indexOfFirst { it.id == done.id }
                    when {
                        index >= 0 && text.isEmpty() -> draft.texts.removeAt(index)
                        index >= 0 -> draft.texts[index] = done.copy(text = text)
                        text.isNotEmpty() -> draft.texts += done.copy(text = text)
                    }
                    editing = null
                },
            )
        }
    }
}

/** Free crop: drag an edge to reshape the canvas; the photo stays covering it. */
@Composable
private fun FreeHandles(draft: StudioDraft, w: Float, h: Float, maxW: Float, maxH: Float) {
    val density = LocalDensity.current
    for (edge in 0 until 4) {
        val horizontal = edge < 2
        val x = when (edge) { 0 -> 0f; 1 -> w; else -> w / 2 }
        val y = when (edge) { 2 -> 0f; 3 -> h; else -> h / 2 }
        var start by remember { mutableStateOf(0f to 0f) }
        var drag by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .offset { IntOffset((x - 22 * density.density).roundToInt(), (y - 22 * density.density).roundToInt()) }
                .size(44.dp)
                .pointerInput(edge) {
                    detectDragGestures(
                        onDragStart = { start = w to h; drag = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            drag += amount
                            var nw = start.first
                            var nh = start.second
                            when (edge) {
                                0 -> nw = start.first - drag.x * 2
                                1 -> nw = start.first + drag.x * 2
                                2 -> nh = start.second - drag.y * 2
                                else -> nh = start.second + drag.y * 2
                            }
                            nw = nw.coerceIn(120f, maxW)
                            nh = nh.coerceIn(120f, maxH)
                            draft.freeRatio = (nw / nh).coerceIn(0.5f, 2f)
                            draft.clampOffset(nh * draft.freeRatio, nh)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(if (horizontal) 6.dp else 42.dp, if (horizontal) 42.dp else 6.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun StudioToolbar(
    draft: StudioDraft,
    drawing: Boolean,
    brush: StudioBrush,
    inkRgb: Int,
    onBrush: (StudioBrush) -> Unit,
    onInk: (Int) -> Unit,
    onUndo: () -> Unit,
    onText: () -> Unit,
    onDraw: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            drawing -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    StudioBrush.entries.forEach { kind -> StudioChip(kind.label, brush == kind) { onBrush(kind) } }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Wyrm.Well).clickable(onClick = onUndo), contentAlignment = Alignment.Center) {
                        Text("↶", fontSize = 17.sp, color = Wyrm.Ink)
                    }
                }
                StudioSwatches(inkRgb, onInk)
            }
            draft.image != null -> {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioAspect.entries.forEach { aspect ->
                        StudioChip(aspect.label, draft.aspect == aspect) {
                            if (aspect == StudioAspect.FREE) draft.freeRatio = draft.ratio
                            draft.aspect = aspect
                            draft.photoScale = 1f
                            draft.photoOffset = Offset.Zero
                        }
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(StudioAspect.PORTRAIT, StudioAspect.SQUARE, StudioAspect.WIDE).forEach { aspect ->
                        StudioChip(aspect.label, draft.aspect == aspect) { draft.aspect = aspect }
                    }
                }
                StudioSwatches(draft.background) { draft.background = it }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioToolButton("Aa  Text", false, Modifier.weight(1f), onText)
            StudioToolButton("✎  Draw", drawing, Modifier.weight(1f), onDraw)
        }
    }
}

@Composable
private fun StudioChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) Wyrm.Ink else Wyrm.Well)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp).height(32.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (selected) Wyrm.OnInk else Wyrm.Ink) }
}

@Composable
private fun StudioToolButton(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).clip(wyrmRounded(13.dp)).background(if (on) Wyrm.Ink else Wyrm.Well)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = if (on) Wyrm.OnInk else Wyrm.Ink) }
}

@Composable
private fun StudioSwatches(selected: Int, onPick: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        StudioPalette.colours.forEach { rgb ->
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .border(if (selected == rgb) 2.5.dp else 0.dp, Wyrm.Ink, CircleShape)
                    .padding(4.dp).clip(CircleShape).background(StudioPalette.color(rgb)).border(1.dp, Wyrm.Rule, CircleShape)
                    .clickable { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPick(rgb) },
            )
        }
    }
}

@Composable
private fun TextEditOverlay(item: StudioText, onChange: (StudioText) -> Unit, onDelete: () -> Unit, onDone: (StudioText) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone(item) },
    ) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.clickable(onClick = onDelete))
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onChange(item.copy(serif = !item.serif)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Aa", fontFamily = if (item.serif) Wyrm.Display else Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
                Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = if (item.filled) 0.45f else 0.18f))
                    .clickable { onChange(item.copy(filled = !item.filled)) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("▣", fontSize = 16.sp, color = Color.White)
                }
                Text("Done", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White,
                    modifier = Modifier.clickable { onDone(item) })
            }
            BasicTextField(
                value = item.text,
                onValueChange = { onChange(item.copy(text = it.take(120))) },
                textStyle = TextStyle(fontFamily = if (item.serif) Wyrm.Display else Wyrm.Body, fontWeight = FontWeight.Bold,
                    fontSize = 30.sp, color = StudioPalette.color(item.rgb), textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).focusRequester(focus)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            )
            StudioSwatches(item.rgb) { onChange(item.copy(rgb = it)) }
        }
    }
}
