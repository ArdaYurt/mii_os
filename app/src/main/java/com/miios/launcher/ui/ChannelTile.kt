package com.miios.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.miios.launcher.art.ChannelArt
import com.miios.launcher.art.ChannelArtLoader
import com.miios.launcher.data.InstalledApp
import com.miios.launcher.data.Slot
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One channel. Focus pops it forward with a spring, leans it toward the direction the remote moved,
 * then settles it into a slight upward tilt with a light sheen, like an Apple TV poster.
 *
 * @param lean direction of the last D-pad move (-1, 0 or 1 per axis); [moveId] changes on every move.
 */
@Composable
fun ChannelTile(
    slot: Slot?,
    focused: Boolean,
    leanX: Int,
    leanY: Int,
    moveId: Int,
    modifier: Modifier = Modifier,
    onFocusedBounds: (Rect) -> Unit = {},
    /** Being moved: it lifts further off the grid. */
    lifted: Boolean = false,
) {
    val colors = LocalMiiColors.current
    val unit = LocalUnit.current
    val density = LocalDensity.current
    val radius = unit * 2f
    val outer = RoundedCornerShape(radius)

    val scale = animateFloatAsState(if (lifted) 1.2f else if (focused) 1.12f else 1f, Motion.focusScale, label = "scale")
    val glow = animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "glow")
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    LaunchedEffect(focused, moveId) {
        if (!focused) {
            coroutineScope {
                launch { tiltX.animateTo(0f, Motion.tilt) }
                launch { tiltY.animateTo(0f, Motion.tilt) }
            }
            return@LaunchedEffect
        }
        // lean into the move, then settle
        coroutineScope {
            launch { tiltX.animateTo(leanX * 0.7f, Motion.tilt) }
            launch { tiltY.animateTo(leanY * 0.7f, Motion.tilt) }
        }
        delay(90)
        coroutineScope {
            launch { tiltX.animateTo(0f, Motion.tilt) }
            launch { tiltY.animateTo(-0.25f, Motion.tilt) }
        }
    }

    Box(
        modifier
            .onGloballyPositioned { if (focused) onFocusedBounds(Rect(it.positionInRoot(), it.size.toSize())) }
            .pointerTarget(focused)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationY = tiltX.value * 9f
                rotationX = -tiltY.value * 9f
                cameraDistance = 14f * density.density
            }
            .drawWithContent {
                // focus glow sits behind the tile
                if (glow.value > 0f) {
                    val outline = outer.createOutline(size, layoutDirection, this)
                    val spread = unit.toPx() * 0.9f
                    for (i in 3 downTo 1) {
                        val grow = spread * i / 3f
                        drawContext.canvas.save()
                        drawContext.transform.translate(-grow, -grow)
                        drawOutline(
                            RoundedCornerShape(radius + unit * (0.9f * i / 3f))
                                .createOutline(size.copy(size.width + grow * 2, size.height + grow * 2), layoutDirection, this),
                            colors.focus.copy(alpha = 0.10f * glow.value),
                        )
                        drawContext.canvas.restore()
                    }
                    drawContent()
                    drawOutline(outline, colors.focus.copy(alpha = glow.value), style = Stroke(unit.toPx() * 0.32f))
                } else {
                    // channels are edge to edge with no frame; only empty places keep a faint edge
                    drawContent()
                    if (slot == null || slot is Slot.Gap) {
                        drawOutline(outer.createOutline(size, layoutDirection, this), colors.tileBorder, style = Stroke(unit.toPx() * 0.12f))
                    }
                }
            }
            .clip(outer)
            .background(if (slot == null || slot is Slot.Gap) colors.tileEmpty else colors.tileFrame),
    ) {
        if (slot == null || slot is Slot.Gap) {
            EmptySlot(Modifier.fillMaxSize())
        } else if (slot is Slot.Folder) {
            FolderArt(slot, Modifier.fillMaxSize())
        } else {
            val app = (slot as Slot.App).app
            Box(Modifier.fillMaxSize()) {
                LoadedArt(app, Modifier.fillMaxSize().notInstalled(!app.installed))
                if (!app.installed) InstallTag(Modifier.align(Alignment.BottomEnd).padding(unit * 0.6f))
                // sheen: a soft highlight that moves opposite to the tilt
                Canvas(Modifier.fillMaxSize()) {
                    if (glow.value > 0f) {
                        val c = Offset(size.width * (0.5f + tiltX.value * 0.4f), size.height * (0.4f + tiltY.value * 0.4f))
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.32f * glow.value), Color.Transparent),
                                center = c,
                                radius = size.width * 0.55f,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** A folder: up to four of its channels in miniature, above its name. */
@Composable
private fun FolderArt(folder: Slot.Folder, modifier: Modifier) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Column(
        modifier.background(colors.bg2).padding(unit * 0.7f),
        verticalArrangement = Arrangement.spacedBy(unit * 0.4f),
    ) {
        val minis = folder.apps.take(4)
        for (r in 0 until 2) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(unit * 0.4f)) {
                for (c in 0 until 2) {
                    val app = minis.getOrNull(r * 2 + c)
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(unit * 0.6f)).background(colors.tileEmpty)) {
                        if (app != null) LoadedArt(app, Modifier.fillMaxSize())
                    }
                }
            }
        }
        Label(folder.name, unit * 1.1f, colors.ink, FontWeight.Bold, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LoadedArt(app: InstalledApp, modifier: Modifier) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var art by remember(app.packageName) { mutableStateOf<ChannelArt?>(null) }
    val version = ChannelArtLoader.version
    LaunchedEffect(app.packageName, size, version) {
        if (size.width > 0) {
            art = ChannelArtLoader.cached(app, size.width, size.height)
                ?: ChannelArtLoader.load(context, app, size.width, size.height)
        }
    }
    ChannelArtView(art, app.label, modifier.onSizeChanged { size = it })
}

/**
 * An empty place, as on the Wii Menu: a soft grey tile with fine horizontal lines and the
 * name pressed faintly into it.
 */
@Composable
private fun EmptySlot(modifier: Modifier) {
    val unit = LocalUnit.current
    val textSize = with(LocalDensity.current) { (unit * 3.4f).toSp() }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // a little light at the top, like a moulded plastic slot
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), 0f, size.height * 0.5f))
            val step = unit.toPx() * 0.42f
            var y = step
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = step * 0.4f)
                y += step
            }
        }
        // embossed: a white highlight just below, the grey letters on top
        val style = androidx.compose.ui.text.TextStyle(fontSize = textSize, fontWeight = FontWeight.Bold, fontFamily = MiiFont.family)
        androidx.compose.foundation.text.BasicText(
            "Mii",
            Modifier.graphicsLayer { translationY = unit.toPx() * 0.12f },
            style = style.copy(color = Color.White.copy(alpha = 0.9f)),
        )
        androidx.compose.foundation.text.BasicText("Mii", style = style.copy(color = Color(0xFFC9CDD1)))
    }
}
