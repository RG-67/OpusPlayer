package com.opusplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.opusplayer.model.TrendingItem
import com.opusplayer.utils.PrefsManager

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager.getInstance(application)

    val recentSearches = MutableLiveData<List<String>>(emptyList())
    val currentUrl = MutableLiveData<String?>(null)
    val isWebViewVisible = MutableLiveData<Boolean>(false)

    val trendingItems = MutableLiveData<List<TrendingItem>>(
        listOf(
            TrendingItem("Neon Horizons", "Vanguard Collective"),
            TrendingItem("Midnight Echoes", "The Nocturnalists"),
            TrendingItem("Stardust Dreams", "Cosmic Weaver"),
            TrendingItem("Obsidian Pulse", "Deep State"),
            TrendingItem("Electric Reverie", "Lumina Collective"),
            TrendingItem("Solar Drift", "Phantom Keys")
        )
    )

    init {
        loadRecentSearches()
    }

    fun loadRecentSearches() {
        recentSearches.value = prefs.getRecentSearches()
    }

    fun addSearch(query: String) {
        prefs.addRecentSearch(query)
        loadRecentSearches()
    }

    fun clearRecentSearches() {
        prefs.clearRecentSearches()
        recentSearches.value = emptyList()
    }

    fun navigateTo(url: String) {
        val fullUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode("$url mp3 download")}"
        }
        addSearch(url)
        currentUrl.value = fullUrl
        isWebViewVisible.value = true
    }

    fun showSuggestions() {
        isWebViewVisible.value = false
        currentUrl.value = null
    }
}
