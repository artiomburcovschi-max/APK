package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTextTest {

    @Test
    fun reachableEasyIsGreenAndListsRows() {
        val plan = GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 8.5) // 2 десятки или 5 девяток

        val verdict = GoalText.verdict(plan)
        assertEquals("Цель 8.50 реальна", verdict.text)
        assertEquals(Tone.GOOD, verdict.tone)

        val rows = GoalText.rows(plan)
        assertEquals(listOf("2 десятки", "5 девяток"), rows.map { it.text })
        assertEquals(listOf("реально", "сложно"), rows.map { it.hint })
    }

    @Test
    fun hardAndVeryHardAreColoredAccordingly() {
        val hard = GoalText.verdict(GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 9.0)) // 5 десяток
        assertEquals(Tone.WARN, hard.tone)

        val veryHard = GoalText.verdict(GoalPlanner.plan(listOf(5, 5, 5, 5, 5), 9.5)) // 5*9.5... много
        assertEquals(Tone.BAD, veryHard.tone)
    }

    @Test
    fun achievedShowsReserveRowsWithMaximum() {
        val plan = GoalPlanner.plan(listOf(10, 10, 9, 9, 9), 9.0)

        assertEquals("Цель 9.00 уже достигнута", GoalText.verdict(plan).text)
        assertEquals(listOf("максимум 2 восьмёрки", "максимум 1 семёрка"), GoalText.rows(plan).map { it.text })
    }

    @Test
    fun achievedWithoutReserveExplainsIt() {
        val plan = GoalPlanner.plan(listOf(9, 10, 8), 9.0)

        assertTrue(GoalText.rows(plan).isEmpty())
        assertTrue(GoalText.optionsTitle(plan)!!.startsWith("Запаса нет"))
    }

    @Test
    fun unreachableTenSuggestsNineAndAHalf() {
        val verdict = GoalText.verdict(GoalPlanner.plan(listOf(10, 9), 10.0))

        assertEquals(Tone.BAD, verdict.tone)
        assertTrue(verdict.text.contains("9.50"))
    }

    @Test
    fun currentLineShowsAverageAndCount() {
        val plan = GoalPlanner.plan(listOf(8, 8, 8, 8, 9), 9.0)

        assertEquals("Сейчас: 8.20 · 5 оценок", GoalText.current(plan))
        assertEquals("", GoalText.current(GoalPlanner.plan(emptyList(), 9.0)))
    }

    @Test
    fun targetIsShownInTheFieldWithoutNeedlessZeros() {
        assertEquals("9", GoalText.formatTarget(9.0))
        assertEquals("8.5", GoalText.formatTarget(8.5))
        assertEquals("8.75", GoalText.formatTarget(8.75))
        assertEquals("10", GoalText.formatTarget(10.0))
    }
}
