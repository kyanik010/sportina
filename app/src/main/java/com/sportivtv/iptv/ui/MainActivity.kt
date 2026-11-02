package com.sportivtv.iptv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.sportivtv.iptv.R
import com.sportivtv.iptv.data.ChannelRepository
import com.sportivtv.iptv.favorites.FavoritesManager
import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.ChannelCategory
import com.sportivtv.iptv.player.PlayerActivity
import com.sportivtv.iptv.search.ChannelSearch
import com.sportivtv.iptv.subscription.SubscriptionActivity
import com.sportivtv.iptv.subscription.SubscriptionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * Main Activity - Channel browsing interface.
 *
 * Features:
 * - Category tabs (Sports, Movies, News, etc.)
 * - Channel grid with logos
 * - Search bar
 * - Favorites view
 * - Bottom navigation (Channels, Favorites, Subscriptions, Settings)
 *
 * RTL layout, Arabic-first design.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var channelRepo: ChannelRepository
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var channelSearch: ChannelSearch

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var searchEditText: EditText
    private lateinit var channelsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var emptyStateAction: com.google.android.material.button.MaterialButton
    private lateinit var emptyStatePanel: android.view.View
    private lateinit var channelAdapter: ChannelGridAdapter

    private var allChannels: List<Channel> = emptyList()
    private var currentCategory: ChannelCategory = ChannelCategory.ALL
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initManagers()
        initViews()
        loadChannels()
    }

    private fun initManagers() {
        subscriptionManager = SubscriptionManager(this)
        channelRepo = ChannelRepository(subscriptionManager)
        favoritesManager = FavoritesManager(this)
        channelSearch = ChannelSearch()
    }

    private fun initViews() {
        searchEditText = findViewById(R.id.search_edit)
        channelsRecyclerView = findViewById(R.id.channels_rv)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyStateText = findViewById(R.id.empty_state_text)
        emptyStateAction = findViewById(R.id.empty_state_action)
        emptyStatePanel = findViewById(R.id.empty_state_panel)
        emptyStateAction.setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        bottomNav = findViewById(R.id.bottom_nav)

        // Channel grid: 4 columns on phone, more on TV
        val spanCount = if (resources.getBoolean(R.bool.is_tv)) 6 else 4
        channelsRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        channelAdapter = ChannelGridAdapter { channel ->
            PlayerActivity.start(this, channel)
        }
        channelsRecyclerView.adapter = channelAdapter

        // Search
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Category tabs
        val tabLayout = findViewById<TabLayout>(R.id.category_tabs)
        val categories = listOf(
            ChannelCategory.ALL, ChannelCategory.SPORTS, ChannelCategory.NEWS,
            ChannelCategory.MOVIES, ChannelCategory.ENTERTAINMENT, ChannelCategory.KIDS
        )
        categories.forEach { category ->
            val tab = tabLayout.newTab()
            tab.text = category.arabicName
            tab.tag = category
            tabLayout.addTab(tab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentCategory = tab.tag as ChannelCategory
                applyFilters()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_channels -> { loadChannels(); true }
                R.id.nav_favorites -> { showFavorites(); true }
                R.id.nav_subscriptions -> {
                    startActivity(Intent(this, SubscriptionActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadChannels() {
        loadingProgress.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                allChannels = channelRepo.loadChannels()
                applyFilters()
            } catch (e: Exception) {
                emptyStateText.text = getString(R.string.error_network)
                emptyStateText.visibility = View.VISIBLE
            } finally {
                loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun showFavorites() {
        val favorites = favoritesManager.getFavorites()
        allChannels = favorites.map { fav ->
            Channel(
                id = fav.channelId,
                name = fav.channelName,
                logoUrl = fav.channelLogo,
                streamUrl = fav.streamUrl
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = allChannels

        // Category filter
        if (currentCategory != ChannelCategory.ALL) {
            filtered = filtered.filter { it.category == currentCategory }
        }

        // Search filter
        if (currentSearchQuery.isNotBlank()) {
            filtered = channelSearch.search(filtered, currentSearchQuery)
        }

        channelAdapter.updateChannels(filtered)

        // Give first-time users a clear path to add a subscription.
        val hasNoSubscriptions = subscriptionManager.getSubscriptions().isEmpty()
        if (filtered.isEmpty()) {
            emptyStateText.text = when {
                currentSearchQuery.isNotBlank() -> getString(R.string.search_no_results)
                hasNoSubscriptions -> getString(R.string.welcome_empty_description)
                else -> getString(R.string.sub_no_channels_for_category)
            }
            emptyStatePanel.visibility = View.VISIBLE
            emptyStateText.visibility = View.VISIBLE
            emptyStateAction.visibility = if (currentSearchQuery.isNotBlank()) View.GONE else View.VISIBLE
        } else {
            emptyStatePanel.visibility = View.GONE
            emptyStateText.visibility = View.GONE
            emptyStateAction.visibility = View.GONE
        }
    }
}

/**
 * Channel grid adapter.
 */
class ChannelGridAdapter(
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelGridAdapter.ChannelGridViewHolder>() {

    private var channels: List<Channel> = emptyList()

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelGridViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_grid, parent, false)
        return ChannelGridViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelGridViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.grid_channel_name)
        private val logoImg: ImageView = itemView.findViewById(R.id.grid_channel_logo)
        private val categoryText: TextView = itemView.findViewById(R.id.grid_channel_category)

        fun bind(channel: Channel) {
            nameText.text = channel.name
            categoryText.text = channel.category.arabicName

            if (!channel.logoUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.ic_channel_placeholder)
                    .error(R.drawable.ic_channel_placeholder)
                    .into(logoImg)
            } else {
                logoImg.setImageResource(R.drawable.ic_channel_placeholder)
            }

            itemView.setOnClickListener { onChannelClick(channel) }
        }
    }
}
