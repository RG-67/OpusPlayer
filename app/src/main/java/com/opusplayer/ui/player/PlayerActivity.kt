package com.opusplayer.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opusplayer.R
import com.opusplayer.databinding.ActivityPlayerBinding
import com.opusplayer.model.Song
import com.opusplayer.service.MusicService
import com.opusplayer.utils.gone
import com.opusplayer.utils.loadAlbumArtLarge
import com.opusplayer.utils.toTimeString
import com.opusplayer.utils.visible

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progressRunnable: Runnable
    private var isShuffleOn = false
    private var isRepeatOn = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? MusicService.MusicBinder ?: return
            musicService = b.getService()
            isBound = true
            updateDuration()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null; isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupProgressUpdater()
        setupClickListeners()
        setupSeekBars()
        observeService()
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun setupProgressUpdater() {
        progressRunnable = object : Runnable {
            override fun run() {
                val svc = musicService ?: return
                val pos = svc.getCurrentPosition()
                val dur = svc.getDuration()
                if (dur > 0) {
                    binding.seekbar.max = dur
                    binding.seekbar.progress = pos
                    binding.tvElapsed.text = pos.toTimeString()
                    binding.tvDuration.text = dur.toTimeString()
                }
                handler.postDelayed(this, 500)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCollapse.setOnClickListener { finish() }
        binding.btnPlayPause.setOnClickListener {
            val svc = musicService ?: return@setOnClickListener
            if (svc.getCurrentPosition() > 0 && MusicService.isPlaying.value == true) {
                svc.pausePlay()
            } else {
                svc.resumePlay()
            }
        }
        binding.btnNext.setOnClickListener { musicService?.playNext() }
        binding.btnPrev.setOnClickListener {
            val svc = musicService ?: return@setOnClickListener
            if (svc.getCurrentPosition() > 3000) svc.seekTo(0) else svc.playPrevious()
        }
        binding.btnShuffle.setOnClickListener {
            isShuffleOn = musicService?.toggleShuffle() ?: false
            binding.btnShuffle.setColorFilter(
                ContextCompat.getColor(
                    this,
                    if (isShuffleOn) R.color.accent_primary else R.color.text_secondary
                )
            )
        }
        binding.btnRepeat.setOnClickListener {
            isRepeatOn = musicService?.toggleRepeat() ?: false
            binding.btnRepeat.setColorFilter(
                ContextCompat.getColor(
                    this,
                    if (isRepeatOn) R.color.accent_primary else R.color.text_secondary
                )
            )
        }
        binding.llTimerIndicator.setOnClickListener {
            SleepTimerDialog().show(
                supportFragmentManager,
                "sleep_timer"
            )
        }
        binding.llLyricsBtn.setOnClickListener {
            Toast.makeText(
                this,
                "Lyrics not available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupSeekBars() {
        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvElapsed.text = progress.toTimeString()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                handler.removeCallbacks(progressRunnable)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                musicService?.seekTo(sb?.progress ?: 0); handler.post(progressRunnable)
            }
        })
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        binding.seekbarVolume.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.seekbarVolume.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.seekbarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) am.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    progress,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun observeService() {
        MusicService.currentSong.observe(this) { it?.let { s -> updateUI(s) } }
        MusicService.isPlaying.observe(this) { playing ->
            binding.btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            if (playing) handler.post(progressRunnable) else handler.removeCallbacks(
                progressRunnable
            )
        }
        MusicService.sleepTimerRemaining.observe(this) { rem ->
            if (rem > 0L) {
                binding.llTimerIndicator.visible()
                binding.tvTimerRemaining.text =
                    "Stops in %d:%02d".format(rem / 60000, (rem % 60000) / 1000)
            } else binding.llTimerIndicator.gone()
        }
    }

    private fun updateUI(song: Song) {
        binding.tvSongTitle.text = song.title
        binding.tvArtist.text = song.artist.uppercase()
        binding.ivAlbumArt.loadAlbumArtLarge(song.albumArtUri)
        binding.ivBgBlur.loadAlbumArtLarge(song.albumArtUri)
        binding.tvDuration.text = song.formattedDuration()
        binding.tvElapsed.text = "0:00"
        binding.seekbar.progress = 0
    }

    private fun updateDuration() {
        val dur = musicService?.getDuration() ?: return
        if (dur > 0) {
            binding.seekbar.max = dur; binding.tvDuration.text = dur.toTimeString()
        }
    }

    override fun onResume() {
        super.onResume(); if (MusicService.isPlaying.value == true) handler.post(progressRunnable)
    }

    override fun onPause() {
        super.onPause(); handler.removeCallbacks(progressRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
        if (isBound) {
            unbindService(serviceConnection); isBound = false
        }
    }
}
