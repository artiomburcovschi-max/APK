package com.agenda.client

import android.app.Application


class AgendaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeSettings.apply(ThemeSettings.load(this))
    }
}
