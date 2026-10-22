package com.miios.launcher.service

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miios.launcher.MainActivity
import com.miios.launcher.art.ChannelStyles
import com.miios.launcher.tv.TvActivity

/**
 * The remote's extra buttons (like TV+ or the Assistant button), given new jobs. The owner teaches
 * Mii_OS a button by pressing it in Settings › Remote Buttons, then picks what it opens. The Home
 * button service catches it from then on, in any app. A button Android handles before any app
 * sees it can't be changed; teaching it then simply catches nothing.
 */
object RemoteButtons {

    /** What a taught button opens. [Off] leaves it doing what it did before. */
    enum class Target(val title: String) {
        Off("What it did before"),
        Tv("TV (your channels)"),
        Plex("Plex (Plezy, or the Plex app)"),
        ScreenShare("Screen Share"),
        Home("Mii_OS home screen"),
    }

    /** One taught button: its key code (0 = none yet) and what it opens. */
    data class Button(val code: Int, val target: Target)

    const val COUNT = 2
    private const val PREFS = "remote_buttons"

    var buttons by mutableStateOf(List(COUNT) { Button(0, Target.Off) })
        private set

    /** The row waiting for a press, or -1. */
    var learning by mutableIntStateOf(-1)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        buttons = List(COUNT) {
            val target = runCatching { Target.valueOf(prefs.getString("target$it", null) ?: "") }.getOrDefault(Target.Off)
            Button(prefs.getInt("code$it", 0), target)
        }
    }

    fun learn(row: Int) {
        learning = row
    }

    fun stopLearning() {
        learning = -1
    }

    fun changeTarget(context: Context, row: Int, step: Int) {
        val now = buttons[row]
        val all = Target.entries
        val next = all[(now.target.ordinal + step).mod(all.size)]
        save(context, row, now.copy(target = next))
    }

    /** Buttons that keep their usual job: moving, OK, Back, Home and volume are never taken. */
    private val reserved = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    )

    /**
     * Called by the Home button service for every key. Returns true when the key was a taught button
     * (or the press being taught) and is used up here.
     */
    fun onKey(context: Context, event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code in reserved || code == KeyEvent.KEYCODE_UNKNOWN) return false
        val row = learning
        if (row >= 0) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // one button, one job: teaching it to a row takes it off the other
                buttons.forEachIndexed { i, b -> if (i != row && b.code == code) save(context, i, Button(0, b.target)) }
                val old = buttons[row]
                save(context, row, Button(code, if (old.target == Target.Off) Target.Tv else old.target))
                learning = -1
                com.miios.launcher.sound.Sounds.play(com.miios.launcher.sound.Sfx.Select)
            }
            return true
        }
        val target = buttons.firstOrNull { it.code == code }?.target ?: return false
        if (target == Target.Off) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) open(context, target)
        return true
    }

    private fun open(context: Context, target: Target) {
        val pm = context.packageManager
        fun app(pkg: String) = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        val intent = when (target) {
            Target.Off -> null
            Target.Tv -> TvActivity.intent(context)
            Target.Plex -> app(ChannelStyles.PLEZY) ?: app(ChannelStyles.PLEX)
            Target.ScreenShare -> app(ChannelStyles.SCREEN_SHARE)
            Target.Home -> Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: Intent(context, MainActivity::class.java) // not installed yet: the home screen, where it can be
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun save(context: Context, row: Int, button: Button) {
        buttons = buttons.toMutableList().also { it[row] = button }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("code$row", button.code)
            .putString("target$row", button.target.name)
            .apply()
    }

    /** A taught button's name, from Android's name for its key: KEYCODE_TV_INPUT → "TV input". */
    fun nameOf(code: Int): String = when (code) {
        0 -> "Not set"
        KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH -> "Assistant button"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").lowercase().replace('_', ' ')
            .replaceFirstChar { it.uppercase() }.let { if (it.all { c -> c.isDigit() }) "Button $it" else "$it button" }
    }
}
