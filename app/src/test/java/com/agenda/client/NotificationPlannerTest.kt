package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class NotificationPlannerTest {

    private val zone = TimeZone.getTimeZone("Europe/Chisinau")
    private val day = "2026-11-19"
    private val tomorrow = "2026-11-20"
    private val margin = WidgetPlanner.REFRESH_MARGIN_MS

    private val grid = listOf(
        1 to ("08:00" to "08:45"), 2 to ("08:55" to "09:40"), 3 to ("09:50" to "10:35"),
        4 to ("10:45" to "11:30"), 5 to ("11:40" to "12:25"), 6 to ("12:45" to "13:30"), 7 to ("13:40" to "14:25")
    )

    private fun school(date: String) = grid.map { (n, t) -> Lesson(date, n, t.first, t.second, "Предмет $n", "", "Учитель", "", emptyList()) }
    private val lessons = school(day) + school(tomorrow)

    private fun at(date: String, hhmm: String, sec: Int = 0) = LessonClock.epoch(date, hhmm, zone)!! + sec * 1000L
    private fun plan(hhmm: String, sec: Int = 0, dismissed: String = "", date: String = day, data: List<Lesson> = lessons) =
        NotificationPlanner.plan(data, at(date, hhmm, sec), zone, dismissed)

    @Test
    fun inALessonItShowsTheCountdownTargetAndWhatIsNext() {
        val p = plan("09:00")
        assertTrue(p.show)
        assertEquals("Урок 2 · Предмет 2", p.title)
        assertEquals("до конца урока · Дальше: урок 3 · Предмет 3, 09:50", p.text)
        assertEquals(at(day, "09:40"), p.countdownEndMs)
        assertEquals(at(day, "09:40") + margin, p.refreshAtMs)
    }

    @Test
    fun onABreakItCountsDownToTheNextLesson() {
        val p = plan("12:30")
        assertTrue(p.show)
        assertEquals("Перемена", p.title)
        assertEquals(at(day, "12:45"), p.countdownEndMs)
        assertEquals("до начала урока 6 · Дальше: урок 6 · Предмет 6, 12:45", p.text)
    }

    @Test
    fun itAppearsExactlyThirtyMinutesBeforeTheFirstLesson() {
        val early = plan("07:29", 59) // за 30:01 до урока
        assertFalse(early.show)
        assertEquals(at(day, "07:30") + margin, early.refreshAtMs) // проснётся ровно к «за полчаса»

        assertTrue("ровно за 30:00 уже показываем", plan("07:30", 0).show)

        val onTime = plan("07:30", 2) // за 29:58: уже показываем
        assertTrue(onTime.show)
        assertEquals("Уроки ещё не начались", onTime.title)
        assertEquals(at(day, "08:00"), onTime.countdownEndMs)
        assertEquals(at(day, "08:00") + margin, onTime.refreshAtMs)
    }

    @Test
    fun theAlarmForTheLeadTimeLandsInsideTheWindow() {
        // будильник «за полчаса» сработал в refreshAtMs: к этому моменту уведомление уже должно показываться
        val early = plan("06:00")
        val shown = NotificationPlanner.plan(lessons, early.refreshAtMs, zone)
        assertTrue(shown.show)
    }

    @Test
    fun afterTheLastLessonItDisappearsAndWaitsForMidnight() {
        val p = plan("15:00")
        assertFalse(p.show)
        assertNull(p.countdownEndMs)
        assertEquals(at(tomorrow, "00:00") + margin, p.refreshAtMs)
    }

    @Test
    fun onADayOffAndWithoutDataThereIsNoNotification() {
        assertFalse(plan("10:00", date = "2026-11-21").show) // суббота
        assertFalse(NotificationPlanner.plan(emptyList(), at(day, "09:00"), zone).show)
    }

    @Test
    fun aDismissedNotificationStaysGoneForTheRestOfTheDayButReturnsTomorrow() {
        val dismissed = plan("09:00", dismissed = day)
        assertFalse(dismissed.show)
        assertEquals(at(tomorrow, "00:00") + margin, dismissed.refreshAtMs)

        // вчерашнее «смахнули» сегодня не действует
        assertTrue(plan("09:00", dismissed = "2026-11-18").show)
        // и завтра в то же время снова показывается
        assertTrue(plan("09:00", dismissed = day, date = tomorrow).show)
    }

    @Test
    fun theRefreshIsAlwaysInTheFutureAndWithinADay() {
        var minute = 0
        while (minute < 24 * 60) {
            val hhmm = String.format("%02d:%02d", minute / 60, minute % 60)
            val now = at(day, hhmm)
            val p = NotificationPlanner.plan(lessons, now, zone)
            assertTrue("в $hhmm обновление не в будущем", p.refreshAtMs > now)
            assertTrue("в $hhmm обновление дальше суток", p.refreshAtMs - now <= 24 * 3600_000L)
            if (p.show) assertTrue("в $hhmm у отсчёта нет цели", p.countdownEndMs != null && p.countdownEndMs!! > now)
            minute++
        }
    }

    @Test
    fun walkingThroughTheDayGivesTheExpectedShowHideSequence() {
        var now = at(day, "05:00")
        val steps = mutableListOf<String>()
        val midnight = at(tomorrow, "00:00")
        var guard = 0
        while (now < midnight) {
            val p = NotificationPlanner.plan(lessons, now, zone)
            steps.add(if (p.show) p.title else "—")
            now = p.refreshAtMs
            assertTrue("зациклились", ++guard < 40)
        }
        assertEquals(
            listOf(
                "—",                            // 05:00, рано: ждём 07:30
                "Уроки ещё не начались",        // 07:30
                "Урок 1 · Предмет 1", "Перемена",
                "Урок 2 · Предмет 2", "Перемена",
                "Урок 3 · Предмет 3", "Перемена",
                "Урок 4 · Предмет 4", "Перемена",
                "Урок 5 · Предмет 5", "Перемена",
                "Урок 6 · Предмет 6", "Перемена",
                "Урок 7 · Предмет 7",
                "—"                             // после последнего урока
            ),
            steps
        )
    }
}
