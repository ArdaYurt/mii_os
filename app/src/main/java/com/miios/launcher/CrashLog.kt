package com.miios.launcher

import android.content.Context
import java.io.File

/**
 * Keeps the last crash on the box, so a channel screen that "just goes back home" can be traced
 * without a computer: the next start shows the error in a toast, and the full stack trace stays in
 * last_crash.txt (adb shell run-as com.miios.launcher cat files/last_crash_seen.txt).
 */
object CrashLog {

    private const val FILE = "last_crash.txt"
    private const val SEEN = "last_crash_seen.txt"

    fun install(context: Context) {
        val file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(file, previous))
    }

    /** The first line of a crash not yet shown, or null. The full trace is kept as [SEEN]. */
    fun takeLast(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val first = runCatching { file.useLines { it.firstOrNull() } }.getOrNull()
        file.renameTo(File(context.filesDir, SEEN))
        return first?.take(160)
    }

    private class Handler(
        private val file: File,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching { file.writeText(error.stackTraceToString()) }
            previous?.uncaughtException(thread, error)
        }
    }
}
