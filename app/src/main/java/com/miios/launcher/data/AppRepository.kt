package com.miios.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** One app the launcher can show as a channel. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val launchIntent: Intent,
    /** The app's own 320×180 TV banner, if it ships one. */
    val banner: Drawable?,
    val icon: Drawable,
)

object AppRepository {

    /**
     * Lists launchable apps, preferring each app's TV entry point over its phone one.
     * Runs on a background thread: PackageManager calls can take tens of milliseconds.
     */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val byPackage = LinkedHashMap<String, InstalledApp>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo
                val pkg = activity.packageName
                if (pkg == context.packageName || pkg in byPackage) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                byPackage[pkg] = InstalledApp(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    launchIntent = launch,
                    banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                    icon = info.loadIcon(pm),
                )
            }
        }
        return byPackage.values.sortedWith(
            compareBy<InstalledApp> { pinnedRank(it.packageName) }.thenBy { it.label.lowercase() },
        )
    }

    /**
     * Channels that always come first, in this order. AirPlay receivers lead so iPhone and iPad
     * screen sharing is one press away; the box has no AirPlay of its own.
     */
    private val pinned = listOf(
        "com.ionitech.airscreen",          // AirScreen (AirPlay, Cast, Miracast receiver)
        "com.apple.atve.androidtv.appletv", // Apple TV
        "com.plexapp.android",
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
    )

    private fun pinnedRank(pkg: String): Int = pinned.indexOf(pkg).let { if (it < 0) pinned.size else it }
}
