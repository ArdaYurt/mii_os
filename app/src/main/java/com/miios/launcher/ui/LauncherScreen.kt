package com.miios.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.miios.launcher.data.InstalledApp
import com.miios.launcher.data.Slot
import com.miios.launcher.sound.Sfx
import com.miios.launcher.sound.Sounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

interface LauncherActions {
    /** Starts the app, or opens its Play Store page if it isn't installed yet. */
    fun launch(app: InstalledApp)
    fun openAppInfo(app: InstalledApp)
    fun openSystemSetting(item: SettingsItem)
    /** Opens AirScreen for iPhone/iPad screen sharing, or its store page if it isn't installed. */
    fun openScreenShare()
    fun installUpdate()
    /** Hides the update prompt until the next check. */
    fun dismissUpdate()

    // Arranging the home screen. Each saves on the box and updates the slots passed back in.
    fun saveOrder(slots: List<Slot>)
    /** Makes a folder holding [pkg] in its place; returns the folder's id. */
    fun newFolder(pkg: String, name: String): String
    fun addToFolder(pkg: String, folderId: String)
    fun removeFromFolder(pkg: String, folderId: String)
    fun renameFolder(folderId: String, name: String)
    fun deleteFolder(folderId: String)
}

private const val COLS = 4
private const val ROWS = 3
private const val PER_PAGE = COLS * ROWS

private enum class Zone { Grid, Dock, Preview, Settings, Update, Folder, Options, Rename }

/**
 * The home screen. Focus is driven by one key handler rather than Compose's focus search,
 * so every D-pad press moves exactly one step, crosses pages predictably, and costs one
 * state write: the animation work happens in graphicsLayer, not in recomposition.
 */
