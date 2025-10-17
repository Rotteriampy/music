package com.example.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GenreManager {

    private var cachedGenres: List<Genre>? = null
    private var lastUpdateTime: Long = 0
    private const val CACHE_VALIDITY_MS = 30000L // 30 секунд

    suspend fun getGenresFromTracksAsync(context: Context): List<Genre> {
        val currentTime = System.currentTimeMillis()

        // Возвращаем кэшированные жанры, если они свежие
        if (cachedGenres != null && (currentTime - lastUpdateTime) < CACHE_VALIDITY_MS) {
            return cachedGenres!!
        }

        return withContext(Dispatchers.IO) {
            val genreMap = mutableMapOf<String, MutableList<Track>>()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn).toString()
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val album = cursor.getString(albumColumn)
                    val path = cursor.getString(dataColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)

                    if (path != null && File(path).exists()) {
                        // Быстрое извлечение жанра из метаданных
                        val genre = extractGenreFast(path) ?: "Unknown"

                        val track = Track(
                            id = id,
                            name = title,
                            artist = artist,
                            albumId = albumId,
                            albumName = album,
                            genre = genre,
                            path = path,
                            duration = duration,
                            dateModified = dateModified
                        )

                        if (!genreMap.containsKey(genre)) {
                            genreMap[genre] = mutableListOf()
                        }
                        genreMap[genre]?.add(track)
                    }
                }
            }

            val genres = genreMap.map { (genreName, tracks) ->
                Genre(name = genreName, tracks = tracks)
            }.sortedBy { it.name }

            // Сохраняем в кэш
            cachedGenres = genres
            lastUpdateTime = currentTime

            genres
        }
    }

    // Синхронный метод для обратной совместимости
    fun getGenresFromTracks(context: Context): List<Genre> {
        // Возвращаем кэш или пустой список
        return cachedGenres ?: emptyList()
    }

    private fun extractGenreFast(path: String): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            retriever.release()
            genre
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        cachedGenres = null
        lastUpdateTime = 0
    }
}