package com.miios.launcher.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.miios.launcher.data.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

sealed interface ChannelArt {
    /** Art that fills the whole tile: an art-pack image or the app's banner. */
    data class FullBleed(val image: ImageBitmap) : ChannelArt

    /** No banner: the app icon centred on a colour taken from the icon itself. */
    data class IconOnColor(val icon: ImageBitmap, val background: Color) : ChannelArt

    /** A favourite channel's Mii_OS art, drawn as vector shapes and text: sharp at 1080p and 4K. */
    data class Styled(val style: ChannelStyle) : ChannelArt

    /** Nothing to show (no banner, icon or style). */
    data object Blank : ChannelArt
}

/**
 * Resolves art for a channel in this order: the art pack folder, Mii_OS art for favourites,
 * the app's banner, the app's icon.
 * Images are decoded at the size they are drawn, never at full 4K, to stay light on the box's 2 GB.
 */
object ChannelArtLoader {

    private val cache = object : LruCache<String, ChannelArt>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ChannelArt): Int = when (value) {
            is ChannelArt.FullBleed -> value.image.width * value.image.height * 4
            is ChannelArt.IconOnColor -> value.icon.width * value.icon.height * 4
            is ChannelArt.Styled, ChannelArt.Blank -> 1
        }
    }

    fun cached(app: InstalledApp, width: Int, height: Int): ChannelArt? = cache.get(key(app, width, height))

    /** Bumped when the logo pack changes, so every tile loads its art again. */
    var version by mutableIntStateOf(0)
        private set
    private var packStamp = -1L

    /**
     * Logo pack: the owner's own images in Android/data/com.miios.launcher/files/logos, named after
     * the channel (plex.png, apple-tv.png, prime-video.png...) or the app's package name. They stay
     * on the box; tools/logos/push-logos.bat copies them from a laptop.
     */
    fun logoFolder(context: Context): File = File(context.getExternalFilesDir(null), "logos")

    /** Notices a changed logo pack (called on resume). */
    fun refresh(context: Context) {
        val dir = logoFolder(context).apply { mkdirs() }
        val stamp = dir.listFiles()?.sumOf { it.lastModified() + it.length() } ?: 0L
        if (packStamp != -1L && stamp != packStamp) {
            cache.evictAll()
            version++
        }
        packStamp = stamp
    }

    private val imageTypes = listOf("png", "webp", "jpg", "jpeg")

    private fun fromLogoPack(context: Context, app: InstalledApp, width: Int, height: Int): ChannelArt? {
        val dir = logoFolder(context)
        val names = listOf(app.packageName) + ChannelStyles.aliasesFor(app.packageName) + slug(app.label)
        val file = names.flatMap { n -> imageTypes.map { File(dir, "$n.$it") } }.firstOrNull { it.isFile } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return ChannelArt.FullBleed(bitmap.asImageBitmap())
    }

    /** "Prime Video" -> "prime-video", "Disney+" -> "disney" */
    private fun slug(label: String) = label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    suspend fun load(context: Context, app: InstalledApp, width: Int, height: Int): ChannelArt {
        val key = key(app, width, height)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val art = runCatching { fromLogoPack(context, app, width, height) }.getOrNull()
                ?: fromArtPack(context, app.packageName, width, height)
                ?: ChannelStyles.forApp(app.packageName, app.label)?.let { ChannelArt.Styled(it) }
                ?: app.banner?.let { ChannelArt.FullBleed(it.toBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()) }
                ?: iconArt(app, height)
            cache.put(key, art)
            art
        }
    }

    private fun key(app: InstalledApp, width: Int, height: Int) = "${app.packageName}@${width}x$height"

    private fun fromArtPack(context: Context, pkg: String, width: Int, height: Int): ChannelArt? {
        val path = "channel_art/$pkg.webp"
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                ?.let { ChannelArt.FullBleed(it.asImageBitmap()) }
        } catch (e: IOException) {
            null
        }
    }

    private fun iconArt(app: InstalledApp, tileHeight: Int): ChannelArt {
        val drawable = app.icon ?: return ChannelArt.Blank
        val size = (tileHeight * 0.5f).toInt().coerceAtLeast(48)
        val icon = drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Average the icon down to one pixel, then darken it so white text and icons stay readable.
        val average = Color(Bitmap.createScaledBitmap(icon, 1, 1, true).getPixel(0, 0))
        val background = Color(
            red = average.red * 0.7f,
            green = average.green * 0.7f,
            blue = average.blue * 0.7f,
        )
        return ChannelArt.IconOnColor(icon.asImageBitmap(), background)
    }
}
