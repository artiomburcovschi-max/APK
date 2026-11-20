package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class WidgetPlannerTest {

    private val zone = TimeZone.getTimeZone("Europe/Chisinau")
    private val day = "2026-11-19"

    private val grid = listOf(
        1 to ("08:00" to "08:45"), 2 to ("08:55" to "09:40"), 3 to ("09:50" to "10:35"),
        4 to ("10:45" to "11:30"), 5 to ("11:40" to "12:25"), 6 to ("12:45" to "13:30"), 7 to ("13:40" to "14:25")
    )
    private val lessons = grid.map { (n, t) ->
        Lesson(day, n, t.first, t.second, "Предмет $n", "", "Учитель", "", emptyList())
    }

    private fun at(hhmm: String, seconds: Int = 0) = LessonClock.epoch(day, hhmm, zone)!! + seconds * 1000L
    private fun plan(hhmm: String, sec: Int = 0) = WidgetPlanner.plan(lessons, at(hhmm, sec), zone)

    @Test
    fun inALessonTheCountdownIsBlueAndRefreshComesJustAfterTheEnd() {
        val p = plan("09:00")
        assertEquals(WidgetTone.LESSON, p.tone)
        assertEquals("Урок 2 · Предмет 2", p.title)
        assertEquals(40 * 60_000L, p.countdownMs)
        assertEquals(at("09:40") + WidgetPlanner.REFRESH_MARGIN_MS, p.refreshAtMs)
    }

    @Test
    fun onABreakTheCountdownIsGreenAndRefreshComesJustAfterTheNextStart() {
        val p = plan("08:50")
        assertEquals(WidgetTone.FREE, p.tone)
        assertEquals("Перемена", p.title)
        assertEquals(5 * 60_000L, p.countdownMs)
        assertEquals(at("08:55") + WidgetPlanner.REFRESH_MARGIN_MS, p.refreshAtMs)
    }

    @Test
    fun beforeTheFirstLessonCountsDownToIt() {
        val p = plan("07:30")
        assertEquals(WidgetTone.FREE, p.tone)
        assertEquals(30 * 60_000L, p.countdownMs)
        assertEquals(at("08:00") + WidgetPlanner.REFRESH_MARGIN_MS, p.refreshAtMs)
    }

    @Test
    fun whenThereIsNothingToCountTheWidgetWaitsForMidnight() {
        val finished = plan("15:00")
        assertEquals(WidgetTone.IDLE, finished.tone)
        assertNull(finished.countdownMs)
        val midnight = LessonClock.epoch("2026-11-20", "00:00", zone)!!
        assertEquals(midnight + WidgetPlanner.REFRESH_MARGIN_MS, finished.refreshAtMs)

        val empty = WidgetPlanner.plan(emptyList(), at("10:00"), zone)
        assertEquals(WidgetTone.IDLE, empty.tone)
        assertNull(empty.countdownMs)
        assertEquals("Расписание пока не загружено", empty.title)
        assertEquals(midnight + WidgetPlanner.REFRESH_MARGIN_MS, empty.refreshAtMs)
    }

    @Test
    fun anEarlyAlarmDoesNotMakeTheWidgetSpin() {
        // будильник сработал на секунду раньше конца урока: состояние прежнее, но новое время обновления строго в будущем
        val now = at("09:40") - 1000L
        val p = WidgetPlanner.plan(lessons, now, zone)
        assertEquals("Урок 2 · Предмет 2", p.title)
        assertTrue("обновление должно быть строго после конца урока", p.refreshAtMs > at("09:40"))
        assertTrue("запас меньше секунды не переживёт раннего будильника", WidgetPlanner.REFRESH_MARGIN_MS >= 1000L)
    }

    @Test
    fun theRefreshIsAlwaysInTheFutureAndNeverMoreThanADayAway() {
        var minute = 0
        while (minute < 24 * 60) {
            val hhmm = String.format("%02d:%02d", minute / 60, minute % 60)
            val now = at(hhmm)
            val p = WidgetPlanner.plan(lessons, now, zone)
            assertTrue("в $hhmm обновление не в будущем", p.refreshAtMs > now)
            assertTrue("в $hhmm обновление дальше суток", p.refreshAtMs - now <= 24 * 3600_000L)
            minute++
        }
    }

    @Test
    fun walkingThroughTheDayVisitsEveryStateOnceAndStopsAtMidnight() {
        // идём от 07:00 по моментам обновления: каждый раз состояние должно смениться, лишних перерисовок быть не должно
        var now = at("07:00")
        val titles = mutableListOf<String>()
        var refreshes = 0
        val midnight = LessonClock.epoch("2026-11-20", "00:00", zone)!!
        while (now < midnight) {
            val p = WidgetPlanner.plan(lessons, now, zone)
            titles.add(p.title)
            refreshes++
            now = p.refreshAtMs
            assertTrue("зациклились", refreshes < 40)
        }
        assertEquals(
            listOf(
                "Уроки ещё не начались",
                "Урок 1 · Предмет 1", "Перемена",
                "Урок 2 · Предмет 2", "Перемена",
                "Урок 3 · Предмет 3", "Перемена",
                "Урок 4 · Предмет 4", "Перемена",
                "Урок 5 · Предмет 5", "Перемена",
                "Урок 6 · Предмет 6", "Перемена",
                "Урок 7 · Предмет 7",
                "Уроки на сегодня закончились"
            ),
            titles
        )
    }
}