@Composable
fun LauncherScreen(
    slots: List<Slot>,
    homePresses: Int,
    update: UpdateState,
    actions: LauncherActions,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L * (60 - now.second).coerceAtLeast(1))
        }
    }
    val colors = WiiColors

    var zone by remember { mutableStateOf(Zone.Grid) }
    var index by remember { mutableIntStateOf(0) }
    var dock by remember { mutableStateOf(DockButton.Settings) }
    var previewButton by remember { mutableStateOf(PreviewButton.Start) }
    var settingsIndex by remember { mutableIntStateOf(0) }
    var leanX by remember { mutableIntStateOf(0) }
    var leanY by remember { mutableIntStateOf(0) }
    var moveId by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }
    // Move mode: a working copy of the home screen while a channel is carried around.
    var moving by remember { mutableStateOf<List<Slot>?>(null) }
    val shown = moving ?: slots
    // The open folder, by key, and the focused channel inside it.
    var folderKey by remember { mutableStateOf<String?>(null) }
    var folderIndex by remember { mutableIntStateOf(0) }
    val openFolder = folderKey?.let { k -> shown.firstOrNull { it.key == k } as? Slot.Folder }
    val folderAnim = remember { Animatable(0f) }
    val folderVisible by remember { derivedStateOf { folderAnim.value > 0f } }
    // The options menu (long press on OK) and where it returns to.
    var options by remember { mutableStateOf<List<MenuOption>>(emptyList()) }
    var optionsTitle by remember { mutableStateOf("") }
    var optionIndex by remember { mutableIntStateOf(0) }
    var optionsReturn by remember { mutableStateOf(Zone.Grid) }
    val optionsAnim = remember { Animatable(0f) }
    val optionsVisible by remember { derivedStateOf { optionsAnim.value > 0f } }
    var renameId by remember { mutableStateOf<String?>(null) }
    var renameInitial by remember { mutableStateOf("") }
    // The channel the channel screen shows, and where Back from it goes.
    var previewTarget by remember { mutableStateOf<InstalledApp?>(null) }
    var previewReturn by remember { mutableStateOf(Zone.Grid) }

    val pageCount = maxOf(1, (shown.size + PER_PAGE - 1) / PER_PAGE)
    val slots = pageCount * PER_PAGE
    LaunchedEffect(slots) { if (index >= slots) index = slots - 1 }
    val page = index.coerceAtMost(slots - 1) / PER_PAGE

    val scope = rememberCoroutineScope()
    val pageAnim = remember { Animatable(0f) }
    LaunchedEffect(page) { pageAnim.animateTo(page.toFloat(), Motion.page) }
    val previewAnim = remember { Animatable(0f) }
    val previewVisible by remember { derivedStateOf { previewAnim.value > 0f } }
    val launchAnim = remember { Animatable(0f) }
    val settingsAnim = remember { Animatable(0f) }
    val settingsVisible by remember { derivedStateOf { settingsAnim.value > 0f } }
    val updateAnim = remember { Animatable(0f) }
    val updateVisible by remember { derivedStateOf { updateAnim.value > 0f } }
    var updateButton by remember { mutableStateOf(UpdateButton.Update) }
    var zoneBeforeUpdate by remember { mutableStateOf(Zone.Grid) }
    // Where the focused tile sits on screen, so the channel screen can grow out of it.
    // A plain holder, not state: it changes every frame while a tile animates.
    val focusedBounds = remember { arrayOf(Rect.Zero) }
    var previewOrigin by remember { mutableStateOf(Rect.Zero) }
    // When the channel screen opened. OK presses right after it are ignored, so the press that
    // opened it (or the remote's repeat of it) can't also hit Start.
    var previewOpenedAt by remember { mutableStateOf(0L) }

    fun openPreview(app: InstalledApp, from: Zone) {
        previewTarget = app
        previewReturn = from
        previewOrigin = focusedBounds[0]
        previewOpenedAt = android.os.SystemClock.uptimeMillis()
        zone = Zone.Preview
        previewButton = PreviewButton.Start
        Sounds.play(Sfx.Select)
        scope.launch { previewAnim.animateTo(1f, Motion.preview) }
    }
    fun closePreview() {
        zone = previewReturn
        Sounds.play(Sfx.Back)
        scope.launch { previewAnim.animateTo(0f, Motion.preview) }
    }
    fun openSettings() {
        zone = Zone.Settings
        settingsIndex = 0
        Sounds.play(Sfx.Select)
        scope.launch { settingsAnim.animateTo(1f, Motion.preview) }
    }
    fun closeSettings() {
        zone = Zone.Dock
        Sounds.play(Sfx.Back)
        scope.launch { settingsAnim.animateTo(0f, Motion.preview) }
    }
    fun startApp(app: InstalledApp) {
        Sounds.play(Sfx.Launch)
        scope.launch {
            // Wii-style start: push in slightly and wash to white, then hand over to the app
            launchAnim.animateTo(1f, androidx.compose.animation.core.tween(260, easing = Motion.emphasized))
            actions.launch(app)
            delay(700)
            launchAnim.snapTo(0f)
            previewAnim.snapTo(0f)
            zone = previewReturn
        }
    }
    fun showFolder(key: String) {
        folderKey = key
        folderIndex = 0
        zone = Zone.Folder
        Sounds.play(Sfx.Select)
        scope.launch { folderAnim.animateTo(1f, Motion.preview) }
    }
    fun closeFolder() {
        zone = Zone.Grid
        Sounds.play(Sfx.Back)
        scope.launch {
            folderAnim.animateTo(0f, Motion.preview)
            if (zone != Zone.Folder) folderKey = null
        }
    }
    fun closeOptions() {
        zone = optionsReturn
        scope.launch { optionsAnim.animateTo(0f, Motion.preview) }
    }
    fun startRename(id: String, current: String) {
        renameId = id
        renameInitial = current
        zone = Zone.Rename
    }
    fun openOptions() {
        val list = ArrayList<MenuOption>()
        val folders = shown.filterIsInstance<Slot.Folder>()
        when (zone) {
            Zone.Grid -> when (val slot = shown.getOrNull(index)) {
                is Slot.App -> {
                    optionsTitle = slot.label
                    list += MenuOption("Move", MiiIcon.Apps) { moving = shown; Sounds.play(Sfx.Select) }
                    list += MenuOption("New folder", MiiIcon.Plus) {
                        val id = actions.newFolder(slot.app.packageName, "New folder")
                        startRename(id, "New folder")
                    }
                    for (f in folders) list += MenuOption("Add to ${f.name}", MiiIcon.Download) { actions.addToFolder(slot.app.packageName, f.id) }
                }
                is Slot.Folder -> {
                    optionsTitle = slot.name
                    list += MenuOption("Move", MiiIcon.Apps) { moving = shown; Sounds.play(Sfx.Select) }
                    list += MenuOption("Rename", MiiIcon.Sliders) { startRename(slot.id, slot.name) }
                    list += MenuOption("Remove folder", MiiIcon.Back) { actions.deleteFolder(slot.id) }
                }
                null -> return
            }
            Zone.Folder -> {
                val folder = openFolder ?: return
                val app = folder.apps.getOrNull(folderIndex) ?: return
                optionsTitle = app.label
                list += MenuOption("Move out of ${folder.name}", MiiIcon.Back) {
                    actions.removeFromFolder(app.packageName, folder.id)
                    folderIndex = (folderIndex - 1).coerceAtLeast(0)
                }
            }
            else -> return
        }
        list += MenuOption("Cancel", MiiIcon.Back) {}
        options = list
        optionIndex = 0
        optionsReturn = zone
        zone = Zone.Options
        Sounds.play(Sfx.Select)
        scope.launch { optionsAnim.animateTo(1f, Motion.preview) }
    }
    fun dropMoving() {
        val list = moving ?: return
        moving = null
        Sounds.play(Sfx.Select)
        actions.saveOrder(list)
    }

    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            launchAnim.snapTo(0f)
            previewAnim.snapTo(0f)
            settingsAnim.snapTo(0f)
            if (zone == Zone.Update) actions.dismissUpdate()
            updateAnim.snapTo(0f)
            moving?.let { actions.saveOrder(it) }
            moving = null
            folderAnim.snapTo(0f)
            optionsAnim.snapTo(0f)
            folderKey = null
            zone = Zone.Grid
            index = 0
        }
    }

    BackHandler {
        // A home screen never exits. Back closes whatever is open, or returns focus to the grid.
        when (zone) {
            Zone.Preview -> closePreview()
            Zone.Settings -> closeSettings()
            Zone.Update -> if (update !is UpdateState.Downloading) {
                Sounds.play(Sfx.Back)
                actions.dismissUpdate()
            }
            Zone.Folder -> closeFolder()
            Zone.Options -> { Sounds.play(Sfx.Back); closeOptions() }
            Zone.Rename -> { zone = Zone.Grid; focusRequester.requestFocus() }
            Zone.Grid -> if (moving != null) dropMoving()
            else -> zone = Zone.Grid
        }
    }

    // The update prompt opens over the grid when a new build is found, never over an open channel.
    LaunchedEffect(update) {
        when {
            update is UpdateState.None && zone == Zone.Update -> {
                zone = zoneBeforeUpdate
                updateAnim.animateTo(0f, Motion.preview)
            }
            update is UpdateState.Available && zone != Zone.Update && (zone == Zone.Grid || zone == Zone.Dock) -> {
                zoneBeforeUpdate = zone
                zone = Zone.Update
                updateButton = UpdateButton.Update
                Sounds.play(Sfx.Notice)
                updateAnim.animateTo(1f, Motion.preview)
            }
        }
    }

    fun step(dx: Int, dy: Int) {
        leanX = dx
        leanY = dy
        moveId++
        when (zone) {
            Zone.Settings -> {
                val count = SettingsItem.entries.size
                val next = settingsIndex + dx + dy * SETTINGS_COLS
                if (next in 0 until count && (dx == 0 || next / SETTINGS_COLS == settingsIndex / SETTINGS_COLS)) settingsIndex = next
            }
            Zone.Update -> if (dx != 0) {
                updateButton = if (dx < 0) UpdateButton.Later else UpdateButton.Update
            }
            Zone.Options -> if (dy != 0) optionIndex = (optionIndex + dy).coerceIn(0, options.size - 1)
            Zone.Rename -> Unit
            Zone.Folder -> {
                val count = openFolder?.apps?.size ?: 0
                val next = folderIndex + dx + dy * FOLDER_COLS
                val sameRow = dx == 0 || next / FOLDER_COLS == folderIndex / FOLDER_COLS
                if (next in 0 until count && sameRow) folderIndex = next
            }
            Zone.Preview -> if (dx != 0) {
                previewButton = if (dx < 0) PreviewButton.Menu else PreviewButton.Start
            }
            Zone.Dock -> when {
                dy < 0 -> zone = Zone.Grid
                dx < 0 -> dock = DockButton.Settings
                dx > 0 -> dock = DockButton.Share
            }
            Zone.Grid -> {
                val carried = moving
                val old = index
                val local = index % PER_PAGE
                val col = local % COLS
                val row = local / COLS
                if (dx != 0) {
                    val nc = col + dx
                    index = when {
                        nc < 0 -> if (page > 0) (page - 1) * PER_PAGE + row * COLS + COLS - 1 else index
                        nc >= COLS -> if (page < pageCount - 1) (page + 1) * PER_PAGE + row * COLS else index
                        else -> index + dx
                    }
                }
                if (dy != 0) {
                    val nr = row + dy
                    when {
                        nr >= ROWS -> if (carried == null) {
                            zone = Zone.Dock
                            dock = if (col < 2) DockButton.Settings else DockButton.Share
                        }
                        nr >= 0 -> index += dy * COLS
                    }
                }
                if (carried != null) {
                    // carry the channel: it swaps places as it moves, and can't go past the last one
                    if (index >= carried.size) index = old
                    if (index != old) moving = carried.toMutableList().apply { add(index, removeAt(old)) }
                }
            }
        }
    }

    fun move(dx: Int, dy: Int) {
        val before = listOf(zone, index, dock, previewButton, settingsIndex, updateButton)
        val pageBefore = page
        step(dx, dy)
        val after = listOf(zone, index, dock, previewButton, settingsIndex, updateButton)
        when {
            zone == Zone.Grid && index / PER_PAGE != pageBefore -> Sounds.play(Sfx.Page)
            before != after -> Sounds.play(Sfx.Move)
        }
    }

    fun select() {
        when (zone) {
            Zone.Grid -> if (moving != null) dropMoving() else when (val slot = shown.getOrNull(index)) {
                is Slot.App -> openPreview(slot.app, Zone.Grid)
                is Slot.Folder -> showFolder(slot.key)
                null -> Unit
            }
            Zone.Folder -> openFolder?.apps?.getOrNull(folderIndex)?.let { openPreview(it, Zone.Folder) }
            Zone.Options -> {
                val option = options.getOrNull(optionIndex)
                closeOptions()
                option?.run?.invoke()
            }
            Zone.Rename -> Unit
            Zone.Dock -> if (dock == DockButton.Settings) {
                openSettings()
            } else {
                Sounds.play(Sfx.Select)
                actions.openScreenShare()
            }
            Zone.Settings -> {
                Sounds.play(Sfx.Select)
                actions.openSystemSetting(SettingsItem.entries[settingsIndex])
            }
            Zone.Update -> when {
                update !is UpdateState.Available -> Unit
                updateButton == UpdateButton.Update -> { Sounds.play(Sfx.Select); actions.installUpdate() }
                else -> { Sounds.play(Sfx.Back); actions.dismissUpdate() }
            }
            Zone.Preview -> {
                if (launchAnim.isRunning) return
                if (android.os.SystemClock.uptimeMillis() - previewOpenedAt < 350) return
                val app = previewTarget ?: return
                when {
                    previewButton == PreviewButton.Menu -> { Sounds.play(Sfx.Select); actions.openAppInfo(app); closePreview() }
                    app.installed -> startApp(app)
                    else -> { actions.launch(app); closePreview() }
                }
            }
        }
    }

    var okDown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // the folder name field takes the keys while it's open
                if (zone == Zone.Rename) return@onPreviewKeyEvent false
                val handled = when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu -> true
                    else -> false
                }
                if (handled) {
                    when (event.key) {
                        Key.DirectionLeft -> if (event.type == KeyEventType.KeyDown) move(-1, 0)
                        Key.DirectionRight -> if (event.type == KeyEventType.KeyDown) move(1, 0)
                        Key.DirectionUp -> if (event.type == KeyEventType.KeyDown) move(0, -1)
                        Key.DirectionDown -> if (event.type == KeyEventType.KeyDown) move(0, 1)
                        // OK acts on release, once: holding it (or a remote that repeats it) can't
                        // open a channel and press Start in the same go.
                        // the remote's menu button opens the options, like holding OK
                        Key.Menu -> if (event.type == KeyEventType.KeyUp) openOptions()
                        else -> if (event.type == KeyEventType.KeyDown) {
                            val repeat = event.nativeKeyEvent.repeatCount
                            if (repeat == 0) okDown = true
                            // held: open the options for this channel instead of opening it
                            if (repeat == 1 && okDown) {
                                okDown = false
                                if (moving == null) openOptions()
                            }
                        } else if (event.type == KeyEventType.KeyUp && okDown) {
                            okDown = false
                            select()
                        }
                    }
                }
                handled
            },
    ) {
        val unit = maxWidth / 100f
        val density = LocalDensity.current
        val screen = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        val scene: @Composable () -> Unit = { Scenery(colors) }
        val pageWidthPx = with(density) { (maxWidth - unit * 11f).toPx() }

        CompositionLocalProvider(
            LocalMiiColors provides colors,
            LocalUnit provides unit,
            LocalBackdrop provides Backdrop(screen, scene),
        ) {
            scene()

            // channel grid
            Box(
                Modifier
                    .padding(start = unit * 5.5f, end = unit * 5.5f, top = unit * 4.4f)
                    .fillMaxWidth()
                    .height(unit * 37f),
            ) {
                for (p in 0 until pageCount) {
                    if (kotlin.math.abs(p - page) > 1) continue
                    ChannelPage(
                        slots = shown,
                        firstSlot = p * PER_PAGE,
                        focusedSlot = if (zone == Zone.Grid) index else -1,
                        lifted = moving != null,
                        leanX = leanX,
                        leanY = leanY,
                        moveId = moveId,
                        onFocusedBounds = { focusedBounds[0] = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = (p - pageAnim.value) * pageWidthPx },
                    )
                }
            }

            PageDots(pageCount, page, Modifier.align(Alignment.TopCenter).offset(y = unit * 42.6f))
            Label(
                if (moving != null) "Moving: use the arrows, then press OK to put it down"
                else "Hold OK on a channel to move it or make a folder",
                unit * 1.05f,
                colors.inkSoft.copy(alpha = if (moving != null) 1f else 0.6f),
                FontWeight.Medium,
                Modifier.align(Alignment.TopCenter).offset(y = unit * 43.7f),
            )

            Dock(
                now = now,
                focused = if (zone == Zone.Dock) dock else null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = unit * 1f)
                    .fillMaxWidth()
                    .height(unit * 11.8f),
            )

            if (openFolder != null && (zone == Zone.Folder || folderVisible || previewReturn == Zone.Folder && zone == Zone.Preview)) {
                FolderView(
                    folder = openFolder,
                    focused = if (zone == Zone.Folder) folderIndex else -1,
                    leanX = leanX,
                    leanY = leanY,
                    moveId = moveId,
                    onFocusedBounds = { focusedBounds[0] = it },
                ) { folderAnim.value }
            }

            val previewApp = previewTarget
            if (previewApp != null && (zone == Zone.Preview || previewVisible)) {
                ChannelPreview(previewApp, previewButton, { previewAnim.value }, { launchAnim.value }, previewOrigin, screen)
            }

            if (zone == Zone.Settings || settingsVisible) {
                SettingsMenu(settingsIndex) { settingsAnim.value }
            }

            if (zone == Zone.Update || updateVisible) {
                UpdatePrompt(update, updateButton) { updateAnim.value }
            }

            if (zone == Zone.Options || optionsVisible) {
                OptionsMenu(optionsTitle, options, optionIndex) { optionsAnim.value }
            }

            val renaming = renameId
            if (zone == Zone.Rename && renaming != null) {
                RenameDialog(renameInitial) { name ->
                    actions.renameFolder(renaming, name.trim().ifEmpty { "Folder" })
                    zone = Zone.Grid
                    Sounds.play(Sfx.Select)
                    focusRequester.requestFocus()
                }
            }
        }
    }
}

