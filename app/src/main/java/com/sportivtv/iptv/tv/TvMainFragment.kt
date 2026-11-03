package com.sportivtv.iptv.tv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.sportivtv.iptv.R
import com.sportivtv.iptv.data.ChannelRepository
import com.sportivtv.iptv.favorites.FavoritesManager
import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.ChannelCategory
import com.sportivtv.iptv.player.PlayerActivity
import com.sportivtv.iptv.subscription.SubscriptionActivity
import com.sportivtv.iptv.subscription.SubscriptionManager
import kotlinx.coroutines.launch

/** Leanback home with category rows and working favorite controls. */
class TvMainFragment : BrowseSupportFragment() {
    private lateinit var channelRepo: ChannelRepository
    private lateinit var favoritesManager: FavoritesManager
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelRepo = ChannelRepository(SubscriptionManager(requireContext()))
        favoritesManager = FavoritesManager(requireContext())
        adapter = rowsAdapter
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        brandColor = ContextCompat.getColor(requireContext(), R.color.background_dark)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.sportiv_gold)
    }

    override fun onResume() {
        super.onResume()
        loadChannels()
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            runCatching { channelRepo.loadChannels(forceRefresh = true) }
                .onSuccess(::buildRows)
                .onFailure {
                    Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_LONG).show()
                    buildRows(emptyList())
                }
        }
    }

    private fun buildRows(channels: List<Channel>) {
        rowsAdapter.clear()
        if (channels.isEmpty()) {
            val header = HeaderItem(0, getString(R.string.welcome_title))
            val emptyAdapter = ArrayObjectAdapter(SubscriptionActionPresenter())
            emptyAdapter.add(getString(R.string.welcome_add_subscription))
            rowsAdapter.add(ListRow(header, emptyAdapter))
            return
        }

        val categories = listOf(
            ChannelCategory.SPORTS, ChannelCategory.ARABIC, ChannelCategory.INTERNATIONAL,
            ChannelCategory.NEWS, ChannelCategory.OTHER, ChannelCategory.MOVIES,
            ChannelCategory.ENTERTAINMENT, ChannelCategory.KIDS,
            ChannelCategory.DOCUMENTARY, ChannelCategory.MUSIC
        )
        var rowIndex = 0L
        categories.forEach { category ->
            val matching = channels.filter { it.category == category }
            if (matching.isNotEmpty()) {
                val row = ArrayObjectAdapter(ChannelPresenter())
                matching.forEach(row::add)
                rowsAdapter.add(ListRow(HeaderItem(rowIndex++, category.arabicName), row))
            }
        }

        val favorites = channels.filter { favoritesManager.isFavorite(it.id) }
        if (favorites.isNotEmpty()) {
            val row = ArrayObjectAdapter(ChannelPresenter())
            favorites.forEach(row::add)
            rowsAdapter.add(ListRow(HeaderItem(rowIndex, getString(R.string.nav_favorites)), row))
        }
    }

    private inner class SubscriptionActionPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val button = MaterialButton(parent.context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                minWidth = 280
                minHeight = 64
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.background_dark))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.sportiv_gold)
                )
            }
            return ViewHolder(button)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            (viewHolder.view as MaterialButton).apply {
                text = item?.toString() ?: getString(R.string.welcome_add_subscription)
                setOnClickListener {
                    startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
                }
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            viewHolder.view.setOnClickListener(null)
        }
    }

    private inner class ChannelPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel_grid, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val channel = item as? Channel ?: return
            val view = viewHolder.view
            view.findViewById<TextView>(R.id.grid_channel_name).text = channel.name
            view.findViewById<TextView>(R.id.grid_channel_category).text = channel.category.arabicName
            val logo = view.findViewById<ImageView>(R.id.grid_channel_logo)
            Glide.with(view).load(channel.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(logo)
            val favorite = view.findViewById<ImageButton>(R.id.grid_channel_favorite)
            fun renderFavorite() {
                val selected = favoritesManager.isFavorite(channel.id)
                favorite.setImageResource(
                    if (selected) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                )
                favorite.contentDescription = getString(
                    if (selected) R.string.fav_remove else R.string.fav_add
                )
            }
            renderFavorite()
            favorite.setOnClickListener {
                favoritesManager.toggleFavorite(channel)
                renderFavorite()
                loadChannels()
                Toast.makeText(
                    requireContext(),
                    if (favoritesManager.isFavorite(channel.id)) R.string.fav_added_toast else R.string.fav_removed_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
            view.setOnClickListener { PlayerActivity.start(requireContext(), channel) }
            view.setOnFocusChangeListener { focusedView, focused ->
                focusedView.isSelected = focused
                focusedView.isActivated = focused
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            viewHolder.view.setOnClickListener(null)
            viewHolder.view.findViewById<ImageButton>(R.id.grid_channel_favorite)?.setOnClickListener(null)
        }
    }
}
