package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTextTest {

    private fun single(plan: GoalPlan) = GoalText.sections(plan).first().rows

    @Test
    fun reachableEasyIsGreenAndListsRows() {
        val plan = GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 8.5) // 2 десятки или 5 девяток

        val verdict = GoalText.verdict(plan)
        assertEquals("Цель 8.50 реальна", verdict.text)
        assertEquals(Tone.GOOD, verdict.tone)

        val rows = single(plan)
        assertEquals(listOf("2 десятки", "5 девяток"), rows.map { it.text })
        assertEquals(listOf("реально", "сложно"), rows.map { it.hint })
        assertEquals(listOf(10, 9), rows.map { it.grade })
    }

    @Test
    fun hardAndVeryHardAreColoredAccordingly() {
        val hard = GoalText.verdict(GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 9.0)) // 5 десяток
        assertEquals(Tone.WARN, hard.tone)

        val veryHard = GoalText.verdict(GoalPlanner.plan(listOf(5, 5, 5, 5, 5), 9.5)) // 45 десяток
        assertEquals(Tone.BAD, veryHard.tone)
    }

    @Test
    fun equalToTargetGradeIsExplainedNotListedAsOption() {
        // средний 8.5, цель 9: девятки к 9.00 не приведут, это и должно быть сказано
        val plan = GoalPlanner.plan(listOf(8, 9, 8, 9), 9.0)

        val rows = single(plan)
        assertEquals(listOf("2 десятки", "Одни девятки не помогут"), rows.map { it.text })
        assertTrue(rows.last().hint.contains("не дойдёт"))
    }

    @Test
    fun fractionalTargetHasNoEqualGradeRow() {
        val rows = single(GoalPlanner.plan(listOf(8, 8, 8, 8, 8), 8.5))

        assertTrue(rows.none { it.text.startsWith("Одни") })
    }

    @Test
    fun seriesSectionShowsMixedExamples() {
        val sections = GoalText.sections(GoalPlanner.plan(listOf(8, 9, 8, 9), 9.0)) // сумма 34, n=4

        assertEquals(2, sections.size)
        val series = sections[1].rows
        assertEquals("2 десятки", series[0].text)
        assertEquals("2 десятки + 1 девятка", series[1].text)
        assertEquals("2 десятки + 2 девятки", series[2].text)
        assertEquals("всего 3 оценки, сумма не меньше 29", series[1].hint)
        assertEquals("2", series[0].lead)
        assertNull(series[0].grade)
    }

    @Test
    fun achievedShowsReserveRowsWithMaximum() {
        val plan = GoalPlanner.plan(listOf(10, 10, 9, 9, 9), 9.0)

        assertEquals("Цель 9.00 уже достигнута", GoalText.verdict(plan).text)
        assertEquals(listOf("максимум 2 восьмёрки", "максимум 1 семёрка"), single(plan).map { it.text })
    }

    @Test
    fun achievedWithoutReserveExplainsIt() {
        val sections = GoalText.sections(GoalPlanner.plan(listOf(9, 10, 8), 9.0))

        assertEquals(1, sections.size)
        assertTrue(sections[0].rows.isEmpty())
        assertTrue(sections[0].title.startsWith("Запаса нет"))
    }

    @Test
    fun unreachableTenSuggestsNineAndAHalf() {
        val plan = GoalPlanner.plan(listOf(10, 9), 10.0)
        val verdict = GoalText.verdict(plan)

        assertEquals(Tone.BAD, verdict.tone)
        assertTrue(verdict.text.contains("9.50"))
        assertTrue(GoalText.sections(plan).isEmpty())
    }

    @Test
    fun currentLineShowsAverageAndCount() {
        assertEquals("Сейчас: 8.20 · 5 оценок", GoalText.current(listOf(8, 8, 8, 8, 9)))
        assertEquals("Сейчас: 8.50 · 4 оценки", GoalText.current(listOf(8, 9, 8, 9)))
        assertEquals("", GoalText.current(emptyList()))
    }

    @Test
    fun targetIsShownInTheFieldWithoutNeedlessZeros() {
        assertEquals("9", GoalText.formatTarget(9.0))
        assertEquals("8.5", GoalText.formatTarget(8.5))
        assertEquals("8.75", GoalText.formatTarget(8.75))
        assertEquals("10", GoalText.formatTarget(10.0))
    }

    @Test
    fun overallCurrentAndVerdicts() {
        assertEquals("Сейчас: 8.43 · 9 предметов", GoalText.currentOverall(8.43, 9))
        assertEquals("Сейчас: 9.00 · 1 предмет", GoalText.currentOverall(9.0, 1))
        assertEquals("Сейчас: 8.00 · 3 предмета", GoalText.currentOverall(8.0, 3))
        assertEquals("", GoalText.currentOverall(0.0, 0))

        val achieved = OverallPlanner.plan(listOf(SubjectScores("A", listOf(9, 10))), 9.0)
        assertEquals(Tone.GOOD, GoalText.overallVerdict(achieved).tone)

        val tenAsGoal = OverallPlanner.plan(listOf(SubjectScores("A", listOf(9, 10))), 10.0)
        assertTrue(GoalText.overallVerdict(tenAsGoal).text.contains("9.50"))
    }

    @Test
    fun overallSectionsListEvenAndSingleRows() {
        val plan = OverallPlanner.plan(
            listOf(SubjectScores("A", listOf(8, 8)), SubjectScores("B", listOf(10, 10))), 9.5
        )

        val sections = GoalText.overallSections(plan)
        assertEquals(2, sections.size)
        assertEquals("по 2 десятки в каждом предмете", sections[0].rows[0].text)
        assertEquals("реально, всего 4 оценки", sections[0].rows[0].hint)
        assertEquals("A", sections[1].rows[0].text)
        assertEquals("B", sections[1].rows[1].text) // «не вытянет один» идёт последней строкой
        assertEquals("–", sections[1].rows[1].lead)
    }

    @Test
    fun noteMentionsYearOnlyForYear() {
        assertEquals(GoalText.NOTE, GoalText.noteFor(false))
        assertTrue(GoalText.noteFor(true).contains("За год"))
    }
}
