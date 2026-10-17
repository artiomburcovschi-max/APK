package com.agenda.client

import org.jsoup.Jsoup

/**
 * Чистая логика: HTML страницы -> список предметов с оценками.
 * Здесь нет Android-классов (Log, Context, Toast), поэтому парсер
 * проверяется обычными unit-тестами на компьютере.
 */
object GradesParser {

    fun parse(html: String): List<SubjectGrades> {
        val doc = Jsoup.parse(html)
        val result = mutableListOf<SubjectGrades>()

        // report_table_sem1, report_table_sem2 ... по одной таблице на семестр
        for (table in doc.select("table[id^=report_table_sem]")) {
            // Название семестра берём как есть. Если его нет, остаётся пустая строка:
            // что показать пользователю вместо него, решает интерфейс, а не парсер.
            val semester = table.attr("aria-label")

            for (row in table.select("tbody tr")) {
                // Ячейки ищем по атрибуту headers, а не по порядковому номеру.
                // Если сайт поменяет порядок колонок, парсер продолжит работать.
                val subjectCell = row.select("td[headers=DISCIPLINA]").first()
                    ?: continue // строка без названия предмета (служебная) - пропускаем

                val subject = subjectCell.text().trim()
                if (subject.isEmpty()) continue

                // headers="LISTA_EVALUATION1" в 1-м семестре и, скорее всего,
                // LISTA_EVALUATION2 во 2-м, поэтому сравниваем только начало ([headers^=...])
                val scoresText = row.select("td[headers^=LISTA_EVALUATION]").first()?.text().orEmpty()
                val scores = scoresText.split(",").mapNotNull { it.trim().toIntOrNull() }

                val media = row.select("td[headers^=MED_SEM]").first()?.text()?.trim().orEmpty()

                result.add(SubjectGrades(subject, scores, semester, media))
            }
        }
        return result
    }
}