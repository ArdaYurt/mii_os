package com.miios.launcher.service

import android.content.Context
import android.graphics.Bitmap
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The HOME Menu: pressing Home inside an app softly blurs and dims it, white bars slide in at the
 * top and bottom and two big buttons pop up in the middle, the way the Wii's HOME Menu does. It's an accessibility overlay,
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

    private var backdrop: ImageView? = null

    fun show() {
        if (root != null) return
        val metrics = context.resources.displayMetrics
        val u = metrics.widthPixels / 100f
        choice = Choice.Home
        val font = com.miios.launcher.ui.MiiFont.typeface

        val bar = TextView(context).apply {
            text = "HOME Menu"
            setTextColor(INK)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u * 2.2f)
            typeface = Typeface.create(font ?: Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.WHITE, Color.rgb(0xED, 0xEF, 0xF1)),
            )
        }
        val home = button("Mii_OS Menu", u, font)
        val close = button("Close", u, font)
        buttons = listOf(home, close)
        // the two big buttons sit in the middle of the screen, as on the Wii
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            addView(home, LinearLayout.LayoutParams((u * 24f).toInt(), (u * 7.5f).toInt()).apply { marginEnd = (u * 2.5f).toInt() })
            addView(close, LinearLayout.LayoutParams((u * 24f).toInt(), (u * 7.5f).toInt()).apply { marginStart = (u * 2.5f).toInt() })
        }
        val foot = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(0xED, 0xEF, 0xF1), Color.WHITE),
            )
        }
        // the app underneath, softly blurred (filled in once the service's screenshot arrives)
        val blurred = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            alpha = 0f
        }
        backdrop = blurred
        val dim = View(context).apply { setBackgroundColor(Color.argb(90, 0, 0, 0)) }
        val layout = FrameLayout(context).apply {
            clipChildren = false
            addView(blurred, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(dim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (u * 6.5f).toInt(), Gravity.TOP))
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (u * 12f).toInt(), Gravity.CENTER))
            addView(foot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (u * 8f).toInt(), Gravity.BOTTOM))
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

        // the dim fades in while the bars slide in from the edges and the buttons pop up, settling with a little bounce
        layout.alpha = 0f
        layout.animate().alpha(1f).setDuration(160).start()
        bar.translationY = -u * 7f
        bar.animate().translationY(0f).setDuration(320).setInterpolator(OvershootInterpolator(1.2f)).start()
        foot.translationY = u * 9f
        foot.animate().translationY(0f).setDuration(320).setInterpolator(OvershootInterpolator(1.2f)).start()
        row.scaleX = 0.85f
        row.scaleY = 0.85f
        row.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    /** Puts the blurred picture of the app behind the menu. Video apps may give a black picture; then it stays dimmed only. */
    fun setBackdrop(picture: Bitmap) {
        val view = backdrop ?: return
        if (root == null) return
        view.setImageBitmap(picture)
        view.animate().alpha(1f).setDuration(180).start()
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
        backdrop = null
        layout.animate().alpha(0f).setDuration(140).setInterpolator(DecelerateInterpolator()).withEndAction {
            runCatching { windows.removeView(layout) }
            onHidden()
        }.start()
    }

    private fun refresh() {
        for ((i, b) in buttons.withIndex()) {
            val focused = i == choice.ordinal
            // a clear grey edge always, and a thick Wii-blue one when chosen
            (b.background as GradientDrawable).setStroke(
                if (focused) (b.textSize * 0.22f).toInt() else (b.textSize * 0.1f).toInt(),
                if (focused) FOCUS else BORDER,
            )
            b.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f)
                .setDuration(200).setInterpolator(OvershootInterpolator(2f)).start()
        }
    }

    private fun button(label: String, u: Float, font: Typeface?) = TextView(context).apply {
        text = label
        setTextColor(INK)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u * 2f)
        typeface = Typeface.create(font ?: Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.WHITE, Color.rgb(0xEE, 0xF1, 0xF4)),
        ).apply { cornerRadius = u * 3.75f }
    }

    private companion object {
        val INK = Color.rgb(0x3A, 0x3F, 0x45)
        val FOCUS = Color.rgb(0x34, 0xBE, 0xED)
        val BORDER = Color.rgb(0xC9, 0xCE, 0xD3)
    }
}
