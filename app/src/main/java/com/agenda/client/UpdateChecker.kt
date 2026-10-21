package com.agenda.client

import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/** Разбирает latest.json. Если что-то не так, вернёт null, а не упадёт. */
object UpdateInfoParser {
    fun parse(json: String): UpdateInfo? = try {
        val o = JSONObject(json)
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
 * Проверка обновлений: раз в сутки скачивает latest.json и запоминает его.
 * Баннер строится из сохранённого, поэтому работает и без сети.
 * Сама установка остаётся за системой: мы только открываем ссылку на APK.
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

    /** Если с прошлой проверки прошли сутки, спрашивает сервер. onDone вызывается из фонового потока. */
    fun refreshIfDue(onDone: () -> Unit) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (!UpdatePolicy.shouldCheck(prefs.getLong("last_check_at", 0L), now)) return
        prefs.edit().putLong("last_check_at", now).apply() // фиксируем ПОПЫТКУ до запроса

        val request = Request.Builder().url(UpdateConfig.UPDATE_URL).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(tag, "Проверка обновлений не удалась: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body
                    if (!it.isSuccessful || body == null || body.contentLength() > MAX_JSON_BYTES) return
                    val text = body.string()
                    if (text.length > MAX_JSON_BYTES) return
                    if (UpdateInfoParser.parse(text) != null) {
                        prefs.edit().putString("last_json", text).apply()
                        onDone()
                    }
                }
            }
        })
    }

    companion object {
        private const val MAX_JSON_BYTES = 64 * 1024L // latest.json крошечный, большие ответы игнорируем
    }
}
