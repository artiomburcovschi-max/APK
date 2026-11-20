package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class LessonClockTest {

    private val zone = TimeZone.getTimeZone("Europe/Chisinau")
    private val day = "2026-11-19" // четверг

    // Сетка из настоящего расписания: 7 уроков, перемены по 10 минут, между 5-м и 6-м 20 минут
    private val grid = listOf(
        1 to ("08:00" to "08:45"), 2 to ("08:55" to "09:40"), 3 to ("09:50" to "10:35"),
        4 to ("10:45" to "11:30"), 5 to ("11:40" to "12:25"), 6 to ("12:45" to "13:30"), 7 to ("13:40" to "14:25")
    )

    private fun lesson(date: String, order: Int, start: String, end: String, subject: String = "Предмет $order") =
        Lesson(date, order, start, end, subject, "", "Учитель", "", emptyList())

    private fun school(date: String = day) = grid.map { (n, t) -> lesson(date, n, t.first, t.second) }

    private fun at(date: String, hhmm: String, seconds: Int = 0): Long =
        LessonClock.epoch(date, hhmm, zone)!! + seconds * 1000L

    private fun state(lessons: List<Lesson>, date: String, hhmm: String, sec: Int = 0) =
        LessonClock.stateAt(lessons, at(date, hhmm, sec), zone)

    // ---------- разбор времени ----------

    @Test
    fun minutesOfDayParsesOnlyRealTimes() {
        assertEquals(8 * 60 + 45, LessonClock.minutesOfDay("08:45"))
        assertEquals(8 * 60 + 5, LessonClock.minutesOfDay("8:05"))
        assertEquals(13 * 60, LessonClock.minutesOfDay(" 13.00 "))
        assertNull(LessonClock.minutesOfDay(""))
        assertNull(LessonClock.minutesOfDay("25:00"))
        assertNull(LessonClock.minutesOfDay("08:75"))
        assertNull(LessonClock.minutesOfDay("утро"))
    }

    @Test
    fun epochRespectsTheTimeZone() {
        // 08:00 в Кишинёве в ноябре (UTC+2) = 06:00 UTC
        val chisinau = LessonClock.epoch(day, "08:00", zone)!!
        val utc = LessonClock.epoch(day, "06:00", TimeZone.getTimeZone("UTC"))!!
        assertEquals(utc, chisinau)
    }

    // ---------- уроки на оси времени ----------

    @Test
    fun slotsAreSortedAndOnePerLessonNumber() {
        val lessons = listOf(
            lesson(day, 2, "08:55", "09:40", "Б"),
            lesson(day, 1, "08:00", "08:45"),
            lesson(day, 2, "08:55", "09:40", "А") // вторая запись на тот же номер (группа)
        )
        val slots = LessonClock.slots(lessons, day, zone)

        assertEquals(listOf(1, 2), slots.map { it.lesson.order })
        assertEquals("А", slots[1].lesson.subject) // из дублей берём первый по названию
    }

    @Test
    fun lessonsWithBrokenTimeAreSkipped() {
        val lessons = listOf(
            lesson(day, 1, "", ""),
            lesson(day, 2, "09:00", "08:00"), // конец раньше начала
            lesson(day, 3, "09:50", "10:35"),
            lesson("2026-11-20", 1, "08:00", "08:45") // другой день
        )
        assertEquals(listOf(3), LessonClock.slots(lessons, day, zone).map { it.lesson.order })
    }

    // ---------- состояния ----------

    @Test
    fun noLessonsAtAllMeansNoData() {
        assertEquals(ClockState.NoData, state(emptyList(), day, "10:00"))
    }

    @Test
    fun beforeTheFirstLesson() {
        val s = state(school(), day, "07:30") as ClockState.BeforeFirst
        assertEquals(1, s.first.lesson.order)
        assertEquals(30 * 60_000L, s.first.startMs - at(day, "07:30"))
    }

    @Test
    fun lessonStartIsInclusiveAndEndIsExclusive() {
        val lessons = school()

        val start = state(lessons, day, "08:00") as ClockState.InLesson
        assertEquals(1, start.current.lesson.order)

        val lastSecond = state(lessons, day, "08:44", 59) as ClockState.InLesson
        assertEquals(1, lastSecond.current.lesson.order)

        val breakStarts = state(lessons, day, "08:45") as ClockState.Break
        assertEquals(1, breakStarts.previous.lesson.order)
        assertEquals(2, breakStarts.next.lesson.order)
    }

    @Test
    fun inLessonKnowsTheNextOneAndTheLastLessonHasNone() {
        val mid = state(school(), day, "09:00") as ClockState.InLesson
        assertEquals(2, mid.current.lesson.order)
        assertEquals(3, mid.next!!.lesson.order)

        val last = state(school(), day, "14:00") as ClockState.InLesson
        assertEquals(7, last.current.lesson.order)
        assertNull(last.next)
    }

    @Test
    fun breakStartsExactlyAtTheEndOfTheLessonAndEndsAtTheNextStart() {
        val s = state(school(), day, "08:50") as ClockState.Break
        assertEquals(5 * 60_000L, s.next.startMs - at(day, "08:50"))
        assertTrue(state(school(), day, "08:55") is ClockState.InLesson)
    }

    @Test
    fun theLongBreakIsRecognisedByItsLength() {
        val s = state(school(), day, "12:30") as ClockState.Break
        assertEquals(20 * 60_000L, s.next.startMs - s.previous.endMs)
        assertEquals(6, s.next.lesson.order)
    }

    @Test
    fun afterTheLastLessonTheDayIsFinishedAndTheNextLessonIsTomorrow() {
        val lessons = school() + school("2026-11-20")
        val s = state(lessons, day, "15:00") as ClockState.Finished
        assertEquals(at("2026-11-20", "08:00"), s.next!!.startMs)
    }

    @Test
    fun finishedWithNothingAheadHasNoNextLesson() {
        val s = state(school(), day, "15:00") as ClockState.Finished
        assertNull(s.next)
    }

    @Test
    fun weekendIsADayOffAndPointsToMonday() {
        val friday = "2026-11-20"
        val monday = "2026-11-23"
        val lessons = school(friday) + school(monday)

        val s = state(lessons, "2026-11-21", "10:00") as ClockState.DayOff // суббота
        assertEquals(at(monday, "08:00"), s.next!!.startMs)
    }

    @Test
    fun todaysLessonsWithoutTimesAreNoDataNotADayOff() {
        val lessons = listOf(lesson(day, 1, "", ""), lesson(day, 2, "", ""))
        assertEquals(ClockState.NoData, state(lessons, day, "10:00"))
    }

    @Test
    fun theSameInstantIsDifferentLocalTimeInDifferentZones() {
        val lessons = school()
        val instant = at(day, "09:00") // 09:00 в Кишинёве
        val inChisinau = LessonClock.stateAt(lessons, instant, zone)
        val inUtc = LessonClock.stateAt(lessons, instant, TimeZone.getTimeZone("UTC")) // там 07:00: уроки ещё не начались

        assertTrue(inChisinau is ClockState.InLesson)
        assertTrue(inUtc is ClockState.BeforeFirst)
    }

    // ---------- когда состояние сменится ----------

    @Test
    fun nextBoundaryIsTheEndOfTheLessonOrTheBreak() {
        val lessons = school()

        val inLesson = state(lessons, day, "09:00")
        assertEquals(at(day, "09:40"), LessonClock.nextBoundary(inLesson, at(day, "09:00"), zone))

        val inBreak = state(lessons, day, "12:30")
        assertEquals(at(day, "12:45"), LessonClock.nextBoundary(inBreak, at(day, "12:30"), zone))

        val before = state(lessons, day, "07:00")
        assertEquals(at(day, "08:00"), LessonClock.nextBoundary(before, at(day, "07:00"), zone))
    }

    @Test
    fun withNothingToWaitForTheBoundaryIsMidnight() {
        val finished = state(school(), day, "15:00")
        assertEquals(at("2026-11-20", "00:00"), LessonClock.nextBoundary(finished, at(day, "15:00"), zone))
    }

    // ---------- тексты ----------

    @Test
    fun liveInfoInALesson() {
        val now = at(day, "09:00")
        val info = LessonClock.info(LessonClock.stateAt(school(), now, zone), now, zone)

        assertEquals("Урок 2 · Предмет 2", info.title)
        assertEquals(40 * 60_000L, info.countdownMs)
        assertEquals("до конца урока", info.caption)
        assertEquals("Дальше: урок 3 · Предмет 3, 09:50", info.nextLine)
        assertEquals(5f / 45f, info.progress!!, 0.001f)
    }

    @Test
    fun liveInfoInTheLastLessonSaysSo() {
        val now = at(day, "14:00")
        val info = LessonClock.info(LessonClock.stateAt(school(), now, zone), now, zone)
        assertEquals("Это последний урок на сегодня", info.nextLine)
    }

    @Test
    fun liveInfoOnABreak() {
        val now = at(day, "08:50")
        val info = LessonClock.info(LessonClock.stateAt(school(), now, zone), now, zone)

        assertEquals("Перемена", info.title)
        assertEquals(5 * 60_000L, info.countdownMs)
        assertEquals("до начала урока 2", info.caption)
        assertEquals("Дальше: урок 2 · Предмет 2, 08:55", info.nextLine)
        assertEquals(0.5f, info.progress!!, 0.001f) // 5 минут из 10
    }

    @Test
    fun aLongGapIsCalledAWindow() {
        val lessons = listOf(lesson(day, 1, "08:00", "08:45"), lesson(day, 3, "10:00", "10:45"))
        val now = at(day, "09:00")
        val info = LessonClock.info(LessonClock.stateAt(lessons, now, zone), now, zone)
        assertEquals("Окно", info.title)
    }

    @Test
    fun liveInfoBeforeAndAfterSchool() {
        val early = at(day, "07:30")
        val before = LessonClock.info(LessonClock.stateAt(school(), early, zone), early, zone)
        assertEquals("до первого урока", before.caption)
        assertEquals(30 * 60_000L, before.countdownMs)

        val lessons = school() + school("2026-11-20")
        val late = at(day, "15:00")
        val after = LessonClock.info(LessonClock.stateAt(lessons, late, zone), late, zone)
        assertNull(after.countdownMs)
        assertEquals("Следующий урок завтра в 08:00: Предмет 1", after.nextLine)
    }

    @Test
    fun nextLessonOnADistantDayNamesTheWeekday() {
        val lessons = school("2026-11-20") + school("2026-11-23")
        val now = at("2026-11-21", "10:00")
        val info = LessonClock.info(LessonClock.stateAt(lessons, now, zone), now, zone)

        assertEquals("Сегодня уроков нет", info.title)
        assertEquals("Следующий урок в понедельник в 08:00: Предмет 1", info.nextLine)
    }

    @Test
    fun remainingTimeIsRoundedUpAndFormatted() {
        assertEquals("00:01", LessonClock.formatRemaining(1))        // ещё не ноль
        assertEquals("00:00", LessonClock.formatRemaining(0))
        assertEquals("00:00", LessonClock.formatRemaining(-5_000))   // отрицательного не показываем
        assertEquals("12:34", LessonClock.formatRemaining((12 * 60 + 34) * 1000L))
        assertEquals("59:59", LessonClock.formatRemaining((59 * 60 + 59) * 1000L))
        assertEquals("1:05:09", LessonClock.formatRemaining((3600 + 5 * 60 + 9) * 1000L))
    }


    @Test
    fun thirtyMinutesIsAlreadyAWindowButTwentyNineIsStillABreak() {
        fun title(gapEnd: String): String {
            val lessons = listOf(lesson(day, 1, "08:00", "08:45"), lesson(day, 2, gapEnd, "10:45"))
            val now = at(day, "08:50")
            return LessonClock.info(LessonClock.stateAt(lessons, now, zone), now, zone).title
        }
        assertEquals("Окно", title("09:15"))      // ровно 30 минут
        assertEquals("Перемена", title("09:14"))  // 29 минут

        val rows = LessonClock.dayRows(listOf(lesson(day, 1, "08:00", "08:45"), lesson(day, 2, "09:15", "10:00")))
        assertTrue((rows[1] as DayRow.GapRow).isWindow)
    }

    // ---------- список дня ----------

    @Test
    fun dayRowsPutBreaksBetweenLessons() {
        val rows = LessonClock.dayRows(school())
        val gaps = rows.filterIsInstance<DayRow.GapRow>()

        assertEquals(13, rows.size) // 7 уроков + 6 промежутков
        assertEquals(listOf(10, 10, 10, 10, 20, 10), gaps.map { it.minutes })
        assertTrue(gaps.none { it.isWindow })
        assertEquals("08:00–08:45", (rows.first() as DayRow.LessonRow).timeText)
    }

    @Test
    fun dayRowsMarkLongGapsAsWindowsAndIgnoreParallelGroups() {
        val lessons = listOf(
            lesson(day, 1, "08:00", "08:45", "А"),
            lesson(day, 1, "08:00", "08:45", "Б"), // параллельная группа: промежутка между ними нет
            lesson(day, 2, "10:00", "10:45")
        )
        val rows = LessonClock.dayRows(lessons)

        assertEquals(4, rows.size)
        val gap = rows.filterIsInstance<DayRow.GapRow>().single()
        assertEquals(75, gap.minutes)
        assertTrue(gap.isWindow)
    }

    @Test
    fun dayRowsKeepLessonsWithoutTimeAndSkipGapsAroundThem() {
        val rows = LessonClock.dayRows(listOf(lesson(day, 1, "", ""), lesson(day, 2, "08:55", "09:40")))
        assertEquals(2, rows.size)
        assertEquals("", (rows[0] as DayRow.LessonRow).timeText)
    }

    // ---------- неделя ----------

    @Test
    fun weekDatesStartOnMonday() {
        assertEquals(
            listOf("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20", "2026-11-21", "2026-11-22"),
            LessonClock.weekDates("2026-11-19")
        )
        assertEquals("2026-11-23", LessonClock.weekDates("2026-11-19", 1).first())
        assertEquals("2026-11-09", LessonClock.weekDates("2026-11-22", -1).first()) // воскресенье относится к своей неделе
    }

    @Test
    fun weekdayNumbers() {
        assertEquals(1, LessonClock.weekdayNumber("2026-11-16")) // понедельник
        assertEquals(4, LessonClock.weekdayNumber("2026-11-19")) // четверг
        assertEquals(7, LessonClock.weekdayNumber("2026-11-22")) // воскресенье
    }

    @Test
    fun weekSpansMonthAndYearBoundaries() {
        assertEquals(listOf("2026-12-28", "2026-12-29", "2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02", "2027-01-03"),
            LessonClock.weekDates("2027-01-01"))
    }
}
