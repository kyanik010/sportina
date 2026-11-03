package com.sportivtv.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
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
 * Arabic-first sports IPTV home. The screen never fabricates match fixtures:
 * it presents the channels and provider data actually available to the user.
 */
class MainActivity : AppCompatActivity() {

    private enum class Destination { CHANNELS, FAVORITES }

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var channelRepo: ChannelRepository
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var channelSearch: ChannelSearch
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var searchEditText: EditText
    private lateinit var channelsRecyclerView: RecyclerView
    private lateinit var loadingProgress: View
    private lateinit var emptyStateText: TextView
    private lateinit var emptyStateAction: com.google.android.material.button.MaterialButton
    private lateinit var emptyStatePanel: View
    private lateinit var channelCountText: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var channelAdapter: ChannelGridAdapter
    private lateinit var categoryTabs: TabLayout

    private var allChannels: List<Channel> = emptyList()
    private var currentCategory = ChannelCategory.ALL
    private var currentSearchQuery = ""
    private var destination = Destination.CHANNELS
    private var hasLoadedOnce = false
    private var loadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        subscriptionManager = SubscriptionManager(this)
        channelRepo = ChannelRepository(subscriptionManager)
        favoritesManager = FavoritesManager(this)
        channelSearch = ChannelSearch()
        bindViews()
        if (subscriptionManager.getSubscriptions().isEmpty()) {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        loadChannels()
    }

