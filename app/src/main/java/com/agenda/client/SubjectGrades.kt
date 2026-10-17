package com.agenda.client

data class SubjectGrades(
    val subject: String,
    val scores: List<Int>,
    val semester: String = "",
    val media: String = ""   // официальная семестровая оценка или "Fără notă"
)
