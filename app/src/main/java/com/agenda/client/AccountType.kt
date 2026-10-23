package com.agenda.client

/**
 * Кто входит в дневник. От этого зависит одно: какую картинку на стартовой странице портала
 * нажимает скрытый WebView (у родителя alt="Părinte", у ученика alt="Elev").
 * Всё остальное (форма входа, страница оценок, таблицы) у обоих одинаковое.
 *
 * Чистый Kotlin без Android-классов, чтобы его можно было проверять обычным JUnit-тестом.
 */
enum class AccountType(
    /** Что пишем в SharedPreferences. Менять нельзя: у уже вошедших людей лежит именно это. */
    val key: String,
    /** Что видит человек в приложении. */
    val label: String,
    /** Текст alt у кнопки на портале. */
    val portalAlt: String
) {
    PARENT("parent", "Родитель", "Părinte"),
    STUDENT("student", "Ученик", "Elev");

    companion object {
        /** Неизвестное или отсутствующее значение = родитель (так работали все версии до этой). */
        fun fromKey(key: String?): AccountType =
            values().firstOrNull { it.key == key } ?: PARENT
    }
}
