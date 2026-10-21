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
import com.miios.launcher.service.HomeButtonService
import com.miios.launcher.ui.LauncherActions
import com.miios.launcher.ui.LauncherScreen
import com.miios.launcher.ui.SettingsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<InstalledApp>>(emptyList())
    private val homePresses = mutableIntStateOf(0)
    private val homeCaptureOn = mutableStateOf(true)

    private val actions = object : LauncherActions {
        override fun launch(app: InstalledApp) {
            val intent = app.launchIntent
            if (intent != null) start(intent) else openStore(app.packageName)
        }

        override fun openAppInfo(app: InstalledApp) {
            if (!app.installed) return openStore(app.packageName)
            start(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        override fun openSystemSetting(item: SettingsItem) {
            val action = when (item) {
                SettingsItem.Network -> Settings.ACTION_WIFI_SETTINGS
                SettingsItem.Display -> Settings.ACTION_DISPLAY_SETTINGS
                SettingsItem.Apps -> Settings.ACTION_APPLICATION_SETTINGS
                SettingsItem.HomeButton -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                SettingsItem.DateTime -> Settings.ACTION_DATE_SETTINGS
                SettingsItem.System -> Settings.ACTION_SETTINGS
            }
            // Google TV doesn't have every settings page; fall back to the main one.
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!tryStart(intent)) start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        setContent {
            LauncherScreen(
                apps = apps.value,
                homePresses = homePresses.intValue,
                homeCaptureOn = homeCaptureOn.value,
                actions = actions,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        homeCaptureOn.value = HomeButtonService.isEnabled(this)
        // Refresh in the background on every return so newly installed apps appear without a restart.
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { AppRepository.load(this@MainActivity) }
            if (loaded.map { it.packageName } != apps.value.map { it.packageName }) apps.value = loaded
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
