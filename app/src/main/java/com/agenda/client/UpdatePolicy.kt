package com.agenda.client

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Что написано в latest.json. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
    val sha256: String = ""
)

/** Итог проверки обновлений: есть новая версия, версия последняя или проверить не удалось (с причиной). */
sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class UpToDate(val serverVersionName: String) : UpdateCheckResult()
    data class Failed(val reason: String) : UpdateCheckResult()
}

/** Что делать с версией из latest.json относительно установленной. */
enum class UpdateVerdict { NEWER, UP_TO_DATE, UNTRUSTED_LINK }

/** Сведения для экрана «О приложении»: что установлено и что последний раз видели на сервере. */
data class UpdateStatus(
    val installedName: String,
    val installedCode: Int,
    val lastSuccessAt: Long,   // 0 = успешной проверки ещё не было
    val serverName: String?,
    val serverCode: Int?
)

/** Чистая логика обновлений (без Android): когда проверять, что считать новой версией, чему доверять. */
object UpdatePolicy {

    /** После УСПЕШНОЙ проверки следующая автоматическая не раньше чем через 6 часов. */
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** Попытки (в том числе неудачные) не чаще раза в 10 минут: сбой сети не блокирует проверку на сутки. */
    const val RETRY_INTERVAL_MS = 10 * 60 * 1000L

    /**
     * Пора ли спрашивать сервер автоматически.
     * Раньше хранилась одна «время последней проверки», и она ставилась ДО запроса: любой сбой (нет сети,
     * сервер ответил ошибкой) блокировал проверку на сутки, и свежая версия не появлялась. Теперь два времени:
     * успеха (по нему большой интервал) и попытки (по ней короткая пауза, чтобы не долбить сервер).
     */
    fun shouldCheck(lastSuccessAt: Long, lastAttemptAt: Long, now: Long): Boolean {
        if (lastSuccessAt > now || lastAttemptAt > now) return true // часы перевели назад
        if (lastSuccessAt > 0 && now - lastSuccessAt < CHECK_INTERVAL_MS) return false
        if (lastAttemptAt > 0 && now - lastAttemptAt < RETRY_INTERVAL_MS) return false
        return true
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

    /** Версия новее установленной и ссылка надёжная? Различаем «последняя версия» и «ссылка не наша». */
    fun evaluate(currentVersionCode: Int, info: UpdateInfo, trustedPrefix: String): UpdateVerdict = when {
        info.versionCode <= currentVersionCode -> UpdateVerdict.UP_TO_DATE
        !isTrusted(info.apkUrl, trustedPrefix) -> UpdateVerdict.UNTRUSTED_LINK
        else -> UpdateVerdict.NEWER
    }

    /** Показывать ли баннер: версия новее установленной, не отложена пользователем, ссылка надёжная. */
    fun shouldShow(currentVersionCode: Int, info: UpdateInfo, dismissedVersionCode: Int, trustedPrefix: String): Boolean =
        evaluate(currentVersionCode, info, trustedPrefix) == UpdateVerdict.NEWER &&
            info.versionCode > dismissedVersionCode

    /**
     * Превращает ответ сервера в понятный итог. info = разобранный latest.json (null, если разобрать не вышло).
     * Раньше любая из этих неполадок молча давала «обновления нет»: теперь у каждой своя причина.
     */
    fun interpret(httpCode: Int, info: UpdateInfo?, currentVersionCode: Int, trustedPrefix: String): UpdateCheckResult {
        if (httpCode !in 200..299) {
            return UpdateCheckResult.Failed(
                "сервер ответил кодом $httpCode (файла latest.json нет по этому адресу или сайт ещё публикуется)"
            )
        }
        if (info == null) {
            return UpdateCheckResult.Failed("файл latest.json на сервере не разобрать, проверь его формат")
        }
        return when (evaluate(currentVersionCode, info, trustedPrefix)) {
            UpdateVerdict.NEWER -> UpdateCheckResult.Available(info)
            UpdateVerdict.UP_TO_DATE -> UpdateCheckResult.UpToDate(info.versionName)
            UpdateVerdict.UNTRUSTED_LINK -> UpdateCheckResult.Failed(
                "версия ${info.versionName} есть, но ссылка на APK не из доверенного репозитория " +
                    "(проверь apkUrl в latest.json)"
            )
        }
    }

    /** Человеческое объяснение сетевой ошибки. */
    fun networkFailureText(e: Throwable): String = when (e) {
        is UnknownHostException -> "нет интернета или сервер недоступен"
        is SocketTimeoutException -> "сервер не ответил вовремя"
        is SSLException -> "не удалось установить защищённое соединение (проверь дату и время на телефоне)"
        else -> "не удалось связаться с сервером (${e.javaClass.simpleName})"
    }

    /** latest.json, сохранённый Блокнотом или PowerShell, может начинаться с невидимого BOM и пробелов. */
    fun cleanJson(text: String): String = text.trim().removePrefix("\uFEFF").trim()
}

/** Тексты для экрана «О приложении» (чистый Kotlin, чтобы проверять тестами). */
object UpdateText {

    fun result(result: UpdateCheckResult): String = when (result) {
        is UpdateCheckResult.Available -> "Доступна версия ${result.info.versionName}"
        is UpdateCheckResult.UpToDate -> "У тебя последняя версия (на сервере ${result.serverVersionName})"
        is UpdateCheckResult.Failed -> "Не удалось проверить: ${result.reason}"
    }

    /** lastSuccessText = уже отформатированное время последней успешной проверки (null, если проверок не было). */
    fun status(status: UpdateStatus, lastSuccessText: String?): String {
        val lines = mutableListOf("Установлена: ${status.installedName} (${status.installedCode})")
        if (lastSuccessText == null) {
            lines.add("Проверка ещё не выполнялась")
        } else {
            lines.add("Последняя успешная проверка: $lastSuccessText")
            if (status.serverName != null && status.serverCode != null) {
                lines.add("На сервере: ${status.serverName} (${status.serverCode})")
            }
        }
        return lines.joinToString("\n")
    }
}
