package com.miios.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
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
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
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
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (up) {
            if (atHome) {
                openMiiOs()
            } else {
                Sounds.play(Sfx.Home)
                menu.show()
            }
        }
        return true
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
        Sounds.play(Sfx.Select)
        openMiiOs()
        menu?.hide()
    }

    private fun openMiiOs() {
        atHome = true
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
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
