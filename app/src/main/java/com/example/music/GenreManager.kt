package com.example.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import java.io.File

object GenreManager {

    fun getGenresFromTracks(context: Context): List<Genre> {
        val genreMap = mutableMapOf<String, Genre>()

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

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
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
                val albumName = cursor.getString(albumColumn)
                val path = cursor.getString(dataColumn)
                val duration = cursor.getLong(durationColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

                if (path != null && File(path).exists()) {
                    // Извлекаем жанр из метаданных
                    var genreName = "Unknown"
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(path)
                        genreName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "Unknown"
                        retriever.release()
                    } catch (e: Exception) {
                        // Используем "Unknown"
                    }

                    val track = Track(
                        id = id,
                        name = title,
                        artist = artist,
                        albumId = albumId,
                        albumName = albumName,
                        genre = genreName,
                        path = path,
                        duration = duration,
                        dateModified = dateModified
                    )

                    val genre = genreMap.getOrPut(genreName) {
                        Genre(name = genreName)
                    }
                    genre.tracks.add(track)
                }
            }
        }

        return genreMap.values.toList().sortedBy { it.name }
    }
}