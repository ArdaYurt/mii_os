package com.miios.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.miios.launcher.update.Downloads
import com.miios.launcher.update.Downloads.Stage
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The download manager, Wii style: a white card above the dock with the app's name, what's
 * happening in words, and a blue bar filling up. It stays out of the way (nothing to press),
 * and goes once the install is done, or a while after it failed so the reason can be read.
 */
@Composable
fun DownloadBar(download: Downloads.Download?, modifier: Modifier = Modifier) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    // keep showing the last one while the card slides away
    val last = remember { arrayOfNulls<Downloads.Download>(1) }
    if (download != null) last[0] = download
    val visible by animateFloatAsState(if (download != null) 1f else 0f, Motion.preview, label = "download")
    // a smooth bar, even though the download reports in 1% steps
    val bar by animateFloatAsState((last[0]?.progress ?: 0f).coerceIn(0f, 1f), label = "downloadBar")

    LaunchedEffect(download?.id, download?.stage) {
        val d = download ?: return@LaunchedEffect
        when (d.stage) {
            Stage.Done, Stage.Cancelled -> { delay(5000); Downloads.clear(d.id) }
            Stage.Failed -> { delay(12000); Downloads.clear(d.id) }
            else -> Unit
        }
    }

    val d = last[0] ?: return
    if (visible <= 0.01f && download == null) return
    val failed = d.stage == Stage.Failed
    val accent = if (failed) Color(0xFFE5484D) else colors.focus

    GlassSurface(
        RoundedCornerShape(unit * 1.6f),
        modifier
            .size(unit * 58f, unit * 5.6f)
            .graphicsLayer {
                alpha = visible
                translationY = (1f - visible) * unit.toPx() * 3f
            },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = unit * 1.8f, vertical = unit * 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(unit * 1.4f),
        ) {
            Canvas(Modifier.size(unit * 2.6f)) {
                drawMiiIcon(if (d.stage == Stage.Done) MiiIcon.PlayCircle else MiiIcon.Download, accent, weight = 2.4f)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(unit * 0.6f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label(d.title, unit * 1.4f, colors.ink, FontWeight.ExtraBold)
                    Box(Modifier.weight(1f).padding(horizontal = unit * 1.2f)) {
                        Label(status(d), unit * 1.15f, if (failed) accent else colors.inkSoft, FontWeight.Medium)
                    }
                    Label(amount(d), unit * 1.15f, colors.inkSoft, FontWeight.Medium)
                }
                Canvas(Modifier.fillMaxWidth().height(unit * 0.9f)) {
                    val r = CornerRadius(size.height / 2f)
                    drawRoundRect(colors.tileEmpty, cornerRadius = r)
                    val fill = when {
                        d.stage == Stage.Downloading && d.progress < 0f -> 0.25f
                        d.stage == Stage.Downloading -> bar
                        failed && d.progress < 0f -> 0f
                        failed -> bar
                        else -> 1f
                    }
                    if (fill > 0f) drawRoundRect(accent, size = Size(size.width * fill, size.height), cornerRadius = r)
                }
            }
        }
    }
}

private fun status(d: Downloads.Download): String = when (d.stage) {
    Stage.Downloading -> "Downloading from GitHub…"
    Stage.Checking -> "Checking the file…"
    Stage.Installing -> "Installing: press Install on the screen that opens"
    Stage.Done -> "Installed. Press Start to open it"
    Stage.Cancelled -> "Install cancelled"
    Stage.Failed -> "Didn't install: ${d.message ?: "something went wrong"}"
}

/** "12.4 / 26.1 MB · 47%" while downloading, nothing once it's past that. */
private fun amount(d: Downloads.Download): String {
    if (d.stage != Stage.Downloading) return ""
    val done = mb(d.doneBytes)
    if (d.totalBytes <= 0) return "$done MB"
    val percent = (d.doneBytes * 100 / d.totalBytes).toInt()
    return "$done / ${mb(d.totalBytes)} MB · $percent%"
}

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f", bytes / 1_048_576.0)
