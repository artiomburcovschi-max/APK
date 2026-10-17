package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradesParserTest {

    // Собирает HTML одной таблицы так же, как на реальной странице
    // (атрибуты headers скопированы оттуда, оценки выдуманные).
    private fun table(id: String, label: String, vararg rows: String): String =
        """<table id="$id" aria-label="$label"><tbody>${rows.joinToString("")}</tbody></table>"""

    private fun row(subject: String, scores: String, teza: String = "", media: String = "", n: Int = 1): String =
        """<tr>
            <td headers="DISCIPLINA">$subject</td>
            <td headers="LISTA_EVALUATION$n">$scores</td>
            <td headers="TEZA$n">$teza</td>
            <td headers="MED_SEM$n">$media</td>
        </tr>"""

    @Test
    fun normalSubjectWithScores() {
        val html = table("report_table_sem1", "Semestrul I", row("Chimie", "9, 10, 8, 9, 10"))

        val result = GradesParser.parse(html)

        assertEquals(1, result.size)
        assertEquals("Chimie", result[0].subject)
        assertEquals(listOf(9, 10, 8, 9, 10), result[0].scores)
        assertEquals("Semestrul I", result[0].semester)
        assertEquals("", result[0].media)
    }

    // Главный тест на надёжность: колонки стоят в другом порядке.
    // Парсер по номерам колонок на нём падал бы, парсер по headers проходит.
    @Test
    fun columnsInDifferentOrderStillParse() {
        val html = """
            <table id="report_table_sem1" aria-label="Semestrul I"><tbody>
              <tr>
                <td headers="MED_SEM1">9.2</td>
                <td headers="LISTA_EVALUATION1">9, 10</td>
                <td headers="TEZA1"></td>
                <td headers="DISCIPLINA">Matematică</td>
              </tr>
            </tbody></table>
        """.trimIndent()

        val result = GradesParser.parse(html)

        assertEquals(1, result.size)
        assertEquals("Matematică", result[0].subject)
        assertEquals(listOf(9, 10), result[0].scores)
        assertEquals("9.2", result[0].media)
    }

    @Test
    fun emptyScoresGiveEmptyList() {
        val html = table("report_table_sem1", "Semestrul I", row("Educație fizică", ""))

        val result = GradesParser.parse(html)

        assertEquals(1, result.size)
        assertTrue(result[0].scores.isEmpty())
    }

    @Test
    fun faraNotaStaysInMedia() {
        val html = table("report_table_sem1", "Semestrul I", row("Dezvoltare personală", "", media = "Fără notă"))

        assertEquals("Fără notă", GradesParser.parse(html)[0].media)
    }

    @Test
    fun twoSemestersAreBothParsed() {
        val html = table("report_table_sem1", "Semestrul I", row("Chimie", "9, 10")) +
                table("report_table_sem2", "Semestrul II", row("Chimie", "8", n = 2))

        val result = GradesParser.parse(html)

        assertEquals(2, result.size)
        assertEquals("Semestrul I", result[0].semester)
        assertEquals("Semestrul II", result[1].semester)
        assertEquals(listOf(8), result[1].scores)
    }

    @Test
    fun rowWithoutSubjectCellIsSkipped() {
        val html = """
            <table id="report_table_sem1" aria-label="Semestrul I"><tbody>
              <tr><td></td></tr>
              <tr><td headers="DISCIPLINA">Informatica</td><td headers="LISTA_EVALUATION1">10</td></tr>
            </tbody></table>
        """.trimIndent()

        val result = GradesParser.parse(html)

        assertEquals(1, result.size)
        assertEquals("Informatica", result[0].subject)
    }

    @Test
    fun garbageInScoresIsIgnored() {
        val html = table("report_table_sem1", "Semestrul I", row("Fizica", "9, abc, 10"))

        assertEquals(listOf(9, 10), GradesParser.parse(html)[0].scores)
    }

    @Test
    fun missingLabelGivesEmptySemester() {
        val html = """<table id="report_table_sem1"><tbody>
            <tr><td headers="DISCIPLINA">Biologie</td><td headers="LISTA_EVALUATION1">10</td></tr>
        </tbody></table>"""

        assertEquals("", GradesParser.parse(html)[0].semester)
    }

    @Test
    fun htmlWithoutTablesReturnsEmpty() {
        val result = GradesParser.parse("<html><body><div>Нет таблиц с оценками</div></body></html>")
        assertTrue(result.isEmpty())
    }
}