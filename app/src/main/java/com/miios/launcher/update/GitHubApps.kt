package com.miios.launcher.update

import android.os.Build
import com.miios.launcher.art.ChannelStyles
import java.net.URL

/**
 * Open-source channels Mii_OS installs straight from their developers' GitHub releases: no Play
 * Store, no account, never bundled. Each link is the newest release's file, so a new install always
 * gets the latest version.
 */
object GitHubApps {

    /** Where a channel's app comes from: [url] is an .apk, or a .tar.gz holding one. */
    data class Source(val url: URL, val title: String)

    fun sourceFor(pkg: String): Source? = when (pkg) {
        // jqssun/android-airplay-server, GPL-3.0, built on UxPlay
        ChannelStyles.SCREEN_SHARE -> Source(
            URL("https://github.com/jqssun/android-airplay-server/releases/latest/download/app-release.apk"),
            "AirPlay Receiver",
        )
        // edde746/plezy, GPL-3.0: a Plex client for TV with direct play, HDR and Dolby Vision.
        // Its Android builds come one per processor type, each as plezy.apk in a .tar.gz.
        ChannelStyles.PLEZY -> Source(
            URL("https://github.com/edde746/plezy/releases/latest/download/plezy-android-${abi()}.tar.gz"),
            "Plezy",
        )
        // NuvioMedia/NuvioTV, GPL-3.0: plays Stremio add-ons. One .apk per processor type. This
        // build can also take scraper plugins; it ships none, and Mii_OS never points to any.
        ChannelStyles.NUVIO -> Source(
            URL("https://github.com/NuvioMedia/NuvioTV/releases/latest/download/app-full-${abi()}-release.apk"),
            "Nuvio",
        )
        else -> null
    }

    /** The build that runs best here: 64-bit when the box's Android is, otherwise 32-bit. */
    private fun abi(): String =
        Build.SUPPORTED_ABIS.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64") } ?: "armeabi-v7a"
}
