package com.miios.launcher.art

import androidx.compose.ui.graphics.Color
import com.miios.launcher.tv.TvActivity
import com.miios.launcher.ui.MiiIcon

/** Mii_OS art for a favourite channel: a colour field with an emblem from the Mii_OS icon set above its name. */
data class ChannelStyle(
    val name: String,
    val top: Color,
    val bottom: Color,
    val ink: Color,
    val icon: MiiIcon,
    /** One line shown on the channel screen, for channels that need explaining (Screen Share). */
    val blurb: String? = null,
    /** The channel screen's colours, when they differ from the tile's (Spotify's screen is green). */
    val stageTop: Color = top,
    val stageBottom: Color = bottom,
)

object ChannelStyles {
    const val APPLE_TV = "com.apple.atve.androidtv.appletv"
    const val YOUTUBE = "com.google.android.youtube.tv"
    const val PLEX = "com.plexapp.android"
    /**
     * Plezy: an open-source Plex client (GPL-3.0) by edde746, installed from its GitHub releases.
     * https://github.com/edde746/plezy
     */
    const val PLEZY = "com.edde746.plezy"
    const val PRIME_VIDEO = "com.amazon.amazonvideo.livingroom"
    const val STREMIO = "com.stremio.one"
    /**
     * Nuvio TV: an open-source (GPL-3.0) app for Stremio add-ons, built natively for Android TV.
     * https://github.com/NuvioMedia/NuvioTV. Installed from the Play Store: that build has Nuvio's
     * scraper plugins switched off, which the GitHub build turns on.
     */
    const val NUVIO = "com.nuvio.app"
    /**
     * Screen Share: the open-source AirPlay receiver by jqssun (GPL-3.0, built on UxPlay), installed
     * from its GitHub releases. https://github.com/jqssun/android-airplay-server
     */
    const val SCREEN_SHARE = "io.github.jqssun.airplay"
    /** AirScreen, the closed Play Store app Screen Share used before 0.5.0. */
    const val AIRSCREEN = "com.ionitech.airscreen"
    const val TV = TvActivity.CHANNEL_ID
    const val DISNEY_PLUS = "com.disney.disneyplus"
    const val MUBI = "com.mubi"
    const val MAX = "com.wbd.stream"
    const val SPOTIFY = "com.spotify.tv.android"

    private val styles = mapOf(
        APPLE_TV to ChannelStyle("Apple TV", Color(0xFF4A4A4E), Color(0xFF111113), Color.White, MiiIcon.Display),
        YOUTUBE to ChannelStyle("YouTube", Color(0xFFFF4545), Color(0xFFCC0000), Color.White, MiiIcon.Play),
        PLEX to ChannelStyle(
            "Plex", Color(0xFF3A3D42), Color(0xFF1A1C1F), Color(0xFFEBAF00), MiiIcon.Chevron,
            stageTop = Color(0xFFF5BE1A), stageBottom = Color(0xFFB07F00),
        ),
        PLEZY to ChannelStyle(
            "Plezy", Color(0xFF2E3137), Color(0xFF111214), Color(0xFFF5BE1A), MiiIcon.Film,
            blurb = "Your Plex library in a faster, open-source player from GitHub, with no ads. " +
                "Plays your films as they are, in 4K, HDR and Dolby Vision. Sign in with your Plex account.",
            stageTop = Color(0xFF3A3D42), stageBottom = Color(0xFF0E0F11),
        ),
        PRIME_VIDEO to ChannelStyle("Prime Video", Color(0xFF3CC0F0), Color(0xFF0A68A0), Color.White, MiiIcon.PlayCircle),
        STREMIO to ChannelStyle("Stremio", Color(0xFF8A6CF7), Color(0xFF4325B0), Color.White, MiiIcon.Film),
        NUVIO to ChannelStyle(
            "Nuvio", Color(0xFF2A3350), Color(0xFF0C1022), Color.White, MiiIcon.Film,
            blurb = "An open-source app for your Stremio add-ons, made for the TV remote and lighter than Stremio. " +
                "Add each add-on by its install link in Nuvio's Settings › Addons.",
            stageTop = Color(0xFF34406A), stageBottom = Color(0xFF080B18),
        ),
        SCREEN_SHARE to ChannelStyle(
            "Screen Share", Color(0xFF8ADAF8), Color(0xFF2F8FD8), Color.White, MiiIcon.Screens,
            blurb = "Show your iPhone or iPad on the TV with AirPlay. Uses a free, open-source receiver " +
                "from GitHub, with no ads: open it, then on the iPhone choose Control Centre > Screen Mirroring.",
        ),
        TV to ChannelStyle("TV", Color(0xFFFFFFFF), Color(0xFFDDE2E7), Color(0xFF34BEED), MiiIcon.Antenna),
        DISNEY_PLUS to ChannelStyle("Disney+", Color(0xFF1F3E8C), Color(0xFF0B1A40), Color.White, MiiIcon.Plus),
        MUBI to ChannelStyle("MUBI", Color(0xFF2A2A2E), Color(0xFF0A0A0B), Color.White, MiiIcon.Dots),
        MAX to ChannelStyle("HBO Max", Color(0xFF2B4CF0), Color(0xFF0A1C8C), Color.White, MiiIcon.Spark),
        SPOTIFY to ChannelStyle(
            "Spotify", Color(0xFF2A2A2A), Color(0xFF121212), Color(0xFF1ED760), MiiIcon.Waves,
            stageTop = Color(0xFF1ED760), stageBottom = Color(0xFF0E7A36),
        ),
        KICK to ChannelStyle(
            "Kick", Color(0xFF1A1A1A), Color(0xFF050505), Color(0xFF53FC18), MiiIcon.Kick,
            stageTop = Color(0xFF53FC18), stageBottom = Color(0xFF1F8A06),
        ),
    )

    private const val KICK = "kick"

    /**
     * Older or regional builds of some apps use other package names; match those by app name.
     * Keys are lowercase labels.
     */
    private val byLabel = mapOf(
        "disney+" to DISNEY_PLUS,
        "disney plus" to DISNEY_PLUS,
        "mubi" to MUBI,
        "max" to MAX,
        "hbo max" to MAX,
        "spotify" to SPOTIFY,
        "kick" to KICK,
        "nuvio" to NUVIO,
    )

    /** Short names a logo pack can use for a channel, besides its package name and app name. */
    private val aliases = mapOf(
        APPLE_TV to listOf("apple-tv", "appletv"),
        YOUTUBE to listOf("youtube"),
        PLEX to listOf("plex"),
        PLEZY to listOf("plezy"),
        PRIME_VIDEO to listOf("prime-video", "prime"),
        STREMIO to listOf("stremio"),
        NUVIO to listOf("nuvio"),
        SCREEN_SHARE to listOf("screen-share", "airplay", "airscreen"),
        TV to listOf("tv"),
        DISNEY_PLUS to listOf("disney-plus", "disney"),
        MUBI to listOf("mubi"),
        MAX to listOf("hbo-max", "max"),
        SPOTIFY to listOf("spotify"),
    )

    fun aliasesFor(pkg: String): List<String> = aliases[pkg].orEmpty()

    fun forPackage(pkg: String): ChannelStyle? = styles[pkg]

    fun forApp(pkg: String, label: String): ChannelStyle? =
        styles[pkg] ?: byLabel[label.trim().lowercase()]?.let { styles[it] }
}
