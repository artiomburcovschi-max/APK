package com.agenda.client

/** Оценки одного предмета за выбранный период (семестр или весь год). */
data class SubjectScores(val name: String, val scores: List<Int>)

/** Откуда берём оценки для цели. */
object GoalScope {

    /** Предметы одного семестра; предметы без оценок пропускаем (среднего у них нет). */
    fun forSemester(items: List<SubjectGrades>): List<SubjectScores> =
        items.filter { it.scores.isNotEmpty() }.map { SubjectScores(it.subject, it.scores) }

    /**
     * Весь год: оценки предмета из всех семестров собираются ВМЕСТЕ, и средний считается по всей куче.
     * Порядок предметов сохраняется как при первом появлении. Предполагаем, что название предмета
     * в разных семестрах пишется одинаково.
     */
    fun forYear(allItems: List<SubjectGrades>): List<SubjectScores> {
        val byName = LinkedHashMap<String, MutableList<Int>>()
        for (item in allItems) byName.getOrPut(item.subject) { mutableListOf() }.addAll(item.scores)
        return byName.filterValues { it.isNotEmpty() }.map { SubjectScores(it.key, it.value) }
    }
}

/** Сколько оценок нужно одному предмету. */
data class OverallSubjectRow(val subject: String, val average: Double, val options: List<GradeCount>)

/** «По perSubject оценок grade в каждом предмете, всего total». */
data class OverallEven(val grade: Int, val perSubject: Int, val total: Int)

data class OverallPlan(
    val status: GoalStatus,
    val target: Double,
    val overall: Double,
    val subjectsCount: Int,
    val even: List<OverallEven> = emptyList(),
    val single: List<OverallSubjectRow> = emptyList(),
    /** Предметы, которым одним общий средний до цели не поднять даже десятками. */
    val cannotAlone: List<String> = emptyList(),
    val difficulty: GoalDifficulty? = null
)

/**
 * Цель по общему среднему (чистый Kotlin). Общий = среднее из средних по предметам, как в «Обзоре»:
 * каждый предмет весит одинаково, сколько бы оценок в нём ни было.
 * Поэтому новая оценка в предмете двигает общий на (изменение среднего предмета) / (число предметов).
 *
 * Два сценария:
 *  1. «поровну»: в КАЖДОМ предмете одинаковое число одинаковых оценок (реалистичный рабочий вариант);
 *  2. «один предмет»: все новые оценки только в нём (показывает, какой предмет вытянет быстрее всего).
 *
 * Здесь считаем перебором k = 1, 2, 3... (закрытой формулы нет: у предметов разные знаменатели).
 * Сравнение с допуском EPS: сумма дробей в Double может быть чуть меньше точного значения,
 * и цель, достигнутая ровно, «потерялась» бы. Тесты сверяют ответ точной арифметикой дробей.
 */
object OverallPlanner {

    /** Предел перебора: больше 60 оценок в каждом предмете считаем недостижимым. */
    const val MAX_K = 60

    /** Для сценария «один предмет» больше стольких оценок не показываем (дальше это шум). */
    const val SINGLE_MAX_K = 15

    /** Десяток в каждом предмете: до 2 «реально», до 4 «сложно», дальше «очень много». Пороги условные. */
    const val EVEN_EASY_MAX = 2
    const val EVEN_HARD_MAX = 4

    private const val EPS = 1e-9
    private val GRADES = listOf(10, 9, 8, 7)

    fun difficultyOf(tensPerSubject: Int): GoalDifficulty = when {
        tensPerSubject <= EVEN_EASY_MAX -> GoalDifficulty.EASY
        tensPerSubject <= EVEN_HARD_MAX -> GoalDifficulty.HARD
        else -> GoalDifficulty.VERY_HARD
    }

    fun plan(subjects: List<SubjectScores>, target: Double): OverallPlan {
        val data = subjects.filter { it.scores.isNotEmpty() }
        val m = data.size
        if (m == 0) return OverallPlan(GoalStatus.NO_DATA, target, 0.0, 0)

        val sums = data.map { it.scores.sum() }
        val counts = data.map { it.scores.size }
        val averages = data.indices.map { sums[it].toDouble() / counts[it] }
        val overall = averages.average()

        if (!target.isFinite()) return OverallPlan(GoalStatus.INVALID_TARGET, target, overall, m)
        val t = Math.round(target * 100) / 100.0
        if (t < GoalPlanner.MIN_GRADE || t > GoalPlanner.MAX_GRADE) {
            return OverallPlan(GoalStatus.INVALID_TARGET, target, overall, m)
        }
        if (overall + EPS >= t) return OverallPlan(GoalStatus.ACHIEVED, target, overall, m)

        // Сценарий 1: в каждом предмете по k оценок g
        val even = GRADES.mapNotNull { g ->
            val k = (1..MAX_K).firstOrNull { k ->
                data.indices.map { (sums[it] + k * g).toDouble() / (counts[it] + k) }.average() + EPS >= t
            }
            k?.let { OverallEven(g, it, it * m) }
        }
        // Без десяток цель не достигается (например, цель 10): дальше считать нечего
        if (even.none { it.grade == 10 }) return OverallPlan(GoalStatus.UNREACHABLE, target, overall, m)

        // Сценарий 2: все новые оценки только в предмете i
        val totalOfAverages = averages.sum()
        val rows = mutableListOf<OverallSubjectRow>()
        val cannot = mutableListOf<String>()
        for (i in data.indices) {
            val options = GRADES.mapNotNull { g ->
                val k = (1..SINGLE_MAX_K).firstOrNull { k ->
                    val newAverage = (sums[i] + k * g).toDouble() / (counts[i] + k)
                    (totalOfAverages - averages[i] + newAverage) / m + EPS >= t
                }
                k?.let { GradeCount(g, it) }
            }
            if (options.isEmpty()) cannot.add(data[i].name) else rows.add(OverallSubjectRow(data[i].name, averages[i], options))
        }
        // Сначала те предметы, что вытянут быстрее всего (меньше всего десяток), потом по названию.
        // Десятки всегда в начале списка: если подходит любая оценка, то десятки подходят не хуже.
        rows.sortWith(compareBy({ it.options.first().count }, { it.subject }))

        val tens = even.first { it.grade == 10 }.perSubject
        return OverallPlan(GoalStatus.REACHABLE, target, overall, m, even, rows, cannot, difficultyOf(tens))
    }
}
