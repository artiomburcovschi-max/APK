package com.agenda.client

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Урок на оси времени: границы в миллисекундах (epoch) в часовом поясе телефона. */
data class Slot(val lesson: Lesson, val startMs: Long, val endMs: Long)

/** Где мы сейчас относительно расписания. Состояние зависит только от списка уроков и текущего момента. */
sealed class ClockState {
    /** Уроков в памяти нет или у сегодняшних уроков не разобралось время. */
    object NoData : ClockState()

    /** Сегодня уроки есть, но ни один ещё не начался. */
    data class BeforeFirst(val first: Slot) : ClockState()

    /** Идёт урок. next = следующий урок сегодня (null, если этот последний). */
    data class InLesson(val current: Slot, val next: Slot?) : ClockState()

    /** Между двумя уроками сегодня: перемена или «окно». */
    data class Break(val previous: Slot, val next: Slot) : ClockState()

    /** Сегодняшние уроки закончились. next = ближайший урок в один из следующих дней. */
    data class Finished(val next: Slot?) : ClockState()

    /** Сегодня уроков нет вообще (выходной, каникулы). */
    data class DayOff(val next: Slot?) : ClockState()
}

/** Строка списка одного дня: урок или промежуток между уроками. */
sealed class DayRow {
    data class LessonRow(val lesson: Lesson, val timeText: String) : DayRow()
    data class GapRow(val minutes: Int, val isWindow: Boolean) : DayRow()
}

/** Что показать на карточке «сейчас». progress = доля прошедшего времени урока или перемены (0..1). */
data class LiveInfo(
    val title: String,
    val countdownMs: Long?,
    val caption: String,
    val nextLine: String?,
    val progress: Float?
)

/**
 * Часы расписания: какой сейчас урок, сколько до его конца, сколько до конца перемены.
 * Чистый Kotlin без Android и без java.time (на Android 7 его нет без десугаринга), поэтому проверяется JUnit.
 * Времена уроков берутся из данных портала (Lesson.start/end), а не зашиты в код: у другого класса сетка может отличаться.
 */
object LessonClock {

    /** Промежуток между уроками от стольких минут называем «окном», а не переменой. */
    const val WINDOW_MIN = 30

    private val timeRegex = Regex("(\\d{1,2})[:.](\\d{2})")
    private val inWeekday = listOf("в понедельник", "во вторник", "в среду", "в четверг", "в пятницу", "в субботу", "в воскресенье")

