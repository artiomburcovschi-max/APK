package com.agenda.client

/**
 * Урок из календаря «Agenda» на главной странице портала. date = "yyyy-MM-dd" (берётся из ячейки дня).
 * grades: оценки, выставленные на этом уроке (обычно одна или ни одной). homework: текст домашнего задания
 * (пусто, если не задано). room часто пустой: портал его не заполняет.
 */
data class Lesson(
    val date: String,
    val order: Int,
    val start: String,
    val end: String,
    val subject: String,
    val room: String,
    val teacher: String,
    val homework: String,
    val grades: List<Int>
)

/** Задание из блока «Sarcini de învățare»: date = день урока в календаре портала. */
data class HomeworkItem(val date: String, val subject: String, val text: String)

/** Кто преподаёт предмет. У одного предмета может быть несколько строк (например, у истории). */
data class TeacherEntry(val subject: String, val teacher: String)

/**
 * Из «Dosar» берём только то, что реально нужно приложению: класс, классного руководителя, учебное заведение
 * и учителей по предметам. Дата рождения, персональный код, адрес и прочее намеренно НЕ читаются и не хранятся.
 */
data class DosarInfo(
    val className: String?,
    val formTeacher: String?,
    val institution: String?,
    val teachers: List<TeacherEntry>
)

enum class AbsenceStatus { MOTIVATED, UNMOTIVATED, UNKNOWN }

/** Один пропущенный урок. cumulative = «Absențe cumulative» портала на момент этого пропуска. */
data class AbsenceEntry(
    val date: String,
    val lessonNo: Int,
    val subject: String,
    val status: AbsenceStatus,
    val cumulative: Int
)

data class SemesterAbsences(val motivated: Int, val unmotivated: Int, val total: Int)

data class SubjectAbsences(val subject: String, val semester1: SemesterAbsences, val semester2: SemesterAbsences)

/**
 * Пропуски. entries = подробный список по урокам (он рисуется на странице позже сводки, поэтому
 * может оказаться пустым или неполным: см. AbsenceMath.listIsComplete). Сводка (semester1/2, presencePercent)
 * лежит в скрытых полях и надёжна всегда.
 */
data class AbsenceData(
    val entries: List<AbsenceEntry>,
    val semester1: SemesterAbsences?,
    val semester2: SemesterAbsences?,
    val bySubject: List<SubjectAbsences>,
    val presencePercent: Int?
)

/** Всё, что приложение знает со страницы портала сверх оценок. Хранится на телефоне (см. PortalExtrasStore). */
data class PortalExtras(
    val lessons: List<Lesson> = emptyList(),
    val homework: List<HomeworkItem> = emptyList(),
    val dosar: DosarInfo? = null,
    val absences: AbsenceData? = null,
    val updatedAt: Long = 0L
)
