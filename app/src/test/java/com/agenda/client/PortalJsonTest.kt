package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortalJsonTest {

    private val page: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("portal_main_anon.html")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun realisticExtras() = PortalExtras(
        lessons = AgendaParser.parseLessons(page),
        homework = AgendaParser.parseHomework(page),
        dosar = DosarParser.parse(page),
        absences = AbsenceParser.parse(page),
        updatedAt = 1_761_487_380_000L
    )

    @Test
    fun everythingSurvivesAWriteAndRead() {
        val original = realisticExtras()

        val restored = PortalJson.fromJson(PortalJson.toJson(original))

        assertEquals(original, restored)
    }

    @Test
    fun emptyExtrasSurviveToo() {
        val empty = PortalExtras()

        assertEquals(empty, PortalJson.fromJson(PortalJson.toJson(empty)))
    }

    @Test
    fun multilineHomeworkAndQuotesSurvive() {
        val tricky = PortalExtras(
            lessons = listOf(
                Lesson("2026-10-01", 1, "08:00", "08:45", "Matematică", "", "Popa Elena", "строка 1\nстрока \"2\" — №5 \\ слэш", listOf(9, 10))
            )
        )

        assertEquals(tricky, PortalJson.fromJson(PortalJson.toJson(tricky)))
    }

    @Test
    fun unknownStatusFallsBackToUnknown() {
        val json = PortalJson.toJson(
            PortalExtras(absences = AbsenceData(listOf(AbsenceEntry("2026-09-03", 1, "Chimie", AbsenceStatus.MOTIVATED, 1)), null, null, emptyList(), null))
        ).replace("MOTIVATED", "SOMETHING_NEW")

        assertEquals(AbsenceStatus.UNKNOWN, PortalJson.fromJson(json)!!.absences!!.entries.single().status)
    }

    @Test
    fun brokenOrForeignTextGivesNullNotACrash() {
        assertNull(PortalJson.fromJson(""))
        assertNull(PortalJson.fromJson("не json"))
        assertNull(PortalJson.fromJson("{\"v\":999}"))                       // другая версия формата
        assertNull(PortalJson.fromJson("{\"v\":1,\"lessons\":[{\"d\":1}]}")) // обязательных полей нет
        assertNull(PortalJson.fromJson("[]"))
    }
}
