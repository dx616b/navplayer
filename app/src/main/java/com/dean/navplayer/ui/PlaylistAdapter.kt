package com.dean.navplayer.ui

import android.util.TypedValue
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
    private var rowMinHeightPx: Int,
    private var coverSizePx: Int,
    private var nameTextSizeSp: Float,
    private val onClick: (PlaylistSummary) -> Unit,
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    var playingPlaylistId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    fun updateLayout(rowMinHeightPx: Int, coverSizePx: Int, nameTextSizeSp: Float) {
        if (this.rowMinHeightPx == rowMinHeightPx &&
            this.coverSizePx == coverSizePx &&
            this.nameTextSizeSp == nameTextSizeSp
        ) {
            return
        }
        this.rowMinHeightPx = rowMinHeightPx
        this.coverSizePx = coverSizePx
        this.nameTextSizeSp = nameTextSizeSp
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        private var coverJob: Job? = null

        fun bind(
            item: PlaylistSummary,
            scope: CoroutineScope,
            subsonic: SubsonicClient,
            config: ServerConfig,
            rowMinHeightPx: Int,
            coverSizePx: Int,
            nameTextSizeSp: Float,
            playingPlaylistId: String?,
            click: (PlaylistSummary) -> Unit,
        ) {
            coverJob?.cancel()
            binding.root.minimumHeight = rowMinHeightPx
            binding.playlistArt.layoutParams = binding.playlistArt.layoutParams.apply {
                width = coverSizePx
                height = coverSizePx
            }
            binding.playlistName.text = item.name
            binding.playlistName.setTextSize(TypedValue.COMPLEX_UNIT_SP, nameTextSizeSp)
            binding.playlistArt.setImageResource(R.drawable.ic_cover_placeholder)
            binding.root.setBackgroundResource(
                if (item.id == playingPlaylistId) R.drawable.bg_list_item_playing else R.drawable.bg_list_item,
            )
            binding.root.setOnClickListener { click(item) }

            val coverArtId = item.coverArtId
            binding.root.tag = coverArtId
            coverJob = CoverArtLoader.load(
                scope,
                subsonic,
                config,
                coverArtId,
                SubsonicClient.COVER_SIZE_THUMB,
                maxSidePx = coverSizePx,
                lowMemory = true,
                isCurrent = { binding.root.tag == coverArtId },
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
        holder.bind(
            items[position],
            scope,
            subsonic,
            config,
            rowMinHeightPx,
            coverSizePx,
            nameTextSizeSp,
            playingPlaylistId,
            onClick,
        )
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size
}
