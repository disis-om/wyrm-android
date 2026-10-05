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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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
 *
 * Snapchat-style tools (OM, 2026-10-05): looks (`TrailLooks`) swiped across
 * the picture or picked in the Looks panel with Adjust sliders, emoji
 * stickers, and text in Clean / Serif, Fill, Glow and Outline. The Video page
 * (`TrailVideo.kt`) edits a clip with this same editor over the playing clip:
 * the canvas is then transparent and exported as one overlay bitmap.
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
    /** Snapchat-style looks for words (OM, 2026-10-05): a neon glow, and a dark outline. */
    val glow: Boolean = false,
    val outline: Boolean = false,
)

/** An emoji sticker (OM, 2026-10-05): dragged, pinched, turned and binned like text. */
internal data class StudioEmoji(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val center: Offset,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

/** The sticker sheet: Wyrm's moments first. */
internal val STUDIO_EMOJIS = listOf(
    "🐍", "🔥", "👑", "🏆", "💀", "⚡", "💯", "🎯", "😂", "😍", "😎", "🤯",
    "😈", "😭", "🥶", "🥇", "✨", "💥", "❤️", "⭐", "🌈", "🎮", "🕹️", "👀",
    "🙌", "🤝", "🫡", "🙏", "💪", "🎉", "🍕", "🌙",
)

internal object StudioEmojiPainter {
    fun size(type: StudioType) = 72f * type.density

    /** Paints the sticker centred on the canvas origin. */
    fun draw(canvas: android.graphics.Canvas, item: StudioEmoji, type: StudioType) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size(type)
        }
        val metrics = paint.fontMetrics
        canvas.drawText(item.emoji, 0f, -(metrics.ascent + metrics.descent) / 2f, paint)
    }
}

internal enum class StudioAspect(val label: String) { ORIGINAL("Original"), FREE("Free"), SQUARE("1:1"), PORTRAIT("4:5"), WIDE("16:9") }

internal enum class StudioMode(val label: String) { PHOTO("Photo"), VIDEO("Video"), TEXT("Text"), CANVAS("Canvas") }

/*
 * "Share this run" (OM, 2026-09-30): the lobby's Share run opens this studio
 * as a Share editor. The background is a colour (Skin) or the arena as it
 * stood at death (Screenshot); on top sit the skin sticker, the stats box and
 * the studio's own text and drawing. Wyrm iOS: `WyrmTrailStudio.swift`.
 *
 * "Share this skin" (OM, 2026-09-30): the Skin tab opens the same editor with
 * no run: only the skin sticker on a colour, no stats and no screenshot.
 */

/** The last finished run. Memory only: replaced by the next death, cleared when a run starts. */
internal data class LastRun(
    val score: Int,
    val kills: Int,
    val seconds: Double,
    val endedAt: Long,
    /** The arena at death (long side at most 1440 px), when it could be read. */
    val screenshot: Bitmap? = null,
)

/** What the Share editor starts from: the run (null for "Share this skin"), and the skin and look the player wears. */
/** [tagId] is the worn tag's index in [SkinCatalog.tags], -1 for none (OM, 2026-10-04). */
internal data class ShareRunInput(val run: LastRun?, val skin: SkinState, val look: WyrmLookSpec, val tagId: Int = -1)

internal enum class ShareLayer { SKIN, SCREENSHOT }

internal enum class StickerKind { SKIN, STATS }

/** The skin sticker or the stats box: dragged, pinched and turned like text. */
internal data class StudioSticker(
    val id: String = UUID.randomUUID().toString(),
    val kind: StickerKind,
    val center: Offset,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

internal enum class StatsStyle(val label: String) {
    PAPER("Paper card"), INK("Ink card"), GLASS("Glass"), NEON("Neon"), LINE("Minimal line"), BIG("Big number"),
}

/** The worn skin and look as the shared-skin JSON ("Share my skin"), the shape Wyrm iOS sends too. */
internal fun SkinState.toTrailSkin(look: WyrmLookSpec, tagNtlId: Int = -1): com.wyrm.omrajput.data.TrailSkin {
    val letters = code.take(256)
    return com.wyrm.omrajput.data.TrailSkin(
        custom = custom && letters.isNotEmpty(),
        preset = preset.coerceIn(0, 255),
        code = letters,
        colours = coloursFor(letters.length).toList(),
        accessory = accessory.coerceIn(-1, 255),
        look = com.wyrm.omrajput.data.TrailSkinLook(look.hair, look.hairTone, look.ears, look.glasses),
        tag = tagNtlId,
    )
}

/** A shared skin as a Skin-tab draft: only code letters, presets and accessories this app has. */
internal fun com.wyrm.omrajput.data.TrailSkin.toSkinState(): SkinState {
    // Colours pair with code positions, so a letter that is dropped takes its colour with it.
    val kept = code.indices.filter { SkinCatalog.group(code[it]) != null }.take(256)
    return SkinState(
        custom = custom && kept.isNotEmpty(),
        preset = preset.takeIf { it in SkinCatalog.presets.indices } ?: 0,
        code = kept.map { code[it] }.joinToString(""),
        accessory = accessory.takeIf { it in SkinCatalog.accessories.indices } ?: -1,
        colours = IntArray(kept.size) { colours.getOrElse(kept[it]) { 0 } },
    )
}

/** A shared skin's Wyrm look; the hair colour is the same slider position on both apps. */
internal fun com.wyrm.omrajput.data.TrailSkin.lookSpec(): WyrmLookSpec =
    WyrmLookSpec(look.hair, look.hairTone, look.ears, look.glasses).checked()

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
    /** [stroke]: the outline pass, drawn under the letters. */
    private fun layout(item: StudioText, type: StudioType, stroke: Boolean = false): StaticLayout {
        val ink = StudioPalette.argb(if (item.filled) StudioPalette.contrast(item.rgb) else item.rgb)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = type.face(item.serif)
            textSize = type.baseSize
            color = ink
            when {
                stroke -> {
                    style = Paint.Style.STROKE
                    strokeWidth = 5f * type.density
                    strokeJoin = Paint.Join.ROUND
                    color = StudioPalette.argb(StudioPalette.contrast(item.rgb))
                }
                item.glow -> setShadowLayer(14f * type.density, 0f, 0f, ink)
                !item.filled -> setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(90, 0, 0, 0))
            }
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
        if (item.outline && !item.filled) layout(item, type, stroke = true).draw(canvas)
        if (item.glow) l.draw(canvas) // twice: a neon glow reads stronger
        l.draw(canvas)
        canvas.restore()
    }
}

internal object StudioInk {
    /** [multiply]: the marker darkens what is under it; off on a video's transparent canvas, where it would vanish. */
    fun draw(canvas: android.graphics.Canvas, strokes: List<StudioStroke>, multiply: Boolean = true) { for (s in strokes) draw(canvas, s, multiply) }

