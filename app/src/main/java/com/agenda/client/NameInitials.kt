package com.agenda.client

/** Инициалы для круглого значка учителя: «Pancenco Natalia» → «PN», одно слово → первые две буквы. */
object NameInitials {
    fun of(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
