package com.miios.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.miios.launcher.art.ChannelStyles

/** One app the launcher can show as a channel. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Null for a favourite channel whose app isn't installed yet; Start then opens the Play Store. */
    val launchIntent: Intent?,
    /** The app's own 320×180 TV banner, if it ships one. */
    val banner: Drawable?,
    val icon: Drawable?,
) {
    val installed: Boolean get() = launchIntent != null
}

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
        // Favourite channels always have a slot, even before the app is installed.
        for (pkg in pinned) {
            if (pkg !in byPackage) {
                byPackage[pkg] = InstalledApp(pkg, ChannelStyles.forPackage(pkg)?.name ?: pkg, null, null, null)
            }
        }
        return byPackage.values.sortedWith(
            compareBy<InstalledApp> { pinnedRank(it.packageName) }.thenBy { it.label.lowercase() },
        )
    }

    /**
     * Favourite channels: always shown first, in this order, with Mii_OS art.
     * AirScreen gives iPhone and iPad screen sharing, since the box has no AirPlay of its own.
     */
    val pinned = listOf(
        ChannelStyles.APPLE_TV,
        ChannelStyles.YOUTUBE,
        ChannelStyles.PLEX,
        ChannelStyles.PRIME_VIDEO,
        ChannelStyles.AIRSCREEN,
    )

    private fun pinnedRank(pkg: String): Int = pinned.indexOf(pkg).let { if (it < 0) pinned.size else it }
}