@Composable
private fun ChannelPage(
    slots: List<Slot>,
    firstSlot: Int,
    focusedSlot: Int,
    lifted: Boolean,
    leanX: Int,
    leanY: Int,
    moveId: Int,
    onFocusedBounds: (Rect) -> Unit,
    modifier: Modifier,
) {
    val unit = LocalUnit.current
    Column(modifier.padding(horizontal = unit * 0.4f), verticalArrangement = Arrangement.spacedBy(unit * 1.6f)) {
        for (r in 0 until ROWS) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(unit * 1.8f)) {
                for (c in 0 until COLS) {
                    val slot = firstSlot + r * COLS + c
                    ChannelTile(
                        slot = slots.getOrNull(slot),
                        focused = slot == focusedSlot,
                        lifted = lifted && slot == focusedSlot,
                        leanX = leanX,
                        leanY = leanY,
                        moveId = moveId,
                        onFocusedBounds = onFocusedBounds,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier) {
    if (count < 2) return
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Canvas(modifier.size(unit * (count * 1.4f + 1.3f), unit * 0.7f)) {
        val d = unit.toPx() * 0.7f
        val gap = unit.toPx() * 0.7f
        var x = 0f
        for (i in 0 until count) {
            val w = if (i == current) d * 2.85f else d
            drawRoundRect(
                color = if (i == current) colors.focus else colors.inkSoft.copy(alpha = 0.35f),
                topLeft = Offset(x, 0f),
                size = Size(w, d),
                cornerRadius = CornerRadius(d / 2f),
            )
            x += w + gap
        }
    }
}
