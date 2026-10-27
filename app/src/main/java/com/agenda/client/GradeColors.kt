package com.agenda.client

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

object GradeColors {
    /** Какой цветовой ресурс подходит оценке или среднему баллу (в тёмной теме значения светлее). */
    @ColorRes
    fun colorRes(score: Double): Int = when {
        score >= 9 -> R.color.grade_excellent // зелёный
        score >= 7 -> R.color.grade_good      // жёлтый
        score >= 5 -> R.color.grade_mid       // оранжевый
        else -> R.color.grade_bad             // красный
    }

    /** Цвет стрелки тренда: рост зелёный, падение красный. */
    fun forTrend(context: Context, direction: TrendDirection): Int = ContextCompat.getColor(
        context,
        if (direction == TrendDirection.DOWN) R.color.grade_bad else R.color.grade_excellent
    )

    /** Цвет по оценке или среднему баллу для текущей темы (поэтому нужен Context). */
    fun forScore(context: Context, score: Double): Int =
        ContextCompat.getColor(context, colorRes(score))
}
