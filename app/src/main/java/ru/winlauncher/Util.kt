package ru.winlauncher

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** Встроенное приложение, которое открывается в собственном окне лаунчера. */
class BuiltIn(val emoji: String, val title: String, val create: (Context) -> View)

/** Пункт меню «Пуск». */
class Entry(val label: String, val icon: Drawable?, val emoji: String?, val action: () -> Unit)
