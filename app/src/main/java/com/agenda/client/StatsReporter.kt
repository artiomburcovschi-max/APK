package com.agenda.client

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Настройки анонимной статистики. Пока PING_URL пустой, статистика полностью выключена:
 * ничего не отправляется, окно согласия и переключатель в «Настройках» не показываются.
 */
object StatsConfig {
    /** Адрес Worker'а вида "https://agenda-stats.ИМЯ.workers.dev/ping". Подставить после развёртывания сервера. */
    const val PING_URL = "https://agenda-stats.watchyourgrade.workers.dev/ping"

    /** Из отладочных сборок сигнал не шлём, чтобы твои запуски не искажали цифры. Для проверки можно поставить true. */
    const val SEND_FROM_DEBUG = false

    val enabled: Boolean get() = PING_URL.startsWith("https://")
}

/** Хранилище состояния статистики: согласие и даты последних отправок. Идентификаторов здесь нет и не будет. */
class StatsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun load(): StatsState {
        val consent = try {
            StatsConsent.valueOf(prefs.getString("consent", StatsConsent.UNSET.name) ?: StatsConsent.UNSET.name)
        } catch (e: IllegalArgumentException) {
            StatsConsent.UNSET
        }
        return StatsState(
            consent = consent,
            lastDay = prefs.getString("last_day", "").orEmpty(),
            lastWeek = prefs.getString("last_week", "").orEmpty(),
            lastMonth = prefs.getString("last_month", "").orEmpty(),
            firstSent = prefs.getBoolean("first_sent", false),
            lastAttemptMs = prefs.getLong("last_attempt", 0L)
        )
    }

    fun save(state: StatsState) {
        prefs.edit()
            .putString("consent", state.consent.name)
            .putString("last_day", state.lastDay)
            .putString("last_week", state.lastWeek)
            .putString("last_month", state.lastMonth)
            .putBoolean("first_sent", state.firstSent)
            .putLong("last_attempt", state.lastAttemptMs)
            .apply()
    }
}

/**
 * Отправка анонимного сигнала «приложение открыли». Что именно уходит, описано в StatsPing:
 * версия приложения и три отметки. Без куки, без заголовков про пользователя, без идентификатора.
 */
class StatsReporter private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val store = StatsStore(appContext)
    // Отдельный клиент: без общих куки и перехватчиков остальной сети приложения
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    private fun isDebuggable() = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun versionName(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** Отправляет сигнал, если пора (согласие есть, сегодня ещё не отправляли, недавней неудачной попытки нет). */
    fun pingIfDue() {
        if (!StatsConfig.enabled) return
        if (isDebuggable() && !StatsConfig.SEND_FROM_DEBUG) return
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()
        val state = store.load()
        val ping = StatsPolicy.nextPing(state, versionName(), now, zone) ?: return

        // Время попытки запоминаем до запроса: два быстрых запуска подряд не отправят сигнал дважды
        store.save(state.copy(lastAttemptMs = now))

        val request = try {
            Request.Builder()
                .url(StatsConfig.PING_URL)
                .post(ping.toJson().toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: Exception) {
            Log.d(TAG, "Неверный адрес статистики: ${e.message}")
            return
        }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Сигнал статистики не отправлен: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        // Успех: запоминаем, что за сегодня, неделю и месяц уже сообщили
                        store.save(StatsPolicy.afterSuccess(store.load(), now, zone))
                    } else {
                        Log.d(TAG, "Сервер статистики ответил ${it.code}")
                    }
                }
            }
        })
    }

    companion object {
        private const val TAG = "AGENDA_STATS"

        @Volatile private var instance: StatsReporter? = null

        fun get(context: Context): StatsReporter =
            instance ?: synchronized(this) { instance ?: StatsReporter(context).also { instance = it } }
    }
}

/**
 * Следит за главным экраном: если пользователь вошёл (MainActivity на экране), то отправляет сигнал, а при первом
 * запуске один раз спрашивает согласие. Подключается в AgendaApp, поэтому MainActivity трогать не нужно.
 */
class StatsLifecycle(private val app: Application) : Application.ActivityLifecycleCallbacks {

    private var dialog: AlertDialog? = null

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity || !StatsConfig.enabled) return
        when (StatsStore(app).load().consent) {
            StatsConsent.YES -> StatsReporter.get(app).pingIfDue()
            StatsConsent.UNSET -> askConsent(activity)
            StatsConsent.NO -> Unit
        }
    }

    private fun askConsent(activity: Activity) {
        if (dialog?.isShowing == true || activity.isFinishing) return
        dialog = AlertDialog.Builder(activity)
            .setTitle("Анонимная статистика")
            .setMessage(
                "Хотите помочь улучшить приложение?\n\n" +
                    "Если согласитесь, раз в сутки приложение отправит на сервер разработчика один анонимный сигнал " +
                    "«приложение открыли»: номер версии и отметки «первый раз / новая неделя / новый месяц». " +
                    "Так я вижу, сколько людей пользуются приложением.\n\n" +
                    "Не отправляются: имя, логин, класс, школа, оценки, уроки, идентификатор устройства или аккаунта. " +
                    "Сервер не записывает IP-адрес (как любой хостинг, Cloudflare видит его в момент передачи).\n\n" +
                    "Передумать можно в любой момент: «Настройки» → «Анонимная статистика»."
            )
            .setCancelable(false)
            .setPositiveButton("Включить") { _, _ ->
                val store = StatsStore(app)
                store.save(store.load().copy(consent = StatsConsent.YES))
                StatsReporter.get(app).pingIfDue()
            }
            .setNegativeButton("Не надо") { _, _ ->
                val store = StatsStore(app)
                store.save(store.load().copy(consent = StatsConsent.NO))
            }
            .show()
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is MainActivity) {
            // Поворот экрана или выход: окно закрываем, при следующем показе MainActivity спросим снова
            dialog?.dismiss()
            dialog = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
