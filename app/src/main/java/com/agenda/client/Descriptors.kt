package com.agenda.client

import java.text.Normalizer

/**
 * Предметы с баллами по дескрипторам (у нас это «Educaţie pentru societate»): там вместо оценок 1–10 стоят баллы вроде
 * 25, 27, 35. В средние они не входят: иначе общий средний и график съезжают (22.50 по такому предмету против 10 максимум).
 *
 * Предмет считается «дескрипторным», если выполнено любое из двух:
 *  1) название похоже на «Educaţie pentru societate» (с любыми диакритиками, регистром и «Educaţia»);
 *  2) среди оценок есть значение выше 10: настоящих оценок больше десяти не бывает.
 * Первое нужно, потому что в начале семестра по такому предмету может стоять один балл «10», и по данным он неотличим от оценки.
 */
object Descriptors {

    const val MAX_MARK = GoalPlanner.MAX_GRADE

    /** Название без диакритики, в нижнем регистре, пробелы схлопнуты: «Educaţie  pentru Societate» -> «educatie pentru societate». */
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isDescriptorName(subject: String): Boolean {
        val n = normalize(subject)
        return n.contains("educati") && n.contains("societate")
    }

    fun isDescriptorSubject(item: SubjectGrades): Boolean =
        isDescriptorName(item.subject) || item.scores.any { it > MAX_MARK }

    /** Один балл из ленты «Новые оценки»: баллом по дескриптору считается всё выше 10 и всё по предмету с такими баллами. */
    fun isDescriptorScore(subject: String, score: Int): Boolean = score > MAX_MARK || isDescriptorName(subject)

    /** Только предметы с настоящими оценками: их и считаем в средних, на графике и в целях. */
    fun marksOnly(items: List<SubjectGrades>): List<SubjectGrades> = items.filterNot { isDescriptorSubject(it) }

    /** Названия дескрипторных предметов с оценками, без повторов, в порядке появления. */
    fun namesOf(items: List<SubjectGrades>): List<String> =
        items.filter { it.scores.isNotEmpty() && isDescriptorSubject(it) }.map { it.subject }.distinct()

    /** Строка под общим средним: какие предметы не вошли и почему. null: таких предметов нет. */
    fun note(names: List<String>): String? =
        if (names.isEmpty()) null else "Не входят в средний (дескрипторы): " + names.joinToString(", ")
}
