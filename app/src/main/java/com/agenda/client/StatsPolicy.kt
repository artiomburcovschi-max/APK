package com.agenda.client

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Согласие на анонимную статистику. UNSET: ещё не спрашивали, ничего не отправляем. */
enum class StatsConsent { UNSET, YES, NO }

/** Что приложение помнит о статистике. Никаких идентификаторов: только согласие и даты последних отправок. */
data class StatsState(
    val consent: StatsConsent = StatsConsent.UNSET,
    val lastDay: String = "",
    val lastWeek: String = "",
    val lastMonth: String = "",
    val firstSent: Boolean = false,
    val lastAttemptMs: Long = 0L
)

/**
 * Один анонимный сигнал «приложение открыли сегодня». Больше в нём нет ничего:
 * версия приложения и три отметки. Дату сервер ставит сам, идентификатора нет.
 *
 * @property newWeek первый сигнал этой недели: сервер складывает такие отметки и получает недельную аудиторию
 * @property newMonth то же для месяца
 * @property first первый сигнал после включения статистики: сервер получает число новых участников
 */
data class StatsPing(val version: String, val newWeek: Boolean, val newMonth: Boolean, val first: Boolean) {

    /** JSON тела запроса. Версия очищается до безопасных символов, так что вставить в JSON нечего. */
    fun toJson(): String =
        "{\"v\":\"${StatsPolicy.cleanVersion(version)}\",\"w\":${bit(newWeek)},\"m\":${bit(newMonth)},\"f\":${bit(first)}}"

    private fun bit(b: Boolean) = if (b) 1 else 0
}

/**
 * Чистая логика статистики (без Android, проверяется JUnit): когда отправлять сигнал и что запомнить после успеха.
 *
 * Идея без идентификатора: каждое устройство шлёт сигнал не чаще раза в сутки. Число сигналов за день равно числу
 * устройств, открывавших приложение (DAU). Сигнал с отметкой «новая неделя» уходит раз в неделю, поэтому сумма
 * отметок за неделю равна недельной аудитории (WAU), а за месяц то же для MAU. Различать устройства серверу
 * нечем, и следить за конкретным человеком нельзя.
 */
object StatsPolicy {

    /** После неудачной попытки (нет сети, сервер недоступен) следующая не раньше чем через полчаса. */
    const val RETRY_INTERVAL_MS = 30 * 60 * 1000L

    /** Пора ли отправлять сигнал. null означает «ничего не отправляем». */
    fun nextPing(state: StatsState, version: String, nowMs: Long, zone: TimeZone): StatsPing? {
        if (state.consent != StatsConsent.YES) return null
        if (state.lastDay == dayKey(nowMs, zone)) return null // сегодня уже сообщали
        val sinceAttempt = nowMs - state.lastAttemptMs
        if (sinceAttempt in 0 until RETRY_INTERVAL_MS) return null // недавно пробовали
        return StatsPing(
            version = version,
            newWeek = state.lastWeek != weekKey(nowMs, zone),
            newMonth = state.lastMonth != monthKey(nowMs, zone),
            first = !state.firstSent
        )
    }

    /** Состояние после УСПЕШНОЙ отправки: запоминаем, что за сегодня, эту неделю и этот месяц уже сообщили. */
    fun afterSuccess(state: StatsState, nowMs: Long, zone: TimeZone): StatsState = state.copy(
        lastDay = dayKey(nowMs, zone),
        lastWeek = weekKey(nowMs, zone),
        lastMonth = monthKey(nowMs, zone),
        firstSent = true
    )

    /** «2026-11-19» по местному времени. */
    fun dayKey(nowMs: Long, zone: TimeZone): String {
        val c = calendar(nowMs, zone)
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Понедельник недели, в которую попадает момент: «2026-11-16». Неделя начинается с понедельника. */
    fun weekKey(nowMs: Long, zone: TimeZone): String {
        val c = calendar(nowMs, zone)
        val sinceMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // у Java воскресенье = 1
        c.add(Calendar.DAY_OF_MONTH, -sinceMonday)
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** «2026-11». */
    fun monthKey(nowMs: Long, zone: TimeZone): String {
        val c = calendar(nowMs, zone)
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    /** Версия приложения только из безопасных символов, не длиннее 20. Иначе «?». */
    fun cleanVersion(version: String): String {
        val v = version.trim()
        return if (v.isNotEmpty() && v.length <= 20 && v.all { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '-' }) v else "?"
    }

    private fun calendar(nowMs: Long, zone: TimeZone): GregorianCalendar {
        val c = GregorianCalendar(zone)
        c.timeInMillis = nowMs
        return c
    }
}
