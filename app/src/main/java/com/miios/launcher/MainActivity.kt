package com.miios.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.miios.launcher.data.AppRepository
import com.miios.launcher.data.InstalledApp
import com.miios.launcher.sound.Sounds
import com.miios.launcher.art.ChannelStyles
import com.miios.launcher.tv.TvActivity
import com.miios.launcher.ui.LauncherActions
import com.miios.launcher.ui.LauncherScreen
import com.miios.launcher.ui.SettingsItem
import com.miios.launcher.ui.UpdateState
import com.miios.launcher.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<InstalledApp>>(emptyList())
    private val homePresses = mutableIntStateOf(0)
    private val update = mutableStateOf<UpdateState>(UpdateState.None)
    private var dismissedUpdate = 0

    private val actions = object : LauncherActions {
        override fun launch(app: InstalledApp) {
            val intent = app.launchIntent
            if (intent != null) start(intent) else openStore(app.packageName)
        }

        override fun openAppInfo(app: InstalledApp) {
            // The TV channel's Menu opens its own setup, where the playlist is changed.
            if (app.packageName == TvActivity.CHANNEL_ID) return start(TvActivity.intent(this@MainActivity, setup = true))
            if (!app.installed) return openStore(app.packageName)
            start(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        override fun openSystemSetting(item: SettingsItem) {
            if (item == SettingsItem.Updates) return checkForUpdate(force = true)
            val action = when (item) {
                SettingsItem.Network -> Settings.ACTION_WIFI_SETTINGS
                SettingsItem.Display -> Settings.ACTION_DISPLAY_SETTINGS
                SettingsItem.Apps -> Settings.ACTION_APPLICATION_SETTINGS
                SettingsItem.DateTime -> Settings.ACTION_DATE_SETTINGS
                SettingsItem.System -> Settings.ACTION_SETTINGS
                SettingsItem.Updates -> return
            }
            // Google TV doesn't have every settings page; fall back to the main one.
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!tryStart(intent)) start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        override fun installUpdate() {
            val available = update.value as? UpdateState.Available ?: return
            if (!Updater.canInstall(this@MainActivity)) return
            update.value = UpdateState.Downloading(-1f)
            lifecycleScope.launch {
                try {
                    Updater.install(this@MainActivity) { p -> runOnUiThread { update.value = UpdateState.Downloading(p) } }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Couldn't download the update. Try again later.", Toast.LENGTH_LONG).show()
                    dismissedUpdate = available.version
                }
                // Android's installer takes over from here; it asks before installing.
                update.value = UpdateState.None
            }
        }

        override fun dismissUpdate() {
            (update.value as? UpdateState.Available)?.let { dismissedUpdate = it.version }
            update.value = UpdateState.None
        }

        override fun openScreenShare() {
            val app = apps.value.firstOrNull { it.packageName == ChannelStyles.AIRSCREEN }
            val intent = app?.launchIntent
            if (intent != null) start(intent) else openStore(ChannelStyles.AIRSCREEN)
        }
    }

    /** Play Store page for a favourite channel that isn't installed yet. */
    private fun openStore(pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!tryStart(market)) {
            Toast.makeText(this, "Play Store isn't available here. Install $pkg from another device.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        CrashLog.takeLast(this)?.let {
            Toast.makeText(this, "Mii_OS restarted after an error: $it", Toast.LENGTH_LONG).show()
        }
        setContent {
            LauncherScreen(
                apps = apps.value,
                homePresses = homePresses.intValue,
                update = update.value,
                actions = actions,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // picks up a sound pack copied over while Mii_OS was in the background
        Sounds.load(this)
        // Refresh in the background on every return so newly installed apps appear without a restart.
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { AppRepository.load(this@MainActivity) }
            if (loaded.map { it.packageName } != apps.value.map { it.packageName }) apps.value = loaded
        }
        checkForUpdate(force = false)
    }

    private fun checkForUpdate(force: Boolean) {
        lifecycleScope.launch {
            val newer = Updater.check(this@MainActivity, force)
            if (newer == null) {
                if (force) Toast.makeText(this@MainActivity, "Mii_OS is up to date", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // "Later" holds that version back until the app restarts, unless asked from Settings.
            if ((force || newer != dismissedUpdate) && update.value is UpdateState.None) update.value = UpdateState.Available(newer)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while Mii_OS is already showing: go back to the first page.
        homePresses.intValue++
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "That screen isn't available on this device", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "That app can't be opened from here", Toast.LENGTH_SHORT).show()
        }
    }
}
