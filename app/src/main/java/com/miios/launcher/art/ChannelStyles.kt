package com.miios.launcher.art

import androidx.compose.ui.graphics.Color
import com.miios.launcher.tv.TvActivity

/** What sits next to the name on a channel's art. Simple shapes, drawn in code so they're sharp at any resolution. */
enum class Glyph { None, Play, Screens }

/** Mii_OS art for a favourite channel: a colour field, the name, and an optional glyph. */
data class ChannelStyle(
    val name: String,
    val top: Color,
    val bottom: Color,
    val ink: Color,
    val glyph: Glyph = Glyph.None,
)

object ChannelStyles {
    const val APPLE_TV = "com.apple.atve.androidtv.appletv"
    const val YOUTUBE = "com.google.android.youtube.tv"
    const val PLEX = "com.plexapp.android"
    const val PRIME_VIDEO = "com.amazon.amazonvideo.livingroom"
    const val AIRSCREEN = "com.ionitech.airscreen"
    const val TV = TvActivity.CHANNEL_ID

    private val styles = mapOf(
        APPLE_TV to ChannelStyle("Apple TV", Color(0xFF3A3A3C), Color(0xFF0B0B0C), Color.White),
        YOUTUBE to ChannelStyle("YouTube", Color(0xFFFF3B3B), Color(0xFFD40000), Color.White, Glyph.Play),
        PLEX to ChannelStyle("Plex", Color(0xFF34373B), Color(0xFF1C1E21), Color(0xFFEBAF00)),
        PRIME_VIDEO to ChannelStyle("Prime Video", Color(0xFF2CB5EA), Color(0xFF0A6FA6), Color.White),
        AIRSCREEN to ChannelStyle("Screen Share", Color(0xFF7FD3F7), Color(0xFF2F8FD8), Color.White, Glyph.Screens),
        TV to ChannelStyle("TV", Color(0xFFFFFFFF), Color(0xFFDDE2E7), Color(0xFF34BEED)),
    )

    fun forPackage(pkg: String): ChannelStyle? = styles[pkg]
}
