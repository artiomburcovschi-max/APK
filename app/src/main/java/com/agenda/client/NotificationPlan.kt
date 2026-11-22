package com.agenda.client

import java.util.TimeZone

/**
 * Что показывать в уведомлении «Урок сейчас» и когда пересчитать в следующий раз.
 *
 * @property show false: уведомления быть не должно (ещё рано, уроки закончились, выходной, пользователь его смахнул)
 * @property title заголовок: «Урок 3 · Matematică» или «Перемена»
 * @property text пояснение: «до конца урока · Дальше: урок 4 · …»
 * @property countdownEndMs момент (epoch мс), когда отсчёт дойдёт до нуля; для setWhen у уведомления
 * @property refreshAtMs когда пересчитать план (граница урока или перемены и т.п., плюс запас)
 */
data class NotificationPlan(
    val show: Boolean,
    val title: String,
    val text: String,
    val countdownEndMs: Long?,
    val refreshAtMs: Long
)

/**
 * Чистая логика уведомления (без Android, проверяется JUnit). Уведомление живёт только в учебное время:
 * с [LEAD_BEFORE_FIRST_MS] до первого урока и до конца последнего. Остальное время его нет, чтобы не висеть весь день.
 */
object NotificationPlanner {

    /** За сколько до первого урока уведомление появляется. */
    const val LEAD_BEFORE_FIRST_MS = 30 * 60_000L

    /**
     * @param dismissedDay день («yyyy-MM-dd»), когда пользователь смахнул уведомление: в этот день его больше не показываем.
     *   Пустая строка: не смахивали.
     */
    fun plan(lessons: List<Lesson>, nowMs: Long, zone: TimeZone, dismissedDay: String = ""): NotificationPlan {
        val margin = WidgetPlanner.REFRESH_MARGIN_MS
        val state = LessonClock.stateAt(lessons, nowMs, zone)

        // Смахнули сегодня: молчим до полуночи (nextBoundary для «пустых» состояний это ближайшая полночь)
        if (dismissedDay.isNotEmpty() && dismissedDay == LessonClock.isoOf(nowMs, zone)) {
            return hidden(LessonClock.nextBoundary(ClockState.NoData, nowMs, zone) + margin)
        }

        val info = LessonClock.info(state, nowMs, zone)
        val text = listOf(info.caption, info.nextLine.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")

        return when (state) {
            is ClockState.NoData, is ClockState.DayOff, is ClockState.Finished ->
                hidden(LessonClock.nextBoundary(state, nowMs, zone) + margin)

            is ClockState.BeforeFirst ->
                if (state.first.startMs - nowMs > LEAD_BEFORE_FIRST_MS) {
                    hidden(state.first.startMs - LEAD_BEFORE_FIRST_MS + margin) // рано: проснёмся, когда подойдёт время
                } else {
                    NotificationPlan(true, info.title, text, state.first.startMs, state.first.startMs + margin)
                }

            is ClockState.InLesson ->
                NotificationPlan(true, info.title, text, state.current.endMs, state.current.endMs + margin)

            is ClockState.Break ->
                NotificationPlan(true, info.title, text, state.next.startMs, state.next.startMs + margin)
        }
    }

    private fun hidden(refreshAtMs: Long) = NotificationPlan(false, "", "", null, refreshAtMs)
}
