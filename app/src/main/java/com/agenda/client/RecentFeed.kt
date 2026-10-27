package com.agenda.client

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Строка таблицы grades как она лежит в базе. */
data class GradeRow(
    val semester: String,
    val subject: String,
    val position: Int,
    val score: Int,
    val firstSeenAt: Long,
    val updatedAt: Long,
    val gradeDate: String?
)

/** Запись ленты: что приложение заметило при обновлении. seenAt = когда заметило. */
data class FeedItem(
    val subject: String,
    val semester: String,
    val score: Int,
    val seenAt: Long,
    val gradeDate: String?,
    val modified: Boolean
)

/**
 * Лента «Новые оценки» (чистый Kotlin). Собирается из истории в базе, отдельного журнала нет.
 *
 * Самый первый запуск записывает все оценки с портала одним моментом: это «точка отсчёта», а не новости.
 * Поэтому оценки с самым ранним firstSeenAt в ленту не попадают, пока их не изменили.
 */
object RecentFeed {

    const val DEFAULT_LIMIT = 100

    fun build(rows: List<GradeRow>, limit: Int = DEFAULT_LIMIT): List<FeedItem> {
        if (rows.isEmpty()) return emptyList()
        val baselineAt = rows.minOf { it.firstSeenAt }

        val items = rows.mapNotNull { row ->
            when {
                // оценка изменилась уже после того, как мы её увидели
                row.updatedAt > row.firstSeenAt ->
                    FeedItem(row.subject, row.semester, row.score, row.updatedAt, row.gradeDate, true)
                // появилась после первой загрузки
                row.firstSeenAt > baselineAt ->
                    FeedItem(row.subject, row.semester, row.score, row.firstSeenAt, row.gradeDate, false)
                else -> null // была при первой загрузке и не менялась: не новость
            }
        }
        return items
            .sortedWith(compareByDescending<FeedItem> { it.seenAt }.thenBy { it.subject })
            .take(limit)
    }

    /** «оценка от 12.10.2026 · замечена 26.10 в 14:03» или «изменена · 26.10 в 14:03». */
    fun subtitle(item: FeedItem, zone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat("dd.MM 'в' HH:mm", Locale.US).apply { timeZone = zone }
        val seen = format.format(Date(item.seenAt))
        if (item.modified) return "оценка изменена · $seen"
        val date = item.gradeDate?.let { GradeDates.toDisplay(it) }
        return if (date != null) "оценка от $date · замечена $seen" else "замечена $seen"
    }
}
