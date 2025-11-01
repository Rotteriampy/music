package com.example.music

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал событий прослушиваний для построения временных графиков.
 * Хранится как JSONL-файл: по одной JSON-строке на событие.
 */
object PlayHistory {
    private const val FOLDER = "history"
    private const val FILE_NAME = "plays.jsonl"

    data class PlayEvent(
        val timestamp: Long,
        val trackPath: String?,
        val trackName: String?,
        val artist: String?,
        val albumName: String?,
        val genre: String?,
        val percent: Float? = null
    )

    private fun file(context: Context): File {
        val dir = File(context.filesDir, FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun append(context: Context, event: PlayEvent) {
        try {
            val f = file(context)
            val obj = JSONObject().apply {
                put("timestamp", event.timestamp)
                put("trackPath", event.trackPath)
                put("trackName", event.trackName)
                put("artist", event.artist)
                put("albumName", event.albumName)
                put("genre", event.genre)
                if (event.percent != null) put("percent", event.percent)
            }
            f.appendText(obj.toString() + "\n")
        } catch (_: Exception) {}
    }

    fun readAll(context: Context): List<PlayEvent> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val result = ArrayList<PlayEvent>()
        f.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            try {
                val o = JSONObject(line)
                result.add(
                    PlayEvent(
                        timestamp = o.optLong("timestamp"),
                        trackPath = o.optString("trackPath", null),
                        trackName = o.optString("trackName", null),
                        artist = o.optString("artist", null),
                        albumName = o.optString("albumName", null),
                        genre = o.optString("genre", null),
                        percent = if (o.has("percent")) o.optDouble("percent", Double.NaN).toFloat().takeIf { !it.isNaN() } else null
                    )
                )
            } catch (_: Exception) {}
        }
        return result
    }

    fun clear(context: Context) {
        try { file(context).delete() } catch (_: Exception) {}
    }

    fun export(context: Context, target: File): Boolean {
        return try {
            val src = file(context)
            if (!src.exists()) return false
            src.copyTo(target, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun import(context: Context, source: File): Boolean {
        return try {
            if (!source.exists()) return false
            val dst = file(context)
            source.copyTo(dst, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    // Утилита: формат времени (может пригодиться для отладки)
    fun format(ts: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
}
