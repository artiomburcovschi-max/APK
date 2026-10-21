package com.agenda.client

/**
 * Настройки проверки обновлений. Пока оба значения пустые, проверка выключена
 * и приложение никуда не ходит. Заполним, когда появится сайт на GitHub Pages и первый релиз.
 */
object UpdateConfig {
    /** Где лежит latest.json, например "https://ИМЯ.github.io/agenda-site/latest.json" */
    const val UPDATE_URL = ""

    /**
     * Единственное место, откуда разрешено скачивать APK. Обязательно https и слэш на конце,
     * например "https://github.com/ИМЯ/agenda-site/releases/download/"
     */
    const val TRUSTED_APK_PREFIX = ""
}
