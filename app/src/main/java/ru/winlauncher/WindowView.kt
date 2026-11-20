package ru.winlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/** Окно: заголовок (иконка, название, свернуть/развернуть/закрыть), содержимое, ручка ресайза. */
class WindowView(
    ctx: Context,
    private val layer: WindowLayer,
    val title: String,
    val emoji: String,
    content: View
) : FrameLayout(ctx) {

    var minimized = false
    var maximized = false
    private var saved: Rect? = null
    private val bar = LinearLayout(ctx)
    private lateinit var maxBtn: TextView

    private var dx = 0f
    private var dy = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean { toggleMax(); return true }
    })

    private fun lp() = layoutParams as LayoutParams

    private fun btn(t: String, onClick: () -> Unit) = TextView(context).apply {
        text = t; setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(context.dp(46), MATCH_PARENT)
        setOnClickListener { onClick() }
    }

    init {
        background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(ctx.dp(1), 0xFF0078D7.toInt()) }
        elevation = ctx.dp(10).toFloat()

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val icon = TextView(ctx).apply { text = emoji; textSize = 16f; setPadding(ctx.dp(10), 0, ctx.dp(6), 0) }
        val t = TextView(ctx).apply {
            text = title; setTextColor(Color.WHITE); textSize = 14f; maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
        }
        bar.addView(icon); bar.addView(t)
        bar.addView(btn("—") { layer.minimize(this) })
        maxBtn = btn("▢") { toggleMax() }
        bar.addView(maxBtn)
        bar.addView(btn("✕") { layer.close(this) })

        col.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, ctx.dp(36)))
        col.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        addView(col, LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // ручка ресайза в правом нижнем углу
        val handle = TextView(ctx).apply { text = "◢"; setTextColor(0xFF888888.toInt()); gravity = Gravity.END or Gravity.BOTTOM }
        addView(handle, LayoutParams(ctx.dp(30), ctx.dp(30), Gravity.END or Gravity.BOTTOM))
        var sx = 0f; var sy = 0f; var sw = 0; var sh = 0
        handle.setOnTouchListener { _, e ->
            if (maximized) return@setOnTouchListener true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; sw = lp().width; sh = lp().height; layer.focus(this) }
                MotionEvent.ACTION_MOVE -> {
                    val p = lp()
                    p.width = (sw + e.rawX - sx).toInt().coerceIn(context.dp(240), max(context.dp(240), layer.width - p.leftMargin))
                    p.height = (sh + e.rawY - sy).toInt().coerceIn(context.dp(160), max(context.dp(160), layer.height - p.topMargin))
                    layoutParams = p
                }
            }
            true
        }

        // перетаскивание за заголовок
        bar.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            val loc = IntArray(2); layer.getLocationOnScreen(loc)
            val x = e.rawX - loc[0]; val y = e.rawY - loc[1]
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    layer.focus(this)
                    downRawX = e.rawX; downRawY = e.rawY; dragging = false
                    dx = x - lp().leftMargin; dy = y - lp().topMargin
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && (abs(e.rawX - downRawX) > context.dp(8) || abs(e.rawY - downRawY) > context.dp(8))) {
                        dragging = true
                        if (maximized) {
                            restore()
                            val p = lp()
                            p.leftMargin = (x - p.width / 2f).toInt().coerceIn(0, max(0, layer.width - p.width))
                            p.topMargin = 0
                            layoutParams = p
                            dx = x - p.leftMargin; dy = context.dp(18).toFloat()
                        }
                    }
                    if (dragging) {
                        val p = lp()
                        p.leftMargin = (x - dx).toInt().coerceIn(-(p.width - context.dp(80)), layer.width - context.dp(80))
                        p.topMargin = (y - dy).toInt().coerceIn(0, max(0, layer.height - context.dp(36)))
                        layoutParams = p
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        when {
                            y < context.dp(6) -> maximize()
                            x < context.dp(10) -> snap(true)
                            x > layer.width - context.dp(10) -> snap(false)
                        }
                    }
                    dragging = false
                }
            }
            true
        }
        setActive(true)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && layer.active !== this) layer.focus(this)
        return false
    }

    fun setActive(a: Boolean) {
        bar.setBackgroundColor(if (a) 0xFF0078D7.toInt() else 0xFF6B6B6B.toInt())
    }

    private fun bounds(): Rect { val p = lp(); return Rect(p.leftMargin, p.topMargin, p.leftMargin + p.width, p.topMargin + p.height) }

    private fun setBounds(r: Rect) {
        val p = lp()
        p.leftMargin = r.left; p.topMargin = r.top; p.width = r.width(); p.height = r.height()
        layoutParams = p
    }

    fun maximize() {
        if (!maximized) saved = bounds()
        setBounds(Rect(0, 0, layer.width, layer.height))
        maximized = true; maxBtn.text = "❐"
    }

    fun snap(left: Boolean) {
        if (!maximized) saved = bounds()
        val half = layer.width / 2
        setBounds(if (left) Rect(0, 0, half, layer.height) else Rect(half, 0, layer.width, layer.height))
        maximized = true; maxBtn.text = "❐"
    }

    fun restore() {
        saved?.let { setBounds(it) }
        maximized = false; maxBtn.text = "▢"
    }

    fun toggleMax() { if (maximized) restore() else maximize() }
}
