package com.agenda.client

import java.util.Locale

enum class TrendDirection { UP, DOWN, FLAT, NONE }

/** recentAverage = средний последних оценок, earlierAverage = средний всех, что были до них. */
data class TrendInfo(val direction: TrendDirection, val recentAverage: Double, val earlierAverage: Double)

/**
 * Тренд предмета (чистый Kotlin): последние оценки лучше или хуже тех, что были раньше?
 * Сравниваем средний последних RECENT_COUNT оценок со средним всех предыдущих.
 * Оценки идут по порядку: слева старые, справа новые (так же хранятся в базе).
 * Меньше MIN_GRADES оценок: тренда нет (две-три оценки ничего не показывают).
 */
object Trend {
    const val RECENT_COUNT = 3

    /** Нужно минимум 3 последние + 2 прежние оценки. */
    const val MIN_GRADES = 5

    /** На сколько баллов должны отличаться средние, чтобы считать это ростом или падением. */
    const val DELTA = 0.5

    private const val EPS = 1e-9

    fun of(scores: List<Int>): TrendInfo {
        if (scores.size < MIN_GRADES) return TrendInfo(TrendDirection.NONE, 0.0, 0.0)
        val recent = scores.takeLast(RECENT_COUNT).average()
        val earlier = scores.dropLast(RECENT_COUNT).average()
        val diff = recent - earlier
        val direction = when {
            diff + EPS >= DELTA -> TrendDirection.UP
            diff - EPS <= -DELTA -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
        return TrendInfo(direction, recent, earlier)
    }

    /** Стрелка для списка: только рост и падение, «без изменений» не загромождает экран. */
    fun arrow(direction: TrendDirection): String = when (direction) {
        TrendDirection.UP -> "↑"
        TrendDirection.DOWN -> "↓"
        else -> ""
    }

    /** Пояснение для раскрытой карточки; null, если тренда нет. */
    fun describe(info: TrendInfo): String? {
        if (info.direction == TrendDirection.NONE) return null
        val verdict = when (info.direction) {
            TrendDirection.UP -> "растёт"
            TrendDirection.DOWN -> "падает"
            else -> "без заметных изменений"
        }
        return String.format(
            Locale.US, "Тренд: последние %d оценки в среднем %.2f, до них %.2f (%s)",
            RECENT_COUNT, info.recentAverage, info.earlierAverage, verdict
        )
    }
}