    fun draw(canvas: android.graphics.Canvas, stroke: StudioStroke, multiply: Boolean = true) {
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
                    if (marker) { alpha = 115; if (multiply) xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
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

/** The stats box: SCORE, KILLS and TIME in one of [StatsStyle], centred on the canvas origin. */
internal object StudioStatsPainter {
    fun time(seconds: Double): String {
        val whole = if (seconds.isFinite()) seconds.coerceAtLeast(0.0).toLong() else 0L
        return "%d:%02d".format(whole / 60, whole % 60)
    }

    private fun number(value: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(value)

    /** The box's own size in canvas pixels, before its scale and rotation. */
    fun size(style: StatsStyle, type: StudioType): Pair<Float, Float> {
        val d = type.density
        return when (style) {
            StatsStyle.BIG -> 210f * d to 132f * d
            StatsStyle.LINE -> 240f * d to 64f * d
            else -> 240f * d to 78f * d
        }
    }

    private fun text(type: StudioType, serif: Boolean, size: Float, argb: Int, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = type.face(serif)
            textSize = size
            color = argb
            textAlign = Paint.Align.CENTER
            letterSpacing = spacing
        }

    /** [tone] is the ink for the unboxed styles: dark on a light background, white on dark or on the screenshot. */
    fun draw(canvas: android.graphics.Canvas, run: LastRun, preset: StatsStyle, type: StudioType, tone: Int) {
        val d = type.density
        val (w, h) = size(preset, type)
        val ink = StudioPalette.argb(tone)
        fun faded(argb: Int, a: Float) = (((argb ushr 24) * a).roundToInt().coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)
        val soft = if (tone == 0xFFFFFF) android.graphics.Color.argb(110, 0, 0, 0) else 0
        if (preset == StatsStyle.BIG) {
            val label = text(type, false, 10f * d, faded(ink, 0.72f), 0.14f)
            val value = text(type, true, 62f * d, ink)
            val line = text(type, false, 12.5f * d, faded(ink, 0.86f), 0.04f)
            if (soft != 0) listOf(label, value, line).forEach { it.setShadowLayer(4f * d, 0f, 1f * d, soft) }
            canvas.drawText("SCORE", 0f, -h / 2 + 16f * d, label)
            val score = number(run.score)
            // A long score shrinks to fit rather than running off the box.
            value.textSize = min(62f * d, 62f * d * w * 0.96f / max(1f, value.measureText(score)))
            canvas.drawText(score, 0f, 22f * d, value)
            canvas.drawText("${number(run.kills)} KILLS  ·  ${time(run.seconds)}", 0f, h / 2 - 8f * d, line)
            return
        }
        val box = RectF(-w / 2, -h / 2, w / 2, h / 2)
        val radius = 18f * d
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
        var labelArgb = faded(ink, 0.7f)
        var valueArgb = ink
        var rule = faded(ink, 0.35f)
        var glow = 0
        when (preset) {
            StatsStyle.PAPER -> {
                fill.color = 0xFFF7F3EA.toInt(); canvas.drawRoundRect(box, radius, radius, fill)
                edge.color = 0x24111111; canvas.drawRoundRect(box, radius, radius, edge)
                labelArgb = 0xFF8C8778.toInt(); valueArgb = 0xFF151515.toInt(); rule = 0x1F111111
            }
            StatsStyle.INK -> {
                fill.color = 0xFF141414.toInt(); canvas.drawRoundRect(box, radius, radius, fill)
                labelArgb = 0x99FFFFFF.toInt(); valueArgb = 0xFFFFFFFF.toInt(); rule = 0x2EFFFFFF
            }
            StatsStyle.GLASS -> {
                fill.color = 0x3DFFFFFF; canvas.drawRoundRect(box, radius, radius, fill)
                edge.color = 0x99FFFFFF.toInt(); canvas.drawRoundRect(box, radius, radius, edge)
                labelArgb = 0xD9FFFFFF.toInt(); valueArgb = 0xFFFFFFFF.toInt(); rule = 0x4DFFFFFF
                glow = android.graphics.Color.argb(90, 0, 0, 0)
            }
            StatsStyle.NEON -> {
                val neon = 0xFF39FF88.toInt()
                fill.color = 0xE60B0D17.toInt(); canvas.drawRoundRect(box, radius, radius, fill)
                edge.color = neon; edge.strokeWidth = 2f * d; edge.setShadowLayer(8f * d, 0f, 0f, neon)
                canvas.drawRoundRect(box, radius, radius, edge)
                labelArgb = 0xCC7CFFC0.toInt(); valueArgb = neon; rule = 0x4039FF88
                glow = neon
            }
            StatsStyle.LINE -> {
                // No box: hairlines above and below, the ink chosen for the background.
                val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = faded(ink, 0.55f); strokeWidth = 1f * d }
                canvas.drawLine(-w / 2, -h / 2, w / 2, -h / 2, hair)
                canvas.drawLine(-w / 2, h / 2, w / 2, h / 2, hair)
                glow = soft
            }
            StatsStyle.BIG -> Unit
        }
        val label = text(type, false, 9.5f * d, labelArgb, 0.14f)
        val value = text(type, false, 23f * d, valueArgb)
        if (glow != 0) {
            value.setShadowLayer(if (preset == StatsStyle.NEON) 10f * d else 3f * d, 0f, if (preset == StatsStyle.NEON) 0f else 1f * d, glow)
            if (preset != StatsStyle.NEON) label.setShadowLayer(3f * d, 0f, 1f * d, glow)
        }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule; strokeWidth = 1f * d }
        val cells = listOf("SCORE" to number(run.score), "KILLS" to number(run.kills), "TIME" to time(run.seconds))
        cells.forEachIndexed { index, (name, figure) ->
            val cx = -w / 2 + w * (index + 0.5f) / 3f
            canvas.drawText(name, cx, -h / 2 + h * 0.38f, label)
            value.textSize = 23f * d
            value.textSize = min(23f * d, 23f * d * (w / 3f) * 0.9f / max(1f, value.measureText(figure)))
            canvas.drawText(figure, cx, h / 2 - h * 0.24f, value)
            if (index > 0) {
                val x = -w / 2 + w * index / 3f
                canvas.drawLine(x, -h / 2 + h * 0.2f, x, h / 2 - h * 0.2f, divider)
            }
        }
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
    /** The photo's look and adjustments; on a video, the clip's (OM, 2026-10-05). */
    var look by mutableStateOf(TrailLooks.all.first())
    var adjust by mutableStateOf(TrailAdjust())
    /** A small picture for the Looks panel's swatches. */
    var lookSample by mutableStateOf<Bitmap?>(null)
    val emojis = mutableStateListOf<StudioEmoji>()
    /** A clip's shape while the Video page edits it; null for every other trail. */
    var videoAspect by mutableStateOf<Float?>(null)
    val video: Boolean get() = videoAspect != null

    // "Share run": null for every other trail.
    var share by mutableStateOf<ShareRunInput?>(null)
    var shareLayer by mutableStateOf(ShareLayer.SKIN)
    /** The screenshot's zoom over "covers the canvas" (1), and its pan. */
    var shotScale by mutableFloatStateOf(1f)
    var shotOffset by mutableStateOf(Offset.Zero)
    val stickers = mutableStateListOf<StudioSticker>()
    var statsStyle by mutableStateOf(StatsStyle.PAPER)
    /** The Skin screen's textures, for the skin sticker. */
    var textures by mutableStateOf<SkinTextures?>(null)
    private var statsSeeded = false
    private var skinSeeded = false
    private val stickerScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()

    /** The screenshot, while it is the background layer. */
    val shot: Bitmap?
        get() = share?.run?.screenshot?.takeIf { shareLayer == ShareLayer.SCREENSHOT }

    val ratio: Float
        get() = videoAspect ?: when (aspect) {
            StudioAspect.FREE -> freeRatio
            StudioAspect.SQUARE -> 1f
            StudioAspect.PORTRAIT -> 0.8f
            StudioAspect.WIDE -> 16f / 9f
            StudioAspect.ORIGINAL -> (image ?: shot)?.takeIf { it.height > 0 }?.let { (it.width.toFloat() / it.height).coerceIn(0.8f, 1.91f) } ?: 0.8f
        }

    fun reset(next: StudioMode) {
        mode = next
        image = null
        aspect = if (next == StudioMode.CANVAS) StudioAspect.PORTRAIT else StudioAspect.ORIGINAL
        photoScale = 1f
        photoOffset = Offset.Zero
        strokes.clear()
        texts.clear()
        stickers.clear()
        emojis.clear()
        look = TrailLooks.all.first()
        adjust = TrailAdjust()
        lookSample = null
        videoAspect = null
        ink++
    }

    /** A Share editor: 4:5, the screenshot behind if there is one, else the theme's paper. */
    fun startShare(input: ShareRunInput, paper: Int) {
        reset(StudioMode.CANVAS)
        share = input
        shareLayer = if (input.run?.screenshot != null) ShareLayer.SCREENSHOT else ShareLayer.SKIN
        background = paper
        statsSeeded = false
        skinSeeded = false
    }

    fun showLayer(layer: ShareLayer) {
        shareLayer = layer
        shotScale = 1f
        shotOffset = Offset.Zero
    }

    /** Where the screenshot sits in a canvas of [w] x [h]: covering it, then the player's zoom and pan. */
    fun shotRect(w: Float, h: Float): RectF {
        val bitmap = shot ?: return RectF(0f, 0f, w, h)
        val cover = max(w / bitmap.width, h / bitmap.height)
        val sw = bitmap.width * cover * shotScale
        val sh = bitmap.height * cover * shotScale
        val left = (w - sw) / 2 + shotOffset.x
        val top = (h - sh) / 2 + shotOffset.y
        return RectF(left, top, left + sw, top + sh)
    }

    /** Pinch and drag on the screenshot: it may shrink past fitting (the colour shows around it), never leave. */
    fun moveShot(pan: Offset, zoom: Float, w: Float, h: Float) {
        val bitmap = shot ?: return
        val fit = min(w / bitmap.width, h / bitmap.height) / max(w / bitmap.width, h / bitmap.height)
        shotScale = (shotScale * zoom).coerceIn(fit * 0.6f, 5f)
        shotOffset = Offset((shotOffset.x + pan.x).coerceIn(-w / 2, w / 2), (shotOffset.y + pan.y).coerceIn(-h / 2, h / 2))
    }

    /** The skin sticker is drawn with beads this wide (canvas pixels), before its own scale. */
    private fun stickerBead(type: StudioType) = 20f * type.density

    /** A sticker's own size in canvas pixels, before its scale and rotation. */
    fun stickerSize(item: StudioSticker, type: StudioType): Pair<Float, Float> = when (item.kind) {
        StickerKind.SKIN -> skinStickerSize(stickerBead(type)).let { it.width to it.height }
        StickerKind.STATS -> StudioStatsPainter.size(statsStyle, type)
    }

    /** Adds the skin sticker or the stats box (once each), sized to the canvas. */
    fun addSticker(kind: StickerKind, w: Float, h: Float, type: StudioType) {
        if (share == null || w <= 0f || h <= 0f || stickers.any { it.kind == kind }) return
        if (kind == StickerKind.STATS && share?.run == null) return
        val (sw, _) = stickerSize(StudioSticker(kind = kind, center = Offset.Zero), type)
        val scale = ((if (kind == StickerKind.SKIN) 0.8f else 0.74f) * w / max(sw, 1f)).coerceIn(0.3f, 5f)
        val y = when {
            kind == StickerKind.SKIN -> h * 0.42f
            shareLayer == ShareLayer.SCREENSHOT -> h * 0.84f
            else -> h * 0.78f
        }
        stickers += StudioSticker(kind = kind, center = Offset(w / 2, y), scale = scale)
    }

    /**
     * The stats box always comes first (a run only); the skin sticker the
     * first time the Skin layer shows. Afterwards only keeps everything on a reshaped canvas.
     */
    fun seedShare(w: Float, h: Float, type: StudioType) {
        if (share == null || w <= 0f || h <= 0f) return
        if (!statsSeeded) { statsSeeded = true; addSticker(StickerKind.STATS, w, h, type) }
        if (shareLayer == ShareLayer.SKIN && !skinSeeded) { skinSeeded = true; addSticker(StickerKind.SKIN, w, h, type) }
        for (i in stickers.indices) {
            val c = stickers[i].center
            if (c.x !in 0f..w || c.y !in 0f..h) stickers[i] = stickers[i].copy(center = Offset(c.x.coerceIn(0f, w), c.y.coerceIn(0f, h)))
        }
        for (i in texts.indices) {
            val c = texts[i].center
            if (c.x !in 0f..w || c.y !in 0f..h) texts[i] = texts[i].copy(center = Offset(c.x.coerceIn(0f, w), c.y.coerceIn(0f, h)))
        }
    }

    /** Ink for words and unboxed stats: dark on a light colour, white on dark or on the screenshot. */
    val tone: Int
        get() = if (image != null || shot != null) 0xFFFFFF else StudioPalette.contrast(background)

    private fun paintStickers(canvas: android.graphics.Canvas, w: Float, h: Float, type: StudioType) {
        val input = share ?: return
        for (item in stickers) {
            canvas.save()
            canvas.translate(item.center.x, item.center.y)
            canvas.rotate(item.rotation)
            canvas.scale(item.scale, item.scale)
            when (item.kind) {
                StickerKind.STATS -> input.run?.let { StudioStatsPainter.draw(canvas, it, statsStyle, type, tone) }
                StickerKind.SKIN -> textures?.let { t ->
                    // The Skin screen's own drawing code, through Compose, onto this canvas.
                    stickerScope.draw(
                        androidx.compose.ui.unit.Density(type.density),
                        androidx.compose.ui.unit.LayoutDirection.Ltr,
                        androidx.compose.ui.graphics.Canvas(canvas),
                        androidx.compose.ui.geometry.Size(w, h),
                    ) { drawSkinSticker(t, input.skin, input.look, stickerBead(type), input.tagId) }
                }
            }
            canvas.restore()
        }
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
        if (video) {
            // The clip plays under the canvas; only what is on top is painted.
        } else if (bitmap != null) {
            canvas.drawColor(android.graphics.Color.BLACK)
            val looked = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                if (!TrailLooks.isIdentity(look, adjust)) colorFilter = TrailLooks.colorFilter(look, adjust)
            }
            canvas.drawBitmap(bitmap, null, photoRect(w, h), looked)
        } else {
            canvas.drawColor(StudioPalette.argb(background))
            // A shared run's screenshot: over the colour, clipped to the canvas.
            shot?.let { screenshot ->
                canvas.save()
                canvas.clipRect(0f, 0f, w, h)
                canvas.drawBitmap(screenshot, null, shotRect(w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
            }
        }
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        StudioInk.draw(canvas, strokes, multiply = !video)
        live?.let { StudioInk.draw(canvas, it, multiply = !video) }
        canvas.restoreToCount(layer)
        for (item in emojis) {
            canvas.save()
            canvas.translate(item.center.x, item.center.y)
            canvas.rotate(item.rotation)
            canvas.scale(item.scale, item.scale)
            StudioEmojiPainter.draw(canvas, item, type)
            canvas.restore()
        }
        paintStickers(canvas, w, h, type)
        for (item in texts) {
            canvas.save()
            canvas.translate(item.center.x, item.center.y)
            canvas.rotate(item.rotation)
            canvas.scale(item.scale, item.scale)
            StudioTextPainter.draw(canvas, item, type)
            canvas.restore()
        }
    }

    /**
     * A video's overlay (OM, 2026-10-05): everything drawn on top, on a clear
     * bitmap the size of the exported frame; null when nothing is on top.
     */
    fun renderOverlay(outW: Int, outH: Int, w: Float, h: Float, type: StudioType): Bitmap? {
        if (strokes.isEmpty() && texts.isEmpty() && emojis.isEmpty() && stickers.isEmpty()) return null
        if (w <= 0f || h <= 0f) return null
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.scale(outW / w, outH / h)
        paint(canvas, w, h, type)
        return out
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

/** What the editor is doing: nothing (move and pinch), writing, drawing or cropping. */
private enum class EditorTool { NONE, TEXT, DRAW, CROP, EMOJI, LOOKS }

/**
 * The studio. With [share] it is the Share editor for the last run: it opens
 * straight on the canvas, closes with [onClose] and, once posted, leaves
 * through [onPosted].
 */
@Composable
internal fun TrailStudioScreen(
    insetTop: Dp,
    insetBottom: Dp,
    onClose: () -> Unit,
    share: ShareRunInput? = null,
    onPosted: () -> Unit = onClose,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val paper = Wyrm.Paper.toArgb() and 0xFFFFFF
    val draft = remember { StudioDraft().also { d -> share?.let { d.startShare(it, paper) } } }
    val type = remember(density) { StudioType(context, density) }
    var step by remember { mutableStateOf(if (share != null) StudioStep.EDIT else StudioStep.PICK) }
    var canvasPx by remember { mutableStateOf(0f to 0f) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var loadingPhoto by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    /** "Share my skin": on unless the player turns it off (OM). */
    var shareSkin by remember { mutableStateOf(true) }
    val textures by rememberSkinTextures()
    val sharing = share != null
    val skinOnly = share != null && share.run == null
    /** The clip being edited on the Video page (OM, 2026-10-05). */
    var videoSession by remember { mutableStateOf<TrailVideoSession?>(null) }
    var openingClip by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { videoSession?.release() } }
    // The player previews the look the export will burn in.
    LaunchedEffect(videoSession, draft.look, draft.adjust) {
        videoSession?.applyEffects(TrailLooks.videoEffects(draft.look, draft.adjust))
    }
    // It plays while being edited and rests on the caption.
    LaunchedEffect(step, videoSession) {
        videoSession?.player?.let { if (step == StudioStep.EDIT) it.play() else it.pause() }
    }

    LaunchedEffect(Unit) { TrailsStore.resetPosting() }
    // The worn skin can arrive after the editor opens; the sticker follows it.
    LaunchedEffect(share) { if (share != null) draft.share = share }
    LaunchedEffect(textures) { draft.textures = textures }

    fun open(bitmap: Bitmap) {
        draft.reset(StudioMode.PHOTO)
        draft.image = bitmap
        step = StudioStep.EDIT
    }

    fun openUri(uri: Uri) {
        loadingPhoto = true
        scope.launch {
            withContext(Dispatchers.IO) { loadPhoto(context, uri) }?.let { open(it) }
            loadingPhoto = false
        }
    }

    /** A recorded or picked clip into the editor; longer than 30 s, it starts trimmed to its first 30. */
    fun openClip(uri: Uri) {
        if (openingClip) return
        openingClip = true
        scope.launch {
            val clip = withContext(Dispatchers.IO) { TrailClips.probe(context, uri) }
            if (clip == null) {
                openingClip = false
                TrailsStore.toast = "That video could not be read."
                return@launch
            }
            val sample = withContext(Dispatchers.IO) { TrailClips.frame(context, clip, 0L, 240) }
            videoSession?.release()
            val session = TrailVideoSession(context, clip)
            draft.reset(StudioMode.VIDEO)
            draft.videoAspect = clip.aspect
            draft.lookSample = sample
            session.load()
            videoSession = session
            openingClip = false
            step = StudioStep.EDIT
        }
    }

    /** The video's poster: the cover frame with the look and everything drawn on top. */
    fun videoPoster(): Bitmap? {
        val session = videoSession ?: return null
        val (w, h) = canvasPx
        val filter = if (TrailLooks.isIdentity(draft.look, draft.adjust)) null else TrailLooks.colorFilter(draft.look, draft.adjust)
        return TrailVideoExport.poster(context, session.clip, session.coverMs, filter) { ow, oh -> draft.renderOverlay(ow, oh, w, h, type) }
    }

    fun postVideo() {
        val session = videoSession ?: return
        if (TrailsStore.posting.busy) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val (w, h) = canvasPx
        TrailsStore.postVideo(
            context = context.applicationContext,
            clip = session.clip,
            startMs = session.trimStart,
            endMs = session.trimEnd,
            muted = session.muted,
            effects = TrailLooks.videoEffects(draft.look, draft.adjust),
            overlay = { ow, oh -> draft.renderOverlay(ow, oh, w, h, type) },
            poster = rendered ?: videoPoster(),
            caption = draft.caption,
        )
        onClose()
    }

    val pickAll = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::openUri) }
    // The phone's own camera app, full screen; photos only for now.
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) openUri(Uri.fromFile(file))
    }
    fun nativeCamera() {
        val folder = java.io.File(context.cacheDir, "trails-camera").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val file = java.io.File(folder, "shot-${System.currentTimeMillis()}.jpg")
        cameraFile = file
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.trails", file)
        runCatching { takePicture.launch(uri) }
    }

    fun post(image: Bitmap?) {
        if (TrailsStore.posting.busy) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val input = draft.share
        if (sharing && input != null) {
            // A shared run says whether the skin goes with it; the skin only when it does.
            TrailsStore.post(image, draft.caption,
                if (shareSkin) input.skin.toTrailSkin(input.look, SkinCatalog.tags.getOrNull(input.tagId)?.ntlId ?: -1) else null,
                shareSkin)
            onPosted()
        } else {
            TrailsStore.post(image, draft.caption)
            onClose()
        }
    }

    fun back() {
        when (step) {
            StudioStep.PICK -> onClose()
            StudioStep.EDIT -> if (sharing) onClose() else {
                step = StudioStep.PICK
                if (draft.mode == StudioMode.PHOTO) draft.image = null
                if (draft.mode == StudioMode.VIDEO) {
                    videoSession?.release()
                    videoSession = null
                    draft.videoAspect = null
                }
            }
            StudioStep.CAPTION -> step = if (sharing) StudioStep.EDIT else if (draft.mode == StudioMode.CANVAS) StudioStep.PICK else StudioStep.EDIT
        }
    }

    fun forward() {
        when (step) {
            StudioStep.PICK -> when (draft.mode) {
                StudioMode.TEXT -> post(null)
                StudioMode.CANVAS -> { rendered = draft.render(canvasPx.first, canvasPx.second, type); step = StudioStep.CAPTION }
                StudioMode.PHOTO, StudioMode.VIDEO -> Unit
            }
            StudioStep.EDIT -> {
                rendered = if (draft.video) videoPoster() else draft.render(canvasPx.first, canvasPx.second, type)
                step = StudioStep.CAPTION
            }
            StudioStep.CAPTION -> if (draft.video) postVideo() else post(rendered ?: draft.render(canvasPx.first, canvasPx.second, type))
        }
    }

    val ready = when (step) {
        StudioStep.PICK -> if (draft.mode == StudioMode.TEXT) draft.caption.isNotBlank() else draft.mode == StudioMode.CANVAS
        StudioStep.EDIT -> true
        StudioStep.CAPTION -> !TrailsStore.posting.busy
    }
    val actionLabel = if (step == StudioStep.CAPTION || (step == StudioStep.PICK && draft.mode == StudioMode.TEXT)) "Post" else "Next"
    val title = when (step) {
        StudioStep.PICK -> "New trail"
        StudioStep.EDIT -> if (skinOnly) "Share skin" else if (sharing) "Share run" else if (draft.video) "Edit video" else "Edit"
        StudioStep.CAPTION -> "Caption"
    }

    Column(Modifier.fillMaxSize().background(Wyrm.Paper).padding(top = insetTop, bottom = insetBottom).imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Wyrm.Well)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { back() },
                contentAlignment = Alignment.Center,
            ) {
                if (step == StudioStep.PICK || (sharing && step == StudioStep.EDIT)) Text("✕", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
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
        // The mode pill sits here, in one place, for every mode; only the page
        // under it moves.
        if (step == StudioStep.PICK) {
            // Video stays out of the bar while `TRAIL_VIDEO_ENABLED` is off (OM, 2026-10-05).
            val shownModes = StudioMode.entries.filter { it != StudioMode.VIDEO || com.wyrm.omrajput.data.TRAIL_VIDEO_ENABLED }
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                PaperSegmented(
                    options = shownModes.map { it.label },
                    selected = shownModes.indexOf(draft.mode).coerceAtLeast(0),
                    onSelect = { index ->
                        val next = shownModes[index]
                        if (next != draft.mode) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            draft.reset(next)
                        }
                    },
                )
            }
        }
        // Share run: what is behind everything, the skin colour or the arena at death.
        if (sharing && !skinOnly && step == StudioStep.EDIT) {
            val hasShot = draft.share?.run?.screenshot != null
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                PaperSegmented(
                    options = listOf("Skin", "Screenshot"),
                    selected = draft.shareLayer.ordinal,
                    onSelect = { index ->
                        val layer = ShareLayer.entries[index]
                        if (layer != draft.shareLayer && (layer == ShareLayer.SKIN || hasShot)) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            draft.showLayer(layer)
                        }
                    },
                )
                if (!hasShot) {
                    Text("No screenshot for this run", fontFamily = Wyrm.Body, fontSize = 11.5.sp, color = Wyrm.Quiet,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val page = if (step == StudioStep.PICK) draft.mode.ordinal else 10 + step.ordinal
            androidx.compose.animation.AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState > initialState
                    (androidx.compose.animation.slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (forward) it / 4 else -it / 4 } +
                        androidx.compose.animation.fadeIn(tween(220))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(tween(240)) { if (forward) -it / 5 else it / 5 } +
                            androidx.compose.animation.fadeOut(tween(160)))
                },
                label = "studio page",
            ) { shown ->
                when (shown) {
                    StudioMode.PHOTO.ordinal -> PhotoPicker(loadingPhoto, onCaptured = { open(it) }, onPick = ::openUri,
                        onAll = { pickAll.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onNativeCamera = ::nativeCamera)
                    StudioMode.VIDEO.ordinal -> Box(Modifier.fillMaxSize()) {
                        TrailVideoPicker(onClip = ::openClip)
                        if (openingClip) {
                            Box(Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Wyrm.Card), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Wyrm.Ink, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    StudioMode.TEXT.ordinal -> TextComposer(draft)
                    10 + StudioStep.CAPTION.ordinal -> CaptionStep(draft, rendered, shareSkin.takeIf { sharing }) { shareSkin = it }
                    else -> {
                        val session = videoSession
                        if (draft.video && session != null) {
                            StudioEditor(
                                draft, type, { w, h -> canvasPx = w to h },
                                underlay = { TrailVideoPreview(session, Modifier.fillMaxSize()) },
                                bottom = { TrailVideoBar(session) },
                            )
                        } else {
                            StudioEditor(draft, type, { w, h -> canvasPx = w to h })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPicker(
    loading: Boolean,
    onCaptured: (Bitmap) -> Unit,
    onPick: (Uri) -> Unit,
    onAll: () -> Unit,
    onNativeCamera: () -> Unit,
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
        else if (!photosAllowed) askPhotos.launch(photoPermissions())
    }
    LaunchedEffect(cameraAsked) { if (cameraAsked && !photosAllowed) askPhotos.launch(photoPermissions()) }
    LaunchedEffect(photosAllowed) { if (photosAllowed) photos = withContext(Dispatchers.IO) { recentPhotos(context) } }

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
        onDispose { runCatching { provider?.unbind(*bound) } }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(0.8f).clip(wyrmRounded(22.dp)).background(Wyrm.Well)) {
            if (cameraAllowed) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                // Full screen: the phone's own camera app.
                StudioRoundButton("⤢", Modifier.align(Alignment.TopEnd).padding(12.dp), onClick = onNativeCamera)
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
                    StudioRoundButton("⟲", Modifier) { front = !front }
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camera is off for Wyrm", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
                    Text(if (cameraAsked) "Allow it in Settings, or use the phone's camera." else "Tap to allow the camera.",
                        fontFamily = Wyrm.Body, fontSize = 12.5.sp, color = Wyrm.Mute, textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { askCamera.launch(Manifest.permission.CAMERA) })
                    PaperOutlineButton(label = "Open camera", onClick = onNativeCamera)
                }
            }
        }
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

/** A text trail: the page opens with the keyboard up. */
@Composable
private fun TextComposer(draft: StudioDraft) {
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(320)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).padding(horizontal = 14.dp).fillMaxWidth().clip(wyrmRounded(22.dp)).background(Wyrm.Card)
                .border(1.dp, Wyrm.Rule, wyrmRounded(22.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    runCatching { focus.requestFocus() }; keyboard?.show()
                }
                .padding(18.dp),
        ) {
            if (draft.caption.isEmpty()) Text("Leave a thought…", fontFamily = Wyrm.Display, fontSize = 28.sp, color = Wyrm.Quiet)
            BasicTextField(
                value = draft.caption,
                onValueChange = { draft.caption = it.take(500) },
                textStyle = TextStyle(fontFamily = Wyrm.Display, fontSize = 28.sp, lineHeight = 36.sp, color = Wyrm.Ink),
                cursorBrush = SolidColor(Wyrm.Link),
                modifier = Modifier.fillMaxSize().focusRequester(focus),
            )
        }
        Text("${draft.caption.length}/500", fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

/** The caption; a shared run also asks whether its skin goes with it ([shareSkin], null otherwise). */
@Composable
private fun CaptionStep(draft: StudioDraft, rendered: Bitmap?, shareSkin: Boolean? = null, onShareSkin: (Boolean) -> Unit = {}) {
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(320); runCatching { focus.requestFocus() }; keyboard?.show() }
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
                    modifier = Modifier.fillMaxSize().focusRequester(focus),
                )
            }
        }
        Text("${draft.caption.length}/500", fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        if (shareSkin != null) {
            Spacer(Modifier.height(6.dp))
            SettingsCard {
                SettingsBoolRow(
                    title = "Share my skin",
                    detail = "Others can try your skin from this post. Turn it off to keep it to yourself.",
                    on = shareSkin,
                    first = true,
                    onToggle = onShareSkin,
                )
            }
        }
    }
}

// ----------------------------------------------------------------- editor

/**
 * The editor, story-style: the picture fills the page, tools stand in a rail
 * on its right edge (Aa, draw, crop, undo), and each tool takes the whole
 * screen while it is in use. Text is dragged, pinched and turned in place and
 * thrown into the bin at the bottom to delete it.
 */
@Composable
private fun StudioEditor(
    draft: StudioDraft,
    type: StudioType,
    onCanvas: (Float, Float) -> Unit,
    /** What plays under a video's transparent canvas (OM, 2026-10-05). */
    underlay: (@Composable () -> Unit)? = null,
    /** A video's own tools under the canvas: trim, cover, sound. */
    bottom: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var tool by remember { mutableStateOf(EditorTool.NONE) }
    /** The look's name, shown for a moment after a swipe. */
    var lookToast by remember { mutableStateOf("") }
    LaunchedEffect(lookToast) { if (lookToast.isNotEmpty()) { kotlinx.coroutines.delay(900); lookToast = "" } }
    var brush by remember { mutableStateOf(StudioBrush.BEADS) }
    var inkRgb by remember { mutableStateOf(0xF2B84B) }
    var live by remember { mutableStateOf<StudioStroke?>(null) }
    var liveTick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<StudioText?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var overBin by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sharing = draft.share != null
        val bottomBar = when {
            tool == EditorTool.LOOKS -> 168.dp
            bottom != null && tool == EditorTool.NONE -> 150.dp
            tool == EditorTool.CROP -> 96.dp
            sharing -> if (tool == EditorTool.NONE) 120.dp else 56.dp
            draft.image == null && !draft.video -> 96.dp
            else -> 56.dp
        }
        val widthPx = with(density) { (maxWidth - 24.dp).toPx() }
        val maxHeightPx = with(density) { (maxHeight - bottomBar - 8.dp).toPx() }.coerceAtLeast(200f)
        val h = min(widthPx / draft.ratio, maxHeightPx)
        val w = h * draft.ratio
        LaunchedEffect(w, h) { onCanvas(w, h) }
        // Share run: the stats box (and the skin sticker on the Skin layer) once
        // the canvas has a size; later only keeps everything inside a new shape.
        LaunchedEffect(w, h, draft.shareLayer) { draft.seedShare(w, h, type) }
        val binCenter = Offset(w / 2, h - 46 * density.density)

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                underlay?.let { playing -> Box(Modifier.fillMaxSize().clip(wyrmRounded(20.dp))) { playing() } }
                Canvas(
                    Modifier.fillMaxSize().clip(wyrmRounded(20.dp))
                        .pointerInput(tool, brush, inkRgb, w, h) {
                            when (tool) {
                                EditorTool.DRAW -> detectDragGestures(
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
                                EditorTool.CROP -> awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (draft.image != null) {
                                            draft.photoScale = (draft.photoScale * event.calculateZoom()).coerceIn(1f, 5f)
                                            draft.photoOffset += event.calculatePan()
                                            draft.clampOffset(w, h)
                                        } else if (draft.shot != null) {
                                            draft.moveShot(event.calculatePan(), event.calculateZoom(), w, h)
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                                else -> awaitEachGesture {
                                    // A text item under the first finger moves, grows and turns;
                                    // then a Share run sticker the same way; otherwise the
                                    // photo (or the run's screenshot) pans and zooms.
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val hit = draft.texts.indexOfLast { item ->
                                        val (tw, th) = StudioTextPainter.size(item, type)
                                        abs(down.position.x - item.center.x) < tw * item.scale / 2 + 16 &&
                                            abs(down.position.y - item.center.y) < th * item.scale / 2 + 16
                                    }
                                    val emojiHit = if (hit >= 0) -1 else draft.emojis.indexOfLast { item ->
                                        val half = StudioEmojiPainter.size(type) * item.scale / 2 + 16
                                        abs(down.position.x - item.center.x) < half && abs(down.position.y - item.center.y) < half
                                    }
                                    val stickerHit = if (hit >= 0 || emojiHit >= 0) -1 else draft.stickers.indexOfLast { item ->
                                        val (sw, sh) = draft.stickerSize(item, type)
                                        abs(down.position.x - item.center.x) < sw * item.scale / 2 + 16 &&
                                            abs(down.position.y - item.center.y) < sh * item.scale / 2 + 16
                                    }
                                    var moved = 0f
                                    var sweep = Offset.Zero
                                    var last = down.position
                                    val start = System.currentTimeMillis()
                                    fun towardBin() {
                                        if (moved > 12f) {
                                            dragging = true
                                            val near = (last - binCenter).getDistance() < 44 * density.density
                                            if (near != overBin) { overBin = near; if (near) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                                        }
                                    }
                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan()
                                        moved += pan.getDistance()
                                        sweep += pan
                                        event.changes.firstOrNull()?.let { last = it.position }
                                        if (hit >= 0 && hit < draft.texts.size) {
                                            val item = draft.texts[hit]
                                            draft.texts[hit] = item.copy(
                                                center = Offset((item.center.x + pan.x).coerceIn(0f, w), (item.center.y + pan.y).coerceIn(0f, h)),
                                                scale = (item.scale * event.calculateZoom()).coerceIn(0.4f, 5f),
                                                rotation = item.rotation + event.calculateRotation(),
                                            )
                                            towardBin()
                                        } else if (emojiHit >= 0 && emojiHit < draft.emojis.size) {
                                            val item = draft.emojis[emojiHit]
                                            draft.emojis[emojiHit] = item.copy(
                                                center = Offset((item.center.x + pan.x).coerceIn(0f, w), (item.center.y + pan.y).coerceIn(0f, h)),
                                                scale = (item.scale * event.calculateZoom()).coerceIn(0.3f, 6f),
                                                rotation = item.rotation + event.calculateRotation(),
                                            )
                                            towardBin()
                                        } else if (stickerHit >= 0 && stickerHit < draft.stickers.size) {
                                            val item = draft.stickers[stickerHit]
                                            draft.stickers[stickerHit] = item.copy(
                                                center = Offset((item.center.x + pan.x).coerceIn(0f, w), (item.center.y + pan.y).coerceIn(0f, h)),
                                                scale = (item.scale * event.calculateZoom()).coerceIn(0.3f, 5f),
                                                rotation = item.rotation + event.calculateRotation(),
                                            )
                                            towardBin()
                                        } else if (draft.image != null) {
                                            draft.photoScale = (draft.photoScale * event.calculateZoom()).coerceIn(1f, 5f)
                                            draft.photoOffset += pan
                                            draft.clampOffset(w, h)
                                        } else if (draft.shot != null) {
                                            draft.moveShot(pan, event.calculateZoom(), w, h)
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } while (event.changes.any { it.pressed })
                                    val tapped = moved < 12f && System.currentTimeMillis() - start < 320
                                    if (hit >= 0 && hit < draft.texts.size) {
                                        when {
                                            dragging && overBin -> draft.texts.removeAt(hit)
                                            tapped -> { editing = draft.texts[hit]; tool = EditorTool.TEXT }
                                        }
                                    } else if (emojiHit >= 0 && emojiHit < draft.emojis.size) {
                                        if (dragging && overBin) draft.emojis.removeAt(emojiHit)
                                    } else if (stickerHit >= 0 && stickerHit < draft.stickers.size) {
                                        val item = draft.stickers[stickerHit]
                                        when {
                                            dragging && overBin -> draft.stickers.removeAt(stickerHit)
                                            // A tap on the stats box shows its next style.
                                            tapped && item.kind == StickerKind.STATS -> {
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                draft.statsStyle = StatsStyle.entries[(draft.statsStyle.ordinal + 1) % StatsStyle.entries.size]
                                            }
                                        }
                                    }
                                    // Snapchat's swipe: across an empty picture, the next or the last look.
                                    val swiped = hit < 0 && emojiHit < 0 && stickerHit < 0 &&
                                        abs(sweep.x) > 72 * density.density && abs(sweep.y) < abs(sweep.x) * 0.6f &&
                                        (draft.video || draft.image != null) && draft.photoScale <= 1.01f
                                    if (swiped) {
                                        val looks = TrailLooks.all
                                        val i = looks.indexOf(draft.look).coerceAtLeast(0)
                                        val next = (i + if (sweep.x < 0) 1 else looks.size - 1) % looks.size
                                        draft.look = looks[next]
                                        lookToast = looks[next].name
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                    dragging = false
                                    overBin = false
                                }
                            }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val redraw = liveTick + draft.ink + draft.texts.size + draft.stickers.size +
                        draft.emojis.size + draft.look.hashCode() + draft.adjust.hashCode()
                    drawIntoCanvas { draft.paint(it.nativeCanvas, w, h, type, live) }
                    if (tool == EditorTool.CROP) {
                        // The rule of thirds while cropping.
                        val line = Color.White.copy(alpha = 0.55f)
                        for (i in 1..2) {
                            drawLine(line, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
                            drawLine(line, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
                        }
                    }
                }
                if (tool == EditorTool.CROP && draft.aspect == StudioAspect.FREE) FreeHandles(draft, w, h, widthPx, maxHeightPx)

                // The bin, while a text item or a sticker is being dragged.
                if (dragging) {
                    Box(
                        Modifier.offset { IntOffset((binCenter.x - 26 * density.density).roundToInt(), (binCenter.y - 26 * density.density).roundToInt()) }
                            .size(52.dp).scale(if (overBin) 1.25f else 1f).clip(CircleShape)
                            .background(if (overBin) Color(0xFFE5484D) else Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("🗑", fontSize = 20.sp) }
                }

                // The look's name after a swipe, as Snapchat shows it.
                if (lookToast.isNotEmpty()) {
                    Text(lookToast, fontFamily = Wyrm.Display, fontSize = 30.sp, color = Color.White,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 12f)),
                        modifier = Modifier.align(Alignment.Center))
                }

                // The tool rail.
                if (tool == EditorTool.NONE && !dragging) {
                    Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioRoundButton("Aa", Modifier) {
                            val rgb = draft.tone
                            editing = StudioText(text = "", rgb = rgb, serif = false, filled = false, center = Offset(w / 2, h / 2))
                            tool = EditorTool.TEXT
                        }
                        StudioRoundButton("✎", Modifier) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); tool = EditorTool.DRAW }
                        StudioRoundButton("☺", Modifier) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); tool = EditorTool.EMOJI }
                        if (draft.image != null || draft.video) StudioRoundButton("◐", Modifier) { tool = EditorTool.LOOKS }
                        if (draft.image != null || sharing) StudioRoundButton("⌗", Modifier) { tool = EditorTool.CROP }
                        if (draft.strokes.isNotEmpty()) StudioRoundButton("↶", Modifier) { draft.strokes.removeAt(draft.strokes.lastIndex); draft.ink++ }
                    }
                }

                // Drawing: brushes on top, colours down the right edge.
                if (tool == EditorTool.DRAW) {
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudioBrush.entries.forEach { kind -> StudioChip(kind.label, brush == kind, dark = true) { brush = kind } }
                        Spacer(Modifier.weight(1f))
                        if (draft.strokes.isNotEmpty()) StudioRoundButton("↶", Modifier) { draft.strokes.removeAt(draft.strokes.lastIndex); draft.ink++ }
                        StudioChip("Done", true, dark = true) { tool = EditorTool.NONE }
                    }
                    Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioPalette.colours.forEach { rgb ->
                            Box(Modifier.size(if (inkRgb == rgb) 28.dp else 22.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape)
                                .background(StudioPalette.color(rgb)).clickable { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); inkRgb = rgb })
                        }
                    }
                }
            }

            // Under the picture: crop shapes, the canvas colour, or a hint.
            Box(Modifier.fillMaxWidth().height(bottomBar), contentAlignment = Alignment.Center) {
                when {
                    tool == EditorTool.LOOKS -> LooksPanel(draft) { tool = EditorTool.NONE }
                    bottom != null && tool == EditorTool.NONE -> bottom()
                    tool == EditorTool.CROP -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioAspect.entries.forEach { aspect ->
                                StudioChip(aspect.label, draft.aspect == aspect) {
                                    if (aspect == StudioAspect.FREE) draft.freeRatio = draft.ratio
                                    draft.aspect = aspect
                                    draft.photoScale = 1f
                                    draft.photoOffset = Offset.Zero
                                    draft.shotScale = 1f
                                    draft.shotOffset = Offset.Zero
                                }
                            }
                        }
                        StudioChip("Done", true) { tool = EditorTool.NONE }
                    }
                    sharing && tool == EditorTool.NONE -> ShareBar(draft, w, h, type)
                    draft.image == null && !draft.video && tool == EditorTool.NONE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(StudioAspect.PORTRAIT, StudioAspect.SQUARE, StudioAspect.WIDE).forEach { aspect ->
                                StudioChip(aspect.label, draft.aspect == aspect) { draft.aspect = aspect }
                            }
                        }
                        StudioSwatches(draft.background) { draft.background = it }
                    }
                    tool == EditorTool.NONE -> Text("Swipe for looks · Aa write · ☺ stickers · ✎ draw", fontFamily = Wyrm.Body,
                        fontSize = 12.sp, color = Wyrm.Quiet)
                }
            }
        }

        if (tool == EditorTool.EMOJI) {
            EmojiSheet(
                onPick = { emoji ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    draft.emojis += StudioEmoji(emoji = emoji, center = Offset(w / 2, h / 2))
                    tool = EditorTool.NONE
                },
                onClose = { tool = EditorTool.NONE },
            )
        }

        editing?.let { current ->
            TextEditOverlay(
                item = current,
                onChange = { editing = it },
                onDone = { done ->
                    val text = done.text.trim()
                    val index = draft.texts.indexOfFirst { it.id == done.id }
                    when {
                        index >= 0 && text.isEmpty() -> draft.texts.removeAt(index)
                        index >= 0 -> draft.texts[index] = done.copy(text = text)
                        text.isNotEmpty() -> draft.texts += done.copy(text = text)
                    }
                    editing = null
                    tool = EditorTool.NONE
                },
            )
        }
    }
}

