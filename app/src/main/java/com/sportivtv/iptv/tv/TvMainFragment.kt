package com.sportivtv.iptv.tv

import android.os.Bundle
import android.content.Intent
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.sportivtv.iptv.subscription.SubscriptionActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.sportivtv.iptv.R
import com.sportivtv.iptv.data.ChannelRepository
import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.ChannelCategory
import com.sportivtv.iptv.player.PlayerActivity
import com.sportivtv.iptv.subscription.SubscriptionManager
import kotlinx.coroutines.launch

/**
 * Android TV Main Fragment using Leanback BrowseSupportFragment.
 *
 * Provides D-pad navigable channel browsing organized by category rows.
 */
class TvMainFragment : BrowseSupportFragment() {

    private lateinit var channelRepo: ChannelRepository
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelRepo = ChannelRepository(SubscriptionManager(requireContext()))

        adapter = rowsAdapter
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED

        loadChannels()
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            val channels = channelRepo.loadChannels()
            buildRows(channels)
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
            ChannelCategory.SPORTS, ChannelCategory.NEWS, ChannelCategory.MOVIES,
            ChannelCategory.ENTERTAINMENT, ChannelCategory.KIDS, ChannelCategory.DOCUMENTARY,
            ChannelCategory.MUSIC, ChannelCategory.OTHER
        )

        var rowIndex = 0
        for (category in categories) {
            val categoryChannels = channels.filter { it.category == category }
            if (categoryChannels.isEmpty()) continue

            val header = HeaderItem(rowIndex.toLong(), category.arabicName)
            val listAdapter = ArrayObjectAdapter(ChannelPresenter())
            categoryChannels.forEach { listAdapter.add(it) }
            rowsAdapter.add(ListRow(header, listAdapter))
            rowIndex++
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
                setTextColor(resources.getColor(R.color.background_dark, null))
                setBackgroundColor(resources.getColor(R.color.sportiv_gold, null))
            }
            return ViewHolder(button)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val button = viewHolder.view as MaterialButton
            button.text = item?.toString() ?: getString(R.string.welcome_add_subscription)
            button.setOnClickListener {
                startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            viewHolder.view.setOnClickListener(null)
        }
    }

    private inner class ChannelPresenter : Presenter() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel_grid, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val channel = item as? Channel ?: return
            viewHolder.view.setOnClickListener {
                PlayerActivity.start(requireContext(), channel)
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }
}
