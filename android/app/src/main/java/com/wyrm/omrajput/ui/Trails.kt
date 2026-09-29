package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.wyrm.omrajput.data.ApiException
import com.wyrm.omrajput.data.Trail
import com.wyrm.omrajput.data.TrailComment
import com.wyrm.omrajput.data.WyrmRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Trails (OM, 2026-09-28), as Wyrm iOS draws them (`WyrmTrails.swift`):
 * photos or words from the community, a bead to like, replies.
 *
 * Wyrm's own, not a copy of any other app: a like is a bead that fills with
 * the theme's live colour; an upload is a snake of beads that fills as it
 * goes while its card sways; a deleted trail breaks into glowing food, the way
 * a snake's body scatters when it dies, coloured from its own photo.
 *
 * All image work is in the app: photos are resized and re-encoded before they
 * go up, which also drops their location. The player sees only "Preparing" and
 * "Uploading". The feed pages 20 at a time and paints each thumbnail before its
 * full photo; images are kept in memory and on disk.
 */

// ------------------------------------------------------------------- store

sealed interface TrailPostPhase {
    data object Idle : TrailPostPhase
    data object Preparing : TrailPostPhase
    data class Uploading(val progress: Float) : TrailPostPhase
    data object Posted : TrailPostPhase
    data class Failed(val message: String) : TrailPostPhase

    val busy: Boolean get() = this is Preparing || this is Uploading
}

