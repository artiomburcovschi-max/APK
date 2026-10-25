package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Test

class RuWordsTest {

    @Test
    fun pluralFollowsRussianRules() {
        fun f(n: Int) = RuWords.plural(n, "оценка", "оценки", "оценок")
        assertEquals("оценок", f(0))
        assertEquals("оценка", f(1))
        assertEquals("оценки", f(2))
        assertEquals("оценки", f(4))
        assertEquals("оценок", f(5))
        assertEquals("оценок", f(11)) // исключение: 11-14
        assertEquals("оценок", f(12))
        assertEquals("оценок", f(14))
        assertEquals("оценка", f(21))
        assertEquals("оценки", f(22))
        assertEquals("оценок", f(25))
        assertEquals("оценок", f(111))
        assertEquals("оценка", f(101))
    }

    @Test
    fun gradeCountUsesTheRightNoun() {
        assertEquals("1 десятка", RuWords.gradeCount(10, 1))
        assertEquals("2 десятки", RuWords.gradeCount(10, 2))
        assertEquals("5 десяток", RuWords.gradeCount(10, 5))
        assertEquals("2 восьмёрки", RuWords.gradeCount(8, 2))
        assertEquals("1 семёрка", RuWords.gradeCount(7, 1))
        assertEquals("12 девяток", RuWords.gradeCount(9, 12))
        assertEquals("3 тройки", RuWords.gradeCount(3, 3))
        assertEquals("5 троек", RuWords.gradeCount(3, 5))
    }

    @Test
    fun gradesLine() {
        assertEquals("1 оценка", RuWords.grades(1))
        assertEquals("5 оценок", RuWords.grades(5))
    }
}
