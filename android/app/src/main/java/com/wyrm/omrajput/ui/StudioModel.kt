package com.wyrm.omrajput.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.random.Random

/*
 * Skin Studio (OM, 2026-10-12): the Skin tab's default page. A player makes
 * beads (a finish, colour, hue, saturation, brightness, opacity, smoothness,
 * detail and their own painting), builds snakes from them (up to 256 beads,
 * up to 48 different ones), and gives each snake its own eyes (any number,
 * anywhere on the head, each with its own white and pupil).
 *
 * Only this phone sees a Studio skin. The arena gets one black bead
 * (slither's colour group 11, `f`), which it repeats along the body; the
 * engine draws the player's own snake from an atlas this file bakes
 * (game/studio.h). Nothing here goes to the account or to Trails yet (OM):
 * the library lives in files/skin_studio/ and stays on the phone, through a
 * log out too.
 */

internal data class StudioBead(
    val id: String,
    /** 0xAARRGGBB, opaque. */
    val color: Int = 0xFF3FEE96.toInt(),
    /** [StudioArt.FINISHES] index. */
    val finish: Int = 0,
    /** Degrees, -180..180. */
    val hue: Float = 0f,
    /** 0..2, 1 = unchanged. */
    val saturation: Float = 1f,
    /** 0..2, 1 = unchanged. */
    val brightness: Float = 1f,
    /** 0.15..1. */
    val opacity: Float = 1f,
    /** 0..1: a softer edge and a softer painting. */
    val smoothness: Float = 0f,
    /** 0..1: how strongly the finish shows. */
    val detail: Float = 0.7f,
    /** A painting lives in paint/<id>.png. */
    val painted: Boolean = false,
    /** Bumped on every edit: the picture caches key on it. */
    val rev: Int = 0,
)

internal data class StudioEye(
    /** Snake units from the head's centre, forward. */
    val fwd: Float = 6f,
    /** Snake units, + = the snake's right. */
    val side: Float = 6.5f,
    /** The white's radius, snake units. */
    val white: Float = 6f,
    /** The pupil's radius, snake units. */
    val pupil: Float = 3.5f,
    /** How far the pupil follows where the snake looks (1 = slither). */
    val look: Float = 1f,
    val whiteColor: Int = 0xFFFFFFFF.toInt(),
    val pupilColor: Int = 0xFF000000.toInt(),
    /** [StudioArt.WHITE_SHAPES] index. */
    val whiteShape: Int = 0,
    /** [StudioArt.PUPIL_SHAPES] index. */
    val pupilShape: Int = 0,
) {
    companion object {
        /** slither's own eyes: forward 6, 6.5 either side, white 6, pupil 3.5. */
        fun pair(): List<StudioEye> = listOf(StudioEye(side = -6.5f), StudioEye(side = 6.5f))
    }
}

internal data class StudioSkin(
    val id: String,
    val name: String,
    /** Bead ids, head first; repeated along the snake. */
    val pattern: List<String>,
    val eyes: List<StudioEye> = StudioEye.pair(),
    val updated: Long = System.currentTimeMillis(),
)

internal object SkinStudio {
    const val MAX_PATTERN = 256
    /** Distinct beads one snake may wear: the atlas' bead cells. */
    const val MAX_DISTINCT = 48
    const val MAX_EYES = 8
    /** slither's darkest bead, colour group 11 ("black"): what the arena gets. */
    const val ARENA_GROUP = 11

    var beads by mutableStateOf<List<StudioBead>>(emptyList())
        private set
    var skins by mutableStateOf<List<StudioSkin>>(emptyList())
        private set
    /** The skin being edited on the Studio page. */
    var selected by mutableStateOf<String?>(null)
        private set
    /** The skin worn now, or null. */
    var worn by mutableStateOf<String?>(null)
        private set
    /** Bumped when a painting changes, so pictures redraw. */
    var paintRevision by mutableIntStateOf(0)
        private set

