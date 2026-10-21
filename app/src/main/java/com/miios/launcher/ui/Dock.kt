package com.miios.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Wii's curved bottom bar: Settings on the left, Screen Share on the right, the clock in the middle. */
val DockShape = GenericShape { size, _ ->
    val rise = size.height * 0.24f
    moveTo(0f, rise)
    quadraticBezierTo(size.width / 2f, -rise, size.width, rise)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

enum class DockButton { Settings, Share }

@Composable
fun Dock(
    now: LocalDateTime,
    focused: DockButton?,
    modifier: Modifier = Modifier,
) {
    val unit = LocalUnit.current
    val iconInk = LocalMiiColors.current.inkSoft
    GlassSurface(DockShape, modifier) {
        Row(
            Modifier.fillMaxSize().padding(start = unit * 7f, end = unit * 7f, top = unit * 1.6f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton("Settings", focused == DockButton.Settings) { drawMiiIcon(MiiIcon.Gear, iconInk) }
            Clock(now)
            RoundButton("Share", focused == DockButton.Share) { drawMiiIcon(MiiIcon.Screens, iconInk) }
        }
    }
}

@Composable
private fun RoundButton(label: String, focused: Boolean, icon: DrawScope.() -> Unit) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, Motion.focusScale, label = "dockScale")
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "dockRing")
    Box(
        Modifier
            .size(unit * 7f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        GlassSurface(CircleShape, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Canvas(Modifier.size(unit * 2.4f)) { icon() }
                Spacer(Modifier.height(unit * 0.3f))
                Label(label, unit * 0.85f, colors.ink, FontWeight.Bold)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (ring > 0f) drawCircle(colors.focus.copy(alpha = ring), style = Stroke(unit.toPx() * 0.3f))
        }
    }
}

@Composable
private fun Clock(now: LocalDateTime) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Label(now.format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm")), unit * 4.6f, colors.ink, FontWeight.Medium)
            if (!is24h) {
                Label(
                    now.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH)),
                    unit * 1.4f,
                    colors.inkSoft,
                    FontWeight.Medium,
                    Modifier.padding(start = unit * 0.4f, bottom = unit * 0.7f),
                )
            }
        }
        Label(
            now.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.getDefault())).uppercase(),
            unit * 1.25f,
            colors.inkSoft,
            FontWeight.Medium,
            Modifier.padding(top = unit * 0.5f),
        )
    }
}

@Composable
fun Label(text: String, size: Dp, color: Color, weight: FontWeight, modifier: Modifier = Modifier) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = weight, textAlign = TextAlign.Center),
        maxLines = 1,
    )
}
