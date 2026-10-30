package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PortalScreensTest {

    private val page: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("portal_main_anon.html")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun lesson(date: String, order: Int = 1, subject: String = "Matematică", hw: String = "", grades: List<Int> = emptyList()) =
        Lesson(date, order, "08:00", "08:45", subject, "", "Popa Elena", hw, grades)

    // ---------- дневник ----------

    @Test
    fun datesAreSortedAndDistinct() {
        val lessons = listOf(lesson("2026-10-05", 2), lesson("2026-10-02"), lesson("2026-10-05", 1))

        assertEquals(listOf("2026-10-02", "2026-10-05"), DiaryLogic.dates(lessons))
    }

    @Test
    fun diaryOpensOnTheNearestDayFromToday() {
        val dates = listOf("2026-10-01", "2026-10-05", "2026-10-09")

        assertEquals("2026-10-05", DiaryLogic.initialDate(dates, "2026-10-05")) // сегодня есть уроки
        assertEquals("2026-10-09", DiaryLogic.initialDate(dates, "2026-10-06")) // сегодня нет: ближайший вперёд
        assertEquals("2026-10-09", DiaryLogic.initialDate(dates, "2026-12-31")) // впереди нет: последний известный
        assertNull(DiaryLogic.initialDate(emptyList(), "2026-10-05"))
    }

    @Test
    fun todayIsTakenInThePhonesTimeZone() {
        val moment = 1_761_517_200_000L // 2025-10-26 23:00 UTC
        assertEquals("2025-10-26", DiaryLogic.todayIso(moment, TimeZone.getTimeZone("UTC")))
        assertEquals("2025-10-27", DiaryLogic.todayIso(moment, TimeZone.getTimeZone("Europe/Chisinau"))) // у них уже следующий день
    }

    @Test
    fun dayTitleHasTheWeekday() {
        assertEquals("Понедельник, 05.10.2026", DiaryLogic.dayTitle("2026-10-05"))
        assertEquals("Воскресенье, 01.11.2026", DiaryLogic.dayTitle("2026-11-01"))
        assertEquals("Четверг, 03.09.2026", DiaryLogic.dayTitle("2026-09-03"))
        assertEquals("мусор", DiaryLogic.dayTitle("мусор"))
    }

    @Test
    fun summaryCountsLessonsGradesAndHomework() {
        val day = listOf(lesson("2026-10-05", 1, hw = "x"), lesson("2026-10-05", 2, grades = listOf(9)), lesson("2026-10-05", 3))

        assertEquals("3 урока · оценок: 1 · с ДЗ: 1", DiaryLogic.summary(day))
        assertEquals("1 урок", DiaryLogic.summary(listOf(lesson("2026-10-05"))))
        assertEquals("7 уроков", DiaryLogic.summary((1..7).map { lesson("2026-10-05", it) }))
    }

    // ---------- домашка ----------

    @Test
    fun homeworkIsSplitIntoSoonAndRecent() {
        val extras = PortalExtras(
            lessons = listOf(
                lesson("2026-10-23", 3, "Matematică", hw = "стр. 54"),
                lesson("2026-10-23", 5, "Matematică", hw = "стр. 54"),   // тот же предмет и текст: один раз
                lesson("2026-10-21", 1, "Chimie", hw = "§5"),
                lesson("2026-09-01", 1, "Fizica", hw = "очень давно")     // старше 14 дней: не показываем
            ),
            homework = listOf(HomeworkItem("2026-11-02", "Matematică", "стр. 61"))
        )

        val rows = HomeworkLogic.rows(extras, "2026-10-27")

        assertEquals(
            listOf(
                HwRow.Section("Скоро"),
                HwRow.DateHeader("Понедельник, 02.11.2026"),
                HwRow.Item("Matematică", "стр. 61"),
                HwRow.Section("Последние 14 дней"),
                HwRow.DateHeader("Пятница, 23.10.2026"),
                HwRow.Item("Matematică", "стр. 54"),
                HwRow.DateHeader("Среда, 21.10.2026"),
                HwRow.Item("Chimie", "§5")
            ),
            rows
        )
    }

    @Test
    fun todaysHomeworkCountsAsSoon() {
        val rows = HomeworkLogic.rows(PortalExtras(homework = listOf(HomeworkItem("2026-10-27", "Chimie", "§1"))), "2026-10-27")

        assertEquals(HwRow.Section("Скоро"), rows.first())
    }

    @Test
    fun noHomeworkGivesNoRows() {
        assertTrue(HomeworkLogic.rows(PortalExtras(), "2026-10-27").isEmpty())
    }

    @Test
    fun homeworkFromTheRealPageShowsUp() {
        val extras = PortalExtras(lessons = AgendaParser.parseLessons(page), homework = AgendaParser.parseHomework(page))

        val rows = HomeworkLogic.rows(extras, "2026-10-05")

        assertTrue(rows.any { it == HwRow.Item("Matematică", "Решение с.61 №1- №5") }) // из списка «Sarcini»
        assertTrue(rows.filterIsInstance<HwRow.Item>().size > 5)                          // и из календаря
    }

    // ---------- пропуски ----------

    @Test
    fun absenceScreenFromTheRealPage() {
        val screen = AbsenceScreens.build(AbsenceParser.parse(page))!!

        assertEquals("Пропущено уроков: 41 (не мотивированных 41, мотивированных 0)", screen.totalLine)
        assertEquals(
            listOf(
                "Семестр 1: не мотивированных 41 · мотивированных 0 · всего 41",
                "Семестр 2: не мотивированных 0 · мотивированных 0 · всего 0"
            ),
            screen.semesterLines
        )
        assertEquals("Посещаемость: 87%", screen.presenceLine)
        assertNull(screen.listNote) // список полный
        assertEquals(41, screen.days.sumOf { it.entries.size })
        assertEquals(6, screen.days.size)
        // свежие сверху: 10 сентября первым, 3 сентября последним (в тот день пропущено 6 уроков из 7, пятого нет)
        assertEquals("Четверг, 10.09.2026 · 7 уроков", screen.days.first().title)
        assertEquals("Четверг, 03.09.2026 · 6 уроков", screen.days.last().title)
        assertEquals(listOf(1, 2, 3, 4, 6, 7), screen.days.last().entries.map { it.lessonNo })
    }

    @Test
    fun subjectsAreSortedByTheNumberOfAbsences() {
        val screen = AbsenceScreens.build(AbsenceParser.parse(page))!!

        val totals = screen.subjects.map { it.semester1.total + it.semester2.total }
        assertEquals(totals.sortedDescending(), totals)
        assertTrue(totals.all { it > 0 })
    }

    @Test
    fun incompleteListIsReportedHonestly() {
        val data = AbsenceParser.parse(page)!!

        val none = AbsenceScreens.build(data.copy(entries = emptyList()))!!
        assertTrue(none.listNote!!.contains("не загрузился"))

        val part = AbsenceScreens.build(data.copy(entries = data.entries.take(10)))!!
        assertTrue(part.listNote!!.contains("10 из 41"))
    }

    @Test
    fun noDataGivesNoScreen() {
        assertNull(AbsenceScreens.build(null))
    }

    @Test
    fun zeroAbsencesNeedNoNote() {
        val data = AbsenceData(emptyList(), SemesterAbsences(0, 0, 0), SemesterAbsences(0, 0, 0), emptyList(), 100)

        val screen = AbsenceScreens.build(data)!!

        assertNull(screen.listNote)
        assertEquals("Посещаемость: 100%", screen.presenceLine)
        assertTrue(screen.days.isEmpty())
    }

    @Test
    fun statusLabels() {
        assertEquals("мотивирован", AbsenceScreens.statusLabel(AbsenceStatus.MOTIVATED))
        assertEquals("не мотивирован", AbsenceScreens.statusLabel(AbsenceStatus.UNMOTIVATED))
        assertEquals("статус неизвестен", AbsenceScreens.statusLabel(AbsenceStatus.UNKNOWN))
    }

    // ---------- учителя ----------

    @Test
    fun teachersAreLookedUpIgnoringCaseAndSpaces() {
        val dosar = DosarParser.parse(page)
        val map = TeacherLookup.bySubject(dosar)

        assertEquals(3, TeacherLookup.forSubject(map, "  istoria românilor  și universală ").size)
        assertEquals(1, TeacherLookup.forSubject(map, "Matematică").size)
        assertTrue(TeacherLookup.forSubject(map, "Неизвестный предмет").isEmpty())
        assertTrue(TeacherLookup.bySubject(null).isEmpty())
    }
}
