package com.agenda.client

/**
 * Чистая логика (без Android и базы): сравнивает старый и новый список оценок
 * одного предмета и говорит, что изменилось. Легко проверяется тестами.
 */
object GradesDiff {

    fun diff(
        old: List<Int>,
        new: List<Int>,
        semester: String,
        subject: String
    ): List<GradeChange> {
        // Защита от неполной загрузки: если раньше оценки были, а сейчас список пуст,
        // скорее всего страница не догрузилась. Не стираем историю из-за такого сбоя.
        if (new.isEmpty() && old.isNotEmpty()) return emptyList()

        val changes = mutableListOf<GradeChange>()
        val common = minOf(old.size, new.size)

        // Совпадающая часть списка: оценка на том же месте стала другой -> исправление
        for (i in 0 until common) {
            if (old[i] != new[i]) {
                changes.add(GradeChange(ChangeType.MODIFIED, semester, subject, i, old[i], new[i]))
            }
        }
        // Новый список длиннее -> хвост это новые оценки
        for (i in common until new.size) {
            changes.add(GradeChange(ChangeType.ADDED, semester, subject, i, null, new[i]))
        }
        // Новый список короче -> оценки пропали
        for (i in common until old.size) {
            changes.add(GradeChange(ChangeType.REMOVED, semester, subject, i, old[i], null))
        }
        return changes
    }
}