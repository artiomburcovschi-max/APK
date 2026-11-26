package com.agenda.client

/** Текст, которым делятся приложением: коротко, с ссылкой на сайт (под тестами). */
object ShareText {
    const val APP_NAME = "WatchYourGrade"
    const val SITE_URL = "https://artiomburcovschi-max.github.io/grades-site/index.html"

    fun message(): String = "Смотрю оценки, расписание и домашку в приложении $APP_NAME. Скачать: $SITE_URL"
}
