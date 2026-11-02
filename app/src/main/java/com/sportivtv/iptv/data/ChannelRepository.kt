package com.sportivtv.iptv.data

import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.ChannelCategory
import com.sportivtv.iptv.subscription.SubscriptionManager

/**
 * Channel Repository.
 * Central data source for all channel operations: loading, filtering, searching.
 *
 * Caches channels in memory after first load to enable fast navigation.
 */
class ChannelRepository(
    private val subscriptionManager: SubscriptionManager
) {
    // In-memory channel cache
    private var channelsCache: List<Channel> = emptyList()
    private var lastLoadTime: Long = 0
    private val cacheExpiryMs = 5 * 60 * 1000L // 5 minutes

    /**
     * Load all channels from all active subscriptions.
     * Uses cache if fresh, otherwise reloads.
     */
    suspend fun loadChannels(forceRefresh: Boolean = false): List<Channel> {
        if (!forceRefresh && channelsCache.isNotEmpty() &&
            System.currentTimeMillis() - lastLoadTime < cacheExpiryMs) {
            return channelsCache
        }
        channelsCache = subscriptionManager.loadAllChannels()
        lastLoadTime = System.currentTimeMillis()
        return channelsCache
    }

    /**
     * Get channels filtered by category.
     */
    suspend fun getChannelsByCategory(category: ChannelCategory): List<Channel> {
        val all = loadChannels()
        return if (category == ChannelCategory.ALL) all
        else all.filter { it.category == category }
    }

    /**
     * Get all unique categories that have channels.
     */
    suspend fun getAvailableCategories(): List<ChannelCategory> {
        val all = loadChannels()
        return all.map { it.category }.distinct().sortedBy { it.ordinal }
    }

    /**
     * Get channels grouped by their M3U group-title.
     */
    suspend fun getChannelsGrouped(): Map<String, List<Channel>> {
        val all = loadChannels()
        return all.groupBy { it.groupTitle ?: "أخرى" }
    }

    /**
     * Find a channel by its ID.
     */
    fun findChannelById(channelId: String): Channel? {
        return channelsCache.find { it.id == channelId }
    }

    /**
     * Get next/previous channel in the list (for player channel switching).
     */
    fun getNextChannel(currentChannelId: String): Channel? {
        val index = channelsCache.indexOfFirst { it.id == currentChannelId }
        if (index < 0 || index >= channelsCache.size - 1) return null
        return channelsCache[index + 1]
    }

    fun getPrevChannel(currentChannelId: String): Channel? {
        val index = channelsCache.indexOfFirst { it.id == currentChannelId }
        if (index <= 0) return null
        return channelsCache[index - 1]
    }

    /**
     * Get all channels (from cache, or trigger load).
     */
    fun getCachedChannels(): List<Channel> = channelsCache

    /**
     * Clear the cache.
     */
    fun clearCache() {
        channelsCache = emptyList()
        lastLoadTime = 0
    }
}
