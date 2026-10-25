package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalPlannerTest {

    private val eps = 0.0001

    private fun counts(plan: GoalPlan) = plan.options.map { it.grade to it.count }

    @Test
    fun noGradesMeansNoData() {
        assertEquals(GoalStatus.NO_DATA, GoalPlanner.plan(emptyList(), 9.0).status)
    }

    @Test
    fun targetOutsideOneToTenIsInvalid() {
        assertEquals(GoalStatus.INVALID_TARGET, GoalPlanner.plan(listOf(8, 9), 11.0).status)
        assertEquals(GoalStatus.INVALID_TARGET, GoalPlanner.plan(listOf(8, 9), 0.5).status)
        assertEquals(GoalStatus.INVALID_TARGET, GoalPlanner.plan(listOf(8, 9), Double.NaN).status)
    }

    @Test
    fun reachableListsOnlyGradesAboveTarget() {
        // средний 8.0, цель 9: оценка 9 средний не поднимет (он станет «ближе», но не дойдёт), нужны десятки
        val plan = GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 9.0)

        assertEquals(GoalStatus.REACHABLE, plan.status)
        assertEquals(listOf(10 to 5), counts(plan))
        assertEquals(8.0, plan.average, eps)
    }

    @Test
    fun fractionalTargetGivesSeveralOptions() {
        // сумма 40 из 5 оценок, цель 8.5
        // две десятки: 60/7 = 8.57 >= 8.5, одна десятка: 50/6 = 8.33 < 8.5
        // пять девяток: 85/10 = 8.5 >= 8.5, четыре: 76/9 = 8.44 < 8.5
        val plan = GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 8.5)

        assertEquals(listOf(10 to 2, 9 to 5), counts(plan))
    }

    @Test
    fun lowTargetAllowsLowerGrades() {
        // пять пятёрок (сумма 25), цель 6.0: не хватает 5 очков до 30
        // каждая оценка g поднимает сумму на (g - 6): семёрка на 1 -> нужно 5, восьмёрка на 2 -> 3, девятка на 3 -> 2, десятка на 4 -> 2
        val plan = GoalPlanner.plan(listOf(5, 5, 5, 5, 5), 6.0)

        assertEquals(listOf(10 to 2, 9 to 2, 8 to 3, 7 to 5), counts(plan))
    }

    @Test
    fun exactlyAtTargetIsAchievedWithNoReserve() {
        val plan = GoalPlanner.plan(listOf(9, 10, 8), 9.0) // сумма 27, n=3, ровно 9.00

        assertEquals(GoalStatus.ACHIEVED, plan.status)
        assertTrue(plan.options.isEmpty())
    }

    @Test
    fun exactFractionalTargetIsNotLostToFloatingPoint() {
        // 43 / 5 = 8.6 ровно. В Double 8.6 * 5 может оказаться чуть меньше 43, и цель «потерялась» бы
        val plan = GoalPlanner.plan(listOf(9, 9, 8, 9, 8), 8.6)

        assertEquals(GoalStatus.ACHIEVED, plan.status)
    }

    @Test
    fun achievedShowsReserveForLowerGrades() {
        // сумма 47, n=5 (средний 9.4), цель 9: можно две восьмёрки (47+16)/7 = 9.0, или одну семёрку (47+7)/6 = 9.0
        val plan = GoalPlanner.plan(listOf(10, 10, 9, 9, 9), 9.0)

        assertEquals(GoalStatus.ACHIEVED, plan.status)
        assertEquals(listOf(8 to 2, 7 to 1), counts(plan))
    }

    @Test
    fun targetTenIsUnreachableWhileThereAreLowerGrades() {
        assertEquals(GoalStatus.UNREACHABLE, GoalPlanner.plan(listOf(10, 9), 10.0).status)
    }

    @Test
    fun targetTenIsAchievedWhenAllGradesAreTen() {
        assertEquals(GoalStatus.ACHIEVED, GoalPlanner.plan(listOf(10, 10), 10.0).status)
    }

    @Test
    fun difficultyFollowsTheNumberOfTens() {
        assertEquals(GoalDifficulty.EASY, GoalPlanner.difficultyOf(3))
        assertEquals(GoalDifficulty.HARD, GoalPlanner.difficultyOf(4))
        assertEquals(GoalDifficulty.HARD, GoalPlanner.difficultyOf(7))
        assertEquals(GoalDifficulty.VERY_HARD, GoalPlanner.difficultyOf(8))
        // сумма 40/5, цель 9.0 -> 5 десяток -> «сложно»
        assertEquals(GoalDifficulty.HARD, GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 9.0).difficulty)
    }

    @Test
    fun parseTargetAcceptsCommaAndDot() {
        assertEquals(8.5, GoalPlanner.parseTarget("8,5")!!, eps)
        assertEquals(8.5, GoalPlanner.parseTarget(" 8.5 ")!!, eps)
        assertEquals(9.0, GoalPlanner.parseTarget("9")!!, eps)
        assertNull(GoalPlanner.parseTarget(""))
        assertNull(GoalPlanner.parseTarget(null))
        assertNull(GoalPlanner.parseTarget("abc"))
        assertNull(GoalPlanner.parseTarget("NaN"))
        assertNull(GoalPlanner.parseTarget("Infinity"))
    }

    /** Главная проверка: ответ минимален (на 1 меньше не хватает) и достаточен (с ним цель достигнута). */
    @Test
    fun countsAreExactlyMinimalForManyInputs() {
        val samples = listOf(
            listOf(5), listOf(7, 8), listOf(6, 6, 7), listOf(8, 9, 7, 10, 6), listOf(4, 5, 5, 6),
            listOf(9, 9, 9, 8), listOf(7, 7, 7, 7, 7, 7, 7), listOf(10, 3), listOf(2, 2, 2)
        )
        val targets = listOf(5.0, 5.5, 6.0, 6.5, 7.0, 7.25, 7.5, 8.0, 8.5, 8.75, 9.0, 9.5, 9.99)

        for (scores in samples) for (target in targets) {
            val plan = GoalPlanner.plan(scores, target)
            val n = scores.size
            val s = scores.sum()
            val t100 = Math.round(target * 100)
            if (plan.status != GoalStatus.REACHABLE) continue
            for ((g, k) in plan.options.map { it.grade to it.count }) {
                assertTrue("$scores цель $target: $k×$g должно хватать",
                    100L * (s + k * g) >= t100 * (n + k))
                assertTrue("$scores цель $target: ${k - 1}×$g уже не должно хватать",
                    100L * (s + (k - 1) * g) < t100 * (n + k - 1))
            }
        }
    }

    @Test
    fun reserveIsExactlyMaximalForManyInputs() {
        val samples = listOf(
            listOf(10, 10, 9, 9, 9), listOf(10, 10, 10, 9), listOf(9, 9, 10, 10, 10, 10),
            listOf(8, 9, 10, 10), listOf(10, 10, 10, 10, 10, 10)
        )
        val targets = listOf(5.0, 6.5, 7.0, 8.0, 8.5, 9.0, 9.5)

        for (scores in samples) for (target in targets) {
            val plan = GoalPlanner.plan(scores, target)
            if (plan.status != GoalStatus.ACHIEVED) continue
            val n = scores.size
            val s = scores.sum()
            val t100 = Math.round(target * 100)
            for ((g, k) in plan.options.map { it.grade to it.count }) {
                assertTrue("$scores цель $target: $k×$g ещё держат цель",
                    100L * (s + k * g) >= t100 * (n + k))
                assertTrue("$scores цель $target: ${k + 1}×$g уже ломают цель",
                    100L * (s + (k + 1) * g) < t100 * (n + k + 1))
            }
        }
    }
}
