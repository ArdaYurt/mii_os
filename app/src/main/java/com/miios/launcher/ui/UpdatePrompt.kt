package com.miios.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight

/** Where an update stands, as the home screen shows it. */
sealed interface UpdateState {
    data object None : UpdateState
    /** [version] is the build number; [name] and [notes] are what the prompt shows. */
    data class Available(val version: Int, val name: String = "", val notes: List<String> = emptyList()) : UpdateState
}

enum class UpdateButton { Later, Update }

/**
 * The Wii's system message: the screen dims and a white panel asks one question with two buttons.
 * [progress] runs 0→1 as it opens.
 */
@Composable
fun UpdatePrompt(state: UpdateState, focused: UpdateButton, progress: () -> Float) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            RoundedCornerShape(unit * 2.4f),
            Modifier
                .size(unit * 52f, if (state is UpdateState.Available && state.notes.isNotEmpty()) unit * 31f else unit * 24f)
                .graphicsLayer {
                    val p = progress().coerceIn(0f, 1f)
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                },
        ) {
            Column(
                Modifier.fillMaxSize().padding(unit * 2.4f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 1f)) {
                    Canvas(Modifier.size(unit * 2.6f)) { drawMiiIcon(MiiIcon.Download, colors.focus, weight = 2.4f) }
                    val title = (state as? UpdateState.Available)?.name?.takeIf { it.isNotEmpty() }
                    Label(if (title != null) "Mii_OS $title" else "Mii_OS update", unit * 2.4f, colors.ink, FontWeight.ExtraBold)
                }
                Label("A new version of Mii_OS is ready. Install it now?", unit * 1.5f, colors.inkSoft, FontWeight.Medium)
                // the release's top highlights, from CHANGELOG.md
                val notes = (state as? UpdateState.Available)?.notes.orEmpty().take(4)
                if (notes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(unit * 0.4f)) {
                        for (note in notes) Label("•  $note", unit * 1.2f, colors.ink, FontWeight.Medium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(unit * 3f)) {
                    PillButton("Later", MiiIcon.Back, focused == UpdateButton.Later)
                    PillButton("Update", MiiIcon.Download, focused == UpdateButton.Update)
                }
            }
        }
    }
}
