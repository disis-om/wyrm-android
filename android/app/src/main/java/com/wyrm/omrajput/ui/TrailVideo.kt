@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wyrm.omrajput.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/*
 * Trails videos (OM, 2026-10-05): "a video editor like Instagram's", clips of
 * at most 30 seconds, compressed in the app to 360p-720p.
 *
 * The studio's Video page records (CameraX, at most 30 s) or takes a clip from
 * the phone. The editor is the photo editor itself over a playing clip: the
 * same text, drawing, stickers and looks (`TrailLooks`), plus a trim window,
 * a cover frame and sound on or off. Export is Media3 Transformer: the trim,
 * the size (short side 720, 540 or 360, the clip's own if smaller), the look as
 * an RgbMatrix and the overlay as one bitmap the size of the output frame,
 * H.264 + AAC, HDR tone-mapped to SDR. The player previews with the same
 * effects, so the editor shows what goes up. The server takes it as it is
 * (`backend/src/trail-video.mjs`). Wyrm iOS: `WyrmTrailVideo.swift`.
 *
 * The feed plays one clip at a time, the one most in view, muted until the
 * player taps for sound, from a disk cache (`TrailFeedPlayer`).
 */

internal const val TRAIL_VIDEO_MAX_MS = 30_000L
private const val TRAIL_VIDEO_MIN_MS = 1_000L

/** A clip on the phone: its length and its upright (display) size. */
internal data class TrailClip(val uri: Uri, val durationMs: Long, val width: Int, val height: Int, val rotation: Int) {
    val aspect: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 9f / 16f
}

internal object TrailClips {
    fun probe(context: Context, uri: Uri): TrailClip? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (duration <= 0L || w <= 0 || h <= 0) null
            else {
                val turned = rotation == 90 || rotation == 270
                TrailClip(uri, duration, if (turned) h else w, if (turned) w else h, rotation)
            }
        } finally {
            r.release()
        }
    }.getOrNull()

    /** One upright frame at [atMs], at most [longest] px on its long side. */
    fun frame(context: Context, clip: TrailClip, atMs: Long, longest: Int): Bitmap? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, clip.uri)
            val raw = r.getFrameAtTime(atMs.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: return@runCatching null
            // Some phones hand the frame back as stored, not as shown: turn it upright.
            val upright = if (clip.rotation != 0 && (raw.width > raw.height) != (clip.width > clip.height)) {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(clip.rotation.toFloat()) }, true)
            } else raw
            val side = max(upright.width, upright.height)
            if (side <= longest) upright
            else Bitmap.createScaledBitmap(upright, (upright.width * longest / side).coerceAtLeast(1),
                (upright.height * longest / side).coerceAtLeast(1), true)
        } finally {
            r.release()
        }
    }.getOrNull()

    /** [count] small frames spread over the whole clip, for the trim and cover strips. */
    fun strip(context: Context, clip: TrailClip, count: Int, longest: Int): List<Bitmap> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, clip.uri)
            (0 until count).mapNotNull { i ->
                val at = clip.durationMs * (i + 0.5) / count
                runCatching {
                    val raw = r.getFrameAtTime((at * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
                    val upright = if (clip.rotation != 0 && (raw.width > raw.height) != (clip.width > clip.height)) {
                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(clip.rotation.toFloat()) }, true)
                    } else raw
                    val side = max(upright.width, upright.height)
                    Bitmap.createScaledBitmap(upright, (upright.width * longest / side).coerceAtLeast(1),
                        (upright.height * longest / side).coerceAtLeast(1), true)
                }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { r.release() }
        }
    }

    /** The phone's newest clips, for the Video page's grid. */
    fun recent(context: Context): List<Pair<Uri, Long>> = runCatching {
        val out = mutableListOf<Pair<Uri, Long>>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DURATION), null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val length = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext() && out.size < 200) {
                out += ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(id)) to cursor.getLong(length)
            }
        }
        out
    }.getOrDefault(emptyList())

    fun thumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
        else probe(context, uri)?.let { frame(context, it, 0L, 256) }
    }.getOrNull()

    fun hasAccess(context: Context): Boolean {
        val granted = { p: String -> ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }
        return when {
            Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_VIDEO) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_VIDEO)
            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