/** One profile's trail grid: what is loaded, and where the next page starts. */
data class AuthorTrails(
    val trails: List<Trail> = emptyList(),
    val cursor: String? = null,
    val reachedEnd: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

object TrailsStore {
    var repository: WyrmRepository? = null
    /** The last good answers, per account (OM, 2026-09-29): see [refresh]. */
    var cache: com.wyrm.omrajput.data.SocialCache? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Trails opened from outside the feed (a grid, an alert): an old one never jumps to its top. */
    val loose = mutableStateMapOf<String, Trail>()
    /** Each profile's grid, by player id. */
    val authors = mutableStateMapOf<String, AuthorTrails>()

    val trails = mutableStateListOf<Trail>()
    var loading by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var reachedEnd by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var error by mutableStateOf("")
    var posting by mutableStateOf<TrailPostPhase>(TrailPostPhase.Idle); private set
    var pendingActive by mutableStateOf(false); private set
    var pendingImage by mutableStateOf<Bitmap?>(null); private set
    var pendingCaption by mutableStateOf(""); private set
    val comments = mutableStateMapOf<String, List<TrailComment>>()
    var toast by mutableStateOf("")
    private var cursor: String? = null
    private val liking = mutableSetOf<String>()

    fun trail(id: String): Trail? =
        trails.firstOrNull { it.id == id } ?: loose[id] ?: authors.values.firstNotNullOfOrNull { entry -> entry.trails.firstOrNull { it.id == id } }

    /** One change, applied wherever this trail is shown. */
    private fun patch(id: String, change: (Trail) -> Trail) {
        val i = trails.indexOfFirst { it.id == id }
        if (i >= 0) trails[i] = change(trails[i])
        loose[id]?.let { loose[id] = change(it) }
        for ((key, entry) in authors.toMap()) {
            if (entry.trails.any { it.id == id }) {
                authors[key] = entry.copy(trails = entry.trails.map { if (it.id == id) change(it) else it })
            }
        }
    }

    private fun persistFeed() { cache?.saveTrails("feed", trails.take(20)) }

    /** Sign-out: "liked" and "mine" belong to the account that signed out. */
    fun reset() {
        trails.clear()
        loose.clear()
        authors.clear()
        comments.clear()
        cursor = null
        reachedEnd = false
        loaded = false
        error = ""
        liking.clear()
    }

    /**
     * Stale-while-revalidate: the last first page paints at once from the
     * cache, the network answer replaces it in place (same ids keep their
     * spot), and a placeholder is only ever seen on the very first open.
     */
    fun refresh(done: () -> Unit = {}) {
        val repo = repository ?: return done()
        if (loading) return done()
        if (!loaded && trails.isEmpty()) {
            cache?.trails("feed")?.takeIf { it.isNotEmpty() }?.let { trails.addAll(it); loaded = true }
        }
        loading = true
        scope.launch {
            try {
                val page = repo.trails(null)
                trails.clear()
                trails.addAll(page.trails)
                cursor = page.nextCursor
                reachedEnd = page.nextCursor == null
                error = ""
                prefetch(page.trails)
                persistFeed()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                error = message(failure)
            } finally {
                loading = false
                loaded = true
                done()
            }
        }
    }

    fun loadMoreIfNeeded(id: String) {
        val repo = repository ?: return
        val next = cursor ?: return
        if (id != trails.lastOrNull()?.id || reachedEnd || loadingMore || loading) return
        loadingMore = true
        scope.launch {
            try {
                val page = repo.trails(next)
                val known = trails.map { it.id }.toSet()
                trails.addAll(page.trails.filter { it.id !in known })
                cursor = page.nextCursor
                reachedEnd = page.nextCursor == null
                prefetch(page.trails)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                error = message(failure)
            } finally {
                loadingMore = false
            }
        }
    }

    /** The trail fresh from the server, wherever it is shown. One not in the feed stays out of it. */
    fun reload(id: String) {
        val repo = repository ?: return
        scope.launch {
            runCatching { repo.trail(id) }.getOrNull()?.let { fresh ->
                val shown = trails.any { it.id == id } || authors.values.any { entry -> entry.trails.any { it.id == id } }
                if (!shown) loose[id] = fresh
                patch(id) { fresh }
            }
        }
    }

    /** A player's trails for their profile grid, cached per player. */
    fun loadAuthor(id: String) {
        val repo = repository ?: return
        if (id.isBlank()) return
        if (authors[id] == null) {
            val cached = cache?.trails("author_$id")
            authors[id] = if (cached != null) AuthorTrails(trails = cached, loaded = true) else AuthorTrails()
        }
        if (authors[id]?.loading == true) return
        authors[id] = authors[id]!!.copy(loading = true)
        scope.launch {
            try {
                val page = repo.trails(null, author = id)
                authors[id] = AuthorTrails(trails = page.trails, cursor = page.nextCursor,
                    reachedEnd = page.nextCursor == null, loaded = true)
                cache?.saveTrails("author_$id", page.trails.take(30))
                TrailImages.prefetch(page.trails.mapNotNull { it.thumbUrl })
            } catch (cancel: CancellationException) {
                authors[id]?.let { authors[id] = it.copy(loading = false) }
                throw cancel
            } catch (_: Exception) {
                authors[id]?.let { authors[id] = it.copy(loading = false, loaded = true, failed = it.trails.isEmpty()) }
            }
        }
    }

    fun loadMoreAuthor(id: String, afterId: String) {
        val repo = repository ?: return
        val entry = authors[id] ?: return
        val next = entry.cursor ?: return
        if (afterId != entry.trails.lastOrNull()?.id || entry.reachedEnd || entry.loading) return
        authors[id] = entry.copy(loading = true)
        scope.launch {
            try {
                val page = repo.trails(next, author = id)
                val current = authors[id] ?: return@launch
                val known = current.trails.map { it.id }.toSet()
                authors[id] = current.copy(trails = current.trails + page.trails.filter { it.id !in known },
                    cursor = page.nextCursor, reachedEnd = page.nextCursor == null, loading = false)
            } catch (cancel: CancellationException) {
                authors[id]?.let { authors[id] = it.copy(loading = false) }
                throw cancel
            } catch (_: Exception) {
                authors[id]?.let { authors[id] = it.copy(loading = false) }
            }
        }
    }

    /** On screen at once; the server's count wins when it answers. */
    fun toggleLike(id: String) {
        val repo = repository ?: return
        val current = trail(id) ?: return
        if (id in liking) return
        val next = !current.liked
        patch(id) { it.copy(liked = next, likeCount = max(0, it.likeCount + if (next) 1 else -1)) }
        liking += id
        scope.launch {
            try {
                val result = repo.likeTrail(id, next)
                patch(id) { it.copy(liked = result.liked, likeCount = result.likeCount) }
                persistFeed()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                patch(id) { it.copy(liked = !next, likeCount = max(0, it.likeCount + if (next) -1 else 1)) }
            } finally {
                liking -= id
            }
        }
    }

    /** A photo trail, or a text trail when [image] is null. Returns at once. */
    fun post(image: Bitmap?, caption: String) {
        val repo = repository ?: return
        if (posting.busy) return
        val words = caption.trim()
        pendingImage = image
        pendingCaption = words
        pendingActive = true
        posting = TrailPostPhase.Preparing
        scope.launch {
            try {
                val trail = if (image == null) {
                    posting = TrailPostPhase.Uploading(0.5f)
                    repo.createTrail(words, null, null)
                } else {
                    val files = withContext(Dispatchers.Default) { TrailEncoder.prepare(image) }
                    posting = TrailPostPhase.Uploading(0f)
                    val total = (files.first.size + files.second.size).toFloat()
                    val thumbShare = files.second.size / max(total, 1f)
                    val thumbId = repo.uploadTrailMedia(files.second) { value ->
                        scope.launch { posting = TrailPostPhase.Uploading(value * thumbShare * 0.97f) }
                    }
                    val photoId = repo.uploadTrailMedia(files.first) { value ->
                        scope.launch { posting = TrailPostPhase.Uploading((thumbShare + value * (1 - thumbShare)) * 0.97f) }
                    }
                    repo.createTrail(words, photoId, thumbId)
                }
                trails.add(0, trail)
                authors[trail.author.playerId]?.let { authors[trail.author.playerId] = it.copy(trails = listOf(trail) + it.trails) }
                persistFeed()
                pendingImage = null
                pendingActive = false
                posting = TrailPostPhase.Posted
                toast = "Trail posted"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                posting = TrailPostPhase.Failed(message(failure))
            }
        }
    }

    fun resetPosting() { if (!posting.busy && !pendingActive) posting = TrailPostPhase.Idle }

    fun retryPending() {
        if (!pendingActive || posting.busy) return
        val image = pendingImage
        val caption = pendingCaption
        posting = TrailPostPhase.Idle
        post(image, caption)
    }

    fun discardPending() {
        if (posting.busy) return
        pendingImage = null
        pendingActive = false
        posting = TrailPostPhase.Idle
    }

    /** The card has already scattered; the trails around it close the gap. */
    suspend fun delete(id: String): Boolean {
        val repo = repository ?: return false
        return try {
            repo.deleteTrail(id)
            trails.removeAll { it.id == id }
            loose.remove(id)
            for ((key, entry) in authors.toMap()) {
                if (entry.trails.any { it.id == id }) authors[key] = entry.copy(trails = entry.trails.filterNot { it.id == id })
            }
            persistFeed()
            toast = "Trail deleted"
            true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            toast = message(failure)
            false
        }
    }

    fun report(id: String, reason: String) {
        val repo = repository ?: return
        scope.launch {
            toast = runCatching { repo.reportTrail(id, reason) }.fold({ "Thanks. We'll take a look." }, { message(it) })
        }
    }

    fun loadComments(id: String) {
        val repo = repository ?: return
        scope.launch { runCatching { repo.trailComments(id) }.getOrNull()?.let { comments[id] = it.comments } }
    }

    fun reply(id: String, body: String, done: (Boolean) -> Unit) {
        val repo = repository ?: return done(false)
        scope.launch {
            try {
                val posted = repo.replyToTrail(id, body)
                comments[id] = (comments[id].orEmpty()) + posted.comment
                patch(id) { it.copy(commentCount = posted.commentCount) }
                done(true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                toast = message(failure)
                done(false)
            }
        }
    }

    fun deleteReply(id: String, commentId: String) {
        val repo = repository ?: return
        scope.launch {
            try {
                repo.deleteTrailReply(id, commentId)
                comments[id] = comments[id].orEmpty().filterNot { it.id == commentId }
                patch(id) { it.copy(commentCount = max(0, it.commentCount - 1)) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                toast = message(failure)
            }
        }
    }

    private fun prefetch(page: List<Trail>) {
        TrailImages.prefetch(page.mapNotNull { it.thumbUrl } + page.take(4).mapNotNull { it.photo?.url })
    }

    private fun message(failure: Throwable): String = when {
        failure is ApiException -> when (failure.message) {
            "IMAGE_TOO_LARGE" -> "That photo is too large."
            "UNSUPPORTED_IMAGE" -> "That photo could not be read."
            "NOT_FOUND" -> "This trail is no longer here."
            "BLOCKED" -> "You can't reply to this trail."
            "HTTP_429" -> "Slow down a little and try again soon."
            else -> "Something went wrong. Try again."
        }
        failure is java.io.IOException -> "Could not reach Wyrm. Check your connection."
        else -> "Something went wrong. Try again."
    }
}

/** Resizes and re-encodes in the app: 1440 px on the long side and a 540 px thumbnail. */
object TrailEncoder {
    fun prepare(image: Bitmap): Pair<ByteArray, ByteArray> {
        var full = encode(image, 1440, 82)
        val thumb = encode(image, 540, 72)
        var quality = 72
        while (full.size > 2_800_000 && quality > 40) {
            full = encode(image, 1440, quality)
            quality -= 10
        }
        return full to thumb
    }

    private fun encode(image: Bitmap, longest: Int, quality: Int): ByteArray {
        val side = max(image.width, image.height).coerceAtLeast(1)
        val scale = min(1f, longest.toFloat() / side)
        val w = max(1, (image.width * scale).roundToInt())
        val h = max(1, (image.height * scale).roundToInt())
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(image, w, h, true) else image
        // Flattened onto black: a JPEG has no transparency.
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(flat).apply { drawColor(android.graphics.Color.BLACK); drawBitmap(sized, 0f, 0f, null) }
        return ByteArrayOutputStream().use { out ->
            flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }
}

/** Decoded photos in memory, files on disk; an address never changes meaning. */
object TrailImages {
    private val memory = object : LruCache<String, ImageBitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private var folder: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attach(context: Context) {
        folder = File(context.cacheDir, "trails").apply { mkdirs() }
    }

    fun peek(url: String): ImageBitmap? = memory.get(url)

    suspend fun load(url: String): ImageBitmap? = memory.get(url) ?: withContext(Dispatchers.IO) {
        val file = folder?.let { File(it, digest(url)) }
        val bytes = file?.takeIf { it.exists() }?.readBytes() ?: download(url)?.also { data ->
            file?.let { runCatching { it.writeBytes(data) } }
        } ?: return@withContext null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        bitmap.asImageBitmap().also { bitmap.prepareToDraw(); memory.put(url, it) }
    }

    fun prefetch(urls: List<String>) {
        for (url in urls.distinct()) if (memory.get(url) == null) scope.launch { load(url) }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode !in 200..299) null else connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun digest(url: String) =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
}

internal fun trailTime(raw: String): String = runCatching {
    val date = Instant.parse(raw)
    val seconds = Duration.between(date, Instant.now()).seconds
    when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 7 * 86_400 -> "${seconds / 86_400}d"
        else -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(date.atZone(ZoneId.systemDefault()))
    }
}.getOrDefault("")

// ------------------------------------------------------------------ pieces

/** A trail photo: its thumbnail at once, the full image when it lands; fitted, never stretched. */
@Composable
internal fun TrailImage(full: String, thumb: String, aspect: Float, modifier: Modifier = Modifier) {
    var image by remember(full) { mutableStateOf(TrailImages.peek(full) ?: TrailImages.peek(thumb)) }
    LaunchedEffect(full) {
        if (TrailImages.peek(full) != null) return@LaunchedEffect
        if (image == null) TrailImages.load(thumb)?.let { image = it }
        TrailImages.load(full)?.let { image = it }
    }
    Box(modifier.fillMaxWidth().aspectRatio(aspect).background(Wyrm.Well), contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

/** Wyrm's like: a bead that fills with the theme's live colour and pops. */
@Composable
internal fun TrailBead(liked: Boolean, count: Int, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(1f) }
    val fill = remember { Animatable(if (liked) 1f else 0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(liked) { fill.animateTo(if (liked) 1f else 0f, spring(0.55f, 600f)) }
    val live = Wyrm.Live
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (liked) live.copy(alpha = 0.14f) else Wyrm.Well)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.performHapticFeedback(if (liked) HapticFeedbackType.SegmentTick else HapticFeedbackType.LongPress)
                if (!liked) scope.launch { pop.snapTo(1f); pop.animateTo(1.35f, tween(110)); pop.animateTo(1f, spring(0.45f, 500f)) }
                onClick()
            }
            .padding(horizontal = 12.dp)
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val ring = if (liked) live else Wyrm.Quiet
        Canvas(Modifier.size(17.dp).scale(pop.value)) {
            val r = size.minDimension / 2
            drawCircle(ring, r - 0.8.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
            if (fill.value > 0.01f) {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), live), center = Offset(r * 0.7f, r * 0.6f), radius = r * 1.1f),
                    radius = r * fill.value,
                )
            }
        }
        Text(
            if (count == 0) "Like" else "$count",
            fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = if (liked) Wyrm.Ink else Wyrm.Mute,
        )
    }
}

