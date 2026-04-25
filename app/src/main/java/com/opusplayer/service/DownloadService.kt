package com.opusplayer.service

import android.app.NotificationChannel
import com.opusplayer.utils.MediaStoreHelper
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.opusplayer.R
import com.opusplayer.model.DownloadItem
import com.opusplayer.model.DownloadStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "opus_download_channel"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_TITLE = "extra_title"

        val activeDownload = MutableLiveData<DownloadItem?>()
        val downloadComplete = MutableLiveData<String?>()   // path of completed file
        val downloadError = MutableLiveData<String?>()
    }

    private val binder = DownloadBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentDownloadJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val fileName = intent.getStringExtra(EXTRA_FILENAME) ?: "download.mp3"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: fileName

        startDownload(url, fileName, title)
        return START_STICKY
    }

    fun startDownload(url: String, fileName: String, title: String) {
        currentDownloadJob?.cancel()

        val item = DownloadItem(
            url = url,
            fileName = fileName,
            title = title,
            status = DownloadStatus.DOWNLOADING
        )
        activeDownload.postValue(item)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading: $title")
            .setContentText("Starting…")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        currentDownloadJob = serviceScope.launch {
            try {
                val destDir = getExternalFilesDir("Music")
                    ?: filesDir.resolve("Music").also { it.mkdirs() }
                destDir.mkdirs()

                val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                val destFile = File(destDir, sanitized)

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    item.status = DownloadStatus.FAILED
                    activeDownload.postValue(item)
                    downloadError.postValue("Server returned ${response.code}")
                    stopForeground(true)
                    return@launch
                }

                val body = response.body ?: run {
                    item.status = DownloadStatus.FAILED
                    activeDownload.postValue(item)
                    downloadError.postValue("Empty response body")
                    stopForeground(true)
                    return@launch
                }

                val contentLength = body.contentLength()
                item.totalBytes = contentLength

                FileOutputStream(destFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloaded = 0L
                    val inputStream = body.byteStream()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        item.downloadedBytes = downloaded

                        val progress = if (contentLength > 0)
                            ((downloaded * 100) / contentLength).toInt()
                        else 0

                        if (item.progress != progress) {
                            item.progress = progress
                            activeDownload.postValue(item.copy())
                            updateProgress(title, progress)
                        }
                    }
                }

                item.status = DownloadStatus.COMPLETED
                item.localPath = destFile.absolutePath
                item.progress = 100
                activeDownload.postValue(item.copy())
                MediaStoreHelper.scanFile(applicationContext, destFile.absolutePath)
                downloadComplete.postValue(destFile.absolutePath)

                stopForeground(true)
                activeDownload.postValue(null)
                stopSelf()

            } catch (e: CancellationException) {
                item.status = DownloadStatus.CANCELLED
                activeDownload.postValue(item)
                stopForeground(true)
            } catch (e: Exception) {
                item.status = DownloadStatus.FAILED
                activeDownload.postValue(item)
                downloadError.postValue(e.message ?: "Download failed")
                stopForeground(true)
            }
        }
    }

    fun cancelDownload() {
        currentDownloadJob?.cancel()
        activeDownload.postValue(null)
        stopForeground(true)
        stopSelf()
    }

    private fun updateProgress(title: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading: $title")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MP3 download progress"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
