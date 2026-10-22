package com.miios.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.miios.launcher.service.HomeButtonService
import com.miios.launcher.service.RemoteButtons

/**
 * Remote Buttons, opened from Mii_OS Settings: each row is one taught button and what it opens.
 * OK teaches the row a button (press it next), ← → change what it opens, Back closes.
 */
@Composable
fun RemotePanel(focused: Int, progress: () -> Float) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .background(Color(0x55000000)),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            RoundedCornerShape(unit * 2f),
            Modifier
                .width(unit * 60f)
                .graphicsLayer {
                    val s = 0.94f + 0.06f * progress().coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Column(Modifier.padding(unit * 2f), verticalArrangement = Arrangement.spacedBy(unit * 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 1f)) {
                    Canvas(Modifier.size(unit * 2.6f)) { drawMiiIcon(MiiIcon.Remote, colors.focus, weight = 2.4f) }
                    Label("Remote Buttons", unit * 2.2f, colors.ink, FontWeight.ExtraBold)
                }
                for ((i, button) in RemoteButtons.buttons.withIndex()) {
                    RowFrame(i == focused) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            val name = when {
                                RemoteButtons.learning == i -> "Press the button now…"
                                button.code == 0 -> "Press OK, then a button"
                                else -> RemoteButtons.nameOf(button.code)
                            }
                            Label(name, unit * 1.6f, if (RemoteButtons.learning == i) colors.focus else colors.ink, FontWeight.Bold)
                            if (button.code != 0) Label("‹  ${button.target.title}  ›", unit * 1.4f, colors.inkSoft, FontWeight.Medium)
                        }
                    }
                }
                val hint = if (HomeButtonService.on) {
                    "OK, then press a button like TV+ to teach it. ← → to pick what it opens, Back to close"
                } else {
                    "Turn on the HOME Menu first (Settings › HOME Menu): it's what listens to the remote"
                }
                Label(hint, unit * 1.1f, colors.inkSoft, FontWeight.Medium, Modifier.fillMaxWidth())
            }
        }
    }
}
