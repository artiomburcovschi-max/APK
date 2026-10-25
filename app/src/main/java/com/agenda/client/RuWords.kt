package com.agenda.client

/** Русские числительные: «1 десятка, 2 десятки, 5 десяток» (чистый Kotlin). */
object RuWords {

    /** Форма слова после числа: one (1, 21), few (2-4, 22-24), many (0, 5-20, 25-30...). */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val abs = kotlin.math.abs(n)
        val mod100 = abs % 100
        val mod10 = abs % 10
        return when {
            mod100 in 11..14 -> many // 11-14 всегда «many», хотя 11 и 12 оканчиваются на 1 и 2
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    /** «5 оценок». */
    fun grades(n: Int): String = "$n " + plural(n, "оценка", "оценки", "оценок")

    private val forms = mapOf(
        10 to Triple("десятка", "десятки", "десяток"),
        9 to Triple("девятка", "девятки", "девяток"),
        8 to Triple("восьмёрка", "восьмёрки", "восьмёрок"),
        7 to Triple("семёрка", "семёрки", "семёрок"),
        6 to Triple("шестёрка", "шестёрки", "шестёрок"),
        5 to Triple("пятёрка", "пятёрки", "пятёрок"),
        4 to Triple("четвёрка", "четвёрки", "четвёрок"),
        3 to Triple("тройка", "тройки", "троек"),
        2 to Triple("двойка", "двойки", "двоек"),
        1 to Triple("единица", "единицы", "единиц")
    )

    /** «5 десяток», «2 восьмёрки», «1 семёрка». */
    fun gradeCount(grade: Int, count: Int): String {
        val f = forms[grade] ?: return "$count × $grade"
        return "$count " + plural(count, f.first, f.second, f.third)
    }
}
