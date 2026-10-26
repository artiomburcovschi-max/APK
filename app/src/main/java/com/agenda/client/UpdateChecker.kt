package com.agenda.client

import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/** Разбирает latest.json. Если что-то не так, вернёт null, а не упадёт. */
object UpdateInfoParser {
    fun parse(json: String): UpdateInfo? = try {
        val o = JSONObject(UpdatePolicy.cleanJson(json))
        val code = o.getInt("versionCode")
        val name = o.getString("versionName")
        val url = o.getString("apkUrl")
        if (code <= 0 || name.isBlank() || url.isBlank()) null
        else UpdateInfo(code, name, url, o.optString("notes", ""), o.optString("sha256", ""))
    } catch (e: Exception) {
        null
    }
}

/**
 * Проверка обновлений. Скачивает latest.json и запоминает его; баннер строится из сохранённого,
 * поэтому работает и без сети. Сама установка остаётся за системой: мы только открываем ссылку на APK.
 *
 * Автоматически: при каждом выходе приложения на экран, но не чаще чем раз в 6 часов после успеха
 * и не чаще чем раз в 10 минут после сбоя (см. UpdatePolicy.shouldCheck).
 * Вручную: checkNow() (кнопка в «О приложении») спрашивает сервер всегда и объясняет результат.
 */
class UpdateChecker(context: Context, private val client: OkHttpClient) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("update_state", Context.MODE_PRIVATE)
    private val tag = "AGENDA_DEBUG"

    private val enabled: Boolean
        get() = UpdateConfig.UPDATE_URL.startsWith("https://") &&
            UpdateConfig.TRUSTED_APK_PREFIX.startsWith("https://")

    @Suppress("DEPRECATION") // versionCode работает на всех версиях Android
    private fun currentVersionCode(): Int = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode
    } catch (e: Exception) {
        0
    }

    private fun currentVersionName(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private val dismissedVersionCode: Int
        get() = prefs.getInt("dismissed", 0)

    /** Обновление, про которое надо напомнить (по сохранённым данным, без сети). */
    fun pendingUpdate(): UpdateInfo? {
        if (!enabled) return null
        val json = prefs.getString("last_json", null) ?: return null
        val info = UpdateInfoParser.parse(json) ?: return null
        val show = UpdatePolicy.shouldShow(
            currentVersionCode(), info, dismissedVersionCode, UpdateConfig.TRUSTED_APK_PREFIX
        )
        return if (show) info else null
    }

    /** "Не сейчас": больше не напоминаем про эту версию (про следующую напомним). */
    fun dismiss(info: UpdateInfo) {
        prefs.edit().putInt("dismissed", info.versionCode).apply()
    }

    /** Человек сам нажал «Проверить»: значит, отложенное «не сейчас» больше не действует. */
    fun clearDismissed() {
        prefs.edit().remove("dismissed").apply()
    }

    /** Что установлено и что в последний раз видели на сервере (для экрана «О приложении»). */
    fun status(): UpdateStatus {
        val server = prefs.getString("last_json", null)?.let { UpdateInfoParser.parse(it) }
        return UpdateStatus(
            installedName = currentVersionName(),
            installedCode = currentVersionCode(),
            lastSuccessAt = prefs.getLong("last_success_at", 0L),
            serverName = server?.versionName,
            serverCode = server?.versionCode
        )
    }

    /** Автоматическая проверка, если пора. onDone вызывается из фонового потока и только при успешной проверке. */
    fun refreshIfDue(onDone: () -> Unit) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val due = UpdatePolicy.shouldCheck(
            prefs.getLong("last_success_at", 0L), prefs.getLong("last_attempt_at", 0L), now
        )
        if (!due) return
        fetch(now) { result -> if (result !is UpdateCheckResult.Failed) onDone() }
    }

    /** Ручная проверка: спрашивает сервер сразу и всегда отвечает итогом. onResult из фонового потока. */
    fun checkNow(onResult: (UpdateCheckResult) -> Unit) {
        if (!enabled) {
            onResult(UpdateCheckResult.Failed("проверка обновлений отключена в настройках сборки"))
            return
        }
        fetch(System.currentTimeMillis(), onResult)
    }

    private fun fetch(now: Long, onResult: (UpdateCheckResult) -> Unit) {
        // Время ПОПЫТКИ фиксируем до запроса (чтобы не слать запросы подряд), а время УСПЕХА только после ответа
        prefs.edit().putLong("last_attempt_at", now).apply()

        val request = try {
            // Параметр t = текущее время: каждый запрос для кэшей уникален, и CDN не отдаёт вчерашний файл
            // (GitHub Pages кэширует файлы минут на десять). В запросе нет ничего о пользователе, только время.
            val url = UpdateConfig.UPDATE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("t", now.toString()).build()
            Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        } catch (e: Exception) {
            onResult(UpdateCheckResult.Failed("неверный адрес проверки обновлений"))
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(tag, "Проверка обновлений не удалась: ${e.message}")
                onResult(UpdateCheckResult.Failed(UpdatePolicy.networkFailureText(e)))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body
                    val text = if (body == null || body.contentLength() > MAX_JSON_BYTES) null else body.string()
                    val info = if (text == null || text.length > MAX_JSON_BYTES) null else UpdateInfoParser.parse(text)
                    val result = UpdatePolicy.interpret(it.code, info, currentVersionCode(), UpdateConfig.TRUSTED_APK_PREFIX)

                    // Сохраняем и считаем проверку успешной, если сервер отдал нормальный latest.json
                    // (даже когда ссылка не прошла проверку доверия: так экран покажет настоящую причину)
                    if (it.isSuccessful && text != null && info != null) {
                        prefs.edit()
                            .putString("last_json", text)
                            .putLong("last_success_at", now)
                            .apply()
                    }
                    Log.d(tag, "Проверка обновлений: $result")
                    onResult(result)
                }
            }
        })
    }

    companion object {
        private const val MAX_JSON_BYTES = 64 * 1024L // latest.json крошечный, большие ответы игнорируем
    }
}
