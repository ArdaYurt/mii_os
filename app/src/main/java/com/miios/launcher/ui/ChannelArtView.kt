package com.miios.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.miios.launcher.art.ChannelArt
import com.miios.launcher.art.ChannelStyle
import com.miios.launcher.art.Glyph

/** Draws a channel's art at whatever size it's given; used by the tiles and the channel screen. */
@Composable
fun ChannelArtView(art: ChannelArt?, label: String, modifier: Modifier = Modifier) {
    val colors = LocalMiiColors.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // 1% of this view's width; text and glyphs scale with the art, from tile to full screen
        val u = maxWidth / 100f
        when (art) {
            is ChannelArt.FullBleed -> Image(art.image, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is ChannelArt.IconOnColor -> {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(art.background.copy(alpha = 0.85f), art.background))))
                Image(art.icon, null, Modifier.align(Alignment.Center).padding(bottom = u * 8f).size(maxHeight * 0.5f))
                ArtText(label, u * 7.5f, Color.White, Modifier.align(Alignment.BottomCenter).padding(bottom = u * 4f))
            }
            is ChannelArt.Styled -> StyledArt(art.style, u)
            ChannelArt.Blank, null -> Box(Modifier.fillMaxSize().background(colors.tileEmpty))
        }
    }
}

@Composable
private fun StyledArt(style: ChannelStyle, u: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(style.top, style.bottom)))
            // Wii-style gloss: a soft white sheen over the top half
            drawOval(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f)),
                    startY = -size.height * 0.4f,
                    endY = size.height * 0.5f,
                ),
                topLeft = Offset(-size.width * 0.25f, -size.height * 0.62f),
                size = Size(size.width * 1.5f, size.height * 1.1f),
            )
        }
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = u * 6f),
            horizontalArrangement = Arrangement.spacedBy(u * 3.5f, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (style.glyph != Glyph.None) {
                Canvas(Modifier.size(u * 15f)) { drawGlyph(style.glyph, style.ink) }
            }
            ArtText(style.name, u * 11f, style.ink, Modifier, weight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ArtText(text: String, size: androidx.compose.ui.unit.Dp, color: Color, modifier: Modifier, weight: FontWeight = FontWeight.Bold) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = weight, textAlign = TextAlign.Center, letterSpacing = fontSize * -0.02f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun DrawScope.drawGlyph(glyph: Glyph, ink: Color) {
    val s = size.minDimension
    when (glyph) {
        Glyph.Play -> {
            drawRoundRect(ink, topLeft = Offset(0f, s * 0.15f), size = Size(s, s * 0.7f), cornerRadius = CornerRadius(s * 0.2f))
            val tri = Path().apply {
                moveTo(s * 0.4f, s * 0.33f)
                lineTo(s * 0.68f, s * 0.5f)
                lineTo(s * 0.4f, s * 0.67f)
                close()
            }
            drawPath(tri, Color(0xFFD40000))
        }
        Glyph.Screens -> {
            val w = s * 0.08f
            // a tablet beaming to a TV
            drawRoundRect(ink, topLeft = Offset(s * 0.02f, s * 0.38f), size = Size(s * 0.3f, s * 0.46f), cornerRadius = CornerRadius(s * 0.06f), style = Stroke(w))
            drawRoundRect(ink, topLeft = Offset(s * 0.42f, s * 0.16f), size = Size(s * 0.56f, s * 0.42f), cornerRadius = CornerRadius(s * 0.06f), style = Stroke(w))
            drawLine(ink, Offset(s * 0.56f, s * 0.74f), Offset(s * 0.84f, s * 0.74f), w)
        }
        Glyph.None -> Unit
    }
}

private val greyscale = androidx.compose.ui.graphics.Paint().apply {
    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) },
    )
}

/** Greys out a channel whose app isn't installed yet: no colour, lifted toward white like an unlit Wii channel. */
fun Modifier.notInstalled(active: Boolean): Modifier = if (!active) this else drawWithContent {
    drawIntoCanvas { it.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), greyscale) }
    drawContent()
    drawIntoCanvas { it.restore() }
    drawRect(Color.White.copy(alpha = 0.38f))
}

/** Small "Install" tag shown on a greyed-out channel. */
@Composable
fun InstallTag(modifier: Modifier = Modifier) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Row(
        modifier
            .background(colors.focus, androidx.compose.foundation.shape.RoundedCornerShape(unit * 1f))
            .padding(horizontal = unit * 0.7f, vertical = unit * 0.25f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(unit * 0.35f),
    ) {
        Canvas(Modifier.size(unit * 0.9f)) {
            val s = size.minDimension
            val w = s * 0.16f
            drawLine(Color.White, Offset(s / 2, s * 0.05f), Offset(s / 2, s * 0.65f), w)
            drawLine(Color.White, Offset(s * 0.22f, s * 0.4f), Offset(s / 2, s * 0.68f), w)
            drawLine(Color.White, Offset(s * 0.78f, s * 0.4f), Offset(s / 2, s * 0.68f), w)
            drawLine(Color.White, Offset(s * 0.1f, s * 0.95f), Offset(s * 0.9f, s * 0.95f), w)
        }
        ArtText("Install", unit * 0.85f, Color.White, Modifier)
    }
}
