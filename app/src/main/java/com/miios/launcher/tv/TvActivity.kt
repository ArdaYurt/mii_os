package com.miios.launcher.tv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mii_OS's TV channel: live IPTV from the user's own M3U playlist, played with Media3.
 * It's a separate activity so the home screen never loads the player or the channel list,
 * and keys are handled in [dispatchKeyEvent], before Compose, so zapping is one step per press.
 */
@OptIn(UnstableApi::class)
class TvActivity : ComponentActivity() {

    private lateinit var store: TvStore
    private val ui = TvUiState()
    private var player: ExoPlayer? = null
    private var surface: SurfaceView? = null
    private var playlistGuideUrl: String? = null
    private var phoneLink: PhoneLink? = null

    private var tuneJob: Job? = null
    private var bannerJob: Job? = null
    private var digitJob: Job? = null
    private var guideJob: Job? = null
    /** Whether the current channel has already been retried as HLS after failing as a plain stream. */
    private var triedHls = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            ui.buffering = state == Player.STATE_BUFFERING
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                ui.aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            val channel = ui.channels.getOrNull(ui.current) ?: return
            when {
                // Fell too far behind a live stream: jump back to live.
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    p.seekToDefaultPosition()
                    p.prepare()
                }
                // Many providers serve HLS from addresses with no .m3u8 in them; try that once.
                !triedHls -> play(channel, asHls = true)
                else -> {
                    ui.status = "This channel isn't working right now. Try another one."
                    ui.buffering = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = TvStore(this)
        setContent { TvScreen(ui) { view -> surface = view; player?.setVideoSurfaceView(view) } }

        if (intent.getBooleanExtra(EXTRA_SETUP, false) || store.playlistUrl.isBlank()) {
            showSetup(null)
        } else {
            loadChannels(force = false)
        }
    }

    override fun onStart() {
        super.onStart()
        createPlayer()
        if (ui.mode == TvMode.Watching && ui.current >= 0) tune(ui.current)
        if (ui.mode == TvMode.Setup) startPhoneLink()
    }

    override fun onStop() {
        super.onStop()
        tuneJob?.cancel()
        player?.release()
        player = null
        stopPhoneLink()
    }

