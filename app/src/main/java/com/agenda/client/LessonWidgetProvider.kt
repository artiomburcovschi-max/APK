package com.agenda.client

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Виджет «Урок сейчас»: идёт урок или перемена, сколько осталось, что дальше.
 *
 * Отсчёт рисует сам Chronometer в режиме обратного отсчёта: он тикает без обновлений виджета и без расхода батареи.
 * Перерисовка нужна только на границах (конец урока, конец перемены, полночь): её ставим будильником AlarmManager.
 * Будильник неточный (setAndAllowWhileIdle, без разрешения SCHEDULE_EXACT_ALARM) и не будит телефон: если телефон
 * спал, виджет поправится в момент пробуждения. Страховка на случай потерянного будильника: updatePeriodMillis.
 */
class LessonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        LessonWidget.refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // APPWIDGET_UPDATE и прочее служебное разберёт базовый класс
        when (intent.action) {
            LessonWidget.ACTION_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED ->
                LessonWidget.refreshAll(context)
        }
    }

    override fun onDisabled(context: Context) {
        LessonWidget.cancelAlarm(context) // последний виджет убрали с экрана: будильник больше не нужен
    }
}

object LessonWidget {
    const val ACTION_TICK = "com.agenda.client.LESSON_WIDGET_TICK"

    /** Какой раздел открыть в приложении по нажатию на виджет (читает MainActivity). */
    const val EXTRA_OPEN = "open_section"
    const val OPEN_SCHEDULE = "schedule"

    private const val TAG = "LessonWidget"

    /** Перерисовать все виджеты и поставить будильник на следующую границу. Безопасно звать откуда угодно. */
    fun refreshAll(context: Context) {
        try {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, LessonWidgetProvider::class.java))
            if (ids.isEmpty()) {
                cancelAlarm(app)
                return
            }
            // Данные того ребёнка, который выбран в приложении
            val lessons = PortalExtrasStore(app, ChildStore(app).profileKey()).load().lessons
            val now = System.currentTimeMillis()
            val plan = WidgetPlanner.plan(lessons, now, TimeZone.getDefault())

            manager.updateAppWidget(ids, build(app, plan))
            scheduleAt(app, plan.refreshAtMs)
        } catch (e: Exception) {
            // Виджет не должен ронять приложение или обновление данных
            Log.e(TAG, "Не удалось обновить виджет: ${e.message}")
        }
    }

    private fun build(context: Context, plan: WidgetPlan): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_lesson)
        views.setTextViewText(R.id.wTitle, plan.title)

        val countdown = plan.countdownMs
        if (countdown == null) {
            views.setViewVisibility(R.id.wCountdown, View.GONE)
        } else {
            views.setViewVisibility(R.id.wCountdown, View.VISIBLE)
            views.setChronometerCountDown(R.id.wCountdown, true)
            // base: момент (по часам elapsedRealtime), когда отсчёт дойдёт до нуля
            views.setChronometer(R.id.wCountdown, SystemClock.elapsedRealtime() + countdown, null, true)
            val colorRes = if (plan.tone == WidgetTone.FREE) R.color.grade_excellent else R.color.wyg_primary
            views.setTextColor(R.id.wCountdown, ContextCompat.getColor(context, colorRes))
        }

        views.setTextViewText(R.id.wCaption, plan.caption)
        views.setViewVisibility(R.id.wCaption, if (plan.caption.isEmpty()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.wNext, plan.nextLine.orEmpty())
        views.setViewVisibility(R.id.wNext, if (plan.nextLine == null) View.GONE else View.VISIBLE)

        // Нажатие открывает приложение сразу на вкладке «Расписание»
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_SCHEDULE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetRoot, tap)
        return views
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, LessonWidgetProvider::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleAt(context: Context, atMs: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // RTC без WAKEUP: телефон ради виджета не будим. Просроченный будильник сработает сразу после пробуждения.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, atMs, tickIntent(context))
    }

    fun cancelAlarm(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(tickIntent(context))
    }
}
