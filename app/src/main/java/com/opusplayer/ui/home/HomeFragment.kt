package com.opusplayer.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.opusplayer.R
import com.opusplayer.databinding.FragmentHomeBinding
import com.opusplayer.model.Song
import com.opusplayer.service.MusicService
import com.opusplayer.ui.adapters.TrackAdapter
import com.opusplayer.ui.player.PlayerActivity
import com.opusplayer.utils.PermissionHelper
import com.opusplayer.utils.gone
import com.opusplayer.utils.loadAlbumArt
import com.opusplayer.utils.visible
import com.opusplayer.viewmodel.HomeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var trackAdapter: TrackAdapter

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupFilterBar()
        setupHeroPlayer()
        tryLoadSongs()
        bindMusicService()
    }

    /** Load songs — if permission not granted yet, request it then load on next resume */
    private fun tryLoadSongs() {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewModel.loadSongs()
        } else {
            PermissionHelper.requestStoragePermission(requireActivity())
        }
    }

    private fun setupRecyclerView() {
        trackAdapter = TrackAdapter(
            onSongClick = { song, index -> playSong(song, index) },
            onSongLongClick = { song -> showSongOptions(song) }
        )
        binding.rvTracks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = trackAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            // You can add a ProgressBar to fragment_home.xml and toggle it here
            // binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.filteredSongs.observe(viewLifecycleOwner) { songs ->
            trackAdapter.submitList(songs)
            binding.tvSongsCount.text = "${songs.size} SONGS"
            Log.d("SONG_LIST: ", songs.toString())
            if (songs.isEmpty()) {
                binding.emptyState.visible()
                binding.rvTracks.gone()
            } else {
                binding.emptyState.gone()
                binding.rvTracks.visible()
            }
        }

        MusicService.currentSong.observe(viewLifecycleOwner) { song ->
            updateHeroCard(song)
            trackAdapter.setPlayingSong(song?.path)
        }

        MusicService.isPlaying.observe(viewLifecycleOwner) { playing ->
            binding.btnHeroPlay.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
            binding.tvNowPausedLabel.text =
                if (playing) getString(R.string.now_playing) else getString(R.string.now_paused)
        }
    }

    private fun setupFilterBar() {
        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.filter(s?.toString() ?: "")
            }
        })
    }

    private fun setupHeroPlayer() {
        binding.cardNowPlaying.setOnClickListener {
            if (MusicService.currentSong.value != null)
                startActivity(Intent(requireContext(), PlayerActivity::class.java))
        }
        binding.btnHeroPlay.setOnClickListener {
            val service = musicService ?: return@setOnClickListener
            if (MusicService.isPlaying.value == true) service.pausePlay() else service.resumePlay()
        }
    }

    private fun updateHeroCard(song: Song?) {
        if (song == null) {
            binding.tvNowPlayingTitle.text = "Tap a song to play"
            binding.tvNowPlayingArtist.text = ""
            binding.ivNowPlayingBg.setImageDrawable(null)
            return
        }
        binding.tvNowPlayingTitle.text = song.title
        binding.tvNowPlayingArtist.text = song.artist
        binding.ivNowPlayingBg.loadAlbumArt(song.albumArtUri, cornerRadius = 0)
    }

    private fun playSong(song: Song, index: Int) {
        val serviceIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().startForegroundService(serviceIntent)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val svc = (binder as? MusicService.MusicBinder)?.getService() ?: return
                svc.setPlaylist(viewModel.getPlaylist(), index)
                requireContext().unbindService(this)
                startActivity(Intent(requireContext(), PlayerActivity::class.java))
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        requireContext().bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun showSongOptions(song: Song) {
        val options = arrayOf("Play", "Song info")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val idx = viewModel.getPlaylist().indexOfFirst { it.path == song.path }
                        if (idx >= 0) playSong(song, idx)
                    }

                    1 -> showSongInfo(song)
                }
            }.show()
    }

    private fun showSongInfo(song: Song) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Song Info")
            .setMessage(
                "Title: ${song.title}\nArtist: ${song.artist}\n" +
                        "Album: ${song.album}\nDuration: ${song.formattedDuration()}\n" +
                        "Size: ${song.formattedSize()}\nPath: ${song.path}"
            )
            .setPositiveButton("OK", null).show()
    }

    private fun bindMusicService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** Reload songs every time the fragment becomes visible (e.g. after download, after permission grant) */
    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewModel.loadSongs()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireContext().unbindService(serviceConnection); isBound = false
        }
        _binding = null
    }
}
