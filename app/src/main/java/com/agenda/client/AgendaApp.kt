package com.agenda.client

import android.app.Activity
import android.app.Application
import android.os.Bundle


class AgendaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeSettings.apply(ThemeSettings.load(this))
        // Анонимная статистика: сама ничего не делает, пока в StatsConfig не указан адрес сервера
        registerActivityLifecycleCallbacks(StatsLifecycle(this))
        // Каждый выход приложения на экран заново показывает уведомление «Урок сейчас» и ставит будильник:
        // если цепочка будильников оборвалась (оболочка телефона убила приложение), она чинится открытием приложения
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                LessonNotifier.refresh(this@AgendaApp)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