    /** Wears and takes off: implemented by WyrmOverlay's host. */
    interface Engine {
        fun studioSet(path: String, text: String)
        fun wearClassic(state: SkinState)
        fun wearArenaBead(code: String)
    }

    private var root: File? = null
    private var engine: Engine? = null
    /** The classic skin before the first Studio skin went on. */
    private var classicBefore: SkinState? = null
    /** The engine has echoed the arena bead since the last wear. */
    private var wearConfirmed = false
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bakeLock = Mutex()
    private var rewearJob: Job? = null
    private var bakeCounter = 0
    private val paintCache = HashMap<String, Bitmap>()

    val arenaCode: String get() = SkinCatalog.code(listOf(ARENA_GROUP))

    fun attach(context: Context, engine: Engine) {
        this.engine = engine
        appContext = context.applicationContext
        if (root != null) return
        val dir = File(context.filesDir, "skin_studio").apply { mkdirs() }
        File(dir, "paint").mkdirs()
        root = dir
        load()
        if (beads.isEmpty() && skins.isEmpty()) seed()
        if (selected == null || skins.none { it.id == selected }) selected = worn ?: skins.firstOrNull()?.id
    }

    // ------------------------------------------------------------ library

    fun bead(id: String): StudioBead? = beads.firstOrNull { it.id == id }
    fun skin(id: String?): StudioSkin? = id?.let { wanted -> skins.firstOrNull { it.id == wanted } }

    fun select(id: String) {
        if (skins.any { it.id == id }) selected = id
    }

    fun newBead(from: StudioBead? = null): StudioBead {
        val bead = (from ?: StudioBead(id = "")).copy(id = newId("b"), painted = false, rev = 0)
        beads = beads + bead
        from?.takeIf { it.painted }?.let { source -> paint(source.id)?.let { savePaint(bead.id, it) } }
        save()
        return bead(bead.id) ?: bead
    }

    fun updateBead(bead: StudioBead) {
        beads = beads.map { if (it.id == bead.id) bead.copy(rev = it.rev + 1) else it }
        save()
        rewearIfWorn { skin -> bead.id in skin.pattern }
    }

    /** A bead no snake wears any more goes; one that is worn stays. */
    fun deleteBead(id: String): Boolean {
        if (skins.any { id in it.pattern }) return false
        beads = beads.filterNot { it.id == id }
        paintCache.remove(id)
        root?.let { File(it, "paint/$id.png").delete() }
        save()
        return true
    }

    fun newSkin(name: String? = null, pattern: List<String>? = null, eyes: List<StudioEye>? = null): StudioSkin {
        val start = pattern ?: listOfNotNull(beads.firstOrNull()?.id ?: newBead().id)
        val skin = StudioSkin(newId("s"), name ?: nextName(), start.take(MAX_PATTERN), eyes ?: StudioEye.pair())
        skins = listOf(skin) + skins
        selected = skin.id
        save()
        return skin
    }

    fun duplicateSkin(id: String): StudioSkin? {
        val source = skin(id) ?: return null
        return newSkin("${source.name} copy", source.pattern, source.eyes)
    }

    fun renameSkin(id: String, name: String) {
        val clean = name.trim().take(28).ifEmpty { return }
        skins = skins.map { if (it.id == id) it.copy(name = clean, updated = now()) else it }
        save()
    }

    fun deleteSkin(id: String) {
        if (worn == id) takeOff()
        skins = skins.filterNot { it.id == id }
        if (selected == id) selected = skins.firstOrNull()?.id
        save()
    }

    /** Distinct beads after a change, and whether it still fits the atlas. */
    fun fits(pattern: List<String>): Boolean =
        pattern.size <= MAX_PATTERN && pattern.distinct().size <= MAX_DISTINCT

    fun setPattern(id: String, pattern: List<String>): Boolean {
        val clean = pattern.filter { bead(it) != null }.take(MAX_PATTERN)
        if (clean.isEmpty() || !fits(clean)) return false
        skins = skins.map { if (it.id == id) it.copy(pattern = clean, updated = now()) else it }
        save()
        rewearIfWorn { it.id == id }
        return true
    }

