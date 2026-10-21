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
)

object ChannelStyles {
    const val APPLE_TV = "com.apple.atve.androidtv.appletv"
    const val YOUTUBE = "com.google.android.youtube.tv"
    const val PLEX = "com.plexapp.android"
    const val PRIME_VIDEO = "com.amazon.amazonvideo.livingroom"
    const val STREMIO = "com.stremio.one"
    const val AIRSCREEN = "com.ionitech.airscreen"
    const val TV = TvActivity.CHANNEL_ID

    private val styles = mapOf(
        APPLE_TV to ChannelStyle("Apple TV", Color(0xFF4A4A4E), Color(0xFF111113), Color.White, MiiIcon.Display),
        YOUTUBE to ChannelStyle("YouTube", Color(0xFFFF4545), Color(0xFFCC0000), Color.White, MiiIcon.Play),
        PLEX to ChannelStyle("Plex", Color(0xFF3A3D42), Color(0xFF1A1C1F), Color(0xFFEBAF00), MiiIcon.Chevron),
        PRIME_VIDEO to ChannelStyle("Prime Video", Color(0xFF3CC0F0), Color(0xFF0A68A0), Color.White, MiiIcon.PlayCircle),
        STREMIO to ChannelStyle("Stremio", Color(0xFF8A6CF7), Color(0xFF4325B0), Color.White, MiiIcon.Film),
        AIRSCREEN to ChannelStyle("Screen Share", Color(0xFF8ADAF8), Color(0xFF2F8FD8), Color.White, MiiIcon.Screens),
        TV to ChannelStyle("TV", Color(0xFFFFFFFF), Color(0xFFDDE2E7), Color(0xFF34BEED), MiiIcon.Antenna),
    )

    fun forPackage(pkg: String): ChannelStyle? = styles[pkg]
}
