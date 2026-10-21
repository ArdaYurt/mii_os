package com.miios.launcher.tv

/** One live channel from the user's M3U playlist. */
data class TvChannel(
    val number: Int,
    val name: String,
    val url: String,
    val group: String,
    val tvgId: String,
    val tvgName: String,
    /** Headers some providers require, from #EXTVLCOPT lines. */
    val userAgent: String?,
    val referrer: String?,
)

class Playlist(
    val channels: List<TvChannel>,
    /** Guide address the playlist names in its #EXTM3U header (url-tvg), if any. */
    val guideUrl: String?,
)

/** Reads the M3U / M3U8 playlists IPTV providers hand out. */
object M3u {
    private val attribute = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): Playlist {
        val channels = ArrayList<TvChannel>()
        var guideUrl: String? = null
        var info = ""
        var group: String? = null
        var userAgent: String? = null
        var referrer: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attributes(line)
                    guideUrl = (attrs["url-tvg"] ?: attrs["x-tvg-url"])?.split(',')?.firstOrNull()?.trim()?.ifEmpty { null }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = line
                    group = null
                    userAgent = null
                    referrer = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substring(8).trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substring(11)
                    val value = option.substringAfter('=').trim()
                    when {
                        option.startsWith("http-user-agent=", ignoreCase = true) -> userAgent = value
                        option.startsWith("http-referrer=", ignoreCase = true) ||
                            option.startsWith("http-referer=", ignoreCase = true) -> referrer = value
                    }
                }
                line.startsWith("#") -> Unit
                "://" in line -> {
                    val attrs = attributes(info)
                    val position = channels.size + 1
                    val name = title(info).ifEmpty { attrs["tvg-name"].orEmpty() }.ifEmpty { "Channel $position" }
                    channels += TvChannel(
                        number = attrs["tvg-chno"]?.toIntOrNull() ?: position,
                        name = name,
                        url = line,
                        group = attrs["group-title"]?.trim()?.ifEmpty { null } ?: group.orEmpty(),
                        tvgId = attrs["tvg-id"].orEmpty(),
                        tvgName = attrs["tvg-name"].orEmpty(),
                        userAgent = userAgent ?: attrs["user-agent"],
                        referrer = referrer,
                    )
                    info = ""
                    group = null
                    userAgent = null
                    referrer = null
                }
            }
        }
        return Playlist(channels, guideUrl)
    }

    private fun attributes(line: String): Map<String, String> =
        attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    /** The channel name: everything after the first comma that isn't inside a quoted attribute. */
    private fun title(extinf: String): String {
        var quoted = false
        for (i in extinf.indices) {
            when (extinf[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return extinf.substring(i + 1).trim()
            }
        }
        return ""
    }
}
