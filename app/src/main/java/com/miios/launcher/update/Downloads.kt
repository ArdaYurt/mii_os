package com.miios.launcher.update

import androidx.compose.runtime.mutableStateOf

/**
 * The download manager: the one app being downloaded or installed right now (a Mii_OS update or
 * a channel such as Screen Share), as the home screen's download bar shows it.
 */
object Downloads {

    enum class Stage { Downloading, Checking, Installing, Done, Cancelled, Failed }

    /**
     * [progress] runs 0→1, or is negative while the size isn't known; [message] says why it failed.
     * [id] tells one download from the next, so a finished bar doesn't hide a newer one.
     */
    data class Download(
        val id: Long,
        val pkg: String,
        val title: String,
        val stage: Stage,
        val progress: Float = -1f,
        val doneBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val message: String? = null,
    ) {
        /** Still on its way: a second press waits for this one instead of starting another. */
        val active: Boolean get() = stage == Stage.Downloading || stage == Stage.Checking || stage == Stage.Installing
    }

    val current = mutableStateOf<Download?>(null)

    fun start(pkg: String, title: String): Download =
        Download(System.nanoTime(), pkg, title, Stage.Downloading).also { current.value = it }

    /** Moves the download for [pkg] on, if it's still the one shown. */
    fun update(pkg: String, change: (Download) -> Download) {
        val now = current.value ?: return
        if (now.pkg == pkg) current.value = change(now)
    }

    fun activeFor(pkg: String): Download? = current.value?.takeIf { it.pkg == pkg && it.active }

    /** Hides a finished bar, unless a newer download has taken its place. */
    fun clear(id: Long) {
        if (current.value?.id == id) current.value = null
    }
}
