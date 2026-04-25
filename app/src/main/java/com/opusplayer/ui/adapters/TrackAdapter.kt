package com.opusplayer.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.opusplayer.R
import com.opusplayer.databinding.ItemTrackBinding
import com.opusplayer.model.Song
import com.opusplayer.utils.loadAlbumArt

class TrackAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onSongLongClick: (Song) -> Unit = {}
) : ListAdapter<Song, TrackAdapter.TrackViewHolder>(SongDiffCallback()) {

    private var playingSongPath: String? = null

    fun setPlayingSong(path: String?) {
        val oldPath = playingSongPath
        playingSongPath = path
        if (oldPath != null) {
            val oldIdx = currentList.indexOfFirst { it.path == oldPath }
            if (oldIdx >= 0) notifyItemChanged(oldIdx)
        }
        if (path != null) {
            val newIdx = currentList.indexOfFirst { it.path == path }
            if (newIdx >= 0) notifyItemChanged(newIdx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class TrackViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, position: Int) {
            val ctx = binding.root.context
            val isPlaying = song.path == playingSongPath

            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvDuration.text = song.formattedDuration()

            // Album art
            binding.ivAlbumArt.loadAlbumArt(song.albumArtUri, cornerRadius = 8)

            // Highlight playing song
            if (isPlaying) {
                binding.rootLayout.setBackgroundResource(R.drawable.bg_song_selected)
                binding.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.accent_primary))
                binding.ivStatus.setImageResource(R.drawable.ic_equalizer)
            } else {
                binding.rootLayout.setBackgroundResource(R.drawable.bg_card)
                binding.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                binding.ivStatus.setImageResource(R.drawable.ic_downloaded)
            }

            binding.root.setOnClickListener { onSongClick(song, position) }
            binding.root.setOnLongClickListener {
                onSongLongClick(song)
                true
            }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem == newItem
    }
}
