package com.agenda.client

data class SubjectAverage(val subject: String, val average: Double, val count: Int)
data class DatedGrade(val date: String, val subject: String, val score: Int)
data class TrendPoint(val date: String, val value: Double)

/** Чистая логика аналитики (без Android): средние и динамика. */
object Stats {

    /** Среднее по каждому предмету (предметы без оценок пропускаем). */
    fun subjectAverages(items: List<SubjectGrades>): List<SubjectAverage> =
        items.filter { it.scores.isNotEmpty() }
            .map { SubjectAverage(it.subject, it.scores.average(), it.scores.size) }

    /**
     * Общий средний балл = среднее из средних по предметам: каждый предмет весит одинаково,
     * независимо от того, сколько по нему оценок.
     */
    fun overall(subjects: List<SubjectAverage>): Double? =
        if (subjects.isEmpty()) null else subjects.map { it.average }.average()

    /** Оценки с известной датой, от старых к новым. */
    fun datedGrades(items: List<SubjectGrades>): List<DatedGrade> {
        val result = mutableListOf<DatedGrade>()
        for (item in items) {
            item.scores.forEachIndexed { i, score ->
                val date = item.dates.getOrNull(i)
                if (date != null) result.add(DatedGrade(date, item.subject, score))
            }
        }
        return result.sortedBy { it.date } // сортировка стабильная: порядок в один день сохраняется
    }

    /** Общий средний после каждой оценки (считаем только по оценкам с датой). */
    fun runningOverall(dated: List<DatedGrade>): List<TrendPoint> {
        val totals = LinkedHashMap<String, Pair<Int, Int>>() // предмет -> (сумма, количество)
        val points = mutableListOf<TrendPoint>()
        for (g in dated) {
            val (sum, count) = totals[g.subject] ?: (0 to 0)
            totals[g.subject] = (sum + g.score) to (count + 1)
            val means = totals.values.map { it.first.toDouble() / it.second }
            points.add(TrendPoint(g.date, means.average()))
        }
        return points
    }
}
