@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wyrm.omrajput.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.composables.icons.lucide.R as LucideR
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The studio's first page (OM, 2026-10-17, Instagram's camera): the camera
 * fills the screen; under it the modes PHOTO · VIDEO · TEXT · CANVAS slide
 * like Instagram's, and a swipe across the camera changes mode too.
 *
 *   - The shutter is a Wyrm bead. On Photo a tap takes a picture and a hold
 *     records (the ring fills green over 30 s); on Video a tap starts and
 *     stops. Video only while `TRAIL_VIDEO_ENABLED`.
 *   - Bottom left: the newest picture of the phone; it opens the gallery
 *     (`TrailGalleryPage`): a big preview of the chosen item (a video plays)
 *     over a grid of photos and videos, Recents / Photos / Videos, Next.
 *   - Bottom right: front or back camera. Top: close and the flash.
 *
 * Wyrm iOS: `WyrmTrailCamera.swift`, the same page.
 */

// ------------------------------------------------------------------ media

/** One photo or video of the phone, for the gallery. */
internal data class TrailMedia(val uri: Uri, val video: Boolean, val durationMs: Long, val added: Long)

internal enum class TrailMediaFilter(val label: String) { RECENTS("Recents"), PHOTOS("Photos"), VIDEOS("Videos") }

internal object TrailMediaStore {
    /** Newest first: photos, and videos when [videos], at most [limit]. */
    fun recent(context: Context, videos: Boolean, limit: Int = 400): List<TrailMedia> = runCatching {
        val out = mutableListOf<TrailMedia>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext() && out.size < limit) {
                out += TrailMedia(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(id)), false, 0L, c.getLong(added))
            }
        }
        if (videos) {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DURATION), null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val added = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val length = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                var n = 0
                while (c.moveToNext() && n < limit) {
                    out += TrailMedia(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(id)), true, c.getLong(length), c.getLong(added))
                    n++
                }
            }
        }
        out.sortedByDescending { it.added }.take(limit)
    }.getOrDefault(emptyList())

    fun thumbnail(context: Context, item: TrailMedia): Bitmap? =
        if (item.video) TrailClips.thumbnail(context, item.uri) else studioThumbnail(context, item.uri)

    fun hasAccess(context: Context, videos: Boolean): Boolean = hasPhotoAccess(context) && (!videos || TrailClips.hasAccess(context))

    fun permissions(videos: Boolean): Array<String> =
        (photoPermissions().toList() + if (videos) TrailClips.permissions().toList() else emptyList()).distinct().toTypedArray()
}

// ----------------------------------------------------------------- camera

