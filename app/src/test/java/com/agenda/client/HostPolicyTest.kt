package com.agenda.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostPolicyTest {

    @Test
    fun blocksYoutubeAndSubdomains() {
        assertTrue(HostPolicy.isBlocked("youtube.com"))
        assertTrue(HostPolicy.isBlocked("www.youtube.com"))
        assertTrue(HostPolicy.isBlocked("i.ytimg.com"))
        assertTrue(HostPolicy.isBlocked("img.icons8.com"))
        assertTrue(HostPolicy.isBlocked("WWW.YouTube.com"))
        assertTrue(HostPolicy.isBlocked("youtube.com."))
    }

    @Test
    fun neverBlocksThePortal() {
        assertFalse(HostPolicy.isBlocked("agenda.sime.md"))
        assertFalse(HostPolicy.isBlocked("sime.md"))
        assertFalse(HostPolicy.isBlocked("static.agenda.sime.md"))
    }

    @Test
    fun doesNotBlockLookalikes() {
        assertFalse(HostPolicy.isBlocked("notyoutube.com"))
        assertFalse(HostPolicy.isBlocked("youtube.com.example.org"))
        assertFalse(HostPolicy.isBlocked(""))
    }
}
