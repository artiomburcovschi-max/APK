package com.agenda.client

import android.app.Application


class AgendaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeSettings.apply(ThemeSettings.load(this))
        // Анонимная статистика: сама ничего не делает, пока в StatsConfig не указан адрес сервера
        registerActivityLifecycleCallbacks(StatsLifecycle(this))
    }
}
