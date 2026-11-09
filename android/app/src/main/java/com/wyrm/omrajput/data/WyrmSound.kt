package com.wyrm.omrajput.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * AIR's music and sounds (OM, 2026-11-08), as Main.as plays them. The engine
 * decides what happens (game/wyrm_audio.c: deaths on screen, your eating, your
 * boost, your death); this plays AIR's own files from `assets/sounds/`.
 *
 *  - Menu: menu_intro, then menu looping without a gap, on every screen
 *    outside a match (Main.as startMenuMusic / menuMusicIntroComplete).
 *  - Match: a game song from Play on (8 new songs, or the old gs_old), each at
 *    Main.as's own volume, the next one picked the Main.as way when it ends.
 *    Your death: new music fades (x0.98 - 0.001 a frame), old music slows.
 *  - Sounds: bloops when a snake dies on screen, eating (new foods_2 / old
 *    foods_1), the boost loop rising and falling (Main.as bs_v).
 *
 * Settings › Music and sounds; prefs `wyrm_audio` (SYNC) and the shared keys
 * musicOn / soundsOn / newSounds / newMusic. Music off and sounds on by
 * default (OM). iOS twin: WyrmSound.swift.
 */
object WyrmSound {
    private const val PREFS = "wyrm_audio"
    var musicOn by mutableStateOf(false); private set
    var soundsOn by mutableStateOf(true); private set
    var newSounds by mutableStateOf(true); private set
    var newMusic by mutableStateOf(true); private set

    private var app: Context? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val ready = HashSet<Int>()
    private var boostStream = 0
    private var boostVolume = 0f
    private var resumed = false

    // Music
    private var menuIntro: MediaPlayer? = null
    private var menuLoop: MediaPlayer? = null
    private var song: MediaPlayer? = null
    private var songIndex = -2         // -1 = old music, 0..7 = songs/1..8
    private var songVolume = 0f
    private var songStarted = 0L
    private val songLastPlayed = LongArray(8)
    private var inMatch = false
    private var dying = false          // your snake died: fade or slow down
    private var lastTick = 0L

    private val SONG_VOLUME = floatArrayOf(.4f, .4f, .31f, .4f, .35f, .4f, .4f, .4f)

    fun attach(context: Context) {
        app = context.applicationContext
        // Every effect loaded up front (SoundPool decodes on its own thread),
        // so the first bite or death never waits.
        load("sounds/boost.wav")
        for (n in 0 until 16) load("sounds/bloops/$n.mp3")
        for (n in 1..80) load("sounds/foods_2/$n.mp3")
        for (n in 1..11) load("sounds/foods_1/$n.mp3")
        app = context.applicationContext
        reload(context)
    }

    fun reload(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        musicOn = p.getBoolean("music", false)
        soundsOn = p.getBoolean("sounds", true)
        newSounds = p.getBoolean("new_sounds", true)
        newMusic = p.getBoolean("new_music", true)
        refreshMusic(restart = true)
        if (!soundsOn) stopBoost()
    }

    private fun save(key: String, value: Boolean) {
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(key, value)?.apply()
    }

    fun applyMusic(on: Boolean) { musicOn = on; save("music", on); refreshMusic(restart = true) }
    fun applySounds(on: Boolean) { soundsOn = on; save("sounds", on); if (!on) stopBoost() }
    fun applyNewSounds(on: Boolean) { newSounds = on; save("new_sounds", on) }
    fun applyNewMusic(on: Boolean) {
        newMusic = on; save("new_music", on)
        if (inMatch) { stopSong(); if (musicOn) startSong() }
    }

