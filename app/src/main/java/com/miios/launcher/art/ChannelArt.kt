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
import java.io.IOException

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

    suspend fun load(context: Context, app: InstalledApp, width: Int, height: Int): ChannelArt {
        val key = key(app, width, height)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val art = fromArtPack(context, app.packageName, width, height)
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
