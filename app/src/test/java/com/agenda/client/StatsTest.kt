package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private val eps = 0.0001

    @Test
    fun subjectAveragesSkipSubjectsWithoutGrades() {
        val items = listOf(
            SubjectGrades("Chimie", listOf(9, 10, 8)),
            SubjectGrades("Educație fizică", emptyList())
        )

        val result = Stats.subjectAverages(items)

        assertEquals(1, result.size)
        assertEquals("Chimie", result[0].subject)
        assertEquals(9.0, result[0].average, eps)
        assertEquals(3, result[0].count)
    }

    @Test
    fun overallIsAverageOfSubjectAveragesNotOfAllGrades() {
        // Предмет A: одна оценка 10. Предмет B: четыре оценки по 6.
        // Среднее из средних = (10 + 6) / 2 = 8. Среднее по всем оценкам было бы 6.8.
        val averages = Stats.subjectAverages(
            listOf(SubjectGrades("A", listOf(10)), SubjectGrades("B", listOf(6, 6, 6, 6)))
        )

        assertEquals(8.0, Stats.overall(averages)!!, eps)
    }

    @Test
    fun overallIsNullWithoutData() {
        assertNull(Stats.overall(emptyList()))
    }

    @Test
    fun datedGradesAreSortedAndUndatedAreDropped() {
        val items = listOf(
            SubjectGrades("A", listOf(9, 10, 8), dates = listOf(null, "2026-10-06", "2026-10-02")),
            SubjectGrades("B", listOf(7), dates = listOf("2026-10-04"))
        )

        val result = Stats.datedGrades(items)

        assertEquals(listOf("2026-10-02", "2026-10-04", "2026-10-06"), result.map { it.date })
        assertEquals(listOf(8, 7, 10), result.map { it.score })
    }

    @Test
    fun runningOverallFollowsSubjectMeans() {
        val dated = listOf(
            DatedGrade("2026-10-01", "A", 10),
            DatedGrade("2026-10-02", "B", 6),
            DatedGrade("2026-10-03", "A", 8)
        )

        val points = Stats.runningOverall(dated)

        assertEquals(3, points.size)
        assertEquals(10.0, points[0].value, eps)          // только A: 10
        assertEquals(8.0, points[1].value, eps)           // A=10, B=6 -> 8
        assertEquals(7.5, points[2].value, eps)           // A=(10+8)/2=9, B=6 -> 7.5
    }

    @Test
    fun runningOverallOfNothingIsEmpty() {
        assertTrue(Stats.runningOverall(emptyList()).isEmpty())
    }
}
