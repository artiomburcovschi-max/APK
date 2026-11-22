package com.agenda.client

import org.jsoup.Jsoup

/**
 * Окно «Detalii lecție» портала (страница calendar-info) в виде, удобном приложению.
 * Даты внутри приложения хранятся как «yyyy-MM-dd» (см. GradeDates).
 *
 * @property topic тема урока («Subiectul lecției»), пусто, если учитель не заполнил
 * @property teacherMessage «Mesajul profesorului»
 * @property gradeText то, что стоит в окошке «Nota / absență»: оценка, «a» (пропуск) или пусто
 * @property homeworkText текст задания («Activități de învățare»)
 * @property homeworkFor «Pentru data de»: на какой день задано
 * @property homeworkPostedRaw «Data completării de către profesor» как есть: формат с временем или без нам не известен,
 *   пока в присланных страницах это поле было пустым
 * @property homeworkPostedOn то же, но только дата, если её удалось выделить
 * @property isSummative суммативная оценка (поле P19_SUMATIVA = «Da»)
 * @property isThesis урок с тезой (поле P19_TEZA_FLG = «Da»)
 * @property meetingLink ссылка на онлайн-урок, только http/https
 */
data class LessonDetails(
    val date: String,
    val order: Int,
    val discipline: String,
    val topic: String,
    val teacherMessage: String,
    val gradeText: String,
    val homeworkText: String,
    val homeworkFor: String?,
    val homeworkPostedRaw: String,
    val homeworkPostedOn: String?,
    val isSummative: Boolean,
    val isThesis: Boolean,
    val meetingLink: String?
)

/**
 * Разбор HTML окна «Detalii lecție». Чистая логика (jsoup), проверяется JUnit.
 * Читает видимые блоки страницы, а где их нет, скрытые поля P19_*.
 * Если страница не похожа на окно урока (например, портал вернул страницу входа), возвращает null.
 */
object LessonDetailsParser {

    private val dateInText = Regex("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")

    fun parse(html: String): LessonDetails? {
        val doc = Jsoup.parse(html)
        if (doc.selectFirst(".lesson-view") == null) return null

        fun field(name: String): String = doc.selectFirst("input[name=$name]")?.attr("value")?.trim().orEmpty()
        // pre-line в стиле портала: переносы строк внутри блока важны, поэтому wholeText, а не text
        fun block(selector: String): String =
            doc.selectFirst(selector)?.wholeText()?.trim().orEmpty()

        val date = GradeDates.toIso(doc.selectFirst(".lesson-hero-date span")?.text().orEmpty())
            ?: GradeDates.toIso(field("P19_LESSON_DATE"))
            ?: return null
        val order = doc.selectFirst(".lesson-hero-badge")?.text()?.trim()?.toIntOrNull()
            ?: field("P19_LESSON_ORDER_ID").toIntOrNull()
            ?: return null

        val homeworkFor = doc.select("li.sub-text").firstOrNull { it.text().startsWith("Pentru data de") }
            ?.text()?.let { dateInText.find(it)?.value }?.let { GradeDates.toIso(it) }
        val postedRaw = doc.select("li.sub-text").firstOrNull { it.text().startsWith("Data completării") }
            ?.text()?.substringAfter(":", "")?.trim().orEmpty()

        return LessonDetails(
            date = date,
            order = order,
            discipline = doc.selectFirst(".lesson-hero-title")?.text()?.trim().orEmpty().ifEmpty { field("P19_DISCIPLINY_NAME") },
            topic = block(".lesson-text").ifEmpty { field("P19_LESSON_SUBJECT") },
            teacherMessage = block(".lesson-message"),
            gradeText = doc.selectFirst(".lesson-grade-pill")?.text()?.trim().orEmpty(),
            homeworkText = block(".lesson-homework-box").ifEmpty { field("P19_HOME_WORK_TXT") },
            homeworkFor = homeworkFor,
            homeworkPostedRaw = postedRaw,
            homeworkPostedOn = dateInText.find(postedRaw)?.value?.let { GradeDates.toIso(it) },
            isSummative = field("P19_SUMATIVA").equals("Da", ignoreCase = true),
            isThesis = field("P19_TEZA_FLG").equals("Da", ignoreCase = true),
            meetingLink = meetingLink(::field)
        )
    }

    /** Ссылка из P19_MEETING_LINK или из HTML-вставки P19_MEETING_HTML. Чужие схемы (javascript: и т.п.) отбрасываем. */
    private fun meetingLink(field: (String) -> String): String? {
        val direct = field("P19_MEETING_LINK")
        val candidate = direct.ifEmpty {
            val inserted = field("P19_MEETING_HTML")
            if (inserted.isEmpty()) "" else Jsoup.parseBodyFragment(inserted).selectFirst("a[href]")?.attr("href").orEmpty()
        }
        val url = candidate.trim()
        return if (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) url else null
    }
}