    private fun createPlayer() {
        // Live TV: start as soon as a little is buffered, rather than the 2.5 s on-demand default.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 40_000, 700, 2_000)
            .build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )
                addListener(listener)
                surface?.let { setVideoSurfaceView(it) }
            }
    }

    // ---- channels -------------------------------------------------------------------------

    private fun loadChannels(force: Boolean) {
        lifecycleScope.launch {
            val cached = if (force) null else withContext(Dispatchers.IO) { runCatching { store.cachedPlaylist() }.getOrNull() }
            if (cached != null && cached.channels.isNotEmpty()) {
                showChannels(cached, startPlaying = true)
            } else {
                ui.mode = TvMode.Loading
                ui.loadingText = "Loading your channels…"
            }
            val fresh = try {
                withContext(Dispatchers.IO) { store.refreshPlaylist(force || cached == null || cached.channels.isEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (ui.mode != TvMode.Watching) showSetup("Couldn't load that playlist (${e.message ?: "no connection"}). Check the address and try again.")
                return@launch
            }
            when {
                fresh == null -> Unit
                fresh.channels.isEmpty() -> if (ui.mode != TvMode.Watching) showSetup("That playlist has no channels in it.")
                else -> showChannels(fresh, startPlaying = ui.mode != TvMode.Watching)
            }
            loadGuide(force)
        }
    }

    private fun showChannels(playlist: Playlist, startPlaying: Boolean) {
        val playingUrl = ui.channels.getOrNull(ui.current)?.url ?: store.lastChannelUrl
        playlistGuideUrl = playlist.guideUrl
        ui.channels = playlist.channels
        ui.groups = listOf(TvUiState.ALL_CHANNELS) + playlist.channels.map { it.group }.filter { it.isNotEmpty() }.distinct()
        ui.group = 0
        ui.rows = playlist.channels.indices.toList()
        ui.mode = TvMode.Watching
        val index = playlist.channels.indexOfFirst { it.url == playingUrl }
        if (startPlaying) tune(index.coerceAtLeast(0)) else if (index >= 0) ui.current = index
    }

    private fun loadGuide(force: Boolean) {
        val channels = ui.channels
        if (channels.isEmpty()) return
        guideJob?.cancel()
        guideJob = lifecycleScope.launch {
            // The guide is extra: if it fails, channels still play without programme names.
            val guide = withContext(Dispatchers.IO) { runCatching { store.guide(channels, playlistGuideUrl, force) }.getOrNull() }
            if (guide != null) ui.guide = guide
        }
    }

    // ---- playback -------------------------------------------------------------------------

    /** Switches to a channel. The banner updates at once; with [delayMs], fast repeated presses only tune the last one. */
    private fun tune(index: Int, delayMs: Long = 0) {
        val channel = ui.channels.getOrNull(index) ?: return
        ui.current = index
        ui.status = null
        showBanner()
        store.lastChannelUrl = channel.url
        tuneJob?.cancel()
        tuneJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            play(channel, asHls = channel.url.contains(".m3u8", ignoreCase = true))
        }
    }

    private fun play(channel: TvChannel, asHls: Boolean) {
        val p = player ?: return
        triedHls = asHls
        ui.status = null
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(channel.userAgent ?: TvStore.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(15_000)
        channel.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }
        val item = MediaItem.Builder()
            .setUri(channel.url)
            .apply { if (asHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        p.setMediaSource(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)).createMediaSource(item))
        p.prepare()
        p.playWhenReady = true
    }

    /** Next or previous channel within the group the list is showing. */
    private fun zap(step: Int) {
        val rows = ui.rows.takeIf { ui.current in it } ?: ui.channels.indices.toList()
        if (rows.isEmpty()) return
        val at = rows.indexOf(ui.current).coerceAtLeast(0)
        tune(rows[Math.floorMod(at + step, rows.size)], delayMs = 250)
    }

    private fun showBanner() {
        ui.bannerVisible = true
        bannerJob?.cancel()
        bannerJob = lifecycleScope.launch {
            delay(4_000)
            ui.bannerVisible = false
        }
    }

    private fun typeDigit(d: Int) {
        if (ui.digits.length >= 4) ui.digits = ""
        ui.digits += d
        digitJob?.cancel()
        digitJob = lifecycleScope.launch {
            delay(1_500)
            val number = ui.digits.toIntOrNull()
            ui.digits = ""
            val index = ui.channels.indexOfFirst { it.number == number }
            if (index >= 0) tune(index)
        }
    }

    // ---- channel list ---------------------------------------------------------------------

    private fun openList() {
        ui.listOpen = true
        ui.listFocus = ui.rows.indexOf(ui.current) + 1
        if (ui.listFocus == 0) ui.listFocus = if (ui.rows.isEmpty()) 0 else 1
    }

    private fun changeGroup(step: Int) {
        ui.group = Math.floorMod(ui.group + step, ui.groups.size)
        val name = ui.groups[ui.group]
        ui.rows = if (ui.group == 0) ui.channels.indices.toList() else ui.channels.indices.filter { ui.channels[it].group == name }
        val at = ui.rows.indexOf(ui.current) + 1
        ui.listFocus = if (at > 0) at else if (ui.rows.isEmpty()) 0 else 1
    }

    // ---- setup ----------------------------------------------------------------------------

    private fun showSetup(message: String?) {
        player?.stop()
        ui.listOpen = false
        ui.mode = TvMode.Setup
        ui.setupMessage = message
        ui.playlistUrl = store.playlistUrl
        ui.guideUrl = store.guideUrl
        ui.setupFocus = if (ui.playlistUrl.isBlank()) SetupRow.Playlist else SetupRow.Watch
        startPhoneLink()
    }

    private fun startPhoneLink() {
        if (phoneLink != null) return
        val link = PhoneLink { playlist, guide ->
            runOnUiThread {
                if (ui.mode != TvMode.Setup) return@runOnUiThread
                ui.playlistUrl = playlist
                if (guide.isNotEmpty()) ui.guideUrl = guide
                saveAndLoad()
            }
        }
        phoneLink = link
        lifecycleScope.launch { ui.phoneAddress = withContext(Dispatchers.IO) { link.start() } }
    }

    private fun stopPhoneLink() {
        val link = phoneLink ?: return
        phoneLink = null
        ui.phoneAddress = null
        lifecycleScope.launch(Dispatchers.IO) { link.stop() }
    }

    private fun saveAndLoad() {
        if (ui.playlistUrl.isBlank()) {
            ui.setupMessage = "Add your playlist address first."
            ui.setupFocus = SetupRow.Playlist
            return
        }
        store.playlistUrl = ui.playlistUrl
        store.guideUrl = ui.guideUrl
        stopPhoneLink()
        ui.channels = emptyList()
        ui.current = -1
        ui.guide = Guide.Empty
        loadChannels(force = true)
    }

    /** A native text box: dependable with the TV's on-screen keyboard, and white like the rest of Mii_OS. */
    private fun editAddress(title: String, current: String, onDone: (String) -> Unit) {
        val field = EditText(this).apply {
            setText(current)
            setSelection(text.length)
            hint = "http://…"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val pad = (resources.displayMetrics.density * 24).toInt()
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(title)
            .setView(FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(field) })
            .setPositiveButton("Save") { _, _ -> onDone(field.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .create()
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                onDone(field.text.toString().trim())
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        field.requestFocus()
    }

    // ---- remote ---------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code !in handledKeys && code !in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) onKey(code)
        return true
    }

    private fun onKey(code: Int) {
        when (ui.mode) {
            TvMode.Loading -> if (code == KeyEvent.KEYCODE_BACK) finish()
            TvMode.Setup -> setupKey(code)
            TvMode.Watching -> if (ui.listOpen) listKey(code) else watchKey(code)
        }
    }

    private fun watchKey(code: Int) {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> zap(1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> openList()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> showBanner()
            KeyEvent.KEYCODE_BACK -> when {
                ui.digits.isNotEmpty() -> { digitJob?.cancel(); ui.digits = "" }
                ui.bannerVisible -> { bannerJob?.cancel(); ui.bannerVisible = false }
                else -> finish()
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> typeDigit(code - KeyEvent.KEYCODE_0)
        }
    }

    private fun listKey(code: Int) {
        val last = ui.rows.size
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> ui.listFocus = (ui.listFocus - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> ui.listFocus = (ui.listFocus + 1).coerceAtMost(last)
            KeyEvent.KEYCODE_CHANNEL_UP -> ui.listFocus = (ui.listFocus - 10).coerceAtLeast(0)
            KeyEvent.KEYCODE_CHANNEL_DOWN -> ui.listFocus = (ui.listFocus + 10).coerceAtMost(last)
            KeyEvent.KEYCODE_DPAD_LEFT -> changeGroup(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> changeGroup(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (ui.listFocus == 0) {
                    showSetup(null)
                } else {
                    val index = ui.rows.getOrNull(ui.listFocus - 1) ?: return
                    ui.listOpen = false
                    if (index != ui.current || ui.status != null) tune(index) else showBanner()
                }
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> ui.listOpen = false
        }
    }

    private fun setupKey(code: Int) {
        val rows = SetupRow.entries
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> ui.setupFocus = rows[(ui.setupFocus.ordinal - 1).coerceAtLeast(0)]
            KeyEvent.KEYCODE_DPAD_DOWN -> ui.setupFocus = rows[(ui.setupFocus.ordinal + 1).coerceAtMost(rows.size - 1)]
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> when (ui.setupFocus) {
                SetupRow.Playlist -> editAddress("Playlist address (M3U)", ui.playlistUrl) {
                    ui.playlistUrl = it
                    ui.setupMessage = null
                    if (it.isNotEmpty()) ui.setupFocus = SetupRow.Watch
                }
                SetupRow.Guide -> editAddress("Guide address (XMLTV)", ui.guideUrl) { ui.guideUrl = it }
                SetupRow.Watch -> saveAndLoad()
            }
            KeyEvent.KEYCODE_BACK -> {
                // Back to the picture if there's a playlist already playing; otherwise leave the TV channel.
                if (ui.channels.isNotEmpty() && store.playlistUrl == ui.playlistUrl) {
                    stopPhoneLink()
                    ui.mode = TvMode.Watching
                    if (ui.current >= 0) tune(ui.current)
                } else {
                    finish()
                }
            }
        }
    }

    companion object {
        /** Stands in for a package name, so the TV channel sits in the grid like any app. */
        const val CHANNEL_ID = "com.miios.launcher.tv"
        private const val EXTRA_SETUP = "setup"

        private val handledKeys = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO,
        )

        fun intent(context: Context, setup: Boolean = false): Intent =
            Intent(context, TvActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_SETUP, setup)
    }
}
