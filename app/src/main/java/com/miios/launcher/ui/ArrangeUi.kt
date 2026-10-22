package com.miios.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.miios.launcher.data.Slot

/** A choice in the options menu that a long press on OK opens. */
class MenuOption(val label: String, val icon: MiiIcon, val run: () -> Unit)

/** The options menu: a small glass card with one row per choice, over a dimmed screen. */
@Composable
fun OptionsMenu(title: String, options: List<MenuOption>, focused: Int, progress: () -> Float) {
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
                .width(unit * 34f)
                .graphicsLayer {
                    val s = 0.94f + 0.06f * progress().coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Column(Modifier.padding(unit * 1.6f), verticalArrangement = Arrangement.spacedBy(unit * 0.8f)) {
                Label(title, unit * 1.6f, colors.inkSoft, FontWeight.Bold, Modifier.fillMaxWidth().padding(bottom = unit * 0.4f))
                for ((i, option) in options.withIndex()) OptionRow(option, i == focused)
            }
        }
    }
}

@Composable
private fun OptionRow(option: MenuOption, focused: Boolean) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "optionRing")
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, Motion.focusScale, label = "optionScale")
    Box(
        Modifier
            .fillMaxWidth()
            .height(unit * 4.6f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(colors.tileFrame, RoundedCornerShape(unit * 1.4f)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = unit * 1.4f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(unit * 1f),
        ) {
            Canvas(Modifier.width(unit * 2f).fillMaxHeight(0.5f)) { drawMiiIcon(option.icon, colors.focus, weight = 2.4f) }
            Label(option.label, unit * 1.5f, colors.ink, FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                if (ring > 0f) colors.focus.copy(alpha = ring) else colors.tileBorder,
                cornerRadius = CornerRadius(unit.toPx() * 1.4f),
                style = Stroke(unit.toPx() * if (ring > 0f) 0.28f else 0.1f),
            )
        }
    }
}

/**
 * Naming a folder: a text field the TV's on-screen keyboard types into. Done saves, Back cancels.
 */
@Composable
fun RenameDialog(initial: String, onDone: (String) -> Unit) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val fontSize = with(LocalDensity.current) { (unit * 2.4f).toSp() }
    Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.TopCenter) {
        GlassSurface(RoundedCornerShape(unit * 2f), Modifier.padding(top = unit * 6f).width(unit * 50f)) {
            Column(Modifier.padding(unit * 2f), verticalArrangement = Arrangement.spacedBy(unit * 1.2f)) {
                Label("Folder name", unit * 1.6f, colors.inkSoft, FontWeight.Bold)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it.copy(text = it.text.take(24)) },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = MiiFont.family),
                    cursorBrush = SolidColor(colors.focus),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone(value.text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.tileFrame, RoundedCornerShape(unit * 1.4f))
                        .padding(unit * 1.2f)
                        .focusRequester(focus),
                )
                Label("Type with the keyboard, then press Done. Back cancels.", unit * 1.1f, colors.inkSoft, FontWeight.Medium)
            }
        }
    }
}

/** An open folder: its channels on a glass sheet over the home screen, four to a row. */
@Composable
fun FolderView(
    folder: Slot.Folder,
    focused: Int,
    leanX: Int,
    leanY: Int,
    moveId: Int,
    onFocusedBounds: (Rect) -> Unit,
    progress: () -> Float,
) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .background(Color(0x40000000)),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            RoundedCornerShape(unit * 2.4f),
            Modifier
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.8f)
                .graphicsLayer {
                    val s = 0.92f + 0.08f * progress().coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Column(Modifier.fillMaxSize().padding(unit * 2.4f), verticalArrangement = Arrangement.spacedBy(unit * 1.6f)) {
                Label(folder.name, unit * 2.4f, colors.ink, FontWeight.ExtraBold, Modifier.fillMaxWidth())
                val rows = maxOf(1, (folder.apps.size + FOLDER_COLS - 1) / FOLDER_COLS)
                val firstRow = (focused / FOLDER_COLS - 2).coerceIn(0, maxOf(0, rows - 3))
                // always three rows tall, so tiles keep the home screen's proportions
                for (r in firstRow until firstRow + 3) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(unit * 1.8f)) {
                        for (c in 0 until FOLDER_COLS) {
                            val i = r * FOLDER_COLS + c
                            val app = folder.apps.getOrNull(i)
                            if (app == null) {
                                Box(Modifier.weight(1f))
                            } else {
                                ChannelTile(
                                    slot = Slot.App(app),
                                    focused = i == focused,
                                    leanX = leanX,
                                    leanY = leanY,
                                    moveId = moveId,
                                    onFocusedBounds = onFocusedBounds,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
                if (folder.apps.isEmpty()) {
                    Label("This folder is empty. Hold OK on a channel to add it.", unit * 1.4f, colors.inkSoft, FontWeight.Medium, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

const val FOLDER_COLS = 4
