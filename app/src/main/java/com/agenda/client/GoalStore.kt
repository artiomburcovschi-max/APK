package com.agenda.client

import android.content.Context

/**
 * Цели по предметам (целевой средний балл). Хранятся только на телефоне, в goals.xml.
 * Ключ = «семестр|предмет», как и у раскрытых карточек в SubjectAdapter.
 * Значение хранится строкой, чтобы 8.5 не превратилось в 8.5000001 при переводе через Float.
 * «Сменить аккаунт» стирает цели вместе с остальными данными (clearAll).
 */
class GoalStore(context: Context) {
    private val prefs = context.getSharedPreferences("goals", Context.MODE_PRIVATE)

    fun get(semester: String, subject: String): Double? =
        prefs.getString(key(semester, subject), null)?.toDoubleOrNull()

    fun set(semester: String, subject: String, target: Double) {
        prefs.edit().putString(key(semester, subject), target.toString()).apply()
    }

    fun clear(semester: String, subject: String) {
        prefs.edit().remove(key(semester, subject)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun key(semester: String, subject: String) = "$semester|$subject"
}
