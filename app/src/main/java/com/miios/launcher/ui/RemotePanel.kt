package com.miios.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.miios.launcher.art.ChannelStyles
import com.miios.launcher.data.InstalledApp
import com.miios.launcher.service.HomeButtonService
import com.miios.launcher.service.RemoteButtons
import com.miios.launcher.service.RemoteKey

/** A channel a remote button can open: null id means the button keeps its own job. */
data class ButtonTarget(val id: String?, val label: String)

/** The choices for a button: its own job, then the TV channel, Plezy and Plex, then the rest by name. */
fun buttonTargets(apps: List<InstalledApp>): List<ButtonTarget> {
    val first = listOf(ChannelStyles.TV, ChannelStyles.PLEZY, ChannelStyles.PLEX)
    val installed = apps.filter { it.installed }.distinctBy { it.packageName }
    return listOf(ButtonTarget(null, "Its own job")) +
        first.mapNotNull { id -> installed.firstOrNull { it.packageName == id } }.map { ButtonTarget(it.packageName, it.label) } +
        installed.filter { it.packageName !in first }.sortedBy { it.label.lowercase() }.map { ButtonTarget(it.packageName, it.label) }
}

/**
 * Remote buttons, opened from Mii_OS Settings: a row for each button Mii_OS can remap.
 * ← → pick what it opens, OK then the button teaches Mii_OS which button it is, Back closes.
 */
@Composable
fun RemotePanel(focused: RemoteKey, targets: List<ButtonTarget>, progress: () -> Float) {
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
                .width(unit * 60f)
                .graphicsLayer {
                    val s = 0.94f + 0.06f * progress().coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Column(Modifier.padding(unit * 2f), verticalArrangement = Arrangement.spacedBy(unit * 0.8f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 1f)) {
                    Canvas(Modifier.size(unit * 2.6f)) { drawMiiIcon(MiiIcon.Remote, colors.focus, weight = 2.4f) }
                    Label("Remote buttons", unit * 2.2f, colors.ink, FontWeight.ExtraBold)
                }
                for (key in RemoteKey.entries) {
                    val target = RemoteButtons.targets[key]
                    val label = targets.firstOrNull { it.id == target }?.label ?: if (target == null) "Its own job" else "Not installed"
                    val status = when {
                        RemoteButtons.teaching == key -> "Press the ${key.title} on the remote now"
                        key in RemoteButtons.taught -> "Learned. OK to teach it again"
                        // the Mi Box S remote sends it over its voice link, where Mii_OS can't see it
                        key == RemoteKey.Assistant -> "May not work on this remote: OK, then press it, to try"
                        RemoteButtons.known(key) -> "Not working? OK, then press it, to teach it"
                        else -> "Press OK, then the ${key.title}, to teach it"
                    }
                    ButtonRow(key.title, status, label, focused == key, RemoteButtons.teaching == key)
                }
                Label(
                    if (HomeButtonService.on) "← → to choose what it opens, OK to teach it, Back to close"
                    else "Needs the HOME Menu: switch it on in Settings › HOME Menu first",
                    unit * 1.1f,
                    colors.inkSoft,
                    FontWeight.Medium,
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ButtonRow(title: String, status: String, target: String, focused: Boolean, waiting: Boolean) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    RowFrame(focused, height = 5.4f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 1.4f)) {
            Column(Modifier.weight(1f)) {
                Label(title, unit * 1.6f, colors.ink, FontWeight.Bold)
                Label(status, unit * 1.05f, if (waiting) colors.focus else colors.inkSoft, FontWeight.Medium, Modifier.padding(top = unit * 0.3f))
            }
            Canvas(Modifier.size(unit * 1.4f)) { drawMiiIcon(MiiIcon.Back, if (focused) colors.focus else colors.inkSoft, weight = 2.6f) }
            Label(target, unit * 1.5f, colors.ink, FontWeight.Bold)
            Canvas(Modifier.size(unit * 1.4f)) { drawMiiIcon(MiiIcon.Chevron, if (focused) colors.focus else colors.inkSoft, weight = 2.6f) }
        }
    }
}
