package com.agenda.client

/**
 * Этапы обновления оценок. base = процент, с которого начинается этап; step = на сколько процентов
 * продвигает каждая секунда ожидания внутри этапа (JS-автомат опрашивает страницу раз в секунду);
 * процент никогда не выходит за границу этапа (cap), чтобы «ожидание» не перепрыгнуло следующие шаги.
 * Проценты примерные: портал не говорит, сколько ему ещё осталось.
 * Имена этапов (name) используются в JS-автомате, переименовывать нельзя без правки скрипта.
 */
enum class RefreshStage(val base: Int, val step: Int, val label: String) {
    OPENING(5, 0, "открываю портал"),
    PAGE_LOADED(15, 1, "ищу вход на странице"),
    CHOOSING_ROLE(25, 1, "выбираю тип входа"),
    LOGGING_IN(40, 1, "ввожу логин и пароль"),
    SUBMITTED(55, 1, "жду ответ портала после входа"),
    WAITING_TABLE(70, 2, "жду таблицу оценок"),
    READING(90, 0, "читаю оценки"),
    SAVING(96, 0, "сохраняю историю");

    /** Максимум процентов внутри этапа: на единицу меньше начала следующего (у последнего 99). */
    val cap: Int get() = (entries.getOrNull(ordinal + 1)?.base ?: 100) - 1

    /** Процент на этапе после sub секунд ожидания. */
    fun percent(sub: Int): Int = minOf(base + maxOf(0, sub) * step, cap)

    companion object {
        /** Неизвестное имя (например, от будущей версии скрипта) -> null, такой сигнал игнорируем. */
        fun fromName(name: String?): RefreshStage? = entries.firstOrNull { it.name == name }
    }
}

data class RefreshProgress(val stage: RefreshStage, val percent: Int) {
    companion object {
        /**
         * Новое состояние после сигнала «этап stage, прошло sub секунд». Прогресс только растёт:
         * запоздавший сигнал более раннего этапа и меньший процент игнорируются, иначе полоска дёргалась бы назад.
         */
        fun advance(current: RefreshProgress?, stage: RefreshStage, sub: Int = 0): RefreshProgress {
            if (current != null && stage.ordinal < current.stage.ordinal) return current
            val percent = stage.percent(sub)
            return RefreshProgress(stage, if (current == null) percent else maxOf(current.percent, percent))
        }
    }
}
