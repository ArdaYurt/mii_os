package com.miios.launcher.tv

import android.view.SurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.miios.launcher.ui.GlassSurface
import com.miios.launcher.ui.Label
import com.miios.launcher.ui.LocalMiiColors
import com.miios.launcher.ui.LocalUnit
import com.miios.launcher.ui.Motion
import com.miios.launcher.ui.Scenery
import com.miios.launcher.ui.WiiColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TvMode { Loading, Setup, Watching }

/** Rows on the setup screen, top to bottom. */
enum class SetupRow { Playlist, Guide, Watch }

/** Everything the TV screen shows. The activity writes it; the composables only read it. */
class TvUiState {
    var mode by mutableStateOf(TvMode.Loading)
    var loadingText by mutableStateOf("Loading your channels…")
    var channels by mutableStateOf<List<TvChannel>>(emptyList())
    var guide by mutableStateOf(Guide.Empty)
    var current by mutableIntStateOf(-1)
    var bannerVisible by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var buffering by mutableStateOf(false)
    var aspect by mutableFloatStateOf(16f / 9f)
    var digits by mutableStateOf("")

    var listOpen by mutableStateOf(false)
    var groups by mutableStateOf(listOf(ALL_CHANNELS))
    var group by mutableIntStateOf(0)
    /** Channel indices in the selected group. */
    var rows by mutableStateOf<List<Int>>(emptyList())
    /** Focused list row: 0 is "Change playlist", then one row per entry of [rows]. */
    var listFocus by mutableIntStateOf(1)

    var setupFocus by mutableStateOf(SetupRow.Playlist)
    var playlistUrl by mutableStateOf("")
    var guideUrl by mutableStateOf("")
    var setupMessage by mutableStateOf<String?>(null)
    var phoneAddress by mutableStateOf<String?>(null)

    companion object {
        const val ALL_CHANNELS = "All channels"
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private fun hhmm(ms: Long) = clock.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
fun TvScreen(ui: TvUiState, onSurface: (SurfaceView) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val unit = maxWidth / 100f
        CompositionLocalProvider(LocalMiiColors provides WiiColors, LocalUnit provides unit) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000)
                    now = System.currentTimeMillis()
                }
            }

            // The video stays composed in every mode so its surface is never torn down and rebuilt.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(factory = { SurfaceView(it).also(onSurface) }, modifier = Modifier.aspectRatio(ui.aspect))
            }

            if (ui.mode == TvMode.Watching) {
                if (ui.buffering && ui.status == null) Spinner(Modifier.align(Alignment.Center).size(unit * 5f))
                Banner(ui, now)
                if (ui.digits.isNotEmpty()) DigitEntry(ui.digits, Modifier.align(Alignment.TopEnd).padding(unit * 3f))
                ChannelList(ui, now)
            }
            if (ui.mode == TvMode.Setup) SetupScreen(ui)
            if (ui.mode == TvMode.Loading) LoadingScreen(ui.loadingText)

            // Wii-style channel start: the launcher washes to white, and the TV fades in out of it.
            val wash = remember { Animatable(1f) }
            LaunchedEffect(Unit) { wash.animateTo(0f, tween(450, easing = Motion.emphasized)) }
            if (wash.value > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = wash.value }.background(Color.White))
        }
    }
}

