package com.opusplayer.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,        // milliseconds
    val path: String = "",
    val albumArtUri: String? = null,
    val size: Long = 0L             // bytes
) : Parcelable {

    fun formattedDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun formattedSize(): String {
        val mb = size / (1024.0 * 1024.0)
        return "%.1f MB".format(mb)
    }
}
