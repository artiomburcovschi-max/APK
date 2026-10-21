package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val prefix = "https://github.com/me/site/releases/download/"
    private val day = UpdatePolicy.CHECK_INTERVAL_MS
    private val t0 = 1_000_000_000L

    private fun info(code: Int, url: String = prefix + "v2/app.apk") =
        UpdateInfo(code, "1.$code.0", url)

    @Test
    fun checksOnFirstLaunchAndOncePerDay() {
        assertTrue(UpdatePolicy.shouldCheck(0L, t0))
        assertFalse(UpdatePolicy.shouldCheck(t0, t0 + day - 1))
        assertTrue(UpdatePolicy.shouldCheck(t0, t0 + day))
    }

    @Test
    fun clockSetBackDoesNotBlockChecks() {
        assertTrue(UpdatePolicy.shouldCheck(t0, t0 - 1000))
    }

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
    fun defaultsAreSane() {
        assertEquals(24 * 60 * 60 * 1000L, UpdatePolicy.CHECK_INTERVAL_MS)
    }
}
