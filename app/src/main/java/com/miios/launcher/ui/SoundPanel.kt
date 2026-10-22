package com.miios.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.miios.launcher.sound.Music
import com.miios.launcher.sound.Sounds

/** Rows of the Sounds panel, in order. */
enum class SoundRow { Effects, EffectsVolume, VolumeKeys, Music, MusicVolume }

/**
 * Sounds, opened from Mii_OS Settings: effects and music each get an on/off switch and a volume
 * bar. ← → change the focused row, OK flips a switch, Back closes.
 */
@Composable
fun SoundPanel(focused: SoundRow, progress: () -> Float) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .background(Color(0x55000000)),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            RoundedCornerShape(unit * 2f),
            Modifier
                .width(unit * 52f)
                .graphicsLayer {
                    val s = 0.94f + 0.06f * progress().coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Column(Modifier.padding(unit * 2f), verticalArrangement = Arrangement.spacedBy(unit * 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 1f)) {
                    Canvas(Modifier.size(unit * 2.6f)) { drawMiiIcon(MiiIcon.PlayCircle, colors.focus, weight = 2.4f) }
                    Label("Sounds", unit * 2.2f, colors.ink, FontWeight.ExtraBold)
                }
                ToggleRow("Clicks and chimes", Sounds.enabled, focused == SoundRow.Effects)
                SliderRow("Clicks volume", Sounds.effectsVolume, focused == SoundRow.EffectsVolume, Sounds.enabled)
                ToggleRow("Volume button sound", Sounds.volumeKeys && Sounds.enabled, focused == SoundRow.VolumeKeys)
                ToggleRow("Menu music", Music.enabled, focused == SoundRow.Music)
                SliderRow("Music volume", Music.volume, focused == SoundRow.MusicVolume, Music.enabled)
                Label("← → to change, OK to switch on or off, Back to close", unit * 1.1f, colors.inkSoft, FontWeight.Medium, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun RowFrame(focused: Boolean, height: Float = 5f, content: @Composable () -> Unit) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "soundRing")
    Box(Modifier.fillMaxWidth().height(unit * height).background(colors.tileFrame, RoundedCornerShape(unit * 1.4f))) {
        Box(Modifier.fillMaxSize().padding(horizontal = unit * 1.6f), contentAlignment = Alignment.CenterStart) { content() }
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                if (ring > 0f) colors.focus.copy(alpha = ring) else colors.tileBorder,
                cornerRadius = CornerRadius(unit.toPx() * 1.4f),
                style = Stroke(unit.toPx() * if (ring > 0f) 0.28f else 0.1f),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, on: Boolean, focused: Boolean) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val knob by animateFloatAsState(if (on) 1f else 0f, Motion.focusScale, label = "knob")
    RowFrame(focused) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Label(title, unit * 1.6f, colors.ink, FontWeight.Bold)
            Canvas(Modifier.size(unit * 5f, unit * 2.6f)) {
                val r = size.height / 2f
                drawRoundRect(if (on) colors.focus else colors.tileEmpty, cornerRadius = CornerRadius(r))
                drawCircle(Color.White, r * 0.8f, Offset(r + (size.width - 2 * r) * knob, r))
            }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, focused: Boolean, active: Boolean) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val shown by animateFloatAsState(value, Motion.tilt, label = "slider")
    RowFrame(focused) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 2f)) {
            Label(title, unit * 1.6f, if (active) colors.ink else colors.inkSoft, FontWeight.Bold)
            Canvas(Modifier.weight(1f).height(unit * 1.2f)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(colors.tileEmpty, cornerRadius = r)
                drawRoundRect(if (active) colors.focus else colors.inkSoft, size = Size(size.width * shown, size.height), cornerRadius = r)
            }
            Label("${(value * 100).toInt()}%", unit * 1.4f, colors.inkSoft, FontWeight.Medium)
        }
    }
}
