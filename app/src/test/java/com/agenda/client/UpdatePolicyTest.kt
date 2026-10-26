package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class UpdatePolicyTest {

    private val prefix = "https://github.com/me/site/releases/download/"
    private val hour = 60 * 60 * 1000L
    private val minute = 60 * 1000L
    private val now = 1_000_000_000_000L

    private fun info(code: Int, url: String = prefix + "v2/app.apk") =
        UpdateInfo(code, "1.$code.0", url)

    // ---- когда проверять ----

    @Test
    fun checksOnFirstLaunch() {
        assertTrue(UpdatePolicy.shouldCheck(0L, 0L, now))
    }

    @Test
    fun afterSuccessWaitsSixHours() {
        val success = now - 5 * hour - 59 * minute
        assertFalse(UpdatePolicy.shouldCheck(success, success, now))

        val later = now - 6 * hour
        assertTrue(UpdatePolicy.shouldCheck(later, later, now))
    }

    /** Главная регрессия: раньше сбой сети блокировал проверку на сутки, и свежая версия не появлялась. */
    @Test
    fun failedAttemptDoesNotBlockForLong() {
        // успеха ещё не было, последняя попытка (неудачная) 5 минут назад: ждём, чтобы не долбить сервер
        assertFalse(UpdatePolicy.shouldCheck(0L, now - 5 * minute, now))
        // …а через 10 минут пробуем снова
        assertTrue(UpdatePolicy.shouldCheck(0L, now - 10 * minute, now))
        // успех был давно (три дня назад), попытка 11 минут назад не удалась: пробуем снова
        assertTrue(UpdatePolicy.shouldCheck(now - 72 * hour, now - 11 * minute, now))
    }

    @Test
    fun freshSuccessBlocksEvenIfAttemptIsOld() {
        // успех час назад: по нему большой интервал, спрашивать рано
        assertFalse(UpdatePolicy.shouldCheck(now - hour, now - hour, now))
    }

    @Test
    fun clockSetBackDoesNotBlockChecks() {
        assertTrue(UpdatePolicy.shouldCheck(now, now, now - 1000))
        assertTrue(UpdatePolicy.shouldCheck(0L, now, now - 1000))
    }

    @Test
    fun defaultsAreSane() {
        assertEquals(6 * 60 * 60 * 1000L, UpdatePolicy.CHECK_INTERVAL_MS)
        assertEquals(10 * 60 * 1000L, UpdatePolicy.RETRY_INTERVAL_MS)
    }

    // ---- чему доверять ----

    @Test
    fun trustsOnlyLinksUnderOurPrefix() {
        assertTrue(UpdatePolicy.isTrusted(prefix + "v2/app.apk", prefix))
        assertFalse(UpdatePolicy.isTrusted("https://evil.com/app.apk", prefix))
        assertFalse(UpdatePolicy.isTrusted("http://github.com/me/site/releases/download/v2/app.apk", prefix))
        assertFalse(UpdatePolicy.isTrusted("https://github.com/me/site2/releases/download/app.apk", prefix))
        assertFalse(UpdatePolicy.isTrusted(prefix + "../../evil.apk", prefix))
        assertFalse(UpdatePolicy.isTrusted(prefix + "my app.apk", prefix))
    }

    @Test
    fun refusesWeakOrEmptyPrefix() {
        assertFalse(UpdatePolicy.isTrusted("https://github.com/x.apk", ""))
        assertFalse(UpdatePolicy.isTrusted("http://a.com/x.apk", "http://a.com/"))
        assertFalse(UpdatePolicy.isTrusted("https://a.com/x.apk", "https://a.com")) // без слэша на конце
    }

    @Test
    fun showsOnlyNewerNotDismissedTrusted() {
        assertTrue(UpdatePolicy.shouldShow(100, info(200), 0, prefix))
        assertFalse(UpdatePolicy.shouldShow(200, info(200), 0, prefix))                    // уже стоит
        assertFalse(UpdatePolicy.shouldShow(300, info(200), 0, prefix))                    // у нас новее
        assertFalse(UpdatePolicy.shouldShow(100, info(200), 200, prefix))                  // отложено
        assertTrue(UpdatePolicy.shouldShow(100, info(300), 200, prefix))                   // вышла ещё новее
        assertFalse(UpdatePolicy.shouldShow(100, info(200, "https://evil.com/a.apk"), 0, prefix))
    }

    @Test
    fun evaluateTellsUntrustedLinkFromUpToDate() {
        assertEquals(UpdateVerdict.NEWER, UpdatePolicy.evaluate(100, info(200), prefix))
        assertEquals(UpdateVerdict.UP_TO_DATE, UpdatePolicy.evaluate(200, info(200), prefix))
        assertEquals(UpdateVerdict.UNTRUSTED_LINK, UpdatePolicy.evaluate(100, info(200, "https://evil.com/a.apk"), prefix))
        // у нас уже новее: ссылку даже не проверяем, это просто «последняя версия»
        assertEquals(UpdateVerdict.UP_TO_DATE, UpdatePolicy.evaluate(300, info(200, "https://evil.com/a.apk"), prefix))
    }

    // ---- итог проверки с причиной ----

    @Test
    fun interpretGivesAvailableUpToDateAndReasons() {
        assertEquals(UpdateCheckResult.Available(info(200)), UpdatePolicy.interpret(200, info(200), 100, prefix))
        assertEquals(UpdateCheckResult.UpToDate("1.200.0"), UpdatePolicy.interpret(200, info(200), 200, prefix))

        val http404 = UpdatePolicy.interpret(404, null, 100, prefix) as UpdateCheckResult.Failed
        assertTrue(http404.reason.contains("404"))

        val broken = UpdatePolicy.interpret(200, null, 100, prefix) as UpdateCheckResult.Failed
        assertTrue(broken.reason.contains("latest.json"))

        val untrusted = UpdatePolicy.interpret(200, info(200, "https://evil.com/a.apk"), 100, prefix)
            as UpdateCheckResult.Failed
        assertTrue(untrusted.reason.contains("доверенного"))
        assertTrue(untrusted.reason.contains("1.200.0"))
    }

    @Test
    fun networkErrorsAreExplainedInHumanWords() {
        assertTrue(UpdatePolicy.networkFailureText(UnknownHostException("x")).contains("нет интернета"))
        assertTrue(UpdatePolicy.networkFailureText(SocketTimeoutException("x")).contains("не ответил"))
        assertTrue(UpdatePolicy.networkFailureText(SSLHandshakeException("x")).contains("дату и время"))
        assertTrue(UpdatePolicy.networkFailureText(IOException("x")).contains("IOException"))
    }

    @Test
    fun cleanJsonStripsBomAndSpaces() {
        assertEquals("{\"a\":1}", UpdatePolicy.cleanJson("\uFEFF{\"a\":1}\n"))
        assertEquals("{\"a\":1}", UpdatePolicy.cleanJson("  \n{\"a\":1}  "))
        assertEquals("{\"a\":1}", UpdatePolicy.cleanJson("{\"a\":1}"))
    }

    // ---- тексты экрана ----

    @Test
    fun resultTexts() {
        assertEquals("Доступна версия 1.200.0", UpdateText.result(UpdateCheckResult.Available(info(200))))
        assertEquals(
            "У тебя последняя версия (на сервере 0.4.0)",
            UpdateText.result(UpdateCheckResult.UpToDate("0.4.0"))
        )
        assertEquals(
            "Не удалось проверить: нет интернета или сервер недоступен",
            UpdateText.result(UpdateCheckResult.Failed("нет интернета или сервер недоступен"))
        )
    }

    @Test
    fun statusText() {
        val seen = UpdateStatus("0.3.0", 300, now, "0.4.0", 400)
        assertEquals(
            "Установлена: 0.3.0 (300)\nПоследняя успешная проверка: 26 окт, 14:03\nНа сервере: 0.4.0 (400)",
            UpdateText.status(seen, "26 окт, 14:03")
        )
        val never = UpdateStatus("0.3.0", 300, 0L, null, null)
        assertEquals(
            "Установлена: 0.3.0 (300)\nПроверка ещё не выполнялась",
            UpdateText.status(never, null)
        )
    }
}
