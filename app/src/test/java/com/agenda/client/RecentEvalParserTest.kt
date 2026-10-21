package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentEvalParserTest {

    // Так блок выглядит на реальной странице: HTML списка лежит в атрибуте value скрытого поля
    // с экранированными символами (&lt; &gt; &quot;). Оценки здесь выдуманные.
    private val pageWithHiddenInput = """
        <html><body>
        <input type="hidden" name="P1_LAST_3_EVAL_LIST" id="P1_LAST_3_EVAL_LIST"
          value="&lt;ul id=&quot;last3-eval&quot;&gt;&lt;li&gt;&lt;span class=&quot;eval-date&quot;&gt;12.10.2026&lt;/span&gt; &lt;span class=&quot;eval-discipline&quot;&gt;Istoria românilor și universală:&lt;/span&gt; &lt;span class=&quot;grade&quot;&gt;10&lt;/span&gt; &lt;/li&gt;&lt;li&gt;&lt;span class=&quot;eval-date&quot;&gt;09.10.2026&lt;/span&gt; &lt;span class=&quot;eval-discipline&quot;&gt;Matematică:&lt;/span&gt; &lt;span class=&quot;grade&quot;&gt;9&lt;/span&gt; &lt;/li&gt;&lt;/ul&gt;">
        </body></html>
    """.trimIndent()

    @Test
    fun parsesDatesSubjectsAndScoresFromHiddenInput() {
        val result = RecentEvalParser.parse(pageWithHiddenInput)

        assertEquals(2, result.size)
        assertEquals(RecentEval("2026-10-12", "Istoria românilor și universală", 10), result[0])
        assertEquals(RecentEval("2026-10-09", "Matematică", 9), result[1])
    }

    @Test
    fun fallsBackToRenderedList() {
        val html = """
            <ul id="last3-eval"><li><span class="eval-date">06.10.2026</span>
            <span class="eval-discipline">Chimie:</span> <span class="grade">10</span></li></ul>
        """.trimIndent()

        val result = RecentEvalParser.parse(html)

        assertEquals(listOf(RecentEval("2026-10-06", "Chimie", 10)), result)
    }

    @Test
    fun skipsBrokenItems() {
        val html = """
            <ul id="last3-eval">
              <li><span class="eval-date">дата</span><span class="eval-discipline">A:</span><span class="grade">10</span></li>
              <li><span class="eval-date">01.10.2026</span><span class="eval-discipline">B:</span><span class="grade">?</span></li>
              <li><span class="eval-date">02.10.2026</span><span class="eval-discipline">C:</span><span class="grade">8</span></li>
            </ul>
        """.trimIndent()

        val result = RecentEvalParser.parse(html)

        assertEquals(listOf(RecentEval("2026-10-02", "C", 8)), result)
    }

    @Test
    fun emptyPageGivesEmptyList() {
        assertTrue(RecentEvalParser.parse("<html><body>ничего</body></html>").isEmpty())
    }
}