    fun setEyes(id: String, eyes: List<StudioEye>) {
        skins = skins.map { if (it.id == id) it.copy(eyes = eyes.take(MAX_EYES), updated = now()) else it }
        save()
        rewearIfWorn { it.id == id }
    }

    // ------------------------------------------------------------ painting

    fun paint(id: String): Bitmap? {
        paintCache[id]?.let { return it }
        val file = root?.let { File(it, "paint/$id.png") } ?: return null
        if (!file.exists()) return null
        val loaded = runCatching {
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            })
        }.getOrNull() ?: return null
        paintCache[id] = loaded
        return loaded
    }

    /** Stores a painting (a copy) for bead [id]; null clears it. */
    fun savePaint(id: String, bitmap: Bitmap?) {
        val dir = root ?: return
        val file = File(dir, "paint/$id.png")
        if (bitmap == null) {
            file.delete()
            paintCache.remove(id)
        } else {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val temp = File(dir, "paint/$id.tmp")
            runCatching { temp.outputStream().use { copy.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
            paintCache[id] = copy
        }
        paintRevision += 1
        beads = beads.map { if (it.id == id) it.copy(painted = bitmap != null, rev = it.rev + 1) else it }
        save()
        rewearIfWorn { skin -> id in skin.pattern }
    }

    // ------------------------------------------------------------ wearing

    /** Wear [id]: bake the atlas, hand it to the engine, and send the arena its one black bead. */
    fun wear(context: Context, id: String, classicNow: SkinState) {
        val skin = skin(id) ?: return
        if (worn == null) classicBefore = classicNow
        worn = skin.id
        wearConfirmed = false
        save()
        engine?.wearArenaBead(arenaCode)
        io.launch { bakeAndSend(context, skin) }
    }

    /** Back to the classic skin that was on before the Studio. */
    fun takeOff() {
        if (worn == null) return
        worn = null
        engine?.studioSet("", "")
        classicBefore?.let { engine?.wearClassic(it) }
        classicBefore = null
        save()
    }

    /** The engine says what is worn: a classic change elsewhere ends the Studio skin. */
    fun onClassicEcho(state: SkinState) {
        if (worn == null) return
        val black = state.custom && state.code == arenaCode
        if (black) {
            wearConfirmed = true
            return
        }
        if (!wearConfirmed) return
        worn = null
        classicBefore = null
        engine?.studioSet("", "")
        save()
    }

    /** Log out: the engine's skin is back to its defaults, so no Studio skin is on. The library stays. */
    fun dropWorn() {
        if (worn == null) return
        worn = null
        classicBefore = null
        wearConfirmed = false
        engine?.studioSet("", "")
        save()
    }

    /** The engine has (re)started: it needs the worn skin again. */
    fun onEngineStarted(context: Context) {
        val skin = skin(worn) ?: return
        wearConfirmed = false
        engine?.wearArenaBead(arenaCode)
        io.launch { bakeAndSend(context, skin) }
    }

    private fun rewearIfWorn(match: (StudioSkin) -> Boolean) {
        val skin = skin(worn) ?: return
        if (!match(skin)) return
        val context = appContext ?: return
        rewearJob?.cancel()
        rewearJob = io.launch {
            delay(350)
            skin(worn)?.let { bakeAndSend(context, it) }
        }
    }

    private var appContext: Context? = null

    private suspend fun bakeAndSend(context: Context, skin: StudioSkin) = bakeLock.withLock {
        val dir = root ?: return@withLock
        val textures = SkinTextures.load(context)
        val baked = withContext(Dispatchers.Default) { StudioArt.bakeAtlas(skin, textures) } ?: return@withLock
        bakeCounter += 1
        val file = File(dir, "worn_${System.currentTimeMillis()}_$bakeCounter.png")
        val ok = runCatching {
            file.outputStream().use { baked.atlas.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        baked.atlas.recycle()
        if (!ok) return@withLock
        // Older atlases go once two newer ones exist (the engine may still be decoding the last).
        dir.listFiles { f -> f.name.startsWith("worn_") && f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(3)
            ?.forEach { it.delete() }
        withContext(Dispatchers.Main) {
            if (worn == skin.id) engine?.studioSet(file.absolutePath, baked.text)
        }
    }

    // ------------------------------------------------------------ storage

    private fun now() = System.currentTimeMillis()
    private fun newId(prefix: String) = prefix + java.lang.Long.toHexString(Random.nextLong() and 0x7FFFFFFFFFFFL)

    private fun nextName(): String {
        var n = skins.size + 1
        while (skins.any { it.name == "Snake $n" }) n++
        return "Snake $n"
    }

    private fun load() {
        val file = root?.let { File(it, "library.json") } ?: return
        if (!file.exists()) return
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        beads = json.optJSONArray("beads").objects().mapNotNull { o ->
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            StudioBead(
                id = id,
                color = o.optInt("color", 0xFF3FEE96.toInt()) or (0xFF shl 24),
                finish = o.optInt("finish").coerceIn(0, StudioArt.FINISHES.lastIndex),
                hue = o.optDouble("hue", 0.0).toFloat().coerceIn(-180f, 180f),
                saturation = o.optDouble("saturation", 1.0).toFloat().coerceIn(0f, 2f),
                brightness = o.optDouble("brightness", 1.0).toFloat().coerceIn(0f, 2f),
                opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0.15f, 1f),
                smoothness = o.optDouble("smoothness", 0.0).toFloat().coerceIn(0f, 1f),
                detail = o.optDouble("detail", 0.7).toFloat().coerceIn(0f, 1f),
                painted = o.optBoolean("painted"),
            )
        }
        val known = beads.map { it.id }.toSet()
        skins = json.optJSONArray("skins").objects().mapNotNull { o ->
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            val pattern = o.optJSONArray("pattern").strings().filter { it in known }.take(MAX_PATTERN)
            if (pattern.isEmpty()) return@mapNotNull null
            StudioSkin(
                id = id,
                name = o.optString("name").ifEmpty { "Snake" },
                pattern = pattern,
                eyes = o.optJSONArray("eyes").objects().take(MAX_EYES).map { e ->
                    StudioEye(
                        fwd = e.optDouble("fwd", 6.0).toFloat().coerceIn(-30f, 30f),
                        side = e.optDouble("side", 6.5).toFloat().coerceIn(-30f, 30f),
                        white = e.optDouble("white", 6.0).toFloat().coerceIn(0f, 20f),
                        pupil = e.optDouble("pupil", 3.5).toFloat().coerceIn(0f, 20f),
                        look = e.optDouble("look", 1.0).toFloat().coerceIn(0f, 3f),
                        whiteColor = e.optInt("whiteColor", -1) or (0xFF shl 24),
                        pupilColor = e.optInt("pupilColor", 0xFF000000.toInt()) or (0xFF shl 24),
                        whiteShape = e.optInt("whiteShape").coerceIn(0, StudioArt.WHITE_SHAPES.lastIndex),
                        pupilShape = e.optInt("pupilShape").coerceIn(0, StudioArt.PUPIL_SHAPES.lastIndex),
                    )
                },
                updated = o.optLong("updated", now()),
            )
        }
        worn = json.optString("worn").ifEmpty { null }?.takeIf { w -> skins.any { it.id == w } }
        selected = json.optString("selected").ifEmpty { null }?.takeIf { s -> skins.any { it.id == s } }
        classicBefore = json.optJSONObject("classicBefore")?.let { c ->
            SkinState(
                custom = c.optBoolean("custom"),
                preset = c.optInt("preset"),
                code = c.optString("code"),
                accessory = c.optInt("accessory", -1),
                colours = c.optJSONArray("colours").ints().toIntArray(),
            )
        }
    }

    private fun save() {
        val dir = root ?: return
        val json = JSONObject()
            .put("version", 1)
            .put("beads", JSONArray(beads.map { b ->
                JSONObject()
                    .put("id", b.id).put("color", b.color).put("finish", b.finish)
                    .put("hue", b.hue.toDouble()).put("saturation", b.saturation.toDouble())
                    .put("brightness", b.brightness.toDouble()).put("opacity", b.opacity.toDouble())
                    .put("smoothness", b.smoothness.toDouble()).put("detail", b.detail.toDouble())
                    .put("painted", b.painted)
            }))
            .put("skins", JSONArray(skins.map { s ->
                JSONObject()
                    .put("id", s.id).put("name", s.name).put("updated", s.updated)
                    .put("pattern", JSONArray(s.pattern))
                    .put("eyes", JSONArray(s.eyes.map { e ->
                        JSONObject()
                            .put("fwd", e.fwd.toDouble()).put("side", e.side.toDouble())
                            .put("white", e.white.toDouble()).put("pupil", e.pupil.toDouble())
                            .put("look", e.look.toDouble())
                            .put("whiteColor", e.whiteColor).put("pupilColor", e.pupilColor)
                            .put("whiteShape", e.whiteShape).put("pupilShape", e.pupilShape)
                    }))
            }))
            .put("worn", worn ?: "")
            .put("selected", selected ?: "")
        classicBefore?.let { c ->
            json.put("classicBefore", JSONObject()
                .put("custom", c.custom).put("preset", c.preset).put("code", c.code)
                .put("accessory", c.accessory).put("colours", JSONArray(c.colours.toList())))
        }
        val temp = File(dir, "library.tmp")
        runCatching {
            temp.writeText(json.toString())
            val target = File(dir, "library.json")
            if (!temp.renameTo(target)) { target.delete(); temp.renameTo(target) }
        }
    }

    /** A first library, so the Studio opens on something to wear and to edit. */
    private fun seed() {
        fun b(color: Long, finish: Int, detail: Float = 0.7f) =
            StudioBead(newId("b"), color = color.toInt(), finish = finish, detail = detail)
        val green = b(0xFF3FEE96, StudioArt.FINISH_GLOSSY)
        val deep = b(0xFF0F6B46, StudioArt.FINISH_GLOSSY)
        val pearl = b(0xFFE9E3FF, StudioArt.FINISH_PEARL)
        val night = b(0xFF241B4A, StudioArt.FINISH_PEARL)
        val gold = b(0xFFF2B33D, StudioArt.FINISH_METAL)
        val ember = b(0xFFFF5A1F, StudioArt.FINISH_GRADIENT)
        val neon = b(0xFF22E3FF, StudioArt.FINISH_NEON)
        val candy = b(0xFFFF4F9A, StudioArt.FINISH_STRIPES)
        val scales = b(0xFF2FB36B, StudioArt.FINISH_SCALES)
        beads = listOf(green, deep, pearl, night, gold, ember, neon, candy, scales)
        skins = listOf(
            StudioSkin(newId("s"), "Wyrm", List(6) { green.id } + List(2) { deep.id }),
            StudioSkin(newId("s"), "Moonlight", List(4) { pearl.id } + List(4) { night.id }),
            StudioSkin(newId("s"), "Sunset", List(3) { gold.id } + List(3) { ember.id } + List(2) { candy.id }),
            StudioSkin(newId("s"), "Neon", List(5) { neon.id } + List(3) { night.id },
                eyes = StudioEye.pair().map { it.copy(pupilShape = StudioArt.PUPIL_CAT, whiteColor = 0xFFFFF27A.toInt()) }),
            StudioSkin(newId("s"), "Viper", List(4) { scales.id } + List(1) { deep.id },
                eyes = StudioEye.pair().map { it.copy(pupilShape = StudioArt.PUPIL_CAT, whiteColor = 0xFFFFC23D.toInt(), white = 5.5f) }),
        )
        selected = skins.first().id
        save()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifEmpty { null } }

    private fun JSONArray?.ints(): List<Int> =
        if (this == null) emptyList() else (0 until length()).map { optInt(it) }

    /** Numbers for the engine: always '.', whatever the phone's language. */
    internal fun num(value: Float): String = String.format(Locale.US, "%.3f", value)
}
