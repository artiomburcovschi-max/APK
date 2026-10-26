package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshProgressTest {

    @Test
    fun startsAtTheStageBase() {
        val p = RefreshProgress.advance(null, RefreshStage.OPENING)

        assertEquals(RefreshStage.OPENING, p.stage)
        assertEquals(5, p.percent)
    }

    @Test
    fun waitingCreepsInsideTheStageButNeverPastIt() {
        assertEquals(15, RefreshStage.PAGE_LOADED.percent(0))
        assertEquals(20, RefreshStage.PAGE_LOADED.percent(5))
        assertEquals(24, RefreshStage.PAGE_LOADED.percent(100)) // следующий этап начинается с 25
        assertEquals(86, RefreshStage.WAITING_TABLE.percent(8))  // 8 секунд ожидания таблицы, шаг 2
        assertEquals(89, RefreshStage.WAITING_TABLE.percent(100))
        assertEquals(99, RefreshStage.SAVING.cap)
    }

    @Test
    fun progressNeverGoesBackwards() {
        var p = RefreshProgress.advance(null, RefreshStage.WAITING_TABLE, 4) // 78
        p = RefreshProgress.advance(p, RefreshStage.WAITING_TABLE, 1)        // меньше: остаётся 78
        assertEquals(78, p.percent)

        val late = RefreshProgress.advance(p, RefreshStage.PAGE_LOADED, 3)   // запоздавший ранний этап
        assertEquals(RefreshStage.WAITING_TABLE, late.stage)
        assertEquals(78, late.percent)
    }

    @Test
    fun nextStageMovesForward() {
        var p = RefreshProgress.advance(null, RefreshStage.SUBMITTED, 14)    // 69
        p = RefreshProgress.advance(p, RefreshStage.WAITING_TABLE, 0)        // 70
        assertEquals(70, p.percent)
        p = RefreshProgress.advance(p, RefreshStage.READING)
        assertEquals(90, p.percent)
        assertEquals(RefreshStage.READING, p.stage)
    }

    @Test
    fun stagesAreOrderedByPercent() {
        val bases = RefreshStage.entries.map { it.base }
        assertEquals(bases.sorted(), bases)
        assertTrue(RefreshStage.entries.all { it.percent(1000) <= 99 })
    }

    @Test
    fun unknownStageNameFromScriptIsIgnored() {
        assertEquals(RefreshStage.LOGGING_IN, RefreshStage.fromName("LOGGING_IN"))
        assertNull(RefreshStage.fromName("SOMETHING_NEW"))
        assertNull(RefreshStage.fromName(null))
    }
}
