package com.miios.launcher.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Lets the laptop scripts copy files Android hides from Mii_OS in shared storage (a font isn't a
 * photo or a sound, so /sdcard/Mii_OS/font can't be read) straight into Mii_OS's own folder:
 *
 *     adb shell content write --uri content://com.miios.launcher.packs/font/font.ttf < font.ttf
 *
 * Only adb's shell user may call it; any app on the box gets a SecurityException.
 */
class PackProvider : ContentProvider() {

    override fun onCreate() = true

    private fun target(uri: Uri): File {
        if (Binder.getCallingUid() != SHELL_UID) throw SecurityException("Only adb can copy packs to Mii_OS")
        val (pack, name) = uri.pathSegments.takeIf { it.size == 2 } ?: throw IllegalArgumentException("Use /<pack>/<file>")
        require(pack in PACKS) { "Unknown pack $pack" }
        require(NAME.matches(name)) { "Bad file name $name" }
        val dir = PackFolders.own(context!!, pack).apply { mkdirs() }
        return File(dir, name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = target(uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(if ("w" in mode) "wt" else "r"))
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (target(uri).delete()) 1 else 0

    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        /** Android's fixed user id for adb shell (Process.SHELL_UID, hidden from apps). */
        const val SHELL_UID = 2000
        val PACKS = setOf("font")
        val NAME = Regex("[a-z0-9_-]{1,40}\\.(ttf|otf)")
    }
}