internal fun clipTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- editor

/**
 * The clip being edited: the player, the trim window (at most 30 s), the
 * cover frame and the sound. The look and the overlays live on the studio's
 * draft, shared with the photo editor.
 */
internal class TrailVideoSession(context: Context, val clip: TrailClip) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        // Effects must be set once before prepare; the looks replace them later.
        setVideoEffects(emptyList())
    }
    var trimStart by mutableLongStateOf(0L)
    var trimEnd by mutableLongStateOf(min(clip.durationMs, TRAIL_VIDEO_MAX_MS))
    /** Absolute time of the cover frame in the clip. */
    var coverMs by mutableLongStateOf(0L)
    var muted by mutableStateOf(false)
    /** Where the player is, relative to [trimStart]. */
    var playhead by mutableLongStateOf(0L)
    private var effects: List<Effect> = emptyList()

    val lengthMs: Long get() = trimEnd - trimStart

    fun load() {
        player.setMediaItem(
            MediaItem.Builder().setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(trimStart)
                        .setEndPositionMs(trimEnd)
                        .build(),
                )
                .build(),
        )
        player.prepare()
        player.play()
    }

    fun setTrim(start: Long, end: Long) {
        val s = start.coerceIn(0L, (clip.durationMs - TRAIL_VIDEO_MIN_MS).coerceAtLeast(0L))
        val e = end.coerceIn(s + min(TRAIL_VIDEO_MIN_MS, clip.durationMs - s), min(clip.durationMs, s + TRAIL_VIDEO_MAX_MS))
        trimStart = s
        trimEnd = e
        coverMs = coverMs.coerceIn(s, e)
    }

    fun applyEffects(next: List<Effect>) {
        if (next == effects) return
        effects = next
        player.setVideoEffects(next)
    }

    fun toggleSound() {
        muted = !muted
        player.volume = if (muted) 0f else 1f
    }

    fun release() = player.release()
}

/** The playing clip, under the studio's canvas. */
@Composable
internal fun TrailVideoPreview(session: TrailVideoSession, modifier: Modifier = Modifier) {
    LaunchedEffect(session) {
        while (isActive) {
            session.playhead = session.player.currentPosition.coerceAtLeast(0L)
            delay(80)
        }
    }
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { session.player.setVideoTextureView(it) } },
        onRelease = { session.player.clearVideoTextureView(it) },
        modifier = modifier,
    )
}

/** The editor's video tools under the canvas: Trim, Cover and Sound, Instagram-style. */
@Composable
internal fun TrailVideoBar(session: TrailVideoSession) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var tab by remember { mutableStateOf(0) }
    var frames by remember(session.clip) { mutableStateOf(emptyList<Bitmap>()) }
    LaunchedEffect(session.clip) { frames = withContext(Dispatchers.IO) { TrailClips.strip(context, session.clip, 10, 160) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Trim", "Cover").forEachIndexed { index, label ->
                TrailVideoChip(label, tab == index) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); tab = index }
            }
            Spacer(Modifier.weight(1f))
            TrailVideoChip(if (session.muted) "Sound off" else "Sound on", !session.muted) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                session.toggleSound()
            }
        }
        if (tab == 0) {
            TrailTrimStrip(session, frames)
            Text(
                "${"%.1f".format(session.lengthMs / 1000f)} s of ${TRAIL_VIDEO_MAX_MS / 1000} s",
                fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Wyrm.Quiet,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TrailCoverStrip(session, frames)
            Text("The picture people see before it plays", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TrailVideoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) Wyrm.Ink else Wyrm.Well)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp).height(32.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (selected) Wyrm.OnInk else Wyrm.Ink) }
}

