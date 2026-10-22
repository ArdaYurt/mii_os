package com.miios.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.miios.launcher.art.ChannelArt
import com.miios.launcher.art.ChannelStyle

/**
 * A known channel's screen, like a Wii channel's preview: its colours fill the screen, its name
 * drifts past in giant letters behind, and its logo (yours from the logo pack, or Mii_OS's
 * emblem) floats in front.
 */
@Composable
fun ChannelStage(style: ChannelStyle, art: ChannelArt?, label: String, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "stage")
    val drift by motion.animateFloat(
        0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "drift",
    )
    val bob by motion.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3_200), RepeatMode.Reverse), label = "bob",
    )
    BoxWithConstraints(modifier) {
        val u = maxWidth / 100f
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(style.stageTop, style.stageBottom)))
        }
        // the name, huge and faint, in three rows sliding opposite ways
        val words = (style.name.uppercase() + "   ").repeat(4)
        val fontSize = with(LocalDensity.current) { (u * 16f).toSp() }
        val textColor = Color.White.copy(alpha = 0.13f)
        Column(Modifier.fillMaxSize().graphicsLayer { rotationZ = -8f; scaleX = 1.3f; scaleY = 1.3f }) {
            for (row in 0 until 3) {
                val dir = if (row % 2 == 0) -1f else 1f
                BasicText(
                    words,
                    Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = dir * drift * size.width / 4f - if (dir > 0) size.width / 4f else 0f },
                    style = TextStyle(color = textColor, fontSize = fontSize, fontWeight = FontWeight.Black, fontFamily = MiiFont.family),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        // the logo card in front, gently floating
        val shape = RoundedCornerShape(u * 2.4f)
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationY = bob * u.toPx() * 0.6f; translationX = 0f }
                .size(u * 46f, u * 46f * 9f / 16f)
                .shadow(u * 2f, shape)
                .clip(shape),
        ) {
            ChannelArtView(art, label, Modifier.fillMaxSize())
        }
        // keep the bottom bar readable over bright colours
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), startY = size.height * 0.7f))
        }
        BasicText(
            label,
            Modifier.align(Alignment.TopCenter).graphicsLayer { translationY = u.toPx() * 4f },
            style = TextStyle(
                color = Color.White,
                fontSize = with(LocalDensity.current) { (u * 3f).toSp() },
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                fontFamily = MiiFont.family,
            ),
            maxLines = 1,
        )
    }
}
