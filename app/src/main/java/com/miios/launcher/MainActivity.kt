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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<InstalledApp>>(emptyList())
    private val homePresses = mutableIntStateOf(0)
    private val homeCaptureOn = mutableStateOf(true)

    private val actions = object : LauncherActions {
        override fun launch(app: InstalledApp) = start(app.launchIntent)
        override fun openAppInfo(app: InstalledApp) = start(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        override fun openSettings() = start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        override fun openHomeSetup() = start(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
