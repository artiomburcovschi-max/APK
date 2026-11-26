package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {

    private val today = "2026-11-25"

    private fun lesson(date: String, order: Int, subject: String, homework: String) =
        Lesson(date, order, "08:00", "08:45", subject, "", "Учитель", homework, emptyList())

    private fun feed(n: Int) = (1..n).map { FeedItem("Предмет $it", "1", it, 1_000L - it, null, false) }

    @Test
    fun recentGradesAreCutToFiveKeepingTheNewestFirst() {
        val shown = HomeLogic.recentToShow(feed(8))
        assertEquals(5, shown.size)
        assertEquals(listOf(1, 2, 3, 4, 5), shown.map { it.score })
    }

    @Test
    fun aShortOrEmptyFeedIsShownAsIs() {
        assertEquals(3, HomeLogic.recentToShow(feed(3)).size)
        assertEquals(5, HomeLogic.recentToShow(feed(5)).size)
        assertTrue(HomeLogic.recentToShow(emptyList()).isEmpty())
    }

    @Test
    fun homeworkSoonStartsTodayAndGoesForwardByDay() {
        val extras = PortalExtras(
            lessons = listOf(
                lesson("2026-11-26", 1, "Fizică", "§12"),
                lesson("2026-11-25", 2, "Matematică", "упр. 5"),
                lesson("2026-11-27", 3, "Istorie", "")
            )
        )
        val hw = HomeLogic.homeworkSoon(extras, today)
        assertEquals(
            listOf(
                HomeHw(DiaryLogic.dayTitle("2026-11-25"), "Matematică", "упр. 5"),
                HomeHw(DiaryLogic.dayTitle("2026-11-26"), "Fizică", "§12")
            ),
            hw
        )
    }

    @Test
    fun yesterdayAndOlderHomeworkIsNotShown() {
        val extras = PortalExtras(
            lessons = listOf(lesson("2026-11-24", 1, "Fizică", "старое"), lesson("2026-11-20", 1, "Chimie", "ещё старее"))
        )
        assertTrue(HomeLogic.homeworkSoon(extras, today).isEmpty())
    }

    @Test
    fun homeworkIsLimitedToFourWithoutCuttingTheOrder() {
        val lessons = (1..6).map { lesson("2026-11-25", it, "Предмет $it", "задание $it") }
        val hw = HomeLogic.homeworkSoon(PortalExtras(lessons = lessons), today)
        assertEquals(4, hw.size)
        assertEquals(listOf("Предмет 1", "Предмет 2", "Предмет 3", "Предмет 4"), hw.map { it.subject })
        assertEquals("ровно лимит пропускает все", 4, HomeLogic.homeworkSoon(PortalExtras(lessons = lessons.take(4)), today).size)
        assertEquals("лимит можно задать", 2, HomeLogic.homeworkSoon(PortalExtras(lessons = lessons), today, max = 2).size)
    }

    @Test
    fun theSameTaskFromTheCalendarAndTheTaskListIsShownOnce() {
        val extras = PortalExtras(
            lessons = listOf(lesson("2026-11-26", 1, "Fizică", "§12")),
            homework = listOf(HomeworkItem("2026-11-26", "Fizică", "§12"), HomeworkItem("2026-11-26", "Chimie", "тест"))
        )
        val hw = HomeLogic.homeworkSoon(extras, today)
        assertEquals(listOf("Chimie", "Fizică"), hw.map { it.subject }) // внутри дня по алфавиту, повтор убран
    }

    @Test
    fun noHomeworkGivesAnEmptyList() {
        assertTrue(HomeLogic.homeworkSoon(PortalExtras(), today).isEmpty())
    }
}
