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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.withFrameNanos
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
import androidx.media3.effect.Crop
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
 * Trails videos (OM, 2026-10-05): "a video editor like Instagram's", at most
 * 30 seconds, compressed in the app to 360p-720p.
 *
 * The studio's camera records (hold the shutter, or tap it on the Video page,
 * CameraX, at most 30 s; `TrailCamera.kt`) or a clip comes from the gallery.
 * The editor is the photo editor itself over the playing clip: the same text,
 * drawing, stickers and looks (`TrailLooks`), plus, since 2026-10-17, a
 * professional cut (`TrailTimeline.kt`): pieces split, trimmed and dropped,
 * transitions at the cuts, a crop, and a cover picked while the picture shows
 * that frame. Export is Media3 Transformer: one clipped item per piece with
 * the crop, the size (short side 720, 540 or 360, the crop's own if smaller),
 * the transitions, the look as an RgbMatrix and the overlay as one bitmap the
 * size of the output frame, H.264 + AAC, HDR tone-mapped to SDR. The player
 * previews the same cut, so the editor shows what goes up. The server takes it
 * as it is (`backend/src/trail-video.mjs`). Wyrm iOS: `WyrmTrailVideo.swift`.
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
 * The clip being edited (OM, 2026-10-17): its cut (`TrailEdit`: pieces,
 * transitions, crop, cover, sound) and the player that previews it. The
 * player holds one clipped item per piece and loops over all of them; the
 * look and the overlays live on the studio's draft, shared with the photo
 * editor. While a piece's end is dragged the player shows the clip itself at
 * that moment, then goes back to the pieces.
 */
internal class TrailVideoSession(context: Context, val clip: TrailClip) {
    val edit = TrailEdit(clip)
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
        // Effects must be set once before prepare; the looks replace them later.
        setVideoEffects(emptyList())
    }
    /** Where the player is in the finished video. */
    var playhead by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    /** The whole clip is loaded (a piece's end is being dragged), not the pieces. */
    private var showingSource = false
    private var effects: List<Effect> = emptyList()

    private fun items(): List<MediaItem> = edit.segments.map { s ->
        MediaItem.Builder().setUri(clip.uri).setMediaId(s.id.toString())
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s.startMs).setEndPositionMs(s.endMs).build(),
            )
            .build()
    }

    /** The pieces into the player, from the playhead. */
    fun load(play: Boolean = true) {
        showingSource = false
        val (index, into) = edit.locate(playhead)
        player.setMediaItems(items(), index, into)
        player.volume = if (edit.muted) 0f else 1f
        player.prepare()
        if (play) player.play() else player.pause()
    }

    /** After a split, a delete or a trim: the same place, the new pieces. */
    fun reload() {
        playhead = playhead.coerceIn(0L, (edit.totalMs - 1).coerceAtLeast(0L))
        load(play = playing)
    }

    /** Reads the player once a frame. */
    fun tick() {
        playing = player.isPlaying
        if (showingSource) return
        val index = player.currentMediaItemIndex
        if (index in 0 until edit.segments.size) {
            playhead = (edit.startOf(index) + player.currentPosition.coerceAtLeast(0L)).coerceIn(0L, edit.totalMs)
        }
    }

    fun seekEdited(ms: Long, play: Boolean) {
        if (showingSource) load(play = false)
        val (index, into) = edit.locate(ms)
        playhead = ms.coerceIn(0L, edit.totalMs)
        player.seekTo(index, into)
        if (play) player.play() else player.pause()
    }

    /** The clip itself at [sourceMs], while a piece's end is dragged. */
    fun showSource(sourceMs: Long) {
        if (!showingSource) {
            showingSource = true
            player.setMediaItem(MediaItem.fromUri(clip.uri))
            player.prepare()
            player.pause()
        }
        player.seekTo(sourceMs.coerceIn(0L, clip.durationMs))
    }

    fun play() { if (showingSource) load(play = true) else player.play() }
    fun pause() = player.pause()
    fun togglePlay() { if (player.isPlaying) pause() else play() }

    fun applyEffects(next: List<Effect>) {
        if (next == effects) return
        effects = next
        player.setVideoEffects(next)
    }

    fun toggleSound() {
        edit.muted = !edit.muted
        player.volume = if (edit.muted) 0f else 1f
    }

    fun release() = player.release()
}

/**
 * The playing clip under the studio's canvas, as it will go up: cropped (the
 * picture is laid out larger than the canvas and moved so the crop fills it),
 * with the transitions drawn over it at each cut.
 */
@Composable
internal fun TrailVideoPreview(session: TrailVideoSession, modifier: Modifier = Modifier) {
    LaunchedEffect(session) {
        while (isActive) {
            withFrameNanos { }
            session.tick()
        }
    }
    val edit = session.edit
    val (kind, k) = TrailTransitions.at(edit.segments, session.playhead)
    BoxWithConstraints(modifier.clipToBounds().background(Color.Black)) {
        val cw = constraints.maxWidth
        val ch = constraints.maxHeight
        val r = edit.crop.rect(session.clip)
        val fullW = (cw / r.width().coerceAtLeast(0.01f)).roundToInt().coerceAtLeast(1)
        val fullH = (ch / r.height().coerceAtLeast(0.01f)).roundToInt().coerceAtLeast(1)
        val offX = -(r.left * fullW).roundToInt()
        val offY = -(r.top * fullH).roundToInt()
        val zoom = if (kind == TrailTransition.ZOOM) 1f + TrailTransitions.ZOOM * k else 1f
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { session.player.setVideoTextureView(it) } },
                onRelease = { session.player.clearVideoTextureView(it) },
                modifier = Modifier.layout { measurable, c ->
                    val placeable = measurable.measure(Constraints.fixed(fullW, fullH))
                    layout(c.maxWidth, c.maxHeight) { placeable.place(offX, offY) }
                },
            )
        }
        if (kind == TrailTransition.FADE && k > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = k)))
        if (kind == TrailTransition.FLASH && k > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = k)))
    }
}