private fun ImageProxy.uprightShot(front: Boolean): Bitmap {
    val raw = toBitmap()
    val matrix = android.graphics.Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
        if (front) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

/** The camera page; [mode] is PHOTO or VIDEO. */
@SuppressLint("MissingPermission")
@Composable
internal fun TrailCameraPage(
    mode: StudioMode,
    busy: Boolean,
    onClose: () -> Unit,
    onSwipe: (Int) -> Unit,
    onPhoto: (Bitmap) -> Unit,
    onClip: (Uri) -> Unit,
    onGallery: () -> Unit,
    onCameraApp: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val videoOn = com.wyrm.omrajput.data.TRAIL_VIDEO_ENABLED
    var cameraAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var mediaAllowed by remember { mutableStateOf(TrailMediaStore.hasAccess(context, videoOn)) }
    var newest by remember { mutableStateOf<Bitmap?>(null) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    /** Photo and video at once on this phone; false: one at a time, by mode. */
    var together by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val recorder = remember {
        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    val askAll = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraAllowed = result[Manifest.permission.CAMERA] ?: cameraAllowed
        mediaAllowed = TrailMediaStore.hasAccess(context, videoOn)
        asked = true
    }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (!cameraAllowed) add(Manifest.permission.CAMERA)
            if (videoOn && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (!mediaAllowed) addAll(TrailMediaStore.permissions(videoOn))
        }
        if (wanted.isNotEmpty()) askAll.launch(wanted.toTypedArray()) else asked = true
    }
    LaunchedEffect(mediaAllowed) {
        if (mediaAllowed) newest = withContext(Dispatchers.IO) {
            TrailMediaStore.recent(context, videoOn, limit = 1).firstOrNull()?.let { TrailMediaStore.thumbnail(context, it) }
        }
    }
    LaunchedEffect(flash) { capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }

    // Photo and video bound together when the phone can; otherwise the mode's own.
    val binding = if (together) "both" else mode.name
    DisposableEffect(cameraAllowed, front, binding) {
        var provider: ProcessCameraProvider? = null
        var bound = emptyArray<androidx.camera.core.UseCase>()
        if (cameraAllowed) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    p.unbindAll()
                    val cases: Array<androidx.camera.core.UseCase> = when {
                        !videoOn -> arrayOf(preview, capture)
                        together -> arrayOf(preview, capture, videoCapture)
                        mode == StudioMode.VIDEO -> arrayOf(preview, videoCapture)
                        else -> arrayOf(preview, capture)
                    }
                    try {
                        camera = p.bindToLifecycle(lifecycle, selector, *cases)
                        bound = cases
                    } catch (_: Exception) {
                        // Three at once is too many for this phone: one mode at a time.
                        if (together) together = false
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            runCatching { recording?.stop() }
            runCatching { provider?.unbind(*bound) }
        }
    }

    fun takePhoto() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val shot = runCatching { image.uprightShot(front) }.getOrNull()
                image.close()
                shot?.let(onPhoto)
            }
            override fun onError(exception: ImageCaptureException) {}
        })
    }

    fun startRecording() {
        if (recording != null || !videoOn) return
        val folder = File(context.cacheDir, "trails-video").apply { mkdirs() }
        folder.listFiles()?.filter { it.name.startsWith("rec-") }?.forEach { it.delete() }
        val file = File(folder, "rec-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).setDurationLimitMillis(TRAIL_VIDEO_MAX_MS).build()
        var pending = recorder.prepareRecording(context, options)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (flash) camera?.cameraControl?.enableTorch(true)
        recordedMs = 0L
        recording = runCatching {
            pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Status -> recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        camera?.cameraControl?.enableTorch(false)
                        val ok = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                        if (ok && file.length() > 0L) onClip(Uri.fromFile(file))
                    }
                    else -> Unit
                }
            }
        }.getOrNull()
    }

    fun stopRecording() { runCatching { recording?.stop() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize().clip(wyrmRounded(24.dp)).background(Color(0xFF111111))
                .pointerInput(Unit) {
                    // A swipe across the camera: the next or the last mode.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dx = 0f
                        var dy = 0f
                        do {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull() ?: break
                            dx += c.position.x - c.previousPosition.x
                            dy += c.position.y - c.previousPosition.y
                        } while (event.changes.any { it.pressed })
                        if (recording == null && abs(dx) > 70 * density.density && abs(dy) < abs(dx) * 0.7f) onSwipe(if (dx < 0) 1 else -1)
                        down.consume()
                    }
                },
        ) {
            if (cameraAllowed) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
            } else {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(LucideR.drawable.lucide_ic_aperture), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(30.dp))
                    Text("Camera is off for Wyrm", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Text(if (asked) "Allow it in Settings, or pick from your gallery." else "Allow the camera to take a photo or a video.",
                        fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    TrailGlassPill("Open the phone's camera", onClick = onCameraApp)
                }
            }
            // Top: close, the time while recording, the flash.
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TrailGlassButton(LucideR.drawable.lucide_ic_x, onClick = { if (recording == null) onClose() })
                Spacer(Modifier.weight(1f))
                if (recording != null) {
                    Row(
                        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                        Text("${clipTime(recordedMs)} / 0:30", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                }
                if (cameraAllowed) TrailGlassButton(if (flash) LucideR.drawable.lucide_ic_zap else LucideR.drawable.lucide_ic_zap_off, on = flash) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    flash = !flash
                } else Spacer(Modifier.size(42.dp))
            }
            // Bottom: the gallery, the bead, the flip.
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 26.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(wyrmRounded(12.dp)).border(2.dp, Color.White, wyrmRounded(12.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(enabled = recording == null && !busy) { onGallery() },
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = newest
                    if (shown != null) Image(remember(shown) { shown.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(painterResource(LucideR.drawable.lucide_ic_images), null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                TrailShutter(
                    recording = recording != null,
                    progress = (recordedMs.toFloat() / TRAIL_VIDEO_MAX_MS).coerceIn(0f, 1f),
                    video = mode == StudioMode.VIDEO,
                    enabled = cameraAllowed && !busy,
                    onTap = {
                        when {
                            mode == StudioMode.VIDEO -> if (recording == null) startRecording() else stopRecording()
                            recording != null -> stopRecording()
                            else -> takePhoto()
                        }
                    },
                    onHoldStart = { if (videoOn && (together || mode == StudioMode.VIDEO) && recording == null) startRecording() },
                    onHoldEnd = { stopRecording() },
                )
                Spacer(Modifier.weight(1f))
                TrailGlassButton(LucideR.drawable.lucide_ic_switch_camera, size = 46) {
                    if (recording == null) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); front = !front }
                }
            }
            if (busy) {
                Box(Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * The shutter, a Wyrm bead: a white glossy bead in a white ring. Recording,
 * the ring fills in Wyrm's own green and the bead shrinks to a rounded square.
 */
@Composable
private fun TrailShutter(
    recording: Boolean,
    progress: Float,
    video: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val live = Wyrm.Live
    // Read inside the gesture, which must not restart when recording begins under the finger.
    val now by androidx.compose.runtime.rememberUpdatedState(Triple(enabled, video, recording))
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val holdStart by androidx.compose.runtime.rememberUpdatedState(onHoldStart)
    val holdEnd by androidx.compose.runtime.rememberUpdatedState(onHoldEnd)
    val inner by animateDpAsState(if (recording) 30.dp else 60.dp, spring(0.7f, 500f), label = "bead")
    val press by animateFloatAsState(if (recording) 1.12f else 1f, spring(0.6f, 400f), label = "ring")
    Box(
        Modifier.size(84.dp).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val (on, isVideo, isRecording) = now
                if (!on) return@awaitEachGesture
                if (isVideo || isRecording) {
                    if (waitForUpOrCancellation() != null) tap()
                    return@awaitEachGesture
                }
                // A tap takes a photo; held past a moment, it records until let go.
                var lifted = false
                val up = withTimeoutOrNull(280L) { waitForUpOrCancellation().also { lifted = true } }
                if (lifted) { if (up != null) tap() } else {
                    holdStart()
                    waitForUpOrCancellation()
                    holdEnd()
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val r = (size.minDimension / 2 - stroke) * press
            drawCircle(Color.White.copy(alpha = if (recording) 0.35f else 1f), r, style = Stroke(stroke))
            if (recording) {
                drawArc(live, -90f, 360f * progress, false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2))
            }
        }
        Box(
            Modifier.size(inner).clip(if (recording) wyrmRounded(9.dp) else CircleShape)
                .background(
                    if (video && !recording) Brush.radialGradient(listOf(Color(0xFFFF8A8A), Color(0xFFE5484D)), Offset(18f, 14f), 90f)
                    else Brush.radialGradient(listOf(Color.White, Color(0xFFE9E9E9), Color(0xFFC9C9C9)), Offset(20f, 16f), 110f),
                ),
        )
    }
}

/** PHOTO · VIDEO · TEXT · CANVAS under the camera; the chosen one sits in the middle. */
@Composable
internal fun StudioModeCarousel(modes: List<StudioMode>, mode: StudioMode, dark: Boolean, enabled: Boolean = true, onMode: (StudioMode) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val slot = 86.dp
    val index = modes.indexOf(mode).coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp)) {
        val shift by animateDpAsState(maxWidth / 2 - slot * index - slot / 2, spring(0.82f, 420f), label = "modes")
        Row(Modifier.offset(x = shift).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            modes.forEach { m ->
                val on = m == mode
                Box(
                    Modifier.width(slot).height(40.dp)
                        .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (m != mode) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onMode(m) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val ink = if (dark) Color.White else Wyrm.Ink
                    Text(m.label.uppercase(), fontFamily = Wyrm.Body, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 13.sp, letterSpacing = 1.2.sp, color = ink.copy(alpha = if (on) 1f else 0.45f))
                }
            }
        }
        // A small bead under the chosen mode.
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape)
            .background(if (dark) Color.White else Wyrm.Ink))
    }
}

