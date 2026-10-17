package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradesDiffTest {

    private val sem = "Semestrul I"
    private val subj = "Chimie"

    private fun diff(old: List<Int>, new: List<Int>) = GradesDiff.diff(old, new, sem, subj)

    @Test
    fun sameListsGiveNoChanges() {
        assertTrue(diff(listOf(9, 10), listOf(9, 10)).isEmpty())
    }

    @Test
    fun newGradeAtTailIsAdded() {
        val result = diff(listOf(9, 10), listOf(9, 10, 8))

        assertEquals(1, result.size)
        assertEquals(ChangeType.ADDED, result[0].type)
        assertEquals(2, result[0].position)
        assertEquals(8, result[0].newScore)
    }

    @Test
    fun everythingIsAddedWhenNothingWasStored() {
        val result = diff(emptyList(), listOf(7, 8))

        assertEquals(2, result.size)
        assertTrue(result.all { it.type == ChangeType.ADDED })
    }

    @Test
    fun changedGradeIsModified() {
        val result = diff(listOf(9, 10, 8), listOf(9, 10, 9))

        assertEquals(1, result.size)
        assertEquals(ChangeType.MODIFIED, result[0].type)
        assertEquals(8, result[0].oldScore)
        assertEquals(9, result[0].newScore)
    }

    @Test
    fun shorterListMeansRemoved() {
        val result = diff(listOf(9, 10, 8), listOf(9, 10))

        assertEquals(1, result.size)
        assertEquals(ChangeType.REMOVED, result[0].type)
        assertEquals(8, result[0].oldScore)
    }

    @Test
    fun emptyNewListDoesNotWipeHistory() {
        // Защита от недогруженной страницы
        assertTrue(diff(listOf(9, 10), emptyList()).isEmpty())
    }

    @Test
    fun deletionInTheMiddleLooksLikeModifyPlusRemove() {
        // У оценок на портале нет id, поэтому удаление из середины сдвигает хвост.
        // Тест фиксирует это известное ограничение.
        val result = diff(listOf(9, 10, 8), listOf(9, 8))

        assertEquals(listOf(ChangeType.MODIFIED, ChangeType.REMOVED), result.map { it.type })
    }
}