package com.wyrm.omrajput.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import java.net.HttpURLConnection
import java.net.URL

/**
 * A player's face, wherever a player appears.
 *
 * One component for the three places that show one — profile, the board and the
 * edit screen — so a photograph, a drawn gradient and an initial never drift
 * apart between them. With no photograph it falls back to the gradient seal it
 * always drew, which is why nothing looks broken while an image is still on its
 * way.
 */
@Composable
fun WyrmAvatar(
    url: String,
    avatarKey: String,
    initial: String,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = size * 0.30f,
) {
    var image by remember(url) { mutableStateOf(AvatarImages.peek(url)) }

    LaunchedEffect(url) {
        image = if (url.isBlank()) null else AvatarImages.load(url)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(wyrmRounded(corner))
            .then(
                if (image == null) Modifier.background(avatarBrushFor(avatarKey))
                else Modifier.background(Wyrm.Carbon)
            )
            .border(1.dp, glassEdge(), wyrmRounded(corner)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initial.take(1).uppercase().ifEmpty { "W" },
                fontFamily = Wyrm.Display,
                fontSize = (size.value * 0.42f).sp,
                color = Wyrm.Black,
            )
        }
    }
}

/**
 * Fetches and remembers faces.
 *
 * Small enough to hold in memory and worth holding: a board scrolls past the
 * same fifty people repeatedly, and every avatar address carries the digest of
 * its own bytes, so a cached image can never be the wrong one — a new
 * photograph is a new address.
 */
object AvatarImages {
    // Every avatar on screen is at most 76 dp, so 256 px is already sharp.
    private const val MAX_PIXELS = 256

    /** Decoded faces, bounded by bytes rather than by count: a whole board fits. */
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<ImageBitmap?>>()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var diskDir: java.io.File? = null

    /** The photographs are also kept on disk, so a face is downloaded once. */
    fun init(context: android.content.Context) {
        if (diskDir == null) diskDir = java.io.File(context.cacheDir, "avatars").apply { mkdirs() }
    }

    private fun fileFor(url: String): java.io.File? = diskDir?.let {
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        java.io.File(it, digest.joinToString("") { b -> "%02x".format(b) })
    }

    /** Memory, then disk, then the network — and one download per address at a time. */
    suspend fun load(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        val job = inFlight.getOrPut(url) {
            scope.async {
                try {
                    val bytes = fileFor(url)?.takeIf { it.exists() }?.readBytes() ?: download(url)
                    bytes?.let { decode(it)?.asImageBitmap() }?.also { cache.put(url, it) }
                } finally {
                    inFlight.remove(url)
                }
            }
        }
        return job.await()
    }

    /** Puts faces on disk ahead of time (the account sync does this for the boards). */
    suspend fun prefetch(urls: Collection<String>) = kotlinx.coroutines.coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(6)
        urls.filter { it.isNotBlank() && fileFor(it)?.exists() != true }.distinct().map { url ->
            async(Dispatchers.IO) { semaphore.acquire(); try { download(url) } finally { semaphore.release() } }
        }.forEach { it.await() }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            // Without this zrok answers with its interstitial page, and
            // an HTML document decodes to no image at all.
            setRequestProperty("skip_zrok_interstitial", "1")
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { it.readBytes() }.also { bytes ->
                fileFor(url)?.let { file -> runCatching { file.writeBytes(bytes) } }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    fun peek(url: String): ImageBitmap? = if (url.isBlank()) null else cache.get(url)

    fun forget(url: String) {
        cache.remove(url)
        fileFor(url)?.delete()
    }

    private fun decode(bytes: ByteArray) = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_PIXELS * 2) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()
}

/** The drawn seal, shared by every screen that has no photograph to show. */
fun avatarBrushFor(key: String): Brush = when (key) {
    "pastel-mint" -> Brush.linearGradient(listOf(Color(0xFFCFF8E5), Color(0xFFD8EBFF)))
    "pastel-sky" -> Brush.linearGradient(listOf(Color(0xFFD8EBFF), Color(0xFFE8DDFC)))
    "pastel-peach" -> Brush.linearGradient(listOf(Color(0xFFFFDDD2), Color(0xFFFFF0B8)))
    "pastel-lilac" -> Brush.linearGradient(listOf(Color(0xFFE8DDFC), Color(0xFFFFDDD2)))
    "pastel-lemon" -> Brush.linearGradient(listOf(Color(0xFFFFF0B8), Color(0xFFCFF8E5)))
    else -> Brush.linearGradient(listOf(Wyrm.White, Color(0xFFA7A7A2)))
}