    /** Shared document restore: each key on its own, a missing one keeps the phone's. */
    fun restore(context: Context, music: Boolean?, sounds: Boolean?, newS: Boolean?, newM: Boolean?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            music?.let { putBoolean("music", it) }
            sounds?.let { putBoolean("sounds", it) }
            newS?.let { putBoolean("new_sounds", it) }
            newM?.let { putBoolean("new_music", it) }
        }.commit()
        reload(context)
    }

    // ------------------------------------------------------------ lifecycle

    fun onResume() { resumed = true; refreshMusic(restart = false) }

    fun onPause() {
        resumed = false
        menuIntro?.runCatching { if (isPlaying) pause() }
        menuLoop?.runCatching { if (isPlaying) pause() }
        song?.runCatching { if (isPlaying) pause() }
        stopBoost()
    }

    /**
     * About thirty times a second while Wyrm is on screen: [packed] is the
     * engine's "<boost>|<events>", [match] whether a match (online or Play
     * with AI) is on screen.
     */
    fun tick(packed: String, match: Boolean) {
        val now = System.currentTimeMillis()
        val dt = if (lastTick == 0L) 0.033f else ((now - lastTick).coerceIn(0, 250)) / 1000f
        lastTick = now
        if (match != inMatch) {
            inMatch = match
            dying = false
            if (match) { stopMenu(); stopSong(); if (musicOn && resumed) startSong() }
            else { stopSong(); stopBoost(); refreshMusic(restart = true) }
        }
        val bar = packed.indexOf('|')
        val boosting = bar > 0 && packed[0] == '1'
        val events = if (bar >= 0) packed.substring(bar + 1) else ""
        if (events.isNotBlank()) for (event in events.split(' ')) handle(event)
        boost(boosting && match && soundsOn && resumed, dt)
        musicFrame(dt)
    }

    private fun handle(event: String) {
        if (event.isEmpty()) return
        when (event[0]) {
            'K' -> if (musicOn && song != null) dying = true
            'B' -> if (soundsOn) event.substring(1).toIntOrNull()?.let { play("sounds/bloops/$it.mp3", 1f) }
            'F' -> if (soundsOn) {
                val parts = event.substring(1).split(',')
                if (newSounds) parts.getOrNull(0)?.toIntOrNull()?.let { play("sounds/foods_2/${it + 1}.mp3", .18f) }
                else parts.getOrNull(1)?.toIntOrNull()?.let { play("sounds/foods_1/${it + 1}.mp3", .16f) }
            }
        }
    }

    // ------------------------------------------------------------ sounds

    private fun pool(): SoundPool {
        pool?.let { return it }
        val made = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        made.setOnLoadCompleteListener { _, id, status -> if (status == 0) { ready.add(id); pending.remove(id)?.let { v -> made.play(id, v, v, 0, 0, 1f) } } }
        pool = made
        return made
    }

    private val pending = HashMap<Int, Float>()

    private fun load(path: String): Int? {
        ids[path]?.let { return it }
        val context = app ?: return null
        return runCatching { context.assets.openFd(path).use { pool().load(it, 1) } }.getOrNull()?.also { ids[path] = it }
    }

    /** Plays now, or as soon as its first load finishes (Main.as loads each on first use too). */
    private fun play(path: String, volume: Float) {
        val id = load(path) ?: return
        // Priority 0, the lowest: music and other sounds are never cut for it.
        if (id in ready) pool().play(id, volume, volume, 0, 0, 1f) else pending[id] = volume
    }

    /** Main.as bs_v: up by 1/90 a frame while boosting, down by 1/35, volume quiet..loud. */
    private fun boost(on: Boolean, dt: Float) {
        val frames = dt * 60f
        boostVolume = if (on) (boostVolume + frames / 90f).coerceAtMost(1f) else (boostVolume - frames / 35f).coerceAtLeast(0f)
        if (boostVolume <= 0f) { stopBoost(); return }
        val id = load("sounds/boost.wav") ?: return
        if (id !in ready) return
        if (boostStream == 0) boostStream = pool().play(id, 0f, 0f, 1, -1, 1f)
        val v = (boostVolume - .001f) * .115f + .001f
        pool().setVolume(boostStream, v, v)
    }

    private fun stopBoost() {
        if (boostStream != 0) pool?.stop(boostStream)
        boostStream = 0
        boostVolume = 0f
    }

    // ------------------------------------------------------------ music

    private fun player(path: String, loop: Boolean): MediaPlayer? {
        val context = app ?: return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                context.assets.openFd(path).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                isLooping = loop
                prepare()
            }
        }.getOrNull()
    }

    /** Outside a match: menu music (intro, then the loop, gapless). */
    private fun refreshMusic(restart: Boolean) {
        if (!musicOn || !resumed) {
            if (!musicOn) { stopMenu(); stopSong() }
            return
        }
        if (inMatch) {
            if (song == null) startSong() else song?.runCatching { if (!isPlaying) start() }
            return
        }
        val loop = menuLoop; val intro = menuIntro
        if (!restart && (loop != null || intro != null)) {
            if (loop != null && !intro.isPlayingSafe()) loop.runCatching { if (!isPlaying) start() }
            else intro?.runCatching { if (!isPlaying) start() }
            return
        }
        if (loop != null && loop.isPlayingSafe()) return
        if (menuPreparing) return
        stopMenu()
        menuPreparing = true
        // Prepared off the main thread (2026-11-08): prepare() reads the file
        // and the frame waited for it.
        worker.execute {
            val next = player("sounds/menu.wav", loop = true)
            val first = player("sounds/menu_intro.wav", loop = false)
            main.post {
                menuPreparing = false
                if (next == null || !musicOn || !resumed || inMatch) {
                    next?.release(); first?.release(); return@post
                }
                menuLoop = next
                if (first != null) {
                    menuIntro = first
                    first.setNextMediaPlayer(next)          // no gap between intro and loop
                    first.setOnCompletionListener { it.release(); if (menuIntro === it) menuIntro = null }
                    first.start()
                } else next.start()
            }
        }
    }

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var menuPreparing = false
    private var songPreparing = false

    private fun MediaPlayer?.isPlayingSafe() = this?.runCatching { isPlaying }?.getOrNull() == true

    private fun stopMenu() {
        menuIntro?.runCatching { stop(); release() }
        menuLoop?.runCatching { stop(); release() }
        menuIntro = null; menuLoop = null
    }

    /** Main.as getNextGameSong, then startGameSong. */
    private fun startSong() {
        stopSong()
        val now = System.currentTimeMillis()
        val index = if (!newMusic) -1 else {
            val count = 8
            if (songIndex !in 0 until count) Random.nextInt(count) else {
                var next = songIndex
                if ((now - songStarted) / 1000 > 10) {
                    if (Random.nextBoolean()) next = (next + 1) % count
                    else {
                        var probe = Random.nextInt(count)
                        for (i in 0 until count) {
                            probe = (probe + 1) % count
                            if (probe != songIndex && now - songLastPlayed[probe] > 600_000) { next = probe; break }
                        }
                    }
                }
                if (next == songIndex) next = (next + 1) % count
                next
            }
        }
        val path = if (index == -1) "sounds/gs_old.mp3" else "sounds/songs/${index + 1}.mp3"
        if (songPreparing) return
        songPreparing = true
        worker.execute {
            val prepared = player(path, loop = false)
            main.post {
                songPreparing = false
                if (prepared == null || !musicOn || !resumed || !inMatch) { prepared?.release(); return@post }
                beginSong(prepared, index, now)
            }
        }
    }

    private fun beginSong(next: MediaPlayer, index: Int, now: Long) {
        songIndex = index
        if (index >= 0) songLastPlayed[index] = now
        songStarted = now
        songVolume = if (index == -1) .65f else SONG_VOLUME[index]
        next.setVolume(songVolume, songVolume)
        next.setOnCompletionListener { if (song === it) { startSong() } }
        song = next
        dying = false
        next.start()
    }

    private fun stopSong() {
        song?.runCatching { stop(); release() }
        song = null
    }

    /** Your death: new music fades out, old music slows down to a stop. */
    private fun musicFrame(dt: Float) {
        val player = song ?: return
        if (!dying) return
        val frames = (dt * 60f).coerceAtLeast(1f)
        if (newMusic) {
            repeat(frames.toInt().coerceAtMost(15)) { songVolume = songVolume * .98f - .001f }
            if (songVolume <= 0f) { stopSong(); dying = false; return }
            player.runCatching { setVolume(songVolume, songVolume) }
        } else {
            val params = player.runCatching { playbackParams }.getOrNull() ?: return
            val speed = (params.speed - dt * .6f)
            if (speed <= .25f) { stopSong(); dying = false; return }
            player.runCatching { playbackParams = params.setSpeed(speed).setPitch(speed) }
        }
    }
}
