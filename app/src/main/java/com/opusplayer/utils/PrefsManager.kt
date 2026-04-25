package com.opusplayer.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "opus_player_prefs"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LAST_SONG_PATH = "last_song_path"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_USERNAME = "username"
        private const val MAX_RECENT_SEARCHES = 8

        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ---- Recent Searches ----

    fun getRecentSearches(): List<String> {
        val json = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecentSearch(query: String) {
        val searches = getRecentSearches().toMutableList()
        searches.remove(query)          // remove duplicate
        searches.add(0, query)          // insert at top
        if (searches.size > MAX_RECENT_SEARCHES) {
            searches.subList(MAX_RECENT_SEARCHES, searches.size).clear()
        }
        prefs.edit().putString(KEY_RECENT_SEARCHES, gson.toJson(searches)).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    // ---- Download Quality ----

    fun getDownloadQuality(): String =
        prefs.getString(KEY_DOWNLOAD_QUALITY, "Lossless") ?: "Lossless"

    fun setDownloadQuality(quality: String) {
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
    }

    // ---- Dark Mode ----

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    // ---- Last Playback State ----

    fun saveLastSong(path: String, position: Int) {
        prefs.edit()
            .putString(KEY_LAST_SONG_PATH, path)
            .putInt(KEY_LAST_POSITION, position)
            .apply()
    }

    fun getLastSongPath(): String? = prefs.getString(KEY_LAST_SONG_PATH, null)

    fun getLastPosition(): Int = prefs.getInt(KEY_LAST_POSITION, 0)

    // ---- Username ----

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "Julian Vance") ?: "Julian Vance"

    fun setUsername(name: String) {
        prefs.edit().putString(KEY_USERNAME, name).apply()
    }
}
