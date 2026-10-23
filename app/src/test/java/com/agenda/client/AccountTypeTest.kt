package com.agenda.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTypeTest {

    @Test fun parent_clicks_Parinte_button() {
        assertEquals("Părinte", AccountType.PARENT.portalAlt)
    }

    @Test fun student_clicks_Elev_button() {
        assertEquals("Elev", AccountType.STUDENT.portalAlt)
    }

    @Test fun missing_key_means_parent_for_old_logins() {
        assertEquals(AccountType.PARENT, AccountType.fromKey(null))
    }

    @Test fun unknown_or_broken_key_means_parent() {
        assertEquals(AccountType.PARENT, AccountType.fromKey(""))
        assertEquals(AccountType.PARENT, AccountType.fromKey("teacher"))
        assertEquals(AccountType.PARENT, AccountType.fromKey("STUDENT")) // регистр важен: пишем только key
    }

    @Test fun every_type_survives_save_and_load() {
        for (t in AccountType.values()) assertEquals(t, AccountType.fromKey(t.key))
    }

    @Test fun stored_keys_are_unique_and_stable() {
        val keys = AccountType.values().map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        // эти строки лежат на телефонах у людей, менять их нельзя
        assertEquals("parent", AccountType.PARENT.key)
        assertEquals("student", AccountType.STUDENT.key)
    }

    @Test fun labels_are_not_empty() {
        assertTrue(AccountType.values().all { it.label.isNotBlank() })
    }
}
