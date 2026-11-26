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

    /** Дольше суток «смахнули» не действует: так сломанные часы или устаревшая отметка не спрячут уведомление надолго. */
    const val MAX_DISMISS_MS = 24 * 3600_000L

    /**
     * @param dismissedUntilMs до какого момента (epoch мс) уведомление скрыто, потому что его смахнули. 0: не смахивали.
     *   Это ближайшая граница урока, а не конец дня: случайный свайп или «Очистить всё» не должны убирать уведомление
     *   на весь день (раньше было так, и вернуть его мог только переключатель в настройках).
     */
    fun plan(lessons: List<Lesson>, nowMs: Long, zone: TimeZone, dismissedUntilMs: Long = 0L): NotificationPlan {
        val margin = WidgetPlanner.REFRESH_MARGIN_MS
        val state = LessonClock.stateAt(lessons, nowMs, zone)

        // Смахнули: молчим до ближайшей границы урока, потом уведомление возвращается само
        if (dismissedUntilMs > nowMs && dismissedUntilMs - nowMs <= MAX_DISMISS_MS) {
            return hidden(dismissedUntilMs)
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

    /**
     * До какого момента прятать уведомление после «смахнули» сейчас: до ближайшей границы (конец урока или перемены).
     * null: сейчас уведомления и так нет, прятать нечего (событие «смахнули» пришло зря).
     */
    fun dismissUntil(lessons: List<Lesson>, nowMs: Long, zone: TimeZone): Long? {
        val current = plan(lessons, nowMs, zone)
        return if (current.show) current.refreshAtMs else null
    }

    private fun hidden(refreshAtMs: Long) = NotificationPlan(false, "", "", null, refreshAtMs)
}

/** Когда на экране «Расписание» показывать карточку «такое уведомление есть» (чистая логика, под тестами). */
object NotifyPromptPolicy {
    /**
     * @param enabled переключатель в приложении включён
     * @param canPost система разрешает показывать уведомления
     * @param hidden пользователь нажал «Не напоминать»
     */
    fun shouldShow(enabled: Boolean, canPost: Boolean, hidden: Boolean): Boolean =
        !hidden && !(enabled && canPost)
}
