package com.miios.launcher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Immutable
data class MiiColors(
    val bg1: Color,
    val bg2: Color,
    val ink: Color,
    val inkSoft: Color,
    val glass: Color,
    val glassEdge: Color,
    val glassShade: Color,
    val tileFrame: Color,
    val tileEmpty: Color,
    val focus: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
)

val DayColors = MiiColors(
    bg1 = Color(0xFFF7FAFC),
    bg2 = Color(0xFFE3EDF5),
    ink = Color(0xFF3A4552),
    inkSoft = Color(0xFF7D8A98),
    glass = Color(0x6BFFFFFF),
    glassEdge = Color(0xD9FFFFFF),
    glassShade = Color(0x2E7896B4),
    tileFrame = Color(0xE6FFFFFF),
    tileEmpty = Color(0x8CCDD6E0),
    focus = Color(0xFF2AA7E6),
    blob1 = Color(0xFF8FD3FF),
    blob2 = Color(0xFFFFD4A8),
    blob3 = Color(0xFFC8B8FF),
)

val NightColors = MiiColors(
    bg1 = Color(0xFF0D1724),
    bg2 = Color(0xFF18263A),
    ink = Color(0xFFE3ECF5),
    inkSoft = Color(0xFF8EA2B8),
    glass = Color(0x61283C55),
    glassEdge = Color(0x59BEDCFF),
    glassShade = Color(0x59000000),
    tileFrame = Color(0x47A0BEDC),
    tileEmpty = Color(0x733C4E64),
    focus = Color(0xFF5CC6FF),
    blob1 = Color(0xFF1F6FAE),
    blob2 = Color(0xFF7A3F86),
    blob3 = Color(0xFF1D8D8A),
)

val LocalMiiColors = staticCompositionLocalOf { DayColors }

/**
 * Layout unit: 1% of the screen width, the same unit the HTML mockup uses (cqw),
 * so the launcher keeps its proportions at 720p, 1080p or 4K.
 */
val LocalUnit = staticCompositionLocalOf { Dp(19.2f) }
