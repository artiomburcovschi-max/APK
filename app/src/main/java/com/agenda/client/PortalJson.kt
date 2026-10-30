package com.agenda.client

import org.json.JSONArray
import org.json.JSONObject

/**
 * Сохранение PortalExtras в JSON-текст и чтение обратно (org.json есть и в Android, и в тестах).
 * Чтение терпимое: любая неполадка (битый файл, старая версия) даёт null, то есть «ничего не знаем»,
 * а не вылет приложения. Поле v: версия формата, чтобы при будущих изменениях различать старые файлы.
 */
object PortalJson {
    const val VERSION = 1

    fun toJson(e: PortalExtras): String {
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("updatedAt", e.updatedAt)
        root.put("lessons", JSONArray().also { arr ->
            e.lessons.forEach { l ->
                arr.put(
                    JSONObject()
                        .put("d", l.date).put("o", l.order).put("st", l.start).put("en", l.end)
                        .put("s", l.subject).put("r", l.room).put("t", l.teacher).put("h", l.homework)
                        .put("g", JSONArray(l.grades))
                )
            }
        })
        root.put("homework", JSONArray().also { arr ->
            e.homework.forEach { arr.put(JSONObject().put("d", it.date).put("s", it.subject).put("x", it.text)) }
        })
        e.dosar?.let { d ->
            root.put("dosar", JSONObject().also { o ->
                d.className?.let { o.put("c", it) }
                d.formTeacher?.let { o.put("f", it) }
                d.institution?.let { o.put("i", it) }
                o.put("teachers", JSONArray().also { arr ->
                    d.teachers.forEach { arr.put(JSONObject().put("s", it.subject).put("t", it.teacher)) }
                })
            })
        }
        e.absences?.let { a ->
            root.put("absences", JSONObject().also { o ->
                a.semester1?.let { o.put("s1", semester(it)) }
                a.semester2?.let { o.put("s2", semester(it)) }
                a.presencePercent?.let { o.put("p", it) }
                o.put("entries", JSONArray().also { arr ->
                    a.entries.forEach {
                        arr.put(
                            JSONObject().put("d", it.date).put("n", it.lessonNo).put("s", it.subject)
                                .put("st", it.status.name).put("c", it.cumulative)
                        )
                    }
                })
                o.put("subjects", JSONArray().also { arr ->
                    a.bySubject.forEach {
                        arr.put(JSONObject().put("s", it.subject).put("a", semester(it.semester1)).put("b", semester(it.semester2)))
                    }
                })
            })
        }
        return root.toString()
    }

    fun fromJson(text: String): PortalExtras? = try {
        val root = JSONObject(text)
        if (root.optInt("v", 0) != VERSION) {
            null
        } else {
            PortalExtras(
                lessons = root.optJSONArray("lessons").objects().map {
                    Lesson(
                        it.getString("d"), it.getInt("o"), it.optString("st"), it.optString("en"),
                        it.getString("s"), it.optString("r"), it.optString("t"), it.optString("h"),
                        it.optJSONArray("g").ints()
                    )
                },
                homework = root.optJSONArray("homework").objects().map {
                    HomeworkItem(it.getString("d"), it.getString("s"), it.getString("x"))
                },
                dosar = root.optJSONObject("dosar")?.let { o ->
                    DosarInfo(
                        o.optString("c").ifEmpty { null }, o.optString("f").ifEmpty { null }, o.optString("i").ifEmpty { null },
                        o.optJSONArray("teachers").objects().map { TeacherEntry(it.getString("s"), it.getString("t")) }
                    )
                },
                absences = root.optJSONObject("absences")?.let { o ->
                    AbsenceData(
                        entries = o.optJSONArray("entries").objects().map {
                            AbsenceEntry(
                                it.getString("d"), it.getInt("n"), it.getString("s"),
                                runCatching { AbsenceStatus.valueOf(it.getString("st")) }.getOrDefault(AbsenceStatus.UNKNOWN),
                                it.optInt("c")
                            )
                        },
                        semester1 = o.optJSONObject("s1")?.let { semester(it) },
                        semester2 = o.optJSONObject("s2")?.let { semester(it) },
                        bySubject = o.optJSONArray("subjects").objects().map {
                            SubjectAbsences(it.getString("s"), semester(it.getJSONObject("a")), semester(it.getJSONObject("b")))
                        },
                        presencePercent = if (o.has("p")) o.getInt("p") else null
                    )
                },
                updatedAt = root.optLong("updatedAt", 0L)
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun semester(s: SemesterAbsences) = JSONObject().put("m", s.motivated).put("u", s.unmotivated).put("t", s.total)
    private fun semester(o: JSONObject) = SemesterAbsences(o.getInt("m"), o.getInt("u"), o.getInt("t"))

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.ints(): List<Int> =
        if (this == null) emptyList() else (0 until length()).map { getInt(it) }
}
