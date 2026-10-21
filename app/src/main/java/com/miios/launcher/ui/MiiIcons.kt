package com.miios.launcher.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Mii_OS icon set. Every icon is drawn on a 24-unit grid with one rounded stroke weight,
 * so they read as a family at any size, from a dock button to a 4K channel screen.
 */
enum class MiiIcon { Gear, Home, Wifi, Display, Apps, Remote, Clock, Sliders, Download, Play, PlayCircle, Screens, Antenna, Chevron, Back, Film }

fun DrawScope.drawMiiIcon(icon: MiiIcon, color: Color, weight: Float = 2f) {
    val k = size.minDimension / 24f
    val ox = (size.width - 24f * k) / 2f
    val oy = (size.height - 24f * k) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)
    val stroke = Stroke(width = weight * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), weight * k, StrokeCap.Round)
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = false) = drawRoundRect(
        color, p(x, y), Size(w * k, h * k), CornerRadius(r * k), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke,
    )
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) =
        drawCircle(color, r * k, p(x, y), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke)
    fun path(fill: Boolean = false, build: Path.() -> Unit) =
        drawPath(Path().apply(build), color, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke)
    fun Path.m(x: Float, y: Float) = moveTo(ox + x * k, oy + y * k)
    fun Path.l(x: Float, y: Float) = lineTo(ox + x * k, oy + y * k)
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticBezierTo(ox + cx * k, oy + cy * k, ox + x * k, oy + y * k)

    when (icon) {
        MiiIcon.Gear -> {
            circle(12f, 12f, 3.2f)
            circle(12f, 12f, 6.6f)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 + 22.5)
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                line(12f + c * 6.6f, 12f + s * 6.6f, 12f + c * 9.4f, 12f + s * 9.4f)
            }
        }
        MiiIcon.Home -> path {
            m(3.5f, 11f); l(12f, 3.8f); l(20.5f, 11f)
            m(6f, 9.2f); l(6f, 20f); l(18f, 20f); l(18f, 9.2f)
            m(10f, 20f); l(10f, 14.5f); l(14f, 14.5f); l(14f, 20f)
        }
        MiiIcon.Wifi -> {
            path { m(2.8f, 9.4f); q(12f, 1.2f, 21.2f, 9.4f) }
            path { m(6.2f, 12.9f); q(12f, 7.6f, 17.8f, 12.9f) }
            path { m(9.5f, 16.3f); q(12f, 14f, 14.5f, 16.3f) }
            circle(12f, 19.6f, 1.2f, fill = true)
        }
        MiiIcon.Display -> {
            rrect(2.5f, 4f, 19f, 12.5f, 2.5f)
            line(8.5f, 20f, 15.5f, 20f)
            line(12f, 16.5f, 12f, 20f)
            path { m(9.5f, 8.2f); l(9.5f, 12.3f) }
            path { m(12f, 7f); l(12f, 13.5f) }
            path { m(14.5f, 8.2f); l(14.5f, 12.3f) }
        }
        MiiIcon.Apps -> {
            rrect(3.5f, 3.5f, 7f, 7f, 2f)
            rrect(13.5f, 3.5f, 7f, 7f, 2f)
            rrect(3.5f, 13.5f, 7f, 7f, 2f)
            rrect(13.5f, 13.5f, 7f, 7f, 2f, fill = true)
        }
        MiiIcon.Remote -> {
            rrect(7.5f, 2.5f, 9f, 19f, 3.5f)
            circle(12f, 8.5f, 2.2f)
            line(10.3f, 14f, 13.7f, 14f)
            line(10.3f, 17.2f, 13.7f, 17.2f)
        }
        MiiIcon.Clock -> {
            circle(12f, 12f, 8.8f)
            path { m(12f, 7f); l(12f, 12f); l(15.4f, 14.2f) }
        }
        MiiIcon.Sliders -> {
            line(4f, 7f, 20f, 7f); circle(9f, 7f, 2f, fill = true)
            line(4f, 12f, 20f, 12f); circle(15.5f, 12f, 2f, fill = true)
            line(4f, 17f, 20f, 17f); circle(7.5f, 17f, 2f, fill = true)
        }
        MiiIcon.Download -> {
            line(12f, 3.5f, 12f, 15f)
            path { m(7.2f, 10.4f); l(12f, 15.2f); l(16.8f, 10.4f) }
            line(4.5f, 20f, 19.5f, 20f)
        }
        MiiIcon.Play -> {
            rrect(1.5f, 5f, 21f, 14f, 4.5f, fill = true)
        }
        MiiIcon.PlayCircle -> {
            circle(12f, 12f, 9.2f)
            path(fill = true) { m(10f, 8.2f); l(16f, 12f); l(10f, 15.8f); close() }
        }
        MiiIcon.Screens -> {
            rrect(2f, 8f, 7f, 12f, 1.8f)
            rrect(11f, 4f, 11f, 8.5f, 1.8f)
            line(14f, 16f, 19f, 16f)
            path { m(10.8f, 15.5f); q(12f, 12.8f, 15f, 12.8f) }
        }
        MiiIcon.Antenna -> {
            rrect(2.5f, 9f, 19f, 12f, 2.5f)
            path { m(7.5f, 3f); l(12f, 8.5f); l(16.5f, 3f) }
            circle(17.5f, 13.5f, 1.1f, fill = true)
            circle(17.5f, 17f, 1.1f, fill = true)
            rrect(5f, 11.5f, 9f, 7f, 1.2f)
        }
        MiiIcon.Chevron -> path { m(8f, 3.5f); l(16f, 12f); l(8f, 20.5f) }
        MiiIcon.Back -> path { m(14.5f, 5f); l(7.5f, 12f); l(14.5f, 19f) }
        MiiIcon.Film -> {
            rrect(3f, 3f, 18f, 18f, 3f)
            line(7.5f, 3f, 7.5f, 21f)
            line(16.5f, 3f, 16.5f, 21f)
            line(3f, 12f, 7.5f, 12f)
            line(16.5f, 12f, 21f, 12f)
            path(fill = true) { m(10.4f, 9.4f); l(14.2f, 12f); l(10.4f, 14.6f); close() }
        }
    }
}
