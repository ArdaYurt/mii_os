package com.miios.launcher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miios.launcher.R
import java.io.File

/** The moments Mii_OS makes a sound. [file] is the name a custom sound pack uses for it. */
enum class Sfx(val file: String, val builtIn: Int) {
    Move("move", R.raw.mii_move),
    Select("select", R.raw.mii_select),
    Back("back", R.raw.mii_back),
    Launch("launch", R.raw.mii_launch),
    Page("page", R.raw.mii_page),
    Home("home", R.raw.mii_home),
    Notice("notice", R.raw.mii_notice),
}

/**
 * Mii_OS's UI sounds. The built-in ones are Mii_OS's own, synthesized for it.
 *
 * Sound pack: put audio files named after an [Sfx] (move.mp3, select.wav, launch.ogg, ...) in
 * Android/data/com.miios.launcher/files/sounds on the box, and Mii_OS plays those instead. They
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

    fun switchTo(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    fun folder(context: Context): File = File(context.getExternalFilesDir(null), "sounds")

    /** Loads the sounds, or reloads them when the sound pack folder changed. Cheap when nothing did. */
    @Synchronized
    fun load(context: Context) {
        val app = context.applicationContext
        if (prefs == null) {
            prefs = app.getSharedPreferences("sounds", Context.MODE_PRIVATE).also { enabled = it.getBoolean("enabled", true) }
        }
        val dir = folder(app)
        val custom = Sfx.entries.associateWith { sfx -> extensions.map { File(dir, "${sfx.file}.$it") }.firstOrNull { it.isFile } }
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
        // make sure the folder exists, so it's easy to find when copying a pack over
        dir.mkdirs()
    }

    fun play(sfx: Sfx) {
        if (!enabled) return
        val pool = pool ?: return
        if (sfx == Sfx.Move) {
            // holding a direction repeats fast; keep the ticks from smearing into a buzz
            val now = SystemClock.uptimeMillis()
            if (now - lastMove < 45) return
            lastMove = now
        }
        val volume = if (sfx == Sfx.Move) 0.8f else 1f
        pool.play(ids[sfx.ordinal], volume, volume, 1, 0, 1f)
    }
}
