package com.agenda.client

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Хранит PortalExtras (уроки, ДЗ, учителей, пропуски) одним файлом extras.json в личной папке приложения.
 * Файл не попадает в облачную копию Android (см. backup_rules) и стирается при смене аккаунта.
 * Запись через временный файл: если телефон выключится посреди записи, прежний файл останется целым.
 */
class PortalExtrasStore(context: Context) {

    private val file = File(context.filesDir, "extras.json")

    @Synchronized
    fun load(): PortalExtras {
        if (!file.exists()) return PortalExtras()
        return try {
            PortalJson.fromJson(file.readText(Charsets.UTF_8)) ?: PortalExtras()
        } catch (e: Exception) {
            Log.e("AGENDA_DEBUG", "Не удалось прочитать extras.json: ${e.message}")
            PortalExtras()
        }
    }

    @Synchronized
    fun save(extras: PortalExtras) {
        try {
            val tmp = File(file.parentFile, "extras.json.tmp")
            tmp.writeText(PortalJson.toJson(extras), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { // на редких файловых системах rename поверх существующего не проходит
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e("AGENDA_DEBUG", "Не удалось сохранить extras.json: ${e.message}")
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, "extras.json.tmp").delete()
    }
}
