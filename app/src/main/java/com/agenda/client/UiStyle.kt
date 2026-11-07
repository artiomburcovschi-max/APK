package com.agenda.client

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Мелкие приёмы оформления, общие для экранов. */
object UiStyle {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** Текст цветом [color] на мягкой подложке того же цвета («таблетка»): так оценки и статусы читаются в обеих темах. */
    fun tintPill(view: TextView, color: Int) {
        val context = view.context
        view.setTextColor(color)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(context, 12).toFloat()
        bg.setColor(ColorUtils.setAlphaComponent(color, 40))
        view.background = bg
        view.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
    }
}
