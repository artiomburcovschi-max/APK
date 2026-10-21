package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeDatesTest {

    private fun r(date: String, score: Int) = RecentEval(date, "X", score)

    @Test
    fun convertsPortalDateToIsoAndBack() {
        assertEquals("2026-10-12", GradeDates.toIso("12.10.2026"))
        assertEquals("2026-01-05", GradeDates.toIso(" 5.1.2026 "))
        assertEquals("12.10.2026", GradeDates.toDisplay("2026-10-12"))
        assertEquals("12.10", GradeDates.toShort("2026-10-12"))
    }

    @Test
    fun rejectsBrokenDates() {
        assertNull(GradeDates.toIso("12/10/2026"))
        assertNull(GradeDates.toIso("32.10.2026"))
        assertNull(GradeDates.toIso("12.13.2026"))
        assertNull(GradeDates.toIso("abc"))
        assertNull(GradeDates.toDisplay("12.10.2026"))
    }

    @Test
    fun isoDatesSortAsText() {
        val sorted = listOf("2026-10-12", "2026-09-30", "2026-10-02").sorted()
        assertEquals(listOf("2026-09-30", "2026-10-02", "2026-10-12"), sorted)
    }

    @Test
    fun normalizeIgnoresCaseAndExtraSpaces() {
        assertEquals(GradeDates.normalize("Limba  engleză "), GradeDates.normalize("limba engleză"))
    }

    @Test
    fun alignsDatesToTheEndOfTheList() {
        // В таблице 5 оценок, а свежие из блока Note recente: две последние.
        val result = GradeDates.alignTail(listOf(9, 10, 8, 9, 10), listOf(r("2026-10-02", 9), r("2026-10-06", 10)))

        assertEquals(mapOf(4 to "2026-10-06", 3 to "2026-10-02"), result)
    }

    @Test
    fun stopsAtFirstMismatch() {
        // Последняя оценка совпала (10), предпоследняя нет: привязываем только последнюю.
        val result = GradeDates.alignTail(listOf(9, 8, 10), listOf(r("2026-10-02", 7), r("2026-10-06", 10)))

        assertEquals(mapOf(2 to "2026-10-06"), result)
    }

    @Test
    fun noMatchGivesNoDates() {
        assertTrue(GradeDates.alignTail(listOf(9, 8), listOf(r("2026-10-06", 10))).isEmpty())
        assertTrue(GradeDates.alignTail(emptyList(), listOf(r("2026-10-06", 10))).isEmpty())
        assertTrue(GradeDates.alignTail(listOf(9), emptyList()).isEmpty())
    }
}
