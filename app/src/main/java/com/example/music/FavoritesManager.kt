package com.arotter.music

import android.content.Context

object FavoritesManager {
    private const val PREFS_NAME = "favorites"
    private const val FAVORITES_KEY = "favorite_tracks"

    private val favorites = mutableSetOf<String>()

    fun loadFavorites(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val favSet = prefs.getStringSet(FAVORITES_KEY, emptySet()) ?: emptySet()
        favorites.clear()
        favorites.addAll(favSet)
    }

    fun saveFavorites(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(FAVORITES_KEY, favorites).apply()
    }

    fun addToFavorites(context: Context, trackPath: String) {
        favorites.add(trackPath)
        saveFavorites(context)
    }

    fun removeFromFavorites(context: Context, trackPath: String) {
        favorites.remove(trackPath)
        saveFavorites(context)
    }

    fun isFavorite(trackPath: String): Boolean {
        return favorites.contains(trackPath)
    }

    fun toggleFavorite(context: Context, trackPath: String): Boolean {
        val isFav = if (isFavorite(trackPath)) {
            removeFromFavorites(context, trackPath)
            false
        } else {
            addToFavorites(context, trackPath)
            true
        }
        return isFav
    }

    fun getFavorites(): Set<String> {
        return favorites.toSet()
    }
}