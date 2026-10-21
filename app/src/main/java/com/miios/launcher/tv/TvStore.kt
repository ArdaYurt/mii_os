package com.miios.launcher.tv

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * The user's playlist and guide addresses, plus cached copies of both, so the TV channel
 * starts on the last channel straight away and refreshes the lists in the background.
 * Every function here does disk or network work: call it off the main thread.
 */
class TvStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "tv").apply { mkdirs() }
    private val playlistFile = File(dir, "playlist.m3u")
    private val guideFile = File(dir, "guide.xml")

    var playlistUrl: String
        get() = prefs.getString("playlist", "").orEmpty()
        set(value) = prefs.edit().putString("playlist", value).apply()

    /** Optional; when empty, the guide address inside the playlist is used. */
    var guideUrl: String
        get() = prefs.getString("guide", "").orEmpty()
        set(value) = prefs.edit().putString("guide", value).apply()

    /** Remembered by address, so a reordered playlist still opens on the same channel. */
    var lastChannelUrl: String
        get() = prefs.getString("last", "").orEmpty()
        set(value) = prefs.edit().putString("last", value).apply()

    /** The cached playlist for the current address, or null if there isn't one. */
    fun cachedPlaylist(): Playlist? {
        if (!isCached(playlistFile, "playlistFor", playlistUrl)) return null
        return M3u.parse(playlistFile.readText())
    }

    /** Downloads the playlist again if [force] or the cached copy is old. Returns null when the cache is still fresh. */
    fun refreshPlaylist(force: Boolean): Playlist? {
        val url = playlistUrl
        if (!force && isFresh(playlistFile, "playlistFor", url, PLAYLIST_MAX_AGE)) return null
        download(url, playlistFile)
        prefs.edit().putString("playlistFor", url).apply()
        return M3u.parse(playlistFile.readText())
    }

    /** Loads the guide for [channels], downloading it when the cached copy is old. Returns null if there's no guide address. */
    fun guide(channels: List<TvChannel>, fallbackUrl: String?, force: Boolean): Guide? {
        val url = guideUrl.ifBlank { fallbackUrl.orEmpty() }
        if (url.isBlank()) return null
        if (force || !isFresh(guideFile, "guideFor", url, GUIDE_MAX_AGE)) {
            download(url, guideFile)
            prefs.edit().putString("guideFor", url).apply()
        }
        val now = System.currentTimeMillis()
        return openMaybeGzip(guideFile).use { Xmltv.parse(it, channels, now - HOUR, now + 12 * HOUR) }
    }

    private fun isCached(file: File, key: String, url: String) =
        url.isNotBlank() && file.exists() && prefs.getString(key, null) == url

    private fun isFresh(file: File, key: String, url: String, maxAge: Long) =
        isCached(file, key, url) && System.currentTimeMillis() - file.lastModified() < maxAge

    private fun download(address: String, target: File) {
        var url = URL(address)
        var redirects = 0
        while (true) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                // Followed by hand: HttpURLConnection won't follow http <-> https redirects, which IPTV hosts use a lot.
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                if (code in 300..399 && location != null && redirects++ < 5) {
                    url = URL(url, location)
                    continue
                }
                if (code !in 200..299) throw IOException("the server answered $code")
                val part = File(target.path + ".part")
                connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it, 64 * 1024) } }
                if (!part.renameTo(target)) throw IOException("couldn't save it")
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Guides are often served as .xml.gz; tell by the gzip magic bytes rather than the name. */
    private fun openMaybeGzip(file: File): InputStream {
        val input = BufferedInputStream(FileInputStream(file), 64 * 1024)
        input.mark(2)
        val gzip = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        return if (gzip) GZIPInputStream(input, 64 * 1024) else input
    }

    companion object {
        const val USER_AGENT = "Mii_OS/1.0 (Android TV)"
        private const val HOUR = 60 * 60 * 1000L
        private const val PLAYLIST_MAX_AGE = 6 * HOUR
        private const val GUIDE_MAX_AGE = 12 * HOUR
    }
}
