package com.agenda.client

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Кэш последнего удачного результата: список предметов (вместе с датами оценок) для мгновенного
 * показа при открытии приложения, плюс время последней успешной загрузки и последней попытки.
 */
class SnapshotCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Время последней ПОПЫТКИ зайти на портал (для RefreshPolicy). */
    var lastAttemptAt: Long
        get() = prefs.getLong("attempt_at", 0L)
        set(value) { prefs.edit().putLong("attempt_at", value).apply() }

    /** Время последней УСПЕШНОЙ загрузки (для подписи "Данные от ..."). */
    val lastSuccessAt: Long
        get() = prefs.getLong("success_at", 0L)

    /** Стирает кэш и отметки времени (при смене аккаунта). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun save(items: List<SubjectGrades>, now: Long) {
        val array = JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("subject", item.subject)
                    .put("scores", JSONArray(item.scores))
                    .put("semester", item.semester)
                    .put("media", item.media)
                    .put("dates", JSONArray(item.dates.map { it ?: "" })) // "" = дата неизвестна
            )
        }
        // одной записью: время и данные не рассинхронизируются
        prefs.edit().putString("data", array.toString()).putLong("success_at", now).apply()
    }

    fun load(): List<SubjectGrades> {
        val raw = prefs.getString("data", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val scoresJson = obj.getJSONArray("scores")
                val datesJson = obj.optJSONArray("dates") // в старых кэшах поля нет
                SubjectGrades(
                    subject = obj.getString("subject"),
                    scores = (0 until scoresJson.length()).map { scoresJson.getInt(it) },
                    semester = obj.optString("semester", ""),
                    media = obj.optString("media", ""),
                    dates = if (datesJson == null) emptyList()
                    else (0 until datesJson.length()).map { datesJson.optString(it, "").ifEmpty { null } }
                )
            }
        } catch (e: Exception) {
            emptyList() // повреждённый кэш лучше пустой, чем падение приложения
        }
    }

    companion object {
        const val PREFS_NAME = "snapshot"   // файл snapshot.xml (его же исключаем из бэкапа)
    }
}