@Composable
private fun TrailFrameRow(frames: List<Bitmap>) {
    Row(Modifier.fillMaxSize()) {
        if (frames.isEmpty()) Box(Modifier.fillMaxSize().background(Wyrm.Well))
        frames.forEach { bitmap ->
            Image(remember(bitmap) { bitmap.asImageBitmap() }, null, contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * The trim window over the whole clip: drag either handle, or the window
 * itself; it never runs past 30 s nor under 1 s. The player restarts on the
 * new window when the finger lifts.
 */
@Composable
private fun TrailTrimStrip(session: TrailVideoSession, frames: List<Bitmap>) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val clip = session.clip
    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp).clip(wyrmRounded(10.dp))) {
        val widthPx = with(density) { maxWidth.toPx() }
        val handle = with(density) { 14.dp.toPx() }
        fun xOf(ms: Long) = (ms.toFloat() / clip.durationMs.coerceAtLeast(1L)) * widthPx
        fun msOf(x: Float) = ((x / widthPx).coerceIn(0f, 1f) * clip.durationMs).toLong()
        TrailFrameRow(frames)
        val left = xOf(session.trimStart)
        val right = xOf(session.trimEnd)
        Canvas(
            Modifier.fillMaxSize().pointerInput(clip) {
                var grab = 0 // 1 left, 2 right, 3 window
                var startAt = 0L
                var endAt = 0L
                var downX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { p ->
                        val l = xOf(session.trimStart)
                        val r = xOf(session.trimEnd)
                        grab = when {
                            kotlin.math.abs(p.x - l) < handle * 1.6f -> 1
                            kotlin.math.abs(p.x - r) < handle * 1.6f -> 2
                            p.x in l..r -> 3
                            else -> 0
                        }
                        startAt = session.trimStart
                        endAt = session.trimEnd
                        downX = p.x
                        if (grab != 0) {
                            session.player.pause()
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        val moved = msOf(change.position.x) - msOf(downX)
                        when (grab) {
                            1 -> session.setTrim(startAt + moved, endAt)
                            2 -> session.setTrim(startAt, endAt + moved)
                            3 -> {
                                val span = endAt - startAt
                                val s = (startAt + moved).coerceIn(0L, clip.durationMs - span)
                                session.setTrim(s, s + span)
                            }
                        }
                        if (grab != 0) {
                            change.consume()
                            session.player.seekTo(if (grab == 2) session.lengthMs else 0L)
                        }
                    },
                    onDragEnd = { if (grab != 0) session.load(); grab = 0 },
                    onDragCancel = { if (grab != 0) session.load(); grab = 0 },
                )
            },
        ) {
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset.Zero, Size(left, size.height))
            drawRect(dim, Offset(right, 0f), Size(size.width - right, size.height))
            val gold = Color(0xFFF2B84B)
            drawRect(gold, Offset(left, 0f), Size(right - left, size.height), style = Stroke(3.dp.toPx()))
            drawRect(gold, Offset(left, 0f), Size(handle, size.height))
            drawRect(gold, Offset(right - handle, 0f), Size(handle, size.height))
            val play = left + (session.playhead.toFloat() / session.lengthMs.coerceAtLeast(1L)) * (right - left)
            drawLine(Color.White, Offset(play, 0f), Offset(play, size.height), 2.dp.toPx())
        }
    }
}

/** The cover: a frame inside the trim window, chosen by dragging along the strip. */
@Composable
private fun TrailCoverStrip(session: TrailVideoSession, frames: List<Bitmap>) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val clip = session.clip
    var cover by remember(clip) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(session.coverMs) {
        delay(120)
        cover = withContext(Dispatchers.IO) { TrailClips.frame(context, clip, session.coverMs, 220) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        fun xOf(ms: Long) = (ms.toFloat() / clip.durationMs.coerceAtLeast(1L)) * widthPx
        Box(Modifier.fillMaxWidth().height(44.dp).align(Alignment.Center).clip(wyrmRounded(8.dp))) { TrailFrameRow(frames) }
        val x = xOf(session.coverMs)
        Box(
            Modifier
                .offset { IntOffset((x - with(density) { 22.dp.toPx() }).roundToInt(), 0) }
                .size(44.dp, 64.dp).clip(wyrmRounded(8.dp)).border(3.dp, Color.White, wyrmRounded(8.dp)).background(Wyrm.Well),
        ) {
            cover?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(clip) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val ms = ((change.position.x / widthPx).coerceIn(0f, 1f) * clip.durationMs).toLong()
                    session.coverMs = ms.coerceIn(session.trimStart, session.trimEnd)
                    scope.launch { session.player.seekTo((session.coverMs - session.trimStart).coerceAtLeast(0L)) }
                }
            },
        )
    }
}

// ----------------------------------------------------------------- picker

