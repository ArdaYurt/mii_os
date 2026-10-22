package com.miios.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import com.miios.launcher.art.ChannelArt
import com.miios.launcher.art.ChannelArtLoader
import com.miios.launcher.art.ChannelStyles
import com.miios.launcher.data.InstalledApp

enum class PreviewButton { Menu, Start }

/**
 * The Wii's channel screen. It grows out of the channel's own tile ([origin]) to fill the screen,
 * with Menu and Start on a glass bar. [progress] runs 0→1 as it opens; [launch] runs 0→1 after
 * Start, zooming in a touch further while the screen washes to white, the way a Wii channel starts.
 */
@Composable
fun ChannelPreview(
    app: InstalledApp,
    focused: PreviewButton,
    progress: () -> Float,
    launch: () -> Float,
    origin: Rect,
    screen: IntSize,
) {
    val unit = LocalUnit.current
    val context = LocalContext.current
    var art by remember(app.packageName) { mutableStateOf<ChannelArt?>(null) }
    // Full-resolution art is only ever decoded here, for the one channel being previewed.
    LaunchedEffect(app.packageName, ChannelArtLoader.version) { art = ChannelArtLoader.load(context, app, screen.width, screen.height) }

    val hero: @Composable () -> Unit = {
        ChannelArtView(art, app.label, Modifier.fillMaxSize().notInstalled(!app.installed))
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // grow from the tile's rectangle to the full screen. The spring overshoots 1 a touch
                // as it settles; clamp, or the corner radius below goes negative and Compose throws.
                val p = progress().coerceIn(0f, 1f)
                val w = size.width
                val h = size.height
                val start = if (origin.width > 0f) origin else Rect(w * 0.4f, h * 0.4f, w * 0.6f, h * 0.6f)
                val sx = lerp(start.width / w, 1f, p)
                val sy = lerp(start.height / h, 1f, p)
                val l = launch()
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = sx * (1f + 0.04f * l)
                scaleY = sy * (1f + 0.04f * l)
                translationX = lerp(start.left, 0f, p) - w * 0.02f * l
                translationY = lerp(start.top, 0f, p) - h * 0.02f * l
                // the tile's rounded corners flatten out as it fills the screen
                val cornerPx = (unit.toPx() * 2f * (1f - p)).coerceAtLeast(0f)
                shape = RoundedCornerShape(CornerSize(cornerPx / sx.coerceAtLeast(0.01f)))
                clip = true
                alpha = (p * 4f).coerceAtMost(1f)
            },
    ) {
        hero()
        Box(Modifier.fillMaxSize().background(Color(0x14000000)))
        CompositionLocalProvider(LocalBackdrop provides Backdrop(screen, hero)) {
            GlassSurface(
                RectangleShape,
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(unit * 10f)
                    .graphicsLayer {
                        val p = progress()
                        // the bar rises in once the channel has nearly filled the screen
                        val t = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        translationY = (1f - t) * unit.toPx() * 10f
                    },
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(unit * 4f, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton("Options", MiiIcon.Sliders, focused == PreviewButton.Menu)
                    PillButton(if (app.installed) "Start" else "Install", if (app.installed) MiiIcon.PlayCircle else MiiIcon.Download, focused == PreviewButton.Start)
                }
            }
        }
        // a line of help for channels that need it, e.g. Screen Share explaining AirPlay
        val blurb = remember(app.packageName) { ChannelStyles.forApp(app.packageName, app.label)?.blurb }
        if (blurb != null) {
            GlassSurface(
                RoundedCornerShape(unit * 1.6f),
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = unit * 12f)
                    .fillMaxWidth(0.7f)
                    .graphicsLayer { alpha = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f) },
            ) {
                BasicText(
                    blurb,
                    Modifier.align(Alignment.Center).padding(horizontal = unit * 2f, vertical = unit * 1.2f),
                    style = TextStyle(
                        color = LocalMiiColors.current.ink,
                        fontSize = with(LocalDensity.current) { (unit * 1.35f).toSp() },
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        // white wash as the app starts
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = launch() }.background(Color.White))
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
internal fun PillButton(text: String, icon: MiiIcon, focused: Boolean) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val scale = animateFloatAsState(if (focused) 1.08f else 1f, Motion.focusScale, label = "pill")
    val ring = animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "pillRing")
    val shape = RoundedCornerShape(unit * 2.6f)
    Box(
        Modifier
            .size(unit * 17f, unit * 5.2f)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    ) {
        GlassSurface(shape, Modifier.fillMaxSize()) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 0.9f)) {
                Canvas(Modifier.size(unit * 2.2f)) { drawMiiIcon(icon, colors.focus, weight = 2.4f) }
                Label(text, unit * 1.8f, colors.ink, FontWeight.Bold)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                if (ring.value > 0f) colors.focus.copy(alpha = ring.value) else colors.tileBorder,
                cornerRadius = CornerRadius(unit.toPx() * 2.6f),
                style = Stroke(unit.toPx() * if (ring.value > 0f) 0.3f else 0.12f),
            )
        }
    }
}
