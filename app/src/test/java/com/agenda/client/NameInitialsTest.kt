package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Test

class NameInitialsTest {
    @Test
    fun twoWordsGiveTwoInitials() = assertEquals("PN", NameInitials.of("Pancenco Natalia"))

    @Test
    fun diacriticsAreKept() = assertEquals("ȘT", NameInitials.of("Șuiu Tatiana"))

    @Test
    fun oneWordGivesFirstTwoLetters() = assertEquals("IV", NameInitials.of("ivan"))

    @Test
    fun blankGivesQuestionMark() = assertEquals("?", NameInitials.of("   "))
}
