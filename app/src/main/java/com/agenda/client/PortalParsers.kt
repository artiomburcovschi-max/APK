package com.agenda.client

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.Normalizer

private fun Element.cleanText(): String = text().replace('\u00A0', ' ').trim()

private fun Element.multilineText(): String {
    val sb = StringBuilder()
    fun walk(node: Node) {
        when (node) {
            is TextNode -> sb.append(node.wholeText)
            is Element -> {
                if (node.tagName() == "br") sb.append('\n')
                node.childNodes().forEach { walk(it) }
            }
        }
    }
    childNodes().forEach { walk(it) }
    return sb.toString()
        .replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ')
        .lines().joinToString("\n") { it.trim().replace(Regex("[ \t]+"), " ") }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}


fun foldRo(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

fun prettyName(raw: String): String {
    val s = raw.trim().replace(Regex("\\s+"), " ")
    if (s.isEmpty() || s != s.uppercase()) return s
    return s.split(" ").joinToString(" ") { word ->
        word.split("-").joinToString("-") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

private fun Element.parentWithAttr(attr: String): Element? {
    var p: Element? = parent()
    while (p != null && !p.hasAttr(attr)) p = p.parent()
    return p
}

object AgendaParser {

    fun parseLessons(html: String): List<Lesson> {
        val doc = Jsoup.parse(html)
        val result = mutableListOf<Lesson>()
        for (card in doc.select(".lesson-card")) {
            // Дата урока = дата ячейки дня, в которой лежит карточка
            val date = card.parentWithAttr("data-date")?.attr("data-date")?.trim()
            if (date.isNullOrEmpty() || !Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) continue

            val subject = card.selectFirst(".lesson-text-wrap")?.cleanText().orEmpty()
            if (subject.isEmpty()) continue
            val times = card.select(".lesson-time span").map { it.cleanText() }
            val grades = card.select(".lesson-grade").mapNotNull { it.cleanText().toIntOrNull() }
                .filter { it in GoalPlanner.MIN_GRADE..GoalPlanner.MAX_GRADE }

            result.add(
                Lesson(
                    date = date,
                    order = card.selectFirst(".lesson-order")?.cleanText()?.toIntOrNull() ?: 0,
                    start = times.getOrElse(0) { "" },
                    end = times.getOrElse(1) { "" },
                    subject = subject,
                    room = card.selectFirst(".lesson-room")?.cleanText().orEmpty(),
                    teacher = card.selectFirst(".lesson-teacher")?.cleanText().orEmpty(),
                    homework = card.selectFirst(".lesson-homework")?.multilineText().orEmpty(),
                    grades = grades
                )
            )
        }
        // Один и тот же урок не должен попасть дважды. Портал иногда отдаёт две карточки на один урок
        // (одна из расписания без записи в журнале, вторая с ДЗ и оценкой): склеиваем, а не отбрасываем вторую,
        // иначе можно потерять ДЗ или оценку.
        val unique = LinkedHashMap<Triple<String, Int, String>, Lesson>()
        for (lesson in result) {
            val key = Triple(lesson.date, lesson.order, lesson.subject)
            val prev = unique[key]
            unique[key] = if (prev == null) lesson else prev.copy(
                room = prev.room.ifEmpty { lesson.room },
                teacher = prev.teacher.ifEmpty { lesson.teacher },
                homework = prev.homework.ifEmpty { lesson.homework },
                grades = if (prev.grades.isNotEmpty()) prev.grades else lesson.grades
            )
        }
        return unique.values.sortedWith(compareBy({ it.date }, { it.order }))
    }

    /** Блок «Sarcini de învățare»: HTML списка лежит в скрытом поле, запасной вариант: уже нарисованный список. */
    fun parseHomework(html: String): List<HomeworkItem> {
        val doc = Jsoup.parse(html)
        val raw = doc.select("#P1_HOMEWORK").attr("value")
        val root = if (raw.isNotBlank()) Jsoup.parseBodyFragment(raw).body() else doc.selectFirst("#homework_list") ?: return emptyList()

        val result = mutableListOf<HomeworkItem>()
        var currentDate: String? = null
        for (el in root.select("b, li")) {
            if (el.tagName() == "b") {
                currentDate = GradeDates.toIso(el.cleanText())
            } else {
                val date = currentDate ?: continue
                val spans = el.select("span")
                val subject = spans.firstOrNull()?.cleanText()?.removeSuffix(":")?.trim().orEmpty()
                val text = spans.drop(1).joinToString(" ") { it.multilineText() }.trim()
                if (subject.isNotEmpty() && text.isNotEmpty()) result.add(HomeworkItem(date, subject, text))
            }
        }
        return result
    }
}

/** «Dosar»: класс, классный руководитель, школа и учителя по предметам. */
object DosarParser {

    fun parse(html: String): DosarInfo? {
        val doc = Jsoup.parse(html)

        val teachers = mutableListOf<TeacherEntry>()
        for (row in doc.select("tr")) {
            val subject = row.selectFirst("td[headers=DISCIPLINY_NAME]")?.cleanText() ?: continue
            val teacher = row.selectFirst("td[headers=STAFF_NAME]")?.cleanText() ?: continue
            if (subject.isNotEmpty() && teacher.isNotEmpty()) teachers.add(TeacherEntry(subject, prettyName(teacher)))
        }

        fun field(id: String): String? = doc.selectFirst("#$id")?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }
        val className = field("P1_CLASS_NAME")
        val formTeacher = field("P1_DIRIGINTE")?.let { prettyName(it) }
        val institution = field("P1_INSTITUTION_NAME")

        if (teachers.isEmpty() && className == null && formTeacher == null && institution == null) return null
        return DosarInfo(className, formTeacher, institution, teachers.distinct())
    }
}

/** «Absențe»: сводка по семестрам, по предметам и подробный список пропущенных уроков. */
object AbsenceParser {

    fun parse(html: String): AbsenceData? {
        val doc = Jsoup.parse(html)

        fun hidden(id: String): Int? = doc.selectFirst("#$id")?.attr("value")?.trim()?.toIntOrNull()
        fun semester(motivated: String, unmotivated: String, total: String): SemesterAbsences? {
            val m = hidden(motivated) ?: return null
            val u = hidden(unmotivated) ?: return null
            val t = hidden(total) ?: (m + u)
            return SemesterAbsences(m, u, t)
        }

        val sem1 = semester("P1_MOTIVATED", "P1_UNMOTIVATED", "P1_TOTAL_ABSENCES1")
        val sem2 = semester("P1_MOTIVATED2", "P1_UNMOTIVATED2", "P1_TOTAL_ABSENCES2")
        val presence = hidden("P1_PRESENCE_PERCENT")

        val entries = doc.select(".absence-field").mapNotNull { parseEntry(it) }
            .sortedWith(compareBy({ it.date }, { it.lessonNo }))

        val bySubject = doc.select(".abs-card").mapNotNull { card ->
            val title = card.selectFirst(".abs-card__title")?.cleanText().orEmpty()
            // в сетке каждая строка семестра = название + три числа (motivate, nemotivate, total)
            val cells = card.select(".abs-grid__cell")
            val numbers = card.select(".abs-badge").map { it.cleanText().toIntOrNull() ?: 0 }
            if (title.isEmpty() || cells.isEmpty() || numbers.size < 6) return@mapNotNull null
            SubjectAbsences(
                title,
                SemesterAbsences(numbers[0], numbers[1], numbers[2]),
                SemesterAbsences(numbers[3], numbers[4], numbers[5])
            )
        }

        if (sem1 == null && sem2 == null && entries.isEmpty() && bySubject.isEmpty() && presence == null) return null
        return AbsenceData(entries, sem1, sem2, bySubject, presence)
    }

    private fun parseEntry(field: Element): AbsenceEntry? {
        var date: String? = null
        var lesson: Int? = null
        var subject: String? = null
        var status = AbsenceStatus.UNKNOWN
        var cumulative = 0

        for (group in field.select(".field-group")) {
            val title = foldRo(group.selectFirst(".field-title")?.cleanText().orEmpty())
            val value = group.selectFirst(".field-value") ?: continue
            val text = value.cleanText()
            // порядок проверок важен: «absente cumulative» тоже начинается с «absen»
            when {
                title.startsWith("data") -> date = GradeDates.toIso(text)
                title.startsWith("lecti") -> lesson = text.toIntOrNull()
                title.startsWith("disciplin") -> subject = text
                title.startsWith("absente") -> cumulative = text.toIntOrNull() ?: 0
                title.startsWith("absenta") -> status = AbsenceMath.statusOf(text)
            }
        }
        if (date == null || lesson == null || subject.isNullOrEmpty()) return null
        return AbsenceEntry(date, lesson, subject, status, cumulative)
    }
}

/** Мелкая логика пропусков (чистый Kotlin). */
object AbsenceMath {

    /**
     * «motivat» и «nemotivat»: ловушка, потому что слово «motivat» ВНУТРИ слова «nemotivat».
     * Поэтому нельзя искать подстроку: сравниваем слово целиком, неизвестное остаётся UNKNOWN.
     */
    fun statusOf(text: String): AbsenceStatus = when (foldRo(text)) {
        "nemotivat", "nemotivata" -> AbsenceStatus.UNMOTIVATED
        "motivat", "motivata" -> AbsenceStatus.MOTIVATED
        else -> AbsenceStatus.UNKNOWN
    }

    /** Семестр по дате: сентябрь-декабрь = 1, январь-август = 2 (так учебный год в Молдове). */
    fun semesterOf(isoDate: String): Int {
        val month = isoDate.substringAfter('-', "").substringBefore('-').toIntOrNull() ?: return 1
        return if (month in 9..12) 1 else 2
    }

    /**
     * Подробный список «Zilnică» рисуется страницей отдельно от сводки, и к моменту разбора он может быть ещё не
     * готов. Список считаем полным, только если число записей совпало с общим числом пропусков из сводки.
     */
    fun listIsComplete(data: AbsenceData): Boolean {
        val total = (data.semester1?.total ?: 0) + (data.semester2?.total ?: 0)
        return data.entries.size == total
    }
}