@Composable
private fun TrailRepliesPill(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Wyrm.Well)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp)
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IosIcon(IosGlyph.BUBBLES, Wyrm.Mute, size = 15.dp, semibold = true)
        Text(if (count == 0) "Reply" else "$count", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, color = Wyrm.Mute)
    }
}

/** A text trail: the words themselves, set large, with the live colour's trail mark. */
@Composable
private fun TrailTextBody(text: String) {
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(wyrmRounded(16.dp))
            .background(Wyrm.Well)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(4.dp).height(if (text.length < 90) 34.dp else 26.dp).clip(CircleShape).background(Wyrm.Live))
        Text(text, fontFamily = Wyrm.Display, fontSize = if (text.length < 90) 26.sp else 20.sp,
            lineHeight = if (text.length < 90) 32.sp else 26.sp, color = Wyrm.Ink)
    }
}

/**
 * A deleted trail breaking into glowing food, the way a snake's body scatters
 * when it dies, each piece coloured from the photo where it lay.
 */
@Composable
private fun TrailFoodBurst(colours: List<Color>, size: IntSize) {
    val time = remember { Animatable(0f) }
    val pieces = remember(size) {
        val columns = 7
        val rows = max(4, (size.height.toFloat() / max(size.width, 1) * 7).roundToInt())
        buildList {
            for (row in 0 until rows) for (column in 0 until columns) {
                val x = (column + 0.5f) / columns * size.width
                val y = (row + 0.5f) / rows * size.height
                val dx = x - size.width / 2f
                val dy = y - size.height / 2f
                val length = max(hypot(dx, dy), 1f)
                val spread = Random.nextFloat() * 80f + 40f
                add(
                    FoodPiece(
                        start = Offset(x, y),
                        velocity = Offset(dx / length * spread * 3f + Random.nextFloat() * 40f - 20f,
                            dy / length * spread * 3f + Random.nextFloat() * 50f - 30f),
                        radius = Random.nextFloat() * 5f + 4f,
                        colour = colours.getOrElse((row * columns + column) % max(colours.size, 1)) { Color.White },
                        delay = Random.nextFloat() * 0.12f,
                    ),
                )
            }
        }
    }
    LaunchedEffect(Unit) { time.animateTo(1.2f, tween(1100, easing = LinearEasing)) }
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        for (piece in pieces) {
            val local = max(0f, t - piece.delay)
            val ease = 1f - exp(-local * 3.2f)
            val center = piece.start + piece.velocity * ease
            val life = max(0f, 1f - local / 0.95f)
            if (life <= 0f) continue
            val r = piece.radius * density * (1f + 0.18f * sin(local * 18f + piece.radius)) * (0.6f + 0.4f * life)
            drawCircle(
                Brush.radialGradient(listOf(piece.colour.copy(alpha = 0.55f * life), piece.colour.copy(alpha = 0f)), center, r * 2.4f),
                radius = r * 2.4f, center = center,
            )
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = life), piece.colour.copy(alpha = life)), center - Offset(r * 0.3f, r * 0.3f), r),
                radius = r, center = center,
            )
        }
    }
}

