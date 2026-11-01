package com.agenda.client

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/** Один ребёнок в аккаунте родителя. id берётся из ссылки меню (параметр user_eleve_id). */
data class Child(val id: String, val name: String)

data class ParsedChildren(val children: List<Child>, val currentId: String)

/**
 * Читает список детей из бургер-меню портала (блок #t_TreeNav). Если у родителя один ребёнок, в списке один пункт.
 * Ссылки в меню содержат session и cs, поэтому их НЕ сохраняем: при переключении скрипт берёт свежую ссылку
 * прямо со страницы (ищет по user_eleve_id).
 */
object ChildrenParser {
    private val ID = Regex("user_eleve_id=([A-Za-z0-9]+)")
    private val BRACKETS = Regex("\\[.*?]")

    fun parse(html: String): ParsedChildren {
        val doc = Jsoup.parse(html)
        val kids = ArrayList<Child>()
        var current = ""
        for (a in doc.select("#t_TreeNav a.a-TreeView-label")) {
            val id = ID.find(a.attr("href"))?.groupValues?.get(1) ?: continue
            val name = a.text().replace(BRACKETS, "").trim()
            if (kids.none { it.id == id }) kids.add(Child(id, name.ifEmpty { id }))
            if (a.hasClass("is-current") || a.attr("aria-current") == "true") current = id
        }
        return ParsedChildren(kids, current)
    }
}

/**
 * Какие дети есть в аккаунте и чьи данные сейчас показаны.
 *
 * Данные каждого ребёнка лежат в своих файлах (profileKey). У «первого» ребёнка, чьи данные уже были в приложении
 * до появления вкладок (legacyOwner), ключ пустой: его старые файлы остаются на месте и не теряются.
 */
class ChildStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("children", Context.MODE_PRIVATE)

    /** Выбранный ребёнок (id). Пусто только до первой успешной загрузки. */
    var activeId: String
        get() = prefs.getString("active", "") ?: ""
        set(value) { prefs.edit().putString("active", value).apply() }

    /** Ребёнок, которому принадлежат файлы без суффикса (extras.json, grades.db...). */
    var legacyOwner: String
        get() = prefs.getString("legacy", "") ?: ""
        private set(value) { prefs.edit().putString("legacy", value).apply() }

    /** Чьи данные нужны: выбранный ребёнок, а до выбора владелец старых файлов. */
    fun targetId(): String = activeId.ifEmpty { legacyOwner }

    fun keyFor(id: String): String =
        if (id.isEmpty() || id == legacyOwner) "" else id.filter { it.isLetterOrDigit() }

    fun profileKey(): String = keyFor(targetId())

    fun adoptFirstChild(id: String) {
        if (legacyOwner.isEmpty()) legacyOwner = id
        if (activeId.isEmpty()) activeId = id
    }

    fun children(): List<Child> = try {
        val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { Child(it.getString("id"), it.getString("name")) }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveChildren(list: List<Child>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
