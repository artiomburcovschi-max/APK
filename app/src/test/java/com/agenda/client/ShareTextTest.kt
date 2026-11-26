package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextTest {

    private val text = ShareText.message()

    @Test
    fun theMessageEndsWithTheSiteLinkExactlyOnce() {
        assertTrue(text.endsWith(ShareText.SITE_URL))
        assertEquals(1, text.split(ShareText.SITE_URL).size - 1)
    }

    @Test
    fun theLinkIsSecureAndPointsAtTheSite() {
        assertEquals("https://artiomburcovschi-max.github.io/grades-site/index.html", ShareText.SITE_URL)
    }

    @Test
    fun theMessageNamesTheAppAndStaysShort() {
        assertTrue(text.contains(ShareText.APP_NAME))
        assertTrue("коротко: ${text.length} знаков", text.length <= 140)
    }

    @Test
    fun theMessageIsOneCleanLine() {
        assertFalse(text.contains("\n"))
        assertFalse(text.contains("  "))
        assertEquals(text.trim(), text)
    }
}
