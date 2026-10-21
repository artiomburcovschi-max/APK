package com.agenda.client

import java.util.Locale

/**
 * Чистая логика работы с датами оценок (без Android).
 * Внутри приложения даты хранятся как "yyyy-MM-dd": такие строки правильно сортируются как текст.
 */
object GradeDates {

    /** "12.10.2026" (формат портала) -> "2026-10-12". Если формат неверный, вернёт null. */
    fun toIso(portalDate: String): String? {
        val p = portalDate.trim().split(".")
        if (p.size != 3) return null
        val d = p[0].trim().toIntOrNull()
        val m = p[1].trim().toIntOrNull()
        val y = p[2].trim().toIntOrNull()
        if (d == null || m == null || y == null) return null
        if (d !in 1..31 || m !in 1..12 || y < 2000) return null
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    /** "2026-10-12" -> "12.10.2026" */
    fun toDisplay(iso: String): String? {
        val p = iso.split("-")
        if (p.size != 3 || p[0].length != 4) return null
        return "${p[2]}.${p[1]}.${p[0]}"
    }

    /** "2026-10-12" -> "12.10" (для подписей на графике) */
    fun toShort(iso: String): String? {
        val p = iso.split("-")
        if (p.size != 3 || p[0].length != 4) return null
        return "${p[2]}.${p[1]}"
    }

    /** Названия предметов на портале могут отличаться пробелами и регистром: приводим к одному виду. */
    fun normalize(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    /**
     * Сопоставляет даты из блока "Note recente" с оценками предмета.
     *
     * scores        - полный список оценок предмета (старые -> новые, как в таблице).
     * recentChrono  - свежие оценки ЭТОГО предмета из блока "Note recente" (старые -> новые).
     *
     * Идём с конца обоих списков: пока оценки совпадают, дата из recent относится к оценке из scores.
     * Как только оценки разошлись, останавливаемся: дальше привязка была бы догадкой.
     * Возвращает: позиция оценки -> дата "yyyy-MM-dd".
     */
    fun alignTail(scores: List<Int>, recentChrono: List<RecentEval>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        var i = scores.size - 1
        var j = recentChrono.size - 1
        while (i >= 0 && j >= 0 && scores[i] == recentChrono[j].score) {
            result[i] = recentChrono[j].date
            i--
            j--
        }
        return result
    }
}