private class FoodPiece(val start: Offset, val velocity: Offset, val radius: Float, val colour: Color, val delay: Float)

/** Colours sampled on a grid across the photo, pushed brighter so the food glows. */
private fun foodColours(image: ImageBitmap?): List<Color> {
    val bitmap = image?.let { runCatching { it.asAndroidBitmap() }.getOrNull() } ?: return emptyList()
    val small = Bitmap.createScaledBitmap(bitmap, 7, 9, true)
    fun lift(v: Int) = min(1f, v / 255f * 1.25f + 0.08f)
    return buildList {
        for (y in 0 until 9) for (x in 0 until 7) {
            val pixel = small.getPixel(x, y)
            add(Color(lift((pixel shr 16) and 0xFF), lift((pixel shr 8) and 0xFF), lift(pixel and 0xFF)))
        }
    }
}

/** Upload progress as a snake of beads that fills with the live colour. */
@Composable
private fun TrailBeadProgress(progress: Float?) {
    val wave = rememberInfiniteTransition(label = "beads")
    val t by wave.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "wave")
    val live = Wyrm.Live
    val well = Wyrm.Well
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val count = 18
        val gap = size.width / count
        val lit = progress?.let { (it * count).toInt() }
        val runner = ((t / (2 * PI)) * count).toInt() % count
        for (i in 0 until count) {
            val on = lit?.let { i < it } ?: (i == runner)
            val y = size.height / 2 + sin(t * 2 - i * 0.55f) * 3.dp.toPx()
            val c = Offset(i * gap + gap / 2, y)
            val r = gap * 0.39f
            drawCircle(if (on) live else well, r, c)
            if (on) drawCircle(Color.White.copy(alpha = 0.35f), r * 0.4f, c - Offset(r * 0.3f, r * 0.3f))
        }
    }
}

