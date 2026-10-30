package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Парсеры страницы портала. Образец portal_main_anon.html это настоящая главная страница родителя,
 * обезличенная (имена, личные коды, адрес и ссылки заменены) и урезанная до четырёх учебных дней.
 */
class PortalParsersTest {

    private val page: String by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("portal_main_anon.html")
            ?: error("нет portal_main_anon.html в test/resources")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    // ---------- календарь ----------

    @Test
    fun lessonsAreReadFromEveryDayCell() {
        val lessons = AgendaParser.parseLessons(page)

        assertEquals(28, lessons.size) // 4 дня по 7 уроков
        assertEquals(listOf("2026-09-28", "2026-10-02", "2026-10-05", "2026-10-06"), lessons.map { it.date }.distinct())
        assertEquals((1..7).toList(), lessons.filter { it.date == "2026-10-05" }.map { it.order })
    }

    @Test
    fun lessonFieldsAreFilled() {
        val lesson = AgendaParser.parseLessons(page).first { it.date == "2026-09-28" && it.order == 2 }

        assertEquals("Limba și literatura română, alolingvi", lesson.subject)
        assertEquals("08:55", lesson.start)
        assertEquals("09:40", lesson.end)
        assertEquals(listOf(8), lesson.grades)
        assertEquals("Repetarea subiectelor studiate la unitate.", lesson.homework)
        assertTrue(lesson.teacher.isNotEmpty())
    }

    @Test
    fun gradesKeepTheirExactDates() {
        val graded = AgendaParser.parseLessons(page).filter { it.grades.isNotEmpty() }

        assertEquals(8, graded.size)
        // 5 октября: три десятки на 3-м, 6-м и 7-м уроках
        assertEquals(listOf(3, 6, 7), graded.filter { it.date == "2026-10-05" }.map { it.order })
        assertEquals(listOf(10, 10, 10), graded.filter { it.date == "2026-10-05" }.map { it.grades.single() })
    }

    @Test
    fun multilineHomeworkKeepsLineBreaks() {
        val hw = AgendaParser.parseLessons(page).map { it.homework }.first { it.contains("\n") }

        assertTrue(hw.startsWith("Разработка и представление проектов:"))
        assertTrue(hw.lines().size >= 2)
    }

    @Test
    fun lessonsWithoutHomeworkHaveEmptyText() {
        val lessons = AgendaParser.parseLessons(page)

        assertEquals(24, lessons.count { it.homework.isNotEmpty() })
        assertTrue(lessons.filter { it.homework.isEmpty() }.all { it.homework == "" })
    }

    @Test
    fun nonNumericAndOutOfRangeGradesAreIgnored() {
        val html = """
            <table><tbody><tr><td data-date="2026-10-01"><div class="lesson-card">
              <span class="lesson-order">1</span><div class="lesson-time"><span>08:00</span><span>08:45</span></div>
              <div class="lesson-text-wrap">Matematică</div><div class="lesson-teacher">Popa Elena</div>
              <span class="lesson-grade">N</span><span class="lesson-grade">11</span><span class="lesson-grade">0</span>
              <span class="lesson-grade">9</span>
            </div></td></tr></tbody></table>
        """.trimIndent()

        assertEquals(listOf(9), AgendaParser.parseLessons(html).single().grades)
    }

    @Test
    fun cardWithoutADateOrSubjectIsSkipped() {
        val html = """
            <div class="lesson-card"><span class="lesson-order">1</span><div class="lesson-text-wrap">Fără dată</div></div>
            <table><tbody><tr><td data-date="2026-10-01"><div class="lesson-card"><span class="lesson-order">1</span></div></td></tr></tbody></table>
        """.trimIndent()

        assertTrue(AgendaParser.parseLessons(html).isEmpty())
    }

    // ---------- список домашних заданий ----------

    @Test
    fun homeworkListIsReadFromTheHiddenField() {
        val hw = AgendaParser.parseHomework(page)

        assertEquals(listOf(HomeworkItem("2026-11-02", "Matematică", "Решение с.61 №1- №5")), hw)
    }

