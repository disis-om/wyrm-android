package com.wyrm.omrajput.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ReleaseMediaKind { IMAGE, VIDEO }

data class ReleaseMedia(
    val url: String,
    val kind: ReleaseMediaKind,
    val description: String,
)

data class WhatsNewState(
    val versionName: String,
    val versionCode: Int = 0,
    val automatic: Boolean = false,
    val newVersion: Boolean = true,
    val title: String = "Wyrm",
    val markdown: String = "",
    val loading: Boolean = true,
    val error: String = "",
)

/** Full-height release sheet. It has one deliberate exit at its foot. */
@Composable
fun WhatsNewSheet(
    state: WhatsNewState,
    insetTop: Dp,
    insetBottom: Dp,
    onRetry: () -> Unit,
    onClosing: () -> Unit,
    onAcknowledged: () -> Unit,
) {
    var arrived by remember { mutableStateOf(false) }
    var expandedMedia by remember { mutableStateOf<ReleaseMedia?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { arrived = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // The atmosphere stays fixed and materialises over Home. It must never
        // ride up with the physical sheet beneath it.
        AnimatedVisibility(
            visible = arrived,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xCC1E1C1A)))
        }

        AnimatedVisibility(
            visible = arrived,
            enter = slideInVertically(tween(360)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = insetTop + 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp)
                    .fillMaxHeight(0.96f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Wyrm.Paper),
            ) {
                Box(
                    Modifier
                        .padding(top = 9.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(wyrmRounded(99.dp))
                        .background(Wyrm.Chevron)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = if (state.newVersion) {
                            "WHAT'S NEW IN ${state.versionName}"
                        } else {
                            "WHAT'S IN ${state.versionName}"
                        },
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.92.sp,
                        color = Wyrm.Quiet,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = state.title,
                        fontFamily = Wyrm.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 33.sp,
                        letterSpacing = (-0.5).sp,
                        color = Wyrm.Ink,
                    )
                    Spacer(Modifier.height(18.dp))

                    when {
                        state.loading -> ReleaseNotesLoading()
                        state.error.isNotBlank() -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(wyrmRounded(14.dp))
                                    .background(Wyrm.Card)
                                    .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
                                    .padding(16.dp),
                            ) {
                                Text(
                                    "Couldn't load what's new",
                                    fontFamily = Wyrm.Body,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Wyrm.Ink,
                                )
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    state.error,
                                    fontFamily = Wyrm.Body,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = Wyrm.Mute,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            PaperOutlineButton(label = "Try again", onClick = onRetry)
                        }
                        else -> WyrmMarkdown(
                            source = state.markdown,
                            baseSize = 14.sp,
                            onMediaOpen = { expandedMedia = it },
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                }

                Box(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = insetBottom + 12.dp),
                ) {
                    PaperPrimaryButton(
                        label = when {
                            state.loading -> "Loading…"
                            state.error.isNotBlank() -> "Done"
                            else -> "Got it"
                        },
                        enabled = !state.loading,
                        onClick = {
                            onClosing()
                            arrived = false
                            scope.launch {
                                delay(320)
                                onAcknowledged()
                            }
                        },
                    )
                }
            }
        }

        expandedMedia?.let { media ->
            ExpandedReleaseMedia(media = media, onDismiss = { expandedMedia = null })
        }
    }
}

@Composable
private fun ReleaseNotesLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(wyrmRounded(14.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = Wyrm.Ink,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.height(13.dp))
        Text(
            text = "FETCHING THIS VERSION",
            fontFamily = Wyrm.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = Wyrm.Quiet,
        )
    }
}

/** Inline media keeps its natural shape; portrait work never swallows the page. */
@Composable
fun ReleaseMediaCard(media: ReleaseMedia, onOpen: () -> Unit) {
    when (media.kind) {
        ReleaseMediaKind.IMAGE -> ReleaseImageCard(media, onOpen)
        ReleaseMediaKind.VIDEO -> ReleaseVideoCard(media, onOpen)
    }
}

@Composable
private fun ReleaseImageCard(media: ReleaseMedia, onOpen: () -> Unit) {
    val bitmap by rememberReleaseBitmap(media.url)
    val portrait = bitmap?.let { it.height > it.width } == true
    val ratio = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (16f / 9f)
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val mediaWidth = if (portrait) minOf(maxWidth * 0.72f, 255.dp) else maxWidth
        Box(
            modifier = Modifier
                .width(mediaWidth)
                .heightIn(min = 160.dp, max = if (portrait) 340.dp else 310.dp)
                .aspectRatio(ratio)
                .clip(wyrmRounded(14.dp))
                .background(Wyrm.Well)
                .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = media.description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().blur(2.dp),
                )
            } else {
                CircularProgressIndicator(color = Wyrm.Ink, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
            MediaOpenPrompt(label = "TAP TO EXPAND")
        }
    }
}

@Composable
private fun ReleaseVideoCard(media: ReleaseMedia, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(wyrmRounded(14.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp))
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("▶", fontFamily = Wyrm.Body, fontSize = 36.sp, color = Wyrm.Ink)
        Spacer(Modifier.height(12.dp))
        MediaOpenPrompt(label = "TAP TO EXPAND")
    }
}

