package com.miios.launcher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Immutable
data class MiiColors(
    val bg1: Color,
    val bg2: Color,
    val lines: Color,
    val ink: Color,
    val inkSoft: Color,
    val glass: Color,
    val glassEdge: Color,
    val glassShade: Color,
    val tileFrame: Color,
    val tileBorder: Color,
    val tileEmpty: Color,
    val focus: Color,
)

/** The Wii Menu's palette: white and soft grey, with the Wii's sky blue for focus and accents. */
val WiiColors = MiiColors(
    bg1 = Color(0xFFFFFFFF),
    bg2 = Color(0xFFEDEFF1),
    lines = Color(0x0F8C96A0),
    ink = Color(0xFF55595E),
    inkSoft = Color(0xFF9A9EA3),
    glass = Color(0xC7F4F5F6),
    glassEdge = Color(0xFFFFFFFF),
    glassShade = Color(0x1A6E7884),
    tileFrame = Color(0xFFFFFFFF),
    tileBorder = Color(0xFFCDD1D5),
    tileEmpty = Color(0xFFE6E8EB),
    focus = Color(0xFF34BEED),
)

val LocalMiiColors = staticCompositionLocalOf { WiiColors }

/**
 * Layout unit: 1% of the screen width, the same unit the HTML mockup uses (cqw),
 * so the launcher keeps its proportions at 720p, 1080p or 4K.
 */
val LocalUnit = staticCompositionLocalOf { Dp(19.2f) }
