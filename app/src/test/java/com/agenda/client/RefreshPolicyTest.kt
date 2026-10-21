package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {

    private val minute = 60_000L
    private val t0 = 1_000_000_000L

    @Test
    fun firstLaunchRefreshes() {
        assertTrue(RefreshPolicy.shouldAutoRefresh(0L, t0))
    }

    @Test
    fun recentAttemptDoesNotAutoRefresh() {
        assertFalse(RefreshPolicy.shouldAutoRefresh(t0, t0 + 10 * minute))
    }

    @Test
    fun oldAttemptAutoRefreshes() {
        assertTrue(RefreshPolicy.shouldAutoRefresh(t0, t0 + 30 * minute))
    }

    @Test
    fun clockSetBackDoesNotBlockForever() {
        assertTrue(RefreshPolicy.shouldAutoRefresh(t0, t0 - 5 * minute))
    }

    @Test
    fun manualRefreshIsBlockedRightAfterAttempt() {
        assertEquals(RefreshPolicy.MANUAL_COOLDOWN_MS, RefreshPolicy.manualWaitMs(t0, t0))
        assertEquals(minute, RefreshPolicy.manualWaitMs(t0, t0 + minute))
    }

    @Test
    fun manualRefreshAllowedAfterCooldown() {
        assertEquals(0L, RefreshPolicy.manualWaitMs(t0, t0 + 2 * minute))
        assertEquals(0L, RefreshPolicy.manualWaitMs(0L, t0))
    }
}