    @Test
    fun homeworkListGroupsItemsByDate() {
        val html = """<ul id="homework_list"><b>02.11.2026</b><li><span>Matematică:</span> <span>№1</span></li>
            <li><span>Fizica:</span> <span>§5</span></li><b>03.11.2026</b><li><span>Chimie:</span> <span>№2</span></li></ul>"""

        val hw = AgendaParser.parseHomework(html)

        assertEquals(
            listOf(
                HomeworkItem("2026-11-02", "Matematică", "№1"),
                HomeworkItem("2026-11-02", "Fizica", "§5"),
                HomeworkItem("2026-11-03", "Chimie", "№2")
            ),
            hw
        )
    }

    @Test
    fun homeworkWithoutAReadableDateOrBlockIsIgnored() {
        assertTrue(AgendaParser.parseHomework("<html></html>").isEmpty())
        assertTrue(AgendaParser.parseHomework("""<ul id="homework_list"><b>скоро</b><li><span>A:</span><span>x</span></li></ul>""").isEmpty())
    }

    // ---------- досье ----------

    @Test
    fun dosarGivesClassFormTeacherAndSchool() {
        val dosar = DosarParser.parse(page)!!

        assertEquals("10A", dosar.className)
        assertEquals("Popescu Maria", dosar.formTeacher) // в поле лежит ЗАГЛАВНЫМИ: приводим к нормальному виду
        assertEquals("Liceul Teoretic „Exemplu”", dosar.institution)
    }

    @Test
    fun dosarListsTeachersPerSubjectIncludingSeveralForOne() {
        val teachers = DosarParser.parse(page)!!.teachers

        assertEquals(16, teachers.size)
        assertEquals(14, teachers.map { it.subject }.distinct().size)
        assertEquals(3, teachers.count { it.subject == "Istoria românilor și universală" })
    }

    @Test
    fun teachersFromLessonsMatchTheDosar() {
        val dosar = DosarParser.parse(page)!!.teachers.map { it.subject to it.teacher }.toSet()

        for (lesson in AgendaParser.parseLessons(page)) {
            assertTrue("${lesson.subject} / ${lesson.teacher}", (lesson.subject to lesson.teacher) in dosar)
        }
    }

    @Test
    fun dosarDoesNotReadPersonalFields() {
        // в модель просто нет полей для даты рождения, личного кода и адреса, а в образце нет и самих значений
        val fields = DosarInfo::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(setOf("className", "formTeacher", "institution", "teachers"), fields)
    }

    @Test
    fun dosarIsNullOnAPageWithoutIt() {
        assertNull(DosarParser.parse("<html><body>пусто</body></html>"))
    }

    @Test
    fun prettyNameFixesCapsOnly() {
        assertEquals("Ionescu Ana", prettyName("IONESCU ANA"))
        assertEquals("Ion-Vasile Popa", prettyName("ION-VASILE POPA"))
        assertEquals("Popa Elena", prettyName("Popa Elena"))
        assertEquals("Popa  Elena".replace("  ", " "), prettyName("Popa  Elena".replace("  ", " ")))
        assertEquals("", prettyName("   "))
    }

    // ---------- пропуски ----------

    @Test
    fun absenceSummaryComesFromHiddenFields() {
        val data = AbsenceParser.parse(page)!!

        assertEquals(SemesterAbsences(0, 41, 41), data.semester1)
        assertEquals(SemesterAbsences(0, 0, 0), data.semester2)
        assertEquals(87, data.presencePercent)
    }

    @Test
    fun absenceListIsReadLessonByLesson() {
        val data = AbsenceParser.parse(page)!!

        assertEquals(41, data.entries.size)
        val first = data.entries.first()
        assertEquals("2026-09-03", first.date)
        assertEquals(1, first.lessonNo)
        assertEquals("Educație pentru societate", first.subject)
        assertEquals(AbsenceStatus.UNMOTIVATED, first.status)
        assertEquals(1, first.cumulative)
        assertEquals(41, data.entries.last().cumulative)
    }

    @Test
    fun nemotivatIsNotMistakenForMotivat() {
        // слово «motivat» лежит ВНУТРИ «nemotivat»: поиск подстроки посчитал бы всё мотивированным
        assertEquals(AbsenceStatus.UNMOTIVATED, AbsenceMath.statusOf("nemotivat"))
        assertEquals(AbsenceStatus.MOTIVATED, AbsenceMath.statusOf("motivat"))
        assertEquals(AbsenceStatus.MOTIVATED, AbsenceMath.statusOf(" Motivată "))
        assertEquals(AbsenceStatus.UNMOTIVATED, AbsenceMath.statusOf("NEMOTIVATĂ"))
        assertEquals(AbsenceStatus.UNKNOWN, AbsenceMath.statusOf(""))
        assertEquals(AbsenceStatus.UNKNOWN, AbsenceMath.statusOf("în așteptare"))
        assertNotEquals(AbsenceStatus.MOTIVATED, AbsenceMath.statusOf("nemotivat"))
    }

