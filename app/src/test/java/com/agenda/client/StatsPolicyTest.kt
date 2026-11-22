package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

class StatsPolicyTest {

    private val zone = TimeZone.getTimeZone("Europe/Chisinau")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        val c = GregorianCalendar(zone)
        c.clear()
        c.set(y, m - 1, d, h, min, 0)
        return c.timeInMillis
    }

    private val consented = StatsState(consent = StatsConsent.YES)

    // ---------- согласие ----------

    @Test
    fun withoutConsentNothingIsEverSent() {
        for (c in listOf(StatsConsent.UNSET, StatsConsent.NO)) {
            assertNull(StatsPolicy.nextPing(StatsState(consent = c), "0.8.2", at(2026, 11, 19), zone))
        }
    }

    @Test
    fun theFirstPingAfterConsentCarriesAllThreeMarks() {
        val p = StatsPolicy.nextPing(consented, "0.8.2", at(2026, 11, 19), zone)!!
        assertEquals("0.8.2", p.version)
        assertTrue(p.newWeek)
        assertTrue(p.newMonth)
        assertTrue(p.first)
    }

    // ---------- раз в сутки ----------

    @Test
    fun oncePerDayEvenIfTheAppIsOpenedManyTimes() {
        val now = at(2026, 11, 19, 8)
        val sent = StatsPolicy.afterSuccess(consented, now, zone)

        assertNull(StatsPolicy.nextPing(sent, "0.8.2", at(2026, 11, 19, 8, 5), zone))
        assertNull(StatsPolicy.nextPing(sent, "0.8.2", at(2026, 11, 19, 23, 59), zone))
        assertNotNull(StatsPolicy.nextPing(sent, "0.8.2", at(2026, 11, 20, 0, 1), zone))
    }

    @Test
    fun theDayChangesAtLocalMidnightNotUtc() {
        val sent = StatsPolicy.afterSuccess(consented, at(2026, 11, 19, 23, 30), zone)
        // 00:10 по Кишинёву уже следующий день, хотя по UTC ещё 22:10 того же дня
        assertNotNull(StatsPolicy.nextPing(sent, "v", at(2026, 11, 20, 0, 10), zone))
    }

    // ---------- отметки недели и месяца ----------

    @Test
    fun theWeekMarkIsSetOnlyOnTheFirstPingOfTheWeek() {
        val monday = StatsPolicy.afterSuccess(consented, at(2026, 11, 16), zone) // понедельник
        val tuesday = StatsPolicy.nextPing(monday, "v", at(2026, 11, 17), zone)!!
        assertFalse(tuesday.newWeek)
        assertFalse(tuesday.first)

        val sunday = StatsPolicy.nextPing(monday, "v", at(2026, 11, 22), zone)!! // воскресенье той же недели
        assertFalse(sunday.newWeek)

        val nextMonday = StatsPolicy.nextPing(monday, "v", at(2026, 11, 23), zone)!!
        assertTrue(nextMonday.newWeek)
    }

    @Test
    fun theMonthMarkIsSetOnlyOnTheFirstPingOfTheMonth() {
        val nov = StatsPolicy.afterSuccess(consented, at(2026, 11, 3), zone)
        assertFalse(StatsPolicy.nextPing(nov, "v", at(2026, 11, 30), zone)!!.newMonth)
        assertTrue(StatsPolicy.nextPing(nov, "v", at(2026, 12, 1), zone)!!.newMonth)
    }

    @Test
    fun aWeekCrossingTheMonthBoundaryGivesOneWeekMarkButTwoMonthMarks() {
        // неделя 30.11.2026 (пн) - 06.12.2026 (вс)
        val mon = StatsPolicy.afterSuccess(consented, at(2026, 11, 30), zone)
        val tue = StatsPolicy.nextPing(mon, "v", at(2026, 12, 1), zone)!!
        assertFalse(tue.newWeek)
        assertTrue(tue.newMonth)
    }

    @Test
    fun weekKeyIsTheMondayAndSundayBelongsToTheWeekThatEnds() {
        assertEquals("2026-11-16", StatsPolicy.weekKey(at(2026, 11, 16, 0, 0), zone))
        assertEquals("2026-11-16", StatsPolicy.weekKey(at(2026, 11, 19), zone))
        assertEquals("2026-11-16", StatsPolicy.weekKey(at(2026, 11, 22, 23, 59), zone))
        assertEquals("2026-11-23", StatsPolicy.weekKey(at(2026, 11, 23, 0, 0), zone))
        assertEquals("2026-12-28", StatsPolicy.weekKey(at(2027, 1, 1), zone)) // через границу года
    }

    // ---------- сбои ----------

    @Test
    fun aFailedAttemptIsNotRememberedAsASuccessButIsRateLimited() {
        val now = at(2026, 11, 19, 8)
        val afterAttempt = consented.copy(lastAttemptMs = now) // отправили, ответа об успехе нет

        assertNull(StatsPolicy.nextPing(afterAttempt, "v", now + 5 * 60_000L, zone))
        val retry = StatsPolicy.nextPing(afterAttempt, "v", now + StatsPolicy.RETRY_INTERVAL_MS, zone)
        assertNotNull(retry)
        assertTrue(retry!!.first) // успеха не было, значит отметки те же
        assertTrue(retry.newWeek)
    }

    @Test
    fun aClockSetBackDoesNotBlockSendingForever() {
        val future = consented.copy(lastAttemptMs = at(2030, 1, 1))
        assertNotNull(StatsPolicy.nextPing(future, "v", at(2026, 11, 19), zone))
    }

    @Test
    fun firstIsSentOnlyOnceEvenAfterToggling() {
        var s = StatsPolicy.afterSuccess(consented, at(2026, 11, 19), zone)
        s = s.copy(consent = StatsConsent.NO)
        s = s.copy(consent = StatsConsent.YES)
        assertFalse(StatsPolicy.nextPing(s, "v", at(2026, 11, 24), zone)!!.first)
    }

    // ---------- что уходит в сеть ----------

    @Test
    fun jsonContainsOnlyTheVersionAndThreeMarks() {
        assertEquals("{\"v\":\"0.8.2\",\"w\":1,\"m\":0,\"f\":1}", StatsPing("0.8.2", true, false, true).toJson())
        assertEquals("{\"v\":\"1.0-beta\",\"w\":0,\"m\":0,\"f\":0}", StatsPing("1.0-beta", false, false, false).toJson())
    }

    @Test
    fun anOddVersionCannotBreakTheJson() {
        assertEquals("?", StatsPolicy.cleanVersion("1.0\",\"x\":\"y"))
        assertEquals("?", StatsPolicy.cleanVersion(""))
        assertEquals("?", StatsPolicy.cleanVersion("v".repeat(21)))
        assertEquals("?", StatsPolicy.cleanVersion("версия"))
        assertEquals("0.8.2", StatsPolicy.cleanVersion(" 0.8.2 "))
        assertTrue(StatsPing("1.0\"}", true, true, true).toJson().startsWith("{\"v\":\"?\""))
    }
    @Test
    fun everyVersionTheClientCanSendMatchesTheServerPattern() {
        // Тот же шаблон проверяет сервер (worker/src/index.js, VERSION_RE). Меняешь здесь, меняй там.
        val serverPattern = Regex("[0-9A-Za-z.\\-?]{1,20}")
        val inputs = listOf("0.8.2", "1.0-beta", "10.20.30", "", "   ", "bad\"ver", "версия", "v".repeat(30), "?", "1.0\",\"x\":\"y")
        for (input in inputs) {
            val sent = StatsPing(input, newWeek = true, newMonth = true, first = true).toJson()
            val version = Regex("\"v\":\"([^\"]*)\"").find(sent)!!.groupValues[1]
            assertTrue("версия «$version» из «$input» не проходит шаблон сервера", serverPattern.matches(version))
        }
    }
}
