package com.agenda.client

enum class GoalStatus {
    NO_DATA,        // по предмету ещё нет оценок: среднего нет, считать нечего
    INVALID_TARGET, // цель вне диапазона 1..10
    ACHIEVED,       // средний уже не ниже цели (options = «запас»)
    REACHABLE,      // цель достижима (options = что нужно получить)
    UNREACHABLE     // цель 10: средний никогда не дойдёт до 10, пока есть оценки ниже десятки
}

enum class GoalDifficulty { EASY, HARD, VERY_HARD }

/** «count штук оценки grade». */
data class GradeCount(val grade: Int, val count: Int)

data class GoalPlan(
    val status: GoalStatus,
    val target: Double,
    val average: Double,
    val gradesCount: Int,
    val options: List<GradeCount> = emptyList(),
    val difficulty: GoalDifficulty? = null
)

/**
 * Калькулятор цели по предмету (чистый Kotlin, без Android).
 *
 * Средний по предмету = сумма / количество, без округления: так считает приложение, а портал
 * итоговую оценку не отдаёт. Оценка за сессию (теза) пока не читается и здесь не учитывается.
 *
 * Все сравнения делаются в ЦЕЛЫХ числах: цель переводится в сотые доли (8.5 -> 850), и условие
 * «(сумма + k*g) / (n + k) >= цель» превращается в
 *     100*(сумма + k*g) >= цель100 * (n + k)
 * Так нет ошибок двоичной дроби: 8.6 * 5 в Double может дать 42.99999999999999 и «потерять» цель.
 */
object GoalPlanner {

    const val MIN_GRADE = 1
    const val MAX_GRADE = 10

    /** Сколько новых оценок «десятка к десятке» ещё можно считать реальным: до 3 включительно. */
    const val EASY_MAX = 3

    /** Серия до 7 оценок: тяжело, но возможно. Дальше цель «почти нереальна». Пороги условные. */
    const val HARD_MAX = 7

    /** "8,5" и "8.5" -> 8.5; пусто и мусор -> null. */
    fun parseTarget(text: CharSequence?): Double? {
        val value = text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return if (value.isFinite()) value else null
    }

    fun difficultyOf(count: Int): GoalDifficulty = when {
        count <= EASY_MAX -> GoalDifficulty.EASY
        count <= HARD_MAX -> GoalDifficulty.HARD
        else -> GoalDifficulty.VERY_HARD
    }

    fun plan(scores: List<Int>, target: Double): GoalPlan {
        val n = scores.size
        val sum = scores.sum()
        val average = if (n == 0) 0.0 else sum.toDouble() / n

        if (n == 0) return GoalPlan(GoalStatus.NO_DATA, target, average, n)
        if (!target.isFinite()) return GoalPlan(GoalStatus.INVALID_TARGET, target, average, n)

        val t100 = Math.round(target * 100)
        if (t100 < MIN_GRADE * 100L || t100 > MAX_GRADE * 100L) {
            return GoalPlan(GoalStatus.INVALID_TARGET, target, average, n)
        }

        val sum100 = sum * 100L

        // Цель уже достигнута: считаем «запас» (сколько оценок g ниже цели можно ещё получить)
        if (sum100 >= t100 * n) {
            val reserve = mutableListOf<GradeCount>()
            for (g in MAX_GRADE downTo MIN_GRADE) {
                val drop = t100 - g * 100L // насколько g ниже цели (в сотых)
                if (drop <= 0) continue
                val k = ((sum100 - t100 * n) / drop).toInt() // целое деление = округление вниз
                if (k >= 1) reserve.add(GradeCount(g, k))
            }
            return GoalPlan(GoalStatus.ACHIEVED, target, average, n, reserve)
        }

        // Цель выше текущего среднего: для каждой оценки g > цели считаем минимальное k
        val need = t100 * n - sum100 // сколько «сотых» не хватает суммарно (> 0)
        val options = mutableListOf<GradeCount>()
        for (g in MAX_GRADE downTo MIN_GRADE) {
            val gain = g * 100L - t100 // на сколько каждая такая оценка поднимает сумму относительно цели
            if (gain <= 0) continue // оценка не выше цели: средний к цели не приблизится
            options.add(GradeCount(g, ceilDiv(need, gain).toInt()))
        }

        if (options.isEmpty()) return GoalPlan(GoalStatus.UNREACHABLE, target, average, n)

        // у большей оценки k меньше, значит лучший вариант всегда первый (самая высокая g)
        return GoalPlan(
            GoalStatus.REACHABLE, target, average, n, options, difficultyOf(options.first().count)
        )
    }

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b // a >= 0, b > 0
}
