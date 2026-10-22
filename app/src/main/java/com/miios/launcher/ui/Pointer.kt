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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
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
    val own = HandImage.bitmap
    // the fingertip: on the drawn hand it's known; on your own image it's near the top, left of centre
    val tipX = if (own != null) HandImage.TIP_X else 0.455f
    val tipY = if (own != null) HandImage.TIP_Y else 0.03f
    Canvas(
        Modifier
            .size(w, if (own != null) w * own.height / own.width else w * 1.3f)
            .graphicsLayer {
                this.alpha = alpha
                // the fingertip sits on the target point
                translationX = pos.x - size.width * tipX
                translationY = pos.y - size.height * tipY
                rotationZ = (if (own != null) 0f else -6f) + tip * 10f
                scaleX = press.value
                scaleY = press.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(tipX, tipY)
            },
    ) {
        if (own != null) {
            drawImage(
                own,
                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        } else {
            drawHand()
        }
    }
}

/**
 * Your own pointer, from the cursor pack: /sdcard/Mii_OS/cursor/hand.png (a PNG with a transparent
 * background, pointing up, its fingertip touching the top edge a little left of centre).
 */
object HandImage {
    const val TIP_X = 0.4f
    const val TIP_Y = 0.03f
    var bitmap: androidx.compose.ui.graphics.ImageBitmap? by mutableStateOf(null)
        private set
    private var stamp = 0L

    fun refresh(context: android.content.Context) {
        val file = com.miios.launcher.storage.PackFolders.find(context, "cursor", "hand", listOf("png", "webp"))
        val next = file?.let { it.lastModified() + it.length() } ?: 0L
        if (next == stamp) return
        stamp = next
        bitmap = file?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }
}

/**
 * An original pointing hand in the Wii's manner: a plump white hand pointing straight up, thumb
 * out to the side, the other fingers curled, outlined in soft charcoal and shaded toward its
 * lower right. For an exact copy of the Wii's own hand, use the cursor pack (hand.png).
 */
private fun DrawScope.drawHand() {
    val outline = Color(0xFF4B5058)
    val crease = Color(0xFFB9C1CA)
    // drawn on a 100 × 130 grid, scaled to the canvas
    scale(size.width / 100f, size.width / 100f, pivot = Offset.Zero) {
        val shapes = listOf(
            // index finger, straight up
            roundRect(36f, 2f, 19f, 62f, 9.5f),
            // middle, ring and little finger, curled into knuckles
            roundRect(52f, 40f, 17f, 27f, 8.5f),
            roundRect(65f, 45f, 15f, 25f, 7.5f),
            roundRect(76f, 52f, 12f, 21f, 6f),
            // palm, rounded off at the wrist
            roundRect(31f, 48f, 56f, 64f, 22f),
            // thumb, out to the side and up
            Path().apply {
                moveTo(37f, 63f)
                cubicTo(27f, 57f, 17f, 48f, 11f, 51f)
                cubicTo(4f, 55f, 7f, 64f, 13f, 68f)
                cubicTo(21f, 74f, 30f, 79f, 37f, 82f)
                close()
            },
        )
        translate(3f, 4f) {
            for (p in shapes) drawPath(p, Color.Black.copy(alpha = 0.18f), style = Stroke(width = 6f, join = StrokeJoin.Round))
            for (p in shapes) drawPath(p, Color.Black.copy(alpha = 0.18f))
        }
        for (p in shapes) drawPath(p, outline, style = Stroke(width = 6f, join = StrokeJoin.Round))
        // white at the top left, a soft grey toward the lower right, like the Wii's shaded glove
        val fill = Brush.linearGradient(
            0f to Color.White, 0.55f to Color(0xFFF7F9FB), 1f to Color(0xFFD9E0E7),
            start = Offset(20f, 10f), end = Offset(90f, 120f),
        )
        for (p in shapes) drawPath(p, fill)
        // where the fingers and thumb meet the palm
        drawLine(crease, Offset(54f, 58f), Offset(54f, 67f), 2.4f, StrokeCap.Round)
        drawLine(crease, Offset(67f, 62f), Offset(67f, 70f), 2.4f, StrokeCap.Round)
        drawLine(crease, Offset(78f, 66f), Offset(78f, 72f), 2.4f, StrokeCap.Round)
        drawLine(crease, Offset(33f, 66f), Offset(38f, 76f), 2.4f, StrokeCap.Round)
        // a gloss down the finger
        drawRoundRect(Color.White, topLeft = Offset(40f, 8f), size = Size(4.5f, 24f), cornerRadius = CornerRadius(2.25f))
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
