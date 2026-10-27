package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendTest {

    @Test
    fun tooFewGradesMeansNoTrend() {
        assertEquals(TrendDirection.NONE, Trend.of(emptyList()).direction)
        assertEquals(TrendDirection.NONE, Trend.of(listOf(5, 6, 7, 8)).direction)
    }

    @Test
    fun recentBetterThanEarlierIsUp() {
        // последние три 9, 10, 10 (9.67), до них 8, 8 (8.0): растёт
        val info = Trend.of(listOf(8, 8, 9, 10, 10))

        assertEquals(TrendDirection.UP, info.direction)
        assertEquals(9.6667, info.recentAverage, 0.001)
        assertEquals(8.0, info.earlierAverage, 0.001)
    }

    @Test
    fun recentWorseThanEarlierIsDown() {
        assertEquals(TrendDirection.DOWN, Trend.of(listOf(10, 10, 8, 7, 7)).direction)
    }

    @Test
    fun smallDifferenceIsFlat() {
        assertEquals(TrendDirection.FLAT, Trend.of(listOf(8, 9, 8, 9, 8)).direction)
    }

    @Test
    fun exactlyHalfAPointCountsEvenWithFloatingPoint() {
        // последние (9, 9, 9) = 9.0, до них (8, 9) = 8.5: разница ровно 0.5
        assertEquals(TrendDirection.UP, Trend.of(listOf(8, 9, 9, 9, 9)).direction)
        // последние (8, 8, 8) = 8.0, до них (8, 9) = 8.5: разница ровно -0.5
        assertEquals(TrendDirection.DOWN, Trend.of(listOf(8, 9, 8, 8, 8)).direction)
    }

    @Test
    fun arrowsOnlyForUpAndDown() {
        assertEquals("↑", Trend.arrow(TrendDirection.UP))
        assertEquals("↓", Trend.arrow(TrendDirection.DOWN))
        assertEquals("", Trend.arrow(TrendDirection.FLAT))
        assertEquals("", Trend.arrow(TrendDirection.NONE))
    }

    @Test
    fun describeExplainsTheNumbers() {
        assertEquals(
            "Тренд: последние 3 оценки в среднем 9.67, до них 8.00 (растёт)",
            Trend.describe(Trend.of(listOf(8, 8, 9, 10, 10)))
        )
        assertNull(Trend.describe(Trend.of(listOf(8, 9))))
    }
}
