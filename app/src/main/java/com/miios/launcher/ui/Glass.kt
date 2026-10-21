package com.miios.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** What sits behind the glass: the full-screen scene, drawn again inside each panel and blurred. */
class Backdrop(val size: IntSize, val content: @Composable () -> Unit)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Liquid Glass panel: the scene behind it, blurred, plus a tint, a bright top edge and a soft inner shade.
 *
 * Cost control: the backdrop is static while nothing moves behind the panel, so the blur layer is
 * rendered once and reused; Mii_OS only uses this for a couple of large panels (dock, preview bar).
 */
@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalMiiColors.current
    val backdrop = LocalBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape),
    ) {
        if (backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .blur(28.dp, BlurredEdgeTreatment.Rectangle)
                    .layout { measurable, constraints ->
                        val scene = measurable.measure(Constraints.fixed(backdrop.size.width, backdrop.size.height))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            scene.place(-origin.x.roundToInt(), -origin.y.roundToInt())
                        }
                    },
            ) { backdrop.content() }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(colors.glass)
                .drawWithContent {
                    drawContent()
                    // inner shade toward the bottom edge gives the sheet thickness
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.glassShade), startY = size.height * 0.55f))
                }
                .border(1.dp, Brush.verticalGradient(listOf(colors.glassEdge, colors.glassEdge.copy(alpha = 0.15f))), shape),
        )
        content()
    }
}

/** The Wii Menu's backdrop: white fading to soft grey, with its faint horizontal lines. */
@Composable
fun Scenery(colors: MiiColors, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(colors.bg1, colors.bg2)))
        val w = size.width
        val step = w * 0.0062f
        var y = step
        while (y < size.height) {
            drawLine(colors.lines, Offset(0f, y), Offset(w, y), strokeWidth = w * 0.0009f)
            y += step
        }
    }
}
