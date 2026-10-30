package com.agenda.client

import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Логика экранов «Дневник», «Домашка», «Пропуски» (чистый Kotlin, без Android). */
object DiaryLogic {

    private val weekdays = listOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")

    /** Все дни, по которым у нас есть уроки, по возрастанию. */
    fun dates(lessons: List<Lesson>): List<String> = lessons.map { it.date }.distinct().sorted()

    /**
     * С какого дня открыть дневник: ближайший день с уроками, начиная с сегодняшнего; если таких нет
     * (каникулы, конец года), то последний известный день. Нет данных: null.
     */
    fun initialDate(dates: List<String>, todayIso: String): String? =
        dates.firstOrNull { it >= todayIso } ?: dates.lastOrNull()

    /** Сегодняшняя дата «yyyy-MM-dd» в часовом поясе телефона. */
    fun todayIso(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): String {
        val c = GregorianCalendar(zone)
        c.timeInMillis = now
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(GregorianCalendar.YEAR), c.get(GregorianCalendar.MONTH) + 1, c.get(GregorianCalendar.DAY_OF_MONTH)
        )
    }

    /** «Понедельник, 05.10.2026». Если дату не разобрать, возвращает её как есть. */
    fun dayTitle(iso: String): String {
        val p = iso.split("-")
        val y = p.getOrNull(0)?.toIntOrNull() ?: return iso
        val m = p.getOrNull(1)?.toIntOrNull() ?: return iso
        val d = p.getOrNull(2)?.toIntOrNull() ?: return iso
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(y, m - 1, d)
        val weekday = weekdays[c.get(GregorianCalendar.DAY_OF_WEEK) - 1]
        return String.format(Locale.US, "%s, %02d.%02d.%04d", weekday, d, m, y)
    }

    /** «7 уроков · оценок: 1 · с ДЗ: 6». */
    fun summary(day: List<Lesson>): String {
        val parts = mutableListOf(day.size.toString() + " " + RuWords.plural(day.size, "урок", "урока", "уроков"))
        val grades = day.sumOf { it.grades.size }
        if (grades > 0) parts.add("оценок: $grades")
        val homework = day.count { it.homework.isNotEmpty() }
        if (homework > 0) parts.add("с ДЗ: $homework")
        return parts.joinToString(" · ")
    }
}

/** Строка экрана «Домашка». */
sealed class HwRow {
    data class Section(val title: String) : HwRow()
    data class DateHeader(val title: String) : HwRow()
    data class Item(val subject: String, val text: String) : HwRow()
}

object HomeworkLogic {

    const val PAST_DAYS = 14

    /**
     * Задания из календаря и из списка «Sarcini de învățare», без повторов (у двух уроков одного предмета в один день
     * бывает одно и то же ДЗ). Блок «Скоро»: сегодня и позже по возрастанию. Блок «Последние дни»: прошедшие
     * PAST_DAYS дней, свежие сверху. Всё, что старше, не показываем: это уже неактуально.
     */
    fun rows(extras: PortalExtras, todayIso: String, pastDays: Int = PAST_DAYS): List<HwRow> {
        val all = (extras.lessons.filter { it.homework.isNotEmpty() }.map { Triple(it.date, it.subject, it.homework) } +
            extras.homework.map { Triple(it.date, it.subject, it.text) }).distinct()

        val pastFrom = PortalMerge.shiftIso(todayIso, -pastDays) ?: ""
        val upcoming = all.filter { it.first >= todayIso }.groupBy { it.first }.toSortedMap()
        val past = all.filter { it.first < todayIso && it.first >= pastFrom }.groupBy { it.first }
            .toSortedMap(compareByDescending { it })

        val rows = mutableListOf<HwRow>()
        fun block(title: String, days: Map<String, List<Triple<String, String, String>>>) {
            if (days.isEmpty()) return
            rows.add(HwRow.Section(title))
            for ((date, items) in days) {
                rows.add(HwRow.DateHeader(DiaryLogic.dayTitle(date)))
                items.sortedBy { it.second }.forEach { rows.add(HwRow.Item(it.second, it.third)) }
            }
        }
        block("Скоро", upcoming)
        block("Последние $pastDays дней", past)
        return rows
    }
}

/** Пропуски одного дня для экрана. */
data class AbsenceDay(val title: String, val entries: List<AbsenceEntry>)

data class AbsenceScreen(
    val totalLine: String,
    val semesterLines: List<String>,
    val presenceLine: String?,
    val subjects: List<SubjectAbsences>,
    val days: List<AbsenceDay>,
    /** Не null, если подробный список неполный: честно говорим об этом, а сводке доверяем. */
    val listNote: String?
)

object AbsenceScreens {

    fun statusLabel(status: AbsenceStatus): String = when (status) {
        AbsenceStatus.MOTIVATED -> "мотивирован"
        AbsenceStatus.UNMOTIVATED -> "не мотивирован"
        AbsenceStatus.UNKNOWN -> "статус неизвестен"
    }

    fun build(data: AbsenceData?): AbsenceScreen? {
        if (data == null) return null

        val motivated = (data.semester1?.motivated ?: 0) + (data.semester2?.motivated ?: 0)
        val unmotivated = (data.semester1?.unmotivated ?: 0) + (data.semester2?.unmotivated ?: 0)
        val total = (data.semester1?.total ?: 0) + (data.semester2?.total ?: 0)

        val lines = mutableListOf<String>()
        data.semester1?.let { lines.add("Семестр 1: " + semesterText(it)) }
        data.semester2?.let { lines.add("Семестр 2: " + semesterText(it)) }

        val days = data.entries.groupBy { it.date }.toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                val title = DiaryLogic.dayTitle(date) + " · " + list.size + " " +
                    RuWords.plural(list.size, "урок", "урока", "уроков")
                AbsenceDay(title, list.sortedBy { it.lessonNo })
            }

        val note = if (total > 0 && !AbsenceMath.listIsComplete(data)) {
            if (data.entries.isEmpty()) {
                "Подробный список пока не загрузился: страница не успела его показать. Сводка выше актуальна."
            } else {
                "Подробный список неполный (${data.entries.size} из $total): страница показала не всё. Сводка выше актуальна."
            }
        } else {
            null
        }

        return AbsenceScreen(
            totalLine = "Пропущено уроков: $total (не мотивированных $unmotivated, мотивированных $motivated)",
            semesterLines = lines,
            presenceLine = data.presencePercent?.let { "Посещаемость: $it%" },
            subjects = data.bySubject
                .filter { it.semester1.total + it.semester2.total > 0 }
                .sortedWith(compareByDescending<SubjectAbsences> { it.semester1.total + it.semester2.total }.thenBy { it.subject }),
            days = days,
            listNote = note
        )
    }

    private fun semesterText(s: SemesterAbsences) =
        "не мотивированных ${s.unmotivated} · мотивированных ${s.motivated} · всего ${s.total}"
}

/** Учителя по предметам для карточек на экране «Оценки». */
object TeacherLookup {

    /** Нормализованное название предмета → учителя (у истории их несколько, порядок как в «Dosar»). */
    fun bySubject(dosar: DosarInfo?): Map<String, List<String>> =
        dosar?.teachers.orEmpty().groupBy({ GradeDates.normalize(it.subject) }, { it.teacher })
            .mapValues { it.value.distinct() }

    fun forSubject(map: Map<String, List<String>>, subject: String): List<String> =
        map[GradeDates.normalize(subject)].orEmpty()
}