/** The trail being posted: breathing and swaying while it uploads, or offering a retry. */
@Composable
private fun TrailPendingCard() {
    val phase = TrailsStore.posting
    val progress = when (phase) {
        is TrailPostPhase.Uploading -> phase.progress
        TrailPostPhase.Posted -> 1f
        else -> null
    }
    val haptics = LocalHapticFeedback.current
    var lastTick by remember { mutableStateOf(-1) }
    LaunchedEffect(progress) {
        progress?.let { value ->
            val tick = (value * 4).toInt()
            if (tick != lastTick) { lastTick = tick; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        }
    }
    val sway = rememberInfiniteTransition(label = "sway")
    val a by sway.animateFloat(-1f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val busy = phase.busy
    val title = when (phase) {
        TrailPostPhase.Preparing -> "Preparing…"
        is TrailPostPhase.Uploading -> "Uploading… ${(phase.progress * 100).roundToInt()}%"
        TrailPostPhase.Posted -> "Posted"
        is TrailPostPhase.Failed -> phase.message
        TrailPostPhase.Idle -> "Waiting…"
    }
    Column(
        Modifier
            .padding(horizontal = 14.dp)
            .graphicsLayer {
                rotationZ = if (busy) a * 0.9f else 0f
                val s = if (busy) 1f + a * 0.012f else 1f
                scaleX = s; scaleY = s
            }
            .fillMaxWidth()
            .clip(wyrmRounded(20.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(58.dp).clip(wyrmRounded(12.dp)).background(Wyrm.Well), contentAlignment = Alignment.Center) {
                val image = TrailsStore.pendingImage
                if (image != null) {
                    Image(remember(image) { image.asImageBitmap() }, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.85f })
                } else {
                    Text("“", fontFamily = Wyrm.Display, fontSize = 30.sp, color = Wyrm.Live)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Wyrm.Ink)
                Text(TrailsStore.pendingCaption.ifBlank { "Photo" }, fontFamily = Wyrm.Body, fontSize = 12.5.sp,
                    color = Wyrm.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (phase is TrailPostPhase.Failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { PaperOutlineButton(label = "Discard") { TrailsStore.discardPending() } }
                Box(Modifier.weight(1f)) { PaperPrimaryButton(label = "Try again") { TrailsStore.retryPending() } }
            }
        } else {
            TrailBeadProgress(progress)
        }
    }
}

/** One trail: who and when, the photo or words, then the caption and actions. */
@Composable
internal fun TrailCard(
    trail: Trail,
    expanded: Boolean = false,
    insetBottom: Dp,
    onOpen: () -> Unit,
    onAuthor: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var dying by remember { mutableStateOf(false) }
    var food by remember { mutableStateOf(emptyList<Color>()) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val burst = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val live = Wyrm.Live

    fun scatter() {
        val url = trail.thumbUrl ?: trail.photo?.url
        food = foodColours(url?.let { TrailImages.peek(it) }).ifEmpty { listOf(live, Wyrm.Link, Wyrm.Badge, Wyrm.Ink) }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        dying = true
        scope.launch {
            fade.animateTo(0f, tween(220))
            delay(630)
            if (!TrailsStore.delete(trail.id)) { dying = false; fade.animateTo(1f, tween(250)) }
        }
    }

    Box(Modifier.padding(horizontal = 14.dp).onSizeChanged { cardSize = it }) {
        Column(
            Modifier
                .graphicsLayer {
                    alpha = fade.value
                    val s = 0.92f + 0.08f * fade.value
                    scaleX = s; scaleY = s
                }
                .fillMaxWidth()
                .clip(wyrmRounded(22.dp))
                .background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(22.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAuthor),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WyrmAvatar(trail.author.avatarUrl, trail.author.avatarKey, trail.author.initials, 34.dp)
                    Column {
                        Text(trail.author.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = Wyrm.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(trail.author.handle, trailTime(trail.createdAt)).filter { it.isNotBlank() }.joinToString(" · "),
                            fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet, maxLines = 1)
                    }
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Text("⋯", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Wyrm.Quiet) }
            }

            Box(
                Modifier.padding(horizontal = 8.dp).pointerInput(trail.id, expanded) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!trail.liked) TrailsStore.toggleLike(trail.id)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                burst.snapTo(0f)
                                burst.animateTo(1f, spring(0.55f, 600f))
                                delay(350)
                                burst.animateTo(0f, tween(250))
                            }
                        },
                        onTap = { if (!expanded) onOpen() },
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                val photo = trail.photo
                if (photo != null) {
                    TrailImage(photo.url, trail.thumbUrl ?: photo.url, trail.aspect, Modifier.clip(wyrmRounded(16.dp)))
                } else {
                    Box(Modifier.padding(horizontal = 0.dp)) { TrailTextBody(trail.caption) }
                }
                if (burst.value > 0.01f) {
                    Canvas(Modifier.size(84.dp).scale(0.2f + 0.8f * burst.value).graphicsLayer { alpha = burst.value }) {
                        val r = size.minDimension / 2
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), live), Offset(r * 0.7f, r * 0.6f), r * 1.1f), r)
                    }
                }
            }

            if (trail.caption.isNotBlank() && trail.photo != null) {
                Text(
                    trail.caption,
                    fontFamily = Wyrm.Body, fontSize = 14.5.sp, lineHeight = 21.sp, color = Wyrm.Ink,
                    maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            Row(
                Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrailBead(trail.liked, trail.likeCount) { TrailsStore.toggleLike(trail.id) }
                TrailRepliesPill(trail.commentCount, onOpen)
            }
        }
        if (dying && cardSize != IntSize.Zero) {
            Box(Modifier.matchParentSize()) { TrailFoodBurst(food, cardSize) }
        }
    }

    if (menu) {
        IosActionSheet(
            title = if (trail.mine) "Your trail" else "Trail",
            actions = if (trail.mine) {
                listOf(IosSheetAction("Delete trail", destructive = true) { confirmDelete = true })
            } else {
                listOf(IosSheetAction("Report") { reporting = true })
            },
            insetBottom = insetBottom,
        ) { menu = false }
    }
    if (confirmDelete) {
        IosActionSheet(
            title = "Delete this trail?",
            actions = listOf(IosSheetAction("Delete", destructive = true) { scatter() }),
            insetBottom = insetBottom,
        ) { confirmDelete = false }
    }
    if (reporting) {
        IosActionSheet(
            title = "Report trail",
            actions = listOf("Spam", "Harassment or abuse", "Nudity or sexual content", "Hate or violence", "Something else")
                .map { reason -> IosSheetAction(reason) { TrailsStore.report(trail.id, reason) } },
            insetBottom = insetBottom,
        ) { reporting = false }
    }
}

