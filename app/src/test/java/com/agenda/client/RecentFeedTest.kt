package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RecentFeedTest {

    private val baseline = 1_000L

    private fun row(subject: String, position: Int, score: Int, first: Long, updated: Long = first, date: String? = null) =
        GradeRow("I", subject, position, score, first, updated, date)

    @Test
    fun emptyDatabaseGivesEmptyFeed() {
        assertTrue(RecentFeed.build(emptyList()).isEmpty())
    }

    @Test
    fun gradesFromFirstLoadAreNotNews() {
        val rows = listOf(row("A", 0, 9, baseline), row("A", 1, 8, baseline), row("B", 0, 10, baseline))

        assertTrue(RecentFeed.build(rows).isEmpty())
    }

    @Test
    fun gradesThatAppearedLaterAreNews() {
        val rows = listOf(
            row("A", 0, 9, baseline), row("B", 0, 10, baseline),
            row("A", 1, 7, 2_000L, date = "2026-10-12")
        )

        val feed = RecentFeed.build(rows)

        assertEquals(1, feed.size)
        assertEquals(FeedItem("A", "I", 7, 2_000L, "2026-10-12", false), feed[0])
    }

    @Test
    fun modifiedGradeIsNewsEvenIfItWasInTheBaseline() {
        val rows = listOf(row("A", 0, 9, baseline, updated = 3_000L), row("B", 0, 10, baseline))

        val feed = RecentFeed.build(rows)

        assertEquals(1, feed.size)
        assertTrue(feed[0].modified)
        assertEquals(3_000L, feed[0].seenAt)
    }

    @Test
    fun newestFirstThenBySubject() {
        val rows = listOf(
            row("Z", 0, 9, baseline),
            row("B", 1, 8, 2_000L), row("A", 1, 7, 2_000L), row("C", 1, 10, 5_000L)
        )

        assertEquals(listOf("C", "A", "B"), RecentFeed.build(rows).map { it.subject })
    }

    @Test
    fun limitCutsTheOldest() {
        val rows = listOf(row("Z", 0, 9, baseline)) + (1..10).map { row("S$it", 1, 8, 1_000L + it) }

        val feed = RecentFeed.build(rows, limit = 3)

        assertEquals(3, feed.size)
        assertEquals("S10", feed[0].subject)
    }

    @Test
    fun subtitleMentionsGradeDateWhenKnown() {
        val zone = TimeZone.getTimeZone("UTC")
        val seen = 1_761_487_380_000L // 2025-10-26 14:03 UTC

        val withDate = FeedItem("A", "I", 9, seen, "2025-10-24", false)
        assertEquals("оценка от 24.10.2025 · замечена 26.10 в 14:03", RecentFeed.subtitle(withDate, zone))

        val noDate = FeedItem("A", "I", 9, seen, null, false)
        assertEquals("замечена 26.10 в 14:03", RecentFeed.subtitle(noDate, zone))

        val changed = FeedItem("A", "I", 9, seen, null, true)
        assertEquals("оценка изменена · 26.10 в 14:03", RecentFeed.subtitle(changed, zone))
    }
}