// ----------------------------------------------------------------- export

/**
 * The video as it goes up: Media3 Transformer, on the main thread as it asks.
 * One clipped item per piece; each gets the crop, the size, its transitions,
 * the look and the overlay. Size tiers by the short side of the cropped
 * picture: 720 (about 3.5 Mbit/s), 540 (2.0), 360 (1.0); a crop smaller than a
 * tier keeps its own size, never scaled up. A result over 15 MB is done again
 * a tier lower; 30 s at 720p is about 13.5 MB. 3.5 Mbit/s since 2026-10-17
 * (OM: 720p without losing quality; 2.2 was soft on fast motion), the same on
 * iOS.
 */
internal object TrailVideoExport {
    private val tiers = listOf(720 to 3_500_000, 540 to 2_000_000, 360 to 1_000_000)
    private const val MAX_BYTES = 15L * 1024 * 1024

    /** The output size for a tier: even sides, the crop's own shape. */
    fun outputSize(spec: TrailEditSpec, tier: Int): Pair<Int, Int> {
        val r = spec.crop.rect(spec.clip)
        val cw = r.width() * spec.clip.width
        val ch = r.height() * spec.clip.height
        val short = min(cw, ch).coerceAtLeast(2f)
        val target = min(tiers[tier].first.toFloat(), short).coerceAtLeast(2f)
        val k = target / short
        fun even(v: Float) = (v.roundToInt() / 2 * 2).coerceAtLeast(2)
        return even(cw * k) to even(ch * k)
    }

    suspend fun export(
        context: Context,
        spec: TrailEditSpec,
        look: List<Effect>,
        overlay: (Int, Int) -> Bitmap?,
        progress: (Float) -> Unit,
    ): File {
        var tier = 0
        while (true) {
            val file = once(context, spec, look, overlay, tier, progress)
            if (file.length() <= MAX_BYTES || tier == tiers.lastIndex) return file
            tier += 1
        }
    }

    private suspend fun once(
        context: Context,
        spec: TrailEditSpec,
        look: List<Effect>,
        overlay: (Int, Int) -> Bitmap?,
        tier: Int,
        progress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Main) {
        val (outW, outH) = outputSize(spec, tier)
        val folder = File(context.cacheDir, "trails-video").apply { mkdirs() }
        folder.listFiles()?.filter { it.name.startsWith("out-") }?.forEach { it.delete() }
        val file = File(folder, "out-${System.currentTimeMillis()}.mp4")
        val top = overlay(outW, outH)
        val r = spec.crop.rect(spec.clip)
        val pieces = spec.segments
        val items = pieces.mapIndexed { i, s ->
            val into = if (i > 0) pieces[i - 1].after else TrailTransition.NONE
            val out = if (i < pieces.lastIndex) s.after else TrailTransition.NONE
            val inUs = if (i > 0) TrailTransitions.halfAfter(pieces, i - 1) * 1000 else 0L
            val outUs = if (i < pieces.lastIndex) TrailTransitions.halfAfter(pieces, i) * 1000 else 0L
            val lengthUs = s.lengthMs * 1000
            val effects = buildList<Effect> {
                // The crop in the frame's own -1..1 space (y up), then the size.
                if (!spec.crop.untouched) add(Crop(2f * r.left - 1f, 2f * r.right - 1f, 1f - 2f * r.bottom, 1f - 2f * r.top))
                add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                if (into == TrailTransition.ZOOM || out == TrailTransition.ZOOM) add(TrailTransitionZoom(lengthUs, into, inUs, out, outUs))
                addAll(look)
                val toned = { t: TrailTransition -> t == TrailTransition.FADE || t == TrailTransition.FLASH }
                if (toned(into) || toned(out)) add(TrailTransitionTone(lengthUs, into, inUs, out, outUs))
                top?.let { add(OverlayEffect(ImmutableList.of<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(it)))) }
            }
            val item = MediaItem.Builder().setUri(spec.clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s.startMs).setEndPositionMs(s.endMs).build(),
                )
                .build()
            EditedMediaItem.Builder(item)
                .setRemoveAudio(spec.muted)
                .setEffects(Effects(emptyList(), effects))
                .build()
        }
        @Suppress("DEPRECATION")
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build())
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
     * The poster: the cover frame, cropped, in the look with the overlay, the
     * size of the video's 720p tier (the photo encoder makes the full and
     * thumb JPEGs).
     */
    fun poster(context: Context, spec: TrailEditSpec, filter: android.graphics.ColorMatrixColorFilter?, overlay: (Int, Int) -> Bitmap?): Bitmap? {
        val (outW, outH) = outputSize(spec, 0)
        val r = spec.crop.rect(spec.clip)
        val longest = (max(outW / r.width().coerceAtLeast(0.05f), outH / r.height().coerceAtLeast(0.05f))).roundToInt().coerceIn(256, 2400)
        val frame = TrailClips.frame(context, spec.clip, spec.sourceAt(spec.coverMs), longest) ?: return null
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = filter }
        val src = android.graphics.Rect(
            (r.left * frame.width).roundToInt(), (r.top * frame.height).roundToInt(),
            (r.right * frame.width).roundToInt().coerceAtLeast(1), (r.bottom * frame.height).roundToInt().coerceAtLeast(1),
        )
        canvas.drawBitmap(frame, src, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), paint)
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