@Composable
private fun TrailPlaceholder() {
    val pulse = rememberInfiniteTransition(label = "placeholder")
    val a by pulse.animateFloat(1f, 0.55f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().graphicsLayer { alpha = a }
            .clip(wyrmRounded(22.dp)).background(Wyrm.Card).border(1.dp, Wyrm.Rule, wyrmRounded(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(wyrmRounded(10.dp)).background(Wyrm.Well))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(120.dp).height(10.dp).clip(CircleShape).background(Wyrm.Well))
                Box(Modifier.width(70.dp).height(8.dp).clip(CircleShape).background(Wyrm.Well))
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(wyrmRounded(16.dp)).background(Wyrm.Well))
    }
}

@Composable
internal fun TrailToast(modifier: Modifier = Modifier) {
    val text = TrailsStore.toast
    LaunchedEffect(text) {
        if (text.isNotEmpty()) { delay(2000); if (TrailsStore.toast == text) TrailsStore.toast = "" }
    }
    AnimatedVisibility(text.isNotEmpty(), modifier = modifier, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
        Text(
            text, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Wyrm.OnInk,
            modifier = Modifier.clip(CircleShape).background(Wyrm.Ink).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** The one way to post: top right once there are trails, in the middle while there are none. */
@Composable
private fun TrailNewButton(large: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .shadow(if (large) 16.dp else 8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Wyrm.Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (large) 22.dp else 14.dp)
            .height(if (large) 50.dp else 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 8.dp else 6.dp),
    ) {
        Text("+", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = if (large) 18.sp else 15.sp, color = Wyrm.OnInk)
        Text(if (large) "Leave a trail" else "New", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold,
            fontSize = if (large) 15.sp else 13.sp, color = Wyrm.OnInk)
    }
}

// -------------------------------------------------------------------- feed

@Composable
fun TrailsFeedScreen(
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onAuthor: (String) -> Unit,
) {
    LaunchedEffect(Unit) { if (!TrailsStore.loaded) TrailsStore.refresh() }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.key as? String }.collect { key ->
            key?.let { TrailsStore.loadMoreIfNeeded(it) }
        }
    }
    val hasTrails = TrailsStore.trails.isNotEmpty() || TrailsStore.pendingActive
    var pulling by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Wyrm.Paper)) {
        Column(Modifier.fillMaxSize().padding(top = insetTop)) {
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                Row(
                    Modifier.align(Alignment.CenterStart)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IosIcon(IosGlyph.CHEVRON_LEFT, Wyrm.Link, size = 16.dp, semibold = true)
                    Text("Back", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Wyrm.Link)
                }
                Text("Trails", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Wyrm.Ink,
                    modifier = Modifier.align(Alignment.Center))
                val shown by androidx.compose.animation.core.animateFloatAsState(
                    if (hasTrails) 1f else 0f, spring(0.7f, 400f), label = "new button",
                )
                if (shown > 0.01f) {
                    Box(Modifier.align(Alignment.CenterEnd).graphicsLayer {
                        alpha = shown
                        scaleX = 0.6f + 0.4f * shown
                        scaleY = 0.6f + 0.4f * shown
                    }) { TrailNewButton(large = false, onClick = onNew) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Wyrm.Rule))
            IosRefreshable(pulling, { pulling = true; TrailsStore.refresh { pulling = false } }, Modifier.weight(1f)) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item(key = "header") {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                            Text("THE WYRM COMMUNITY", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                                letterSpacing = 1.15.sp, color = Wyrm.Quiet)
                            Text("Trails", fontFamily = Wyrm.Display, fontSize = 34.sp, color = Wyrm.Ink)
                            Text("Show off your skins, kills and best moments.", fontFamily = Wyrm.Body, fontSize = 13.sp, color = Wyrm.Mute)
                        }
                    }
                    when {
                        !TrailsStore.loaded && TrailsStore.trails.isEmpty() -> items(3, key = { "placeholder-$it" }) { TrailPlaceholder() }
                        !hasTrails -> item(key = "empty") { TrailsEmpty(onNew) }
                        else -> {
                            if (TrailsStore.pendingActive) item(key = "pending") { Box(Modifier.animateItem()) { TrailPendingCard() } }
                            items(TrailsStore.trails, key = { it.id }) { trail ->
                                Box(Modifier.animateItem(fadeInSpec = tween(250), placementSpec = spring(0.82f, 380f), fadeOutSpec = tween(150))) {
                                    TrailCard(trail, insetBottom = insetBottom, onOpen = { onOpen(trail.id) },
                                        onAuthor = { onAuthor(trail.author.playerId) })
                                }
                            }
                            if (TrailsStore.loadingMore) item(key = "more") {
                                Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Wyrm.Quiet, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                }
                            }
                            if (TrailsStore.reachedEnd && TrailsStore.trails.size > 3) item(key = "end") {
                                Text("You're all caught up", fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                                    color = Wyrm.Quiet, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(18.dp))
                            }
                        }
                    }
                    if (TrailsStore.error.isNotEmpty() && TrailsStore.trails.isEmpty()) item(key = "error") {
                        Text(TrailsStore.error, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Badge,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    item(key = "foot") { Spacer(Modifier.height(40.dp + insetBottom)) }
                }
            }
        }
        TrailToast(Modifier.align(Alignment.TopCenter).padding(top = insetTop + 60.dp))
    }
}