/** Number, name and what's on, sliding up from the bottom whenever the channel changes. */
@Composable
private fun Banner(ui: TvUiState, now: Long) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val shown by animateFloatAsState(if ((ui.bannerVisible || ui.status != null) && !ui.listOpen) 1f else 0f, Motion.preview, label = "banner")
    if (shown <= 0.01f) return
    val channel = ui.channels.getOrNull(ui.current) ?: return
    val (on, next) = ui.guide.nowAndNext(channel, now)
    Box(Modifier.fillMaxSize()) {
        GlassSurface(
            RoundedCornerShape(unit * 2.4f),
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = unit * 6f, end = unit * 6f, bottom = unit * 3f)
                .fillMaxWidth()
                .height(unit * 10f)
                .graphicsLayer {
                    alpha = shown
                    translationY = (1f - shown) * unit.toPx() * 6f
                },
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = unit * 2.4f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(unit * 2f),
            ) {
                Box(Modifier.size(unit * 6f).background(colors.focus, CircleShape), contentAlignment = Alignment.Center) {
                    Label(channel.number.toString(), unit * 2.2f, Color.White, FontWeight.ExtraBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(unit * 0.3f)) {
                    TvText(channel.name, unit * 2.2f, colors.ink, FontWeight.Bold)
                    val status = ui.status
                    when {
                        status != null -> TvText(status, unit * 1.3f, Color(0xFFD9534F), FontWeight.SemiBold)
                        on != null -> {
                            TvText("${hhmm(on.start)}–${hhmm(on.stop)}  ${on.title}", unit * 1.3f, colors.ink, FontWeight.Medium)
                            ProgressBar(((now - on.start).toFloat() / (on.stop - on.start)).coerceIn(0f, 1f), Modifier.fillMaxWidth(0.6f).height(unit * 0.4f))
                        }
                        channel.group.isNotEmpty() -> TvText(channel.group, unit * 1.3f, colors.inkSoft, FontWeight.Medium)
                    }
                    if (status == null && next != null) TvText("Next  ${hhmm(next.start)}  ${next.title}", unit * 1.1f, colors.inkSoft, FontWeight.Medium)
                }
                Label(hhmm(now), unit * 1.8f, colors.inkSoft, FontWeight.Bold)
            }
        }
    }
}

/** The channel list: a glass panel that slides in from the left over the picture. */
@Composable
private fun ChannelList(ui: TvUiState, now: Long) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val shown by animateFloatAsState(if (ui.listOpen) 1f else 0f, Motion.preview, label = "list")
    if (shown <= 0.01f) return
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (ui.listFocus - 4).coerceAtLeast(0))
    // The focused row settles about a third of the way down, so the list scrolls under a steady focus.
    LaunchedEffect(ui.listFocus, ui.group) { state.animateScrollToItem((ui.listFocus - 4).coerceAtLeast(0)) }

    GlassSurface(
        RoundedCornerShape(topEnd = unit * 2.4f, bottomEnd = unit * 2.4f),
        Modifier
            .fillMaxHeight()
            .width(unit * 36f)
            .graphicsLayer {
                alpha = shown
                translationX = (shown - 1f) * unit.toPx() * 36f
            },
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = unit * 2.4f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = unit * 2f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("‹", unit * 2.2f, colors.inkSoft, FontWeight.Bold)
                TvText(ui.groups.getOrElse(ui.group) { "" }, unit * 1.8f, colors.ink, FontWeight.ExtraBold, Modifier.weight(1f).padding(horizontal = unit * 1f), center = true)
                Label("›", unit * 2.2f, colors.inkSoft, FontWeight.Bold)
            }
            Box(Modifier.padding(horizontal = unit * 2f, vertical = unit * 1.2f).fillMaxWidth().height(unit * 0.2f).background(colors.focus))
            LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(ui.rows.size + 1) { row ->
                    val focused = row == ui.listFocus
                    if (row == 0) {
                        ListRow(null, "Change playlist", null, focused, playing = false)
                    } else {
                        val i = ui.rows[row - 1]
                        val channel = ui.channels[i]
                        ListRow(channel.number.toString(), channel.name, ui.guide.nowAndNext(channel, now).first?.title, focused, playing = i == ui.current)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListRow(number: String?, name: String, detail: String?, focused: Boolean, playing: Boolean) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val glow by animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "row")
    val ink = if (focused) Color.White else colors.ink
    Box(
        Modifier
            .fillMaxWidth()
            .height(unit * 4.6f)
            .padding(horizontal = unit * 1.2f, vertical = unit * 0.25f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (glow > 0f) drawRoundRect(colors.focus.copy(alpha = glow), cornerRadius = CornerRadius(unit.toPx() * 1.4f))
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = unit * 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(unit * 1.2f),
        ) {
            TvText(number ?: "⚙", unit * 1.4f, if (focused) Color.White else colors.inkSoft, FontWeight.Bold, Modifier.width(unit * 3.6f))
            Column(Modifier.weight(1f)) {
                TvText(name, unit * 1.5f, ink, if (playing) FontWeight.ExtraBold else FontWeight.SemiBold)
                if (detail != null) TvText(detail, unit * 1.05f, if (focused) Color.White.copy(alpha = 0.85f) else colors.inkSoft, FontWeight.Medium)
            }
            if (playing) Box(Modifier.size(unit * 0.8f).background(if (focused) Color.White else colors.focus, CircleShape))
        }
    }
}

