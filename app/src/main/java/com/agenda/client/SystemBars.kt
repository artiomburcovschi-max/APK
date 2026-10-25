package com.agenda.client

import android.app.Activity
import androidx.core.view.WindowCompat

object SystemBars {
    /**
     * Значки статус-бара и панели навигации: на светлом фоне тёмные, на тёмном светлые.
     * Вызывать после setContentView; при смене темы Activity пересоздаётся и вызов повторяется сам.
     */
    fun matchTheme(activity: Activity) {
        val light = !ThemeSettings.isNight(activity)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }
}
