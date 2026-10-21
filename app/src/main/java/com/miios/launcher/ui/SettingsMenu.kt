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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight

/** Entries on the Mii_OS Settings screen. Each opens the matching page of the box's own settings. */
enum class SettingsItem(val title: String, val detail: String) {
    Network("Internet", "Wi-Fi and Ethernet"),
    Display("Display & Sound", "Resolution, HDR, audio output"),
    Apps("Apps", "Storage, updates, uninstall"),
    HomeButton("Home Button", "Make Home open Mii_OS"),
    DateTime("Date & Time", "Clock and time zone"),
    System("All Settings", "Everything else on the box"),
}

const val SETTINGS_COLS = 2

/**
 * The Wii-style Settings screen: a title, then large rounded buttons in two columns.
 * [progress] runs 0→1 as it opens; it rises and fades in over the channel grid.
 */
@Composable
fun SettingsMenu(focused: Int, homeCaptureOn: Boolean, progress: () -> Float) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress()
                alpha = p
                translationY = (1f - p) * unit.toPx() * 4f
            },
    ) {
        Scenery(colors)
        Column(
            Modifier.fillMaxSize().padding(horizontal = unit * 12f, vertical = unit * 5f),
            verticalArrangement = Arrangement.spacedBy(unit * 2f),
        ) {
            Label("Mii_OS Settings", unit * 3.2f, colors.ink, FontWeight.ExtraBold)
            Box(Modifier.fillMaxWidth().height(unit * 0.2f).graphicsLayer { alpha = 0.9f }) {
                Canvas(Modifier.fillMaxSize()) { drawRect(colors.focus) }
            }
            val items = SettingsItem.entries
            for (row in items.chunked(SETTINGS_COLS).withIndex()) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(unit * 3f)) {
                    for ((col, item) in row.value.withIndex()) {
                        val i = row.index * SETTINGS_COLS + col
                        val detail = if (item == SettingsItem.HomeButton) {
                            if (homeCaptureOn) "On: Home opens Mii_OS" else "Off: press to turn on"
                        } else item.detail
                        SettingsButton(item.title, detail, i == focused, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            Label("Back returns to the channels", unit * 1.1f, colors.inkSoft, FontWeight.Medium, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun SettingsButton(title: String, detail: String, focused: Boolean, modifier: Modifier) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val scale = animateFloatAsState(if (focused) 1.05f else 1f, Motion.focusScale, label = "settingsScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "settingsRing")
    val radius = unit * 2.4f
    Box(modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        GlassSurface(RoundedCornerShape(radius), Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(title, unit * 2.2f, colors.ink, FontWeight.Bold)
                Label(detail, unit * 1.15f, colors.inkSoft, FontWeight.Medium, Modifier.padding(top = unit * 0.5f))
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
