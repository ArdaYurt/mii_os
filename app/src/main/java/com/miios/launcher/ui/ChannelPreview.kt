package com.miios.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RectangleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import com.miios.launcher.art.ChannelArt
import com.miios.launcher.art.ChannelArtLoader
import com.miios.launcher.data.InstalledApp

enum class PreviewButton { Menu, Start }

/**
 * The Wii's channel screen: the channel's art full-screen, with Menu and Start on a glass bar.
 * [progress] runs 0→1 as it opens; the art zooms from 1.08 to 1 while it fades in.
 */
@Composable
fun ChannelPreview(
    app: InstalledApp,
    focused: PreviewButton,
    progress: () -> Float,
    screen: IntSize,
) {
    val unit = LocalUnit.current
    val context = LocalContext.current
    var art by remember(app.packageName) { mutableStateOf<ChannelArt?>(null) }
    // Full-resolution art is only ever decoded here, for the one channel being previewed.
    LaunchedEffect(app.packageName) { art = ChannelArtLoader.load(context, app, screen.width, screen.height) }

    val hero: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            when (val a = art) {
                is ChannelArt.FullBleed -> Image(a.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                is ChannelArt.IconOnColor -> {
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(a.background.copy(alpha = 0.8f), a.background))))
                    Image(a.icon, null, Modifier.align(Alignment.Center).size(unit * 16f))
                }
                null -> Box(Modifier.fillMaxSize().background(LocalMiiColors.current.bg2))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1.08f - 0.08f * progress()
                    scaleX = s
                    scaleY = s
                },
        ) { hero() }
        Box(Modifier.fillMaxSize().background(Color(0x260A141E)))
        Label(
            app.label,
            unit * 1.2f,
            Color.White,
            FontWeight.Medium,
            Modifier.align(Alignment.TopStart).graphicsLayer { translationX = unit.toPx() * 2.8f; translationY = unit.toPx() * 2.4f },
        )
        CompositionLocalProvider(LocalBackdrop provides Backdrop(screen, hero)) {
            GlassSurface(
                RectangleShape,
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(unit * 10f)
                    .graphicsLayer { translationY = (1f - progress()) * unit.toPx() * 10f },
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(unit * 4f, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton("Menu", focused == PreviewButton.Menu)
                    PillButton("Start", focused == PreviewButton.Start)
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, focused: Boolean) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, Motion.focusScale, label = "pill")
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "pillRing")
    val shape = RoundedCornerShape(unit * 2.6f)
    Box(
        Modifier
            .size(unit * 17f, unit * 5.2f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        GlassSurface(shape, Modifier.fillMaxSize()) {
            Label(text, unit * 1.8f, colors.ink, FontWeight.Bold, Modifier.align(Alignment.Center))
        }
        Canvas(Modifier.fillMaxSize()) {
            if (ring > 0f) {
                drawRoundRect(
                    colors.focus.copy(alpha = ring),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit.toPx() * 2.6f),
                    style = Stroke(unit.toPx() * 0.3f),
                )
            }
        }
    }
}
