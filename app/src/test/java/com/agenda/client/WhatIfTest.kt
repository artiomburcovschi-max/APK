package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatIfTest {

    private fun s(name: String, vararg scores: Int) = SubjectScores(name, scores.toList())

    @Test
    fun subjectAndOverallChangeAfterAGrade() {
        // A: (8+9)/2 = 8.5, B: 10, общий 9.25. Добавляем в A десятку: A = 9.0, общий (9 + 10)/2 = 9.5
        val lines = WhatIf.compute(listOf(s("A", 8, 9), s("B", 10)), "A", listOf(10))!!

        assertEquals("Предмет: 8.50 → 9.00", lines[0].text)
        assertEquals("+0.50", lines[0].deltaText)
        assertEquals(Tone.GOOD, lines[0].tone)
        assertEquals("Общий средний: 9.25 → 9.50", lines[1].text)
        assertEquals("+0.25", lines[1].deltaText)
    }

    @Test
    fun overallWeightsEverySubjectEquallyNotByGradeCount() {
        // у A четыре оценки, у B одна: общий всё равно среднее из двух средних
        val lines = WhatIf.compute(listOf(s("A", 8, 8, 8, 8), s("B", 10)), "B", listOf(6))!!

        assertEquals(9.0, lines[1].before, 0.0001) // (8 + 10)/2
        assertEquals(8.0, lines[1].after, 0.0001)  // B стал 8, общий (8 + 8)/2
    }

    @Test
    fun lowGradeIsBadAndNoChangeIsNeutral() {
        val down = WhatIf.compute(listOf(s("A", 9, 9)), "A", listOf(5))!![0]
        assertEquals(Tone.BAD, down.tone)
        assertEquals("-1.33", down.deltaText) // (9 + 9 + 5) / 3 = 7.67 против 9.00

        val same = WhatIf.compute(listOf(s("A", 8, 8)), "A", listOf(8))!![0]
        assertEquals(Tone.NEUTRAL, same.tone)
        assertEquals("без изменений", same.deltaText)
    }

    @Test
    fun noGradesAddedMeansNoChange() {
        val lines = WhatIf.compute(listOf(s("A", 7, 8)), "A", emptyList())!!

        assertEquals(Tone.NEUTRAL, lines[0].tone)
        assertEquals(Tone.NEUTRAL, lines[1].tone)
    }

    @Test
    fun unknownSubjectGivesNull() {
        assertNull(WhatIf.compute(listOf(s("A", 8)), "Z", listOf(10)))
    }

    @Test
    fun addedTextListsGrades() {
        assertEquals("Если получить: 10, 9", WhatIf.addedText(listOf(10, 9)))
        assertEquals("", WhatIf.addedText(emptyList()))
    }
}
