package com.miios.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.miios.launcher.service.HomeButtonService

/** Entries on the Mii_OS Settings screen. Each opens the matching page of the box's own settings. */
enum class SettingsItem(val title: String, val detail: String, val icon: MiiIcon) {
    Network("Internet", "Wi-Fi and Ethernet", MiiIcon.Wifi),
    Display("Display & Sound", "Resolution, HDR, audio output", MiiIcon.Display),
    Apps("Apps", "Storage, updates, uninstall", MiiIcon.Apps),
    DateTime("Date & Time", "Clock and time zone", MiiIcon.Clock),
    Sounds("Sounds", "Clicks and chimes", MiiIcon.PlayCircle),
    HomeMenu("HOME Menu", "The pop-up when you press Home in an app", MiiIcon.Home),
    Updates("Mii_OS Update", "Check for a new version", MiiIcon.Download),
    System("All Settings", "Everything else on the box", MiiIcon.Sliders),
}

const val SETTINGS_COLS = 2

/**
 * The Wii-style Settings screen: a title, then large rounded buttons in two columns.
 * [progress] runs 0→1 as it opens; it rises and fades in over the channel grid.
 */
@Composable
fun SettingsMenu(focused: Int, progress: () -> Float) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * unit.toPx() * 4f
            },
    ) {
        Scenery(colors)
        Column(
            Modifier.fillMaxSize().padding(horizontal = unit * 12f, vertical = unit * 5f),
            verticalArrangement = Arrangement.spacedBy(unit * 2f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 1.2f)) {
                Canvas(Modifier.size(unit * 3.4f)) { drawMiiIcon(MiiIcon.Gear, colors.focus, weight = 2.2f) }
                Label("Mii_OS Settings", unit * 3.2f, colors.ink, FontWeight.ExtraBold)
            }
            Box(Modifier.fillMaxWidth().height(unit * 0.2f).graphicsLayer { alpha = 0.9f }) {
                Canvas(Modifier.fillMaxSize()) { drawRect(colors.focus) }
            }
            val items = SettingsItem.entries
            val context = androidx.compose.ui.platform.LocalContext.current
            val versionName = androidx.compose.runtime.remember { com.miios.launcher.update.Updater.currentName(context) }
            for (row in items.chunked(SETTINGS_COLS).withIndex()) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(unit * 3f)) {
                    for ((col, item) in row.value.withIndex()) {
                        val i = row.index * SETTINGS_COLS + col
                        val detail = when (item) {
                            SettingsItem.Updates -> "Version $versionName: check for a new one"
                            SettingsItem.Sounds ->
                                if (com.miios.launcher.sound.Sounds.enabled) "On: press to turn off" else "Off: press to turn on"
                            SettingsItem.HomeMenu ->
                                if (HomeButtonService.on) "On: press Home in any app" else "Off: press to set it up"
                            else -> item.detail
                        }
                        SettingsButton(item.icon, item.title, detail, i == focused, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            Label("Back returns to the channels", unit * 1.1f, colors.inkSoft, FontWeight.Medium, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun SettingsButton(icon: MiiIcon, title: String, detail: String, focused: Boolean, modifier: Modifier) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val scale = animateFloatAsState(if (focused) 1.05f else 1f, Motion.focusScale, label = "settingsScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "settingsRing")
    val radius = unit * 2.4f
    Box(modifier.pointerTarget(focused).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        GlassSurface(RoundedCornerShape(radius), Modifier.fillMaxSize()) {
            Row(
                Modifier.align(Alignment.CenterStart).padding(start = unit * 2.4f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(unit * 1.8f),
            ) {
                // icon in a soft Wii-blue disc
                Box(Modifier.size(unit * 6f).background(colors.focus.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(unit * 3.4f)) { drawMiiIcon(icon, colors.focus, weight = 2.2f) }
                }
                Column {
                    Label(title, unit * 2.2f, colors.ink, FontWeight.Bold)
                    Label(detail, unit * 1.15f, colors.inkSoft, FontWeight.Medium, Modifier.padding(top = unit * 0.4f))
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val on = ring.value > 0f
            drawRoundRect(
                if (on) colors.focus.copy(alpha = ring.value) else colors.tileBorder,
                cornerRadius = CornerRadius(radius.toPx()),
                style = Stroke(unit.toPx() * if (on) 0.3f else 0.12f),
            )
        }
    }
}
