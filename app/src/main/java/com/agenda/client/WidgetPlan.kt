package com.agenda.client

import java.util.TimeZone

/** Какого цвета обратный отсчёт: синий на уроке, зелёный на перемене и до первого урока, нейтральный в остальное время. */
enum class WidgetTone { LESSON, FREE, IDLE }

/**
 * Всё, что нужно нарисовать на виджете, и когда его перерисовать в следующий раз.
 * Чистый Kotlin без Android: проверяется JUnit. Рисует по этому плану LessonWidget.
 */
data class WidgetPlan(
    val title: String,
    val countdownMs: Long?,
    val caption: String,
    val nextLine: String?,
    val tone: WidgetTone,
    val refreshAtMs: Long
)

object WidgetPlanner {

    /**
     * Запас после границы урока. Будильник Android неточный и может сработать на секунду раньше:
     * тогда состояние ещё прежнее, и без запаса виджет крутился бы, перепланируя себя каждую секунду.
     */
    const val REFRESH_MARGIN_MS = 1_500L

    fun plan(lessons: List<Lesson>, nowMs: Long, zone: TimeZone): WidgetPlan {
        val state = LessonClock.stateAt(lessons, nowMs, zone)
        val info = LessonClock.info(state, nowMs, zone)
        val tone = when (state) {
            is ClockState.InLesson -> WidgetTone.LESSON
            is ClockState.Break, is ClockState.BeforeFirst -> WidgetTone.FREE
            else -> WidgetTone.IDLE
        }
        return WidgetPlan(
            title = info.title,
            countdownMs = info.countdownMs,
            caption = info.caption,
            nextLine = info.nextLine,
            tone = tone,
            refreshAtMs = LessonClock.nextBoundary(state, nowMs, zone) + REFRESH_MARGIN_MS
        )
    }
}