@Composable
private fun DigitEntry(digits: String, modifier: Modifier) {
    val unit = LocalUnit.current
    GlassSurface(RoundedCornerShape(unit * 2f), modifier.size(unit * 14f, unit * 7f)) {
        Label(digits, unit * 4f, LocalMiiColors.current.ink, FontWeight.ExtraBold, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun SetupScreen(ui: TvUiState) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(Modifier.fillMaxSize()) {
        Scenery(colors)
        Column(
            Modifier.fillMaxSize().padding(horizontal = unit * 14f, vertical = unit * 4f),
            verticalArrangement = Arrangement.spacedBy(unit * 1.6f),
        ) {
            Label("TV", unit * 3.2f, colors.ink, FontWeight.ExtraBold)
            Label("Add the playlist address from your IPTV provider.", unit * 1.3f, colors.inkSoft, FontWeight.Medium)
            Box(Modifier.fillMaxWidth().height(unit * 0.2f).background(colors.focus))
            SetupButton("Playlist address (M3U)", ui.playlistUrl.ifEmpty { "Press OK to type it" }, ui.setupFocus == SetupRow.Playlist, Modifier.weight(1f))
            SetupButton("Guide address (XMLTV, optional)", ui.guideUrl.ifEmpty { "Uses the playlist's own guide if it has one" }, ui.setupFocus == SetupRow.Guide, Modifier.weight(1f))
            SetupButton("Watch TV", if (ui.playlistUrl.isEmpty()) "Add a playlist first" else "Load the channels", ui.setupFocus == SetupRow.Watch, Modifier.weight(1f))
            val phone = ui.phoneAddress
            val message = ui.setupMessage
            when {
                message != null -> TvText(message, unit * 1.25f, Color(0xFFD9534F), FontWeight.SemiBold, center = true)
                phone != null -> TvText("Easier: on your phone, open $phone and paste it there.", unit * 1.25f, colors.ink, FontWeight.SemiBold, center = true)
                else -> TvText("Back returns to the channels", unit * 1.1f, colors.inkSoft, FontWeight.Medium, center = true)
            }
        }
    }
}

@Composable
private fun SetupButton(title: String, detail: String, focused: Boolean, modifier: Modifier) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, Motion.focusScale, label = "setupScale")
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.glow, label = "setupRing")
    val radius = unit * 2.4f
    Box(modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }) {
        GlassSurface(RoundedCornerShape(radius), Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = unit * 3f)) {
                TvText(title, unit * 1.8f, colors.ink, FontWeight.Bold)
                TvText(detail, unit * 1.15f, colors.inkSoft, FontWeight.Medium, Modifier.padding(top = unit * 0.3f))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val on = ring > 0f
            drawRoundRect(
                if (on) colors.focus.copy(alpha = ring) else colors.tileBorder,
                cornerRadius = CornerRadius(radius.toPx()),
                style = Stroke(unit.toPx() * if (on) 0.3f else 0.12f),
            )
        }
    }
}

@Composable
private fun LoadingScreen(text: String) {
    val unit = LocalUnit.current
    val colors = LocalMiiColors.current
    Box(Modifier.fillMaxSize()) {
        Scenery(colors)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(unit * 2f)) {
            Spinner(Modifier.size(unit * 5f))
            Label(text, unit * 1.6f, colors.ink, FontWeight.SemiBold)
        }
    }
}

/** Only composed while something is loading, so an idle TV screen draws nothing extra. */
@Composable
private fun Spinner(modifier: Modifier) {
    val colors = LocalMiiColors.current
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinAngle",
    )
    Canvas(modifier.graphicsLayer { rotationZ = turn }) {
        val w = size.minDimension * 0.12f
        drawArc(colors.focus, 0f, 270f, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier) {
    val colors = LocalMiiColors.current
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(colors.tileBorder, cornerRadius = r)
        drawRoundRect(colors.focus, size = Size(size.width * fraction, size.height), cornerRadius = r)
    }
}

/** Single-line text that ends in "…" when it doesn't fit; channel and programme names are often long. */
@Composable
private fun TvText(text: String, size: Dp, color: Color, weight: FontWeight, modifier: Modifier = Modifier, center: Boolean = false) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    BasicText(
        text,
        if (center) modifier.fillMaxWidth() else modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = weight,
            textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
