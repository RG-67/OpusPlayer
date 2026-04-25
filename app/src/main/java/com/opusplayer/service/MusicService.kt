package com.opusplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.opusplayer.R
import com.opusplayer.model.Song
import com.opusplayer.ui.MainActivity

class MusicService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "opus_player_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_PREV = "action_prev"
        const val ACTION_NEXT = "action_next"
        const val ACTION_STOP = "action_stop"

        val currentSong = MutableLiveData<Song?>()
        val isPlaying = MutableLiveData<Boolean>(false)
        val currentPosition = MutableLiveData<Int>(0)
        val sleepTimerRemaining = MutableLiveData<Long>(0L)
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null
    private var sleepTimer: CountDownTimer? = null

    private var playlist: MutableList<Song> = mutableListOf()
    private var currentIndex: Int = -1
    private var isShuffleOn = false
    private var isRepeatOn = false

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, "OpusPlayerSession")
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        val service = this@MusicService          // capture service reference
        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnCompletionListener {
                if (service.isRepeatOn) {
                    seekTo(0)
                    start()
                } else {
                    service.playNext()
                }
            }
            setOnPreparedListener {
                start()
                updatePlaybackState()
                service.startForeground(NOTIFICATION_ID, service.buildNotification())
            }
            setOnErrorListener { _, _, _ ->
                MusicService.isPlaying.postValue(false)
                false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> resumePlay()
            ACTION_PAUSE -> pausePlay()
            ACTION_PREV -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        playlist = songs.toMutableList()
        currentIndex = startIndex
        playCurrent()
    }

    fun playSong(song: Song) {
        val idx = playlist.indexOfFirst { it.path == song.path }
        if (idx >= 0) {
            currentIndex = idx
            playCurrent()
        } else {
            playlist.add(song)
            currentIndex = playlist.size - 1
            playCurrent()
        }
    }

    private fun playCurrent() {
        if (currentIndex < 0 || currentIndex >= playlist.size) return
        val song = playlist[currentIndex]
        currentSong.postValue(song)
        requestAudioFocus()
        mediaPlayer?.apply {
            reset()
            try {
                setDataSource(song.path)
                prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumePlay() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                requestAudioFocus()
                it.start()
                updatePlaybackState()
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    fun pausePlay() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                updatePlaybackState()
                updateNotification()
            }
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        currentIndex = if (isShuffleOn) {
            (0 until playlist.size).random()
        } else {
            (currentIndex + 1) % playlist.size
        }
        playCurrent()
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
        playCurrent()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun toggleShuffle(): Boolean {
        isShuffleOn = !isShuffleOn
        return isShuffleOn
    }

    fun toggleRepeat(): Boolean {
        isRepeatOn = !isRepeatOn
        return isRepeatOn
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) {
            sleepTimerRemaining.postValue(0L)
            return
        }
        val millis = minutes * 60_000L
        sleepTimer = object : CountDownTimer(millis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerRemaining.postValue(millisUntilFinished)
            }

            override fun onFinish() {
                sleepTimerRemaining.postValue(0L)
                pausePlay()
            }
        }.start()
    }

    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimerRemaining.postValue(0L)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> pausePlay()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlay()
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Opus Player music controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val song = currentSong.value
        val playing = isPlaying.value ?: false

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPending = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).apply {
                action = if (playing) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, MusicService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song?.title ?: "Opus Player")
            .setContentText(song?.artist ?: "")
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_music_note))
            .setContentIntent(mainPending)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updatePlaybackState() {
        isPlaying.postValue(mediaPlayer?.isPlaying == true)
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepTimer?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
    }
}
