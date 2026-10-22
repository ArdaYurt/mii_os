package com.miios.launcher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.graphics.Bitmap
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miios.launcher.MainActivity
import com.miios.launcher.sound.Sfx
import com.miios.launcher.sound.Sounds

/**
 * Catches the remote's Home button. On the Mii_OS home screen it goes back to the first page;
 * inside any other app it opens the HOME Menu over it first, like the Wii: Home again or OK goes
 * to Mii_OS, Back closes the menu and stays in the app.
 */
class HomeButtonService : AccessibilityService() {

    private var menu: HomeMenu? = null
    /** Whether the app on screen is Mii_OS's own home screen, from window changes. */
    private var atHome = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        menu = HomeMenu(this)
        Sounds.load(this)
        RemoteButtons.load(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // The volume keys change the TV's volume over HDMI, which shows nothing on screen, so
        // Mii_OS plays a sound for them. They always pass on untouched, even over the menu.
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) Sounds.volumeKey(event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return false
        }
        val menu = menu ?: return false
        val up = event.action == KeyEvent.ACTION_UP
        if (menu.showing) {
            // The menu owns the remote while it's open; the app underneath gets nothing.
            if (up) when (event.keyCode) {
                KeyEvent.KEYCODE_HOME -> goHome()
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    if (menu.choice == HomeMenu.Choice.Home) goHome() else close()
                KeyEvent.KEYCODE_BACK -> close()
                KeyEvent.KEYCODE_DPAD_LEFT -> move(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> move(1)
            }
            return true
        }
        // Settings › Remote buttons is waiting for a button to learn.
        if (RemoteButtons.learn(event)) {
            if (up) Sounds.play(Sfx.Select)
            return true
        }
        RemoteButtons.keyFor(event)?.let { key ->
            // Started on the press, not the release, so it feels instant; held buttons don't repeat it.
            if (!up && event.repeatCount == 0) RemoteButtons.targets[key]?.let(::openTarget)
            return true
        }
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (up) {
            if (atHome) {
                openMiiOs()
            } else {
                Sounds.play(Sfx.Home)
                com.miios.launcher.ui.MiiFont.refresh(this)
                captureBackdrop()
                menu.show()
            }
        }
        return true
    }

    /**
     * Takes a picture of the app on screen and hands the menu a small blurred copy for its
     * background. Protected video comes back black or not at all; the menu then just dims it.
     */
    private fun captureBackdrop() {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val picture = runCatching {
                            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let { softBlur(it) }
                        }.getOrNull()
                        buffer.close()
                        if (picture != null) menu?.setBackdrop(picture)
                    }

                    override fun onFailure(errorCode: Int) = Unit
                },
            )
        } catch (e: Exception) {
            // older service settings without the screenshot permission: dim only
        }
    }

    private fun move(dx: Int) {
        val before = menu?.choice
        menu?.move(dx)
        if (menu?.choice != before) Sounds.play(Sfx.Move)
    }

    private fun close() {
        Sounds.play(Sfx.Back)
        menu?.hide()
    }

    private fun goHome() {
        Sounds.play(Sfx.HomeExit)
        // the home screen settles in over the sound's length, so both end together
        openMiiOs(returnMs = Sounds.lengthOf(Sfx.HomeExit).coerceIn(400L, 2500L))
        menu?.hide()
    }

    private fun openMiiOs(returnMs: Long = 0L) {
        atHome = true
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ).putExtra(MainActivity.EXTRA_RETURN_MS, returnMs),
        )
    }

    private fun openTarget(target: String) {
        val intent = RemoteButtons.intentFor(this, target) ?: return
        Sounds.play(Sfx.Launch)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // uninstalled since it was chosen: the button does nothing rather than crash the service
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || menu?.showing == true) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Only full-screen activities count; toasts, dialogs and the keyboard also send this event.
        val isActivity = try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (isActivity) atHome = pkg == packageName && cls == MainActivity::class.java.name
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        menu?.hide()
        super.onDestroy()
    }

    companion object {
        /** Whether the HOME Menu is on, as of the last [refresh]. */
        var on by mutableStateOf(false)
            private set

        fun refresh(context: Context) {
            on = isEnabled(context)
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val self = ComponentName(context, HomeButtonService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(self, ignoreCase = true) }
        }
    }
}

/**
 * A soft blur, cheap enough to run on every HOME press: shrink the picture to a tiny size, box-blur
 * it there, and let the image view scale it back up smoothly.
 */
private fun softBlur(source: Bitmap): Bitmap {
    val full = source.copy(Bitmap.Config.ARGB_8888, false)
    val w = 160
    val h = (160f * full.height / full.width).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(full, w, h, true)
    full.recycle()
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(3) {
        boxPass(px, tmp, w, h, 3, horizontal = true)
        boxPass(tmp, px, w, h, 3, horizontal = false)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    for (line in 0 until lines) {
        for (i in 0 until len) {
            var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
            for (k in -r..r) {
                val j = (i + k).coerceIn(0, len - 1)
                val c = if (horizontal) src[line * w + j] else src[j * w + line]
                a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
            }
            val out = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
        }
    }
}
