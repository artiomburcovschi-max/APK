package com.agenda.client

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Создаёт базу grades.db и таблицу grades.
 * Одна строка = одна оценка. Её "адрес" это (семестр, предмет, место в списке).
 */
class GradesDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "grades.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE grades (
                semester TEXT NOT NULL,
                subject TEXT NOT NULL,
                position INTEGER NOT NULL,
                score INTEGER NOT NULL,
                first_seen_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (semester, subject, position)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Пока схема одна, миграций нет. Когда добавим колонку, поднимем версию
        // (число 1 выше) и здесь напишем ALTER TABLE.
    }
}

/** Результат одной синхронизации. isBaseline = самый первый запуск, истории ещё не было. */
data class SyncReport(val isBaseline: Boolean, val changes: List<GradeChange>)

/**
 * Тонкая "оболочка" вокруг базы. Вся логика сравнения в GradesDiff,
 * здесь только чтение и запись.
 */
class GradesRepository(context: Context) {

    private val helper = GradesDbHelper(context)

    /** Сравнивает свежие оценки со снимком в базе, записывает разницу и возвращает её. */
    @Synchronized // два обновления одновременно не должны писать в базу вперемешку
    fun sync(parsed: List<SubjectGrades>, now: Long = System.currentTimeMillis()): SyncReport {
        val db = helper.writableDatabase
        val old = loadAll(db)
        val isBaseline = old.isEmpty()
        val allChanges = mutableListOf<GradeChange>()

        // Транзакция: либо все изменения записались, либо ни одного
        db.beginTransaction()
        try {
            for (item in parsed) {
                val oldScores = old[item.semester to item.subject].orEmpty()
                val changes = GradesDiff.diff(oldScores, item.scores, item.semester, item.subject)
                for (change in changes) apply(db, change, now)
                allChanges.addAll(changes)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // Первый запуск: всё что есть на портале это "точка отсчёта", а не "новые оценки"
        return SyncReport(isBaseline, if (isBaseline) emptyList() else allChanges)
    }

    private fun apply(db: SQLiteDatabase, c: GradeChange, now: Long) {
        when (c.type) {
            ChangeType.ADDED -> db.execSQL(
                "INSERT OR REPLACE INTO grades " +
                        "(semester, subject, position, score, first_seen_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(c.semester, c.subject, c.position, c.newScore, now, now)
            )
            ChangeType.MODIFIED -> db.execSQL(
                "UPDATE grades SET score = ?, updated_at = ? " +
                        "WHERE semester = ? AND subject = ? AND position = ?",
                arrayOf<Any?>(c.newScore, now, c.semester, c.subject, c.position)
            )
            ChangeType.REMOVED -> db.execSQL(
                "DELETE FROM grades WHERE semester = ? AND subject = ? AND position = ?",
                arrayOf<Any?>(c.semester, c.subject, c.position)
            )
        }
    }

    /** Все оценки из базы: (семестр, предмет) -> список оценок по порядку. */
    private fun loadAll(db: SQLiteDatabase): Map<Pair<String, String>, List<Int>> {
        val result = HashMap<Pair<String, String>, MutableList<Int>>()
        db.rawQuery(
            "SELECT semester, subject, score FROM grades ORDER BY semester, subject, position",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0) to cursor.getString(1)
                result.getOrPut(key) { mutableListOf() }.add(cursor.getInt(2))
            }
        }
        return result
    }
}