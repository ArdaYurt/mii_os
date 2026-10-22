package com.miios.launcher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miios.launcher.R
import com.miios.launcher.storage.PackFolders
import java.io.File

/** The moments Mii_OS makes a sound. [file] is the name a custom sound pack uses for it. */
enum class Sfx(val file: String, val builtIn: Int) {
    Move("move", R.raw.mii_move),
    Select("select", R.raw.mii_select),
    Back("back", R.raw.mii_back),
    Launch("launch", R.raw.mii_launch),
    Page("page", R.raw.mii_page),
    Home("home", R.raw.mii_home),
    /** Leaving an app for Mii_OS from the HOME Menu; the built-in one is the select sound. */
    HomeExit("home-exit", R.raw.mii_select),
    Notice("notice", R.raw.mii_notice),
    /** The remote's volume keys (the TV's own volume, which shows nothing on screen). */
    Volume("volume", R.raw.mii_volume),
}

/**
 * Mii_OS's UI sounds. The built-in ones are Mii_OS's own, synthesized for it.
 *
 * Sound pack: put audio files named after an [Sfx] (move.mp3, select.wav, launch.ogg, ...) in
 * /sdcard/Mii_OS/sounds on the box, and Mii_OS plays those instead. They
 * stay on the box; tools/sounds/push-sounds.bat copies a folder there from a laptop.
 */
object Sounds {

    private val extensions = listOf("ogg", "mp3", "wav", "m4a", "flac")
    private var pool: SoundPool? = null
    private val ids = IntArray(Sfx.entries.size)
    private var packStamp = -1L
    private var lastMove = 0L
    private var prefs: android.content.SharedPreferences? = null

    /** The Sounds switch in Mii_OS Settings. State, so the Settings screen shows it live. */
    var enabled by mutableStateOf(true)
        private set

    /** 0..1, the effects slider in Mii_OS Settings. */
    var effectsVolume by mutableFloatStateOf(0.8f)
        private set

    fun changeVolume(value: Float) {
        effectsVolume = value.coerceIn(0f, 1f)
        prefs?.edit()?.putFloat("effectsVolume", effectsVolume)?.apply()
    }

    /** The volume-keys sound switch in Settings › Sounds. */
    var volumeKeys by mutableStateOf(true)
        private set

    fun switchVolumeKeys(on: Boolean) {
        volumeKeys = on
        prefs?.edit()?.putBoolean("volumeKeys", on)?.apply()
    }

    fun switchTo(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    /** Your own file for [name] from the sound pack, if there is one. */
    fun custom(context: Context, name: String): File? = PackFolders.find(context, "sounds", name, extensions)

    /** Loads the sounds, or reloads them when the sound pack folder changed. Cheap when nothing did. */
    @Synchronized
    fun load(context: Context) {
        val app = context.applicationContext
        if (prefs == null) {
            prefs = app.getSharedPreferences("sounds", Context.MODE_PRIVATE).also {
                enabled = it.getBoolean("enabled", true)
                effectsVolume = it.getFloat("effectsVolume", 0.8f)
                volumeKeys = it.getBoolean("volumeKeys", true)
            }
            Music.restore(app)
        }
        val custom = Sfx.entries.associateWith { sfx -> custom(app, sfx.file) }
        val stamp = custom.values.sumOf { it?.lastModified()?.plus(it.length()) ?: 0L }
        if (pool != null && stamp == packStamp) return
        packStamp = stamp
        pool?.release()
        val next = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                // Media usage, so the sounds follow the TV's main volume. Android TV boxes often
                // keep the separate system-sounds volume very low or muted.
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        for (sfx in Sfx.entries) {
            val file = custom[sfx]
            ids[sfx.ordinal] = if (file != null) next.load(file.path, 1) else next.load(app, sfx.builtIn, 1)
        }
        pool = next
        // only the home-exit sound's length is needed: the home screen's return lasts as long as it
        lengths[Sfx.HomeExit.ordinal] = measure(app, Sfx.HomeExit, custom[Sfx.HomeExit])
        // make sure the folder exists, so it's easy to find when copying a pack over
        PackFolders.own(app, "sounds").mkdirs()
    }

    private var lastVolume = 0L
    private var volumeRun = 0

    /**
     * A press of the remote's volume key. The first press of a run plays the volume sound in full;
     * held or repeated presses play it at most every 110 ms, softer each time and sliding up in
     * pitch for louder (down for quieter), so going from 0 to 100 sounds like one smooth sweep.
     */
    fun volumeKey(up: Boolean) {
        if (!enabled || !volumeKeys) return
        val pool = pool ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastVolume > 450) volumeRun = 0
        if (volumeRun > 0 && now - lastVolume < 110) return
        lastVolume = now
        val step = volumeRun.coerceAtMost(15)
        val rate = if (up) 1f + step * 0.035f else 1f - step * 0.025f
        val gain = (if (volumeRun == 0) 1f else (0.7f - step * 0.025f).coerceAtLeast(0.35f)) * effectsVolume
        volumeRun++
        pool.play(ids[Sfx.Volume.ordinal], gain, gain, 1, 0, rate)
    }

    private val lengths = LongArray(Sfx.entries.size)

    /** How long [sfx] plays, in milliseconds (your own file's length, or the built-in one's); 0 if unknown. */
    fun lengthOf(sfx: Sfx): Long = lengths[sfx.ordinal]

    private fun measure(context: Context, sfx: Sfx, file: File?): Long = runCatching {
        android.media.MediaMetadataRetriever().run {
            try {
                if (file != null) setDataSource(file.path)
                else setDataSource(context, android.net.Uri.parse("android.resource://${context.packageName}/${sfx.builtIn}"))
                extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                release()
            }
        }
    }.getOrDefault(0L)

    fun play(sfx: Sfx) {
        if (!enabled) return
        val pool = pool ?: return
        if (sfx == Sfx.Move) {
            // holding a direction repeats fast; keep the ticks from smearing into a buzz
            val now = SystemClock.uptimeMillis()
            if (now - lastMove < 45) return
            lastMove = now
        }
        val volume = (if (sfx == Sfx.Move) 0.8f else 1f) * effectsVolume
        pool.play(ids[sfx.ordinal], volume, volume, 1, 0, 1f)
    }
}
