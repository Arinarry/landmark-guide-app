package com.example.diplom

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object FavoritesManager {
    private const val PREFS_NAME = "favorites_prefs"
    private const val FAVORITES_KEY = "favorite_landmarks"
    private const val SYNC_TIMESTAMP_KEY = "last_sync_timestamp"
    private lateinit var preferences: SharedPreferences
    private lateinit var apiService: ApiService

    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiService = ApiService(context)
    }

    suspend fun syncFavorites(userId: Int?) {
        if (userId == null || userId == -1) return

        try {
            val serverFavorites = apiService.getFavorites(userId)
            val localFavorites = getFavorites().map { it.toInt() }.toSet()

            val toAdd = localFavorites - serverFavorites.toSet()
            toAdd.forEach { landmarkId ->
                apiService.addFavorite(userId, landmarkId)
            }

            val mergedFavorites = (localFavorites + serverFavorites).map { it.toString() }.toSet()
            preferences.edit().putStringSet(FAVORITES_KEY, mergedFavorites).apply()

            preferences.edit().putLong(SYNC_TIMESTAMP_KEY, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.e("FavoritesManager", "Sync failed", e)
        }
    }

    suspend fun addFavorite(userId: Int?, id: Int) {
        val favorites = getFavorites().toMutableSet()
        favorites.add(id.toString())
        preferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()
        Log.d("FavoritesManager", "Favorites after add: $favorites")

        if (userId != null && userId != -1) {
            try {
                apiService.addFavorite(userId, id)
            } catch (e: Exception) {
                Log.e("FavoritesManager", "Failed to add favorite to server", e)
            }
        }
    }

    suspend fun removeFavorite(userId: Int?, id: Int) {
        val favorites = getFavorites().toMutableSet()
        favorites.remove(id.toString())
        preferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()

        if (userId != null && userId != -1) {
            try {
                apiService.removeFavorite(userId, id)
            } catch (e: Exception) {
                Log.e("FavoritesManager", "Failed to remove favorite from server", e)
            }
        }
    }

    fun isFavorite(id: Int): Boolean {
        return getFavorites().contains(id.toString())
    }

    fun getFavorites(): Set<String> {
        return preferences.getStringSet(FAVORITES_KEY, emptySet()) ?: emptySet()
    }
}