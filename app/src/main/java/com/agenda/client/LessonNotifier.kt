package com.agenda.client

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Уведомление «Урок сейчас»: идёт урок или перемена, сколько осталось, что дальше. Видно в шторке и на экране блокировки,
 * открывать приложение не нужно. Включается в «Настройках», по умолчанию выключено.
 *
 * Отсчёт тикает сам (Chronometer в уведомлении), перерисовка только на границах урока. Её ставит неточный будильник
 * (setAndAllowWhileIdle, RTC_WAKEUP, без разрешения на точные будильники), поэтому на границе возможна задержка.
 * Смахнули уведомление: оно возвращается на ближайшей границе урока (раньше пропадало на весь день).
 * Что показывать и когда пересчитать, решает NotificationPlanner (чистая логика под тестами).
 */
object LessonNotifier {
    const val ACTION_TICK = "com.agenda.client.LESSON_NOTIFY_TICK"
    const val ACTION_DISMISSED = "com.agenda.client.LESSON_NOTIFY_DISMISSED"

    private const val CHANNEL_ID = "lesson_now"
    private const val NOTIFICATION_ID = 4107
    private const val PREFS = "lesson_notify"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DISMISSED_UNTIL = "dismissed_until_ms"
    private const val LEGACY_KEY_DISMISSED_DAY = "dismissed_day" // прежняя схема «скрыто до полуночи»: теперь только стираем
    private const val KEY_PROMO_HIDDEN = "promo_hidden"
    private const val TAG = "LessonNotifier"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Включить или выключить. «Смахнули» сбрасывается: человек сам попросил показывать. */
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on)
            .remove(KEY_DISMISSED_UNTIL).remove(LEGACY_KEY_DISMISSED_DAY).apply()
        refresh(context)
    }

    /** Карточка-напоминание на экране «Расписание» скрыта кнопкой «Не напоминать». */
    fun isPromoHidden(context: Context): Boolean = prefs(context).getBoolean(KEY_PROMO_HIDDEN, false)

    fun hidePromo(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMO_HIDDEN, true).apply()
    }

    /** Можно ли показывать уведомления: на Android 13+ нужно разрешение, раньше достаточно общего выключателя. */
    fun canPost(context: Context): Boolean {
        val app = context.applicationContext
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(app).areNotificationsEnabled()
        }
    }

    /**
     * Уведомление смахнули (или нажали «Очистить всё»): прячем до ближайшей границы урока, потом оно возвращается само.
     * Раньше прятали до конца дня, а вернуть могла только выключка и включка переключателя. Система присылает это событие
     * и при случайных жестах, поэтому наказывать за него целым днём нельзя.
     */
    fun markDismissed(context: Context) {
        val app = context.applicationContext
        try {
            val lessons = PortalExtrasStore(app, ChildStore(app).profileKey()).load().lessons
            val until = NotificationPlanner.dismissUntil(lessons, System.currentTimeMillis(), TimeZone.getDefault())
            if (until != null) prefs(app).edit().putLong(KEY_DISMISSED_UNTIL, until).apply()
            Log.i(TAG, "Смахнули: скрыто до границы урока=${until != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запомнить «смахнули»: ${e.message}")
        }
        refresh(app)
    }

    /** Показать, обновить или убрать уведомление и поставить будильник на следующую границу. Безопасно звать откуда угодно. */
    @SuppressLint("MissingPermission") // разрешение проверяет canPost() строкой выше
    fun refresh(context: Context) {
        val app = context.applicationContext
        try {
            if (!isEnabled(app) || !canPost(app)) {
                cancelAll(app)
                return
            }
            // Уроки того ребёнка, который выбран в приложении
            val lessons = PortalExtrasStore(app, ChildStore(app).profileKey()).load().lessons
            val now = System.currentTimeMillis()
            val sp = prefs(app)
            if (sp.contains(LEGACY_KEY_DISMISSED_DAY)) {
                // отметка прежней версии «скрыто до полуночи» могла застрять: стираем, уведомление вернётся
                sp.edit().remove(LEGACY_KEY_DISMISSED_DAY).apply()
            }
            val dismissedUntil = sp.getLong(KEY_DISMISSED_UNTIL, 0L)
            val plan = NotificationPlanner.plan(lessons, now, TimeZone.getDefault(), dismissedUntil)
            // Для разбора «пропало»: без названий уроков, только числа и причина
            val today = LessonClock.isoOf(now, TimeZone.getDefault())
            Log.i(
                TAG,
                "refresh: показать=${plan.show}, уроков всего=${lessons.size}, сегодня=${lessons.count { it.date == today }}, " +
                    "скрыто до=${if (dismissedUntil > now) dismissedUntil - now else 0} мс, следующий пересчёт через ${plan.refreshAtMs - now} мс"
            )

            val manager = NotificationManagerCompat.from(app)
            if (plan.show) {
                ensureChannel(app)
                manager.notify(NOTIFICATION_ID, build(app, plan))
            } else {
                manager.cancel(NOTIFICATION_ID)
            }
            scheduleAt(app, plan.refreshAtMs)
        } catch (e: Exception) {
            // Уведомление не должно ронять приложение или обновление данных
            Log.e(TAG, "Не удалось обновить уведомление: ${e.message}")
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Урок сейчас", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Какой сейчас урок или перемена и сколько осталось"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel) // повторное создание безопасно: настройки пользователя не сбрасываются
    }

    private fun build(context: Context, plan: NotificationPlan): Notification {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(LessonWidget.EXTRA_OPEN, LessonWidget.OPEN_SCHEDULE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, 2, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismissIntent = Intent(context, LessonNotifyReceiver::class.java).setAction(ACTION_DISMISSED)
        val onDismiss = PendingIntent.getBroadcast(context, 3, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lesson)
            .setContentTitle(plan.title)
            .setContentText(plan.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(plan.text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tap)
            .setDeleteIntent(onDismiss)

        val end = plan.countdownEndMs
        if (end != null) {
            // when в будущем + режим обратного отсчёта: система сама показывает «осталось mm:ss» и сама тикает
            builder.setShowWhen(true).setWhen(end).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16+: просим «живое обновление» (плашка в строке состояния, заметнее на экране блокировки).
            // Ключ из платформы Android (EXTRA_REQUEST_PROMOTED_ONGOING); система вправе отказать, тогда это обычное уведомление
            builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        }
        return builder.build()
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, LessonNotifyReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(context, 4, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleAt(context: Context, atMs: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // RTC_WAKEUP: будильник сработает и при спящем телефоне. Раньше стоял RTC без WAKEUP: пока телефон спал,
        // уведомление показывало прошлый урок или перемену. Пара срабатываний в день батарею не чувствует
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, tickIntent(context))
    }

    /** Убрать уведомление и будильник: выключили в настройках или пропало разрешение. */
    fun cancelAll(context: Context) {
        val app = context.applicationContext
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tickIntent(app))
    }
}

/**
 * Принимает будильник границы урока, перезагрузку телефона, обновление приложения, смену времени, пояса и даты
 * (будильники после перезагрузки и обновления пропадают, поэтому их ставим заново) и «смахнули уведомление».
 */
class LessonNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("LessonNotifier", "событие: ${intent.action}")
        if (intent.action == LessonNotifier.ACTION_DISMISSED) {
            LessonNotifier.markDismissed(context)
        } else {
            LessonNotifier.refresh(context)
        }
    }
}
