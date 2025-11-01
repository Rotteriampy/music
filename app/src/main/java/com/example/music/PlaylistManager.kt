package com.arotter.music

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log
import java.io.File

object PlaylistManager {
    private const val PREFS_NAME = "playlist_prefs"
    private const val PLAYLISTS_KEY = "playlists"

    private var playlists: MutableList<Playlist> = mutableListOf()

    fun getPlaylists(): List<Playlist> = playlists.toList()

    fun addPlaylist(context: Context, playlist: Playlist) {
        playlists.add(playlist)
        savePlaylists(context)
    }

    fun removePlaylist(context: Context, playlistId: String) {
        playlists.removeIf { it.id == playlistId }
        savePlaylists(context)
    }

    fun addTrackToPlaylist(context: Context, playlistId: String, track: Track) {
        playlists.find { it.id == playlistId }?.tracks?.add(track)
        savePlaylists(context)
    }

    fun savePlaylists(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = Gson()
        val playlistsJson = gson.toJson(playlists)
        editor.putString(PLAYLISTS_KEY, playlistsJson)
        editor.apply()
        Log.d("PlaylistManager", "Playlists saved: $playlists")
    }

    fun loadPlaylists(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val playlistsJson = prefs.getString(PLAYLISTS_KEY, null)
        if (playlistsJson != null) {
            val gson = Gson()
            val type = object : TypeToken<MutableList<Playlist>>() {}.type
            playlists = gson.fromJson(playlistsJson, type) ?: mutableListOf()
        } else {
            playlists = mutableListOf()
        }
        Log.d("PlaylistManager", "Loaded playlists: $playlists")
    }

    fun createFavoritesIfNotExists(context: Context) {
        val playlists = getPlaylists()
        if (playlists.none { it.id == Playlist.FAVORITES_ID }) {
            val favorites = Playlist(id = Playlist.FAVORITES_ID, name = "Избранное", tracks = mutableListOf())
            addPlaylist(context, favorites)
        }
    }

    fun addToFavorites(context: Context, track: Track) {
        createFavoritesIfNotExists(context)
        val favorites = getPlaylists().find { it.id == Playlist.FAVORITES_ID }!!
        if (!favorites.tracks.any { it.path == track.path }) {
            favorites.tracks.add(track)
            savePlaylists(context)
        }
    }

    fun removeFromFavorites(context: Context, track: Track) {
        val favorites = getPlaylists().find { it.id == Playlist.FAVORITES_ID }
        favorites?.tracks?.removeAll { it.path == track.path }
        savePlaylists(context)
    }

    fun isInFavorites(track: Track): Boolean {
        val favorites = getPlaylists().find { it.id == Playlist.FAVORITES_ID }
        return favorites?.tracks?.any { it.path == track.path } == true
    }

    fun removeTrackFromPlaylist(context: Context, playlistId: String, position: Int) {
        val playlist = playlists.find { it.id == playlistId }
        if (playlist != null && position in playlist.tracks.indices) {
            playlist.tracks.removeAt(position)
            savePlaylists(context)
        }
    }

    fun updatePlaylistOrder(context: Context, newOrder: List<Playlist>) {
        playlists.clear()
        playlists.addAll(newOrder)
        savePlaylists(context)
    }

    fun updatePlaylist(context: Context, playlistId: String, newName: String? = null, newCoverUri: String? = null) {
        val idx = playlists.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val cur = playlists[idx]
            val updated = cur.copy(
                name = newName ?: cur.name,
                coverUri = newCoverUri ?: cur.coverUri
            )
            playlists[idx] = updated
            savePlaylists(context)
        }
    }

    fun cleanupDeletedTracks(context: Context) {
        var hasChanges = false
        playlists.forEach { playlist ->
            val before = playlist.tracks.size
            playlist.tracks.removeAll { track ->
                track.path == null || !File(track.path).exists()
            }
            if (playlist.tracks.size != before) {
                hasChanges = true
            }
        }
        if (hasChanges) {
            savePlaylists(context)
        }
    }
}