package com.opusplayer.model

data class DownloadItem(
    val url: String,
    val fileName: String,
    val title: String = fileName,
    var progress: Int = 0,
    var status: DownloadStatus = DownloadStatus.PENDING,
    var downloadedBytes: Long = 0L,
    var totalBytes: Long = 0L,
    var localPath: String? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TrendingItem(
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val searchQuery: String = "$title $artist"
)