    private fun bindViews() {
        bottomNav = findViewById(R.id.bottom_nav)
        searchEditText = findViewById(R.id.search_edit)
        channelsRecyclerView = findViewById(R.id.channels_rv)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyStateText = findViewById(R.id.empty_state_text)
        emptyStateAction = findViewById(R.id.empty_state_action)
        emptyStatePanel = findViewById(R.id.empty_state_panel)
        channelCountText = findViewById(R.id.channel_count)
        sectionTitle = findViewById(R.id.section_title)
        categoryTabs = findViewById(R.id.category_tabs)

        val columns = if (resources.getBoolean(R.bool.is_tv)) 5 else 2
        channelsRecyclerView.layoutManager = GridLayoutManager(this, columns)
        channelAdapter = ChannelGridAdapter(
            favoritesManager,
            onChannelClick = { PlayerActivity.start(this, it) },
            onFavoriteChanged = { if (destination == Destination.FAVORITES) showFavorites() }
        )
        channelsRecyclerView.adapter = channelAdapter

        emptyStateAction.setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString().orEmpty()
                renderChannels()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val categories = listOf(
            ChannelCategory.ALL, ChannelCategory.SPORTS, ChannelCategory.ARABIC,
            ChannelCategory.INTERNATIONAL, ChannelCategory.NEWS, ChannelCategory.OTHER
        )
        categories.forEach { category ->
            val tab = categoryTabs.newTab()
            tab.text = category.arabicName
            tab.tag = category
            categoryTabs.addTab(tab)
        }
        categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentCategory = tab.tag as? ChannelCategory ?: ChannelCategory.ALL
                renderChannels()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_channels -> {
                    destination = Destination.CHANNELS
                    if (searchEditText.text.isNotEmpty()) searchEditText.setText("")
                    currentCategory = ChannelCategory.ALL
                    categoryTabs.getTabAt(0)?.select()
                    sectionTitle.setText(R.string.browse_title)
                    if (hasLoadedOnce) renderChannels() else loadChannels()
                    true
                }
                R.id.nav_favorites -> {
                    destination = Destination.FAVORITES
                    if (searchEditText.text.isNotEmpty()) searchEditText.setText("")
                    currentSearchQuery = ""
                    sectionTitle.setText(R.string.favorites_title)
                    currentCategory = ChannelCategory.ALL
                    categoryTabs.getTabAt(0)?.select()
                    showFavorites()
                    true
                }
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

    override fun onResume() {
        super.onResume()
        if (!hasLoadedOnce) return
        if (destination == Destination.FAVORITES) showFavorites() else loadChannels()
    }

    private fun loadChannels() {
        loadingProgress.visibility = View.VISIBLE
        emptyStatePanel.visibility = View.GONE
        lifecycleScope.launch {
            try {
                allChannels = channelRepo.loadChannels(forceRefresh = true)
                loadFailed = false
                hasLoadedOnce = true
                renderChannels()
            } catch (_: Exception) {
                loadFailed = true
                hasLoadedOnce = true
                renderChannels()
            } finally {
                loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun showFavorites() {
        val source = allChannels.associateBy { it.id }
        allChannels = favoritesManager.getFavorites().map { favorite ->
            source[favorite.channelId] ?: Channel(
                id = favorite.channelId,
                name = favorite.channelName,
                logoUrl = favorite.channelLogo,
                streamUrl = favorite.streamUrl,
                category = Channel.categorize(favorite.channelName, null)
            )
        }
        renderChannels()
    }

    private fun renderChannels() {
        val base = if (destination == Destination.FAVORITES) {
            val ids = favoritesManager.getFavorites().mapTo(hashSetOf()) { it.channelId }
            allChannels.filter { it.id in ids }
        } else allChannels

        var filtered = base
        if (currentCategory != ChannelCategory.ALL) {
            filtered = filtered.filter { it.category == currentCategory }
        }
        if (currentSearchQuery.isNotBlank()) {
            filtered = channelSearch.search(filtered, currentSearchQuery)
        }

        channelAdapter.updateChannels(filtered)
        channelCountText.text = getString(R.string.browse_channel_count, filtered.size)

        val noSubscriptions = subscriptionManager.getSubscriptions().isEmpty()
        val message = when {
            loadFailed && destination == Destination.CHANNELS -> getString(R.string.error_network)
            destination == Destination.FAVORITES && base.isEmpty() -> getString(R.string.fav_empty)
            currentSearchQuery.isNotBlank() && filtered.isEmpty() -> getString(R.string.search_no_results)
            filtered.isEmpty() && noSubscriptions -> getString(R.string.welcome_empty_description)
            filtered.isEmpty() -> getString(R.string.sub_no_channels_for_category)
            else -> null
        }
        emptyStatePanel.visibility = if (message == null) View.GONE else View.VISIBLE
        emptyStateText.text = message.orEmpty()
        emptyStateAction.visibility = if (message != null && noSubscriptions &&
            destination == Destination.CHANNELS && currentSearchQuery.isBlank()) View.VISIBLE else View.GONE
        loadingProgress.visibility = View.GONE
    }
}

class ChannelGridAdapter(
    private val favoritesManager: FavoritesManager,
    private val onChannelClick: (Channel) -> Unit,
    private val onFavoriteChanged: () -> Unit = {}
) : RecyclerView.Adapter<ChannelGridAdapter.ChannelGridViewHolder>() {

    private var channels: List<Channel> = emptyList()

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelGridViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel_grid, parent, false)
        return ChannelGridViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelGridViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount() = channels.size

    inner class ChannelGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.grid_channel_name)
        private val logoImg: ImageView = itemView.findViewById(R.id.grid_channel_logo)
        private val categoryText: TextView = itemView.findViewById(R.id.grid_channel_category)
        private val favoriteButton: android.widget.ImageButton =
            itemView.findViewById(R.id.grid_channel_favorite)

        fun bind(channel: Channel) {
            nameText.text = channel.name
            categoryText.text = channel.category.arabicName
            Glide.with(itemView).load(channel.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(logoImg)
            val favorite = favoritesManager.isFavorite(channel.id)
            renderFavorite(favorite)
            favoriteButton.setOnClickListener {
                renderFavorite(favoritesManager.toggleFavorite(channel))
                onFavoriteChanged()
            }
            itemView.setOnClickListener { onChannelClick(channel) }
            itemView.setOnFocusChangeListener { view, focused ->
                view.isSelected = focused
                view.isActivated = focused
            }
        }

        private fun renderFavorite(favorite: Boolean) {
            favoriteButton.setImageResource(
                if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            favoriteButton.contentDescription = itemView.context.getString(
                if (favorite) R.string.fav_remove else R.string.fav_add
            )
        }
    }
}
