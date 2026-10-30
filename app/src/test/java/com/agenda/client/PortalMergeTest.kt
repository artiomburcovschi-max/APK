package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalMergeTest {

    private fun lesson(date: String, order: Int, subject: String = "Matematică", hw: String = "", grades: List<Int> = emptyList()) =
        Lesson(date, order, "08:00", "08:45", subject, "", "Popa Elena", hw, grades)

    private fun parsed(
        lessons: List<Lesson> = emptyList(), homework: List<HomeworkItem> = emptyList(),
        dosar: DosarInfo? = null, absences: AbsenceData? = null
    ) = ParsedPortal(lessons, homework, dosar, absences)

    // ---------- уроки ----------

    @Test
    fun newMonthIsAddedToTheHistory() {
        val old = listOf(lesson("2026-09-30", 1), lesson("2026-10-01", 1))
        val fresh = listOf(lesson("2026-10-28", 1), lesson("2026-10-29", 1))

        val merged = PortalMerge.mergeLessons(old, fresh)

        assertEquals(listOf("2026-09-30", "2026-10-01", "2026-10-28", "2026-10-29"), merged.map { it.date })
    }

    @Test
    fun daysSeenAgainAreReplacedWholesale() {
        val old = listOf(lesson("2026-10-05", 1, hw = ""), lesson("2026-10-05", 2, hw = "старое"))
        val fresh = listOf(lesson("2026-10-05", 1, hw = "новое ДЗ", grades = listOf(9)))

        val merged = PortalMerge.mergeLessons(old, fresh)

        assertEquals(1, merged.size) // второй урок убрали из расписания
        assertEquals("новое ДЗ", merged.single().homework)
        assertEquals(listOf(9), merged.single().grades)
    }

    @Test
    fun emptyParseNeverWipesTheHistory() {
        val old = listOf(lesson("2026-10-05", 1))

        assertEquals(old, PortalMerge.mergeLessons(old, emptyList()))
    }

    @Test
    fun veryOldLessonsAreDropped() {
        val old = listOf(lesson("2025-01-10", 1), lesson("2026-10-01", 1))
        val fresh = listOf(lesson("2026-10-20", 1))

        assertEquals(listOf("2026-10-01", "2026-10-20"), PortalMerge.mergeLessons(old, fresh).map { it.date })
    }

    @Test
    fun shiftIsoCrossesMonthsAndYears() {
        assertEquals("2026-09-30", PortalMerge.shiftIso("2026-10-01", -1))
        assertEquals("2025-12-31", PortalMerge.shiftIso("2026-01-01", -1))
        assertEquals("2024-02-29", PortalMerge.shiftIso("2024-03-01", -1)) // високосный год
        assertNull(PortalMerge.shiftIso("мусор", 1))
    }

    // ---------- остальные блоки ----------

    @Test
    fun homeworkIsReplacedWhenThePageLoadedFully() {
        val old = PortalExtras(homework = listOf(HomeworkItem("2026-11-02", "Matematică", "старое")))
        val newHw = listOf(HomeworkItem("2026-11-03", "Fizica", "новое"))

        assertEquals(newHw, PortalMerge.merge(old, parsed(lessons = listOf(lesson("2026-10-05", 1)), homework = newHw), 1L).homework)
        // календарь на месте, а заданий нет: значит, их действительно нет
        assertTrue(PortalMerge.merge(old, parsed(lessons = listOf(lesson("2026-10-05", 1))), 1L).homework.isEmpty())
        // страница пустая (не загрузилась): оставляем прежнее
        assertEquals(old.homework, PortalMerge.merge(old, parsed(), 1L).homework)
    }

    @Test
    fun dosarKeepsOldTeachersIfTheTableIsMissing() {
        val teachers = listOf(TeacherEntry("Matematică", "Popa Elena"))
        val old = PortalExtras(dosar = DosarInfo("10A", "Popescu Maria", "Liceul", teachers))

        val merged = PortalMerge.merge(old, parsed(dosar = DosarInfo("10B", null, "Liceul", emptyList())), 1L).dosar!!

        assertEquals("10B", merged.className) // обновилось
        assertEquals(teachers, merged.teachers) // таблица не пришла: остались прежние
    }

    @Test
    fun missingDosarKeepsTheOldOne() {
        val old = PortalExtras(dosar = DosarInfo("10A", null, null, emptyList()))

        assertEquals(old.dosar, PortalMerge.merge(old, parsed(), 1L).dosar)
    }

    private fun absence(n: Int) = AbsenceEntry("2026-09-0${n}", 1, "Chimie", AbsenceStatus.UNMOTIVATED, n)

    @Test
    fun completeAbsenceListReplacesTheOldOne() {
        val old = AbsenceData(listOf(absence(1)), SemesterAbsences(0, 1, 1), null, emptyList(), 90)
        val fresh = AbsenceData(listOf(absence(1), absence(2)), SemesterAbsences(0, 2, 2), null, emptyList(), 85)

        val merged = PortalMerge.merge(PortalExtras(absences = old), parsed(absences = fresh), 1L).absences!!

        assertEquals(2, merged.entries.size)
        assertEquals(85, merged.presencePercent)
    }

    @Test
    fun summaryUpdatesEvenIfTheListHasNotRenderedYet() {
        // в сводке стало 3 пропуска, а подробный список ещё не нарисовался: сводку берём новую, список прежний
        val old = AbsenceData(listOf(absence(1), absence(2)), SemesterAbsences(0, 2, 2), null, emptyList(), 90)
        val fresh = AbsenceData(emptyList(), SemesterAbsences(0, 3, 3), null, emptyList(), 80)

        val merged = PortalMerge.merge(PortalExtras(absences = old), parsed(absences = fresh), 1L).absences!!

        assertEquals(3, merged.semester1!!.total)
        assertEquals(2, merged.entries.size)
        assertEquals(false, AbsenceMath.listIsComplete(merged)) // экран честно скажет, что список неполный
    }

    @Test
    fun updatedAtIsRecorded() {
        assertEquals(12345L, PortalMerge.merge(PortalExtras(), parsed(), 12345L).updatedAt)
    }

    // ---------- точные даты оценок ----------

    @Test
    fun gradeEventsAreNewestFirstAndOrderedWithinADay() {
        val lessons = listOf(
            lesson("2026-10-05", 3, "Matematică", grades = listOf(9)),
            lesson("2026-10-05", 6, "Matematică", grades = listOf(10)),
            lesson("2026-10-02", 1, "Chimie", grades = listOf(8)),
            lesson("2026-10-02", 2, "Chimie")
        )

        val events = LessonDates.gradeEvents(lessons)

        assertEquals(
            listOf(
                RecentEval("2026-10-05", "Matematică", 10),
                RecentEval("2026-10-05", "Matematică", 9),
                RecentEval("2026-10-02", "Chimie", 8)
            ),
            events
        )
    }

    @Test
    fun gradeEventsWorkWithFillDatesAlignment() {
        // fillDates разворачивает события и сортирует по дате: получается хронологический порядок, как в таблице оценок
        val lessons = listOf(
            lesson("2026-10-05", 3, "Matematică", grades = listOf(9)),
            lesson("2026-10-05", 6, "Matematică", grades = listOf(10)),
            lesson("2026-10-12", 2, "Matematică", grades = listOf(8))
        )
        val chrono = LessonDates.gradeEvents(lessons).reversed().sortedBy { it.date }

        val dates = GradeDates.alignTail(listOf(7, 9, 10, 8), chrono)

        assertEquals(mapOf(3 to "2026-10-12", 2 to "2026-10-05", 1 to "2026-10-05"), dates)
    }

    @Test
    fun pageRecentOnlyFillsDatesWithoutLessons() {
        val lessons = listOf(lesson("2026-10-05", 3, "Matematică", grades = listOf(9)))
        val pageRecent = listOf(
            RecentEval("2026-10-05", "Matematică", 9),   // дата уже есть в календаре: берём оттуда
            RecentEval("2026-09-20", "Chimie", 7)        // календаря за эту дату нет: добавляем
        )

        val merged = LessonDates.mergeRecent(lessons, pageRecent)

        assertEquals(listOf(RecentEval("2026-10-05", "Matematică", 9), RecentEval("2026-09-20", "Chimie", 7)), merged)
    }

    @Test
    fun withoutLessonsTheOldBehaviourIsUnchanged() {
        val pageRecent = listOf(RecentEval("2026-10-05", "Matematică", 9), RecentEval("2026-10-02", "Chimie", 8))

        assertEquals(pageRecent, LessonDates.mergeRecent(emptyList(), pageRecent))
    }
}
