package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class OverallPlannerTest {

    private fun s(name: String, vararg scores: Int) = SubjectScores(name, scores.toList())

    /** Точная проверка без дробей Double: сумма (S_i/n_i) >= m * цель, считаем целыми BigInteger. */
    private fun reaches(sums: List<Int>, counts: List<Int>, t100: Long): Boolean {
        var num = BigInteger.ZERO
        var den = BigInteger.ONE
        for (i in sums.indices) { // num/den += sums[i]/counts[i]
            num = num * BigInteger.valueOf(counts[i].toLong()) + BigInteger.valueOf(sums[i].toLong()) * den
            den *= BigInteger.valueOf(counts[i].toLong())
        }
        // num/den >= m * t100/100  <=>  num * 100 >= m * t100 * den
        return num * BigInteger.valueOf(100) >= BigInteger.valueOf(sums.size.toLong() * t100) * den
    }

    @Test
    fun noSubjectsMeansNoData() {
        assertEquals(GoalStatus.NO_DATA, OverallPlanner.plan(emptyList(), 9.0).status)
        assertEquals(GoalStatus.NO_DATA, OverallPlanner.plan(listOf(SubjectScores("x", emptyList())), 9.0).status)
    }

    @Test
    fun targetOutsideRangeIsInvalid() {
        assertEquals(GoalStatus.INVALID_TARGET, OverallPlanner.plan(listOf(s("A", 8)), 11.0).status)
        assertEquals(GoalStatus.INVALID_TARGET, OverallPlanner.plan(listOf(s("A", 8)), 0.0).status)
        assertEquals(GoalStatus.INVALID_TARGET, OverallPlanner.plan(listOf(s("A", 8)), Double.NaN).status)
    }

    @Test
    fun overallIsTheMeanOfSubjectMeans() {
        // A: (8+8)/2 = 8, B: 10, общий = 9, а не среднее по четырём оценкам (9.0 совпало бы), поэтому берём разное число оценок
        val plan = OverallPlanner.plan(listOf(s("A", 8, 8), s("B", 10, 10, 10, 10)), 9.0)

        assertEquals(9.0, plan.overall, 0.0001)
        assertEquals(GoalStatus.ACHIEVED, plan.status)
    }

    @Test
    fun achievedExactlyAtTargetIsNotLost() {
        // (8.5 + 9.5)/2 = 9.0 ровно
        val plan = OverallPlanner.plan(listOf(s("A", 8, 9), s("B", 9, 10)), 9.0)

        assertEquals(GoalStatus.ACHIEVED, plan.status)
    }

    @Test
    fun evenSpreadAndSingleSubjectForTwoSubjects() {
        // A = 8.0, B = 10.0, общий 9.0, цель 9.5. B выше десяти не станет, значит весь подъём за счёт A:
        // A должен дойти до 9.0: (16 + 10k)/(2 + k) >= 9  ->  k >= 2
        val plan = OverallPlanner.plan(listOf(s("A", 8, 8), s("B", 10, 10)), 9.5)

        assertEquals(GoalStatus.REACHABLE, plan.status)
        assertEquals(OverallEven(10, 2, 4), plan.even.first())
        assertEquals(listOf("A"), plan.single.map { it.subject })
        assertEquals(listOf(GradeCount(10, 2)), plan.single[0].options) // девятки A до 9.0 не доведут
        assertEquals(listOf("B"), plan.cannotAlone)
    }

    @Test
    fun targetTenIsUnreachableWhileThereAreLowerGrades() {
        assertEquals(GoalStatus.UNREACHABLE, OverallPlanner.plan(listOf(s("A", 10, 9), s("B", 10)), 10.0).status)
    }

    @Test
    fun singleRowsAreSortedByEaseThenName() {
        val plan = OverallPlanner.plan(
            listOf(s("Б", 7, 7), s("А", 7, 7), s("В", 9, 9, 9), s("Г", 5, 5)), 7.5
        )

        assertEquals(GoalStatus.REACHABLE, plan.status)
        val counts = plan.single.map { it.options.first().count }
        assertEquals(counts.sorted(), counts)
        // при равенстве по алфавиту: А раньше Б
        val ab = plan.single.map { it.subject }.filter { it == "А" || it == "Б" }
        assertEquals(listOf("А", "Б"), ab)
    }

    @Test
    fun difficultyFollowsTensPerSubject() {
        assertEquals(GoalDifficulty.EASY, OverallPlanner.difficultyOf(2))
        assertEquals(GoalDifficulty.HARD, OverallPlanner.difficultyOf(3))
        assertEquals(GoalDifficulty.HARD, OverallPlanner.difficultyOf(4))
        assertEquals(GoalDifficulty.VERY_HARD, OverallPlanner.difficultyOf(5))
    }

    /** Главная проверка: ответы минимальны и достаточны, сверка точной арифметикой, а не тем же кодом. */
    @Test
    fun answersAreExactlyMinimalForManyInputs() {
        val sets = listOf(
            listOf(s("A", 8, 9, 8), s("B", 7, 7), s("C", 10, 9, 9, 10)),
            listOf(s("A", 5, 6), s("B", 6, 6, 7, 8, 9)),
            listOf(s("A", 9, 9, 10), s("B", 8), s("C", 7, 8), s("D", 10, 10)),
            listOf(s("A", 4, 5, 5), s("B", 8, 9))
        )
        val targets = listOf(5.0, 6.25, 7.0, 7.5, 8.0, 8.4, 8.5, 8.75, 9.0, 9.3, 9.5)

        for (subjects in sets) for (target in targets) {
            val plan = OverallPlanner.plan(subjects, target)
            if (plan.status != GoalStatus.REACHABLE) continue
            val t100 = Math.round(target * 100)
            val sums = subjects.map { it.scores.sum() }
            val counts = subjects.map { it.scores.size }

            for (e in plan.even) {
                val hit = reaches(sums.map { it + e.perSubject * e.grade }, counts.map { it + e.perSubject }, t100)
                assertTrue("$subjects цель $target: ${e.perSubject}×${e.grade} в каждом должно хватать", hit)
                if (e.perSubject > 1) {
                    val k = e.perSubject - 1
                    val miss = reaches(sums.map { it + k * e.grade }, counts.map { it + k }, t100)
                    assertTrue("$subjects цель $target: $k×${e.grade} в каждом уже не должно хватать", !miss)
                }
                assertEquals(e.perSubject * subjects.size, e.total)
            }
            for (row in plan.single) {
                val i = subjects.indexOfFirst { it.name == row.subject }
                for (o in row.options) {
                    fun with(k: Int) = reaches(
                        sums.mapIndexed { j, x -> if (j == i) x + k * o.grade else x },
                        counts.mapIndexed { j, x -> if (j == i) x + k else x }, t100
                    )
                    assertTrue("$subjects цель $target ${row.subject}: ${o.count}×${o.grade} должно хватать", with(o.count))
                    if (o.count > 1) assertTrue("${row.subject}: ${o.count - 1}×${o.grade} уже не должно хватать", !with(o.count - 1))
                }
            }
            // кто попал в «не вытянет один», тому и 15 десяток не хватило бы
            for (name in plan.cannotAlone) {
                val i = subjects.indexOfFirst { it.name == name }
                val k = OverallPlanner.SINGLE_MAX_K
                val hit = reaches(
                    sums.mapIndexed { j, x -> if (j == i) x + k * 10 else x },
                    counts.mapIndexed { j, x -> if (j == i) x + k else x }, t100
                )
                assertTrue("$name не должен был попасть в «не вытянет»", !hit)
            }
        }
    }

    // ---- выбор оценок для периода ----

    @Test
    fun yearPoolsGradesOfTheSameSubjectAcrossSemesters() {
        val items = listOf(
            SubjectGrades("Математика", listOf(8, 9), "I"),
            SubjectGrades("Физика", listOf(7), "I"),
            SubjectGrades("Математика", listOf(10), "II"),
            SubjectGrades("История", emptyList(), "II")
        )

        val year = GoalScope.forYear(items)

        assertEquals(listOf("Математика", "Физика"), year.map { it.name })
        assertEquals(listOf(8, 9, 10), year[0].scores)
    }

    @Test
    fun semesterSkipsSubjectsWithoutGrades() {
        val items = listOf(
            SubjectGrades("Математика", listOf(8), "I"),
            SubjectGrades("История", emptyList(), "I")
        )

        assertEquals(listOf("Математика"), GoalScope.forSemester(items).map { it.name })
    }
}
