package com.opusplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.opusplayer.model.Song
import com.opusplayer.utils.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val songs = MutableLiveData<List<Song>>(emptyList())
    val filteredSongs = MutableLiveData<List<Song>>(emptyList())
    val isLoading = MutableLiveData<Boolean>(false)

    private var allSongs: List<Song> = emptyList()

    fun loadSongs() {
        isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = MediaScanner.getAllSongs(getApplication())
            allSongs = loaded
            songs.postValue(loaded)
            filteredSongs.postValue(loaded)
            isLoading.postValue(false)
        }
    }

    fun filter(query: String) {
        if (query.isBlank()) {
            filteredSongs.value = allSongs
            return
        }
        val lower = query.lowercase()
        filteredSongs.value = allSongs.filter {
            it.title.lowercase().contains(lower) ||
            it.artist.lowercase().contains(lower)
        }
    }

    fun getSongAt(index: Int): Song? = filteredSongs.value?.getOrNull(index)

    fun getPlaylist(): List<Song> = filteredSongs.value ?: emptyList()
}
