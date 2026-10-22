package com.miios.launcher.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.cos
import kotlin.math.sin

/*
 * The dock's two emblems, drawn like the Wii Menu's buttons: solid sky-blue shapes with a soft
 * gradient, a white glossy top and a darker rim, on a 24-unit grid so they stay sharp at any size.
 */

private val sky = Color(0xFF5FD0F5)
private val deep = Color(0xFF1E8FD0)
private val rim = Color(0xFF1677B3)

/** Settings: a chunky glossy gear with a white hub. */
fun DrawScope.drawSettingsEmblem() {
    val k = size.minDimension / 24f
    val o = Offset((size.width - 24f * k) / 2f, (size.height - 24f * k) / 2f)
    val c = o + Offset(12f * k, 12f * k)
    val teeth = 8
    val gear = Path().apply {
        for (i in 0 until teeth * 4) {
            // each tooth: rise, top, fall, valley
            val a = Math.toRadians(i * 360.0 / (teeth * 4) - 90.0)
            val r = when (i % 4) { 0, 1 -> 11f; else -> 8.4f } * k
            val p = c + Offset(cos(a).toFloat() * r, sin(a).toFloat() * r)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(gear, Color.Black.copy(alpha = 0.12f), style = Stroke(2.6f * k, join = StrokeJoin.Round))
    drawPath(gear, Brush.verticalGradient(listOf(sky, deep), o.y, o.y + 24f * k))
    drawPath(gear, rim, style = Stroke(1.1f * k, join = StrokeJoin.Round))
    gloss(gear, o, k)
    drawCircle(Color.White, 3.6f * k, c)
    drawCircle(rim, 3.6f * k, c, style = Stroke(1.1f * k))
}

/** Share: a phone sending its picture across to a TV. */
fun DrawScope.drawShareEmblem() {
    val k = size.minDimension / 24f
    val o = Offset((size.width - 24f * k) / 2f, (size.height - 24f * k) / 2f)
    fun p(x: Float, y: Float) = o + Offset(x * k, y * k)
    val line = Stroke(1.1f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // the TV: a glossy blue screen in a white bezel, on a little stand
    drawRoundRect(Color.Black.copy(alpha = 0.12f), p(5.3f, 2.6f), Size(18f * k, 13.4f * k), CornerRadius(2.6f * k))
    drawRoundRect(Color.White, p(5f, 2.2f), Size(18f * k, 13.4f * k), CornerRadius(2.6f * k))
    drawRoundRect(rim, p(5f, 2.2f), Size(18f * k, 13.4f * k), CornerRadius(2.6f * k), style = line)
    val screen = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(p(6.8f, 4f).x, p(6.8f, 4f).y, p(21.2f, 13.8f).x, p(21.2f, 13.8f).y, CornerRadius(1.4f * k)))
    }
    drawPath(screen, Brush.verticalGradient(listOf(sky, deep), p(0f, 4f).y, p(0f, 13.8f).y))
    gloss(screen, o, k)
    drawLine(rim, p(11f, 18.2f), p(17f, 18.2f), 1.6f * k, StrokeCap.Round)
    drawLine(rim, p(14f, 15.6f), p(14f, 18.2f), 1.4f * k, StrokeCap.Round)

    // the waves from the phone to the screen, white edged in blue so they read on both
    for ((x, d) in listOf(1f to 7f, -1.5f to 12f)) {
        drawArc(rim, -80f, 70f, false, p(x, 12f - d / 2f), Size(d * k, d * k), style = Stroke(2.3f * k, cap = StrokeCap.Round))
        drawArc(Color.White, -80f, 70f, false, p(x, 12f - d / 2f), Size(d * k, d * k), style = Stroke(1.1f * k, cap = StrokeCap.Round))
    }

    // the phone, in front at the lower left
    drawRoundRect(Color.Black.copy(alpha = 0.14f), p(1.4f, 11.4f), Size(7f * k, 11.6f * k), CornerRadius(1.8f * k))
    drawRoundRect(Color.White, p(1f, 11f), Size(7f * k, 11.6f * k), CornerRadius(1.8f * k))
    drawRoundRect(rim, p(1f, 11f), Size(7f * k, 11.6f * k), CornerRadius(1.8f * k), style = line)
    drawRoundRect(Brush.verticalGradient(listOf(sky, deep), p(0f, 12.6f).y, p(0f, 20f).y), p(2.2f, 12.6f), Size(4.6f * k, 7.4f * k), CornerRadius(0.8f * k))
    drawCircle(rim, 0.55f * k, p(4.5f, 21.1f))
}

/** The Wii's white shine over the top half of a shape. */
private fun DrawScope.gloss(shape: Path, o: Offset, k: Float) {
    clipPath(shape) {
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.08f)), o.y, o.y + 12f * k),
            topLeft = o + Offset(-2f * k, -10f * k),
            size = Size(28f * k, 21f * k),
        )
    }
}
