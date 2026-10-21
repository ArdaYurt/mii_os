package com.miios.launcher.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The HOME Menu: pressing Home inside an app dims it, a white bar drops in from the top and two
 * buttons rise from the bottom, the way the Wii's HOME Menu does. It's an accessibility overlay,
 * so it sits above any app (including video) without taking focus away from it; the service
 * passes it the remote's keys.
 */
class HomeMenu(private val context: Context) {

    enum class Choice { Home, Close }

    private val windows = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var buttons: List<TextView> = emptyList()
    var choice = Choice.Home
        private set

    val showing: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val metrics = context.resources.displayMetrics
        val u = metrics.widthPixels / 100f
        choice = Choice.Home

        val bar = TextView(context).apply {
            text = "HOME Menu"
            setTextColor(INK)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u * 2.2f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.WHITE, Color.rgb(0xED, 0xEF, 0xF1)),
            )
        }
        val home = button("Mii_OS Menu", u)
        val close = button("Close", u)
        buttons = listOf(home, close)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (u * 2.4f).toInt(), 0, (u * 2.4f).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(0xED, 0xEF, 0xF1), Color.WHITE),
            )
            addView(home, LinearLayout.LayoutParams((u * 20f).toInt(), (u * 5.6f).toInt()).apply { marginEnd = (u * 2f).toInt() })
            addView(close, LinearLayout.LayoutParams((u * 20f).toInt(), (u * 5.6f).toInt()).apply { marginStart = (u * 2f).toInt() })
        }
        val hint = TextView(context).apply {
            text = "OK or Home: go to Mii_OS     Back: return to the app"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u * 1.2f)
            gravity = Gravity.CENTER
            setShadowLayer(u * 0.3f, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        val layout = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(150, 10, 20, 30))
            addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (u * 6.5f).toInt(), Gravity.TOP))
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        refresh()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windows.addView(layout, params)
        root = layout

        // the dim fades in while the bars slide in from the edges, settling with a little bounce
        layout.alpha = 0f
        layout.animate().alpha(1f).setDuration(160).start()
        bar.translationY = -u * 7f
        bar.animate().translationY(0f).setDuration(320).setInterpolator(OvershootInterpolator(1.2f)).start()
        row.translationY = u * 11f
        row.animate().translationY(0f).setDuration(340).setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    fun move(dx: Int) {
        val next = if (dx < 0) Choice.Home else Choice.Close
        if (next == choice) return
        choice = next
        refresh()
    }

    fun hide(onHidden: () -> Unit = {}) {
        val layout = root ?: return onHidden()
        root = null
        layout.animate().alpha(0f).setDuration(140).setInterpolator(DecelerateInterpolator()).withEndAction {
            runCatching { windows.removeView(layout) }
            onHidden()
        }.start()
    }

    private fun refresh() {
        for ((i, b) in buttons.withIndex()) {
            val focused = i == choice.ordinal
            (b.background as GradientDrawable).setStroke(
                if (focused) (b.textSize * 0.16f).toInt() else (b.textSize * 0.06f).toInt(),
                if (focused) FOCUS else BORDER,
            )
            b.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f)
                .setDuration(200).setInterpolator(OvershootInterpolator(2f)).start()
        }
    }

    private fun button(label: String, u: Float) = TextView(context).apply {
        text = label
        setTextColor(INK)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u * 1.8f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = u * 2.8f
            setColor(Color.WHITE)
        }
        elevation = u * 0.4f
        visibility = View.VISIBLE
    }

    private companion object {
        val INK = Color.rgb(0x3A, 0x3F, 0x45)
        val FOCUS = Color.rgb(0x34, 0xBE, 0xED)
        val BORDER = Color.rgb(0xC9, 0xCE, 0xD3)
    }
}
