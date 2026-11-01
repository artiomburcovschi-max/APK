package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildrenParserTest {

    private fun item(id: String, name: String, current: Boolean) =
        """<li class="a-TreeView-node"><div class="a-TreeView-content"><span class="fa fa-user"></span>
           <a class="a-TreeView-label${if (current) " is-current" else ""}"
              href="/ords/r/agenda/main/main-page?user_eleve_id=$id&amp;clear=1&amp;session=1&amp;cs=x">$name</a></div></li>"""

    @Test
    fun readsTwoChildrenAndMarksTheCurrentOne() {
        val html = """<div id="t_TreeNav"><ul>${item("AAA111", "PETROV IVAN", false)}${item("BBB222", "PETROV PAVEL", true)}</ul></div>"""
        val parsed = ChildrenParser.parse(html)

        assertEquals(listOf(Child("AAA111", "PETROV IVAN"), Child("BBB222", "PETROV PAVEL")), parsed.children)
        assertEquals("BBB222", parsed.currentId)
    }

    @Test
    fun singleChildGivesOneItem() {
        val html = """<div id="t_TreeNav"><ul>${item("AAA111", "PETROV IVAN [x]", true)}</ul></div>"""
        val parsed = ChildrenParser.parse(html)

        assertEquals(1, parsed.children.size)
        assertEquals("PETROV IVAN", parsed.children[0].name) // [..] из названия убираем, как это делает сам портал
        assertEquals("AAA111", parsed.currentId)
    }

    @Test
    fun pageWithoutMenuMeansNoChildren() {
        val parsed = ChildrenParser.parse("<html><body>нет меню</body></html>")

        assertTrue(parsed.children.isEmpty())
        assertEquals("", parsed.currentId)
    }
}