@Composable
private fun StudioRoundButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
}

/** Free crop: drag an edge to reshape the frame; the photo stays covering it. */
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
private fun StudioChip(label: String, selected: Boolean, dark: Boolean = false, onClick: () -> Unit) {
    val back = when {
        dark && selected -> Color.White
        dark -> Color.Black.copy(alpha = 0.42f)
        selected -> Wyrm.Ink
        else -> Wyrm.Well
    }
    val ink = when {
        dark && selected -> Color.Black
        dark -> Color.White
        selected -> Wyrm.OnInk
        else -> Wyrm.Ink
    }
    Box(
        Modifier.clip(CircleShape).background(back)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp).height(32.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ink) }
}

/**
 * Share run, under the canvas: "+ Skin" / "+ Stats" (a run only) to bring back what was
 * binned, the stats box's looks, and the background colour (the theme's own
 * colours first, then the studio's).
 */
@Composable
private fun ShareBar(draft: StudioDraft, w: Float, h: Float, type: StudioType) {
    val haptics = LocalHapticFeedback.current
    val theme = listOf(Wyrm.Paper, Wyrm.Card, Wyrm.Ink, Wyrm.Live, Wyrm.Link, Wyrm.Badge).map { it.toArgb() and 0xFFFFFF }
    val colours = (theme + StudioPalette.colours).distinct()
    val hasSkin = draft.stickers.any { it.kind == StickerKind.SKIN }
    val hasStats = draft.stickers.any { it.kind == StickerKind.STATS }
    val hasRun = draft.share?.run != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!hasSkin) StudioChip("+ Skin", false) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                draft.addSticker(StickerKind.SKIN, w, h, type)
            }
            if (hasRun && !hasStats) StudioChip("+ Stats", false) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                draft.addSticker(StickerKind.STATS, w, h, type)
            }
            if (hasStats) StatsStyle.entries.forEach { style ->
                StudioChip(style.label, draft.statsStyle == style) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    draft.statsStyle = style
                }
            }
        }
        StudioSwatches(draft.background, colours, ring = Wyrm.Ink) { draft.background = it }
    }
}

