package com.agenda.client

enum class ChangeType { ADDED, MODIFIED, REMOVED }

/**
 * Одно изменение в списке оценок предмета.
 * position - номер оценки в списке на портале (с нуля): у оценок там нет ни id, ни даты,
 * поэтому оценку можно опознать только по месту в списке.
 */
data class GradeChange(
    val type: ChangeType,
    val semester: String,
    val subject: String,
    val position: Int,
    val oldScore: Int?,
    val newScore: Int?
)