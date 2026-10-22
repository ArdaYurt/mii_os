package com.miios.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.miios.launcher.art.ChannelStyles
import com.miios.launcher.tv.TvActivity

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
                if (pkg == context.packageName || pkg in byPackage || pkg in hidden) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                byPackage[pkg] = InstalledApp(
                    packageName = pkg,
                    // the AirPlay receiver is the box's Screen Share channel, so it goes by that name
                    label = if (pkg == ChannelStyles.SCREEN_SHARE) "Screen Share" else info.loadLabel(pm).toString(),
                    launchIntent = launch,
                    banner = sharp(pm, activity.applicationInfo, if (activity.banner != 0) activity.banner else activity.applicationInfo.banner)
                        ?: activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                    icon = sharp(pm, activity.applicationInfo, if (info.iconResource != 0) info.iconResource else activity.iconResource) ?: info.loadIcon(pm),
                )
            }
        }
        // The TV channel (IPTV) is part of Mii_OS itself, not a separate app.
        byPackage[ChannelStyles.TV] = InstalledApp(ChannelStyles.TV, "TV", TvActivity.intent(context), null, null)
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
     * Plezy is an open-source Plex client and Nuvio an open-source Stremio alternative. Screen Share is an open-source AirPlay receiver, for
     * iPhone and iPad screen sharing.
     * TV is Mii_OS's own live IPTV channel.
     */
    val pinned = listOf(
        ChannelStyles.APPLE_TV,
        ChannelStyles.YOUTUBE,
        ChannelStyles.PLEX,
        ChannelStyles.PLEZY,
        ChannelStyles.PRIME_VIDEO,
        ChannelStyles.STREMIO,
        ChannelStyles.NUVIO,
        ChannelStyles.SCREEN_SHARE,
        ChannelStyles.TV,
    )

    /**
     * The box's own Settings app: Mii_OS has its own Settings in the dock, so a second tile is noise.
     * Google TV's stock launcher is hidden too, in case it's still installed.
     */
    private val hidden = setOf(
        "com.android.tv.settings",
        "com.android.settings",
        "com.google.android.tv.settings",
        "com.google.android.apps.tv.launcherx",
    )

    /**
     * Loads a drawable at the highest density the app ships (xxxhdpi), not the box's own (xhdpi for a
     * 1080p UI). Banners and icons are then decoded from 4× the pixels, which is what keeps them crisp
     * when they're drawn large on the channel screen.
     */
    private fun sharp(pm: PackageManager, app: ApplicationInfo, resId: Int): Drawable? {
        if (resId == 0) return null
        return try {
            pm.getResourcesForApplication(app).getDrawableForDensity(resId, DisplayMetrics.DENSITY_XXXHIGH, null)
        } catch (e: Exception) {
            null
        }
    }

    private fun pinnedRank(pkg: String): Int = pinned.indexOf(pkg).let { if (it < 0) pinned.size else it }
}
