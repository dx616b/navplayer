package com.dean.navplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dean.navplayer.R
import com.dean.navplayer.data.CoverArtLoader
import com.dean.navplayer.data.PlaylistSummary
import com.dean.navplayer.data.ServerConfig
import com.dean.navplayer.data.SubsonicClient
import com.dean.navplayer.databinding.ItemPlaylistBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class PlaylistAdapter(
    private val items: List<PlaylistSummary>,
    private val scope: CoroutineScope,
    private val subsonic: SubsonicClient,
    private val config: ServerConfig,
    private val rowMinHeightPx: Int,
    private val coverSizePx: Int,
    private val onClick: (PlaylistSummary) -> Unit,
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        private var coverJob: Job? = null

        fun bind(
            item: PlaylistSummary,
            position: Int,
            scope: CoroutineScope,
            subsonic: SubsonicClient,
            config: ServerConfig,
            rowMinHeightPx: Int,
            coverSizePx: Int,
            click: (PlaylistSummary) -> Unit,
        ) {
            coverJob?.cancel()
            binding.root.minimumHeight = rowMinHeightPx
            binding.playlistArt.layoutParams = binding.playlistArt.layoutParams.apply {
                width = coverSizePx
                height = coverSizePx
            }
            binding.playlistName.text = item.name
            binding.playlistArt.setImageResource(R.drawable.ic_cover_placeholder)
            binding.root.setOnClickListener { click(item) }

            coverJob = CoverArtLoader.load(
                scope,
                subsonic,
                config,
                item.coverArtId,
                SubsonicClient.COVER_SIZE_THUMB,
                maxSidePx = coverSizePx,
                lowMemory = true,
                isCurrent = { bindingAdapterPosition == position },
                apply = { binding.playlistArt.setImageBitmap(it) },
            )
        }

        fun clear() {
            coverJob?.cancel()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position, scope, subsonic, config, rowMinHeightPx, coverSizePx, onClick)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size
}
