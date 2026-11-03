package com.sportivtv.iptv.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sportivtv.iptv.R
import com.sportivtv.iptv.model.Channel

/**
 * Adapter for the in-player channel list.
 */
class ChannelListAdapter(
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelListAdapter.ChannelViewHolder>() {

    private var channels: List<Channel> = emptyList()
    private var selectedPosition = 0

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_list, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel, position == selectedPosition)
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.channel_item_name)
        private val logoImg: ImageView = itemView.findViewById(R.id.channel_item_logo)
        private val categoryText: TextView = itemView.findViewById(R.id.channel_item_category)

        fun bind(channel: Channel, isSelected: Boolean) {
            nameText.text = channel.name
            categoryText.text = channel.category.arabicName

            // Load logo
            if (!channel.logoUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.ic_channel_placeholder)
                    .error(R.drawable.ic_channel_placeholder)
                    .into(logoImg)
            } else {
                logoImg.setImageResource(R.drawable.ic_channel_placeholder)
            }

            // Highlight selected
            itemView.isActivated = isSelected

            itemView.setOnClickListener {
                selectedPosition = bindingAdapterPosition
                onChannelClick(channel)
            }
        }
    }
}
