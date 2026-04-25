package com.opusplayer.viewmodel

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.opusplayer.utils.MediaScanner
import com.opusplayer.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager.getInstance(application)

    val username = MutableLiveData<String>()
    val isDarkMode = MutableLiveData<Boolean>()
    val downloadQuality = MutableLiveData<String>()
    val sleepTimerMinutes = MutableLiveData<Int>(0)

    // Storage
    val usedBytes = MutableLiveData<Long>(0L)
    val totalBytes = MutableLiveData<Long>(10L * 1024 * 1024 * 1024)  // default 10 GB
    val freeBytes = MutableLiveData<Long>(0L)

    init {
        loadPrefs()
        loadStorageInfo()
    }

    private fun loadPrefs() {
        username.value = prefs.getUsername()
        isDarkMode.value = prefs.isDarkMode()
        downloadQuality.value = prefs.getDownloadQuality()
    }

    fun loadStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val used = MediaScanner.getStorageUsedByApp(getApplication())
            usedBytes.postValue(used)

            try {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val total = stat.totalBytes
                val free = stat.availableBytes
                totalBytes.postValue(total)
                freeBytes.postValue(free)
            } catch (e: Exception) {
                freeBytes.postValue(10L * 1024 * 1024 * 1024 - used)
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.setDarkMode(enabled)
        isDarkMode.value = enabled
    }

    fun setDownloadQuality(quality: String) {
        prefs.setDownloadQuality(quality)
        downloadQuality.value = quality
    }

    fun setUsername(name: String) {
        prefs.setUsername(name)
        username.value = name
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerMinutes.value = minutes
    }

    fun getUsedPercent(): Int {
        val total = totalBytes.value ?: 1L
        val used = usedBytes.value ?: 0L
        return ((used * 100) / total).toInt().coerceIn(0, 100)
    }

    fun getUsedGb(): Float = (usedBytes.value ?: 0L) / (1024f * 1024f * 1024f)
    fun getTotalGb(): Float = (totalBytes.value ?: 0L) / (1024f * 1024f * 1024f)
    fun getFreeGb(): Float = (freeBytes.value ?: 0L) / (1024f * 1024f * 1024f)
}
