package com.opusplayer.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.opusplayer.R
import com.opusplayer.databinding.ActivityMainBinding
import com.opusplayer.model.Song
import com.opusplayer.service.MusicService
import com.opusplayer.ui.player.PlayerActivity
import com.opusplayer.utils.PermissionHelper
import com.opusplayer.utils.gone
import com.opusplayer.utils.loadAlbumArt
import com.opusplayer.utils.visible

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private var musicService: MusicService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as? MusicService.MusicBinder)?.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null; isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        observePlayback()
        setupMiniPlayerClicks()
        PermissionHelper.requestAllPermissions(this)
        bindService(Intent(this, MusicService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        // This works because menu item IDs now match fragment IDs in nav_graph
        binding.bottomNav.setupWithNavController(navController)
    }

    private fun observePlayback() {
        MusicService.currentSong.observe(this) { song ->
            if (song != null) {
                binding.miniPlayerContainer.visible()
                updateMiniPlayerUI(song)
            } else {
                binding.miniPlayerContainer.gone()
            }
        }

        MusicService.isPlaying.observe(this) { playing ->
            findMiniView<ImageButton>(R.id.mini_play_btn)?.apply {
                setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.black))
            }
        }
    }

    private fun updateMiniPlayerUI(song: Song) {
        findMiniView<TextView>(R.id.mini_song_title)?.text = song.title
        findMiniView<TextView>(R.id.mini_song_artist)?.text = song.artist
        findMiniView<ImageView>(R.id.mini_album_art)?.loadAlbumArt(song.albumArtUri)
    }

    private fun setupMiniPlayerClicks() {
        binding.miniPlayerContainer.setOnClickListener {
            if (MusicService.currentSong.value != null)
                startActivity(Intent(this, PlayerActivity::class.java))
        }
        // We need post{} because the included layout's children aren't inflated yet at onCreate
        binding.miniPlayerContainer.post {
            findMiniView<ImageButton>(R.id.mini_play_btn)?.setOnClickListener {
                if (MusicService.isPlaying.value == true) musicService?.pausePlay()
                else musicService?.resumePlay()
            }
            findMiniView<ImageButton>(R.id.mini_prev_btn)?.setOnClickListener {
                musicService?.playPrevious()
            }
            findMiniView<ImageButton>(R.id.mini_next_btn)?.setOnClickListener {
                musicService?.playNext()
            }
        }
    }

    /** Finds a view inside the included mini_player layout */
    private fun <T : android.view.View> findMiniView(id: Int): T? {
        return binding.miniPlayerContainer.findViewById(id)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // HomeFragment.onResume() will reload songs automatically when user returns to it
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) { unbindService(serviceConnection); isBound = false }
    }
}
