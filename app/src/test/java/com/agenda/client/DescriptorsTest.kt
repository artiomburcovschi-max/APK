package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptorsTest {

    private fun subj(name: String, vararg scores: Int, semester: String = "Semestrul I") =
        SubjectGrades(name, scores.toList(), semester)

    @Test
    fun theCivicsSubjectIsRecognisedWhateverTheSpellingOfItsLetters() {
        assertTrue(Descriptors.isDescriptorName("Educaţie pentru societate"))   // ţ с седилью
        assertTrue(Descriptors.isDescriptorName("Educație pentru societate"))   // ț с запятой
        assertTrue(Descriptors.isDescriptorName("Educatie pentru societate"))   // без диакритики
        assertTrue(Descriptors.isDescriptorName("EDUCAŢIE  PENTRU   SOCIETATE")) // регистр и пробелы
        assertTrue(Descriptors.isDescriptorName("Educaţia pentru societate"))   // форма «Educaţia»
    }

    @Test
    fun ordinarySubjectsAreNotDescriptorSubjects() {
        for (name in listOf("Informatica", "Limba engleză", "Educaţie fizică", "Educaţie tehnologică", "Matematica", "Istoria", ""))
            assertFalse(name, Descriptors.isDescriptorName(name))
    }

    @Test
    fun aSubjectWithAScoreAboveTenIsADescriptorSubjectEvenWithAnUnknownName() {
        assertTrue(Descriptors.isDescriptorSubject(subj("Dezvoltare personală", 8, 25)))
        assertTrue("11 уже не оценка", Descriptors.isDescriptorSubject(subj("Chimia", 11)))
        assertFalse("10 это ещё оценка", Descriptors.isDescriptorSubject(subj("Chimia", 10, 9, 10)))
        assertFalse(Descriptors.isDescriptorSubject(subj("Chimia")))
    }

    @Test
    fun theCivicsSubjectIsADescriptorSubjectEvenWhenOnlyATenIsThereSoFar() {
        assertTrue(Descriptors.isDescriptorSubject(subj("Educaţie pentru societate", 10)))
        assertTrue(Descriptors.isDescriptorSubject(subj("Educaţie pentru societate")))
    }

    @Test
    fun marksOnlyDropsDescriptorSubjectsAndKeepsTheRestUntouched() {
        val info = subj("Informatica", 10, 10, 10)
        val eng = SubjectGrades("Limba engleză", listOf(10, 10), "Semestrul I", "10", listOf("2026-10-01", null))
        val civ = subj("Educaţie pentru societate", 25, 27, 35, 10, 21)
        assertEquals(listOf(info, eng), Descriptors.marksOnly(listOf(info, civ, eng)))
        assertTrue(Descriptors.marksOnly(emptyList()).isEmpty())
        assertTrue(Descriptors.marksOnly(listOf(civ)).isEmpty())
    }

    @Test
    fun theOverallAverageIsNoLongerPulledUpByDescriptorPoints() {
        // как на скриншоте: по гражданскому 22.50, по остальным 10
        val items = listOf(
            subj("Educaţie pentru societate", 25, 27, 35, 10, 21, 22),
            subj("Informatica", 10, 10, 10),
            subj("Limba engleză", 10, 10)
        )
        val before = Stats.overall(Stats.subjectAverages(items))!!
        val after = Stats.overall(Stats.subjectAverages(Descriptors.marksOnly(items)))!!
        assertTrue("раньше было завышено: $before", before > 14.0)
        assertEquals(10.0, after, 0.0001)
    }

    @Test
    fun theChartStartsFromRealMarksOnly() {
        val dated = Stats.datedGrades(
            Descriptors.marksOnly(
                listOf(
                    SubjectGrades("Educaţie pentru societate", listOf(25, 30), "S", "", listOf("2026-09-11", "2026-09-12")),
                    SubjectGrades("Informatica", listOf(9), "S", "", listOf("2026-09-13"))
                )
            )
        )
        assertEquals(listOf("Informatica"), dated.map { it.subject })
        assertTrue(Stats.runningOverall(dated).all { it.value <= 10.0 })
    }

    @Test
    fun aScoreInTheFeedIsADescriptorWhenItIsAboveTenOrTheSubjectIsCivics() {
        assertTrue(Descriptors.isDescriptorScore("Informatica", 11))
        assertTrue(Descriptors.isDescriptorScore("Educaţie pentru societate", 5))
        assertTrue(Descriptors.isDescriptorScore("Educaţie pentru societate", 10))
        assertFalse(Descriptors.isDescriptorScore("Informatica", 10))
        assertFalse(Descriptors.isDescriptorScore("Informatica", 1))
    }

    @Test
    fun theNoteListsSkippedSubjectsOnceAndOnlyThoseWithScores() {
        val items = listOf(
            subj("Educaţie pentru societate", 25, 27),
            subj("Educaţie pentru societate", 30, semester = "Semestrul II"),
            subj("Dezvoltare personală"), // нет оценок: не упоминаем
            subj("Educaţia pentru societate"), // дескрипторный по названию, но оценок ещё нет: тоже не упоминаем
            subj("Informatica", 10)
        )
        assertEquals(listOf("Educaţie pentru societate"), Descriptors.namesOf(items))
        assertEquals("Не входят в средний (дескрипторы): Educaţie pentru societate", Descriptors.note(Descriptors.namesOf(items)))
        assertEquals("Не входят в средний (дескрипторы): A, B", Descriptors.note(listOf("A", "B")))
        assertNull(Descriptors.note(emptyList()))
        assertNull(Descriptors.note(Descriptors.namesOf(listOf(subj("Informatica", 10)))))
    }
}
