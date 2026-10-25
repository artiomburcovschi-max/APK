package com.agenda.client

import android.app.Application

/**
 * Класс приложения: выполняется один раз при запуске процесса, раньше любой Activity.
 * Здесь применяем сохранённую тему, чтобы первый экран сразу открылся в нужных цветах (без мигания).
 */
class AgendaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeSettings.apply(ThemeSettings.load(this))
    }
}
