package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Страницы здесь собраны по структуре настоящего окна «Detalii lecție», но с выдуманным содержимым:
 * реальных имён, кодов и номеров сеансов в тестах нет.
 */
class LessonDetailsParserTest {

    private fun hidden(name: String, value: String) =
        """<input type="hidden" name="$name" id="$name" value="$value">"""

    private fun page(
        order: Int = 3,
        discipline: String = "Matematică",
        date: String = "04.11.2026",
        topic: String = "Ecuații de gradul II",
        message: String = "",
        grade: String = "",
        homework: String = "",
        homeworkFor: String = "04.11.2026",
        posted: String = "",
        summative: String = "Nu",
        thesis: String = "Nu",
        meetingLink: String = "",
        meetingHtml: String = "",
        hiddenTopic: String = topic,
        hiddenHomework: String = homework
    ) = """
        <html><body class="t-Dialog-page"><form>
        ${hidden("P19_LESSON_DATE", date)}
        ${hidden("P19_LESSON_ORDER_ID", order.toString())}
        ${hidden("P19_DISCIPLINY_NAME", discipline)}
        ${hidden("P19_LESSON_SUBJECT", hiddenTopic)}
        ${hidden("P19_HOME_WORK_TXT", hiddenHomework)}
        ${hidden("P19_SUMATIVA", summative)}
        ${hidden("P19_TEZA_FLG", thesis)}
        ${hidden("P19_MEETING_LINK", meetingLink)}
        ${hidden("P19_MEETING_HTML", meetingHtml)}
        <div class="lesson-view">
          <div class="lesson-hero">
            <div class="lesson-hero-badge">$order</div>
            <div class="lesson-hero-text">
              <div class="lesson-hero-title">$discipline</div>
              <div class="lesson-hero-date"><i class="fa fa-calendar-alt"></i><span>$date</span></div>
            </div>
          </div>
          <div class="card-section">
            <h3>Subiectul lecției</h3>
            <ul><div class="lesson-text">$topic</div><div id="meeting_html_placeholder"></div></ul>
          </div>
          <div class="card-section">
            <h3>Mesajul profesorului</h3>
            <div class="lesson-message">$message</div>
            <ul><div class="lesson-grade-wrap"><div class="lesson-grade-label">Nota / absență</div>
              <div class="lesson-grade-pill">$grade</div></div></ul>
          </div>
          <div class="card-section">
            <h3>Activități de învățare</h3>
            <ul>
              <li class="sub-text">Pentru data de: $homeworkFor</li>
              <li class="sub-text">Data completării de către profesor: $posted</li>
              <div class="lesson-homework-box">$homework</div>
            </ul>
          </div>
        </div>
        </form></body></html>
    """.trimIndent()

    @Test
    fun parsesAFullyFilledLesson() {
        val d = LessonDetailsParser.parse(
            page(
                topic = "Ecuații de gradul II",
                message = "Pregătiți-vă pentru lucrare",
                grade = "9",
                homework = "Exercițiile 4-7, pagina 61",
                homeworkFor = "05.11.2026",
                posted = "04.11.2026 14:05",
                summative = "Da"
            )
        )!!

        assertEquals("2026-11-04", d.date)
        assertEquals(3, d.order)
        assertEquals("Matematică", d.discipline)
        assertEquals("Ecuații de gradul II", d.topic)
        assertEquals("Pregătiți-vă pentru lucrare", d.teacherMessage)
        assertEquals("9", d.gradeText)
        assertEquals("Exercițiile 4-7, pagina 61", d.homeworkText)
        assertEquals("2026-11-05", d.homeworkFor)
        assertEquals("04.11.2026 14:05", d.homeworkPostedRaw)
        assertEquals("2026-11-04", d.homeworkPostedOn)
        assertTrue(d.isSummative)
        assertFalse(d.isThesis)
    }