/**
 * The Video page: a camera that records up to 30 s (hold or tap the button;
 * the ring fills as the time runs), and the phone's recent clips below.
 */
@SuppressLint("MissingPermission")
@Composable
internal fun TrailVideoPicker(onClip: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var cameraAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var micAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var clipsAllowed by remember { mutableStateOf(TrailClips.hasAccess(context)) }
    var clips by remember { mutableStateOf(emptyList<Pair<Uri, Long>>()) }
    var front by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
    }
    val capture = remember { VideoCapture.withOutput(recorder) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    val askAll = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraAllowed = result[Manifest.permission.CAMERA] ?: cameraAllowed
        micAllowed = result[Manifest.permission.RECORD_AUDIO] ?: micAllowed
        clipsAllowed = TrailClips.hasAccess(context)
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onClip) }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (!cameraAllowed) add(Manifest.permission.CAMERA)
            if (!micAllowed) add(Manifest.permission.RECORD_AUDIO)
            if (!clipsAllowed) addAll(TrailClips.permissions())
        }
        if (wanted.isNotEmpty()) askAll.launch(wanted.toTypedArray())
    }
    LaunchedEffect(clipsAllowed) { if (clipsAllowed) clips = withContext(Dispatchers.IO) { TrailClips.recent(context) } }

    DisposableEffect(cameraAllowed, front) {
        var provider: ProcessCameraProvider? = null
        // Only this page's own use cases are let go: the Photo and Video pages
        // overlap while one slides over the other (OM, 2026-10-05).
        var bound = emptyArray<androidx.camera.core.UseCase>()
        if (cameraAllowed) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    provider = future.get().also { p ->
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        p.unbindAll()
                        p.bindToLifecycle(lifecycle, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, capture)
                        bound = arrayOf(preview, capture)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            runCatching { recording?.stop() }
            runCatching { provider?.unbind(*bound) }
        }
    }

    fun startRecording() {
        if (recording != null) return
        val folder = File(context.cacheDir, "trails-video").apply { mkdirs() }
        folder.listFiles()?.filter { it.name.startsWith("rec-") }?.forEach { it.delete() }
        val file = File(folder, "rec-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).setDurationLimitMillis(TRAIL_VIDEO_MAX_MS).build()
        var pending = recorder.prepareRecording(context, options)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        recordedMs = 0L
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Status -> recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val ok = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                    if (ok && file.length() > 0L) onClip(Uri.fromFile(file))
                }
                else -> Unit
            }
        }
    }

    fun stopRecording() {
        runCatching { recording?.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(0.8f).clip(wyrmRounded(22.dp)).background(Wyrm.Well)) {
            if (cameraAllowed) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                if (recording != null) {
                    Text(clipTime(recordedMs), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clip(CircleShape)
                            .background(Color(0xCCE5484D)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(44.dp))
                    Spacer(Modifier.weight(1f))
                    val progress = (recordedMs.toFloat() / TRAIL_VIDEO_MAX_MS).coerceIn(0f, 1f)
                    Box(
                        Modifier.size(78.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (recording == null) startRecording() else stopRecording()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color.White, radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(5.dp.toPx()))
                            if (recording != null) {
                                drawArc(Color(0xFFE5484D), -90f, 360f * progress, false, style = Stroke(5.dp.toPx()),
                                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                                    size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()))
                            }
                        }
                        Box(
                            Modifier.size(if (recording != null) 28.dp else 56.dp)
                                .clip(if (recording != null) wyrmRounded(7.dp) else CircleShape)
                                .background(Color(0xFFE5484D)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f))
                            .clickable(enabled = recording == null) { front = !front },
                        contentAlignment = Alignment.Center,
                    ) { Text("⟲", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camera is off for Wyrm", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
                    Text("Allow the camera to record a clip, or pick one below.", fontFamily = Wyrm.Body, fontSize = 12.5.sp,
                        color = Wyrm.Mute, textAlign = TextAlign.Center)
                }
            }
        }
        Text("Up to 30 seconds · trim longer clips in the editor", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (clipsAllowed) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.padding(horizontal = 12.dp).clip(wyrmRounded(14.dp)),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item {
                        Box(
                            Modifier.aspectRatio(1f).background(Wyrm.Well).clickable {
                                pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            contentAlignment = Alignment.Center,
                        ) { Text("All videos", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Wyrm.Mute) }
                    }
                    items(clips, key = { it.first.toString() }) { (uri, length) ->
                        var thumb by remember(uri) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(uri) { thumb = withContext(Dispatchers.IO) { TrailClips.thumbnail(context, uri) } }
                        Box(Modifier.aspectRatio(1f).background(Wyrm.Well).clickable { onClip(uri) }) {
                            thumb?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            Text(clipTime(length), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
            } else {
                Column(Modifier.align(Alignment.TopCenter).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your videos", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
                    PaperPrimaryButton(label = "Choose a video", onClick = {
                        pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    })
                }
            }
        }
    }
}

// ----------------------------------------------------------------- export

/**
 * The clip as it goes up: Media3 Transformer, on the main thread as it asks.
 * Size tiers by the short side: 720 (about 2.2 Mbit/s), 540 (1.5), 360 (0.9);
 * a clip smaller than a tier keeps its own size. A result over 15 MB is done
 * again a tier lower; 30 s at 720p is about 8.5 MB.
 */
internal object TrailVideoExport {
    private val tiers = listOf(720 to 2_200_000, 540 to 1_500_000, 360 to 900_000)
    private const val MAX_BYTES = 15L * 1024 * 1024

    /** The output size for a tier: even sides, the clip's own shape. */
    fun outputSize(clip: TrailClip, tier: Int): Pair<Int, Int> {
        val short = min(clip.width, clip.height)
        val target = min(tiers[tier].first, short).coerceAtLeast(2)
        val k = target.toFloat() / short
        fun even(v: Float) = (v.roundToInt() / 2 * 2).coerceAtLeast(2)
        return even(clip.width * k) to even(clip.height * k)
    }

    suspend fun export(
        context: Context,
        clip: TrailClip,
        startMs: Long,
        endMs: Long,
        muted: Boolean,
        look: List<Effect>,
        overlay: (Int, Int) -> Bitmap?,
        progress: (Float) -> Unit,
    ): File {
        var tier = 0
        while (true) {
            val file = once(context, clip, startMs, endMs, muted, look, overlay, tier, progress)
            if (file.length() <= MAX_BYTES || tier == tiers.lastIndex) return file
            tier += 1
        }
    }

    private suspend fun once(
        context: Context,
        clip: TrailClip,
        startMs: Long,
        endMs: Long,
        muted: Boolean,
        look: List<Effect>,
        overlay: (Int, Int) -> Bitmap?,
        tier: Int,
        progress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Main) {
        val (outW, outH) = outputSize(clip, tier)
        val folder = File(context.cacheDir, "trails-video").apply { mkdirs() }
        folder.listFiles()?.filter { it.name.startsWith("out-") }?.forEach { it.delete() }
        val file = File(folder, "out-${System.currentTimeMillis()}.mp4")
        val effects = buildList<Effect> {
            add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            addAll(look)
            overlay(outW, outH)?.let { add(OverlayEffect(ImmutableList.of<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(it)))) }
        }
        val item = MediaItem.Builder().setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build(),
            )
            .build()
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(muted)
            .setEffects(Effects(emptyList(), effects))
            .build()
        @Suppress("DEPRECATION")
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(edited).build())
            .setHdrMode(if (Build.VERSION.SDK_INT >= 29) Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL else Composition.HDR_MODE_KEEP_HDR)
            .build()
        val encoder = DefaultEncoderFactory.Builder(context.applicationContext)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(tiers[tier].second).build())
            .build()
        suspendCancellableCoroutine<File> { done ->
            val transformer = Transformer.Builder(context.applicationContext)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoder)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (done.isActive) done.resume(file)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (done.isActive) done.resumeWithException(exportException)
                    }
                })
                .build()
            val main = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    if (!done.isActive) return
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress / 100f)
                    main.postDelayed(this, 250)
                }
            }
            done.invokeOnCancellation { main.post { runCatching { transformer.cancel() } } }
            transformer.start(composition, file.absolutePath)
            main.post(poll)
        }
    }

    /**
     * The poster: the cover frame with the look and the overlay, the size of
     * the clip's 720p tier (the photo encoder makes the full and thumb JPEGs).
     */
    fun poster(context: Context, clip: TrailClip, atMs: Long, filter: android.graphics.ColorMatrixColorFilter?, overlay: (Int, Int) -> Bitmap?): Bitmap? {
        val (outW, outH) = outputSize(clip, 0)
        val frame = TrailClips.frame(context, clip, atMs, max(outW, outH)) ?: return null
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = filter }
        // Cover the output frame, as Presentation's crop layout does.
        val k = max(outW.toFloat() / frame.width, outH.toFloat() / frame.height)
        val w = frame.width * k
        val h = frame.height * k
        canvas.drawBitmap(frame, null, RectF((outW - w) / 2, (outH - h) / 2, (outW + w) / 2, (outH + h) / 2), paint)
        overlay(outW, outH)?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        return out
    }
}

