package com.agenda.client


enum class AccountType(

    val key: String,

    val label: String,

    val portalAlt: String
) {
    PARENT("parent", "Родитель", "Părinte"),
    STUDENT("student", "Ученик", "Elev");

    companion object {

        fun fromKey(key: String?): AccountType =
            values().firstOrNull { it.key == key } ?: PARENT
    }
}
