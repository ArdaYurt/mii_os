package com.miios.launcher.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.miios.launcher.storage.PackFolders

/**
 * Your own font for Mii_OS's text, from the font pack (font.ttf or font.otf, copied over by
 * tools/font/push-font.bat). Without one, Mii_OS uses the box's own font.
 */
object MiiFont {
    var family: FontFamily? by mutableStateOf(null)
        private set
    /** The same font for the HOME Menu, which is drawn with plain Android views. */
    var typeface: android.graphics.Typeface? = null
        private set
    private var stamp = 0L

    fun refresh(context: Context) {
        val file = PackFolders.find(context, "font", "font", listOf("ttf", "otf"))
        val next = file?.let { it.lastModified() + it.length() } ?: 0L
        if (next == stamp) return
        stamp = next
        val face = file?.let { runCatching { android.graphics.Typeface.createFromFile(it) }.getOrNull() }
        typeface = face
        family = face?.let { FontFamily(androidx.compose.ui.text.font.Typeface(it)) }
    }
}