    /** «08:45» → 525. Не время (пусто, «25:00», «8-45») → null. */
    fun minutesOfDay(hhmm: String): Int? {
        val m = timeRegex.matchEntire(hhmm.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** Момент (epoch мс) «дата + HH:mm» в часовом поясе [zone]. Дата «yyyy-MM-dd». Неразборчивое: null. */
    fun epoch(date: String, hhmm: String, zone: TimeZone): Long? {
        val minutes = minutesOfDay(hhmm) ?: return null
        return epoch(date, minutes, zone)
    }

    private fun epoch(date: String, minutes: Int, zone: TimeZone): Long? {
        val p = date.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        val c = GregorianCalendar(zone)
        c.clear()
        c.set(y, m - 1, d, minutes / 60, minutes % 60, 0)
        return c.timeInMillis
    }

    /**
     * Уроки дня на оси времени, по возрастанию начала. Если на один номер пришло несколько записей
     * (например, группы), берём одну: первую по названию предмета. Уроки без разборчивого времени
     * или с концом не позже начала пропускаем: считать по ним нельзя.
     */
    fun slots(lessons: List<Lesson>, date: String, zone: TimeZone): List<Slot> =
        lessons.filter { it.date == date }
            .groupBy { it.order }
            .values
            .mapNotNull { group ->
                val l = group.minByOrNull { it.subject } ?: return@mapNotNull null
                val s = minutesOfDay(l.start)
                val e = minutesOfDay(l.end)
                if (s == null || e == null || e <= s) return@mapNotNull null
                val startMs = epoch(date, s, zone) ?: return@mapNotNull null
                val endMs = epoch(date, e, zone) ?: return@mapNotNull null
                Slot(l, startMs, endMs)
            }
            .sortedBy { it.startMs }

    /** Состояние на момент [nowMs]. Начало урока включительно, конец исключительно (в 08:45:00 уже перемена). */
    fun stateAt(lessons: List<Lesson>, nowMs: Long, zone: TimeZone): ClockState {
        if (lessons.isEmpty()) return ClockState.NoData
        val today = isoOf(nowMs, zone)
        val todaySlots = slots(lessons, today, zone)

        if (todaySlots.isEmpty()) {
            // Уроки сегодня есть, но времени у них нет: честно говорим «нет данных», а не «выходной»
            if (lessons.any { it.date == today }) return ClockState.NoData
            return ClockState.DayOff(firstSlotAfter(lessons, today, zone))
        }

        val current = todaySlots.firstOrNull { nowMs >= it.startMs && nowMs < it.endMs }
        if (current != null) {
            return ClockState.InLesson(current, todaySlots.firstOrNull { it.startMs >= current.endMs })
        }
        val next = todaySlots.firstOrNull { it.startMs > nowMs }
        val previous = todaySlots.lastOrNull { it.endMs <= nowMs }
        return when {
            next == null -> ClockState.Finished(firstSlotAfter(lessons, today, zone))
            previous == null -> ClockState.BeforeFirst(next)
            else -> ClockState.Break(previous, next)
        }
    }

    /** Первый урок первого дня строго после [afterDate], у которого есть время. */
    private fun firstSlotAfter(lessons: List<Lesson>, afterDate: String, zone: TimeZone): Slot? =
        lessons.map { it.date }.distinct().sorted().filter { it > afterDate }
            .firstNotNullOfOrNull { date -> slots(lessons, date, zone).firstOrNull() }

    /**
     * Когда состояние сменится в следующий раз (epoch мс): конец урока, конец перемены, начало первого урока.
     * Если ждать нечего (выходной, всё закончилось), то ближайшая полночь: после неё «сегодня» уже другой день.
     * Нужно виджету: на этот момент ставится перерисовка.
     */
    fun nextBoundary(state: ClockState, nowMs: Long, zone: TimeZone): Long = when (state) {
        is ClockState.InLesson -> state.current.endMs
        is ClockState.Break -> state.next.startMs
        is ClockState.BeforeFirst -> state.first.startMs
        else -> nextMidnight(nowMs, zone)
    }

    private fun nextMidnight(nowMs: Long, zone: TimeZone): Long {
        val c = GregorianCalendar(zone)
        c.timeInMillis = nowMs
        c.add(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Текст и числа для карточки «сейчас». */
    fun info(state: ClockState, nowMs: Long, zone: TimeZone): LiveInfo = when (state) {
        is ClockState.NoData -> LiveInfo(
            "Расписание пока не загружено", null,
            "Обнови оценки: вместе с ними подтянутся уроки", null, null
        )
        is ClockState.BeforeFirst -> LiveInfo(
            "Уроки ещё не начались",
            state.first.startMs - nowMs,
            "до первого урока",
            "Первый урок: ${lessonName(state.first)}, ${hm(state.first.startMs, zone)}",
            null
        )
        is ClockState.InLesson -> LiveInfo(
            "Урок ${state.current.lesson.order} · ${state.current.lesson.subject}",
            state.current.endMs - nowMs,
            "до конца урока",
            state.next?.let { "Дальше: ${lessonName(it)}, ${hm(it.startMs, zone)}" } ?: "Это последний урок на сегодня",
            fraction(nowMs, state.current.startMs, state.current.endMs)
        )
        is ClockState.Break -> {
            val gapMs = state.next.startMs - state.previous.endMs
            val window = gapMs >= WINDOW_MIN * 60_000L
            LiveInfo(
                if (window) "Окно" else "Перемена",
                state.next.startMs - nowMs,
                "до начала урока ${state.next.lesson.order}",
                "Дальше: ${lessonName(state.next)}, ${hm(state.next.startMs, zone)}",
                fraction(nowMs, state.previous.endMs, state.next.startMs)
            )
        }
        is ClockState.Finished -> LiveInfo(
            "Уроки на сегодня закончились", null, "", nextDayLine(state.next, nowMs, zone), null
        )
        is ClockState.DayOff -> LiveInfo(
            "Сегодня уроков нет", null, "", nextDayLine(state.next, nowMs, zone), null
        )
    }

    private fun lessonName(slot: Slot) = "урок ${slot.lesson.order} · ${slot.lesson.subject}"

    private fun nextDayLine(next: Slot?, nowMs: Long, zone: TimeZone): String? {
        if (next == null) return null
        val today = isoOf(nowMs, zone)
        val nextDate = isoOf(next.startMs, zone)
        val when_ = if (nextDate == shiftIso(today, 1)) "завтра" else inWeekday[weekdayNumber(nextDate) - 1]
        return "Следующий урок $when_ в ${hm(next.startMs, zone)}: ${next.lesson.subject}"
    }

    private fun fraction(now: Long, from: Long, to: Long): Float =
        if (to <= from) 1f else ((now - from).toFloat() / (to - from).toFloat()).coerceIn(0f, 1f)

    /** «12:34» до часа, «1:05:09» от часа. Округляем вверх: пока не истекло, нуля не показываем. */
    fun formatRemaining(ms: Long): String {
        val total = ((ms.coerceAtLeast(0L) + 999L) / 1000L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Строки списка дня: уроки по порядку, между ними перемены и «окна» с длительностью в минутах. */
    fun dayRows(lessons: List<Lesson>): List<DayRow> {
        val rows = mutableListOf<DayRow>()
        var maxEnd: Int? = null
        for (lesson in lessons.sortedWith(compareBy({ it.order }, { it.subject }))) {
            val s = minutesOfDay(lesson.start)
            val e = minutesOfDay(lesson.end)
            if (s != null && maxEnd != null && s > maxEnd) {
                val gap = s - maxEnd
                rows.add(DayRow.GapRow(gap, gap >= WINDOW_MIN))
            }
            if (e != null) maxEnd = if (maxEnd == null) e else maxOf(maxEnd, e)
            rows.add(DayRow.LessonRow(lesson, if (s != null && e != null) "${lesson.start}–${lesson.end}" else ""))
        }
        return rows
    }

    /** Семь дней недели (с понедельника), в которую попадает [anyDateIso], со сдвигом на [weekOffset] недель. */
    fun weekDates(anyDateIso: String, weekOffset: Int = 0): List<String> {
        val monday = shiftIso(anyDateIso, -(weekdayNumber(anyDateIso) - 1) + 7 * weekOffset) ?: return emptyList()
        return (0..6).mapNotNull { shiftIso(monday, it) }
    }

    /** Понедельник = 1 … воскресенье = 7. Неразборчивая дата считается понедельником. */
    fun weekdayNumber(iso: String): Int {
        val p = iso.split("-")
        val y = p.getOrNull(0)?.toIntOrNull() ?: return 1
        val m = p.getOrNull(1)?.toIntOrNull() ?: return 1
        val d = p.getOrNull(2)?.toIntOrNull() ?: return 1
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(y, m - 1, d)
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // у Java воскресенье = 1
    }

    /** «yyyy-MM-dd» момента [nowMs] в часовом поясе [zone]. */
    fun isoOf(nowMs: Long, zone: TimeZone): String {
        val c = GregorianCalendar(zone)
        c.timeInMillis = nowMs
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** «2026-10-05» + n дней. Неразборчивая дата: null. */
    fun shiftIso(iso: String, days: Int): String? {
        val p = iso.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(y, m - 1, d)
        c.add(Calendar.DAY_OF_MONTH, days)
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** «08:00» момента [ms] в часовом поясе [zone]. */
    fun hm(ms: Long, zone: TimeZone): String {
        val c = GregorianCalendar(zone)
        c.timeInMillis = ms
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