@Composable
internal fun TrailGlassButton(icon: Int, on: Boolean = false, size: Int = 42, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (on) Color.White else Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size((size * 0.45f).dp)) }
}

@Composable
internal fun TrailGlassPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp).height(36.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White) }
}

// ---------------------------------------------------------------- gallery

/**
 * The gallery, Instagram's New post: the chosen item big on top (a video
 * plays, muted), the phone's photos and videos below, Recents / Photos /
 * Videos, and Next. Browse opens the phone's own picker.
 */
@Composable
internal fun TrailGalleryPage(onClose: () -> Unit, onPhoto: (Uri) -> Unit, onVideo: (Uri) -> Unit, busy: Boolean) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val videoOn = com.wyrm.omrajput.data.TRAIL_VIDEO_ENABLED
    var allowed by remember { mutableStateOf(TrailMediaStore.hasAccess(context, videoOn)) }
    var filter by remember { mutableStateOf(TrailMediaFilter.RECENTS) }
    var all by remember { mutableStateOf(emptyList<TrailMedia>()) }
    var chosen by remember { mutableStateOf<TrailMedia?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = TrailMediaStore.hasAccess(context, videoOn) }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val type = context.contentResolver.getType(uri).orEmpty()
        if (type.startsWith("video")) onVideo(uri) else onPhoto(uri)
    }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(TrailMediaStore.permissions(videoOn)) }
    LaunchedEffect(allowed) {
        if (allowed) {
            all = withContext(Dispatchers.IO) { TrailMediaStore.recent(context, videoOn) }
            if (chosen == null) chosen = all.firstOrNull()
        }
        loaded = true
    }
    val shown = when (filter) {
        TrailMediaFilter.RECENTS -> all
        TrailMediaFilter.PHOTOS -> all.filter { !it.video }
        TrailMediaFilter.VIDEOS -> all.filter { it.video }
    }
    fun next() {
        val item = chosen ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (item.video) onVideo(item.uri) else onPhoto(item.uri)
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrailGlassButton(LucideR.drawable.lucide_ic_x, onClick = onClose)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    val options = if (videoOn) TrailMediaFilter.entries else listOf(TrailMediaFilter.RECENTS, TrailMediaFilter.PHOTOS)
                    filter = options[(options.indexOf(filter) + 1) % options.size]
                }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(filter.label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Icon(painterResource(LucideR.drawable.lucide_ic_chevron_down), null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = if (chosen != null && !busy) 1f else 0.35f))
                    .clickable(enabled = chosen != null && !busy) { next() }.padding(horizontal = 16.dp).height(36.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Next", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black) }
        }
        // The chosen item, big.
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF0E0E0E)), contentAlignment = Alignment.Center) {
            chosen?.let { item -> if (item.video) TrailGalleryVideo(item.uri) else TrailGalleryPhoto(item.uri) }
            if (!allowed && loaded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp)) {
                    Text("Your gallery", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Text("Allow Wyrm to show your photos and videos here, or browse the phone.", fontFamily = Wyrm.Body,
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    TrailGlassPill("Allow access") { ask.launch(TrailMediaStore.permissions(videoOn)) }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                TrailGlassButton(LucideR.drawable.lucide_ic_layers) {
                    browse.launch(PickVisualMediaRequest(if (videoOn) ActivityResultContracts.PickVisualMedia.ImageAndVideo else ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(shown, key = { it.uri.toString() }) { item ->
                var thumb by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(item.uri) { thumb = withContext(Dispatchers.IO) { TrailMediaStore.thumbnail(context, item) } }
                val on = chosen?.uri == item.uri
                Box(
                    Modifier.aspectRatio(1f).background(Color(0xFF1A1A1A)).clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (on) next() else scope.launch { chosen = item }
                    },
                ) {
                    thumb?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    if (item.video) {
                        Text(clipTime(item.durationMs), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    if (on) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.28f)).border(3.dp, Color.White))
                }
            }
        }
    }
}

@Composable
private fun TrailGalleryPhoto(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val longest = max(info.size.width, info.size.height)
                        if (longest > 1200) {
                            val s = 1200f / longest
                            decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                        }
                    }
                } else studioThumbnail(context, uri)
            }.getOrNull()
        }
    }
    bitmap?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
}

/** The chosen video, playing muted in its own shape. */
@Composable
private fun TrailGalleryVideo(uri: Uri) {
    val context = LocalContext.current
    var aspect by remember(uri) { mutableStateOf<Float?>(null) }
    LaunchedEffect(uri) { aspect = withContext(Dispatchers.IO) { TrailClips.probe(context, uri)?.aspect } }
    val player = remember(uri) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val a = aspect ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            onRelease = { player.clearVideoTextureView(it) },
            modifier = Modifier.aspectRatio(a),
        )
    }
}
