package com.agenda.client

import java.util.Locale

/**
 * Какие сторонние адреса не пускаем во встроенный браузер.
 * Страница портала сама подтягивает видео YouTube, иконки и счётчики. Для работы приложения
 * это не нужно, а лишние обращения к чужим серверам раскрывают им, что вы открыли портал.
 * Сам портал (agenda.sime.md и его поддомены) здесь никогда не блокируется.
 */
object HostPolicy {

    private val blockedDomains = listOf(
        "youtube.com",
        "youtube-nocookie.com",
        "ytimg.com",
        "googlevideo.com",
        "icons8.com",
        "google-analytics.com",
        "googletagmanager.com"
    )

    fun isBlocked(host: String): Boolean {
        val h = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (h.isEmpty()) return false
        return blockedDomains.any { h == it || h.endsWith(".$it") }
    }
}
