package com.miios.launcher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miios.launcher.R
import java.io.File

/**
 * Home-screen music, like the Wii Menu's: a calm loop while Mii_OS is on screen, fading out when
 * an app starts. The built-in loop is Mii_OS's own (tools/sounds/make_music.py); a file named
 * "music" in the sound pack folder replaces it.
 */
object Music {

    private val extensions = listOf("ogg", "mp3", "m4a", "wav", "flac")
    private var player: MediaPlayer? = null
    private var source: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var level = 0f
    private var target = 0f

    var enabled by mutableStateOf(true)
        private set
    /** 0..1, set from Mii_OS Settings. */
    var volume by mutableFloatStateOf(0.5f)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("sounds", Context.MODE_PRIVATE)

    fun restore(context: Context) {
        val p = prefs(context)
        enabled = p.getBoolean("music", true)
        volume = p.getFloat("musicVolume", 0.5f)
    }

    fun switchTo(context: Context, on: Boolean) {
        enabled = on
        prefs(context).edit().putBoolean("music", on).apply()
        if (on) play(context) else fadeOut()
    }

    fun changeVolume(context: Context, value: Float) {
        volume = value.coerceIn(0f, 1f)
        prefs(context).edit().putFloat("musicVolume", volume).apply()
        target = volume
        apply(volume)
    }

    /** Starts (or resumes) the loop and fades it in. */
    fun play(context: Context) {
        if (!enabled) return
        val custom = extensions.map { File(Sounds.folder(context), "music.$it") }.firstOrNull { it.isFile }
        val wanted = custom?.let { "${it.path}@${it.lastModified()}" } ?: "built-in"
        if (player == null || wanted != source) {
            release()
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    if (custom != null) {
                        setDataSource(custom.path)
                    } else {
                        val fd = context.resources.openRawResourceFd(R.raw.mii_music)
                        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                        fd.close()
                    }
                    isLooping = true
                    prepare()
                }
            }.getOrNull()
            source = wanted
            level = 0f
        }
        val p = player ?: return
        apply(level)
        if (!p.isPlaying) p.start()
        ramp(volume, 900)
    }

    /** Fades out and pauses, e.g. as an app starts or Mii_OS goes to the background. */
    fun fadeOut(ms: Long = 450) {
        if (player?.isPlaying != true) return
        ramp(0f, ms)
    }

    private fun ramp(to: Float, ms: Long) {
        handler.removeCallbacksAndMessages(null)
        target = to
        val from = level
        val steps = (ms / 30).coerceAtLeast(1)
        for (i in 1..steps) {
            handler.postDelayed({
                apply(from + (to - from) * i / steps)
                if (i == steps && to == 0f) player?.takeIf { it.isPlaying }?.pause()
            }, i * 30L)
        }
    }

    private fun apply(v: Float) {
        level = v
        runCatching { player?.setVolume(v, v) }
    }

    private fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.release() }
        player = null
    }
}
