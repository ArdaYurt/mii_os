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
import androidx.compose.ui.graphics.drawOutline
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
    val colors = LocalMiiColors.current
    Box(modifier) {
        // the Wii's grey bar, its curved top edge traced in sky blue
        Canvas(Modifier.fillMaxSize()) {
            val outline = DockShape.createOutline(size, layoutDirection, this)
            drawOutline(outline, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF1F2F4), Color(0xFFDCDFE2))))
            val rise = size.height * 0.24f
            val edge = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, rise)
                quadraticBezierTo(size.width / 2f, -rise, size.width, rise)
            }
            val w = unit.toPx() * 0.32f
            drawPath(edge, Color.White, style = Stroke(w * 3f))
            drawPath(edge, colors.focus.copy(alpha = 0.85f), style = Stroke(w, cap = StrokeCap.Round))
        }
        Row(
            Modifier.fillMaxSize().padding(start = unit * 7f, end = unit * 7f, top = unit * 1.6f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton("Settings", focused == DockButton.Settings) { drawSettingsEmblem() }
            Clock(now)
            RoundButton("Share", focused == DockButton.Share) { drawShareEmblem() }
        }
    }
}

@Composable
private fun RoundButton(label: String, focused: Boolean, icon: DrawScope.() -> Unit) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, Motion.focusScale, label = "dockScale")
    val rimBlue = colors.focus
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "dockRing")
    Box(
        Modifier
            .size(unit * 7f)
            .pointerTarget(focused)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        // a white button in a grey socket with a blue rim, like the Wii's
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFFCDD1D5), r)
            drawCircle(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color(0xFFE9ECEF))), r * 0.94f)
            drawCircle(rimBlue.copy(alpha = 0.7f), r * 0.86f, style = Stroke(r * 0.07f))
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Canvas(Modifier.size(unit * 3f)) { icon() }
                Spacer(Modifier.height(unit * 0.15f))
                Label(label, unit * 0.85f, colors.ink, FontWeight.Bold)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (ring > 0f) drawCircle(colors.focus.copy(alpha = ring), style = Stroke(unit.toPx() * 0.4f))
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
            SegmentTime(now.format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm")), unit * 4.4f, colors.inkSoft)
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
            now.format(DateTimeFormatter.ofPattern("EEE d/M", Locale.getDefault())),
            unit * 2.1f,
            colors.inkSoft,
            FontWeight.Bold,
            Modifier.padding(top = unit * 0.4f),
        )
    }
}

/**
 * The time in thin segmented digits, like the Wii Menu's clock: each digit is built from up to
 * seven rounded bars with small gaps between them.
 */
@Composable
private fun SegmentTime(text: String, height: Dp, color: Color) {
    val digitW = height * 0.52f
    val gap = height * 0.22f
    val colonW = height * 0.22f
    val width = text.fold(height * 0f) { w, ch -> w + (if (ch == ':') colonW else digitW) + gap } - gap
    Canvas(Modifier.size(width, height)) {
        val h = size.height
        val dw = digitW.toPx()
        val t = h * 0.075f
        var x = 0f
        for (ch in text) {
            if (ch == ':') {
                val cx = x + colonW.toPx() / 2f
                drawCircle(color, t * 0.75f, Offset(cx, h * 0.32f))
                drawCircle(color, t * 0.75f, Offset(cx, h * 0.68f))
                x += colonW.toPx() + gap.toPx()
                continue
            }
            val on = SEGMENTS[ch.digitToIntOrNull() ?: continue]
            val g = t * 0.9f
            // segments: a top, b upper right, c lower right, d bottom, e lower left, f upper left, g middle
            fun bar(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x + x1, y1), Offset(x + x2, y2), t, StrokeCap.Round)
            val l = t / 2f
            val r = dw - t / 2f
            val top = t / 2f
            val mid = h / 2f
            val bot = h - t / 2f
            if ('a' in on) bar(l + g, top, r - g, top)
            if ('b' in on) bar(r, top + g, r, mid - g)
            if ('c' in on) bar(r, mid + g, r, bot - g)
            if ('d' in on) bar(l + g, bot, r - g, bot)
            if ('e' in on) bar(l, mid + g, l, bot - g)
            if ('f' in on) bar(l, top + g, l, mid - g)
            if ('g' in on) bar(l + g, mid, r - g, mid)
            x += dw + gap.toPx()
        }
    }
}

private val SEGMENTS = listOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")

@Composable
fun Label(text: String, size: Dp, color: Color, weight: FontWeight, modifier: Modifier = Modifier) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = weight, textAlign = TextAlign.Center, fontFamily = MiiFont.family),
        maxLines = 1,
    )
}