    @Test
    fun anEmptyLessonLikeTheRealOneHasOnlyATopic() {
        // как присланное окно: тема есть, сообщение, оценка и ДЗ пустые, поле «Data completării» пустое
        val d = LessonDetailsParser.parse(page(order = 1, discipline = "Limba rusă", date = "02.11.2026", homeworkFor = "02.11.2026"))!!

        assertEquals("2026-11-02", d.date)
        assertEquals(1, d.order)
        assertEquals("Ecuații de gradul II", d.topic)
        assertEquals("", d.teacherMessage)
        assertEquals("", d.gradeText)
        assertEquals("", d.homeworkText)
        assertEquals("2026-11-02", d.homeworkFor)
        assertEquals("", d.homeworkPostedRaw)
        assertNull(d.homeworkPostedOn)
        assertFalse(d.isSummative)
        assertNull(d.meetingLink)
    }

    @Test
    fun anAbsenceIsKeptAsTheLetterA() {
        assertEquals("a", LessonDetailsParser.parse(page(grade = "a"))!!.gradeText)
    }

    @Test
    fun lineBreaksInsideATopicSurvive() {
        val d = LessonDetailsParser.parse(page(topic = "Prima linie\nA doua linie"))!!
        assertEquals("Prima linie\nA doua linie", d.topic)
    }

    @Test
    fun markupInTeacherTextStaysPlainText() {
        // учитель может написать что угодно: разбор возвращает текст, а не разметку
        val d = LessonDetailsParser.parse(page(message = "Tema &amp; &lt;b&gt;urgent&lt;/b&gt;"))!!
        assertEquals("Tema & <b>urgent</b>", d.teacherMessage)
    }

    @Test
    fun fallsBackToHiddenFieldsWhenVisibleBlocksAreEmpty() {
        val d = LessonDetailsParser.parse(page(topic = "", homework = "", hiddenTopic = "Din câmpul ascuns", hiddenHomework = "Temă ascunsă"))!!
        assertEquals("Din câmpul ascuns", d.topic)
        assertEquals("Temă ascunsă", d.homeworkText)
    }

    @Test
    fun thesisAndSummativeFlagsAreIndependent() {
        val d = LessonDetailsParser.parse(page(summative = "Nu", thesis = "Da"))!!
        assertFalse(d.isSummative)
        assertTrue(d.isThesis)
    }

    @Test
    fun meetingLinkComesFromTheLinkFieldOrTheInsertedHtml() {
        val direct = LessonDetailsParser.parse(page(meetingLink = "https://meet.example.org/abc"))!!
        assertEquals("https://meet.example.org/abc", direct.meetingLink)

        val html = "&lt;a href=&quot;https://meet.example.org/xyz&quot;&gt;Intră&lt;/a&gt;"
        val inserted = LessonDetailsParser.parse(page(meetingHtml = html))!!
        assertEquals("https://meet.example.org/xyz", inserted.meetingLink)
    }

    @Test
    fun foreignSchemesInTheMeetingLinkAreDropped() {
        assertNull(LessonDetailsParser.parse(page(meetingLink = "javascript:alert(1)"))!!.meetingLink)
        val html = "&lt;a href=&quot;javascript:alert(1)&quot;&gt;x&lt;/a&gt;"
        assertNull(LessonDetailsParser.parse(page(meetingHtml = html))!!.meetingLink)
    }

    @Test
    fun aPageThatIsNotALessonWindowGivesNull() {
        assertNull(LessonDetailsParser.parse("<html><body><form>Autentificare</form></body></html>"))
        assertNull(LessonDetailsParser.parse(""))
    }

    @Test
    fun aLessonWithoutADateOrNumberCannotBeIdentifiedSoItIsNull() {
        val noDate = page().replace("04.11.2026", "")
        assertNull(LessonDetailsParser.parse(noDate))
        val noOrder = page().replace(Regex("""<div class="lesson-hero-badge">\d+</div>"""), """<div class="lesson-hero-badge"></div>""")
            .replace(Regex("""name="P19_LESSON_ORDER_ID" id="P19_LESSON_ORDER_ID" value="\d+""""), """name="P19_LESSON_ORDER_ID" id="P19_LESSON_ORDER_ID" value=""""")
        assertNull(LessonDetailsParser.parse(noOrder))
    }

    @Test
    fun aPostedDateWithoutTimeIsParsedToo() {
        val d = LessonDetailsParser.parse(page(homework = "x", posted = "03.11.2026"))!!
        assertEquals("03.11.2026", d.homeworkPostedRaw)
        assertEquals("2026-11-03", d.homeworkPostedOn)
        assertNotNull(d.homeworkFor)
    }
}
