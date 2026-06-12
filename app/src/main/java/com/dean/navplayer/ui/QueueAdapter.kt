package com.dean.navplayer.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dean.navplayer.R
import com.dean.navplayer.databinding.ItemQueueTrackBinding

data class QueueItem(
    val playerIndex: Int,
    val title: String,
    val artist: String,
    val isCurrent: Boolean,
)

class QueueAdapter(
    private val onSelect: (Int) -> Unit,
) : RecyclerView.Adapter<QueueAdapter.ViewHolder>() {

    private var items: List<QueueItem> = emptyList()

    fun submitItems(newItems: List<QueueItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: ItemQueueTrackBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QueueItem, onSelect: (Int) -> Unit) {
            binding.queueTitle.text = item.title
            binding.queueArtist.text = item.artist
            val color = if (item.isCurrent) R.color.primary else R.color.on_surface
            binding.queueTitle.setTextColor(ContextCompat.getColor(binding.root.context, color))
            binding.queueTitle.setTypeface(null, if (item.isCurrent) Typeface.BOLD else Typeface.NORMAL)
            binding.root.setOnClickListener { onSelect(item.playerIndex) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onSelect)
    }

    override fun getItemCount(): Int = items.size
}
