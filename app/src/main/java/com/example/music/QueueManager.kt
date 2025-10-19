package com.example.music

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import android.util.Log

object QueueManager {
    private const val PREFS_NAME = "queue_prefs"
    private const val QUEUE_KEY = "current_queue"
    private const val CURRENT_TRACK_INDEX_KEY = "current_track_index"

    private var currentQueue: MutableList<Track> = mutableListOf()
    private var currentTrackIndex: Int = 0
    private var originalQueue: List<Track> = listOf()
    private var isManualQueueMode = false

    fun initializeQueueFromPosition(context: Context, tracks: List<Track>, startPosition: Int) {
        val availableTracks = tracks.filter { it.path != null && File(it.path).exists() }
        currentQueue.clear()
        currentQueue.addAll(availableTracks)
        originalQueue = availableTracks
        currentTrackIndex = if (startPosition in availableTracks.indices) startPosition else 0
        isManualQueueMode = false // Сброс режима при новом запуске
        saveQueue(context)
    }

    fun shuffleQueue(context: Context) {
        if (originalQueue.isNotEmpty()) {
            val currentTrack = getCurrentTrack()
            val others = originalQueue.filter { it.path != currentTrack?.path }
            val shuffled = others.shuffled()
            currentQueue.clear()
            if (currentTrack != null) {
                currentQueue.add(currentTrack)
            }
            currentQueue.addAll(shuffled)
            currentTrackIndex = 0
            isManualQueueMode = false // Сброс режима при перемешивании
            saveQueue(context)
        }
    }

    fun shuffleQueue(context: Context, tracks: List<Track>, startPosition: Int = 0) {
        val availableTracks = tracks.filter { it.path != null && File(it.path).exists() }
        originalQueue = availableTracks

        // Полностью перемешиваем все треки
        val shuffled = availableTracks.shuffled()
        currentQueue.clear()
        currentQueue.addAll(shuffled)
        currentTrackIndex = 0
        isManualQueueMode = false // Сброс режима при перемешивании
        saveQueue(context)
    }

    fun restoreOriginalQueue(context: Context) {
        if (originalQueue.isNotEmpty()) {
            val currentTrack = getCurrentTrack()
            currentQueue.clear()
            currentQueue.addAll(originalQueue)
            currentTrackIndex = currentQueue.indexOfFirst { it.path == currentTrack?.path }
                .coerceAtLeast(0)
            isManualQueueMode = false // Сброс режима при восстановлении
            saveQueue(context)
        }
    }

    fun addToManualQueue(context: Context, track: Track) {
        if (!isManualQueueMode) {
            // Первое добавление - очищаем очередь, оставляя только текущий трек
            val currentTrack = getCurrentTrack()
            currentQueue.clear()
            if (currentTrack != null) {
                currentQueue.add(currentTrack)
            }
            currentTrackIndex = 0
            isManualQueueMode = true
        }

        // Добавляем новый трек в конец
        currentQueue.add(track)
        saveQueue(context)
    }

    fun getCurrentQueue(): List<Track> = currentQueue.toList()

    fun getCurrentTrack(): Track? {
        return if (currentTrackIndex in currentQueue.indices) currentQueue[currentTrackIndex] else null
    }

    fun getCurrentTrackIndex(): Int = currentTrackIndex

    fun getNextTrack(): Track? {
        return if (currentTrackIndex + 1 < currentQueue.size) currentQueue[currentTrackIndex + 1] else null
    }

    fun getPreviousTrack(): Track? {
        return if (currentTrackIndex > 0) currentQueue[currentTrackIndex - 1] else null
    }

    fun moveItem(context: Context, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        if (fromPosition !in currentQueue.indices || toPosition !in currentQueue.indices) return

        val item = currentQueue.removeAt(fromPosition)
        currentQueue.add(toPosition, item)

        // Корректируем индекс текущего трека
        currentTrackIndex = when {
            fromPosition == currentTrackIndex -> toPosition
            fromPosition < currentTrackIndex && toPosition >= currentTrackIndex -> currentTrackIndex - 1
            fromPosition > currentTrackIndex && toPosition <= currentTrackIndex -> currentTrackIndex + 1
            else -> currentTrackIndex
        }

        // При ручном изменении порядка помечаем режим как ручной
        isManualQueueMode = true
        saveQueue(context)
    }

    fun moveToNextTrack(context: Context): Boolean {
        if (currentTrackIndex + 1 < currentQueue.size) {
            currentTrackIndex++
            saveQueue(context)
            return true
        }
        return false
    }

    fun moveToPreviousTrack(context: Context): Boolean {
        if (currentTrackIndex > 0) {
            currentTrackIndex--
            saveQueue(context)
            return true
        }
        return false
    }

    fun setCurrentIndex(context: Context, index: Int) {
        if (index in currentQueue.indices) {
            currentTrackIndex = index
            saveQueue(context)
        }
    }

    fun clearQueue(context: Context) {
        currentQueue.clear()
        originalQueue = listOf()
        currentTrackIndex = 0
        isManualQueueMode = false
        saveQueue(context)
    }

    fun isEmpty(): Boolean = currentQueue.isEmpty()

    private fun saveQueue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = Gson()
        editor.putString(QUEUE_KEY, gson.toJson(currentQueue))
        editor.putString("original_queue", gson.toJson(originalQueue))
        editor.putInt(CURRENT_TRACK_INDEX_KEY, currentTrackIndex)
        editor.putBoolean("is_manual_queue_mode", isManualQueueMode)
        editor.apply()
    }

    fun loadQueue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val queueJson = prefs.getString(QUEUE_KEY, null)
        val originalQueueJson = prefs.getString("original_queue", null)
        currentTrackIndex = prefs.getInt(CURRENT_TRACK_INDEX_KEY, 0)
        isManualQueueMode = prefs.getBoolean("is_manual_queue_mode", false)
        val gson = Gson()
        currentQueue = if (queueJson != null) {
            gson.fromJson(queueJson, object : TypeToken<MutableList<Track>>() {}.type) ?: mutableListOf()
        } else mutableListOf()
        originalQueue = if (originalQueueJson != null) {
            gson.fromJson(originalQueueJson, object : TypeToken<List<Track>>() {}.type) ?: listOf()
        } else currentQueue.toList()
    }

    fun setQueueOrder(context: Context, newOrder: List<Track>) {
        val currentPath = getCurrentTrack()?.path
        currentQueue = newOrder.toMutableList()
        currentTrackIndex = if (currentPath != null) {
            currentQueue.indexOfFirst { it.path == currentPath }.coerceAtLeast(0)
        } else 0
        isManualQueueMode = true
        saveQueue(context)
    }
}