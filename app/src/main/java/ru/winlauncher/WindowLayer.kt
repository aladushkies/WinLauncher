package ru.winlauncher

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/** Слой рабочего стола: хранит окна, z-порядок и фокус. */
class WindowLayer(ctx: Context) : FrameLayout(ctx) {
    val windows = mutableListOf<WindowView>()   // последний = верхний
    var onChanged: (() -> Unit)? = null
    private var cascade = 0

    val active: WindowView? get() = windows.lastOrNull { !it.minimized }

    fun open(title: String, emoji: String, content: View): WindowView {
        val w = WindowView(context, this, title, emoji, content)
        val ww = (width * 0.6f).toInt().coerceAtLeast(context.dp(320))
        val hh = (height * 0.72f).toInt().coerceAtLeast(context.dp(240))
        cascade = (cascade + 1) % 8
        val lp = LayoutParams(ww, hh)
        lp.leftMargin = context.dp(40) + cascade * context.dp(32)
        lp.topMargin = context.dp(20) + cascade * context.dp(28)
        addView(w, lp)
        windows.add(w)
        focus(w)
        return w
    }

    fun focus(w: WindowView) {
        if (w.minimized) { w.minimized = false; w.visibility = VISIBLE }
        windows.remove(w); windows.add(w)
        w.bringToFront()
        refresh()
    }

    fun minimize(w: WindowView) {
        w.minimized = true
        w.visibility = GONE
        refresh()
    }

    fun toggleFromTaskbar(w: WindowView) {
        if (w.minimized || active !== w) focus(w) else minimize(w)
    }

    fun close(w: WindowView) {
        removeView(w)
        windows.remove(w)
        refresh()
    }

    private fun refresh() {
        val a = active
        windows.forEach { it.setActive(it === a) }
        onChanged?.invoke()
    }
}
