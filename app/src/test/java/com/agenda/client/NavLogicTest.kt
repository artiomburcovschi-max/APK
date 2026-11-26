package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLogicTest {

    @Test
    fun theBottomBarHasFiveTabsInTheAgreedOrder() {
        assertEquals(
            listOf(Section.HOME, Section.GRADES, Section.ANALYTICS, Section.SCHEDULE, Section.MORE),
            NavLogic.TABS
        )
        assertEquals(Tab.values().toList(), NavLogic.TABS.map { it.tab })
        assertTrue(NavLogic.TABS.all { it.isRoot })
        assertEquals(Section.HOME, NavLogic.START)
    }

    @Test
    fun theRootOfEachTabIsItsOwnSection() {
        for (tab in Tab.values()) {
            val root = NavLogic.rootFor(tab)
            assertEquals(tab, root.tab)
            assertTrue(root.isRoot)
        }
        assertEquals(Section.GRADES, NavLogic.rootFor(Tab.GRADES))
        assertEquals(Section.MORE, NavLogic.rootFor(Tab.MORE))
    }

    @Test
    fun sectionKeysAreUniqueAndFindableBack() {
        val keys = Section.values().map { it.key }
        assertEquals("ключи должны быть уникальны", keys.size, keys.toSet().size)
        for (s in Section.values()) assertEquals(s, NavLogic.fromKey(s.key))
        assertNull(NavLogic.fromKey(null))
        assertNull(NavLogic.fromKey(""))
        assertNull(NavLogic.fromKey("nope"))
    }

    @Test
    fun noSectionOfTheOldDrawerIsLost() {
        // всё, что было в боковой шторке, достижимо: вкладки, список «Ещё» или «Новые оценки» с главной
        val reachable = NavLogic.TABS.toSet() + NavLogic.MORE_ITEMS.toSet() + Section.RECENT
        assertEquals(Section.values().toSet(), reachable)
        assertEquals("в «Ещё» нет повторов", NavLogic.MORE_ITEMS.size, NavLogic.MORE_ITEMS.toSet().size)
        assertTrue("в «Ещё» только подразделы", NavLogic.MORE_ITEMS.none { it.isRoot })
        assertFalse(Section.RECENT in NavLogic.MORE_ITEMS)
    }

    @Test
    fun theWidgetAndNotificationKeyOpensTheSchedule() {
        // LessonWidget.OPEN_SCHEDULE = "schedule": это значение кладут в Intent виджет и уведомление
        assertEquals("schedule", Section.SCHEDULE.key)
        assertEquals(Section.SCHEDULE, NavLogic.fromOpenExtra("schedule"))
        assertEquals(Section.GRADES, NavLogic.fromOpenExtra("grades"))
    }

    @Test
    fun anUnknownOrNonRootOpenValueFallsBackToTheNormalStart() {
        assertNull(NavLogic.fromOpenExtra(null))
        assertNull(NavLogic.fromOpenExtra(""))
        assertNull(NavLogic.fromOpenExtra("bogus"))
        assertNull("подраздел напрямую не открываем", NavLogic.fromOpenExtra("settings"))
    }

    @Test
    fun aRootHighlightsItsOwnTab() {
        for (s in NavLogic.TABS) assertEquals(s.tab, NavLogic.highlightedTab(s, null))
        assertEquals(Tab.GRADES, NavLogic.highlightedTab(Section.GRADES, Section.MORE.key))
    }

    @Test
    fun aSubsectionHighlightsTheTabItWasOpenedFrom() {
        assertEquals(Tab.HOME, NavLogic.highlightedTab(Section.RECENT, Section.HOME.key))
        assertEquals(Tab.HOME, NavLogic.highlightedTab(Section.HOMEWORK, Section.HOME.key))
        assertEquals(Tab.MORE, NavLogic.highlightedTab(Section.HOMEWORK, Section.MORE.key))
        assertEquals(Tab.SCHEDULE, NavLogic.highlightedTab(Section.DIARY, Section.SCHEDULE.key))
    }

    @Test
    fun aSubsectionWithAnUnknownOriginHighlightsMore() {
        assertEquals(Tab.MORE, NavLogic.highlightedTab(Section.SETTINGS, null))
        assertEquals(Tab.MORE, NavLogic.highlightedTab(Section.SETTINGS, "garbage"))
        assertEquals("откуда пришли из подраздела: не корень", Tab.MORE, NavLogic.highlightedTab(Section.ABOUT, Section.DIARY.key))
        assertEquals("«Новые оценки» сами по себе принадлежат главной, но это не корень", Tab.MORE, NavLogic.highlightedTab(Section.ABOUT, Section.RECENT.key))
    }

    @Test
    fun theNewGradesFeedBelongsToHomeByDefault() {
        assertEquals(Tab.HOME, NavLogic.highlightedTab(Section.RECENT, null))
        assertEquals(Tab.HOME, NavLogic.highlightedTab(Section.RECENT, Section.HOME.key))
        assertEquals(Tab.MORE, NavLogic.highlightedTab(Section.RECENT, Section.MORE.key))
    }

    @Test
    fun backGoesHomeAndFromHomeLeavesTheApp() {
        assertNull(NavLogic.backTarget(Section.HOME))
        for (s in Section.values()) if (s != Section.HOME) assertEquals("с ${s.key}", Section.HOME, NavLogic.backTarget(s))
    }
}