/**
 * Looks and Adjust (OM, 2026-10-05): the filter strip, each swatch the picture
 * itself in that look, and four sliders. A video takes the same look.
 */
@Composable
private fun LooksPanel(draft: StudioDraft, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var adjusting by remember { mutableStateOf(false) }
    val sample = draft.lookSample ?: draft.image
    val thumb = remember(sample) {
        sample?.let { s ->
            val k = 120f / max(s.width, s.height).coerceAtLeast(1)
            Bitmap.createScaledBitmap(s, max(1, (s.width * k).roundToInt()), max(1, (s.height * k).roundToInt()), true).asImageBitmap()
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioChip("Looks", !adjusting) { adjusting = false }
            StudioChip("Adjust", adjusting) { adjusting = true }
            Spacer(Modifier.weight(1f))
            if (!TrailLooks.isIdentity(draft.look, draft.adjust)) {
                StudioChip("Reset", false) { draft.look = TrailLooks.all.first(); draft.adjust = TrailAdjust() }
            }
            StudioChip("Done", true, onClick = onDone)
        }
        if (!adjusting) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrailLooks.all.forEach { look ->
                    val selected = draft.look == look
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            draft.look = look
                        },
                    ) {
                        Box(Modifier.size(58.dp).clip(wyrmRounded(12.dp)).background(Wyrm.Well)
                            .border(if (selected) 2.5.dp else 0.dp, Wyrm.Ink, wyrmRounded(12.dp))) {
                            thumb?.let {
                                Image(it, null, contentScale = ContentScale.Crop,
                                    colorFilter = if (look.name == "Normal") null else TrailLooks.composeFilter(look),
                                    modifier = Modifier.fillMaxSize().clip(wyrmRounded(12.dp)))
                            }
                        }
                        Text(look.name, fontFamily = Wyrm.Body, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.5.sp, color = if (selected) Wyrm.Ink else Wyrm.Mute, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AdjustRow("Brightness", draft.adjust.brightness) { draft.adjust = draft.adjust.copy(brightness = it) }
                AdjustRow("Contrast", draft.adjust.contrast) { draft.adjust = draft.adjust.copy(contrast = it) }
                AdjustRow("Saturation", draft.adjust.saturation) { draft.adjust = draft.adjust.copy(saturation = it) }
                AdjustRow("Warmth", draft.adjust.warmth) { draft.adjust = draft.adjust.copy(warmth = it) }
            }
        }
    }
}