    @Test
    fun absencesPerSubjectAddUpToTheTotal() {
        val data = AbsenceParser.parse(page)!!

        assertEquals(14, data.bySubject.size)
        assertEquals(41, data.bySubject.sumOf { it.semester1.total })
        assertEquals(0, data.bySubject.sumOf { it.semester2.total })
    }

    @Test
    fun listIsCompleteOnlyWhenItMatchesTheSummary() {
        val data = AbsenceParser.parse(page)!!
        assertTrue(AbsenceMath.listIsComplete(data))

        // страница ещё не дорисовала список: сводка есть, записей нет
        assertFalse(AbsenceMath.listIsComplete(data.copy(entries = emptyList())))
        assertFalse(AbsenceMath.listIsComplete(data.copy(entries = data.entries.take(10))))
    }

    @Test
    fun entryTitlesSurviveDifferentRomanianSpellings() {
        // у ț и ș бывает запятая и бывает седиль; портал мог поменять написание
        val html = """
            <div class="absence-field">
              <div class="field-group"><span class="field-title">Data lecţiei:</span><span class="field-value">03.09.2026</span></div>
              <div class="field-group"><span class="field-title">Lecţia:</span><span class="field-value">4</span></div>
              <div class="field-group"><span class="field-title">Disciplina:</span><span class="field-value">Chimie</span></div>
              <div class="field-group"><span class="field-title">Absenţă:</span><span class="field-value motivat">motivat</span></div>
              <div class="field-group"><span class="field-title">Absenţe cumulative:</span><span class="field-value">7</span></div>
            </div>
        """.trimIndent()

        val entry = AbsenceParser.parse(html)!!.entries.single()

        assertEquals(AbsenceEntry("2026-09-03", 4, "Chimie", AbsenceStatus.MOTIVATED, 7), entry)
    }

    @Test
    fun brokenEntriesAreSkippedAndEmptyPageGivesNull() {
        val html = """
            <div class="absence-field"><div class="field-group"><span class="field-title">Disciplina:</span><span class="field-value">Chimie</span></div></div>
        """.trimIndent()

        assertNull(AbsenceParser.parse(html))
        assertNull(AbsenceParser.parse("<html></html>"))
    }

    @Test
    fun semesterIsTakenFromTheDate() {
        assertEquals(1, AbsenceMath.semesterOf("2026-09-03"))
        assertEquals(1, AbsenceMath.semesterOf("2026-12-31"))
        assertEquals(2, AbsenceMath.semesterOf("2027-01-10"))
        assertEquals(2, AbsenceMath.semesterOf("2027-05-30"))
    }

    @Test
    fun duplicateLessonCardsAreMergedAndAbsenceMarkIsNotAGrade() {
        val html = """
            <table><tr><td data-date="2026-09-01">
              <div class="lesson-card"><div class="lesson-order">4</div><span class="lesson-text-wrap">Matematică</span></div>
              <div class="lesson-card"><div class="lesson-order">4</div><span class="lesson-text-wrap">Matematică</span>
                <span class="lesson-teacher">Colesnicov Ludmila</span><span class="lesson-grade">9</span>
                <span class="lesson-homework">Решение с.5</span></div>
              <div class="lesson-card"><div class="lesson-order">5</div><span class="lesson-text-wrap">Chimie</span>
                <span class="lesson-grade">a</span></div>
            </td></tr></table>
        """
        val lessons = AgendaParser.parseLessons(html)

        assertEquals(2, lessons.size)
        val math = lessons.first { it.order == 4 }
        assertEquals(listOf(9), math.grades)
        assertEquals("Решение с.5", math.homework)
        assertEquals("Colesnicov Ludmila", math.teacher)
        // «a» в кружке значит «absent» (пропуск), это не оценка
        assertTrue(lessons.first { it.order == 5 }.grades.isEmpty())
    }
}
