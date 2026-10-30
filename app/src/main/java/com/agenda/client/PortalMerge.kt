package com.agenda.client

/** Что разобрали с одной загрузки страницы. Пустое значение может значить «блок ещё не нарисовался». */
data class ParsedPortal(
    val lessons: List<Lesson>,
    val homework: List<HomeworkItem>,
    val dosar: DosarInfo?,
    val absences: AbsenceData?
)

/**
 * Склейка новых данных с сохранёнными (чистый Kotlin).
 * Главное правило: страница могла загрузиться не целиком, поэтому пустой блок НЕ стирает то, что мы уже знали.
 */
object PortalMerge {

    /** Уроки старше этого срока от самого свежего дня выбрасываем, чтобы файл не рос бесконечно. */
    const val KEEP_DAYS = 400

    fun merge(old: PortalExtras, parsed: ParsedPortal, now: Long): PortalExtras = PortalExtras(
        lessons = mergeLessons(old.lessons, parsed.lessons),
        homework = mergeHomework(old, parsed),
        dosar = mergeDosar(old.dosar, parsed.dosar),
        absences = mergeAbsences(old.absences, parsed.absences),
        updatedAt = now
    )

    /**
     * Календарь показывает один месяц. Дни, которые есть в новой загрузке, заменяем целиком (ДЗ и оценки
     * могли измениться, урок могли убрать); дни, которых в ней нет, оставляем: так копится история по месяцам.
     */
    fun mergeLessons(old: List<Lesson>, parsed: List<Lesson>): List<Lesson> {
        if (parsed.isEmpty()) return old
        val freshDays = parsed.map { it.date }.toSet()
        val merged = (old.filter { it.date !in freshDays } + parsed)
            .sortedWith(compareBy({ it.date }, { it.order }))
        val newest = merged.last().date
        val cutoff = shiftIso(newest, -KEEP_DAYS) ?: return merged
        return merged.filter { it.date >= cutoff }
    }

    /**
     * Список «Sarcini de învățare»: если календарь на месте (страница загрузилась целиком), пустой список
     * значит «заданий нет» и заменяет старый. Если календаря нет, страница неполная: оставляем прежнее.
     */
    private fun mergeHomework(old: PortalExtras, parsed: ParsedPortal): List<HomeworkItem> =
        if (parsed.lessons.isNotEmpty() || parsed.homework.isNotEmpty()) parsed.homework else old.homework

    private fun mergeDosar(old: DosarInfo?, parsed: DosarInfo?): DosarInfo? {
        if (parsed == null) return old
        if (old == null) return parsed
        // таблица учителей могла не нарисоваться: тогда берём прежних, остальное обновляем
        return if (parsed.teachers.isEmpty()) parsed.copy(teachers = old.teachers) else parsed
    }

    /**
     * Сводка (по семестрам, по предметам, процент) надёжна, её берём всегда, когда она есть. Подробный список
     * рисуется отдельно и мог не успеть: полный список принимаем, неполный заменяет прежний, только если в нём
     * больше записей (иначе остаётся последний известный).
     */
    private fun mergeAbsences(old: AbsenceData?, parsed: AbsenceData?): AbsenceData? {
        if (parsed == null) return old
        if (old == null) return parsed
        val entries = when {
            AbsenceMath.listIsComplete(parsed) -> parsed.entries
            parsed.entries.size > old.entries.size -> parsed.entries
            else -> old.entries
        }
        return parsed.copy(
            entries = entries,
            bySubject = parsed.bySubject.ifEmpty { old.bySubject },
            semester1 = parsed.semester1 ?: old.semester1,
            semester2 = parsed.semester2 ?: old.semester2,
            presencePercent = parsed.presencePercent ?: old.presencePercent
        )
    }

    /** «2026-10-05» + n дней → «2026-09-05» без java.time (на Android 7 его ещё нет без десугаринга). */
    fun shiftIso(iso: String, days: Int): String? {
        val p = iso.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        val cal = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(y, m - 1, d)
        cal.add(java.util.Calendar.DAY_OF_MONTH, days)
        return String.format(
            java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}

/** Точные даты оценок из календаря → в существующую привязку дат к оценкам (GradesRepository.fillDates). */
object LessonDates {

    /** Оценки по урокам, новые сверху (так же, как блок «Note recente»; дальше их разворачивает fillDates). */
    fun gradeEvents(lessons: List<Lesson>): List<RecentEval> =
        lessons.flatMap { lesson -> lesson.grades.map { Triple(lesson.date, lesson.order, RecentEval(lesson.date, lesson.subject, it)) } }
            .sortedWith(compareByDescending<Triple<String, Int, RecentEval>> { it.first }.thenByDescending { it.second })
            .map { it.third }

    /**
     * Объединяет события из уроков и из блока «Note recente». Если у даты есть уроки в календаре, берём оценки
     * оттуда (там их все, а не пять последних); из «Note recente» добавляем только то, чего в календаре нет.
     */
    fun mergeRecent(lessons: List<Lesson>, pageRecent: List<RecentEval>): List<RecentEval> {
        val fromLessons = gradeEvents(lessons)
        val knownDates = lessons.map { it.date }.toSet()
        val extra = pageRecent.filter { it.date !in knownDates }
        // обе части «новые сверху»; сортировка стабильная, поэтому порядок внутри одной даты сохраняется
        return (fromLessons + extra).sortedByDescending { it.date }
    }
}
