package com.opusplayer.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.opusplayer.databinding.ItemTrendingBinding
import com.opusplayer.model.TrendingItem
import com.opusplayer.utils.loadAlbumArt

class TrendingAdapter(
    private val onDownloadClick: (TrendingItem) -> Unit
) : RecyclerView.Adapter<TrendingAdapter.TrendingViewHolder>() {

    private val items = mutableListOf<TrendingItem>()

    fun submitList(list: List<TrendingItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingViewHolder {
        val binding = ItemTrendingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrendingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrendingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TrendingViewHolder(
        private val binding: ItemTrendingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TrendingItem) {
            binding.tvTitle.text = item.title
            binding.tvArtist.text = item.artist
            binding.ivThumb.loadAlbumArt(item.thumbnailUrl, cornerRadius = 8)
            binding.btnDownload.setOnClickListener { onDownloadClick(item) }
        }
    }
}
