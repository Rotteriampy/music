package com.arotter.music

import java.util.UUID

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val artist: String? = null,
    val albumId: Long? = null,
    val albumName: String? = null,
    val genre: String? = null,
    val path: String? = null,
    val duration: Long? = null,
    val dateModified: Long? = null
)

// Модель плейлиста
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tracks: MutableList<Track> = mutableListOf(),
    val coverUri: String? = null
) {
    companion object {
        const val FAVORITES_ID = "favorites"
    }
}

// Модель альбома
data class Album(
    val name: String,
    val artist: String?,
    val tracks: MutableList<Track> = mutableListOf()
)

// Модель исполнителя
data class Artist(
    val name: String,
    val tracks: MutableList<Track> = mutableListOf()
)

// Модель жанра
data class Genre(
    val name: String,
    val tracks: MutableList<Track> = mutableListOf()
)