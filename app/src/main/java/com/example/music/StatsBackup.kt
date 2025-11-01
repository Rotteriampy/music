package com.example.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object StatsBackup {
    private const val LISTENING_PREFS = "listening_stats"
    private const val LISTENING_KEY = "stats"
    private const val APP_USAGE_PREFS = "app_usage"
    private const val APP_USAGE_KEY = "total_ms"

    fun exportAll(context: Context, target: File): Boolean {
        return try {
            // Ensure current stats are flushed to prefs
            ListeningStats.saveStats(context)

            val root = JSONObject()

            // 1) Listening stats (track play counts) from SharedPreferences JSON
            val lprefs = context.getSharedPreferences(LISTENING_PREFS, Context.MODE_PRIVATE)
            val statsJson = lprefs.getString(LISTENING_KEY, null)
            if (!statsJson.isNullOrBlank()) {
                root.put("listening_stats", JSONArray(statsJson))
            } else {
                root.put("listening_stats", JSONArray())
            }

            // 2) App usage total milliseconds
            val uprefs = context.getSharedPreferences(APP_USAGE_PREFS, Context.MODE_PRIVATE)
            val totalMs = uprefs.getLong(APP_USAGE_KEY, 0L)
            root.put("app_usage_ms", totalMs)

            // 3) Play history events for charts
            val events = PlayHistory.readAll(context)
            val history = JSONArray()
            events.forEach { e ->
                val o = JSONObject().apply {
                    put("timestamp", e.timestamp)
                    put("trackPath", e.trackPath)
                    put("trackName", e.trackName)
                    put("artist", e.artist)
                    put("albumName", e.albumName)
                    put("genre", e.genre)
                    if (e.percent != null) put("percent", e.percent)
                }
                history.put(o)
            }
            root.put("history", history)

            // Write
            target.writeText(root.toString())
            true
        } catch (_: Exception) { false }
    }

    fun importAll(context: Context, source: File): Pair<Boolean, String> {
        return try {
            if (!source.exists()) return Pair(false, "Файл не найден")
            val text = source.readText()
            if (text.isBlank()) return Pair(false, "Файл пустой")

            val root = JSONObject(text)

            // 1) listening_stats -> restore via ListeningStats to keep consistent format
            if (root.has("listening_stats")) {
                val arr = root.optJSONArray("listening_stats") ?: JSONArray()
                // Write to temp file as map path->count JSON expected by ListeningStats.importFromFile
                val map = linkedMapOf<String, Int>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val path = obj.optString("path", null)
                    val count = obj.optInt("count", 0)
                    if (!path.isNullOrBlank() && count > 0) map[path] = count
                }
                val tmp = File(context.cacheDir, "import_listening.json")
                tmp.writeText(com.google.gson.Gson().toJson(map))
                ListeningStats.importFromFile(context, tmp)
                tmp.delete()
            }

            // 2) app_usage_ms
            if (root.has("app_usage_ms")) {
                val total = root.optLong("app_usage_ms", 0L)
                val uprefs = context.getSharedPreferences(APP_USAGE_PREFS, Context.MODE_PRIVATE)
                uprefs.edit().putLong(APP_USAGE_KEY, total).apply()
            }

            // 3) history -> rebuild JSONL file
            if (root.has("history")) {
                PlayHistory.clear(context)
                val arr = root.optJSONArray("history") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val ev = PlayHistory.PlayEvent(
                        timestamp = o.optLong("timestamp"),
                        trackPath = o.optString("trackPath", null),
                        trackName = o.optString("trackName", null),
                        artist = o.optString("artist", null),
                        albumName = o.optString("albumName", null),
                        genre = o.optString("genre", null),
                        percent = if (o.has("percent")) o.optDouble("percent").toFloat() else null
                    )
                    PlayHistory.append(context, ev)
                }
            }

            // Notify
            context.sendBroadcast(android.content.Intent("com.example.music.STATS_UPDATED").setPackage(context.packageName))

            Pair(true, "Полный импорт статистики выполнен")
        } catch (e: Exception) {
            Pair(false, "Ошибка: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
