package com.agenda.client

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/** Тема оформления: как в системе / светлая / тёмная. key хранится на телефоне, менять строки нельзя. */
enum class ThemeMode(val key: String, val label: String, val nightMode: Int) {
    SYSTEM("system", "Как в системе", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", "Светлая", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", "Тёмная", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        /** Нет значения или неизвестное -> «как в системе». */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Выбор темы. Хранится в settings.xml (SharedPreferences), применяется через AppCompatDelegate.
 * Сам AppCompatDelegate выбор между запусками НЕ помнит, поэтому AgendaApp применяет его при старте.
 */
object ThemeSettings {
    private const val PREFS = "settings"
    private const val KEY_THEME = "theme"

    fun load(context: Context): ThemeMode =
        ThemeMode.fromKey(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        )

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.key).apply()
    }

    /** Меняет тему на лету: AppCompat сам пересоздаёт открытые Activity. */
    fun apply(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /** Сейчас у этого контекста тёмная конфигурация? (учитывает и выбор в приложении, и системную тему) */
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