@Composable
private fun MediaOpenPrompt(label: String) {
    Text(
        label,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
        color = Wyrm.Ink,
        modifier = Modifier
            .clip(wyrmRounded(99.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(99.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun ExpandedReleaseMedia(media: ReleaseMedia, onDismiss: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 330f),
        label = "media-expand",
    )
    LaunchedEffect(Unit) { entered = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .scale(scale)
                .alpha(scale.coerceIn(0f, 1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (media.kind) {
                ReleaseMediaKind.IMAGE -> ExpandedImage(media)
                ReleaseMediaKind.VIDEO -> WyrmVideoPlayer(media.url)
            }
        }
    }
}

@Composable
private fun ExpandedImage(media: ReleaseMedia) {
    val bitmap by rememberReleaseBitmap(media.url)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 680.dp)
            .clip(wyrmRounded(14.dp))
            .background(Wyrm.Card),
        contentAlignment = Alignment.Center,
    ) {
            if (bitmap == null) {
                CircularProgressIndicator(color = Wyrm.Ink, strokeWidth = 2.dp)
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = media.description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
    }
}

@Composable
private fun WyrmVideoPlayer(url: String) {
    val context = LocalContext.current
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var duration by remember(url) { mutableIntStateOf(0) }
    var position by remember(url) { mutableIntStateOf(0) }
    var playing by remember(url) { mutableStateOf(false) }
    var muted by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf("") }

    DisposableEffect(url) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(player, playing) {
        while (player != null) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            playing = runCatching { player?.isPlaying == true }.getOrDefault(false)
            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 680.dp)
            .clip(wyrmRounded(14.dp))
            .background(Wyrm.Card)
            .border(1.dp, Wyrm.Rule, wyrmRounded(14.dp)),
    ) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                if (error.isNotBlank()) {
                    Text(error, fontFamily = Wyrm.Body, fontSize = 12.sp, color = Wyrm.Mute)
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            TextureView(viewContext).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                        if (!trustedReleaseMediaUrl(url)) {
                                            error = "This video address is not allowed."
                                            return
                                        }
                                        val created = MediaPlayer()
                                        player = created
                                        created.setSurface(Surface(texture))
                                        created.setDataSource(
                                            context,
                                            Uri.parse(url),
                                            mapOf("User-Agent" to "Wyrm-Android-Release-Media/1.0"),
                                        )
                                        created.setOnPreparedListener {
                                            duration = it.duration.coerceAtLeast(0)
                                            it.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                                        }
                                        created.setOnCompletionListener { playing = false; position = duration }
                                        created.setOnErrorListener { _, _, _ ->
                                            error = "This video could not be played."
                                            true
                                        }
                                        created.prepareAsync()
                                    }

                                    override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
                                    override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
                                    override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                                        player?.release()
                                        player = null
                                        return true
                                    }
                                }
                            }
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VideoTextControl(if (playing) "Ⅱ" else "▶") {
                    player?.let {
                        if (runCatching { it.isPlaying }.getOrDefault(false)) it.pause() else it.start()
                        playing = runCatching { it.isPlaying }.getOrDefault(false)
                    }
                }
                LiquidSlider(
                    value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                    onValueChange = { position = it.toInt() },
                    onValueChangeFinished = { player?.seekTo(position) },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f),
                )
                VideoTextControl(if (muted) "MUTE" else "SOUND") {
                    muted = !muted
                    player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                }
            }
    }
}

@Composable
private fun VideoTextControl(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = Wyrm.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        color = Wyrm.Ink,
        modifier = Modifier
            .clip(wyrmRounded(99.dp))
            .background(Wyrm.Well)
            .border(1.dp, Wyrm.Rule, wyrmRounded(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun rememberReleaseBitmap(url: String) = produceState<Bitmap?>(initialValue = IMAGE_CACHE[url], url) {
    value = IMAGE_CACHE[url] ?: withContext(Dispatchers.IO) { loadReleaseBitmap(url) }
        ?.also { IMAGE_CACHE[url] = it }
}

private fun loadReleaseBitmap(address: String): Bitmap? {
    if (!trustedReleaseMediaUrl(address)) return null
    var current = URL(address)
    repeat(5) {
        if (!trustedReleaseMediaUrl(current.toString())) return null
        val connection = (current.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Wyrm-Android-Release-Media/1.0")
            setRequestProperty("Accept", "image/*")
        }
        try {
            val status = connection.responseCode
            if (status in 300..399) {
                val next = connection.getHeaderField("Location") ?: return null
                current = URL(current, next)
                return@repeat
            }
            if (status != HttpURLConnection.HTTP_OK) return null
            val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            val typedImage = type.startsWith("image/")
            val genericReleaseAsset = type == "application/octet-stream" && imageReleaseUrl(address)
            if (!typedImage && !genericReleaseAsset) return null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
            val bytes = output.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            var largest = maxOf(bounds.outWidth, bounds.outHeight)
            while (largest / options.inSampleSize > MAX_IMAGE_EDGE) options.inSampleSize *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            connection.disconnect()
        }
    }
    return null
}

internal fun trustedReleaseMediaUrl(address: String): Boolean = runCatching {
    val url = URL(address)
    val host = url.host.lowercase()
    url.protocol.equals("https", true) && url.userInfo == null && url.port == -1 &&
        host in TRUSTED_MEDIA_HOSTS
}.getOrDefault(false)

internal fun videoReleaseUrl(address: String): Boolean {
    val path = runCatching { URL(address).path.lowercase() }.getOrDefault("")
    return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov") ||
        runCatching { URL(address).host.equals("github.com", true) && path.startsWith("/user-attachments/") }
            .getOrDefault(false)
}

private fun imageReleaseUrl(address: String): Boolean {
    val path = runCatching { URL(address).path.lowercase() }.getOrDefault("")
    return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
        path.endsWith(".webp") || path.endsWith(".gif")
}

private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
private const val MAX_IMAGE_EDGE = 4096
private val IMAGE_CACHE = object : LinkedHashMap<String, Bitmap>(12, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 12
}
private val TRUSTED_MEDIA_HOSTS = setOf(
    "github.com",
    "raw.githubusercontent.com",
    "user-images.githubusercontent.com",
    "user-attachments.githubusercontent.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "github-releases.githubusercontent.com",
)