// ------------------------------------------------------------------- feed

/**
 * The feed's one player (OM, 2026-10-05: Instagram-light): one clip plays at
 * a time, the card most in view; clips come through a 256 MB disk cache, so
 * scrolling back never fetches a clip twice. Muted until the player taps.
 */
internal object TrailFeedPlayer {
    private var cache: SimpleCache? = null
    private var player: ExoPlayer? = null
    private var current: String? = null
    var muted by mutableStateOf(true)
    /** The clip whose card is most in view; only that one plays. */
    var activeId by mutableStateOf<String?>(null)
    /** Bumped when the playing clip draws its first frame, so its card drops the poster. */
    var firstFrame by mutableStateOf<String?>(null)

    private fun player(context: Context): ExoPlayer {
        player?.let { return it }
        val app = context.applicationContext
        val store = cache ?: SimpleCache(
            File(app.cacheDir, "trails-video-cache"),
            LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
            StandaloneDatabaseProvider(app),
        ).also { cache = it }
        val source = CacheDataSource.Factory()
            .setCache(store)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setConnectTimeoutMs(10_000).setReadTimeoutMs(20_000))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(app).setDataSourceFactory(source))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (muted) 0f else 1f
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrame = current }
                })
            }
            .also { player = it }
    }

    fun attach(context: Context, id: String, url: String, view: TextureView) {
        val p = player(context)
        p.setVideoTextureView(view)
        if (current != id) {
            current = id
            firstFrame = null
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
        }
        p.volume = if (muted) 0f else 1f
        p.play()
    }

    fun detach(id: String, view: TextureView) {
        val p = player ?: return
        if (current != id) return
        p.pause()
        p.clearVideoTextureView(view)
    }

    fun toggleSound() {
        muted = !muted
        player?.volume = if (muted) 0f else 1f
    }

    /** Leaving Trails, or the app going to the background. */
    fun pause() { player?.pause() }

    /** Back in the app: the clip that was in view plays on. */
    fun resume() { if (current != null && current == activeId) player?.play() }

    /** Sign-out: nothing of the last account plays on. */
    fun stop() {
        player?.stop()
        current = null
        activeId = null
    }
}

/**
 * A video trail in a card: the poster at once; the clip over it while its
 * card is the one most in view. The corner shows the length and a sound
 * button.
 */
@Composable
internal fun TrailVideoView(trailId: String, video: com.wyrm.omrajput.data.TrailVideo, posterUrl: String, thumbUrl: String,
                            aspect: Float, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Box(modifier.fillMaxWidth().aspectRatio(aspect).background(Color.Black), contentAlignment = Alignment.Center) {
        if (active) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { TrailFeedPlayer.attach(context, trailId, video.url, it) } },
                onRelease = { TrailFeedPlayer.detach(trailId, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!active || TrailFeedPlayer.firstFrame != trailId) {
            TrailImage(posterUrl, thumbUrl, aspect, Modifier.fillMaxSize())
        }
        Text(clipTime(video.durationMs), fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp))
        Box(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    TrailFeedPlayer.toggleSound()
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (TrailFeedPlayer.muted) "🔇" else "🔊", fontSize = 13.sp) }
        if (active && TrailFeedPlayer.firstFrame != trailId) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

/** The editor's playback progress as a fraction, for small progress rings. */
internal fun TrailVideoSession.fraction(): Float = (playhead.toFloat() / lengthMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
