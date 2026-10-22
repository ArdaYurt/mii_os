package com.miios.launcher.storage

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Where your own logo and sound packs live. The laptop scripts copy them to /sdcard/Mii_OS/<pack>,
 * which adb can write to on every box (some boxes refuse adb writes into an app's Android/data
 * folder). Reading it needs READ_EXTERNAL_STORAGE, which the scripts grant once with pm grant.
 * The app's own Android/data folder is still read as a fallback, for packs copied there before.
 */
object PackFolders {

    fun shared(pack: String): File = File(Environment.getExternalStorageDirectory(), "Mii_OS/$pack")

    fun own(context: Context, pack: String): File = File(context.getExternalFilesDir(null), pack)

    /** Both folders, the shared one first. */
    fun all(context: Context, pack: String): List<File> = listOf(shared(pack), own(context, pack))

    /** The first file named [name] with one of [extensions], from the shared folder first. */
    fun find(context: Context, pack: String, name: String, extensions: List<String>): File? =
        all(context, pack).asSequence()
            .flatMap { dir -> extensions.asSequence().map { File(dir, "$name.$it") } }
            .firstOrNull { runCatching { it.isFile }.getOrDefault(false) }

    /** Changes whenever a file in either folder is added, removed or replaced. */
    fun stamp(context: Context, pack: String): Long =
        all(context, pack).sumOf { dir ->
            runCatching { dir.listFiles()?.sumOf { it.name.hashCode() + it.lastModified() + it.length() } }.getOrNull() ?: 0L
        }
}
