package com.miios.launcher.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * One place for every motion curve, so the whole launcher moves with the same feel.
 * All of these animate graphicsLayer properties only (scale, rotation, translation, alpha),
 * which the GPU composites without re-measuring or redrawing content.
 */
object Motion {
    /** Focus pop: a quick spring with a small overshoot, like the Wii pointer landing on a channel. */
    val focusScale = spring<Float>(dampingRatio = 0.62f, stiffness = 520f)

    /** Tilt follows the remote quickly and settles without wobble. */
    val tilt = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

    /** Glow and sheen fade. */
    val glow = tween<Float>(durationMillis = 180)

    /** Page slide: decisive start, soft landing. */
    val page = spring<Float>(dampingRatio = 0.86f, stiffness = 260f)

    /** Channel preview zooming in from the grid. */
    val preview = spring<Float>(dampingRatio = 0.82f, stiffness = 300f)

    val emphasized = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}
