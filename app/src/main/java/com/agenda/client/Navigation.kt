package com.agenda.client

/** Пять вкладок нижней панели. Порядок объявления = порядок слева направо. */
enum class Tab { HOME, GRADES, ANALYTICS, SCHEDULE, MORE }

/**
 * Все экраны приложения. [key] это метка фрагмента (по ней FragmentManager находит экран после поворота) и значение,
 * которое виджет и уведомление кладут в Intent, чтобы открыть нужный раздел. Менять ключи нельзя: они уже живут в Intent.
 *
 * @property tab вкладка, которая подсвечена на этом экране (у подраздела: если не знаем, откуда пришли)
 * @property isRoot корневой экран вкладки (подраздел открывается поверх и возвращается кнопкой «назад»)
 */
enum class Section(val key: String, val title: String, val tab: Tab, val isRoot: Boolean) {
    HOME("home", "Главная", Tab.HOME, true),
    GRADES("grades", "Оценки", Tab.GRADES, true),
    ANALYTICS("analytics", "Аналитика", Tab.ANALYTICS, true),
    SCHEDULE("schedule", "Расписание", Tab.SCHEDULE, true),
    MORE("more", "Ещё", Tab.MORE, true),

    RECENT("recent", "Новые оценки", Tab.HOME, false), // ведёт сюда «Главная», поэтому по умолчанию подсвечена она
    DIARY("diary", "Дневник", Tab.MORE, false),
    HOMEWORK("homework", "Домашка", Tab.MORE, false),
    ABSENCES("absences", "Пропуски", Tab.MORE, false),
    TEACHERS("teachers", "Учителя", Tab.MORE, false),
    ACCOUNT("account", "Аккаунт", Tab.MORE, false),
    SETTINGS("settings", "Настройки", Tab.MORE, false),
    ABOUT("about", "О приложении", Tab.MORE, false)
}

/** Правила навигации без Android (проверяются JUnit): какая вкладка подсвечена, куда ведёт «назад», что в «Ещё». */
object NavLogic {

    /** С какого экрана открывается приложение. */
    val START = Section.HOME

    /** Вкладки нижней панели слева направо. */
    val TABS: List<Section> = listOf(Section.HOME, Section.GRADES, Section.ANALYTICS, Section.SCHEDULE, Section.MORE)

    /** Строки списка «Ещё» сверху вниз. «Новые оценки» сюда не входят: к ним ведёт «Главная». */
    val MORE_ITEMS: List<Section> = listOf(
        Section.DIARY, Section.HOMEWORK, Section.ABSENCES, Section.TEACHERS,
        Section.ACCOUNT, Section.SETTINGS, Section.ABOUT
    )

    fun fromKey(key: String?): Section? = Section.values().firstOrNull { it.key == key }

    /** Корневой экран вкладки. */
    fun rootFor(tab: Tab): Section = TABS.first { it.tab == tab }

    /**
     * Какой раздел открыть по значению из Intent (виджет, уведомление). Только корневые экраны вкладок:
     * неизвестное или пустое значение даёт null, и приложение откроется как обычно.
     */
    fun fromOpenExtra(value: String?): Section? = fromKey(value)?.takeIf { it.isRoot }

    /**
     * Какая вкладка подсвечена. Корневой экран подсвечивает свою вкладку. Подраздел подсвечивает вкладку того
     * корня, откуда его открыли (например, «Все новые оценки» с «Главной»), а если не знаем откуда, то «Ещё».
     */
    fun highlightedTab(current: Section, originKey: String?): Tab {
        if (current.isRoot) return current.tab
        val origin = fromKey(originKey)
        return if (origin != null && origin.isRoot) origin.tab else current.tab
    }

    /** Куда ведёт «назад», когда стек подразделов пуст: с «Главной» выход из приложения (null), откуда угодно на «Главную». */
    fun backTarget(current: Section): Section? = if (current == START) null else START
}
