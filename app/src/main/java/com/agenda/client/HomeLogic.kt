package com.agenda.client

/** Одно задание для блока «Домашка» на главной. dayTitle: «Понедельник, 05.10.2026». */
data class HomeHw(val dayTitle: String, val subject: String, val text: String)

/** Что показывает экран «Главная» (чистая логика, проверяется JUnit). */
object HomeLogic {

    const val MAX_RECENT = 5
    const val MAX_HOMEWORK = 4

    /** Последние оценки для главной: лента уже отсортирована от новых к старым, берём начало. */
    fun recentToShow(feed: List<FeedItem>, max: Int = MAX_RECENT): List<FeedItem> = feed.take(max)

    /**
     * Ближайшие задания: сегодняшние и будущие, по дням от ближайшего. Берём те же задания, что и экран «Домашка»
     * (без повторов, из календаря и из списка заданий), только без блока «Последние дни»: на главной нужно то, что ещё предстоит.
     */
    fun homeworkSoon(extras: PortalExtras, todayIso: String, max: Int = MAX_HOMEWORK): List<HomeHw> {
        val rows = HomeworkLogic.rows(extras, todayIso, pastDays = 0)
        var day = ""
        val result = mutableListOf<HomeHw>()
        for (row in rows) {
            when (row) {
                is HwRow.DateHeader -> day = row.title
                is HwRow.Item -> if (result.size < max) result.add(HomeHw(day, row.subject, row.text))
                is HwRow.Section -> Unit
            }
        }
        return result
    }
}
