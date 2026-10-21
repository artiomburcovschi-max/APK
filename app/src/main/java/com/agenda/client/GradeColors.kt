package com.agenda.client

import android.graphics.Color

object GradeColors {
    /** Цвет по оценке или среднему баллу. */
    fun forScore(score: Double): Int = when {
        score >= 9 -> Color.parseColor("#2E7D32") // зелёный
        score >= 7 -> Color.parseColor("#F9A825") // жёлтый
        score >= 5 -> Color.parseColor("#EF6C00") // оранжевый
        else -> Color.parseColor("#C62828")       // красный
    }
}