@Composable
private fun AdjustRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
        Text(label, fontFamily = Wyrm.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Wyrm.Mute,
            modifier = Modifier.width(78.dp))
        WyrmSlider(value, -1f, 1f, Modifier.weight(1f)) { onChange((it * 20f).roundToInt() / 20f) }
        Text("${(value * 100).roundToInt()}", fontFamily = Wyrm.Body, fontSize = 11.sp, color = Wyrm.Quiet,
            textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
    }
}

/** Stickers, Snapchat-style: a sheet of emoji; one tap places it in the middle. */
@Composable
private fun EmojiSheet(onPick: (String) -> Unit, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(wyrmRounded(24.dp)).background(Wyrm.Card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Text("Stickers", fontFamily = Wyrm.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Wyrm.Ink)
            LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.fillMaxWidth().height(250.dp).padding(top = 10.dp)) {
                items(STUDIO_EMOJIS) { emoji ->
                    Box(Modifier.aspectRatio(1f).clickable { onPick(emoji) }, contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 30.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSwatches(
    selected: Int,
    colours: List<Int> = StudioPalette.colours,
    ring: Color = Color.White,
    onPick: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        colours.forEach { rgb ->
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .border(if (selected == rgb) 2.5.dp else 0.dp, ring, CircleShape)
                    .padding(3.dp).clip(CircleShape).background(StudioPalette.color(rgb)).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .clickable { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPick(rgb) },
            )
        }
    }
}

/**
 * Writing, story-style: the screen dims, the keyboard comes straight up and the
 * words appear large in the middle as they are typed. Font and background at
 * the top, colours just above the keyboard. Tap anywhere or Done to place it.
 */
@Composable
private fun TextEditOverlay(item: StudioText, onChange: (StudioText) -> Unit, onDone: (StudioText) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val shown = StudioPalette.color(if (item.filled) StudioPalette.contrast(item.rgb) else item.rgb)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone(item) },
    ) {
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioChip(if (item.serif) "Serif" else "Clean", true, dark = true) { onChange(item.copy(serif = !item.serif)) }
            StudioChip(if (item.filled) "Fill" else "Plain", item.filled, dark = true) { onChange(item.copy(filled = !item.filled)) }
            StudioChip("Glow", item.glow, dark = true) { onChange(item.copy(glow = !item.glow)) }
            StudioChip("Line", item.outline, dark = true) { onChange(item.copy(outline = !item.outline)) }
            Spacer(Modifier.weight(1f))
            StudioChip("Done", true, dark = true) { onDone(item) }
        }
        Box(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = item.text,
                onValueChange = { onChange(item.copy(text = it.take(160))) },
                textStyle = TextStyle(fontFamily = if (item.serif) Wyrm.Display else Wyrm.Body, fontWeight = FontWeight.Bold,
                    fontSize = 32.sp, lineHeight = 38.sp, color = shown, textAlign = TextAlign.Center,
                    shadow = when {
                        item.glow -> Shadow(shown, Offset.Zero, 24f)
                        item.outline && !item.filled -> Shadow(StudioPalette.color(StudioPalette.contrast(item.rgb)), Offset.Zero, 6f)
                        else -> null
                    }),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .then(if (item.filled) Modifier.clip(wyrmRounded(16.dp)).background(StudioPalette.color(item.rgb)).padding(horizontal = 14.dp, vertical = 8.dp) else Modifier)
                    .widthIn(min = 24.dp, max = 300.dp)
                    .focusRequester(focus),
            )
            if (item.text.isEmpty()) {
                Text("Type something", fontFamily = if (item.serif) Wyrm.Display else Wyrm.Body, fontWeight = FontWeight.Bold,
                    fontSize = 32.sp, color = Color.White.copy(alpha = 0.35f), textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) { StudioSwatches(item.rgb) { onChange(item.copy(rgb = it)) } }
    }
}
