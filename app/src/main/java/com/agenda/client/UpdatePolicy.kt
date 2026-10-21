package com.agenda.client

/** Что написано в latest.json. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
    val sha256: String = ""
)

/** Чистая логика обновлений (без Android): когда проверять, что считать новой версией, чему доверять. */
object UpdatePolicy {

    /** Спрашиваем сервер не чаще раза в сутки. */
    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun shouldCheck(lastCheckAt: Long, now: Long): Boolean {
        if (lastCheckAt <= 0L) return true
        if (now < lastCheckAt) return true // часы перевели назад
        return now - lastCheckAt >= CHECK_INTERVAL_MS
    }

    /**
     * Ссылке на APK доверяем только если она целиком начинается с нашего адреса.
     * Даже если кто-то подменит latest.json, приложение не предложит скачать файл с чужого сайта.
     */
    fun isTrusted(url: String, trustedPrefix: String): Boolean {
        if (!trustedPrefix.startsWith("https://") || !trustedPrefix.endsWith("/")) return false
        if (!url.startsWith(trustedPrefix)) return false
        if (url.contains("..") || url.any { it.isWhitespace() }) return false
        return true
    }

    /** Показывать ли баннер: версия новее установленной, не отложена пользователем, ссылка надёжная. */
    fun shouldShow(currentVersionCode: Int, info: UpdateInfo, dismissedVersionCode: Int, trustedPrefix: String): Boolean =
        info.versionCode > currentVersionCode &&
            info.versionCode > dismissedVersionCode &&
            isTrusted(info.apkUrl, trustedPrefix)
}