@Composable
private fun TrailsEmpty(onNew: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(wyrmRounded(22.dp)).background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(22.dp)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(width = 42.dp, height = 18.dp)) {
            val r = size.height / 2.4f
            for (i in 0 until 5) drawCircle(Wyrm.Live.copy(alpha = 0.4f + i * 0.15f), r, Offset(r + i * r * 1.5f, size.height / 2))
        }
        Text("No trails yet", fontFamily = Wyrm.Display, fontSize = 22.sp, color = Wyrm.Ink)
        Text("Be the first to leave one. Share a skin, a big run or a moment from the arena.", fontFamily = Wyrm.Body,
            fontSize = 13.sp, color = Wyrm.Mute, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        TrailNewButton(large = true, onClick = onNew)
    }
}

// ------------------------------------------------------------------ detail

@Composable
fun TrailDetailScreen(
    trailId: String,
    meId: String,
    insetTop: Dp,
    insetBottom: Dp,
    onBack: () -> Unit,
    onAuthor: (String) -> Unit,
) {
    LaunchedEffect(trailId) {
        if (TrailsStore.trail(trailId) == null) TrailsStore.reload(trailId)
        TrailsStore.loadComments(trailId)
    }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val trail = TrailsStore.trail(trailId)
    val replies = TrailsStore.comments[trailId].orEmpty()
    Box(Modifier.fillMaxSize()) {
        IosPageChrome("Trail", insetTop, onBack) {
            val backdrop = rememberLayerBackdrop()
            val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Column(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().layerBackdrop(backdrop)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "trail") {
                            Box(Modifier.padding(top = 12.dp)) {
                                if (trail != null) {
                                    TrailCard(trail, expanded = true, insetBottom = insetBottom, onOpen = {},
                                        onAuthor = { onAuthor(trail.author.playerId) })
                                } else {
                                    TrailPlaceholder()
                                }
                            }
                        }
                        item(key = "label") { IosSectionLabel("Replies") }
                        if (replies.isEmpty()) {
                            item(key = "none") {
                                Text("No replies yet. Be the first to say something.", fontFamily = Wyrm.Body, fontSize = 12.5.sp,
                                    color = Wyrm.Quiet, modifier = Modifier.padding(horizontal = 20.dp))
                            }
                        } else {
                            items(replies, key = { it.id }) { reply ->
                                TrailReplyRow(reply, isAuthor = reply.author.playerId == trail?.author?.playerId,
                                    canDelete = reply.mine || trail?.mine == true, insetBottom = insetBottom,
                                    onAuthor = { onAuthor(reply.author.playerId) },
                                    onDelete = { TrailsStore.deleteReply(trailId, reply.id) })
                            }
                        }
                        item(key = "foot") { Spacer(Modifier.height(24.dp)) }
                    }
                }
                IosChatComposer(
                    text = draft,
                    onText = { draft = it.take(300) },
                    placeholder = "Reply to this trail",
                    limit = 300,
                    sending = sending,
                    bottomInset = if (imeUp) 0.dp else insetBottom,
                    backdrop = backdrop,
                ) {
                    val body = draft.trim()
                    if (body.isNotEmpty() && !sending) {
                        sending = true
                        TrailsStore.reply(trailId, body) { ok -> if (ok) draft = ""; sending = false }
                    }
                }
            }
        }
        TrailToast(Modifier.align(Alignment.TopCenter).padding(top = insetTop + 60.dp))
    }
}

