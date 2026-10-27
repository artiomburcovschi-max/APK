package com.agenda.client

import java.util.Locale

/** До и после для одного показателя: среднего предмета или общего среднего. */
data class WhatIfLine(val label: String, val before: Double, val after: Double) {
    val delta: Double get() = after - before

    val tone: Tone
        get() = when {
            delta >= 0.005 -> Tone.GOOD
            delta <= -0.005 -> Tone.BAD
            else -> Tone.NEUTRAL
        }

    /** «+0.25», «-0.06» или «без изменений» (если разница меньше сотой). */
    val deltaText: String
        get() = if (tone == Tone.NEUTRAL) "без изменений" else String.format(Locale.US, "%+.2f", delta)

    val text: String
        get() = String.format(Locale.US, "%s: %.2f → %.2f", label, before, after)
}

/**
 * «А что если»: как изменятся средний предмета и общий средний, если получить такие оценки (чистый Kotlin).
 * Общий = среднее из средних по предметам, как в «Обзоре» и в целях. В данные ничего не записывается.
 */
object WhatIf {

    /** Столько гипотетических оценок за раз хватит с запасом, больше только захламит экран. */
    const val MAX_ADDED = 12

    /** subject не нашли среди предметов: null. */
    fun compute(subjects: List<SubjectScores>, subject: String, added: List<Int>): List<WhatIfLine>? {
        val data = subjects.filter { it.scores.isNotEmpty() }
        val target = data.firstOrNull { it.name == subject } ?: return null

        val subjectBefore = target.scores.average()
        val subjectAfter = (target.scores + added).average()
        val overallBefore = data.map { it.scores.average() }.average()
        val overallAfter = data.map { if (it === target) subjectAfter else it.scores.average() }.average()

        return listOf(
            WhatIfLine("Предмет", subjectBefore, subjectAfter),
            WhatIfLine("Общий средний", overallBefore, overallAfter)
        )
    }

    /** «Добавлено: 10, 9». */
    fun addedText(added: List<Int>): String =
        if (added.isEmpty()) "" else "Если получить: " + added.joinToString(", ")
}
