package com.arotter.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson

object ListeningStats {
    private const val PREFS_NAME = "listening_stats"
    private const val STATS_KEY = "stats"

    private val stats = mutableMapOf<String, Int>() // trackPath -> count

    fun loadStats(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(STATS_KEY, null) ?: return

        try {
            val jsonArray = JSONArray(jsonString)
            stats.clear()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val path = item.getString("path")
                val count = item.getInt("count")
                stats[path] = count
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveStats(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()

        stats.forEach { (path, count) ->
            val item = JSONObject().apply {
                put("path", path)
                put("count", count)
            }
            jsonArray.put(item)
        }

        prefs.edit().putString(STATS_KEY, jsonArray.toString()).apply()
    }

    fun incrementPlayCount(context: Context, trackPath: String) {
        val currentCount = stats[trackPath] ?: 0
        stats[trackPath] = currentCount + 1
        saveStats(context)
    }

    fun getPlayCount(trackPath: String): Int {
        return stats[trackPath] ?: 0
    }

    fun exportToFile(context: Context, file: File): Boolean {
        return try {
            // Экспортируем из памяти, а не из SharedPreferences
            if (stats.isEmpty()) {
                return false
            }

            val json = Gson().toJson(stats)
            file.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromFile(context: Context, file: File): Pair<Boolean, String> {
        return try {
            if (!file.exists()) {
                return Pair(false, "Файл не найден")
            }

            val json = file.readText()
            if (json.isBlank()) {
                return Pair(false, "Файл пустой")
            }

            val type = object : TypeToken<Map<String, Int>>() {}.type
            val importedStats: Map<String, Int>? = Gson().fromJson(json, type)

            if (importedStats == null) {
                return Pair(false, "Неверный формат JSON")
            }

            if (importedStats.isEmpty()) {
                return Pair(false, "Нет данных для импорта")
            }

            // Очищаем старую статистику в памяти
            stats.clear()

            // Импортируем новую
            var imported = 0
            importedStats.forEach { (path, count) ->
                if (count > 0) {
                    stats[path] = count
                    imported++
                }
            }

            // Сохраняем в SharedPreferences
            saveStats(context)

            Pair(true, "Импортировано $imported записей")
        } catch (e: Exception) {
            val errorMsg = "Ошибка: ${e.javaClass.simpleName}\n${e.message}\n${e.stackTraceToString().take(500)}"
            Pair(false, errorMsg)
        }
    }

    fun clearStats(context: Context) {
        stats.clear()
        saveStats(context)
    }
}