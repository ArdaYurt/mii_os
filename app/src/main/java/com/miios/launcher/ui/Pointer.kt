package com.miios.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.exp

/** What the hand points at: the focused tile or button, as live layout coordinates. */
class PointerTarget {
    var coords: LayoutCoordinates? = null
        private set
    /** Bumped on every new target, so the hand wakes up and glides over. */
    var version by mutableIntStateOf(0)
        private set

    fun claim(c: LayoutCoordinates) {
        if (c === coords) return
        coords = c
        version++
    }
}

val LocalPointer = staticCompositionLocalOf { PointerTarget() }

/** Makes this the hand's target while [focused]. */
fun Modifier.pointerTarget(focused: Boolean): Modifier = composed {
    val pointer = LocalPointer.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    if (focused) SideEffect { holder[0]?.let { if (it.isAttached) pointer.claim(it) } }
    onGloballyPositioned {
        holder[0] = it
        if (focused) pointer.claim(it)
    }
}

/**
 * The pointing hand. It glides to whatever is focused and rests on its lower right, tipping a
 * little as it travels. It only animates while it moves, so it costs nothing when the remote is idle.
 */
@Composable
fun HandPointer(target: PointerTarget, show: Boolean) {
    val unit = LocalUnit.current
    var pos by remember { mutableStateOf(Offset.Unspecified) }
    var tip by remember { mutableStateOf(0f) }
    val alpha by animateFloatAsState(if (show) 1f else 0f, Motion.glow, label = "handAlpha")
    val press = remember { Animatable(1f) }

    LaunchedEffect(target) {
        snapshotFlow { target.version }.collectLatest {
            var last = 0L
            var still = 0
            while (still < 8) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                    last = now
                    val c = target.coords?.takeIf { it.isAttached } ?: run { still = 8; return@withFrameNanos }
                    val b = c.boundsInRoot()
                    val goal = Offset(b.left + b.width * 0.7f, b.top + b.height * 0.62f)
                    val from = pos
                    pos = if (from == Offset.Unspecified || alpha == 0f) goal else from + (goal - from) * (1f - exp(-dt * 13f))
                    val vx = if (from == Offset.Unspecified || dt == 0f) 0f else (pos.x - from.x) / dt
                    tip += ((vx / 2600f).coerceIn(-1f, 1f) - tip) * (1f - exp(-dt * 10f))
                    still = if (abs(goal.x - pos.x) + abs(goal.y - pos.y) < 0.5f && abs(tip) < 0.01f) still + 1 else 0
                }
            }
        }
    }
    // a small press each time it lands somewhere new
    LaunchedEffect(target.version) {
        press.snapTo(0.9f)
        press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }

    if (pos == Offset.Unspecified) return
    val w = unit * 4.2f
    Canvas(
        Modifier
            .size(w, w * 1.3f)
            .graphicsLayer {
                this.alpha = alpha
                // the fingertip sits on the target point
                translationX = pos.x - size.width * 0.36f
                translationY = pos.y - size.height * 0.03f
                rotationZ = -14f + tip * 10f
                scaleX = press.value
                scaleY = press.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.36f, 0.03f)
            },
    ) { drawHand() }
}

/** An original pointing glove: white, outlined in soft blue, with a cuff and a drop shadow. */
private fun DrawScope.drawHand() {
    val outline = Color(0xFF3A9BD6)
    val crease = Color(0xFFBFD4E2)
    // drawn on a 100 × 130 grid, scaled to the canvas
    scale(size.width / 100f, size.width / 100f, pivot = Offset.Zero) {
        val shapes = listOf(
            // index finger
            roundRect(30f, 3f, 17f, 60f, 8.5f),
            // middle, ring and little finger, curled
            roundRect(46f, 42f, 15f, 26f, 7.5f),
            roundRect(59f, 46f, 14f, 24f, 7f),
            roundRect(71f, 52f, 12f, 21f, 6f),
            // palm
            roundRect(22f, 50f, 61f, 62f, 18f),
            // thumb, folded across
            Path().apply {
                moveTo(24f, 66f)
                cubicTo(14f, 60f, 4f, 64f, 6f, 72f)
                cubicTo(8f, 80f, 20f, 86f, 30f, 88f)
                close()
            },
            // cuff
            roundRect(28f, 104f, 48f, 22f, 7f),
        )
        translate(2.4f, 3.4f) {
            for (p in shapes) drawPath(p, Color.Black.copy(alpha = 0.16f))
        }
        for (p in shapes) drawPath(p, outline, style = Stroke(width = 7f, join = StrokeJoin.Round))
        val fill = Brush.verticalGradient(listOf(Color.White, Color(0xFFEFF4F8)), startY = 0f, endY = 130f)
        for (p in shapes) drawPath(p, fill)
        // knuckle creases and the cuff seam
        drawLine(crease, Offset(47f, 60f), Offset(47f, 68f), 2.2f)
        drawLine(crease, Offset(60f, 62f), Offset(60f, 70f), 2.2f)
        drawLine(crease, Offset(72f, 65f), Offset(72f, 72f), 2.2f)
        drawLine(crease, Offset(31f, 110f), Offset(74f, 110f), 2.2f)
        // a highlight down the finger
        drawRoundRect(Color.White, topLeft = Offset(34f, 9f), size = Size(4f, 22f), cornerRadius = CornerRadius(2f))
    }
}

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply {
    addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + w, y + h, CornerRadius(r)))
}

/**
 * A page arrow at the screen's edge, shown when there's another page that way. It nudges outward
 * once when it appears and each time the page turns.
 */
@Composable
fun PageArrow(pointsRight: Boolean, visible: Boolean, turns: Int, modifier: Modifier) {
    val colors = LocalMiiColors.current
    val unit = LocalUnit.current
    val alpha by animateFloatAsState(if (visible) 1f else 0f, Motion.glow, label = "arrowAlpha")
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(visible, turns) {
        if (!visible) return@LaunchedEffect
        nudge.snapTo(1f)
        nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
    }
    if (alpha == 0f) return
    Canvas(
        modifier.graphicsLayer {
            this.alpha = alpha
            translationX = (if (pointsRight) 1f else -1f) * nudge.value * unit.toPx() * 0.9f
        },
    ) {
        val w = size.width
        val h = size.height
        val tri = Path().apply {
            if (pointsRight) {
                moveTo(w * 0.2f, h * 0.1f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.2f, h * 0.9f)
            } else {
                moveTo(w * 0.8f, h * 0.1f); lineTo(w * 0.1f, h * 0.5f); lineTo(w * 0.8f, h * 0.9f)
            }
            close()
        }
        val round = Stroke(width = w * 0.22f, join = StrokeJoin.Round)
        translate(w * 0.03f, h * 0.04f) { drawPath(tri, Color.Black.copy(alpha = 0.12f), style = round) }
        drawPath(tri, Color.White, style = Stroke(width = w * 0.34f, join = StrokeJoin.Round))
        drawPath(tri, colors.focus, style = round)
        drawPath(tri, Brush.verticalGradient(listOf(colors.focus.copy(alpha = 0.75f).compositeOverWhite(), colors.focus), 0f, h))
        // glossy top half
        drawPath(tri, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), 0f, h * 0.5f))
    }
}

private fun Color.compositeOverWhite(): Color =
    Color(red * alpha + (1f - alpha), green * alpha + (1f - alpha), blue * alpha + (1f - alpha))
