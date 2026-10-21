package com.agenda.client
object RefreshPolicy {

    const val AUTO_INTERVAL_MS = 30 * 60 * 1000L
    const val MANUAL_COOLDOWN_MS = 2 * 60 * 1000L

    fun shouldAutoRefresh(lastAttemptAt: Long, now: Long): Boolean {
        if (lastAttemptAt <= 0L) return true          // ещё ни разу не заходили
        if (now < lastAttemptAt) return true          // часы перевели назад: не зависаем навсегда
        return now - lastAttemptAt >= AUTO_INTERVAL_MS
    }
    fun manualWaitMs(lastAttemptAt: Long, now: Long): Long {
        if (lastAttemptAt <= 0L || now < lastAttemptAt) return 0L
        return maxOf(0L, MANUAL_COOLDOWN_MS - (now - lastAttemptAt))
    }
}