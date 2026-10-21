package com.agenda.client

data class SubjectGrades(
    val subject: String,
    val scores: List<Int>,
    val semester: String = "",
    val media: String = "",              // официальная семестровая оценка или "Fără notă"
    val dates: List<String?> = emptyList() // даты оценок "yyyy-MM-dd" (null = дата неизвестна), по порядку scores
)
