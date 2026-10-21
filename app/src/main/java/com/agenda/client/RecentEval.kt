package com.agenda.client

import org.jsoup.Jsoup

/** Одна строка из блока "Note recente" на главной странице портала. date = "yyyy-MM-dd". */
data class RecentEval(val date: String, val subject: String, val score: Int)

/**
 * Достаёт блок "Note recente". Там у каждой оценки есть настоящая дата, поэтому он
 * наш единственный надёжный источник дат. В блоке бывает до 5 последних оценок по всем предметам.
 * Порядок результата такой же, как на странице: от новых к старым.
 */
object RecentEvalParser {

    fun parse(html: String): List<RecentEval> {
        val doc = Jsoup.parse(html)

        // Основной источник: скрытое поле, в котором лежит готовый HTML списка.
        // Запасной: уже нарисованный на странице список.
        val raw = doc.select("#P1_LAST_3_EVAL_LIST").attr("value")
        val items = if (raw.isNotBlank()) Jsoup.parse(raw).select("li") else doc.select("#last3-eval li")

        val result = mutableListOf<RecentEval>()
        for (li in items) {
            val date = GradeDates.toIso(li.select(".eval-date").text()) ?: continue
            val subject = li.select(".eval-discipline").text().trim().removeSuffix(":").trim()
            val score = li.select(".grade").text().trim().toIntOrNull() ?: continue
            if (subject.isEmpty()) continue
            result.add(RecentEval(date, subject, score))
        }
        return result
    }
}