@Composable
private fun TrailReplyRow(
    reply: TrailComment,
    isAuthor: Boolean,
    canDelete: Boolean,
    insetBottom: Dp,
    onAuthor: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .pointerInput(canDelete) { detectTapGestures(onLongPress = { if (canDelete) menu = true }) }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAuthor)) {
            WyrmAvatar(reply.author.avatarUrl, reply.author.avatarKey, reply.author.initials, 30.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(reply.author.name, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Wyrm.Ink)
                if (isAuthor) {
                    Text("AUTHOR", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 8.5.sp, letterSpacing = 0.8.sp,
                        color = Wyrm.Live, modifier = Modifier.clip(CircleShape).background(Wyrm.Live.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(trailTime(reply.createdAt), fontFamily = Wyrm.Body, fontSize = 10.5.sp, color = Wyrm.Quiet)
            }
            Text(reply.body, fontFamily = Wyrm.Body, fontSize = 13.5.sp, lineHeight = 19.sp, color = Wyrm.Ink)
        }
    }
    if (menu) {
        IosActionSheet("Reply", listOf(IosSheetAction("Delete reply", destructive = true, onClick = onDelete)), insetBottom) { menu = false }
    }
}

// ------------------------------------------------------------- social tab

/** Trails at the top of Social: the newest photos, and one tap into the feed. */
@Composable
internal fun TrailsTeaser(onOpen: () -> Unit) {
    LaunchedEffect(Unit) { if (!TrailsStore.loaded) TrailsStore.refresh() }
    val trails = TrailsStore.trails
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            .fillMaxWidth()
            .clip(wyrmRounded(20.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Trails", fontFamily = Wyrm.Display, fontSize = 24.sp, color = Wyrm.Ink)
                Text(if (trails.isEmpty()) "Show off your skins, kills and best moments." else "New from the Wyrm community",
                    fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Mute)
            }
            IosIcon(IosGlyph.CHEVRON_RIGHT, Wyrm.Chevron, size = 13.dp, semibold = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in 0 until 4) {
                Box(Modifier.weight(1f).aspectRatio(1f).clip(wyrmRounded(12.dp)).background(Wyrm.Well), contentAlignment = Alignment.Center) {
                    val item = trails.getOrNull(index)
                    val thumb = item?.thumbUrl
                    when {
                        thumb != null -> TrailImage(thumb, thumb, 1f)
                        item != null -> Text(item.caption, fontFamily = Wyrm.Display, fontSize = 11.sp, color = Wyrm.Ink,
                            maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
                        index == 0 -> Text("+", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Wyrm.Quiet)
                    }
                }
            }
        }
    }
}
