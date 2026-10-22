package com.miios.launcher.service

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miios.launcher.art.ChannelStyles
import com.miios.launcher.tv.TvActivity

/**
 * Remote buttons Mii_OS can give a job. Each one is known by the key codes it sends; a box
 * whose remote sends something else learns it in Settings › Remote buttons.
 */
enum class RemoteKey(val title: String, val defaultCodes: Set<Int>, val defaultTarget: String?) {
    // The Mi Box S remote's key layout (Vendor_2717_Product_32b9.kl) sends its shortcut buttons
    // as gamepad buttons. Xiaomi's handler for them (partnercustomizer) starts apps through the
    // stock home, which fails once the speed-up has switched it off, so Mii_OS opens them itself.
    TvPlus("TV+ button", setOf(KeyEvent.KEYCODE_BUTTON_5), TvActivity.CHANNEL_ID),
    Netflix("Netflix button", setOf(KeyEvent.KEYCODE_BUTTON_6), "com.netflix.ninja"),
    YouTube("YouTube button", setOf(KeyEvent.KEYCODE_BUTTON_3), ChannelStyles.YOUTUBE),
    PrimeVideo("Prime Video button", setOf(KeyEvent.KEYCODE_BUTTON_7), ChannelStyles.PRIME_VIDEO),
    // On the Mi Box S the Assistant button goes over the remote's voice link, not as a key, so
    // these only help on remotes that do send it as a key.
    Assistant(
        "Assistant button",
        setOf(KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH),
        null,
    ),
}

/**
 * What each [RemoteKey] opens, and the codes it was taught. [HomeButtonService] sees the remote's
 * keys before any app does and starts the chosen channel; a button with no channel does what it
 * always did.
 */
object RemoteButtons {
    private var prefs: android.content.SharedPreferences? = null

    /** The channel each button opens (a package, or the TV channel's id); null leaves it alone. */
    var targets by mutableStateOf<Map<RemoteKey, String?>>(RemoteKey.entries.associateWith { it.defaultTarget })
        private set

    /** Codes taught on this box, in place of the defaults. */
    var taught by mutableStateOf<Map<RemoteKey, Int>>(emptyMap())
        private set

    /** The button waiting to be taught: its next press is learned instead of acted on. */
    var teaching by mutableStateOf<RemoteKey?>(null)

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("remote_buttons", Context.MODE_PRIVATE)
        prefs = p
        targets = RemoteKey.entries.associateWith { key ->
            if (p.contains("target.${key.name}")) p.getString("target.${key.name}", null) else key.defaultTarget
        }
        taught = RemoteKey.entries.filter { p.contains("code.${it.name}") }.associateWith { p.getInt("code.${it.name}", 0) }
    }

    fun setTarget(key: RemoteKey, target: String?) {
        targets = targets + (key to target)
        prefs?.edit()?.putString("target.${key.name}", target)?.apply()
    }

    /** Whether [key] has codes to listen for, taught or default. */
    fun known(key: RemoteKey) = key in taught || key.defaultCodes.isNotEmpty()

    /**
     * Learns [event] for the button being taught. Keys Mii_OS needs to get around (arrows, OK,
     * Back, Home, volume) can't be taught; returns whether the press was used.
     */
    fun learn(event: KeyEvent): Boolean {
        val key = teaching ?: return false
        if (event.keyCode in reserved) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            val code = codeOf(event)
            // one code, one button: teaching it to this one takes it off the other
            taught = taught.filterValues { it != code } + (key to code)
            prefs?.edit()?.apply {
                RemoteKey.entries.filter { taught[it] == null }.forEach { remove("code.${it.name}") }
                putInt("code.${key.name}", code)
            }?.apply()
        } else {
            teaching = null
        }
        return true
    }

    /** The button [event] came from, if it's one with a job. */
    fun keyFor(event: KeyEvent): RemoteKey? {
        val code = codeOf(event)
        return RemoteKey.entries.firstOrNull { key ->
            targets[key] != null && (taught[key]?.let { it == code } ?: (code in key.defaultCodes))
        }
    }

    /** How to start [target]: the TV channel, or an app's TV (or phone) launcher entry. */
    fun intentFor(context: Context, target: String): Intent? {
        if (target == TvActivity.CHANNEL_ID) return TvActivity.intent(context)
        val pm = context.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(target) ?: pm.getLaunchIntentForPackage(target))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    // Some vendor buttons have no Android key code; they're told apart by their scan code instead.
    private fun codeOf(event: KeyEvent) = if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) -event.scanCode else event.keyCode

    private val reserved = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER,
    )
}
