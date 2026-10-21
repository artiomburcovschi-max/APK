package com.agenda.client

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * База grades.db. Одна строка = одна оценка. Её "адрес": (семестр, предмет, место в списке).
 * Версия 2 добавила колонку grade_date (дата оценки).
 */
class GradesDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "grades.db", null, 2) {

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
                grade_date TEXT,
                PRIMARY KEY (semester, subject, position)
            )
            """.trimIndent()
        )
    }

    /** Миграция: у тех, кто уже ставил версию 1, добавляем колонку; старые строки остаются на месте. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE grades ADD COLUMN grade_date TEXT")
        }
    }
}

/** Оценка из базы. date = "yyyy-MM-dd" или null, если дата неизвестна. */
data class StoredGrade(val score: Int, val date: String?)

/**
 * Результат синхронизации. isBaseline = самый первый запуск (истории ещё не было).
 * display = то, что показываем на экране: свежий список с портала плюс даты из базы.
 */
data class SyncReport(
    val isBaseline: Boolean,
    val changes: List<GradeChange>,
    val display: List<SubjectGrades>
)

/**
 * Тонкая "оболочка" вокруг базы: чтение и запись.
 * Вся логика сравнения лежит в GradesDiff, а привязки дат в GradeDates.
 */
class GradesRepository(context: Context) {

    private val helper = GradesDbHelper(context)

    /**
     * Сравнивает свежие оценки со снимком в базе, записывает разницу и проставляет даты.
     * recent = блок "Note recente" (единственный источник настоящих дат).
     */
    @Synchronized // два обновления одновременно не должны писать в базу вперемешку
    fun sync(
        parsed: List<SubjectGrades>,
        recent: List<RecentEval> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): SyncReport {
        val db = helper.writableDatabase
        val old = loadAll(db)
        val isBaseline = old.isEmpty()
        val allChanges = mutableListOf<GradeChange>()

        // Транзакция: либо все изменения записались, либо ни одного
        db.beginTransaction()
        try {
            for (item in parsed) {
                val oldScores = old[item.semester to item.subject].orEmpty().map { it.score }
                val changes = GradesDiff.diff(oldScores, item.scores, item.semester, item.subject)
                for (change in changes) apply(db, change, now)
                allChanges.addAll(changes)
            }
            fillDates(db, parsed, recent)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // Для экрана берём список с портала и дописываем к нему даты, сохранённые в базе
        val stored = loadAll(db)
        val display = parsed.map { item ->
            val rows = stored[item.semester to item.subject].orEmpty()
            item.copy(dates = item.scores.indices.map { rows.getOrNull(it)?.date })
        }

        // Первый запуск: всё что есть на портале это "точка отсчёта", а не "новые оценки"
        return SyncReport(isBaseline, if (isBaseline) emptyList() else allChanges, display)
    }

    /** Стирает всю историю оценок (при смене аккаунта: чужая история не должна смешиваться с новой). */
    @Synchronized
    fun clearAll() {
        helper.writableDatabase.execSQL("DELETE FROM grades")
    }

    /**
     * Проставляет даты оценкам, у которых их ещё нет.
     * Дату, однажды записанную, не меняем: она стабильна.
     */
    private fun fillDates(db: SQLiteDatabase, parsed: List<SubjectGrades>, recent: List<RecentEval>) {
        if (recent.isEmpty()) return

        // Блок "Note recente" показывает самые новые оценки, поэтому для каждого предмета
        // берём его ПОСЛЕДНИЙ (самый свежий) семестр, где есть оценки.
        val latestBySubject = LinkedHashMap<String, SubjectGrades>()
        for (item in parsed) {
            if (item.scores.isNotEmpty()) latestBySubject[GradeDates.normalize(item.subject)] = item
        }

        for ((key, item) in latestBySubject) {
            // На странице список идёт от новых к старым. Разворачиваем и сортируем по дате (сортировка стабильная).
            val chrono = recent
                .filter { GradeDates.normalize(it.subject) == key }
                .reversed()
                .sortedBy { it.date }

            for ((position, date) in GradeDates.alignTail(item.scores, chrono)) {
                db.execSQL(
                    "UPDATE grades SET grade_date = ? " +
                        "WHERE semester = ? AND subject = ? AND position = ? AND grade_date IS NULL",
                    arrayOf<Any?>(date, item.semester, item.subject, position)
                )
            }
        }
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
    private fun loadAll(db: SQLiteDatabase): Map<Pair<String, String>, List<StoredGrade>> {
        val result = HashMap<Pair<String, String>, MutableList<StoredGrade>>()
        db.rawQuery(
            "SELECT semester, subject, score, grade_date FROM grades ORDER BY semester, subject, position",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0) to cursor.getString(1)
                val date = if (cursor.isNull(3)) null else cursor.getString(3)
                result.getOrPut(key) { mutableListOf() }.add(StoredGrade(cursor.getInt(2), date))
            }
        }
        return result
    }
}
