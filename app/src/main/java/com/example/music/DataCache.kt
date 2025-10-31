package com.example.music

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object DataCache {
    private const val CACHE_DIR = "app_cache"
    private const val TRACKS_CACHE = "tracks_cache.json"
    private const val PLAYLISTS_CACHE = "playlists_cache.json"
    private const val GENRES_CACHE = "genres_cache.json"
    private const val ARTISTS_CACHE = "artists_cache.json"
    private const val ALBUMS_CACHE = "albums_cache.json"

    private val gson = Gson()

    // Кэш треков
    fun saveTracks(context: Context, tracks: List<Track>) {
        saveToFile(context, TRACKS_CACHE, tracks)
    }

    fun loadTracks(context: Context): List<Track>? {
        val type = object : TypeToken<List<Track>>() {}.type
        return loadFromFile(context, TRACKS_CACHE, type)
    }

    // Кэш плейлистов
    fun savePlaylists(context: Context, playlists: List<Playlist>) {
        saveToFile(context, PLAYLISTS_CACHE, playlists)
    }

    fun loadPlaylists(context: Context): List<Playlist>? {
        val type = object : TypeToken<List<Playlist>>() {}.type
        return loadFromFile(context, PLAYLISTS_CACHE, type)
    }

    // Кэш жанров
    fun saveGenres(context: Context, genres: List<Genre>) {
        saveToFile(context, GENRES_CACHE, genres)
    }

    fun loadGenres(context: Context): List<Genre>? {
        val type = object : TypeToken<List<Genre>>() {}.type
        return loadFromFile(context, GENRES_CACHE, type)
    }

    // Кэш исполнителей
    fun saveArtists(context: Context, artists: List<Artist>) {
        saveToFile(context, ARTISTS_CACHE, artists)
    }

    fun loadArtists(context: Context): List<Artist>? {
        val type = object : TypeToken<List<Artist>>() {}.type
        return loadFromFile(context, ARTISTS_CACHE, type)
    }

    // Кэш альбомов
    fun saveAlbums(context: Context, albums: List<Album>) {
        saveToFile(context, ALBUMS_CACHE, albums)
    }

    fun loadAlbums(context: Context): List<Album>? {
        val type = object : TypeToken<List<Album>>() {}.type
        return loadFromFile(context, ALBUMS_CACHE, type)
    }

    private fun <T> saveToFile(context: Context, fileName: String, data: T) {
        try {
            val dir = File(context.filesDir, CACHE_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, fileName)
            val json = gson.toJson(data)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun <T> loadFromFile(context: Context, fileName: String, type: java.lang.reflect.Type): T? {
        return try {
            val file = File(File(context.filesDir, CACHE_DIR), fileName)
            if (file.exists()) {
                val json = file.readText()
                gson.fromJson(json, type)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearAll(context: Context) {
        val dir = File(context.filesDir, CACHE_DIR)
        dir.deleteRecursively()
    }
}